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
import java.util.Set;

import net.larrykramer.test.util.OperatingSystem;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * A {@code ConfigSource} that supplies a single, operating-system-aware
 * default for the {@code driver.type} configuration property.
 * <p>
 * The source inspects {@link OperatingSystem#current()} to determine a
 * sensible driver type selection for the runtime environment:
 * <ul>
 * <li>{@code SAFARI} on macOS
 * <li>{@code EDGE} on Windows
 * <li>{@code FIREFOX} on every other supported operating system
 * </ul>
 *
 * By registering with a relatively low ordinal (50), this implementation acts
 * as a baseline value that can be easily overridden by higher-priority
 * MicroProfile Config sources, while still providing a meaningful default when
 * no other configuration is present.
 */
public class DefaultTypeConfigSource implements ConfigSource {
    private static final Map<String, String> PROPERTIES = Map.of("driver.type", defaultType());

    private static String defaultType() {
        return switch (OperatingSystem.current()) {
            case MACOS -> "SAFARI";
            case WINDOWS -> "EDGE";
            // Firefox is more broadly available on non-macOS and non-Windows operating systems, so
            // use that as the default.
            default -> "FIREFOX";
        };
    }

    @Override
    public Map<String, String> getProperties() {
        return PROPERTIES;
    }

    @Override
    public Set<String> getPropertyNames() {
        return PROPERTIES.keySet();
    }

    @Override
    public int getOrdinal() {
        // The low ordinal provides a base default that can easily be overridden by standard
        // configuration sources.
        return 50;
    }

    @Override
    public String getValue(String propertyName) {
        return (propertyName == null) ? null : PROPERTIES.get(propertyName);
    }

    @Override
    public String getName() {
        return "OS-Aware Default WebDriver Config Source";
    }
}
