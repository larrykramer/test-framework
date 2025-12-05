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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.larrykramer.test.config.FirefoxConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import static net.larrykramer.test.webdriver.FactoryTestHelper.setInjectedConfigField;
import static net.larrykramer.test.webdriver.FactoryTestHelper.setWebDriverConfig;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FirefoxDriverFactoryTest {
    private FirefoxDriverFactory factory;

    @Before
    public void setUp() {
        factory = new FirefoxDriverFactory();

        setWebDriverConfig(factory, createConfig());

        FirefoxConfig firefox = new FirefoxConfig();
        firefox.userPrefs = Map.of();
        firefox.executable = Optional.empty();
        setFirefoxConfig(firefox);
    }

    @Test
    public void testGetDriverType_whenCalled_returnsFirefoxWebDriverType() {
        // Act
        WebDriverType result = factory.getType();
        // Assert
        assertEquals(WebDriverType.FIREFOX, result);
        assertEquals("firefox", result.getCanonicalName());
    }

    @Test
    public void testCreateWebDriver_whenCalled_returnsDriverInstance() {
        // Arrange
        try (var mocked = mockConstruction(FirefoxDriver.class)) {
            // Act
            WebDriver driver = factory.createWebDriver();
            // Assert
            assertEquals(1, mocked.constructed().size());
            assertSame(mocked.constructed().getFirst(), driver);
        }
    }

    @Test
    public void testConfigureWebDriver_withWindowSize_appliesFirefoxSpecificConfiguration() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);

        WebDriverConfig config = createConfig();
        config.implicitTimeout = 150L;
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.of(new Dimension(1280, 720));
        setWebDriverConfig(factory, config);

        // Act
        factory.configureWebDriver(mockDriver);

        // Assert
        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(150L));
        verify(mockOptions).deleteAllCookies();
        verify(mockWindow).setSize(config.windowSize.get());
        verify(mockWindow, never()).maximize();
    }

    @Test
    public void testConfigureWebDriver_withMaximizeFalseAndNoWindowSize_doesNotChangeWindowSize() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);

        WebDriverConfig config = createConfig();
        config.maximize = false;
        config.windowSize = Optional.empty();
        setWebDriverConfig(factory, config);

        // Act
        factory.configureWebDriver(mockDriver);

        // Assert
        verify(mockWindow, never()).maximize();
        verify(mockWindow, never()).setSize(any(Dimension.class));
    }

    @Test
    public void testConfigureWebDriver_whenMaximizeTrueAndHeadlessModeIsDisabled_maximizesWindow() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);

        WebDriverConfig config = createConfig();
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.empty();
        setWebDriverConfig(factory, config);

        // Act
        factory.configureWebDriver(mockDriver);

        // Assert
        verify(mockWindow).maximize();
        verify(mockWindow, never()).setSize(any(Dimension.class));
    }

    @Test
    public void testConfigureWebDriver_whenHeadlessModeIsEnabled_doesNotMaximizeWindow() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);

        WebDriverConfig config = createConfig();
        config.headless = true;
        config.maximize = true;
        config.windowSize = Optional.empty();
        setWebDriverConfig(factory, config);

        // Act
        factory.configureWebDriver(mockDriver);

        // Assert
        verify(mockWindow, never()).maximize();
        verify(mockWindow, never()).setSize(any(Dimension.class));
    }

    @Test(expected = WebDriverException.class)
    public void testConfigureWebDriver_whenMaximizeThrows_propagatesException() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        WebDriver.Window mockWindow = mock(WebDriver.Window.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);
        when(mockOptions.window()).thenReturn(mockWindow);
        doThrow(new WebDriverException("maximize")).when(mockWindow).maximize();

        WebDriverConfig config = createConfig();
        config.maximize = true;
        setWebDriverConfig(factory, config);

        // Act & Assert
        // Exception is propagated.
        factory.configureWebDriver(mockDriver);
    }

    @Test
    public void testBuildOptions_whenHeadlessModeIsEnabled_addsHeadlessArgument() {
        // Arrange
        WebDriverConfig config = createConfig();
        config.headless = true;
        setWebDriverConfig(factory, config);

        // Act
        FirefoxOptions options = factory.buildOptions();

        // Assert
        assertNotNull(options);
        assertTrue(extractArguments(options).contains("-headless"));
    }

    @Test
    public void testBuildOptions_whenUserPrefsAreProvided_setsProfileWithConvertedValues() {
        // Arrange
        double overflow = Integer.MAX_VALUE + 1.0d;
        double underflow = Integer.MIN_VALUE - 1.0d;
        //@formatter:off
        Map<String, Object> userPrefs = Map.ofEntries(
                // Standard types
                Map.entry("pref.boolean.true", Boolean.TRUE),
                Map.entry("pref.boolean.false", Boolean.FALSE),
                Map.entry("pref.string", "  keep  "),
                Map.entry("pref.blank", ""),
                Map.entry("pref.integer", 42),
                // Edge cases
                Map.entry("pref.double.whole", 42.0d),                     // should -> Integer
                Map.entry("pref.double.fraction", 3.14d),                  // should -> String
                Map.entry("pref.integer.max", (double) Integer.MAX_VALUE), // should -> Integer
                Map.entry("pref.integer.min", (double) Integer.MIN_VALUE), // should -> Integer
                Map.entry("pref.overflow", overflow),                      // should -> String
                Map.entry("pref.underflow", underflow),                    // should -> String
                Map.entry("pref.nan", Double.NaN),                         // should -> String
                Map.entry("pref.inf.pos", Double.POSITIVE_INFINITY),       // should -> String
                Map.entry("pref.inf.neg", Double.NEGATIVE_INFINITY)        // should -> String
        );
        //@formatter:on
        FirefoxConfig firefox = new FirefoxConfig();
        firefox.userPrefs = userPrefs;
        firefox.executable = Optional.empty();
        setFirefoxConfig(firefox);

        // Act
        FirefoxOptions result = factory.buildOptions();

        // Assert
        assertNotNull(result);

        Object rawPrefs = extractFirefoxOptions(result).get("prefs");
        assertTrue(rawPrefs instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) rawPrefs;
        // Standard types
        assertEquals(Boolean.TRUE, prefs.get("pref.boolean.true"));
        assertEquals(Boolean.FALSE, prefs.get("pref.boolean.false"));
        assertEquals("  keep  ", prefs.get("pref.string"));
        assertEquals("", prefs.get("pref.blank"));
        assertEquals(42, prefs.get("pref.integer"));
        // Whole number Double -> Integer
        assertEquals("42.0 should become Integer 42", 42, prefs.get("pref.double.whole"));
        assertEquals(Integer.MAX_VALUE, prefs.get("pref.integer.max"));
        assertEquals(Integer.MIN_VALUE, prefs.get("pref.integer.min"));
        // Fractional/Overflow/Special Double -> String
        assertEquals("3.14", prefs.get("pref.double.fraction"));
        assertEquals(String.valueOf(overflow), prefs.get("pref.overflow"));
        assertEquals(String.valueOf(underflow), prefs.get("pref.underflow"));
        assertEquals("NaN", prefs.get("pref.nan"));
        assertEquals("Infinity", prefs.get("pref.inf.pos"));
        assertEquals("-Infinity", prefs.get("pref.inf.neg"));
    }

    @Test
    public void testBuildOptions_withExecutable_setsBinaryPath() {
        // Arrange
        FirefoxConfig firefox = new FirefoxConfig();
        firefox.userPrefs = Map.of();
        firefox.executable = Optional.of("/custom/firefox");
        setFirefoxConfig(firefox);

        // Act
        FirefoxOptions result = factory.buildOptions();

        // Assert
        assertNotNull(result);
        assertEquals("/custom/firefox", extractFirefoxOptions(result).get("binary"));
    }

    private static WebDriverConfig createConfig() {
        WebDriverConfig config = new WebDriverConfig();

        config.type = WebDriverType.FIREFOX;
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

    private void setFirefoxConfig(FirefoxConfig config) {
        setInjectedConfigField(factory, "firefoxConfig", config);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFirefoxOptions(FirefoxOptions options) {
        Object raw = options.asMap().get("moz:firefoxOptions");
        return (raw instanceof Map) ? (Map<String, Object>) raw : Map.of();
    }

    private List<String> extractArguments(FirefoxOptions options) {
        Object args = extractFirefoxOptions(options).get("args");
        if (args instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) args;
            return list;
        }
        return List.of();
    }
}
