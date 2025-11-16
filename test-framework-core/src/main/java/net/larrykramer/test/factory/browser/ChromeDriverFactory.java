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
import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.config.WebDriverType;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;

/**
 * Factory that constructs Google Chrome {@link WebDriver} instances.
 */
@ApplicationScoped
public final class ChromeDriverFactory extends ChromiumDriverFactory<ChromeOptions> {
    @Inject
    @ConfigProperties(prefix = "webdriver.chrome")
    private ChromiumConfig chromeConfig;

    /**
     * {@inheritDoc}
     */
    @Override
    public WebDriverType getType() {
        return WebDriverType.CHROME;
    }

    /**
     * Produces a Google Chrome {@link WebDriver} using the capabilities derived
     * from {@link #getOptions()}.
     *
     * @return a Google Chrome WebDriver instance
     */
    @Override
    public WebDriver createWebDriver() {
        return new ChromeDriver(getOptions());
    }


    /**
     * Builds the Chrome-specific options from the shared Chromium and Google
     * Chrome configuration settings.
     *
     * @return the Chrome-specific options
     * @see ChromiumDriverFactory#buildChromiumOptions(ChromiumOptions, ChromiumConfig)
     */
    @Override
    protected ChromeOptions buildOptions() {
        final ChromeOptions options = new ChromeOptions();
        buildChromiumOptions(options, chromeConfig);
        return options;
    }
}
