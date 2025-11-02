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

import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides locale-specific access to string resources backed by the {@code strings}
 * {@link ResourceBundle}.
 * <p>
 * A {@code StringLocalizer} is bound to a single {@link Locale}. Upon construction the matching
 * resource bundle is loaded and cached for the lifetime of the instance, making the class
 * effectively immutable and thread-safe as long as the underlying {@link ResourceBundle} is also
 * thread-safe (which is the case for the standard JDK implementation).
 */
public class StringLocalizer {
    private static final Logger LOGGER = Logger.getLogger(StringLocalizer.class.getName());

    private final Locale locale;
    private final ResourceBundle bundle;

    /**
     * Creates a new localizer for the supplied {@link Locale}.
     *
     * @param locale the locale whose resources should be loaded; must not be {@code null}
     * @throws NullPointerException     if {@code locale} is {@code null}
     * @throws MissingResourceException if the {@code strings} bundle cannot be found
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
     * Resolves the supplied key to its localized message and optionally formats it with the
     * provided arguments.
     * <p>
     * If the key is {@code null} or cannot be found in the bundle, a placeholder in the form
     * {@code "???key???"} is returned instead.
     *
     * @param key  the resource bundle key to look up
     * @param args optional arguments used to format the localized message via {@link MessageFormat}
     * @return the localized (and optionally formatted) message, or a placeholder if the
     *         {@code key} is missing
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
