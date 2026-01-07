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

import java.lang.reflect.Proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SharedUtilsTest {
    @Test
    public void testIdentityToString_givenNull_returnsUninitialized() {
        assertEquals("<uninitialized>", SharedUtils.identityToString(null));
    }

    @Test
    public void testIdentityToString_givenObject_returnsIdentityStyleString() {
        // Arrange
        Object obj = new Object();
        String expected = obj.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(obj));
        // Act
        String result = SharedUtils.identityToString(obj);
        // Assert
        assertEquals(expected, result);
    }

    @Test
    public void testGetUnproxiedClass_givenRegularClass_returnsSameClass() {
        assertSame(String.class, SharedUtils.getUnproxiedClass(String.class));
    }

    @Test
    public void testGetUnproxiedClass_givenProxySubclassChain_returnsBaseClass() {
        assertSame(Base.class, SharedUtils.getUnproxiedClass(Base$$Proxy.class));
    }

    @Test
    public void testGetUnproxiedClass_givenJDKDynamicProxy_returnsProxyClass() {
        // Arrange
        //noinspection SuspiciousInvocationHandlerImplementation
        Object proxyInstance = Proxy.newProxyInstance(TestInterface.class.getClassLoader(),
                new Class<?>[] { TestInterface.class },
                (proxy, method, args) -> null);
        // Act & Assert
        assertSame(Proxy.class, SharedUtils.getUnproxiedClass(proxyInstance.getClass()));
    }

    @Test
    public void testGetUnproxiedClass_givenProxyExtendsObject_returnsSameClass() {
        //@formatter:off
        class Object$$Proxy {}
        //@formatter:on
        assertSame(Object$$Proxy.class, SharedUtils.getUnproxiedClass(Object$$Proxy.class));
    }

    @Test
    public void testGetUnproxiedClass_givenProxyNamedInterface_returnsSameInterface() {
        //@formatter:off
        interface Interface$$Proxy {}
        //@formatter:on
        assertSame(Interface$$Proxy.class, SharedUtils.getUnproxiedClass(Interface$$Proxy.class));
    }

    @Test(expected = NullPointerException.class)
    public void testGetUnproxiedClass_givenNull_throwsNullPointerException() {
        SharedUtils.getUnproxiedClass(null);
    }

    // -- Class Definitions --

    //@formatter:off
    public interface TestInterface {}
    static class Base {}
    static class Base_ClientProxy extends Base {}
    static class Base_WeldClientProxy extends Base_ClientProxy {}
    static class Base$$Proxy extends Base_WeldClientProxy {}
    //@formatter:on
}
