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
import java.util.Locale;
import java.util.StringJoiner;

import jakarta.inject.Inject;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;

import static org.openqa.selenium.remote.CapabilityType.ACCEPT_INSECURE_CERTS;
import static org.openqa.selenium.remote.CapabilityType.PROXY;

/**
 * Base abstraction for all Selenium WebDriver factories that are capable of
 * building a driver instance from a {@code WebDriverConfig}.
 * <p>
 * The factory coordinates three key responsibilities:
 * <ul>
 * <li>Translating a {@link WebDriverConfig} into driver-specific
 *   {@link MutableCapabilities}
 * <li>Enforcing configuration that is common to every driver, e.g. insecure
 *   certificate support and proxy handling
 * <li>Producing a fully constructed {@code WebDriver} instance for the
 *   requested {@code WebDriverType}
 * </ul>
 *
 * All concrete subclasses of this factory must be CDI-managed beans annotated
 * with {@code @ApplicationScoped}. Implementations are required to override
 * {@link #createWebDriver()} and construct the appropriate WebDriver. SPI
 * {@link WebDriverType} factories may optionally override
 * {@link #buildOptions()} when the SPI needs to create a
 * {@link MutableCapabilities} instance before delegating to an external
 * provider.
 *
 * @param <T> the concrete {@link MutableCapabilities} subtype used by the
 *           WebDriver
 *
 * @see WebDriverConfig
 * @see WebDriverType
 */
public abstract class WebDriverFactory<T extends MutableCapabilities> {
    @Inject
    @ConfigProperties
    protected WebDriverConfig config;

    /**
     * Returns the driver type associated with the factory.
     *
     * @return the {@link WebDriverType} that this factory supports
     */
    public abstract WebDriverType getType();

    /**
     * Produces a driver-specific {@link WebDriver} using the capabilities
     * derived from {@link #getOptions()}.
     *
     * @return a WebDriver instance
     */
    public abstract WebDriver createWebDriver();

    /**
     * Returns the WebDriver options (capabilities) after augmenting them with
     * common options.
     *
     * @return the WebDriver options, or {@code null} if the WebDriver
     *         implementation does not require options
     */
    public T getOptions() {
        T options = buildOptions();
        if (options == null) {
            return null;
        }

        // Apply common options.
        options.setCapability(ACCEPT_INSECURE_CERTS, config.allowInsecureCerts);
        addProxy(options);

        return options;
    }

    /**
     * Builds the initial WebDriver options (capabilities).
     * <p>
     * The default implementation returns {@code null}, indicating that option
     * construction is not handled. Factories registered for non-SPI
     * {@code WebDriverType}s are expected to override this method. Factories
     * registered for SPI {@code WebDriverType}s may override this method when
     * they need to create WebDriver-specific options.
     *
     * @return the WebDriver-specific options, or {@code null} if the factory
     *         does not create options
     */
    protected T buildOptions() {
        return null;
    }

    private void addProxy(T options) {
        if (config.proxyAddress.isEmpty()) {
            return;
        }

        Proxy proxy = new Proxy();
        proxy.setProxyType(Proxy.ProxyType.MANUAL);

        if (config.nonProxyHosts.isPresent()) {
            StringJoiner sj = new StringJoiner(",");
            for (String host : config.nonProxyHosts.get().split("\\|")) {
                if (!host.isBlank()) {
                    sj.add(host.strip());
                }
            }
            if (sj.length() > 0) {
                proxy.setNoProxy(sj.toString());
            }
        }

        URI proxyAddress = config.proxyAddress.get();
        String proxyHost = proxyAddress.getHost();
        if (proxyAddress.getPort() >= 0) {
            proxyHost += ":" + proxyAddress.getPort();
        }

        String scheme = proxyAddress.getScheme();
        if (scheme == null || scheme.isEmpty() || scheme.equalsIgnoreCase("http")) {
            proxy.setHttpProxy(proxyHost);
        } else if (scheme.equalsIgnoreCase("https")) {
            proxy.setHttpProxy(proxyHost);
            proxy.setSslProxy(proxyHost);
        } else if (scheme.toLowerCase(Locale.ROOT).startsWith("socks")) {
            proxy.setSocksProxy(proxyHost);
            proxy.setSocksVersion(scheme.endsWith("4") ? 4 : 5);
            // Use SOCKS proxy server authentication if both the username and password are provided.
            // Note: Username and password are not trimmed as leading and trailing spaces might
            // be an intentional part of the username or password.
            if (config.proxyUser.isPresent() && config.proxyPassword.isPresent()) {
                String proxyUser = config.proxyUser.get();
                String proxyPassword = config.proxyPassword.get();
                if (!proxyUser.isEmpty() && !proxyPassword.isEmpty()) {
                    proxy.setSocksUsername(proxyUser);
                    proxy.setSocksPassword(proxyPassword);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported proxy scheme: " + scheme);
        }

        options.setCapability(PROXY, proxy);
    }
}
