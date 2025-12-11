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
import java.util.Locale;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.config.DriverType;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.*;

import static org.openqa.selenium.remote.CapabilityType.ACCEPT_INSECURE_CERTS;
import static org.openqa.selenium.remote.CapabilityType.PROXY;

/**
 * Base abstraction for all driver factories that are capable of building a
 * {@code WebDriver} instance from a {@code DriverConfig}.
 * <p>
 * The factory coordinates four key responsibilities:
 * <ul>
 * <li>Translating a {@code DriverConfig} into driver-specific
 *   {@code MutableCapabilities}
 * <li>Enforcing configuration that is common to every WebDriver, e.g. insecure
 *   certificate support and proxy handling
 * <li>Producing a fully constructed {@code WebDriver} instance for the
 *   requested {@code DriverType}
 * <li>Applying runtime configuration to the created {@code WebDriver}, such as
 *   implicit wait timeouts
 * </ul>
 *
 * All concrete subclasses of this factory must be CDI-managed beans annotated
 * with {@code @ApplicationScoped}. Implementations are required to override
 * {@link #create()} and construct the appropriate {@code WebDriver}. SPI
 * {@code DriverType} factories may optionally override
 * {@link #getCapabilities()} when the SPI needs to create a
 * {@code MutableCapabilities} object before delegating to an external
 * provider.
 *
 * @param <T> the concrete {@code MutableCapabilities} subtype used by the
 *            {@code WebDriver}
 *
 * @see DriverConfig
 * @see DriverType
 */
public abstract class DriverFactory<T extends MutableCapabilities> {
    protected static final Logger LOGGER = Logger.getLogger(DriverFactory.class.getName());

    @Inject
    @ConfigProperties
    protected DriverConfig config;

    /**
     * Returns the driver type associated with the factory.
     *
     * @return the {@code DriverType} that this factory supports
     */
    public abstract DriverType getDriverType();

    /**
     * Produces a driver-specific {@code WebDriver} using the capabilities
     * derived from {@link #getCapabilities()}.
     *
     * @implSpec
     * Implementations must always create and return a <em>new</em>, unmanaged
     * {@code WebDriver} instance on each invocation. The returned
     * {@code WebDriver} must not be cached, pooled, or shared between calls,
     * and its lifecycle must not be managed by the factory (for example,
     * implementations must not call {@link WebDriver#quit()} on the returned
     * instance).
     * <p>
     * Ownership of the returned {@code WebDriver}, including responsibility
     * for eventually invoking {@link WebDriver#quit()}, is transferred to
     * {@link net.larrykramer.test.service.WebDriverService WebDriverService},
     * which assumes full control of the {@code WebDriver}'s lifecycle.
     *
     * @return a new {@code WebDriver} instance; never a reused or shared
     *         instance
     */
    public abstract WebDriver create();

    /**
     * Applies post-construction configuration to the given {@code WebDriver}.
     * <p>
     * The default implementation sets the
     * {@linkplain DriverConfig#implicitTimeout implicit wait timeout}. Negative
     * timeout values are ignored and no implicit wait is applied.
     * <p>
     * This method is invoked by
     * {@link net.larrykramer.test.service.WebDriverService WebDriverService}
     * immediately after a {@code WebDriver} has been created (either locally
     * or remotely). Subclasses are expected to override this method to apply
     * additional WebDriver-specific configuration, for example:
     * <ul>
     * <li>Deleting cookies
     * <li>Setting an initial window size
     * <li>Maximizing or otherwise manipulating the browser window
     * </ul>
     *
     * Implementations should choose an appropriate failure policy for these
     * operations based on the capabilities and quirks of the underlying
     * WebDriver:
     * <ul>
     * <li>For WebDrivers where configuration operations are known to be
     *   unreliable or unsupported, it may be preferable to catch
     *   {@code WebDriverException}, log the failure, and continue.
     * <li>For WebDrivers where such configuration operations are considered
     *   essential to test correctness, implementations may allow exceptions to
     *   propagate in order to fail fast when the environment is misconfigured.
     * </ul>
     *
     * @param driver the {@code WebDriver} instance to configure
     */
    public void configure(WebDriver driver) {
        Duration timeout = Duration.ofMillis(config.implicitTimeout);
        if (timeout.isNegative()) {
            LOGGER.log(Level.WARNING, "Ignoring negative implicit timeout {0}", timeout);
            return;
        }
        driver.manage().timeouts().implicitlyWait(timeout);
    }

    /**
     * Returns the driver-specific capabilities.
     * <p>
     * The default implementation returns {@code null}, indicating that
     * {@code MutableCapabilities} construction is not handled. Factories
     * registered for non-SPI {@code DriverType}s are expected to override
     * this method. Factories registered for SPI {@code DriverType}s may
     * override this method when they need to create driver-specific
     * capabilities.
     *
     * @return the driver-specific capabilities, or {@code null}
     */
    public T getCapabilities() {
        return null;
    }

    /**
     * Applies common capabilities to the given capabilities object.
     *
     * @param capabilities the capabilities object to augment
     * @implNote This method sets {@code ACCEPT_INSECURE_CERTS} and
     *           {@code PROXY} based on the global configuration, overwriting
     *           existing values for those keys if present.
     */
    protected void applyCommonCapabilities(MutableCapabilities capabilities) {
        capabilities.setCapability(ACCEPT_INSECURE_CERTS, config.allowInsecureCerts);
        addProxy(capabilities);
    }

    /**
     * Deletes all cookies for the given {@code WebDriver} in a lenient manner.
     * <p>
     * By default, this method logs and ignores {@code WebDriverException} to
     * avoid failing WebDriver creation when a particular implementation does
     * not support cookie deletion reliably (e.g. some remote or vendor-specific
     * WebDrivers).
     *
     * @param options the {@link WebDriver.Options} for the driver; must not be
     *                {@code null}
     */
    protected void deleteAllCookies(WebDriver.Options options) {
        try {
            options.deleteAllCookies();
        } catch (WebDriverException e) {
            LOGGER.log(Level.WARNING, "Unable to delete all cookies");
            LOGGER.throwing(getClass().getName(), "deleteAllCookies", e);
        }
    }

    /**
     * Configures proxy settings on the given capabilities object using the
     * global {@code DriverConfig}.
     * <p>
     * If the proxy address is undefined in the configuration, the given
     * capabilities object remains unchanged. Otherwise, it sets the
     * {@code PROXY} capability according to the configured proxy address and
     * any optional exclusions or authentication details.
     *
     * @param capabilities the capabilities object to update with proxy
     *                     settings; must not be {@code null}
     * @throws IllegalArgumentException if the configured proxy scheme is not
     *                                  supported
     * @implNote Any existing {@code PROXY} capability on the capabilities
     *           object will be replaced.
     */
    protected void addProxy(MutableCapabilities capabilities) {
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

        capabilities.setCapability(PROXY, proxy);
    }
}
