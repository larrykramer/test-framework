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

import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration properties specific to the Mozilla Firefox driver.
 * <p>
 * All properties within this class are prefixed with {@code webdriver.firefox}.
 */
@ConfigProperties(prefix = "webdriver.firefox")
@Dependent
public class FirefoxConfig {
    /**
     * A map of key-value pairs representing Firefox user preferences,
     * equivalent to those configured via {@code about:config}.
     * <p>
     * This property is mapped from properties prefixed with
     * {@code webdriver.firefox.user-preferences}.
     */
    @ConfigProperty(name = "user-preferences")
    public Map<String, String> userPrefs = Map.of();

    /**
     * Specifies the absolute path to a custom Firefox executable.
     * <p>
     * This property maps to {@code webdriver.firefox.executable}.
     */
    public Optional<String> executable;
}
