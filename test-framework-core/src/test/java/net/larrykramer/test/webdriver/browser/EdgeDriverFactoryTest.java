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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.remote.CapabilityType;

import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setConfigField;
import static net.larrykramer.test.webdriver.DriverFactoryTestHelper.setDriverConfig;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class EdgeDriverFactoryTest {
    private EdgeDriverFactory factory;

    @Before
    public void setUp() {
        factory = new EdgeDriverFactory();

        setDriverConfig(factory, createConfig());

        ChromiumConfig chromium = new ChromiumConfig();
        chromium.executable = Optional.empty();
        chromium.arguments = List.of();
        setEdgeConfig(chromium);
    }

    @Test
    public void testGetDriverType_whenCalled_returnsEdgeDriverType() {
        assertEquals(DriverType.EDGE, factory.getDriverType());
    }

    @Test
    public void testCreate_whenCalled_returnsWebDriverInstance() {
        try (var mocked = mockConstruction(EdgeDriver.class)) {
            WebDriver driver = factory.create();
            assertEquals(1, mocked.constructed().size());
            assertSame(mocked.constructed().getFirst(), driver);
        }
    }

    @Test
    public void testConfigure_whenCalled_appliesCommonAndChromiumConfiguration() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);

        DriverConfig config = createConfig();
        config.implicitTimeout = 400L;
        setDriverConfig(factory, config);

        factory.configure(mockDriver);

        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(400L));
        verify(mockOptions).deleteAllCookies();
        verify(mockOptions, never()).window();
    }

    @Test
    public void testGetCapabilities_givenEdgeAndGlobalConfig_appliesExpectedCapabilities() {
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        config.maximize = false;
        config.allowInsecureCerts = true;
        config.proxyAddress = Optional.of(URI.create("https://localhost:8443"));
        chromium.executable = Optional.of("/custom/edge");
        chromium.arguments = List.of("--foo", "--bar");
        setDriverConfig(factory, config);
        setEdgeConfig(chromium);

        EdgeOptions options = factory.getCapabilities();

        assertNotNull(options);

        assertEquals(Boolean.TRUE, options.getCapability(CapabilityType.ACCEPT_INSECURE_CERTS));
        assertNotNull(options.getCapability(CapabilityType.PROXY));

        assertEquals("/custom/edge", extractEdgeOptions(options).get("binary"));

        List<String> args = extractArguments(options);
        assertEquals(3, args.size());
        assertTrue(args.contains("--foo"));
        assertTrue(args.contains("--bar"));
        assertTrue(args.contains("--headless"));
    }

    @Test
    public void testGetCapabilities_withMaximizeTrueAndWindowSize_addsOnlyWindowSizeArgument() {
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.of(new Dimension(3840, 2160));
        chromium.executable = Optional.empty();
        chromium.arguments = List.of();
        setDriverConfig(factory, config);
        setEdgeConfig(chromium);

        EdgeOptions options = factory.getCapabilities();

        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(1, args.size());
        assertTrue(args.contains("--window-size=3840,2160"));
        assertFalse(args.contains("--start-maximized"));
    }

    @Test
    public void testGetCapabilities_withMaximizeTrueAndNoWindowSize_addsStartMaximizedArgument() {
        DriverConfig config = createConfig();
        config.headless = false;
        config.maximize = true;
        config.windowSize = Optional.empty();
        setDriverConfig(factory, config);

        EdgeOptions options = factory.getCapabilities();

        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(1, args.size());
        assertEquals("--start-maximized", args.getFirst());
    }

    @Test
    public void testGetCapabilities_withStartMaximizedArgument_doesNotDuplicateArgument() {
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = false;
        config.maximize = true;
        chromium.executable = Optional.empty();
        chromium.arguments = List.of("--start-maximized", "--foo");
        setDriverConfig(factory, config);
        setEdgeConfig(chromium);

        EdgeOptions options = factory.getCapabilities();

        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertEquals(2, args.size());
        assertTrue(args.contains("--foo"));
        assertTrue(args.contains("--start-maximized"));
    }

    @Test
    public void testGetCapabilities_withWindowSize_addsWindowSizeArgument() {
        DriverConfig config = createConfig();
        config.headless = false;
        config.windowSize = Optional.of(new Dimension(800, 600));
        setDriverConfig(factory, config);

        EdgeOptions options = factory.getCapabilities();

        assertNotNull(options);

        List<String> args = extractArguments(options);
        assertTrue(args.contains("--window-size=800,600"));
        assertFalse(args.contains("--start-maximized"));
    }

    @Test
    public void testGetCapabilities_withHeadlessArgument_doesNotDuplicateHeadlessOption() {
        DriverConfig config = createConfig();
        ChromiumConfig chromium = new ChromiumConfig();
        config.headless = true;
        chromium.executable = Optional.empty();
        chromium.arguments = List.of("--headless=new", "--foo");
        setDriverConfig(factory, config);
        setEdgeConfig(chromium);

        EdgeOptions options = factory.getCapabilities();

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
    public void testGetCapabilities_withZeroHeightWindowSize_throwsIllegalArgumentException() {
        DriverConfig config = createConfig();
        config.windowSize = Optional.of(new Dimension(800, 0));
        setDriverConfig(factory, config);

        factory.getCapabilities();
    }

    private static DriverConfig createConfig() {
        DriverConfig config = new DriverConfig();

        config.type = DriverType.EDGE;
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

    private void setEdgeConfig(ChromiumConfig config) {
        setConfigField(factory, "edgeConfig", config);
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
