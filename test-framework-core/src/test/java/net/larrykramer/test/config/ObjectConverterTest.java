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

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(Enclosed.class)
public class ObjectConverterTest {
    @RunWith(Parameterized.class)
    public static class ParameterizedTest {
        private final String input;
        private final Object expected;
        private final Class<?> expectedClass;
        private final boolean expectSameInstance;

        @Parameterized.Parameters(name = "{index}: convert(\"{0}\") -> {1}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "   ", "", String.class, false },
                    { "true  ", Boolean.TRUE, Boolean.class, true },
                    { "  yes  ", Boolean.TRUE, Boolean.class, true },
                    { "y", Boolean.TRUE, Boolean.class, true },
                    { "  on", Boolean.TRUE, Boolean.class, true },
                    { "  false", Boolean.FALSE, Boolean.class, true },
                    { "no  ", Boolean.FALSE, Boolean.class, true },
                    { "  n  ", Boolean.FALSE, Boolean.class, true },
                    { "off", Boolean.FALSE, Boolean.class, true },
                    { "0", 0, Integer.class, false },
                    { "1", 1, Integer.class, false },
                    { "42", 42, Integer.class, false },
                    { "-5", -5, Integer.class, false },
                    { "3.1415", 3.1415d, Double.class, false },
                    { "-0.5", -0.5d, Double.class, false},
                    { "1e5", 1e5d, Double.class, false },
                    { "  some text  ", "  some text  ", String.class, true },
                    { "123a", "123a", String.class, true },
                    { "3.14.15", "3.14.15", String.class, true },
                    { "\"123\"", "123", String.class, false },
                    { "'true'", "true", String.class, false },
                    { "  \"  padded  \" ", "  padded  ", String.class, false },
                    { "  '0'  ", "0", String.class, false },
                    { "1'", "1'", String.class, false },
                    { "123\"", "123\"", String.class, false },
                    { "\"\"", "", String.class, false },
                    { "''", "", String.class, false },
            });
        }

        public ParameterizedTest(String input,
                Object expected, Class<?> expectedClass, boolean expectSameInstance) {
            this.input = input;
            this.expected = expected;
            this.expectedClass = expectedClass;
            this.expectSameInstance = expectSameInstance;
        }

        @Test
        public void testConvert_supportedValues_returnsExpectedResult() {
            Object result = new ObjectConverter().convert(input);
            assertNotNull(result);
            assertEquals(expectedClass, result.getClass());
            if (expectSameInstance) {
                assertSame(expected, result);
            } else if (expectedClass == Double.class) {
                assertEquals((Double) expected, (Double) result, 0.0000001d);
            } else {
                assertEquals(expected, result);
            }
        }
    }

    public static class InvalidInputTest {
        @Test(expected = IllegalArgumentException.class)
        public void testConvert_unmatchedDoubleQuote_throwsIllegalArgumentException() {
            new ObjectConverter().convert("\"abc");
        }

        @Test(expected = IllegalArgumentException.class)
        public void testConvert_unmatchedSingleQuote_throwsIllegalArgumentException() {
            new ObjectConverter().convert("'abc");
        }

        @Test(expected = IllegalArgumentException.class)
        public void testConvert_singleQuoteOnly_throwsIllegalArgumentException() {
            new ObjectConverter().convert("'");
        }

        @Test(expected = NullPointerException.class)
        public void testConvert_nullInput_throwsNullPointerException() {
            new ObjectConverter().convert(null);
        }
    }
}
