/*
 * Copyright (c) 2026 Larry Kramer
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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.IllegalFormatException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class ExceptionsTest {
    public static final String INCLUDE_IN_EXCEPTIONS = "jdk.includeInExceptions";

    private record Result(String message, boolean enhanced) {}

    @Test(expected = NullPointerException.class)
    public void testFilterHostInfo_nullHost_throwsNullPointerException() {
        // No isolated loader needed: this tests only null validation, not the
        // class-initialized jdk.includeInExceptions setting.
        //noinspection ResultOfMethodCallIgnored
        Exceptions.filterHostInfo(null);
    }

    @Test
    public void testFormatMsg_secureReplacement_returnsReplacement() throws Throwable {
        Result result;

        result = doWithExceptionInclusion("none", clazz -> {
            Object si = filterHostInfo(clazz, "secret.example.com");
            with(si, "withPrefix", ": [");
            with(si, "withSuffix", "]");
            with(si, "withReplacement", ": redacted");
            return new Result(formatMsg(clazz, "invalid%s", si), isEnhanced(si));
        });

        assertEquals("invalid: redacted", result.message());
        assertFalse(result.enhanced());
    }

    @Test
    public void testFormatMsg_hostInfoProperty_returnsEnhancedInfo() throws Throwable {
        Result result;

        result = doWithExceptionInclusion(" other, HOSTINFO ", clazz -> {
            Object si = filterHostInfo(clazz, "secret.example.com");
            with(si, "withPrefix", ": [");
            with(si, "withSuffix", "]");
            with(si, "withReplacement", ": redacted");
            return new Result(formatMsg(clazz, "invalid%s", si), isEnhanced(si));
        });

        assertEquals("invalid: [secret.example.com]", result.message());
        assertTrue(result.enhanced());
    }

    @Test
    public void testFormatMsg_hostInfoExclSocket_returnsEnhancedInfo() throws Throwable {
        Result result;

        result = doWithExceptionInclusion("hostInfoExclSocket", clazz -> {
            Object si = filterHostInfo(clazz, "secret.example.com:443");
            with(si, "withPrefix", "<");
            with(si, "withSuffix", ">");
            with(si, "withReplacement", "redacted");
            return new Result(formatMsg(clazz, si), isEnhanced(si));
        });

        assertEquals("<secret.example.com:443>", result.message());
        assertTrue(result.enhanced());
    }

    @Test
    public void testFormatMsg_singleInfoNonEnhanced_returnsReplacement() throws Throwable {
        final String result = doWithExceptionInclusion("none", clazz -> {
            Object si = filterHostInfo(clazz, "secret");
            with(si, "withReplacement", "hidden");
            return formatMsg(clazz, si);
        });
        assertEquals("hidden", result);
    }

    @Test
    public void testFormatMsg_singleInfoEnhanced_returnsOriginal() throws Throwable {
        final String result = doWithExceptionInclusion(
                "hostInfo",
                clazz -> formatMsg(clazz, filterHostInfo(clazz, "my-host")));
        assertEquals("my-host", result);
    }

    @Test
    public void testFormatMsg_nonEnhancedDefaultReplacement_returnsEmptyString() throws Throwable {
        final String result = doWithExceptionInclusion(
                "none",
                clazz -> formatMsg(clazz, filterHostInfo(clazz, "host")));
        assertEquals("", result);
    }

    @Test
    public void testFormatMsg_multipleInfoArgs_formatsAll() throws Throwable {
        final String result = doWithExceptionInclusion("hostInfo", clazz -> {
            Object source = filterHostInfo(clazz, "host1");
            with(source, "withPrefix", " ");
            with(source, "withSuffix", "   ");

            Object target = filterHostInfo(clazz, "host2");
            with(target, "withPrefix", " ");

            return formatMsg(clazz, "from%sto%s", source, target);
        });
        assertEquals("from host1 to host2", result);
    }

    @Test
    public void testFormatMsg_emptyInfos_returnsFormattedLiteral() throws Throwable {
        String result = doWithExceptionInclusion("none", clazz -> {
            // A two-argument call, formatMsg(clazz, "literal message"), would bind to
            // the fixed-arity helper formatMsg(Class<?>, Object), not the varargs helper
            // formatMsg(Class<?>, String, Object...), because Java prefers fixed-arity
            // overloads before varargs. Pass an explicit empty varargs array so this calls
            // the format-string overload with zero SensitiveInfo arguments.
            return formatMsg(clazz, "literal message", new Object[0]);
        });
        assertEquals("literal message", result);
    }

    @Test
    public void testFormatMsg_emptyReplacementAtStart_trimsLeading() throws Throwable {
        final String result = doWithExceptionInclusion(
                "none",
                clazz -> formatMsg(clazz, "%s error", filterHostInfo(clazz, "host")));
        assertEquals("error", result);
    }

    @Test
    public void testFormatMsg_emptyReplacementAtEnd_trimsTrailing() throws Throwable {
        final String result = doWithExceptionInclusion(
                "none",
                clazz -> formatMsg(clazz, "error %s", filterHostInfo(clazz, "host")));
        assertEquals("error", result);
    }

    @Test
    public void testFormatMsg_multipleConsecutiveSpaces_collapsedToOne() throws Throwable {
        final String result = doWithExceptionInclusion(
                "none",
                clazz -> formatMsg(clazz, "a  %s b", filterHostInfo(clazz, "host")));
        assertEquals("a b", result);
    }

    @Test
    public void testFormatMsg_allSpacesLiteral_returnsEmptyString() throws Throwable {
        final String result = doWithExceptionInclusion(
                "none",
                clazz -> formatMsg(clazz, "  ", new Object[0]));
        assertEquals("", result);
    }

    @Test
    public void testFormatMsg_securityPropertyFallback_returnsEnhancedInfo() throws Throwable {
        String result;

        // Mock Security.getProperty for this test thread only. This avoids mutating
        // JVM-wide security properties. The isolated class loader ensures that
        // Exceptions.ENHANCED_HOST_EXCEPTION_TEXT is initialized while this scoped mock
        // and the scoped System property view are active.
        try (var mocked = mockStatic(Security.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> Security.getProperty(INCLUDE_IN_EXCEPTIONS)).thenReturn("hostInfo");

            result = doWithExceptionInclusion(null, clazz -> {
                final Object si = filterHostInfo(clazz, "secret.example.com");
                return formatMsg(clazz, si);
            });
        }

        assertEquals("secret.example.com", result);
    }

    @Test(expected = IllegalFormatException.class)
    public void testFormatMsg_invalidFormat_throwsIllegalFormatException() throws Throwable {
        doWithExceptionInclusion("none", clazz -> {
            Object si = filterHostInfo(clazz, "secret.example.com");
            with(si, "withReplacement", "redacted");

            // formatMsg converts each SensitiveInfo argument to a String before delegating to
            // String.format(...). In secure mode, this particular SensitiveInfo object therefore
            // contributes the replacement text "redacted".
            //
            // The format string intentionally uses "%d", which requires an integral numeric
            // argument, not a String. This verifies that formatMsg does not hide or rewrite
            // formatting failures.
            //
            // String.format should reject the String argument and throw an IllegalFormatException.
            formatMsg(clazz, "%d", si);

            return null;
        });
    }

    @Test
    public void testSensitiveInfo_nullPrefixAndSuffix_usesEmptyStrings() throws Throwable {
        String result = doWithExceptionInclusion("hostInfo", clazz -> {
            Object si = filterHostInfo(clazz, "secret.example.com");
            with(si, "withPrefix", null);
            with(si, "withSuffix", null);
            return formatMsg(clazz, si);
        });
        assertEquals("secret.example.com", result);
    }

    @Test
    public void testSensitiveInfo_fluentChaining_returnsSameInstance() throws Throwable {
        Boolean result = doWithExceptionInclusion("none", clazz -> {
            Object si = filterHostInfo(clazz, "host");

            Object afterPrefix = with(si, "withPrefix", "p");
            Object afterSuffix = with(si, "withSuffix", "s");
            Object afterReplacement = with(si, "withReplacement", "r");

            return si == afterPrefix && si == afterSuffix && si == afterReplacement;
        });
        assertTrue(result);
    }

    @Test
    public void testWithReplacement_nullReplacement_usesEmptyReplacement() throws Throwable {
        String result = doWithExceptionInclusion("none", clazz -> {
            Object si = filterHostInfo(clazz, "host");
            with(si, "withReplacement", null);
            return formatMsg(clazz, "prefix %s suffix", si);
        });
        assertEquals("prefix suffix", result);
    }

    @Test
    public void testIsEnhanced_beforeOutputCalled_returnsFalse() throws Throwable {
        assertFalse(doWithExceptionInclusion(
                "hostInfo",
                clazz -> isEnhanced(filterHostInfo(clazz, "host"))));
    }

    @Test
    public void testOutput_unrelatedTokensOnly_returnsNonEnhanced() throws Throwable {
        Result result;

        result = doWithExceptionInclusion("stackTrace,other,, ", clazz -> {
            final Object si = filterHostInfo(clazz, "secret.example.com:443");
            return new Result(output(si), isEnhanced(si));
        });

        assertEquals("", result.message());
        assertFalse(result.enhanced());
    }

    private static <T> T doWithExceptionInclusion(
            String includeInExceptions,
            ThrowingFunction<Class<?>, T> fn) throws Throwable {
        // A scoped system property sandbox ensures the "jdk.includeInExceptions" property override
        // stays isolated to the test iteration.
        try (var env = ScopedSystemProperties.open()) {
            if (includeInExceptions != null) {
                env.setProperty(INCLUDE_IN_EXCEPTIONS, includeInExceptions);
            } else {
                env.clearProperty(INCLUDE_IN_EXCEPTIONS);
            }
            return IsolatedClassLoader.doInvoke(Exceptions.class, fn);
        }
    }

    private static Object filterHostInfo(Class<?> c, String value) throws Throwable {
        final Method method = c.getMethod("filterHostInfo", String.class);
        return method.invoke(null, value);
    }

    private static String formatMsg(Class<?> c, Object info) throws Throwable {
        final Method method = c.getMethod("formatMsg", getSensitiveInfoClass(c));
        return (String) method.invoke(null, info);
    }

    private static String formatMsg(Class<?> c, String format, Object... infos) throws Throwable {
        Class<?> infoClass = getSensitiveInfoClass(c);
        Object infoArray = Array.newInstance(infoClass, infos.length);

        for (int i = 0; i < infos.length; i++) {
            Array.set(infoArray, i, infos[i]);
        }

        final Method method = c.getMethod("formatMsg", String.class, infoArray.getClass());
        return (String) method.invoke(null, format, infoArray);
    }

    private static Object with(Object info, String methodName, String value) throws Throwable {
        return info.getClass().getMethod(methodName, String.class).invoke(info, value);
    }

    private static boolean isEnhanced(Object info) throws Throwable {
        return (Boolean) info.getClass().getMethod("isEnhanced").invoke(info);
    }

    private static String output(Object info) throws Throwable {
        return (String) info.getClass().getMethod("output").invoke(info);
    }

    private static Class<?> getSensitiveInfoClass(Class<?> c) throws ClassNotFoundException {
        return Class.forName(c.getName() + "$SensitiveInfo", false, c.getClassLoader());
    }
}
