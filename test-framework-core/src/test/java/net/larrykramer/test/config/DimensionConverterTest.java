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
import org.openqa.selenium.Dimension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class DimensionConverterTest {
    @RunWith(Parameterized.class)
    public static class ParameterizedTest {
        private final String value;
        private final Dimension expected;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "1920x1080", new Dimension(1920, 1080) },
                    { "800X600", new Dimension(800, 600) },
                    { "300 , 200", new Dimension(300, 200) },
                    { "  1024 x  768  ", new Dimension(1024, 768) },
                    { "-1600x900", new Dimension(-1600, 900) },
                    { "2560x-1440", new Dimension(2560, -1440) },
                    { "-640x-480", new Dimension(-640, -480) },
                    { "0x0", new Dimension(0, 0) },
                    { "   ", null }
            });
        }

        public ParameterizedTest(String value, Dimension expected) {
            this.value = value;
            this.expected = expected;
        }

        @Test
        public void testConvert_supportedFormats_returnsExpectedDimension() {
            assertEquals(expected, new DimensionConverter().convert(value));
        }
    }

    public static class InvalidInputTest {
        @Test
        public void testConvert_invalidFormat_throwsIllegalArgumentException() {
            DimensionConverter converter = new DimensionConverter();

            var expected = IllegalArgumentException.class;
            var e = assertThrows(expected, () -> converter.convert("1920*1080"));
            assertTrue(e.getMessage().contains("<width>x<height>"));
            assertTrue(e.getMessage().contains("<width>,<height>"));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testConvert_nonNumericHeight_throwsIllegalArgumentException() {
            new DimensionConverter().convert("1920xabc");
        }

        @Test(expected = NullPointerException.class)
        public void testConvert_nullInput_throwsNullPointerException() {
            new DimensionConverter().convert(null);
        }
    }
}
