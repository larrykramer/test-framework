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

import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.factory.WebDriverFactory;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.chromium.ChromiumOptions;

/**
 * Base abstraction for Chromium-based browser factories such as Google Chrome
 * and Microsoft Edge.
 * <p>
 * This sealed class centralizes the option handling that translates a
 * {@link ChromiumConfig} into {@link ChromiumOptions} prior to driver
 * creation.
 *
 * @param <T> the concrete Chromium options type
 *
 * @see ChromeDriverFactory
 * @see EdgeDriverFactory
 */
abstract sealed class ChromiumDriverFactory<T extends ChromiumOptions<T>>
        extends WebDriverFactory<T>
        permits ChromeDriverFactory, EdgeDriverFactory {
    /**
     * Applies shared Chromium configuration settings to the provided options.
     *
     * @param options        the Chromium options that should be configured
     * @param chromiumConfig the Chromium-specific configuration
     */
    protected void buildChromiumOptions(T options, ChromiumConfig chromiumConfig) {
        chromiumConfig.executable.ifPresent(options::setBinary);

        boolean hasHeadlessArg = false;
        boolean hasWindowSizeArg = false;

        if (!chromiumConfig.arguments.isEmpty()) {
            options.addArguments(chromiumConfig.arguments);

            // Check existing arguments to avoid duplicate arguments
            for (String arg : chromiumConfig.arguments) {
                if (arg.equals("--headless") || arg.startsWith("--headless=")) {
                    hasHeadlessArg = true;
                } else if (arg.startsWith("--window-size=")) {
                    hasWindowSizeArg = true;
                }
            }
        }

        if (config.headless && !hasHeadlessArg) {
            // Let the browser decide which headless implementation to use.
            options.addArguments("--headless");
        }
        if (!config.maximize && config.windowSize.isPresent() && !hasWindowSizeArg) {
            Dimension windowSize = config.windowSize.get();
            options.addArguments("--window-size=" + windowSize.width + "," + windowSize.height);
        }
    }
}
