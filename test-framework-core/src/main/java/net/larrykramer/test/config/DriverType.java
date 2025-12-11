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

/**
 * Enumeration of supported driver types.
 */
public enum DriverType {
    /** Represents the Google Chrome WebDriver. */
    CHROME("chrome"),
    /** Represents the Microsoft Edge WebDriver. */
    EDGE("MicrosoftEdge"),
    /** Represents the Safari WebDriver. */
    SAFARI("safari"),
    /** Represents the Firefox WebDriver. */
    FIREFOX("firefox"),
    /**
     * Represents a custom driver implementation provided via the Service
     * Provider Interface (SPI). When this type is selected, a fully qualified
     * class name for the driver implementation must be provided elsewhere in
     * the configuration.
     */
    SPI(null),
    ;

    private final String canonicalName;

    DriverType(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    /**
     * Resolves the {@code DriverType} whose enum constant name matches the
     * supplied name, ignoring character case.
     *
     * @param name the enum constant name to look up
     * @return the matching {@code DriverType}
     * @throws IllegalArgumentException if {@code name} is {@code null} or
     *                                  blank, or no enum constant matches the
     *                                  supplied name
     */
    public static DriverType of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }

        for (var value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown DriverType: " + name);
    }

    /**
     * Returns the canonical name of this driver type.
     *
     * @return the canonical driver name associated with this enum constant
     * @throws UnsupportedOperationException if the driver type is {@link #SPI},
     *                                       which does not have a predefined
     *                                       name
     */
    public String getCanonicalName() {
        if (this == SPI) {
            throw new UnsupportedOperationException("SPI does not have a canonical driver name");
        }
        return canonicalName;
    }
}
