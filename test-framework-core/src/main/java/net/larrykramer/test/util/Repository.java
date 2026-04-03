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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized repository for resolving UI element locators declared in a
 * repository.
 * <p>
 * Locators are exposed through the {@link Locator Locator} record, which
 * encapsulates the normalized locator type and the fully formatted locator
 * selector. Consumers typically call {@link #get(String, Object...)} with the
 * locator key and any optional format arguments to obtain a ready-to-use
 * locator.
 * <p>
 * <b>Example object repository entry:</b>
 * <pre>{@code
 * login.button = css=.btn-primary
 * user.link = xpath=//a[text()="%s"]
 * }</pre>
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * Locator loginButton = Repository.get("login.button");
 * Locator userLink = Repository.get("user.link", username);
 * }</pre>
 */
public final class Repository {
    private static final Logger LOGGER = Logger.getLogger(Repository.class.getName());

    private static final String REPOSITORY_FILE = "repository.properties";

    /*
     * Backing store containing the locator entries.
     * Loaded during class initialization.
     */
    private static final Map<Object, Object> repoMap = loadRepository();

    /*
     * Loads the repository resource from the following sources (by descending order):
     * 1. The system property "net.larrykramer.test.repository.file".
     * 2. Current thread's context ClassLoader.
     * 3. This class's ClassLoader.
     * 4. The system ClassLoader.
     *
     * Note: We intentionally use Properties.load(InputStream), which forces
     * ISO-8859-1 per the Properties spec. If you need non-ASCII characters, escape
     * them (e.g. "\u00E9").
     */
    private static Map<Object, Object> loadRepository() {
        Properties props;

        String fname = System.getProperty("net.larrykramer.test.repository.file");
        if (fname != null) {
            try (InputStream in = Files.newInputStream(Path.of(fname))) {
                props = new Properties();
                props.load(in);
                return Map.copyOf(props);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unable to load repository from file {0}", fname);
                LOGGER.throwing(Repository.class.getName(), "loadRepository", e);
                // Fallback to class loaders.
            }
        }

        props = loadProperties(Thread.currentThread().getContextClassLoader());
        if (props == null) {
            props = loadProperties(Repository.class.getClassLoader());
        }
        if (props == null) {
            props = loadProperties(ClassLoader.getSystemClassLoader());
        }
        if (props == null) {
            throw new MissingResourceException("Can't find " + REPOSITORY_FILE,
                    REPOSITORY_FILE, // class name
                    "");             // key
        }

        return Map.copyOf(props);
    }

    private static Properties loadProperties(ClassLoader cl) {
        if (cl != null) {
            try (InputStream in = cl.getResourceAsStream(REPOSITORY_FILE)) {
                if (in != null) {
                    Properties props = new Properties();
                    props.load(in);
                    return props;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unable to load repository from class loader {0}", cl);
                LOGGER.throwing(Repository.class.getName(), "loadProperties", e);
            }
        }
        return null;
    }

    private Repository() {}

    /**
     * Resolves the locator entry associated with the given key and substitutes
     * any optional placeholder values.
     * <p>
     * The lookup expects the locator entry to adhere to the
     * {@code type=selector} convention, where {@code type} designates the
     * locator strategy (for example, {@code css}, {@code id}, or {@code xpath})
     * and {@code selector} is a pattern compatible with
     * {@link String#format(String, Object...)}. The returned {@code Locator}
     * contains the normalized locator type as well as the fully formatted
     * locator string.
     *
     * @param key  the locator key of the locator definition
     * @param args optional arguments applied to the locator pattern
     * @return a {@link Locator} comprising the locator strategy and locator
     *         selector
     * @throws IllegalArgumentException if the stored locator does not match the
     *                                  {@code type=selector} convention
     * @throws MissingResourceException if no locator entry exists for the
     *                                  supplied key
     * @throws NullPointerException     if {@code key} is null
     * @apiNote If the selector contains format specifiers and no format
     *          arguments are provided, no formatting is applied and the
     *          selector will be returned unformatted in the {@code Locator}.
     *          This may lead to downstream errors when the locator is used.
     *          Callers must supply arguments for locators that require them.
     */
    public static Locator get(String key, Object... args) {
        Objects.requireNonNull(key, "Key is null");

        if (!(repoMap.get(key) instanceof String value)) {
            throw new MissingResourceException("Missing locator: " + key, REPOSITORY_FILE, key);
        }

        int idx = value.indexOf('=');
        if (idx <= 0 || idx == (value.length() - 1)) {
            throw invalidFormat(key, value, null);
        }

        String selector = value.substring(idx + 1);
        if (args != null && args.length > 0) {
            try {
                selector = String.format(Locale.ROOT, selector, args);
            } catch (IllegalFormatException e) {
                throw invalidFormat(key, value, e);
            }
        }

        return new Locator(value.substring(0, idx), selector);
    }

    private static IllegalArgumentException invalidFormat(String key, String value, Throwable t) {
        return new IllegalArgumentException(
                String.format("Invalid locator format for '%s': %s", key, value), t);
    }
}
