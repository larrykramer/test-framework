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

package net.larrykramer.test.util;

import java.util.Locale;

/**
 * Immutable value object that encapsulates a normalized locator definition.
 * <p>
 * A locator consists of a locator strategy {@code type} (for example
 * {@code css}, {@code id}, or {@code xpath}) and an associated {@code value}
 * representing the selector to be executed with that strategy. Instances of
 * this record ensure that the locator type is stored in lower case and that
 * neither component is null or blank.
 * <p>
 * The selector is automatically stripped of leading and trailing whitespace. To
 * preserve significant whitespace, enclose the selector in single ({@code '})
 * or double ({@code "}) quotes. The quotes themselves will be trimmed, but the
 * content within them will be preserved as-is.
 * <p>
 * Instances are typically produced by {@link Repository#get(String, Object...)}
 * after expanding any format placeholders in the repository entry.
 * <p>
 * <b>Note:</b> Only basic normalization is performed. No validation is done to
 * ensure that the type or value corresponds to a particular locator strategy.
 *
 * @param type     the locator strategy name
 * @param selector the locator selector
 * @see Repository#get(String, Object...)
 */
public record Locator(String type, String selector) {
    /**
     * Validates and normalizes the supplied locator components.
     *
     * @throws IllegalArgumentException if {@code type} or {@code selector} is
     *                                  null or blank, or if {@code selector}
     *                                  has mismatched quotes
     */
    public Locator {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Type must not be null or blank");
        }
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Selector must not be null or blank");
        }

        // Normalize type
        type = type.strip().toLowerCase(Locale.ROOT);

        // Normalize selector.
        // Strips any leading and trailing whitespace, and removes enclosing quotes.
        String s = selector.strip();
        char ch = s.charAt(0);
        if (ch == '"' || ch == '\'') {
            // The selector starts with a quote, so it MUST end with a matching one.
            if (s.length() >= 2 && s.charAt(s.length() - 1) == ch) {
                selector = s.substring(1, s.length() - 1);
            } else {
                throw new IllegalArgumentException("Mismatched quote in selector: " + selector);
            }
        } else {
            selector = s;
        }
        if (selector.isBlank()) {
            throw new IllegalArgumentException("Selector must not be blank after normalization");
        }
    }
}
