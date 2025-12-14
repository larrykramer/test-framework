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
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

/**
 * Factory that constructs Microsoft Edge {@code WebDriver} instances.
 */
@ApplicationScoped
public final class EdgeDriverFactory extends ChromiumDriverFactory<EdgeOptions> {
    @Inject
    @ConfigProperties(prefix = "driver.edge")
    private ChromiumConfig edgeConfig;

    /**
     * {@inheritDoc}
     */
    @Override
    public DriverType getDriverType() {
        return DriverType.EDGE;
    }

    /**
     * Produces a Microsoft Edge {@code WebDriver} using the capabilities
     * derived from {@link #getCapabilities()}.
     *
     * @return a new Microsoft Edge {@code WebDriver} instance
     */
    @Override
    public WebDriver create() {
        return new EdgeDriver(getCapabilities());
    }


    /**
     * Returns the Microsoft Edge-specific capabilities from the shared
     * Chromium and Microsoft Edge configuration settings.
     *
     * @return the Microsoft Edge-specific capabilities
     * @see ChromiumDriverFactory#buildChromiumOptions(ChromiumOptions, ChromiumConfig)
     */
    @Override
    public EdgeOptions getCapabilities() {
        final EdgeOptions options = new EdgeOptions();
        buildChromiumOptions(options, edgeConfig);
        applyCommonCapabilities(options);
        return options;
    }
}
