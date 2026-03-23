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

package net.larrykramer.test.webdriver;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.config.DriverType;
import net.larrykramer.test.junit.rule.LogRule;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.openqa.selenium.remote.CapabilityType.ACCEPT_INSECURE_CERTS;
import static org.openqa.selenium.remote.CapabilityType.PROXY;

public class DriverFactoryTest {
    @Rule
    public LogRule logRule = new LogRule(DriverFactory.class.getName());

    @Test
    public void testGetDriverType_givenSPIFactory_returnsSPIDriverType() {
        final SPIDriverFactory factory = new SPIDriverFactory(null, null, null);
        assertEquals(DriverType.SPI, factory.getDriverType());
    }

    @Test
    public void testCreate_withConfig_invokesSubclassAndReturnsWebDriver() {
        MutableCapabilities capabilities = new MutableCapabilities();
        WebDriver mockDriver = mock(WebDriver.class);
        DriverConfig config = createConfig();
        config.allowInsecureCerts = true;

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, mockDriver);

        WebDriver result = factory.create();

        assertSame(mockDriver, result);
        assertSame(capabilities, factory.lastCapabilities);
        assertSame(config, factory.config);
        assertEquals(Boolean.TRUE, factory.lastCapabilities.getCapability(ACCEPT_INSECURE_CERTS));
        assertNull(factory.lastCapabilities.getCapability(PROXY));
    }

    @Test
    public void testCreate_withNullOptions_passesNullToSubclass() {
        WebDriver mockDriver = mock(WebDriver.class);
        DriverConfig config = createConfig();
        SPIDriverFactory factory = new SPIDriverFactory(null, config, mockDriver);

        WebDriver result = factory.create();

        assertSame(mockDriver, result);
        assertNull(factory.lastCapabilities);
        assertSame(config, factory.config);
    }

    @Test
    public void testConfigure_withPositiveImplicitTimeout_setsImplicitWait() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);

        DriverConfig config = createConfig();
        config.implicitTimeout = 500L;

        SPIDriverFactory factory = new SPIDriverFactory(null, config, null);

        factory.configure(mockDriver);

        verify(mockTimeouts).implicitlyWait(Duration.ofMillis(500L));
    }

    @Test
    public void testConfigure_withNegativeImplicitTimeout_doesNotSetImplicitWait() {
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        WebDriver.Timeouts mockTimeouts = mock(WebDriver.Timeouts.class);
        when(mockDriver.manage()).thenReturn(mockOptions);
        when(mockOptions.timeouts()).thenReturn(mockTimeouts);

        DriverConfig config = createConfig();
        config.implicitTimeout = -10L;

        SPIDriverFactory factory = new SPIDriverFactory(null, config, null);

        factory.configure(mockDriver);

        verify(mockTimeouts, never()).implicitlyWait(any(Duration.class));
    }

    @Test
    public void testGetCapabilities_givenDefaultImplementation_returnsNull() {
        DriverFactory<MutableCapabilities> factory = new DriverFactory<>() {
            @Override
            public DriverType getDriverType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public WebDriver create() {
                throw new UnsupportedOperationException();
            }
        };
        assertNull(factory.getCapabilities());
    }

    @Test
    public void testGetCapabilities_givenNullCapabilities_returnsNull() {
        DriverConfig config = createConfig();
        SPIDriverFactory factory = new SPIDriverFactory(null, config, null);
        MutableCapabilities result = factory.getCapabilities();
        assertNull(result);
        assertSame(config, factory.config);
    }

    @Test
    public void testGetCapabilities_givenAllowInsecureCertsIsFalse_setsCapabilityToFalse() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.allowInsecureCerts = false;

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        MutableCapabilities result = factory.getCapabilities();

        assertSame(capabilities, result);
        assertEquals(Boolean.FALSE, result.getCapability(ACCEPT_INSECURE_CERTS));
        assertNull(result.getCapability(PROXY));
    }

    @Test
    public void testGetCapabilities_givenHttpProxyWithoutPort_setsHttpProxyOnly() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("http://proxy.example.com"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals(Proxy.ProxyType.MANUAL, proxy.getProxyType());
        assertEquals("proxy.example.com", proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
        assertNull(proxy.getSocksProxy());
    }

    @Test
    public void testGetCapabilities_givenHttpProxyWithPort_setsHttpProxyWithPort() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("//proxy.example.com:8181"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("proxy.example.com:8181", proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
    }

    @Test
    public void testGetCapabilities_givenIPv6HttpProxy_setsHttpProxyWithBrackets() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("http://[fe80::1]:8080"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("[fe80::1]:8080", proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
        assertNull(proxy.getSocksProxy());
    }

    @Test
    public void testGetCapabilities_givenHttpsProxy_setsHttpAndSslProxy() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("https://secure.example:8443"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("secure.example:8443", proxy.getHttpProxy());
        assertEquals("secure.example:8443", proxy.getSslProxy());
    }

    @Test
    public void testGetCapabilities_givenSocksProxy_setsVersion5AndProxy() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks://socks.example:1080"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("socks.example:1080", proxy.getSocksProxy());
        assertEquals(Integer.valueOf(5), proxy.getSocksVersion());
        assertNull(proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
    }

    @Test
    public void testGetCapabilities_givenSocks5Proxy_setsVersion5AndProxy() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("SOCKS5://socks.example:1080"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("socks.example:1080", proxy.getSocksProxy());
        assertEquals(Integer.valueOf(5), proxy.getSocksVersion());
        assertNull(proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
    }

    @Test
    public void testGetCapabilities_givenSocks4Proxy_setsVersion4() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks4://legacy.example:9050"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("legacy.example:9050", proxy.getSocksProxy());
        assertEquals(Integer.valueOf(4), proxy.getSocksVersion());
    }

    @Test
    public void testGetCapabilities_givenSocksProxyWithCredentials_setsAuthentication() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks://auth.example:1080"));
        config.proxyUser = Optional.of(" user ");
        config.proxyPassword = Optional.of(" pass ");

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals(" user ", proxy.getSocksUsername());
        assertEquals(" pass ", proxy.getSocksPassword());
    }

    @Test
    public void testGetCapabilities_givenSocksProxyWithMissingPassword_skipsAuthentication() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks://auth.example:1080"));
        config.proxyUser = Optional.of(" user ");
        config.proxyPassword = Optional.of("");

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertNull(proxy.getSocksUsername());
        assertNull(proxy.getSocksPassword());
    }

    @Test
    public void testGetCapabilities_givenEmptyNonProxyHosts_doesNotSetNoProxy() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("https://proxy.example.com:8443"));
        config.nonProxyHosts = Optional.of("");

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertNull(proxy.getNoProxy());
    }

    @Test
    public void testGetCapabilities_givenNonProxyHosts_trimsAndSetsNoProxy() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("https://proxy.example.com:8443"));
        config.nonProxyHosts = Optional.of(" |example.com|| internal.local | ");

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        Proxy proxy = (Proxy) factory.getCapabilities().getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("example.com,internal.local", proxy.getNoProxy());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCapabilities_givenUnsupportedProxyScheme_throwsIllegalArgumentException() {
        MutableCapabilities capabilities = new MutableCapabilities();
        DriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("ftp://invalid.example:21"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);
        factory.getCapabilities();
    }

    @Test
    public void testApplyCommonCapabilities_givenExistingCapabilities_overwritesValues() {
        MutableCapabilities capabilities = new MutableCapabilities();
        Proxy proxy = new Proxy();
        proxy.setHttpProxy("old.proxy.example:1111");
        capabilities.setCapability(PROXY, proxy);
        capabilities.setCapability(ACCEPT_INSECURE_CERTS, true);

        DriverConfig config = createConfig();
        config.allowInsecureCerts = false;
        config.proxyAddress = Optional.of(URI.create("http://new.proxy.example:8080"));

        SPIDriverFactory factory = new SPIDriverFactory(capabilities, config, null);

        MutableCapabilities result = factory.getCapabilities();

        assertEquals(Boolean.FALSE, result.getCapability(ACCEPT_INSECURE_CERTS));

        Proxy resultProxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(resultProxy);
        assertEquals("new.proxy.example:8080", resultProxy.getHttpProxy());
    }

    @Test
    public void testDeleteAllCookies_whenSuccessful_invokesOptionsDeleteAllCookies() {
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        SPIDriverFactory factory = new SPIDriverFactory(null, createConfig(), null);

        factory.deleteAllCookies(mockOptions);

        verify(mockOptions).deleteAllCookies();
    }

    @Test
    @LogRule.UsesLogger(level = "WARNING")
    public void testDeleteAllCookies_whenDeleteThrows_doesNotPropagateException() {
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        doThrow(new WebDriverException("deleteAllCookies")).when(mockOptions).deleteAllCookies();

        SPIDriverFactory factory = new SPIDriverFactory(null, createConfig(), null);

        factory.deleteAllCookies(mockOptions);

        verify(mockOptions).deleteAllCookies();
        assertTrue(logRule.getRecords().stream()
                .filter(r -> r.getLevel() == Level.WARNING)
                .anyMatch(r -> "Unable to delete all cookies".equals(r.getMessage())));
    }

    @Test
    public void testDeleteAllCookies_withFailOnCookieDeleteErrorTrue_propagatesException() {
        WebDriver.Options mockOptions = mock(WebDriver.Options.class);
        doThrow(new WebDriverException("deleteAllCookies")).when(mockOptions).deleteAllCookies();

        DriverConfig config = createConfig();
        config.failOnCookieDeleteError = true;

        SPIDriverFactory factory = new SPIDriverFactory(null, config, null);

        assertThrows(WebDriverException.class, () -> factory.deleteAllCookies(mockOptions));
        verify(mockOptions).deleteAllCookies();
    }

    private static DriverConfig createConfig() {
        DriverConfig config = new DriverConfig();

        config.type = DriverType.SPI;
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

    private static class SPIDriverFactory extends DriverFactory<MutableCapabilities> {
        private final MutableCapabilities capabilities;
        private final WebDriver driver;

        MutableCapabilities lastCapabilities;

        SPIDriverFactory(MutableCapabilities caps, DriverConfig config, WebDriver driver) {
            this.capabilities = caps;
            this.config = config;
            this.driver = driver;
        }

        @Override
        public DriverType getDriverType() {
            return DriverType.SPI;
        }

        @Override
        public WebDriver create() {
            lastCapabilities = getCapabilities();
            return driver;
        }

        @Override
        public void configure(WebDriver driver) {
            setImplicitWait(driver.manage());
        }

        @Override
        public MutableCapabilities getCapabilities() {
            // We must simulate the behavior of a real factory.
            if (capabilities != null) {
                applyCommonCapabilities(capabilities);
            }
            return capabilities;
        }
    }
}
