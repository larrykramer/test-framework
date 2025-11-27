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

import org.eclipse.microprofile.config.spi.Converter;

/**
 * A {@link Converter} implementation that maps textual configuration values to
 * the most appropriate Java type.
 */
public class ObjectConverter implements Converter<Object> {
    /**
     * Converts the supplied configuration value to a strongly typed object. The
     * method returns:
     * <ul>
     * <li>An empty string if the trimmed value is blank;
     * <li>The unquoted string value if the trimmed input is enclosed in matching
     *   single ({@code '}) or double ({@code "}) quotes. This serves as an
     *   escape hatch to force a specific value to remain a {@link String}
     *   (e.g., {@code "123"} becomes the string {@code 123});
     * <li>{@link Boolean#TRUE} or {@link Boolean#FALSE} for recognized boolean
     *   literals such as {@code "true"}, {@code "false"}, {@code "yes"},
     *   {@code "no"}, {@code "on"}, {@code "off"}, {@code "y"}, {@code "n"}
     *   (case-insensitive);
     * <li>An {@link Integer} if the trimmed value represents a <em>valid</em>
     *   integer. The literals {@code "0"} and {@code "1"} are treated as
     *   integers rather than a boolean;
     * <li>A {@link Double} if the value represents a floating-point number or
     *   if it cannot be represented as a valid integer;
     * <li>The <em>original</em> string if none of the above conversions
     *   succeed.
     * </ul>
     *
     * @param value the configuration value to convert; must not be {@code null}
     * @return the converted object as described above
     * @throws IllegalArgumentException if {@code value} has mismatched quotes
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    @Override
    public Object convert(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        String s = value.strip();
        if (s.isEmpty()) {
            return "";
        }

        char ch = s.charAt(0);
        if (ch == '"' || ch == '\'') {
            // The value starts with a quote, so it MUST end with a matching one.
            if (s.length() >= 2 && s.charAt(s.length() - 1) == ch) {
                return s.substring(1, s.length() - 1);
            } else {
                throw new IllegalArgumentException("Mismatched quote in value: " + value);
            }
        }

        // Follow the Microprofile Config boolean convention with two notable exceptions—"0" and
        // "1". We can't differentiate between "0"/"1" as a boolean and "0"/"1" as an integer, so
        // convert "0" and "1" to an integer.
        if ("true".equalsIgnoreCase(s)
                || "yes".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s)
                || "on".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        } else if ("false".equalsIgnoreCase(s)
                || "no".equalsIgnoreCase(s) || "n".equalsIgnoreCase(s)
                || "off".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }

        // The value is not a boolean, try parsing it as an integer.
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException nfe) {
            // The value is not an integer, try parsing it as a double.
            try {
                return Double.valueOf(s);
            } catch (NumberFormatException nfe1) {
                // That's okay; just use the value as a String.
                return value;
            }
        }
    }
}
