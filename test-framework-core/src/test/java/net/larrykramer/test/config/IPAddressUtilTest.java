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

package net.larrykramer.test.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import net.larrykramer.test.util.IsolatedClassLoader;
import net.larrykramer.test.util.ScopedSystemProperties;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class IPAddressUtilTest {
    public abstract static class IPAddressUtilTestBase {
        protected boolean invokeIsIPv6LiteralAddress(String input, boolean allowAmbiguous)
                throws Throwable {
            // A scoped system property sandbox ensures the
            // "jdk.net.allowAmbiguousIPAddressLiterals" property override stays isolated to the
            // test iteration.
            try (var env = ScopedSystemProperties.open()) {
                String value = Boolean.toString(allowAmbiguous);
                env.setProperty("jdk.net.allowAmbiguousIPAddressLiterals", value);

                return IsolatedClassLoader.doInvoke(IPAddressUtil.class, clazz -> {
                    // Reflectively invoke IPAddressUtil.isIPv6LiteralAddress and return its result.
                    // A Boolean from the bootstrap loader can cross the class-loader boundaries
                    // without adaptation.
                    Method method = clazz.getDeclaredMethod("isIPv6LiteralAddress", String.class);
                    method.setAccessible(true);
                    return (Boolean) method.invoke(null, input);
                });
            }
        }
    }

    @RunWith(Parameterized.class)
    public static class ParameterizedTest extends IPAddressUtilTestBase {
        private final String input;
        private final boolean expected;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    // Valid IPv6 Addresses.
                    { "2001:db8:85a3:0:0:8a2e:370:7334", true },
                    { "::1", true },
                    { "fe80::1%eth0", true },
                    { "::ffff:192.168.0.1", true },
                    { "100::ffff:192.168.0.1", true },
                    { "1::ffff:192.168.0.1", true },
                    { "0:100::ffff:192.168.0.1", true },
                    { "0:1::ffff:192.168.0.1", true },
                    { "0:0:100::ffff:192.168.0.1", true },
                    { "0:0:1::ffff:192.168.0.1", true },
                    { "0:0:0:100::ffff:192.168.0.1", true },
                    { "0:0:0:1::ffff:192.168.0.1", true },
                    { "0:0:0:0:100:ffff:192.168.0.1", true },
                    { "0:0:0:0:1:ffff:192.168.0.1", true },
                    { "0:0:0:0:0:efff:192.168.0.1", true },
                    { "0:0:0:0:0:ffef:192.168.0.1", true },
                    // Invalid IPv6 addresses.
                    { "", false },
                    { ":1", false },
                    { "fe80::1%", false },
                    { "1:", false },
                    { ":::", false },
                    { "100000::", false },
                    { "1::g", false },
                    { "1:2:3:4:5:6:7", false },
                    { "1:2:3:4:5:6:7:8::", false },
                    { "1:1:1:1:1:1:1:1:1", false },
                    { "1:1:1:1:1:1:1:1:1::1", false },
                    { "::ffff:192.168.1", false },
                    { "::ffff:999.1.1.1", false },
                    { "::ffff:192.168.f.1", false },
                    { "::ffff:1111.1111.1111.1111", false },
                    { "::ffff:.1.1.1", false },
                    { "::ffff:1.2.3.", false },
                    { "1:2:3:4:5:6:7:1.2.3.4", false },
                    { "::ffff:1.2.3.256", false },
                    { "1:2:3:4:5:6:7::8", false },
                    { "::ffff:١.٢.٣.٤", false }
            });
        }

        public ParameterizedTest(String input, Boolean expected) {
            this.input = input;
            this.expected = expected;
        }

        @Test
        public void testIsIPv6LiteralAddress_sampleInput_returnsExpectedValue() throws Throwable {
            assertEquals(expected, invokeIsIPv6LiteralAddress(input, false));
        }
    }

    public static class NonParameterizedTest extends IPAddressUtilTestBase {
        @Test
        public void testIsIPv6LiteralAddress_ambiguousInputAllowed_returnsTrue() throws Throwable {
            assertTrue(invokeIsIPv6LiteralAddress("::ffff:١.٢.٣.٤", true));
        }

        @Test(expected = NullPointerException.class)
        public void testIsIPv6LiteralAddress_nullInput_throwsNullPointerException() {
            IPAddressUtil.isIPv6LiteralAddress(null);
        }
    }
}
