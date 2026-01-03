/*
 * Copyright (c) 2025-2026 Larry Kramer
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
     * Represents a custom driver factory implementation that creates Selenium
     * {@code WebDriver} instances.
     */
    SPI(null),
    ;

    private final String canonicalName;

    DriverType(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    /**
     * Returns the enum constant of this class with the specified name,
     * ignoring character case. (Extraneous whitespace characters are not
     * permitted.)
     *
     * @param name the name of the enum constant to be returned.
     * @return the enum constant with the specified name
     * @throws IllegalArgumentException if this enum class has no constant with
     *                                  the specified name
     * @throws NullPointerException     if {@code name} is null
     */
    public static DriverType of(String name) {
        if (name == null) {
            throw new NullPointerException("Name is null");
        }

        for (var value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }

        throw new IllegalArgumentException(
                "No enum constant " + DriverType.class.getCanonicalName() + "." + name);
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
