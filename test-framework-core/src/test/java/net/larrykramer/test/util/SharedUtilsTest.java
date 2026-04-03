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

import org.junit.Test;

import static net.larrykramer.test.util.SharedUtils.stripToNull;
import static net.larrykramer.test.util.SharedUtils.toIdentityString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SharedUtilsTest {
    @Test
    public void testToIdentityString_nullInput_returnsUninitialized() {
        assertEquals("<uninitialized>", toIdentityString(null));
    }

    @Test
    public void testToIdentityString_objectInput_returnsIdentityStyleString() {
        Object obj = new Object();
        String expected = obj.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(obj));
        assertEquals(expected, toIdentityString(obj));
    }

    @Test
    public void testStripToNull_nullInput_returnsNull() {
        assertNull(stripToNull(null));
    }

    @Test
    public void testStripToNull_emptyString_returnsNull() {
        assertNull(stripToNull(""));
    }

    @Test
    public void testStripToNull_nonBlankInput_returnsSameContent() {
        assertEquals("value", stripToNull("value"));
    }

    @Test
    public void testStripToNull_asciiWhitespace_returnsStrippedString() {
        assertEquals("value", stripToNull("  value  "));
    }

    @Test
    public void testStripToNull_unicodeWhitespace_returnsStrippedString() {
        assertEquals("value", stripToNull("\u2003value\u2003"));
    }

    @Test
    public void testStripToNull_whitespaceOnlyInput_returnsNull() {
        assertNull(stripToNull(" \t\n "));
    }

    @Test
    public void testStripToNull_paddedInputWithInternalSpace_returnsStrippedString() {
        assertEquals("ab c", stripToNull("  ab c\u2003  "));
    }
}
