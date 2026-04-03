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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mockStatic;

public class StringLocalizerTest {
    private static Set<Locale> candidateLocales;

    @BeforeClass
    public static void setupCandidateLocales() {
        LinkedHashSet<Locale> set = new LinkedHashSet<>();
        // A small, stable, "fast first" set of commonly available JDK locales.
        // High-signal first: usually different date order/words and/or currency formatting.
        set.add(Locale.GERMANY);        // "26. Oktober 2025", currency "12.345,67 €"
        set.add(Locale.FRANCE);         // "26 octobre 2025", currency often "12 345,67 €"
        set.add(Locale.CANADA_FRENCH);  // French conventions; differs strongly from US/EN-CA
        set.add(Locale.UK);             // often "26 October 2025" (day-month), vs US month-day
        set.add(Locale.ITALY);
        set.add(Locale.JAPAN);
        set.add(Locale.KOREA);
        set.add(Locale.CHINA);
        set.add(Locale.TAIWAN);
        // Likely defaults last (to avoid wasting early attempts).
        set.add(Locale.US);
        set.add(Locale.CANADA);
        // Add all available as a fallback for obscure environments.
        Collections.addAll(set, Locale.getAvailableLocales());

        candidateLocales = Collections.unmodifiableSet(set);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_nullLocale_throwsNullPointerException() {
        new StringLocalizer(null);
    }

    @Test(expected = MissingResourceException.class)
    public void testConstructor_missingBundle_throwsMissingResourceException() {
        Locale locale = Locale.forLanguageTag("zz-ZZ");
        try (var mocked = mockStatic(ResourceBundle.class)) {
            MissingResourceException e = new MissingResourceException(
                    "Can't find bundle for base name strings, locale zz_ZZ", "strings", "zz_ZZ");
            mocked.when(() -> ResourceBundle.getBundle("strings", locale)).thenThrow(e);

            new StringLocalizer(locale);
        }
    }

    @Test
    public void testGetLocale_rootLocale_returnsSameInstance() {
        final Locale locale = Locale.ROOT;
        StringLocalizer localizer = newLocalizer(locale);
        assertSame(locale, localizer.getLocale());
    }

    @Test
    public void testGetLocale_nonDefaultLocale_returnsConstructorLocale() {
        // Find a locale that is different from the default locale.
        // If no such locale is available, the test is skipped.
        Locale baseline = Locale.getDefault();
        Locale targetLocale = candidateLocales.stream()
                .filter(l -> isCandidate(baseline, l))
                .findFirst()
                .orElseThrow(() -> new AssumptionViolatedException("No suitable locale found"));

        Locale result = newLocalizer(targetLocale).getLocale();

        assertSame(targetLocale, result);
        assertNotEquals(baseline, result);
    }

    @Test
    public void testLocalize_existingKey_returnsLocalizedMessage() {
        String result = newLocalizer().localize("greeting");
        assertEquals("Hello world", result);
    }

    @Test
    public void testLocalize_formatArgs_returnsFormattedMessage() {
        String result = newLocalizer().localize("welcome", "Jane", "Doe");
        assertEquals("Welcome, Jane Doe!", result);
    }

    @Test
    public void testLocalize_unicodeKeys_returnsUnicodeMessage() {
        final StringLocalizer localizer = newLocalizer();
        assertEquals("Bonjour! Créons une journée géniale.", localizer.localize("french.greeting"));
        assertEquals("Willkommen, Jürgen!", localizer.localize("german.welcome", "Jürgen"));
        assertEquals("こんにちは、世界 🌏", localizer.localize("日本語.挨拶"));
    }

    @Test
    public void testLocalize_currencyArg_useInstanceLocaleNotJVMDefault() {
        double amount = 12345.67;
        Locale targetLocale = assumeLocaleWithDifferentCurrencyFormat(amount);

        final StringLocalizer localizer = newLocalizer(targetLocale);
        String result = localizer.localize("balance", amount);

        assertEquals("Balance: " + formatCurrency(targetLocale, amount), result);
    }

    @Test
    public void testLocalize_dateArg_useInstanceLocaleNotJVMDefault() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2025, Calendar.OCTOBER, 26, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();

        Locale targetLocale = assumeLocaleWithDifferentDateFormat(date);

        final StringLocalizer localizer = newLocalizer(targetLocale);
        String result = localizer.localize("date", date);

        assertEquals("Date: " + formatLongDate(targetLocale, date), result);
    }

