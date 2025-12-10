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

import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setInjectedConfigField;
import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setWebDriverConfig;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ChromeDriverFactoryTest {
    private ChromeDriverFactory factory;

    @Before
    public void setUp() {
        factory = new ChromeDriverFactory();

        setWebDriverConfig(factory, createConfig());

        ChromiumConfig config = new ChromiumConfig();
        config.executable = Optional.empty();
        config.arguments = List.of();
        setChromeConfig(config);
    }

    @Test
    public void testGetDriverType_whenCalled_returnsChromeDriverType() {
        // Act
        DriverType result = factory.getDriverType();
        // Assert
        assertEquals(DriverType.CHROME, result);
        assertEquals("chrome", result.getCanonicalName());
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
        setWebDriverConfig(factory, config);

        // Act
        factory.configure(mockDriver);

        // Assert
        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(400L));
        verify(mockOptions).deleteAllCookies();
        verify(mockOptions, never()).window();
    }

    @Test
    public void testGetCapabilities_whenCalled_appliesCommonCapabilities() {
        // Arrange
        DriverConfig config = createConfig();
        config.allowInsecureCerts = true;
        config.proxyAddress = Optional.of(URI.create("https://localhost:8443"));
        setWebDriverConfig(factory, config);

        // Act
        ChromeOptions result = factory.getCapabilities();

        // Assert
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.getCapability(CapabilityType.ACCEPT_INSECURE_CERTS));
        assertNotNull(result.getCapability(CapabilityType.PROXY));
    }

    @Test
    public void testGetCapabilities_withChromeAndGlobalOptions_appliesExpectedChromeOptions() {
        // Arrange
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        config.maximize = true;
        chromium.executable = Optional.of("/custom/chrome");
        chromium.arguments = List.of("--foo", "--bar");
        setWebDriverConfig(factory, config);
        setChromeConfig(chromium);

        // Act
        ChromeOptions options = factory.getCapabilities();

        // Assert
        assertNotNull(options);

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
        setWebDriverConfig(factory, config);
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
        setWebDriverConfig(factory, config);

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
        setWebDriverConfig(factory, config);
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
        setWebDriverConfig(factory, config);

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
        setWebDriverConfig(factory, config);
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
        setWebDriverConfig(factory, config);
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
        setWebDriverConfig(factory, config);

        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, () -> factory.getCapabilities());
        assertEquals("Window width and height must be greater than 0: -800x-600", e.getMessage());
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

        config.proxyAddress = Optional.empty();
        config.proxyUser = Optional.empty();
        config.proxyPassword = Optional.empty();
        config.nonProxyHosts = Optional.empty();

        return config;
    }

    private void setChromeConfig(ChromiumConfig config) {
        setInjectedConfigField(factory, "chromeConfig", config);
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
