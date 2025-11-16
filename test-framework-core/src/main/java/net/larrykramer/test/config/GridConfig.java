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

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Encapsulates configuration settings for executing tests on a remote Selenium
 * Grid.
 * <p>
 * Maps to properties prefixed with {@code grid}.
 */
@ConfigProperties(prefix = "grid")
@Dependent
public class GridConfig {
    /**
     * The URI of the Selenium Grid hub endpoint.
     * <p>
     * This property maps to {@code grid.url}.
     */
    @ConfigProperty(name = "url")
    public Optional<URI> uri;

    /**
     * Specifies the desired driver version for the remote session on the grid.
     * <p>
     * This property maps to {@code grid.browser-version}.
     */
    public Optional<String> browserVersion;

    /**
     * Specifies the desired operating system or platform for the remote session
     * on the grid.
     * <p>
     * This property maps to {@code grid.platform}.
     */
    public Optional<String> platform;

    /**
     * Defines a custom application name to identify the test suite in the
     * grid's dashboard or logs.
     * <p>
     * This property maps to {@code grid.application-name}.
     */
    public Optional<String> applicationName;

    /**
     * A map of arbitrary key-value pairs to be passed as capabilities to the
     * remote grid. This provides an extension point for vendor-specific
     * settings (e.g., for cloud-based grid providers like BrowserStack or Sauce
     * Labs) that are not covered by the standard properties.
     * <p>
     * Mapped from properties prefixed with {@code grid.capabilities}.
     */
    public Map<String, String> capabilities = Map.of();
}
