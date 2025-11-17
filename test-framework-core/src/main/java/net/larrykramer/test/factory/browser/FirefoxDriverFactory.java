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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.larrykramer.test.config.FirefoxConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.factory.WebDriverFactory;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.WebDriver;
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
     * Creates the Firefox-specific options from the global and Mozilla Firefox
     * configuration settings.
     *
     * @return the Firefox-specific options
     */
    @Override
    protected FirefoxOptions buildOptions() {
        FirefoxOptions options = new FirefoxOptions();

        firefoxConfig.executable.ifPresent(options::setBinary);
        firefoxConfig.userPrefs.forEach(options::addPreference);

        if (config.headless) {
            options.addArguments("-headless");
        }

        return options;
    }
}
