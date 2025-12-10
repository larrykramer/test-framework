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

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;

/**
 * Configuration properties shared by Chromium-based browsers such as Google
 * Chrome and Microsoft Edge.
 * <p>
 * This class encapsulates properties such as the executable path and
 * command-line arguments that are common to the underlying Chromium
 * architecture.
 */
@ConfigProperties(prefix = "driver.chromium") // default prefix; can be overridden
@Dependent
public class ChromiumConfig {
    /**
     * Specifies the absolute path to a custom Chromium executable.
     * <p>
     * This property maps to <code>&lt;prefix&gt;.executable</code>, where
     * <code>&lt;prefix&gt;</code> is the value provided by the
     * {@link ConfigProperties @ConfigProperties} annotation.
     */
    public Optional<String> executable;

    /**
     * A list of command-line arguments to be passed to the Chromium executable
     * upon startup.
     * <p>
     * <strong>Note:</strong> Custom arguments may interfere with WebDriver's
     * standard operation and should be used with caution.
     * <p>
     * This property is mapped from <code>&lt;prefix&gt;.arguments</code> as a
     * comma-separated list, where <code>&lt;prefix&gt;</code> is the value
     * provided by the {@link ConfigProperties @ConfigProperties} annotation.
     *
     * @see <a href="https://peter.sh/experiments/chromium-command-line-switches/">List of Chromium Command Line Switches</a>
     */
    public List<String> arguments = List.of();
}
