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

package net.larrykramer.test.util;

import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides locale-specific access to string resources backed by the
 * {@code strings} resource bundle.
 * <p>
 * Each {@code StringLocalizer} instance is bound to a single locale. When the
 * instance is created, the {@code strings} resource bundle is loaded and cached
 * for the lifetime of the object.
 * <p>
 * Because the locale and resource bundle are fixed after construction, the
 * class is effectively immutable. It is also thread-safe provided that the
 * underlying {@link ResourceBundle} implementation is thread-safe (as it is in
 * the standard JDK).
 */
public class StringLocalizer {
    private static final Logger LOGGER = Logger.getLogger(StringLocalizer.class.getName());

    private final Locale locale;
    private final ResourceBundle bundle;

    /**
     * Creates a new localizer for the supplied {@code Locale}.
     *
     * @param locale the locale whose resources should be loaded
     * @throws NullPointerException     if {@code locale} is null
     * @throws MissingResourceException if the {@code strings} resource bundle
     *                                  cannot be found
     */
    public StringLocalizer(Locale locale) {
        this.locale = Objects.requireNonNull(locale);
        this.bundle = ResourceBundle.getBundle("strings", locale);
    }

    /**
     * {@return the locale associated with this localizer}
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Resolves the supplied key to its localized message and optionally formats
     * it with the provided arguments.
     *
     * @param key  the resource bundle key to look up
     * @param args optional arguments used to format the localized message via
     *             {@link MessageFormat}
     * @return the localized (and optionally formatted) message, or a
     *         placeholder if the {@code key} is null or cannot be found in the
     *         {@code strings} resource bundle
     */
    public String localize(String key, Object... args) {
        if (key == null) {
            return missingKeyPlaceholder("null_key");
        }

        try {
            String text = bundle.getString(key);
            if (args != null && args.length > 0) {
                return new MessageFormat(text, locale).format(args);
            } else {
                return text;
            }
        } catch (MissingResourceException ignored) {
            LOGGER.log(Level.FINER, "Missing localization for key '{0}' in locale {1}",
                    new Object[] { key, locale });
        }

        return missingKeyPlaceholder(key);
    }

    private static String missingKeyPlaceholder(String key) {
        return "???" + key + "???";
    }
}