    @Test
    public void testLocalize_extraArgs_returnsFormattedMessage() {
        String result = newLocalizer().localize("welcome", "John", "Michael", "Smit");
        assertEquals("Welcome, John Michael!", result);
    }

    @Test
    public void testLocalize_missingArgs_returnsPartiallyFormattedMessage() {
        String result = newLocalizer().localize("welcome", "Jane");
        assertEquals("Welcome, Jane {1}!", result);
    }

    @Test
    public void testLocalize_nullArgs_returnsLocalizedMessage() {
        assertEquals("Welcome, {0} {1}!", newLocalizer().localize("welcome", (Object[]) null));
    }

    @Test
    public void testLocalize_nullKey_returnsNullKeyPlaceholder() {
        assertEquals("???null_key???", newLocalizer().localize(null));
    }

    @Test
    public void testLocalize_missingKey_returnsMissingKeyPlaceholder() {
        assertEquals("???absent???", newLocalizer().localize("absent"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocalize_invalidPattern_throwsIllegalArgumentException() {
        newLocalizer().localize("invalid.pattern", "ignored");
    }

    @Test(expected = ClassCastException.class)
    public void testLocalize_nonStringResource_throwsClassCastException() {
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

    private static String formatCurrency(Locale locale, double amount) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        return nf.format(amount);
    }

    private static String formatLongDate(Locale locale, Date date) {
        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG, locale);
        // StringLocalizer uses MessageFormat, which uses the JVM default TimeZone.
        // We must use the same TimeZone for our expectation to match.
        df.setTimeZone(TimeZone.getDefault());
        return df.format(date);
    }

    private static Locale assumeLocaleWithDifferentCurrencyFormat(double amount) {
        final Locale baseline = Locale.getDefault();
        final String formattedAmount;
        try {
            formattedAmount = formatCurrency(baseline, amount);
        } catch (RuntimeException e) {
            throw new AssumptionViolatedException(
                    "Default locale cannot format currency: " + baseline, e);
        }

        for (var candidate : candidateLocales) {
            if (!isCandidate(baseline, candidate)) {
                continue;
            }
            try {
                if (!formattedAmount.equals(formatCurrency(candidate, amount))) {
                    return candidate;
                }
            } catch (RuntimeException e) {
                // Some locales/providers can throw for missing data; just skip.
            }
        }

        throw new AssumptionViolatedException("No locale with different currency format than"
                + " default locale: "
                + baseline
                + " ('" + formattedAmount + "')");
    }

    private static Locale assumeLocaleWithDifferentDateFormat(Date date) {
        final Locale baseline = Locale.getDefault();
        final String formattedDate;
        try {
            formattedDate = formatLongDate(baseline, date);
        } catch (RuntimeException e) {
            throw new AssumptionViolatedException(
                    "Default locale cannot format date: " + baseline, e);
        }

        for (var candidate : candidateLocales) {
            if (!isCandidate(baseline, candidate)) {
                continue;
            }
            try {
                if (!formattedDate.equals(formatLongDate(candidate, date))) {
                    return candidate;
                }
            } catch (RuntimeException e) {
                // Some locales/providers can throw for missing data; just skip.
            }
        }

        throw new AssumptionViolatedException("No locale with different LONG date format than"
                + " default locale: "
                + baseline
                + " ('" + formattedDate + "')");
    }

    private static boolean isCandidate(Locale baseline, Locale candidate) {
        return candidate != null && !Locale.ROOT.equals(candidate) && !candidate.equals(baseline);
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
