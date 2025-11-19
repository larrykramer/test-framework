/*
 * Copyright (c) 2025 Larry Kramer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.larrykramer.test.service;

import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.*;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import net.larrykramer.test.config.GridConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.factory.WebDriverFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WebDriverServiceTest {
    @Mock
    private WebDriverFactory<MutableCapabilities> mockFactory;

    @Mock
    private WebDriver mockDriver;

    @Mock
    private WebDriver.Options mockOptions;
    @Mock
    private WebDriver.Timeouts mockTimeouts;
    @Mock
    private WebDriver.Window mockWindow;

    @Before
    public void setUp() {
        stubWebDriver(mockDriver);
    }

    @Test
    public void testCreateWebDriver_withLocalConfig_returnsConfiguredWebDriver() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        config.implicitTimeout = 500L;
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);
        // Act
        WebDriver result = service.createWebDriver();
        // Assert
        assertSame(mockDriver, result);
        verify(mockFactory).createWebDriver();
        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(500L));
        verify(mockOptions).deleteAllCookies();
        verify(mockWindow, never()).maximize();
        verify(mockWindow, never()).setSize(any(Dimension.class));
    }

    @Test
    public void testCreateWebDriver_givenNegativeImplicitTimeout_usesZeroDuration() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        config.implicitTimeout = -10L;
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);
        // Act
        WebDriver result = service.createWebDriver();
        // Assert
        assertSame(mockDriver, result);
        verify(mockTimeouts).implicitlyWait(Duration.ZERO);
    }

    @Test
    public void testCreateWebDriver_givenWindowSizeAndMaximizeAreConfigured_setsWindowSize() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        config.maximize = true;
        config.windowSize = Optional.of(new Dimension(1920, 1080));
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);

        // Act
        WebDriver result = service.createWebDriver();

        // Assert
        assertSame(mockDriver, result);

        ArgumentCaptor<Dimension> captor = ArgumentCaptor.forClass(Dimension.class);
        verify(mockWindow).setSize(captor.capture());
        Dimension windowSize = captor.getValue();
        assertEquals(1920, windowSize.width);
        assertEquals(1080, windowSize.height);

        verify(mockWindow, never()).maximize();
    }

    @Test
    public void testCreateWebDriver_givenMaximizeWithHeadlessChrome_doesNotMaximize() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        config.maximize = true;
        config.headless = true;
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);
        // Act
        WebDriver result = service.createWebDriver();
        // Assert
        assertSame(mockDriver, result);
        verify(mockWindow, never()).maximize();
    }

    @Test
    public void testCreateWebDriver_givenMaximizeWithHeadlessSafari_maximizesWindow() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.SAFARI);
        config.maximize = true;
        config.headless = true;
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);
        // Act
        WebDriver result = service.createWebDriver();
        // Assert
        assertSame(mockDriver, result);
        verify(mockWindow).maximize();
    }

    @Test
    public void testCreateWebDriver_whenDeleteAllCookiesThrows_doesNotPropagateException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        stubFactory(mockFactory, config);
        doThrow(new WebDriverException("deleteAllCookies")).when(mockOptions).deleteAllCookies();

        WebDriverService service = createService(config, null, mockFactory);

        // Act
        WebDriver result = service.createWebDriver();

        // Assert
        assertSame(mockDriver, result);
        verify(mockOptions).deleteAllCookies();
    }

    @Test
    public void testCreateWebDriver_whenMaximizeThrows_doesNotPropagateException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        config.maximize = true;
        stubFactory(mockFactory, config);
        doThrow(new WebDriverException("maximize")).when(mockWindow).maximize();

        WebDriverService service = createService(config, null, mockFactory);

        // Act
        WebDriver result = service.createWebDriver();

        // Assert
        assertSame(mockDriver, result);
        verify(mockWindow).maximize();
    }

    @Test
    public void testCreateWebDriver_givenFactoryReturnsNull_throwsIllegalStateException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.createWebDriver()).thenReturn(null);
        WebDriverService service = createService(config, null, mockFactory);
        // Act & Assert
        var e = assertThrows(IllegalStateException.class, service::createWebDriver);
        assertTrue(e.getMessage().startsWith("Unable to create WebDriver using factory "));
    }

    @Test
    public void testCreateWebDriver_withUnknownType_throwsIllegalArgumentException() {
        // Arrange
        WebDriverService service = createService(createConfig(WebDriverType.EDGE), null);
        // Act & Arrange
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("Unsupported WebDriver EDGE", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenSPIWithoutFactoryClass_throwsIllegalArgumentException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.SPI);
        when(mockFactory.getType()).thenReturn(config.type);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        String expected = "webdriver.spi must be set when webdriver.type=SPI";
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenSPIFactoryClassNotFound_throwsIllegalArgumentException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.SPI);
        config.spi = Optional.of("MissingFactory");

        when(mockFactory.getType()).thenReturn(config.type);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("No SPI factory found for MissingFactory", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenConfiguredSPIFactoryClass_returnsConfiguredWebDriver() {
        // Arrange
        @SuppressWarnings("unchecked")
        WebDriverFactory<MutableCapabilities> mockFirstSPIFactory = mock(WebDriverFactory.class);
        @SuppressWarnings("unchecked")
        WebDriverFactory<MutableCapabilities> mockSecondSPIFactory = mock(WebDriverFactory.class);

        WebDriverConfig config = createConfig(WebDriverType.SPI);
        config.spi = Optional.of(mockSecondSPIFactory.getClass().getName());
        stubFactory(mockFirstSPIFactory, config);
        stubFactory(mockSecondSPIFactory, config);

        WebDriverService service;
        service = createService(config, null, mockFirstSPIFactory, mockSecondSPIFactory);

        // Act
        WebDriver result = service.createWebDriver();

        // Assert
        assertSame(mockDriver, result);
        verify(mockSecondSPIFactory).createWebDriver();
        verify(mockFirstSPIFactory, never()).createWebDriver();
    }

    @Test
    public void testCreateWebDriver_givenMultipleFactoriesForSameType_usesLastRegisteredFactory() {
        // Arrange
        @SuppressWarnings("unchecked")
        WebDriverFactory<MutableCapabilities> mockFirstFactory = mock(WebDriverFactory.class);
        @SuppressWarnings("unchecked")
        WebDriverFactory<MutableCapabilities> mockSecondFactory = mock(WebDriverFactory.class);

        WebDriverConfig config = createConfig(WebDriverType.CHROME);

        stubFactory(mockFirstFactory, config);
        stubFactory(mockSecondFactory, config);

        WebDriverService service = createService(config, null, mockFirstFactory, mockSecondFactory);

        // Act
        WebDriver result = service.createWebDriver();

        // Assert
        assertSame(mockDriver, result);
        verify(mockSecondFactory).createWebDriver();
        verify(mockFirstFactory, never()).createWebDriver();
    }

    @Test
    public void testCreateWebDriver_givenGridUnsupported_throwsIllegalStateException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/wd/hub"));

        stubFactory(mockFactory, config);

        WebDriverService service = createService(config, grid, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalStateException.class, service::createWebDriver);
        String expected = "Grid execution not supported for WebDriver CHROME";
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void testCreateWebDriver_withInvalidGridURL_throwsIllegalArgumentException() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("grid://admin:s3cr3t@selenium-hub.local:4444"));

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(new MutableCapabilities());

        WebDriverService service = createService(config, grid, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("Invalid Selenium Grid URL 'grid://selenium-hub.local:4444'", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_whenSanitizationFails_returnsSafeFallback() throws Exception {
        // Arrange
        // Setup mock values to trigger URISyntaxException in the sanitization helper.
        // The 7-arg URI constructor throws if a Host is present but the Path is relative.
        URI mockURI = mock(URI.class);
        when(mockURI.toURL()).thenThrow(new MalformedURLException("Invalid protocol"));
        when(mockURI.getScheme()).thenReturn("grid");
        when(mockURI.getHost()).thenReturn("selenium-hub");
        when(mockURI.getPort()).thenReturn(4444);
        when(mockURI.getPath()).thenReturn("relative/path"); // the trap

        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(mockURI);

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(new MutableCapabilities());

        WebDriverService service = createService(config, grid, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("Invalid Selenium Grid URL 'grid://<masked>@selenium-hub'", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_whenGridIsConfigured_createsRemoteWebDriverWithCapabilities() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.FIREFOX);
        config.implicitTimeout = 100L;
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444"));
        grid.browserVersion = Optional.of("140.5.0");
        grid.platform = Optional.of("linux");
        grid.applicationName = Optional.of("suite");
        grid.capabilities = Map.of("custom", "value");

        MutableCapabilities options = new MutableCapabilities();

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(options);

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class, (mock, context) -> {
            stubWebDriver(mock);
            capturedArguments.addAll(context.arguments());
        })) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            WebDriver result = service.createWebDriver();

            // Assert
            assertSame(mocked.constructed().getFirst(), result);
        }

        assertEquals(2, capturedArguments.size());
        MutableCapabilities capturedOptions = (MutableCapabilities) capturedArguments.get(1);

        assertNotEquals(options, capturedOptions);

        assertEquals("firefox", capturedOptions.getCapability(CapabilityType.BROWSER_NAME));
        assertEquals("140.5.0", capturedOptions.getCapability(CapabilityType.BROWSER_VERSION));
        assertEquals(Platform.LINUX, capturedOptions.getCapability(CapabilityType.PLATFORM_NAME));
        assertEquals("suite", capturedOptions.getCapability("applicationName"));
        assertEquals("suite", capturedOptions.getCapability("se:applicationName"));
        assertEquals("value", capturedOptions.getCapability("custom"));

        verify(mockFactory, never()).createWebDriver();
        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(100L));
        verify(mockOptions).deleteAllCookies();
    }

    @Test
    public void testCreateWebDriver_givenGridWithSPIType_doesNotSetBrowserNameOrVersion() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.SPI);
        config.spi = Optional.of(mockFactory.getClass().getName());
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/"));
        grid.browserVersion = Optional.of("1.0");

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(new MutableCapabilities());

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class, (mock, context) -> {
            stubWebDriver(mock);
            capturedArguments.addAll(context.arguments());
        })) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            WebDriver result = service.createWebDriver();

            // Assert
            assertSame(mocked.constructed().getFirst(), result);
        }

        assertEquals(2, capturedArguments.size());
        MutableCapabilities options = (MutableCapabilities) capturedArguments.get(1);

        assertNull(options.getCapability(CapabilityType.BROWSER_NAME));
        assertNull(options.getCapability(CapabilityType.BROWSER_VERSION));
    }

    @Test
    public void testDestroyWebDriver_withDriver_callsQuit() {
        WebDriverService service = createService(createConfig(WebDriverType.CHROME), null);
        service.destroyWebDriver(mockDriver);
        verify(mockDriver).quit();
    }

    @Test
    public void testDestroyWebDriver_withNullDriver_doesNothing() {
        WebDriverService service = createService(createConfig(WebDriverType.CHROME), null);
        service.destroyWebDriver(null);
    }

    @Test
    public void testDestroyWebDriver_whenQuitThrows_doesNotPropagateException() {
        // Arrange
        WebDriverService service = createService(createConfig(WebDriverType.CHROME), null);
        doThrow(new RuntimeException("quit")).when(mockDriver).quit();
        // Act
        service.destroyWebDriver(mockDriver);
        // Assert
        verify(mockDriver).quit();
    }

    private void stubFactory(WebDriverFactory<?> factory, WebDriverConfig config) {
        when(factory.getType()).thenReturn(config.type);
        when(factory.createWebDriver()).thenReturn(mockDriver);
    }

    private void stubWebDriver(WebDriver driver) {
        when(driver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);
    }

    private static WebDriverConfig createConfig(WebDriverType type) {
        WebDriverConfig config = new WebDriverConfig();

        config.type = type;
        config.spi = Optional.empty();

        config.headless = false;

        config.maximize = false;
        config.windowSize = Optional.empty();

        config.implicitTimeout = 0L;

        config.allowInsecureCerts = false;

        config.proxyAddress = Optional.empty();
        config.proxyUser = Optional.empty();
        config.proxyPassword = Optional.empty();
        config.nonProxyHosts = Optional.empty();

        return config;
    }

    private static GridConfig createGridConfig() {
        GridConfig grid = new GridConfig();

        grid.uri = Optional.empty();

        grid.browserVersion = Optional.empty();
        grid.platform = Optional.empty();
        grid.applicationName = Optional.empty();

        grid.capabilities = Map.of();

        return grid;
    }

    private static WebDriverService createService(WebDriverConfig config, GridConfig grid,
            WebDriverFactory<?>... factories) {
        if (grid == null) {
            grid = createGridConfig();
        }

        // Do not mock jakarta.enterprise.inject.Instance with Mockito:
        // When IntelliJ (or JaCoCo) runs the suite in coverage mode it instruments the Instance
        // interface, adding synthetic methods; Mockito’s inline mock-maker then fails while trying
        // to redefine that instrumented class. Using a stub implementation avoids that byte-code
        // clash and keeps coverage runs green.
        return new WebDriverService(config, grid, new Instance<>() {
            @Override
            public Iterator<WebDriverFactory<?>> iterator() {
                return List.of(factories).iterator();
            }

            @Override
            public boolean isUnsatisfied() {
                return factories.length == 0;
            }

            @Override
            public boolean isAmbiguous() {
                return factories.length > 1;
            }

            // -- Unused methods --

            @Override
            public Instance<WebDriverFactory<?>> select(Annotation... annotations) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U extends WebDriverFactory<?>> Instance<U> select(Class<U> aClass,
                    Annotation... annotations) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U extends WebDriverFactory<?>> Instance<U> select(TypeLiteral<U> typeLiteral,
                    Annotation... annotations) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void destroy(WebDriverFactory<?> factory) {
            }

            @Override
            public Handle<WebDriverFactory<?>> getHandle() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterable<? extends Handle<WebDriverFactory<?>>> handles() {
                throw new UnsupportedOperationException();
            }

            @Override
            public WebDriverFactory<?> get() {
                throw new UnsupportedOperationException();
            }
        });
    }
}
