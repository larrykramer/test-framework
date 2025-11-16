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

package net.larrykramer.test.config;

import java.net.URI;
import java.util.Optional;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.openqa.selenium.Dimension;

/**
 * Central configuration class for WebDriver settings.
 * <p>
 * This class aggregates general WebDriver properties such as the driver type,
 * headless mode properties, window dimensions, timeouts, and proxy
 * configuration.
 * <p>
 * All properties within this class are prefixed with {@code webdriver}.
 */
@ConfigProperties(prefix = "webdriver")
@Dependent
public class WebDriverConfig {
    /**
     * Specifies the target driver for test execution.
     * <p>
     * This property maps to {@code webdriver.type}.
     */
    public WebDriverType type;

    /**
     * Defines the fully qualified class name of a custom WebDriver
     * implementation. This allows for the integration of a driver not natively
     * supported, via the Service Provider Interface (SPI).
     * <p>
     * This property maps to {@code webdriver.spi}.
     */
    public Optional<String> spi;

    /**
     * Determines whether the WebDriver should operate in headless mode. When
     * {@code true}, the graphical user interface will not be rendered.
     * Defaults to {@code false}.
     * <p>
     * This property maps to {@code webdriver.headless}.
     */
    @ConfigProperty(defaultValue = "false")
    public boolean headless;

    /**
     * Specifies whether the window should be maximized upon startup. This
     * behavior is contingent on the WebDriver and driver's capabilities.
     * Defaults to {@code false}.
     * <p>
     * This property maps to {@code webdriver.maximize}.
     */
    @ConfigProperty(defaultValue = "false")
    public boolean maximize;

    /**
     * Defines a specific dimension (width and height) for the window. This
     * setting overrides the {@link #maximize} property if both are specified.
     * <p>
     * This property maps to {@code webdriver.window-size}.
     */
    public Optional<Dimension> windowSize;

    /**
     * Specifies the duration for the implicit wait in milliseconds. This
     * setting instructs the WebDriver to wait for a certain amount of time when
     * trying to find an element if it is not immediately present on the page.
     * <p>
     * It is generally recommended to use explicit waits over implicit waits to
     * avoid unpredictable test execution delays. A value of zero (the default)
     * disables the implicit wait entirely. For example, a value of {@code 5000}
     * sets a 5-second timeout.
     * Defaults to {@code 0}.
     * <p>
     * This property maps to {@code webdriver.implicit-timeout}
     */
    @ConfigProperty(defaultValue = "0")
    public long implicitTimeout;

    /**
     * Controls whether the WebDriver session will accept expired or invalid TLS
     * certificates. Setting this to {@code true} may be necessary for test
     * environments with self-signed certificates.
     * Defaults to {@code false}.
     * <p>
     * This property maps to {@code webdriver.allow-insecure-certs}.
     */
    @ConfigProperty(defaultValue = "false")
    public boolean allowInsecureCerts;

    /**
     * The URI of the proxy server to be used for all WebDriver-initiated
     * network requests.
     * <p>
     * This property maps to {@code webdriver.proxy-address}.
     */
    public Optional<URI> proxyAddress;

    /**
     * The username for authenticating with a SOCKS proxy server, if required.
     * <p>
     * This property maps to {@code webdriver.proxy-user}.
     */
    public Optional<String> proxyUser;

    /**
     * The password for authenticating with a SOCKS proxy server, if required.
     * <p>
     * This property maps to {@code webdriver.proxy-password}.
     */
    public Optional<String> proxyPassword;

    /**
     * Defines hosts that should be reached directly, bypassing the configured
     * proxy. The format is a {@code |}-separated list of hostnames or IP
     * addresses.
     * Examples include {@code localhost|10.0.0.1}.
     * <p>
     * This property maps to {@code webdriver.non-proxy-hosts}.
     */
    public Optional<String> nonProxyHosts;
}
