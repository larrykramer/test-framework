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

import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.webdriver.DriverFactory;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumOptions;

/**
 * Base abstraction for Chromium-based browser factories such as Google Chrome
 * and Microsoft Edge.
 * <p>
 * This sealed class centralizes the option handling that translates a
 * {@code ChromiumConfig} into {@code ChromiumOptions} prior to driver
 * creation.
 *
 * @param <T> the concrete Chromium options type
 *
 * @see ChromeDriverFactory
 * @see EdgeDriverFactory
 */
abstract sealed class ChromiumDriverFactory<T extends ChromiumOptions<T>>
        extends DriverFactory<T>
        permits ChromeDriverFactory, EdgeDriverFactory {
    /**
     * Applies Chromium-specific post-construction configuration to the given
     * {@code WebDriver}.
     *
     * @param driver the Chromium-based {@code WebDriver} instance to configure
     */
    @Override
    public void configure(WebDriver driver) {
        WebDriver.Options options = driver.manage();
        setImplicitWait(options);
        deleteAllCookies(options);
        // Window sizing is handled via buildChromiumOptions, so no sizing is needed here.
    }

    /**
     * Applies shared Chromium configuration settings to the provided options.
     *
     * @param options        the Chromium options that should be configured
     * @param chromiumConfig the Chromium-specific configuration
     * @throws IllegalArgumentException if the configured window width or height
     *                                  is negative or 0
     */
    protected void buildChromiumOptions(T options, ChromiumConfig chromiumConfig) {
        chromiumConfig.executable.ifPresent(options::setBinary);

        boolean hasHeadlessArg = false;
        boolean hasWindowSizeArg = false;
        boolean hasMaximizedArg = false;

        if (!chromiumConfig.arguments.isEmpty()) {
            options.addArguments(chromiumConfig.arguments);

            // Check existing arguments to avoid duplicate arguments.
            for (String arg : chromiumConfig.arguments) {
                if (arg.equals("--headless") || arg.startsWith("--headless=")) {
                    hasHeadlessArg = true;
                } else if (arg.startsWith("--window-size=")) {
                    hasWindowSizeArg = true;
                } else if (arg.equals("--start-maximized")) {
                    hasMaximizedArg = true;
                }
            }
        }

        if (config.headless && !hasHeadlessArg) {
            // Let the browser decide which headless implementation to use.
            options.addArguments("--headless");
        }

        if (hasWindowSizeArg || hasMaximizedArg) {
            // window-size or start-maximized argument flag already specified.
            // Don't override either flag.
            return;
        }
        if (config.windowSize.isPresent()) {
            Dimension windowSize = config.windowSize.get();
            if (windowSize.width <= 0 || windowSize.height <= 0) {
                throw new IllegalArgumentException("Window width and height must be "
                        + "greater than 0: "
                        + windowSize.width + "x" + windowSize.height);
            }
            options.addArguments("--window-size=" + windowSize.width + "," + windowSize.height);
        } else if (config.maximize && !config.headless) {
            // Only apply start-maximized if not headless and not already specified.
            // Headless Chrome usually ignores start-maximized and defaults to 800x600 unless
            // window-size is set.
            options.addArguments("--start-maximized");
        }
    }
}
