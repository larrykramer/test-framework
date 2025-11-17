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

package net.larrykramer.test.factory.browser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.larrykramer.test.config.FirefoxConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.factory.FactoryTestHelper;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockConstruction;

public class FirefoxDriverFactoryTest {
    private FirefoxDriverFactory factory;

    @Before
    public void setUp() {
        factory = new FirefoxDriverFactory();

        FactoryTestHelper.setWebDriverConfig(factory, createConfig());

        FirefoxConfig firefox = new FirefoxConfig();
        firefox.userPrefs = Map.of();
        firefox.executable = Optional.empty();
        setInjectedConfigField(firefox);
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
    public void testBuildOptions_whenHeadlessModeIsEnabled_addsHeadlessArgument() {
        // Arrange
        WebDriverConfig config = createConfig();
        config.headless = true;
        FactoryTestHelper.setWebDriverConfig(factory, config);

        // Act
        FirefoxOptions options = factory.buildOptions();

        // Assert
        assertNotNull(options);
        assertTrue(extractArguments(options).contains("-headless"));
    }

    @Test
    public void testBuildOptions_whenUserPrefsAreProvided_setsProfileWithConvertedValues() {
        // Arrange
        //@formatter:off
        Map<String, Object> userPrefs = Map.of(
                "pref.blank", "",
                "pref.boolean.true", Boolean.TRUE,
                "pref.boolean.false", Boolean.FALSE,
                "pref.integer", 42,
                "pref.double", 3.14,
                "pref.string", "  keep  ",
                "pref.integer.one", 1
        );
        //@formatter:on
        FirefoxConfig firefox = new FirefoxConfig();
        firefox.userPrefs = userPrefs;
        firefox.executable = Optional.empty();
        setInjectedConfigField(firefox);

        // Act
        FirefoxOptions result = factory.buildOptions();

        // Assert
        assertNotNull(result);

        Object rawPrefs = extractFirefoxOptions(result).get("prefs");
        assertTrue(rawPrefs instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) rawPrefs;
        assertEquals("", prefs.get("pref.blank"));
        assertEquals(Boolean.TRUE, prefs.get("pref.boolean.true"));
        assertEquals(Boolean.FALSE, prefs.get("pref.boolean.false"));
        assertEquals(42, prefs.get("pref.integer"));
        assertEquals(3.14, ((Number) prefs.get("pref.double")).doubleValue(), 0.000001);
        assertEquals("  keep  ", prefs.get("pref.string"));
        assertEquals(1, prefs.get("pref.integer.one"));
    }

    @Test
    public void testBuildOptions_withExecutable_setsBinaryPath() {
        // Arrange
        FirefoxConfig firefox = new FirefoxConfig();
        firefox.userPrefs = Map.of();
        firefox.executable = Optional.of("/custom/firefox");
        setInjectedConfigField(firefox);

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

    private void setInjectedConfigField(FirefoxConfig config) {
        FactoryTestHelper.setInjectedConfigField(factory, "firefoxConfig", config);
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
