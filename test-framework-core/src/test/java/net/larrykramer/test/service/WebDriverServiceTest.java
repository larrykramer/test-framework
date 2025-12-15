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
import net.larrykramer.test.config.DriverType;
import net.larrykramer.test.config.GridConfig;
import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.webdriver.DriverFactory;
import net.larrykramer.test.webdriver.WebDriverReference;
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
    private DriverFactory<MutableCapabilities> mockFactory;

    @Mock
    private WebDriver mockDriver;

    @Test
    public void testCreateWebDriver_withLocalConfig_returnsConfiguredWebDriver() {
        // Arrange
        DriverConfig config = createConfig(DriverType.CHROME);
        stubFactory(mockFactory, config);
        WebDriverService service = createService(config, null, mockFactory);

        // Act
        WebDriverReference driverRef = service.createWebDriver();

        // Assert
        assertNotNull(driverRef);
        WebDriver driver = driverRef.get();

        assertSame(mockDriver, driver);
        verify(mockFactory).create();
        verify(mockFactory).configure(mockDriver);
    }

    @Test
    public void testCreateWebDriver_givenFactoryReturnsNull_throwsIllegalStateException() {
        // Arrange
        DriverConfig config = createConfig(DriverType.CHROME);
        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.create()).thenReturn(null);
        WebDriverService service = createService(config, null, mockFactory);
        // Act & Assert
        var e = assertThrows(IllegalStateException.class, service::createWebDriver);
        assertTrue(e.getMessage().startsWith("Unable to create driver using factory "));
        verify(mockFactory, never()).configure(any());
    }

    @Test
    public void testCreateWebDriver_withUnknownType_throwsIllegalArgumentException() {
        // Arrange
        WebDriverService service = createService(createConfig(DriverType.EDGE), null);
        // Act & Arrange
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("Unsupported driver EDGE", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenSPIWithoutFactoryClass_throwsIllegalArgumentException() {
        // Arrange
        DriverConfig config = createConfig(DriverType.SPI);
        when(mockFactory.getDriverType()).thenReturn(config.type);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        String expected = "driver.spi must be set when driver.type=SPI";
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenSPIIsBlank_throwsIllegalArgumentException() {
        // Arrange
        DriverConfig config = createConfig(DriverType.SPI);
        config.spi = Optional.of("    ");

        when(mockFactory.getDriverType()).thenReturn(config.type);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        String expected = "driver.spi must be set when driver.type=SPI";
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenSPIFactoryClassNotFound_throwsIllegalArgumentException() {
        // Arrange
        DriverConfig config = createConfig(DriverType.SPI);
        config.spi = Optional.of("MissingFactory");

        when(mockFactory.getDriverType()).thenReturn(config.type);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("No SPI factory found for MissingFactory", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_givenConfiguredSPIFactoryClass_returnsConfiguredWebDriver() {
        // Arrange
        @SuppressWarnings("unchecked")
        DriverFactory<MutableCapabilities> mockFirstSPIFactory = mock(DriverFactory.class);
        @SuppressWarnings("unchecked")
        DriverFactory<MutableCapabilities> mockSecondSPIFactory = mock(DriverFactory.class);

        DriverConfig config = createConfig(DriverType.SPI);
        config.spi = Optional.of(mockSecondSPIFactory.getClass().getName());
        stubFactory(mockFirstSPIFactory, config);
        stubFactory(mockSecondSPIFactory, config);

        WebDriverService service;
        service = createService(config, null, mockFirstSPIFactory, mockSecondSPIFactory);

        // Act
        WebDriverReference driverRef = service.createWebDriver();

        // Assert
        assertNotNull(driverRef);
        WebDriver driver = driverRef.get();

        assertSame(mockDriver, driver);
        verify(mockSecondSPIFactory).create();
        verify(mockSecondSPIFactory).configure(mockDriver);
        verify(mockFirstSPIFactory, never()).create();
        verify(mockFirstSPIFactory, never()).configure(any());
    }

    @Test
    public void testCreateWebDriver_givenMultipleFactoriesForSameType_usesLastRegisteredFactory() {
        // Arrange
        @SuppressWarnings("unchecked")
        DriverFactory<MutableCapabilities> mockFirstFactory = mock(DriverFactory.class);
        @SuppressWarnings("unchecked")
        DriverFactory<MutableCapabilities> mockSecondFactory = mock(DriverFactory.class);

        DriverConfig config = createConfig(DriverType.CHROME);

        stubFactory(mockFirstFactory, config);
        stubFactory(mockSecondFactory, config);

        WebDriverService service = createService(config, null, mockFirstFactory, mockSecondFactory);

        // Act
        WebDriverReference driverRef = service.createWebDriver();

        // Assert
        assertNotNull(driverRef);
        WebDriver driver = driverRef.get();

        assertSame(mockDriver, driver);
        verify(mockSecondFactory).create();
        verify(mockSecondFactory).configure(mockDriver);
        verify(mockFirstFactory, never()).create();
        verify(mockFirstFactory, never()).configure(any());
    }

    @Test
    public void testCreateWebDriver_givenGridUnsupported_throwsIllegalStateException() {
        // Arrange
        DriverConfig config = createConfig(DriverType.CHROME);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/wd/hub"));

        stubFactory(mockFactory, config);

        WebDriverService service = createService(config, grid, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalStateException.class, service::createWebDriver);
        String expected = "Grid execution not supported for driver CHROME";
        assertEquals(expected, e.getMessage());
    }

    @Test
    public void testCreateWebDriver_withInvalidGridURL_throwsIllegalArgumentException() {
        // Arrange
        DriverConfig config = createConfig(DriverType.CHROME);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("grid://admin:s3cr3t@selenium-hub.local:4444"));

        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.getCapabilities()).thenReturn(new MutableCapabilities());

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

        DriverConfig config = createConfig(DriverType.CHROME);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(mockURI);

        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.getCapabilities()).thenReturn(new MutableCapabilities());

        WebDriverService service = createService(config, grid, mockFactory);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, service::createWebDriver);
        assertEquals("Invalid Selenium Grid URL 'grid://<masked>@selenium-hub'", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_whenGridIsConfigured_createsRemoteWebDriverWithCapabilities() {
        // Arrange
        DriverConfig config = createConfig(DriverType.FIREFOX);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444"));
        grid.browserVersion = Optional.of("140.5.0");
        grid.platform = Optional.of("linux");
        grid.applicationName = Optional.of("suite");
        grid.capabilities = Map.of("custom", "value");

        MutableCapabilities caps = new MutableCapabilities();

        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.getCapabilities()).thenReturn(caps);

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class,
                (mock, context) -> capturedArguments.addAll(context.arguments()))) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            WebDriverReference driverRef = service.createWebDriver();

            // Assert
            assertNotNull(driverRef);
            WebDriver driver = driverRef.get();

            assertSame(mocked.constructed().getFirst(), driver);
            verify(mockFactory, never()).create();
            verify(mockFactory).configure(driver);
        }

        assertEquals(2, capturedArguments.size());

        MutableCapabilities capturedCaps = (MutableCapabilities) capturedArguments.get(1);
        assertNotEquals(caps, capturedCaps);

        assertEquals("firefox", capturedCaps.getCapability(CapabilityType.BROWSER_NAME));
        assertEquals("140.5.0", capturedCaps.getCapability(CapabilityType.BROWSER_VERSION));
        assertEquals(Platform.LINUX, capturedCaps.getCapability(CapabilityType.PLATFORM_NAME));
        assertEquals("suite", capturedCaps.getCapability("applicationName"));
        assertEquals("suite", capturedCaps.getCapability("se:applicationName"));
        assertEquals("value", capturedCaps.getCapability("custom"));
    }

    @Test
    public void testCreateWebDriver_givenGridWithSPIType_doesNotSetBrowserName() {
        // Arrange
        DriverConfig config = createConfig(DriverType.SPI);
        config.spi = Optional.of(mockFactory.getClass().getName());
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/"));

        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.getCapabilities()).thenReturn(new MutableCapabilities());

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class,
                (mock, context) -> capturedArguments.addAll(context.arguments()))) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            service.createWebDriver();
        }

        // Assert
        assertEquals(2, capturedArguments.size());

        MutableCapabilities capabilities = (MutableCapabilities) capturedArguments.get(1);
        assertNull(capabilities.getCapability(CapabilityType.BROWSER_NAME));
    }

    @Test
    public void testCreateWebDriver_whenOptionsHasBrowserName_preservesExistingBrowserName() {
        // Arrange
        DriverConfig config = createConfig(DriverType.EDGE);
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444"));

        MutableCapabilities caps = new MutableCapabilities();
        caps.setCapability(CapabilityType.BROWSER_NAME, "mock-browser");

        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.getCapabilities()).thenReturn(caps);

        List<Object> capturedArguments = new ArrayList<>();

        try (var mocked = mockConstruction(RemoteWebDriver.class,
                (mock, context) -> capturedArguments.addAll(context.arguments()))) {
            WebDriverService service = createService(config, grid, mockFactory);

            // Act
            service.createWebDriver();
        }

        // Assert
        assertEquals(2, capturedArguments.size());

        MutableCapabilities capturedCaps = (MutableCapabilities) capturedArguments.get(1);
        assertEquals("mock-browser", capturedCaps.getCapability(CapabilityType.BROWSER_NAME));
    }

    @Test
    public void testCreateWebDriver_whenGridURLHasCustomPath_logsInfoMessage() {
        // Arrange
        DriverConfig config = createConfig(DriverType.CHROME);
        config.spi = Optional.of(mockFactory.getClass().getName());
        GridConfig grid = createGridConfig();
        grid.uri = Optional.of(URI.create("https://localhost:4444/custom/grid"));

        when(mockFactory.getDriverType()).thenReturn(config.type);
        when(mockFactory.getCapabilities()).thenReturn(new MutableCapabilities());

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
        DriverConfig config = createConfig(DriverType.CHROME);
        RuntimeException configureException = new RuntimeException("configure");
        stubFactory(mockFactory, config);
        doThrow(configureException).when(mockFactory).configure(mockDriver);

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(RuntimeException.class, service::createWebDriver);
        assertSame(configureException, e);
        verify(mockDriver).quit();
    }

    @Test
    public void testCreateWebDriver_whenConfigureWebDriverAndQuitThrow_quitExceptionIsSuppressed() {
        // Arrange
        DriverConfig config = createConfig(DriverType.CHROME);
        RuntimeException quitException = new RuntimeException("quite");
        stubFactory(mockFactory, config);
        doThrow(new RuntimeException("configure")).when(mockFactory).configure(mockDriver);
        doThrow(quitException).when(mockDriver).quit();

        WebDriverService service = createService(config, null, mockFactory);

        // Act & Assert
        var e = assertThrows(RuntimeException.class, service::createWebDriver);
        assertEquals(1, e.getSuppressed().length);
        assertSame(quitException, e.getSuppressed()[0]);
    }

    @Test
    public void testDisposeWebDriver_withDriverRef_callsQuit() {
        // Arrange
        WebDriverService service = createService(createConfig(DriverType.CHROME), null);
        WebDriverReference mockRef = mock(WebDriverReference.class);
        when(mockRef.get()).thenReturn(mockDriver);
        // Act
        service.disposeWebDriver(mockRef);
        // Assert
        verify(mockDriver).quit();
        verify(mockRef).clear();
    }

    @Test
    public void testDisposeWebDriver_withWrappedDriver_callsWrappedDriverQuit() {
        // Arrange
        var settings = withSettings().extraInterfaces(WrapsDriver.class);
        WebDriver mockWrappedDriver = mock(WebDriver.class, settings);
        when(((WrapsDriver) mockWrappedDriver).getWrappedDriver()).thenReturn(mockDriver);

        WebDriverReference driverRef = new WebDriverReference(mockWrappedDriver);

        WebDriverService service = createService(createConfig(DriverType.CHROME), null);

        // Act
        service.disposeWebDriver(driverRef);

        // Assert
        verify(mockWrappedDriver).quit();
        verify(mockDriver, never()).quit();
    }

    @Test
    public void testDisposeWebDriver_withNullDriverRef_doesNothing() {
        WebDriverService service = createService(createConfig(DriverType.CHROME), null);
        service.disposeWebDriver(null);
    }

    @Test
    public void testDisposeWebDriver_withEmptyWebDriverReference_doesNothing() {
        // Arrange
        // We simulate the specific exception thrown by an empty reference.
        WebDriverReference mockRef = mock(WebDriverReference.class);
        var ise = new IllegalStateException("WebDriverReference not initialized");
        doThrow(ise).when(mockRef).get();

        WebDriverService service = createService(createConfig(DriverType.CHROME), null);

        // Act
        service.disposeWebDriver(mockRef);

        // Assert
        verify(mockDriver, never()).quit();
        verify(mockRef).clear();
    }

    @Test
    public void testDisposeWebDriver_whenDriverQuitThrows_doesNotPropagateException() {
        // Arrange
        WebDriverService service = createService(createConfig(DriverType.CHROME), null);
        WebDriverReference driverRef = new WebDriverReference(mockDriver);
        doThrow(new RuntimeException("quit")).when(mockDriver).quit();
        // Act
        service.disposeWebDriver(driverRef);
        // Assert
        verify(mockDriver).quit();
    }

    @Test
    public void testDisposeWebDriver_whenReferenceThrows_doesNotPropagateException() {
        // Arrange
        // Simulate the reference throwing on ALL method calls. This could happen if the reference
        // is broken in an unrecoverable way (e.g., the CDI proxy is failing).
        WebDriverReference mockRef = mock(WebDriverReference.class);
        RuntimeException re = new RuntimeException("Broken WebDriverReference");
        doThrow(re).when(mockRef).get();
        doThrow(re).when(mockRef).clear();

        WebDriverService service = createService(createConfig(DriverType.CHROME), null);

        // Act
        service.disposeWebDriver(mockRef);

        // Assert
        verify(mockRef).get();
        verify(mockDriver, never()).quit();
        verify(mockRef).clear();
    }

    private void stubFactory(DriverFactory<?> factory, DriverConfig config) {
        when(factory.getDriverType()).thenReturn(config.type);
        when(factory.create()).thenReturn(mockDriver);
    }

    private static DriverConfig createConfig(DriverType type) {
        DriverConfig config = new DriverConfig();

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

    private static WebDriverService createService(DriverConfig config, GridConfig grid,
            DriverFactory<?>... factories) {
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
            public Iterator<DriverFactory<?>> iterator() {
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
            public Instance<DriverFactory<?>> select(Annotation... annotations) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U extends DriverFactory<?>> Instance<U> select(Class<U> aClass,
                    Annotation... annotations) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <U extends DriverFactory<?>> Instance<U> select(TypeLiteral<U> typeLiteral,
                    Annotation... annotations) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void destroy(DriverFactory<?> factory) {
            }

            @Override
            public Handle<DriverFactory<?>> getHandle() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Iterable<? extends Handle<DriverFactory<?>>> handles() {
                throw new UnsupportedOperationException();
            }

            @Override
            public DriverFactory<?> get() {
                throw new UnsupportedOperationException();
            }
        });
    }
}
