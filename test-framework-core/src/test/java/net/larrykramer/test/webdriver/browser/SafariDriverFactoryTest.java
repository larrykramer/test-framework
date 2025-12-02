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

package net.larrykramer.test.webdriver.browser;

import java.util.Optional;
import java.util.logging.*;

import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.webdriver.FactoryTestHelper;
import net.larrykramer.test.util.OperatingSystem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SafariDriverFactoryTest {
    private SafariDriverFactory factory;

    @Before
    public void setUp() {
        factory = new SafariDriverFactory();
        FactoryTestHelper.setWebDriverConfig(factory, createConfig());
    }

    @Test
    public void testGetType_whenCalled_returnsSafariWebDriverType() {
        // Act
        WebDriverType result = factory.getType();
        // Assert
        assertEquals(WebDriverType.SAFARI, result);
        assertEquals("safari", result.getCanonicalName());
    }

    @Test
    public void testCreateWebDriver_whenCalled_returnsWebDriverInstance() {
        // Arrange
        try (var mockSafariDriver = mockConstruction(SafariDriver.class);
             var mockOperatingSystem = mockStatic(OperatingSystem.class)) {
            mockOperatingSystem.when(OperatingSystem::isMacOS).thenReturn(true);

            // Act
            WebDriver driver = factory.createWebDriver();

            // Assert
            assertEquals(1, mockSafariDriver.constructed().size());
            assertSame(mockSafariDriver.constructed().getFirst(), driver);
        }
    }

    @Test
    public void testCreateWebDriver_givenNonMacOS_throwsUnsupportedOperationException() {
        // Arrange
        try (var mockSafariDriver = mockConstruction(SafariDriver.class);
             var mockOperatingSystem = mockStatic(OperatingSystem.class)) {
            mockOperatingSystem.when(OperatingSystem::isMacOS).thenReturn(false);

            // Act & Assert
            var expected  = UnsupportedOperationException.class;
            var e = assertThrows(expected, () -> factory.createWebDriver());
            assertEquals("Safari local execution not supported on this platform", e.getMessage());

            assertEquals(0, mockSafariDriver.constructed().size());
        }
    }

    @Test
    public void testBuildOptions_whenHeadlessRequested_logsMessageAndReturnsSafariOptions() {
        // Arrange
        Logger logger = Logger.getLogger(SafariDriverFactory.class.getName());
        Level originalLevel = logger.getLevel();
        boolean useParentHandlers = logger.getUseParentHandlers();

        Handler mockLogHandler = mock(Handler.class);
        ArgumentCaptor<LogRecord> logRecordCaptor = ArgumentCaptor.forClass(LogRecord.class);

        logger.addHandler(mockLogHandler);
        logger.setLevel(Level.WARNING);
        logger.setUseParentHandlers(false);

        try (var mocked = mockStatic(OperatingSystem.class)) {
            mocked.when(OperatingSystem::isMacOS).thenReturn(true);
            when(mockLogHandler.isLoggable(any(LogRecord.class))).thenReturn(true);

            WebDriverConfig config = createConfig();
            config.headless = true;
            FactoryTestHelper.setWebDriverConfig(factory, config);

            // Act
            SafariOptions result = factory.buildOptions();

            // Assert
            assertNotNull(result);

            verify(mockLogHandler).publish(logRecordCaptor.capture());

            LogRecord record = logRecordCaptor.getValue();
            assertEquals(Level.WARNING, record.getLevel());
            assertTrue(record.getMessage().contains("Headless mode in Safari not supported"));
        } finally {
            logger.removeHandler(mockLogHandler);
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(useParentHandlers);
        }
    }

    @Test
    public void testGetOptions_givenMacOSConfig_appliesCommonCapabilities() {
        // Arrange
        try (var mocked = mockStatic(OperatingSystem.class)) {
            mocked.when(OperatingSystem::isMacOS).thenReturn(true);

            WebDriverConfig config = createConfig();
            config.allowInsecureCerts = true;
            FactoryTestHelper.setWebDriverConfig(factory, config);

            // Act
            SafariOptions result = factory.getOptions();

            // Assert
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.getCapability(CapabilityType.ACCEPT_INSECURE_CERTS));
        }
    }

    private static WebDriverConfig createConfig() {
        WebDriverConfig config = new WebDriverConfig();

        config.type = WebDriverType.SAFARI;
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
}
