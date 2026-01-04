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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DriverTypeTest {
    @Test
    public void testOf_givenMatchingName_returnsEnumConstant() {
        assertEquals(DriverType.CHROME, DriverType.of("CHROME"));
    }

    @Test
    public void testOf_givenMixedCaseName_returnsEnumConstant() {
        assertEquals(DriverType.SAFARI, DriverType.of("sAfArI"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOf_givenInvalidName_throwsIllegalArgumentException() {
        DriverType.of("opera");
    }

    @Test(expected = NullPointerException.class)
    public void testOf_givenNull_throwsNullPointerException() {
        DriverType.of(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOf_givenNameWithWhitespace_throwsIllegalArgumentException() {
        DriverType.of("CHROME  ");
    }

    @Test
    public void testGetCanonicalName_givenFirefox_returnsFirefox() {
        assertEquals("firefox", DriverType.FIREFOX.getCanonicalName());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetCanonicalName_givenSPI_throwsUnsupportedOperationException() {
        DriverType.SPI.getCanonicalName();
    }
}
