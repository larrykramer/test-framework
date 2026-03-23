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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.MissingFormatArgumentException;
import java.util.MissingResourceException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RepositoryTest {
    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGet_existingKey_returnsCorrectLocator() throws Throwable {
        //language=properties
        final String repository = "login.button=css=.btn-primary\n";

        Locator result = getLocator(repository, "login.button");
        assertEquals("css", result.type());
        assertEquals(".btn-primary", result.selector());
    }

    @Test
    public void testGet_formatArgs_returnsFormattedLocator() throws Throwable {
        //language=properties
        final String repository = "user.link=xpath=//a[@data-user='%s']\n";

        Locator result = getLocator(repository, "user.link", "alice");
        assertEquals("xpath", result.type());
        assertEquals("//a[@data-user='alice']", result.selector());
    }

    @Test
    public void testGet_unicodeContent_returnsCorrectLocators() throws Throwable {
        // This string simulates a properties file containing ISO-8859-1 encoded characters.
        // We include a key with a French character and a value to be formatted with a German
        // character. We also include a locator with Japanese text.
        //language=properties
        final String repository = """
                page.title.fran\\u00E7ais=xpath=//h1[text()='Titre de la Page']
                german.welcome=xpath=//span[text()='Willkommen, %s!']
                japanese.salutation=xpath=//h2[text()='\\u3053\\u3093\\u306B\\u3061\\u306F\\u3001\\u4E16\\u754C\\u0020\\uD83C\\uDF0F']
                """;

        Locator frenchResult = getLocator(repository, "page.title.français");
        Locator germanResult = getLocator(repository, "german.welcome", "Jürgen");
        Locator japaneseResult = getLocator(repository, "japanese.salutation");

        assertEquals("xpath", frenchResult.type());
        assertEquals("//h1[text()='Titre de la Page']", frenchResult.selector());
        assertEquals("xpath", germanResult.type());
        assertEquals("//span[text()='Willkommen, Jürgen!']", germanResult.selector());
        assertEquals("xpath", japaneseResult.type());
        assertEquals("//h2[text()='こんにちは、世界 🌏']", japaneseResult.selector());
    }

    @Test
    public void testGet_nullArgs_returnsNormalizedLocator() throws Throwable {
        //language=properties
        final String repository = "null.arguments=css=Item %s\n";

        Locator result = getLocator(repository, "null.arguments", (Object[]) null);
        assertEquals("css", result.type());
        assertEquals("Item %s", result.selector());
    }

    @Test
    public void testGet_externalOverride_prefersExternalLocator() throws Throwable {
        File f = tempFolder.newFile("override-repository.properties");
        Files.writeString(f.toPath(), "key=css=.btn-secondary", StandardCharsets.ISO_8859_1);

        //language=properties
        final String repository = "key=id=fallback-id\n";

        try (var env = ScopedSystemProperties.open()) {
            env.setProperty("net.larrykramer.test.repository.file", f.getAbsolutePath());

            Locator result = getLocator(repository, "key");
            assertEquals("css", result.type());
            assertEquals(".btn-secondary", result.selector());
        }
    }

    @Test
    public void testGet_malformedExternal_usesFallbackLocator() throws Throwable {
        // Create a properties files with a malformed Unicode escape sequence (e.g., containing an
        // invalid character or an incomplete sequence). This is guaranteed by the Properties.load()
        // specification to throw an exception.
        File f = tempFolder.newFile("malformed-repository.properties");
        Files.writeString(f.toPath(), "key=\\u123X", StandardCharsets.ISO_8859_1);

        //language=properties
        final String repository = "key=id=fallback-id\n";

        try (var env = ScopedSystemProperties.open()) {
            env.setProperty("net.larrykramer.test.repository.file", f.getAbsolutePath());

            Locator result = getLocator(repository, "key");
            assertEquals("id", result.type());
            assertEquals("fallback-id", result.selector());
        }
    }

    @Test
    public void testGet_missingKey_throwsMissingResourceException() {
        //language=properties
        final String repository = "login.button=css=.btn\n";

        Throwable thrown = getLocatorExceptionally(repository, "absent");
        assertNotNull(thrown);
        assertEquals(MissingResourceException.class, thrown.getClass());
        assertEquals("Missing locator: absent", thrown.getMessage());
    }

    @Test
    public void testGet_missingRepo_throwsMissingResourceException() {
        Throwable thrown = getLocatorExceptionally(null, "any.key");
        assertNotNull(thrown);
        assertEquals(MissingResourceException.class, thrown.getClass());
        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().startsWith("Can't find "));
    }

    @Test
    public void testGet_invalidFormat_throwsIllegalArgumentException() {
        //language=properties
        final String repository = "broken=css-selector\n";

        Throwable thrown = getLocatorExceptionally(repository, "broken");
        assertNotNull(thrown);
        assertEquals(IllegalArgumentException.class, thrown.getClass());
        assertEquals("Invalid locator format for 'broken': css-selector", thrown.getMessage());
    }

    @Test
    public void testGet_blankSelector_throwsIllegalArgumentException() {
        //language=properties
        final String repository = "blank=css=\n";

        Throwable thrown = getLocatorExceptionally(repository, "blank");
        assertNotNull(thrown);
        assertEquals(IllegalArgumentException.class, thrown.getClass());
        assertEquals("Invalid locator format for 'blank': css=", thrown.getMessage());
    }

    @Test
    public void testGet_missingFormatArg_throwsIllegalArgumentException() {

        //language=properties
        final String repository = "bad.format=css=Item %s %d\n";

        Throwable thrown = getLocatorExceptionally(repository, "bad.format", "only-one-arg");
        assertEquals(IllegalArgumentException.class, thrown.getClass());

        Throwable cause = thrown.getCause();
        assertNotNull(cause);
        assertEquals(MissingFormatArgumentException.class, cause.getClass());
        assertEquals("Format specifier '%d'", cause.getMessage());
    }

    private static Locator getLocator(String props, String key, Object... args) throws Throwable {
        HashMap<String, String> resources = createResourceMap(props);
        return IsolatedClassLoader.doInvoke(Repository.class, resources, clazz -> {
            // Reflectively invoke Repository.get and adapt its returned locator (which lives
            // in the isolated class loader) into this test's Locator type.
            final Method method = clazz.getMethod("get", String.class, Object[].class);

            Object locator = method.invoke(null, key, args);

            // Pull out the locator type and selector via reflection.
            Class<?> c = locator.getClass();
            String type = (String) c.getMethod("type").invoke(locator);
            String selector = (String) c.getMethod("selector").invoke(locator);

            return new Locator(type, selector);
        });
    }

    private static Throwable getLocatorExceptionally(String props, String key, Object... args) {
        HashMap<String, String> resources = createResourceMap(props);
        try {
            return IsolatedClassLoader.doInvoke(Repository.class, resources, clazz -> {
                // Reflectively invoke Repository.get and capture any thrown exception instead
                // of letting it fail the test. This enables assertions to inspect the exception
                // type or message.
                try {
                    clazz.getMethod("get", String.class, Object[].class).invoke(null, key, args);
                    return null; // no exception occurred
                } catch (InvocationTargetException e) {
                    return e.getCause();
                } catch (Throwable t) {
                    return t;
                }
            });
        } catch (Throwable t) {
            return t;
        }
    }

    private static HashMap<String, String> createResourceMap(String contents) {
        // A HashMap allows for null values which allows us to simulate a missing repository
        // properties file.
        HashMap<String, String> resources = HashMap.newHashMap(1);
        resources.put("repository.properties", contents);
        return resources;
    }
}
