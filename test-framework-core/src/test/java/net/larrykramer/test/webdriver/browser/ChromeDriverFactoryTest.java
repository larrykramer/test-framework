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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.config.DriverType;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;

import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setConfigField;
import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setDriverConfig;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ChromeDriverFactoryTest {
    private ChromeDriverFactory factory;

    @Before
    public void setUp() {
        factory = new ChromeDriverFactory();

        setDriverConfig(factory, createConfig());

        ChromiumConfig config = new ChromiumConfig();
        config.executable = Optional.empty();
        config.arguments = List.of();
        setChromeConfig(config);
    }

    @Test
    public void testGetDriverType_whenCalled_returnsChromeDriverType() {
        assertEquals(DriverType.CHROME, factory.getDriverType());
    }

    @Test
    public void testCreate_whenCalled_returnsWebDriverInstance() {
        // Arrange
        try (var mocked = mockConstruction(ChromeDriver.class)) {
            // Act
            WebDriver driver = factory.create();
            // Assert
            assertEquals(1, mocked.constructed().size());
            assertSame(mocked.constructed().getFirst(), driver);
        }
    }

    @Test
    public void testConfigure_whenCalled_appliesCommonAndChromiumConfiguration() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);

        DriverConfig config = createConfig();
        config.implicitTimeout = 400L;
        setDriverConfig(factory, config);

        // Act
        factory.configure(mockDriver);

        // Assert
        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(400L));
        verify(mockOptions).deleteAllCookies();
        verify(mockOptions, never()).window();
    }

    @Test
    public void testGetCapabilities_givenChromeAndGlobalConfig_appliesExpectedCapabilities() {
        // Arrange
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        config.maximize = true;
        config.allowInsecureCerts = true;
        config.proxyAddress = Optional.of(URI.create("https://localhost:8443"));
        chromium.executable = Optional.of("/custom/chrome");
        chromium.arguments = List.of("--foo", "--bar");
        setDriverConfig(factory, config);
        setChromeConfig(chromium);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);

        assertEquals(Boolean.TRUE, options.getCapability(CapabilityType.ACCEPT_INSECURE_CERTS));
        assertNotNull(options.getCapability(CapabilityType.PROXY));

        assertEquals("/custom/chrome", extractChromeOptions(options).get("binary"));

        List<String> args = extractArguments(options);
        assertEquals(3, args.size());
        assertTrue(args.contains("--foo"));
        assertTrue(args.contains("--bar"));
        assertTrue(args.contains("--headless"));
    }

    @Test
    public void testGetCapabilities_withMaximizeTrueAndWindowSize_addsOnlyWindowSizeArgument() {
        // Arrange
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.of(new Dimension(1600, 900));
        chromium.executable = Optional.empty();
        chromium.arguments = List.of();
        setDriverConfig(factory, config);
        setChromeConfig(chromium);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(1, args.size());
        assertTrue(args.contains("--window-size=1600,900"));
        assertFalse(args.contains("--start-maximized"));
    }

    @Test
    public void testGetCapabilities_withMaximizeTrueAndNoWindowSize_addsStartMaximizedArgument() {
        // Arrange
        DriverConfig config = createConfig();
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.empty();
        setDriverConfig(factory, config);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(1, args.size());
        assertEquals("--start-maximized", args.getFirst());
    }

    @Test
    public void testGetCapabilities_withStartMaximizedArgument_doesNotDuplicateArgument() {
        // Arrange
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = false;
        config.maximize = true;
        chromium.executable = Optional.empty();
        chromium.arguments = List.of("--start-maximized", "--foo");
        setDriverConfig(factory, config);
        setChromeConfig(chromium);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(2, args.size());
        assertTrue(args.contains("--foo"));
        assertTrue(args.contains("--start-maximized"));
    }

    @Test
    public void testGetCapabilities_withWindowSize_addsWindowSizeArgument() {
        // Arrange
        DriverConfig config = createConfig();
        config.headless = false;
        config.windowSize = Optional.of(new Dimension(1024, 768));
        setDriverConfig(factory, config);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);
        List<String> args = extractArguments(options);
        assertTrue(args.contains("--window-size=1024,768"));
    }

    @Test
    public void testGetCapabilities_withHeadlessArgument_doesNotDuplicateHeadlessOption() {
        // Arrange
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        chromium.executable = Optional.empty();
        chromium.arguments = List.of("--headless", "--foo");
        setDriverConfig(factory, config);
        setChromeConfig(chromium);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(2, args.size());
        assertTrue(args.contains("--foo"));

        int headlessArgCount = 0;
        for (String arg : args) {
            if (arg.equals("--headless") || arg.startsWith("--headless=")) {
                headlessArgCount++;
            }
        }
        assertEquals(1, headlessArgCount);
    }

    @Test
    public void testGetCapabilities_withWindowSizeArgument_doesNotDuplicateWindowSizeOption() {
        // Arrange
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.maximize = false;
        config.windowSize = Optional.of(new Dimension(3840, 2160));
        chromium.executable = Optional.empty();
        chromium.arguments = List.of("--window-size=800,600", "--foo");
        setDriverConfig(factory, config);
        setChromeConfig(chromium);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);
        List<String> args = extractArguments(options);
        assertEquals(2, args.size());
        assertTrue(args.contains("--foo"));
        assertTrue(args.contains("--window-size=800,600"));
        assertFalse(args.contains("--window-size=3840,2160"));
    }

    @Test
    public void testGetCapabilities_givenNegativeWindowSize_throwsIllegalArgumentException() {
        // Arrange
        DriverConfig config = createConfig();
        config.windowSize = Optional.of(new Dimension(-800, -600));
        setDriverConfig(factory, config);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, () -> factory.getCapabilities());
        assertTrue(e.getMessage().contains("-800"));
        assertTrue(e.getMessage().contains("-600"));
    }

    private static DriverConfig createConfig() {
        DriverConfig config = new DriverConfig();

        config.type = DriverType.CHROME;
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

    private void setChromeConfig(ChromiumConfig config) {
        setConfigField(factory, "chromeConfig", config);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractChromeOptions(ChromeOptions options) {
        Object raw = options.asMap().get("goog:chromeOptions");
        return (raw instanceof Map) ? (Map<String, Object>) raw : Map.of();
    }

    private List<String> extractArguments(ChromeOptions options) {
        Object args = extractChromeOptions(options).get("args");
        if (args instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) args;
            return list;
        }
        return List.of();
    }
}
