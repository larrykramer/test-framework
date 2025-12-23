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

import java.util.Objects;

/**
 * Shared utility methods.
 */
public class SharedUtils {
    private SharedUtils() {
        // Ensure there is only one instance of this utility class.
    }

    /**
     * Returns an identity-style string for the given object, in a form similar
     * to the default implementation of {@link Object#toString()}.
     *
     * @implSpec
     * For a non-{@code null} object, this method returns a string consisting of
     * the name of the class of which the object is an instance, the at-sign
     * character `{@code @}', and the unsigned hexadecimal representation of the
     * hash code of the object. In other words, this method returns a string
     * equal to the value of:
     * <blockquote>
     * <pre>
     * obj.getClass().getName() + '@' + Integer.toHexString(hashCode)
     * </pre>
     * </blockquote>
     * where {@code hashCode} is the hash code returned by
     * {@link System#identityHashCode(Object)} for the given object.
     * <p>
     * If {@code obj} is {@code null}, this method returns
     * {@code "<uninitialized>"}.
     *
     * @param obj the object to format; may be {@code null}
     * @return an identity-style string for {@code obj}, or
     *         {@code "<uninitialized>"} if {@code obj} is {@code null}
     */
    public static String identityToString(Object obj) {
        if (obj == null) {
            return "<uninitialized>";
        }

        return obj.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(obj));
    }

    /**
     * Attempts to return the "real" (unproxied) class for the given class.
     * <p>
     * This method is intended for frameworks that create subclass-based proxies
     * (e.g., CDI/Weld/Quarkus). It walks up the superclass hierarchy while the
     * current class appears to be a proxy and returns the first superclass that
     * no longer looks proxied.
     * <p>
     * Note: for JDK dynamic proxy classes, there is no meaningful "user class"
     * in the superclass chain (dynamic proxies implement interfaces), so this
     * method will typically return {@link java.lang.reflect.Proxy}.
     *
     * @param c the class to inspect; must not be {@code null}
     * @return the unproxied/base class, or {@code c} if {@code c} is not
     *         recognized as a proxy (or if unproxying cannot proceed beyond
     *         {@link Object})
     * @throws NullPointerException if {@code c} is {@code null}
     */
    public static Class<?> getUnproxiedClass(Class<?> c) {
        Objects.requireNonNull(c);
        Class<?> unproxiedClass = c;
        while (isProxiedClass(unproxiedClass)) {
            Class<?> clazz = unproxiedClass.getSuperclass();
            if (clazz == null || clazz == Object.class) {
                break;
            }
            unproxiedClass = clazz;
        }
        return unproxiedClass;
    }

    private static boolean isProxiedClass(Class<?> c) {
        String name = c.getName();
        return name.contains("$$") // CDI
                || name.contains("_WeldClientProxy") // Weld
                || name.contains("_ClientProxy") // Quarkus
                || java.lang.reflect.Proxy.isProxyClass(c); // JDK
    }
}
