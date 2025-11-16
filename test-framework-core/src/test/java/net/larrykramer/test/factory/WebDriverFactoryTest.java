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

package net.larrykramer.test.factory;

import java.net.URI;
import java.util.Optional;

import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import org.junit.Test;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.openqa.selenium.remote.CapabilityType.ACCEPT_INSECURE_CERTS;
import static org.openqa.selenium.remote.CapabilityType.PROXY;

public class WebDriverFactoryTest {
    @Test
    public void testGetType_givenSPIFactory_returnsSPITypeAndRejectsCanonicalName() {
        // Arrange
        SPIWebDriverFactory factory = new SPIWebDriverFactory(null, null, null);
        // Act
        WebDriverType result = factory.getType();
        // Assert
        assertEquals(WebDriverType.SPI, result);
        var e = assertThrows(UnsupportedOperationException.class, result::getCanonicalName);
        assertEquals("SPI does not have a canonical driver name", e.getMessage());
    }

    @Test
    public void testCreateWebDriver_withConfig_invokesSubclassAndReturnsWebDriver() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriverConfig config = createConfig();
        config.allowInsecureCerts = true;

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, mockDriver);

        // Act
        WebDriver result = factory.createWebDriver();

        // Assert
        assertSame(mockDriver, result);
        assertSame(options, factory.lastOptions);
        assertSame(config, factory.config);
        assertEquals(Boolean.TRUE, factory.lastOptions.getCapability(ACCEPT_INSECURE_CERTS));
        assertNull(factory.lastOptions.getCapability(PROXY));
    }

    @Test
    public void testCreateWebDriver_withNullOptions_passesNullToSubclass() {
        // Arrange
        WebDriver mockDriver = mock(WebDriver.class);
        WebDriverConfig config = createConfig();
        SPIWebDriverFactory factory = new SPIWebDriverFactory(null, config, mockDriver);
        // Act
        WebDriver result = factory.createWebDriver();
        // Assert
        assertSame(mockDriver, result);
        assertNull(factory.lastOptions);
        assertSame(config, factory.config);
    }

    @Test
    public void testGetOptions_givenDefaultImplementation_returnsNull() {
        // Arrange
        WebDriverFactory<MutableCapabilities> factory = new WebDriverFactory<>() {
            @Override
            public WebDriverType getType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public WebDriver createWebDriver() {
                throw new UnsupportedOperationException();
            }
        };
        // Act & Assert
        assertNull(factory.getOptions());
    }

    @Test
    public void testGetOptions_whenBuildOptionsReturnsNull_returnsNull() {
        // Arrange
        WebDriverConfig config = createConfig();
        SPIWebDriverFactory factory = new SPIWebDriverFactory(null, config, null);
        // Act
        MutableCapabilities result = factory.getOptions();
        // Assert
        assertNull(result);
        assertSame(config, factory.config);
    }

    @Test
    public void testGetOptions_givenAllowInsecureCertsIsFalse_setsCapabilityToFalse() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.allowInsecureCerts = false;

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        assertSame(options, result);
        assertEquals(Boolean.FALSE, result.getCapability(ACCEPT_INSECURE_CERTS));
        assertNull(result.getCapability(PROXY));
    }

    @Test
    public void testGetOptions_givenHttpProxyWithoutPort_setsHttpProxyOnly() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("http://proxy.example.com"));

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals(Proxy.ProxyType.MANUAL, proxy.getProxyType());
        assertEquals("proxy.example.com", proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
        assertNull(proxy.getSocksProxy());
    }

    @Test
    public void testGetOptions_givenHttpProxyWithPort_setsHttpProxyWithPort() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("//proxy.example.com:8181"));

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("proxy.example.com:8181", proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
    }

    @Test
    public void testGetOptions_givenHttpsProxy_setsHttpAndSslProxy() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("https://secure.example:8443"));

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("secure.example:8443", proxy.getHttpProxy());
        assertEquals("secure.example:8443", proxy.getSslProxy());
    }

    @Test
    public void testGetOptions_givenSocksProxy_setsVersion5AndProxy() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks://socks.example:1080"));

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("socks.example:1080", proxy.getSocksProxy());
        assertEquals(Integer.valueOf(5), proxy.getSocksVersion());
        assertNull(proxy.getHttpProxy());
        assertNull(proxy.getSslProxy());
    }

    @Test
    public void testGetOptions_givenSocks4Proxy_setsVersion4() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks4://legacy.example:9050"));

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("legacy.example:9050", proxy.getSocksProxy());
        assertEquals(Integer.valueOf(4), proxy.getSocksVersion());
    }

    @Test
    public void testGetOptions_givenSocksProxyWithCredentials_setsAuthentication() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks://auth.example:1080"));
        config.proxyUser = Optional.of(" user ");
        config.proxyPassword = Optional.of(" pass ");

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals(" user ", proxy.getSocksUsername());
        assertEquals(" pass ", proxy.getSocksPassword());
    }

    @Test
    public void testGetOptions_givenSocksProxyWithMissingPassword_skipsAuthentication() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("socks://auth.example:1080"));
        config.proxyUser = Optional.of(" user ");
        config.proxyPassword = Optional.of("");

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertNull(proxy.getSocksUsername());
        assertNull(proxy.getSocksPassword());
    }

    @Test
    public void testGetOptions_givenEmptyNonProxyHosts_doesNotSetNoProxy() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("https://proxy.example.com:8443"));
        config.nonProxyHosts = Optional.of("");

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertNull(proxy.getNoProxy());
    }

    @Test
    public void testGetOptions_givenNonProxyHosts_trimsAndSetsNoProxy() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("https://proxy.example.com:8443"));
        config.nonProxyHosts = Optional.of("  example.com || internal.local  ");

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        MutableCapabilities result = factory.getOptions();

        // Assert
        Proxy proxy = (Proxy) result.getCapability(PROXY);
        assertNotNull(proxy);
        assertEquals("example.com,internal.local", proxy.getNoProxy());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetOptions_givenProxyWithUnsupportedScheme_throwsIllegalArgumentException() {
        // Arrange
        MutableCapabilities options = new MutableCapabilities();
        WebDriverConfig config = createConfig();
        config.proxyAddress = Optional.of(URI.create("ftp://invalid.example:21"));

        SPIWebDriverFactory factory = new SPIWebDriverFactory(options, config, null);

        // Act
        factory.getOptions();
    }

    private static WebDriverConfig createConfig() {
        WebDriverConfig config = new WebDriverConfig();

        config.type = WebDriverType.SPI;
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

    private static class SPIWebDriverFactory extends WebDriverFactory<MutableCapabilities> {
        private final MutableCapabilities options;
        private final WebDriver driver;

        MutableCapabilities lastOptions;

        SPIWebDriverFactory(MutableCapabilities options, WebDriverConfig config, WebDriver driver) {
            this.options = options;
            this.config = config;
            this.driver = driver;
        }

        @Override
        public WebDriverType getType() {
            return WebDriverType.SPI;
        }

        @Override
        public WebDriver createWebDriver() {
            lastOptions = getOptions();
            return driver;
        }

        @Override
        public MutableCapabilities buildOptions() {
            return options;
        }
    }
}
