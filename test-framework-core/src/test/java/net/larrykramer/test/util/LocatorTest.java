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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocatorTest {
    @Test
    public void testConstructor_paddedType_normalizesTypeToLowercase() {
        Locator locator = new Locator("  XPATH  ", "button");
        assertEquals("xpath", locator.type());
        assertEquals("button", locator.selector());
    }

    @Test
    public void testConstructor_paddedSelector_trimsSelector() {
        Locator locator = new Locator("css", "   div > span   ");
        assertEquals("css", locator.type());
        assertEquals("div > span", locator.selector());
    }

    @Test
    public void testConstructor_quotedSelector_preservesInnerWhitespace() {
        Locator locator = new Locator("css", " '  div span  ' ");
        assertEquals("css", locator.type());
        assertEquals("  div span  ", locator.selector());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_blankQuotedSelector_throwsIllegalArgumentException() {
        new Locator("css", "  \" \" ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_unmatchedDoubleQuote_throwsIllegalArgumentException() {
        new Locator("css", "\"div");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_singleQuoteOnly_throwsIllegalArgumentException() {
        new Locator("css", "'");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullType_throwsIllegalArgumentException() {
        new Locator(null, "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_blankType_throwsIllegalArgumentException() {
        new Locator("   ", "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullSelector_throwsIllegalArgumentException() {
        new Locator("css", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_blankSelector_throwsIllegalArgumentException() {
        new Locator("css", "   ");
    }
}
