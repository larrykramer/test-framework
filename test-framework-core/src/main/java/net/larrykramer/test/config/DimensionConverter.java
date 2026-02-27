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
import org.openqa.selenium.Dimension;

/**
 * A {@code Converter} implementation that parses textual configuration values
 * into {@code Dimension} instances.
 */
public class DimensionConverter implements Converter<Dimension> {
    /**
     * Converts the supplied string into a {@code Dimension}.
     * <p>
     * The value is parsed as a case-insensitive dimension string in either
     * {@code "<width>x<height>"} or {@code "<width>,<height>"} form. Leading
     * and trailing whitespace around each component is ignored. An empty or
     * blank value is treated as unset and results in {@code null}.
     *
     * @param value the dimension string to convert
     * @return a {@code Dimension} parsed from the supplied value, or
     *         {@code null} if the value is blank
     * @throws IllegalArgumentException if the value does not match the expected
     *                                  formats or contains non-numeric
     *                                  width/height components
     * @throws NullPointerException     if {@code value} is null
     */
    @Override
    public Dimension convert(String value) {
        if (value == null) {
            throw new NullPointerException("Value is null");
        }
        if (value.isBlank()) {
            return null;
        }

        int idx = value.indexOf('x');
        if (idx == -1) {
            idx = value.indexOf('X');
            if (idx == -1) {
                idx = value.indexOf(',');
                if (idx == -1) {
                    throw new IllegalArgumentException(
                            "Dimension must be <width>x<height> or <width>,<height>: " + value);
                }
            }
        }

        try {
            String width = value.substring(0, idx).strip();
            String height = value.substring(idx + 1).strip();
            return new Dimension(Integer.parseInt(width), Integer.parseInt(height));
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(
                    "Dimension components must be numeric: " + value, nfe);
        }
    }
}
