/*
 * Copyright (c) 2025-2026 Larry Kramer
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
 * <h2>Implementation requirements</h2>
 * All concrete subclasses of this factory must be CDI-managed beans annotated
 * with {@code @ApplicationScoped}. Implementations are required to override
 * {@link #create()} and construct the appropriate {@code WebDriver}. SPI
 * factories may optionally override {@link #getCapabilities()} when the SPI
 * factory needs to create a {@code MutableCapabilities} object before
 * delegating to an external provider.
 * <p>
 * Subclasses must invoke {@link #applyCommonCapabilities(MutableCapabilities)}
 * within their {@link #getCapabilities()} implementation if they wish to
 * support global proxy or SSL configuration. Alternatively, subclasses may
 * invoke {@link #addProxy(MutableCapabilities)} directly if only global proxy
 * configuration is desired.
 *
 * <h2>SPI factory configuration</h2>
 * When an SPI factory is used, the framework selects a specific
 * {@code DriverFactory} implementation using the {@code driver.spi}
 * configuration property (the fully qualified class name of the factory
 * implementation).
 * <p>
 * SPI factories can define their own configuration namespaces using
 * {@code @ConfigProperties}. A recommended convention is to place SPI
 * factory-specific settings under {@code driver.spi.<id>.*} (for example,
 * {@code driver.spi.appium.*}) to keep custom settings grouped and distinct
 * from built-in driver configuration keys.
 *
 * @param <T> the concrete {@code MutableCapabilities} subtype used by the
 *            {@code WebDriver}
 *
 * @see DriverConfig
 * @see DriverType
 */
public abstract class DriverFactory<T extends MutableCapabilities> {
    /**
     * Shared, class-scoped logger for the {@code DriverFactory} base type and
     * all concrete factory implementations.
     */
    protected static final Logger LOGGER = Logger.getLogger(DriverFactory.class.getName());

    /**
     * The globally configured driver properties injected via MicroProfile
     * Config.
     * <p>
     * This configuration is shared across factories and is used to derive
     * common capabilities (such as insecure certificate acceptance and proxy
     * settings) and to apply runtime WebDriver configuration (such as implicit
     * wait timeouts) after driver creation.
     *
     * @implNote This field is populated by CDI using
     *           {@link ConfigProperties @ConfigProperties}.
     */
    @Inject
    @ConfigProperties
    protected DriverConfig config;

    /**
     * {@return the driver type associated with the factory}
     */
    public abstract DriverType getDriverType();

    /**
     * Produces a driver-specific {@code WebDriver} using the capabilities
     * derived from {@link #getCapabilities()}.
     *
     * @implSpec
     * Overriding implementations <i>must</i> create and return a <i>new</i>,
     * unmanaged {@code WebDriver} instance on each invocation. In all cases,
     * overriding implementations <i>must not</i> assume ownership of the
     * WebDriver; they <i>must not</i> call {@code WebDriver.quit()} on that
     * instance, and <i>must not</i> retain, cache, store, or otherwise publish
     * the returned {@code WebDriver} for later reuse.
     * <p>
     * Ownership of the returned {@code WebDriver}, including responsibility
     * for eventually invoking {@code WebDriver.quit()}, is transferred to
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
     * This method is invoked by
     * {@link net.larrykramer.test.service.WebDriverService WebDriverService}
     * immediately after a {@code WebDriver} has been created (either locally or
     * remotely) and before it is exposed for scenario use.
     *
     * @implSpec
     * Overriding implementations <em>may</em> perform any driver-specific
     * initialization required by the factory (for example, deleting cookies,
     * setting the initial window size, maximizing the window, or registering
     * wrappers).
     * <p>
     * Overriding implementations <em>must not</em> assume ownership of the
     * {@code WebDriver}: they <em>must not</em> call {@code driver.quit()} and
     * they <em>must not</em> retain, cache, or otherwise publish the
     * {@code WebDriver} reference for later use outside the scenario lifecycle.
     * <p>
     * If an implementation wishes to set the globally configured implicit-wait
     * timeout, it <em>must</em> do so by calling
     * {@link #setImplicitWait(WebDriver.Options)}.
     *
     * @implNote
     * The base implementation is a no-op.
     *
     * @param driver the {@code WebDriver} instance to configure
     */
    public void configure(WebDriver driver) {
        /* no-op */
    }

    /**
     * Returns the driver-specific capabilities.
     * <p>
     * The default implementation returns {@code null}, indicating that
     * {@code MutableCapabilities} construction is not handled. Factories
     * registered for non-SPI {@code DriverType}s are expected to override this
     * method. Factories registered for SPI {@code DriverType}s may override
     * this method when they need to create driver-specific capabilities.
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
     * @see #addProxy(MutableCapabilities)
     * @implNote The base implementation currently sets
     *           {@code ACCEPT_INSECURE_CERTS} and applies proxy configuration
     *           (when configured) based on the global configuration. As an
     *           implementation detail, existing values for those keys may be
     *           overwritten.
     */
    protected void applyCommonCapabilities(MutableCapabilities capabilities) {
        capabilities.setCapability(ACCEPT_INSECURE_CERTS, config.allowInsecureCerts);
        addProxy(capabilities);
    }

    /**
     * Applies the globally configured implicit-wait timeout to the given
     * {@code WebDriver}.
     * <p>
     * The {@link DriverConfig#implicitTimeout implicit wait timeout} is
     * taken from {@link #config} and is applied to the driver's implicit-wait
     * setting. If the configured timeout is negative, this method logs a
     * warning and leaves the driver's implicit-wait setting unchanged.
     *
     * @implNote
     * Implicit waits affect all subsequent element-finding operations executed
     * by the driver and may interact with explicit waits. This helper exists so
     * factory implementations can consistently apply the framework's global
     * implicit-wait behavior from {@link #configure(WebDriver)} without
     * duplicating configuration logic.
     *
     * @param options the {@code WebDriver.Options} to configure
     */
    protected void setImplicitWait(WebDriver.Options options) {
        Duration timeout = Duration.ofMillis(config.implicitTimeout);
        if (timeout.isNegative()) {
            LOGGER.log(Level.WARNING, "Ignoring negative implicit timeout {0}", timeout);
            return;
        }
        options.timeouts().implicitlyWait(timeout);
    }

    /**
     * Deletes all cookies for the given {@code WebDriver}.
     * <p>
     * If cookie deletion fails with a {@link WebDriverException}, the behavior
     * depends on {@code driver.fail-on-cookie-delete-error}:
     * <ul>
     * <li>When {@code false} (default), the failure is treated as non-fatal:
     *   the error is logged and the session continues.</li>
     * <li>When {@code true}, the failure is treated as fatal and the
     *   {@code WebDriverException} is propagated.</li>
     * </ul>
     *
     * This is intended to accommodate environments where cookie deletion may
     * be unreliable for certain WebDriver implementations while still allowing
     * strict isolation when required.
     *
     * @param options the {@code WebDriver.Options} for the driver
     * @throws WebDriverException if cookie deletion fails and
     *                            {@code driver.fail-on-cookie-delete-error} is
     *                            true
     */
    protected void deleteAllCookies(WebDriver.Options options) throws WebDriverException {
        try {
            options.deleteAllCookies();
        } catch (WebDriverException e) {
            if (config.failOnCookieDeleteError) {
                throw e;
            }
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
     * <p>
     * When a proxy address is configured, any existing {@code PROXY} capability
     * on the capabilities object is replaced.
     *
     * @param capabilities the capabilities object to update with proxy settings
     * @throws IllegalArgumentException if the configured proxy scheme is not
     *                                  supported
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
