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

/**
 * Shared utility methods.
 */
public final class SharedUtils {
    private SharedUtils() {}

    /**
     * Returns an identity-style string for the given object, in a form similar
     * to the default implementation of {@link Object#toString()}.
     * <p>
     * For a non-null object, this method returns a string consisting of the
     * name of the class of which the object is an instance, the at-sign
     * character ({@code @}), and the unsigned hexadecimal representation of the
     * hash code of the object. In other words, this method returns a string
     * equal to the value of:
     * <blockquote>
     * <pre>
     * obj.getClass().getName() + '@' + Integer.toHexString(hashCode)
     * </pre>
     * </blockquote>
     * where {@code hashCode} is the hash code returned by
     * {@link System#identityHashCode(Object)} for the given object.
     *
     * @param obj the object to format
     * @return an identity-style string for {@code obj}, or
     *         {@code "<uninitialized>"} if {@code obj} is null
     */
    public static String toIdentityString(Object obj) {
        if (obj == null) {
            return "<uninitialized>";
        }

        return obj.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(obj));
    }

    /**
     * Returns the given string with leading and trailing whitespace removed, or
     * {@code null} if the input is {@code null} or contains no non-whitespace
     * characters.
     * <p>
     * Whitespace is defined by {@link Character#isWhitespace(char)}.
     *
     * <pre>{@code
     * SharedUtils.stripToNull(null)     = null
     * SharedUtils.stripToNull("")       = null
     * SharedUtils.stripToNull("   ")    = null
     * SharedUtils.stripToNull("abc")    = "abc"
     * SharedUtils.stripToNull("  abc")  = "abc"
     * SharedUtils.stripToNull("abc  ")  = "abc"
     * SharedUtils.stripToNull(" abc ")  = "abc"
     * SharedUtils.stripToNull(" ab c ") = "ab c"
     * }</pre>
     *
     * @param s the string to normalize; may be {@code null}
     * @return the stripped string, or {@code null} if {@code s} is
     *         {@code null}, empty, or consists only of whitespace
     */
    public static String stripToNull(String s) {
        if (s == null) {
            return null;
        }

        s = s.strip();
        return s.isEmpty() ? null : s;
    }
}
