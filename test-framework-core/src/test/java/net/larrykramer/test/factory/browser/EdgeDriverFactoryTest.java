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

import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.factory.FactoryTestHelper;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockConstruction;

public class EdgeDriverFactoryTest {
    private EdgeDriverFactory factory;

    @Before
    public void setUp() {
        factory = new EdgeDriverFactory();

        FactoryTestHelper.setWebDriverConfig(factory, createConfig());

        ChromiumConfig chromium = new ChromiumConfig();
        chromium.executable = Optional.empty();
        chromium.arguments = List.of();
        setInjectedConfigField(chromium);
    }

    @Test
    public void testGetDriverType_whenCalled_returnsEdgeWebDriverType() {
        // Act
        WebDriverType result = factory.getType();
        // Assert
        assertEquals(WebDriverType.EDGE, result);
        assertEquals("MicrosoftEdge", result.getCanonicalName());
    }

    @Test
    public void testCreateWebDriver_whenCalled_returnsWebDriverInstance() {
        // Arrange
        try (var mocked = mockConstruction(EdgeDriver.class)) {
            // Act
            WebDriver driver = factory.createWebDriver();
            // Assert
            assertEquals(1, mocked.constructed().size());
            assertSame(mocked.constructed().getFirst(), driver);
        }
    }

    @Test
    public void testBuildOptions_withEdgeAndGlobalOptions_appliesExpectedEdgeOptions() {
        // Arrange
        WebDriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        config.maximize = false;
        chromium.executable = Optional.of("/custom/edge");
        chromium.arguments = List.of("--foo", "--bar");
        FactoryTestHelper.setWebDriverConfig(factory, config);
        setInjectedConfigField(chromium);

        // Act
        EdgeOptions options = factory.buildOptions();

        // Assert
        assertNotNull(options);

        assertEquals("/custom/edge", extractEdgeOptions(options).get("binary"));

        List<String> args = extractArguments(options);
        assertEquals(3, args.size());
        assertEquals("--foo", args.get(0));
        assertEquals("--bar", args.get(1));
        assertEquals("--headless", args.get(2));
    }

    @Test
    public void testBuildOptions_withMaximizeTrue_doesNotAddWindowSizeArgument() {
        // Arrange
        WebDriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.of(new Dimension(3840, 2160));
        chromium.executable = Optional.empty();
        chromium.arguments = List.of();
        FactoryTestHelper.setWebDriverConfig(factory, config);
        setInjectedConfigField(chromium);

        // Act
        EdgeOptions options = factory.buildOptions();

        // Assert
        assertNotNull(options);
        assertTrue(extractArguments(options).isEmpty());
    }

    @Test
    public void testBuildOptions_withWindowSize_addsWindowSizeArgument() {
        // Arrange
        WebDriverConfig config = createConfig();
        config.headless = false;
        config.windowSize = Optional.of(new Dimension(800, 600));
        FactoryTestHelper.setWebDriverConfig(factory, config);

        // Act
        EdgeOptions options = factory.buildOptions();

        // Assert
        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertTrue(args.contains("--window-size=800,600"));
    }

    @Test
    public void testBuildOptions_withHeadlessArgument_doesNotDuplicateHeadlessOption() {
        // Arrange
        WebDriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        chromium.executable = Optional.empty();
        chromium.arguments = List.of("--headless=new", "--foo");
        FactoryTestHelper.setWebDriverConfig(factory, config);
        setInjectedConfigField(chromium);

        // Act
        EdgeOptions options = factory.buildOptions();

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

    @Test(expected = IllegalArgumentException.class)
    public void testBuildOptions_withZeroHeightWindowSize_throwsIllegalArgumentException() {
        // Arrange
        WebDriverConfig config = createConfig();
        config.windowSize = Optional.of(new Dimension(800, 0));
        FactoryTestHelper.setWebDriverConfig(factory, config);
        // Act
        factory.buildOptions();
    }

    private static WebDriverConfig createConfig() {
        WebDriverConfig config = new WebDriverConfig();

        config.type = WebDriverType.EDGE;
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

    private void setInjectedConfigField(ChromiumConfig config) {
        FactoryTestHelper.setInjectedConfigField(factory, "edgeConfig", config);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractEdgeOptions(EdgeOptions options) {
        Object raw = options.asMap().get("ms:edgeOptions");
        return (raw instanceof Map) ? (Map<String, Object>) raw : Map.of();
    }

    private List<String> extractArguments(EdgeOptions options) {
        Object args = extractEdgeOptions(options).get("args");
        if (args instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) args;
            return list;
        }
        return List.of();
    }
}
