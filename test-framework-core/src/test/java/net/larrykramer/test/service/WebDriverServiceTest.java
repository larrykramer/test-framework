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
import java.util.*;
import java.util.logging.*;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import net.larrykramer.test.config.GridConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.webdriver.WebDriverFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
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

    @Test
    public void testCreateWebDriver_withLocalConfig_returnsConfiguredWebDriver() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);
        // Act
        WebDriver result = service.createWebDriver();
        // Assert
        assertSame(mockDriver, result);
        verify(mockFactory).createWebDriver();
        verify(mockFactory).configureWebDriver(mockDriver);
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
        verify(mockFactory, never()).configureWebDriver(any());
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
        verify(mockSecondSPIFactory).configureWebDriver(mockDriver);
        verify(mockFirstSPIFactory, never()).createWebDriver();
        verify(mockFirstSPIFactory, never()).configureWebDriver(any());
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
        verify(mockSecondFactory).configureWebDriver(mockDriver);
        verify(mockFirstFactory, never()).createWebDriver();
        verify(mockFirstFactory, never()).configureWebDriver(any());
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

        try (var mocked = mockConstruction(RemoteWebDriver.class,
                (mock, context) -> capturedArguments.addAll(context.arguments()))) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            WebDriver result = service.createWebDriver();

            // Assert
            assertSame(mocked.constructed().getFirst(), result);
            verify(mockFactory, never()).createWebDriver();
            verify(mockFactory).configureWebDriver(result);
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
    }

    @Test
    public void testCreateWebDriver_givenGridWithSPIType_doesNotSetBrowserName() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.SPI);
        config.spi = Optional.of(mockFactory.getClass().getName());
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/"));

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(new MutableCapabilities());

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class,
                (mock, context) -> capturedArguments.addAll(context.arguments()))) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            service.createWebDriver();
        }

        // Assert
        assertEquals(2, capturedArguments.size());

        MutableCapabilities options = (MutableCapabilities) capturedArguments.get(1);
        assertNull(options.getCapability(CapabilityType.BROWSER_NAME));
    }

    @Test
    public void testCreateWebDriver_whenOptionsHasBrowserName_preservesExistingBrowserName() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.EDGE);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444"));

        MutableCapabilities options = new MutableCapabilities();
        options.setCapability(CapabilityType.BROWSER_NAME, "mock-browser");

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(options);

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class,
                (mock, context) -> capturedArguments.addAll(context.arguments()))) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            service.createWebDriver();
        }

        // Assert
        assertEquals(2, capturedArguments.size());

        MutableCapabilities capturedOptions = (MutableCapabilities) capturedArguments.get(1);
        assertEquals("mock-browser", capturedOptions.getCapability(CapabilityType.BROWSER_NAME));
    }

    @Test
    public void testCreateWebDriver_whenGridURLHasCustomPath_logsInfoMessage() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        config.spi = Optional.of(mockFactory.getClass().getName());
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/custom/grid"));

        when(mockFactory.getType()).thenReturn(config.type);
        when(mockFactory.getOptions()).thenReturn(new MutableCapabilities());

        Logger logger = Logger.getLogger(WebDriverService.class.getName());
        Level originalLevel = logger.getLevel();
        boolean useParentHandlers = logger.getUseParentHandlers();

        Handler mockLogHandler = mock(Handler.class);
        List<LogRecord> capturedLogRecords = new ArrayList<>();
        doAnswer(invocation -> {
            LogRecord original = invocation.getArgument(0);
            String message = new SimpleFormatter().formatMessage(original);
            capturedLogRecords.add(new LogRecord(original.getLevel(), message));
            return null;
        }).when(mockLogHandler).publish(any(LogRecord.class));

        logger.addHandler(mockLogHandler);
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);

        try (var mocked = mockConstruction(RemoteWebDriver.class)) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            service.createWebDriver();

            // Assert
            assertEquals(1, capturedLogRecords.size());

            LogRecord record = capturedLogRecords.getFirst();
            assertEquals(Level.INFO, record.getLevel());
            assertTrue(record.getMessage().startsWith("Configured Selenium URL is "
                    + "'https://localhost:4444/custom/grid'.\nIf you encounter connection issues, "
                    + "ensure https://localhost:4444/custom/grid"));
        } finally {
            logger.removeHandler(mockLogHandler);
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(useParentHandlers);
        }
    }

    @Test
    public void testCreateWebDriver_whenConfigureWebDriverThrows_quitsDriverAndPropagates() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        RuntimeException configureException = new RuntimeException("configure");
        stubFactory(mockFactory, config);
        doThrow(configureException).when(mockFactory).configureWebDriver(mockDriver);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(RuntimeException.class, service::createWebDriver);
        assertSame(configureException, e);
        verify(mockDriver).quit();
    }

    @Test
    public void testCreateWebDriver_whenConfigureWebDriverAndQuitThrow_quitExceptionIsSuppressed() {
        // Arrange
        WebDriverConfig config = createConfig(WebDriverType.CHROME);
        RuntimeException quitException = new RuntimeException("quite");
        stubFactory(mockFactory, config);
        doThrow(new RuntimeException("configure")).when(mockFactory).configureWebDriver(mockDriver);
        doThrow(quitException).when(mockDriver).quit();

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(RuntimeException.class, service::createWebDriver);
        assertEquals(1, e.getSuppressed().length);
        assertSame(quitException, e.getSuppressed()[0]);
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
