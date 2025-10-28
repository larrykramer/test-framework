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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mockStatic;

public class StringLocalizerTest {
    @Test(expected = NullPointerException.class)
    public void testConstructor_givenNullLocale_throwsNullPointerException() {
        new StringLocalizer(null);
    }

    @Test(expected = MissingResourceException.class)
    public void testConstructor_givenMissingBundle_throwsMissingResourceException() {
        // Arrange
        Locale locale = Locale.forLanguageTag("zz-ZZ");
        try (var mocked = mockStatic(ResourceBundle.class)) {
            MissingResourceException e = new MissingResourceException(
                    "Can't find bundle for base name strings, locale zz_ZZ", "strings", "zz_ZZ");
            mocked.when(() -> ResourceBundle.getBundle("strings", locale)).thenThrow(e);

            // Act
            new StringLocalizer(locale);
        }
    }

    @Test
    public void testGetLocale_givenRootLocale_returnsSameInstance() {
        // Arrange
        final Locale locale = Locale.ROOT;
        StringLocalizer localizer = newLocalizer(locale);
        // Act & Assert
        assertSame(locale, localizer.getLocale());
    }

    @Test
    public void testGetLocale_givenDifferentJVMDefault_returnsConstructorLocale() {
        // Arrange: Part 1
        Locale locale = Locale.CANADA_FRENCH;
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            // Arrange: Part 2
            StringLocalizer localizer = newLocalizer(locale);
            // Act & Assert
            assertSame(locale, localizer.getLocale());
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    public void testLocalize_withExistingKey_returnsLocalizedMessage() {
        String result = newLocalizer().localize("greeting");
        assertEquals("Hello world", result);
    }

    @Test
    public void testLocalize_withExistingKeyAndArguments_returnsFormattedMessage() {
        String result = newLocalizer().localize("welcome", "Jane", "Doe");
        assertEquals("Welcome, Jane Doe!", result);
    }

    @Test
    public void testLocalize_withUnicodeKey_returnsUnicodeMessage() {
        // Arrange
        final StringLocalizer localizer = newLocalizer();
        // Act & Assert
        assertEquals("Bonjour! Créons une journée géniale.", localizer.localize("french.greeting"));
        assertEquals("Willkommen, Jürgen!", localizer.localize("german.welcome", "Jürgen"));
        assertEquals("こんにちは、世界 🌏", localizer.localize("日本語.挨拶"));
    }

    @Test
    public void testLocalize_withNumericArgument_useInstanceLocaleNotJVMDefault() {
        // Arrange: Part 1
        // The JVM defaults to '.' as the decimal separator and ',' as the grouping separator.
        // NumberFormat and StringLocalizer are localized to CANADA_FRENCH which uses ',' as the
        // decimal separator and narrow no-break space (\u202F) as the grouping separator.
        Locale locale = Locale.CANADA_FRENCH;
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            // Arrange: Part 2
            double amount = 12345.67;

            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            String expected = "Balance: " + nf.format(amount);

            StringLocalizer localizer = newLocalizer(locale);

            // Act & Assert
            assertEquals(expected, localizer.localize("balance", amount));
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    public void testLocalize_withDateArgument_useInstanceLocaleNotJVMDefault() {
        // Arrange: Part 1
        // The JVM default: "Month Day, Year".
        // DateFormat and StringLocalizer are localized to GERMANY which uses "Day. Month Year".
        Locale locale = Locale.GERMANY;
        Locale previousDefault = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            // Arrange: Part 2
            Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            calendar.set(2025, Calendar.OCTOBER, 26, 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date date = calendar.getTime();

            DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, locale);
            String expected = "Date: " + df.format(date);

            StringLocalizer localizer = newLocalizer(locale);

            // Act & Assert
            assertEquals(expected, localizer.localize("date", date));
        } finally {
            Locale.setDefault(previousDefault);
        }
    }

    @Test
    public void testLocalize_withMoreArgumentsThanPlaceholders_returnsFormattedMessage() {
        String result = newLocalizer().localize("welcome", "John", "Michael", "Smit");
        assertEquals("Welcome, John Michael!", result);
    }

    @Test
    public void testLocalize_withInsufficientArguments_returnsPartiallyFormattedMessage() {
        String result = newLocalizer().localize("welcome", "Jane");
        assertEquals("Welcome, Jane {1}!", result);
    }

    @Test
    public void testLocalize_withNullArguments_returnsLocalizedMessage() {
        assertEquals("Welcome, {0} {1}!", newLocalizer().localize("welcome", (Object[]) null));
    }

    @Test
    public void testLocalize_withNullKey_returnsNullKeyPlaceholder() {
        assertEquals("???null_key???", newLocalizer().localize(null));
    }

    @Test
    public void testLocalize_withNonExistantKey_returnsMissingKeyPlaceholder() {
        assertEquals("???absent???", newLocalizer().localize("absent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocalize_withInvalidMessageFormatPattern_throwsIllegalArgumentException() {
        newLocalizer().localize("invalid.pattern", "ignored");
    }

    @Test(expected = ClassCastException.class)
    public void testLocalize_withNonStringValue_throwsClassCastException() {
        newLocalizer().localize("non.string");
    }

    private static StringLocalizer newLocalizer() {
        return newLocalizer(Locale.ROOT);
    }

    private static StringLocalizer newLocalizer(Locale locale) {
        StringLocalizer localizer;
        try (var mocked = mockStatic(ResourceBundle.class)) {
            ResourceBundle bundle = new StringLocalizerTestBundle();
            mocked.when(() -> ResourceBundle.getBundle("strings", locale)).thenReturn(bundle);
            localizer = new StringLocalizer(locale);
        }
        return localizer;
    }

    private static class StringLocalizerTestBundle extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    { "greeting", "Hello world" },
                    { "welcome", "Welcome, {0} {1}!" },
                    // Unicode test patterns.
                    { "french.greeting", "Bonjour! Créons une journée géniale." }, // French
                    { "german.welcome", "Willkommen, {0}!" }, // German
                    { "日本語.挨拶", "こんにちは、世界 🌏" }, // Japanese
                    // Formatter patterns.
                    { "balance", "Balance: {0,number,currency}" },
                    { "date", "Date: {0,date,long}" },
                    // Malformed pattern.
                    { "invalid.pattern", "Welcome, {0" },
                    // Non-String resources.
                    { "non.string", new Object() }
            };
        }
    }
}
