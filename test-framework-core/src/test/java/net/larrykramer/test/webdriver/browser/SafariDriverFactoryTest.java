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

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.config.DriverType;
import net.larrykramer.test.junit.rule.LogRule;
import net.larrykramer.test.util.OperatingSystem;
import net.larrykramer.test.webdriver.DriverFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setDriverConfig;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SafariDriverFactoryTest {
    @Rule
    public LogRule logRule = new LogRule(DriverFactory.class.getName());

    private SafariDriverFactory factory;

    @Before
    public void setUp() {
        factory = new SafariDriverFactory();
        setDriverConfig(factory, createConfig());
    }

    @Test
    public void testGetDriverType_whenCalled_returnsSafariDriverType() {
        assertEquals(DriverType.SAFARI, factory.getDriverType());
    }

    @Test
    public void testCreate_whenCalled_returnsWebDriverInstance() {
        try (var mockSafariDriver = mockConstruction(SafariDriver.class);
             var mockOperatingSystem = mockStatic(OperatingSystem.class)) {
            mockOperatingSystem.when(OperatingSystem::isMacOS).thenReturn(true);

            WebDriver driver = factory.create();

            assertEquals(1, mockSafariDriver.constructed().size());
            assertSame(mockSafariDriver.constructed().getFirst(), driver);
        }
    }

    @Test
    public void testCreate_givenNonMacOS_throwsUnsupportedOperationException() {
        try (var mockSafariDriver = mockConstruction(SafariDriver.class);
             var mockOperatingSystem = mockStatic(OperatingSystem.class)) {
            mockOperatingSystem.when(OperatingSystem::isMacOS).thenReturn(false);

            assertThrows(UnsupportedOperationException.class, () -> factory.create());

            assertEquals(0, mockSafariDriver.constructed().size());
        }
    }

    @Test
    public void testConfigure_withWindowSize_setsImplicitWaitAndWindowSize() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);

        DriverConfig config = createConfig();
        config.implicitTimeout = 350L;
        config.windowSize = Optional.of(new Dimension(1440, 900));
        setDriverConfig(factory, config);

        factory.configure(mockDriver);

        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(350L));
        verify(mockWindow).setSize(config.windowSize.get());
        verify(mockWindow, never()).maximize();
        verify(mockOptions, never()).deleteAllCookies();
    }

    @Test
    public void testConfigure_withMaximize_setsImplicitWaitAndMaximize() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);

        DriverConfig config = createConfig();
        config.implicitTimeout = 125L;
        config.windowSize = Optional.empty();
        config.maximize = true;
        setDriverConfig(factory, config);

        factory.configure(mockDriver);

        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(125L));
        verify(mockWindow).maximize();
        verify(mockWindow, never()).setSize(any(Dimension.class));
        verify(mockOptions, never()).deleteAllCookies();
    }

    @Test
    public void testConfigure_withoutWindowConfiguration_setsImplicitWaitOnly() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);

        factory.configure(mockDriver);

        verify(mockTimeouts).implicitlyWait(Duration.ZERO);
        verify(mockOptions, never()).deleteAllCookies();
    }

    @Test
    public void testConfigure_whenMaximizeThrows_doesNotPropagateException() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);
        doThrow(new WebDriverException("maximize")).when(mockWindow).maximize();

        DriverConfig config = createConfig();
        config.maximize = true;
        setDriverConfig(factory, config);

        factory.configure(mockDriver);

        verify(mockWindow).maximize();
    }

    @Test
    @LogRule.UsesLogger(level = "WARNING")
    public void testGetCapabilities_whenHeadlessRequested_logsMessageAndReturnsSafariOptions() {
        try (var mocked = mockStatic(OperatingSystem.class)) {
            mocked.when(OperatingSystem::isMacOS).thenReturn(true);

            DriverConfig config = createConfig();
            config.headless = true;
            setDriverConfig(factory, config);

            SafariOptions result = factory.getCapabilities();
            assertNotNull(result);

            final String expectedPrefix = "Headless mode in Safari not supported";
            assertTrue(logRule.getRecords().stream()
                    .filter(r -> r.getLevel() == Level.WARNING)
                    .anyMatch(r -> {
                        final String msg = r.getMessage();
                        return msg != null && msg.startsWith(expectedPrefix);
                    }));
        }
    }

    @Test
    public void testGetCapabilities_givenMacOSConfig_appliesCommonCapabilities() {
        try (var mocked = mockStatic(OperatingSystem.class)) {
            mocked.when(OperatingSystem::isMacOS).thenReturn(true);

            DriverConfig config = createConfig();
            config.allowInsecureCerts = true;
            config.proxyAddress = Optional.of(URI.create("https://localhost:8443"));
            setDriverConfig(factory, config);

            SafariOptions result = factory.getCapabilities();

            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.getCapability(CapabilityType.ACCEPT_INSECURE_CERTS));
            assertNotNull(result.getCapability(CapabilityType.PROXY));
        }
    }

    @Test
    public void testGetCapabilities_givenNonMacOS_allowsGetCapabilitiesButCreateFails() {
        try (var mocked = mockStatic(OperatingSystem.class)) {
            mocked.when(OperatingSystem::isMacOS).thenReturn(false);

            // We should be able to retrieve the Safari-specific capabilities on non-macOS, but
            // local creation *must* not be allowed on non-macOS.
            assertNotNull(factory.getCapabilities());
            assertThrows(UnsupportedOperationException.class, () -> factory.create());
        }
    }

    private static DriverConfig createConfig() {
        DriverConfig config = new DriverConfig();

        config.type = DriverType.SAFARI;
        config.spi = Optional.empty();

        config.headless = false;

        config.maximize = false;
        config.windowSize = Optional.empty();

        config.implicitTimeout = 0L;

        config.allowInsecureCerts = false;

        config.failOnCookieDeleteError = false;

        config.proxyAddress = Optional.empty();
        config.proxyUser = Optional.empty();
        config.proxyPassword = Optional.empty();
        config.nonProxyHosts = Optional.empty();

        return config;
    }
}
