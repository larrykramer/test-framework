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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.config.DriverType;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;

/**
 * Factory that constructs Google Chrome {@code WebDriver} instances.
 */
@ApplicationScoped
public final class ChromeDriverFactory extends ChromiumDriverFactory<ChromeOptions> {
    @Inject
    @ConfigProperties(prefix = "driver.chrome")
    private ChromiumConfig chromeConfig;

    /**
     * {@inheritDoc}
     */
    @Override
    public DriverType getDriverType() {
        return DriverType.CHROME;
    }

    /**
     * Produces a Google Chrome {@code WebDriver} using the capabilities derived
     * from {@link #getCapabilities()}.
     *
     * @return a new Google Chrome {@code WebDriver} instance
     */
    @Override
    public WebDriver create() {
        return new ChromeDriver(getCapabilities());
    }


    /**
     * Returns the Google Chrome-specific capabilities derived from the shared
     * Chromium and Google Chrome configuration properties.
     *
     * @return the Google Chrome-specific capabilities
     * @see ChromiumDriverFactory#buildChromiumOptions(ChromiumOptions, ChromiumConfig)
     */
    @Override
    public ChromeOptions getCapabilities() {
        final ChromeOptions options = new ChromeOptions();
        buildChromiumOptions(options, chromeConfig);
        applyCommonCapabilities(options);
        return options;
    }
}
