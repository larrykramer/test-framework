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

import java.util.logging.Level;

import jakarta.enterprise.context.ApplicationScoped;
import net.larrykramer.test.config.DriverType;
import net.larrykramer.test.webdriver.DriverFactory;
import net.larrykramer.test.util.OperatingSystem;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

/**
 * Factory that constructs Safari {@code WebDriver} instances.
 * <p>
 * Local Safari automation is supported exclusively on macOS hosts. Attempting
 * to create a local Safari driver on any other platform will result in an
 * {@link UnsupportedOperationException}. However, this factory may still be
 * used on non-macOS platforms to generate capabilities for remote Grid
 * execution.
 */
@ApplicationScoped
public final class SafariDriverFactory extends DriverFactory<SafariOptions> {
    /**
     * {@inheritDoc}
     */
    @Override
    public DriverType getDriverType() {
        return DriverType.SAFARI;
    }

    /**
     * Produces a Safari {@code WebDriver} using the capabilities derived from
     * {@link #getCapabilities()}.
     *
     * @return a new Safari {@code WebDriver} instance
     * @throws UnsupportedOperationException if executed on a non-macOS host
     */
    @Override
    public WebDriver create() {
        // Safari can only run locally on macOS. However, we allow the factory to be instantiated
        // on any OS to support RemoteWebDriver (Grid) scenarios where the client is Linux/Windows
        // but the Grid Node is macOS. Therefore, the OS check is performed here during local
        // creation.
        if (!OperatingSystem.isMacOS()) {
            throw new UnsupportedOperationException(
                    "Safari local execution not supported on this platform");
        }
        return new SafariDriver(getCapabilities());
    }

    /**
     * Applies Safari-specific post-construction configuration to the given
     * {@code WebDriver}.
     * <p>
     * In addition to the common configuration from the base implementation,
     * this method applies window sizing/maximize based on {@code DriverConfig},
     * but does not delete cookies on startup. Because Safari is sensitive to
     * window operations during startup, failures from window sizing/maximize
     * may be caught and logged rather than treated as fatal.
     *
     * @param driver the Safari {@code WebDriver} instance to configure
     */
    @Override
    public void configure(WebDriver driver) {
        // Deleting all cookies while Safari is still on the default Start Page causes the next
        // WebDriver command to throw a NoSuchWindowException, which in turn crashes our test
        // framework.
        WebDriver.Options options = driver.manage();
        setImplicitWait(options);
        try {
            if (config.windowSize.isPresent()) {
                options.window().setSize(config.windowSize.get());
            } else if (config.maximize) {
                options.window().maximize();
            }
        } catch (WebDriverException e) {
            LOGGER.log(Level.FINER, "Ignoring Safari window operation failure");
            LOGGER.throwing(getClass().getName(), "configure", e);
        }
    }

    /**
     * Returns the Safari-specific capabilities.
     * <p>
     * Headless mode is not supported for Safari and the configuration property
     * will be ignored.
     *
     * @return the Safari-specific capabilities
     */
    @Override
    public SafariOptions getCapabilities() {
        if (config.headless) {
            LOGGER.log(Level.WARNING, "Headless mode in Safari not supported - headless mode "
                    + "configuration property will be ignored");
        }

        SafariOptions options = new SafariOptions();
        applyCommonCapabilities(options);
        return options;
    }
}
