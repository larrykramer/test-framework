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

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.larrykramer.test.config.FirefoxConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.webdriver.WebDriverFactory;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

/**
 * Factory that constructs Mozilla Firefox {@link WebDriver} instances.
 */
@ApplicationScoped
public final class FirefoxDriverFactory extends WebDriverFactory<FirefoxOptions> {
    @Inject
    @ConfigProperties
    private FirefoxConfig firefoxConfig;

    /**
     * {@inheritDoc}
     */
    @Override
    public WebDriverType getType() {
        return WebDriverType.FIREFOX;
    }

    /**
     * Produces a Firefox {@link WebDriver} using the capabilities derived from
     * {@link #getOptions()}.
     *
     * @return a Firefox WebDriver instance
     */
    @Override
    public WebDriver createWebDriver() {
        return new FirefoxDriver(getOptions());
    }

    /**
     * Applies Firefox-specific post-construction configuration to the supplied
     * WebDriver.
     * <p>
     * In addition to the common configuration from the base implementation,
     * this method deletes all cookies and applies window sizing/maximize based
     * on {@link WebDriverConfig}. Failures in window operations are treated as
     * fatal; any {@link WebDriverException} raised by window resizing or
     * maximization is allowed to propagate to signal a misconfigured
     * environment.
     *
     * @param driver the Firefox WebDriver instance to configure
     */
    @Override
    public void configureWebDriver(WebDriver driver) {
        super.configureWebDriver(driver); // apply common config

        WebDriver.Options options = driver.manage();
        deleteAllCookies(options);
        if (config.windowSize.isPresent()) {
            options.window().setSize(config.windowSize.get());
        } else if (config.maximize && !config.headless) {
            options.window().maximize();
        }
    }

    /**
     * Creates the Firefox-specific options from the global and Mozilla Firefox
     * configuration settings.
     *
     * @return the Firefox-specific options
     */
    @Override
    protected FirefoxOptions buildOptions() {
        FirefoxOptions options = new FirefoxOptions();

        firefoxConfig.executable.ifPresent(options::setBinary);

        for (var entry : firefoxConfig.userPrefs.entrySet()) {
            options.addPreference(entry.getKey(), convertToFirefoxPreference(entry.getValue()));
        }

        if (config.headless) {
            options.addArguments("-headless");
        }

        return options;
    }

    /*
     * Convert configuration values into types accepted by FirefoxOptions.
     *
     * The ObjectConverter produces Double instance for all floating-point inputs.
     * However, Selenium's FirefoxOptions strictly accepts String, Integer, or
     * Boolean. This method ensures that whole numbers fitting within an Integer,
     * while floating-point values are converted to String.
     */
    private static Object convertToFirefoxPreference(Object value) {
        if (!(value instanceof Number n)) {
            return value; // Boolean or String
        }
        if (n instanceof Integer) {
            return n;
        }

        final double d = n.doubleValue();
        if (Double.isFinite(d)) {
            BigDecimal bd = BigDecimal.valueOf(d);
            if (bd.stripTrailingZeros().scale() <= 0
                    && bd.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0
                    && bd.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) >= 0) {
                try {
                    return bd.intValueExact(); // use intValueExact to be sure.
                } catch (ArithmeticException e) {
                    // Not exactly representable as an int.
                }
            }
        }

        return String.valueOf(n);
    }
}
