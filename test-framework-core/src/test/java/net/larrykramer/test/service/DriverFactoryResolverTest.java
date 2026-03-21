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

package net.larrykramer.test.service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.inject.spi.Bean;
import net.larrykramer.test.webdriver.DriverFactory;
import org.junit.Test;

import static net.larrykramer.test.service.DriverFactoryResolver.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class DriverFactoryResolverTest {
    @Test
    public void testGetUnproxiedClass_plainClass_returnsSameClass() {
        assertSame(String.class, getUnproxiedClass(String.class));
    }

    @Test
    public void testGetUnproxiedClass_proxySubclass_returnsBaseClass() {
        assertSame(Base.class, getUnproxiedClass(Base$$Proxy.class));
    }

    @Test
    public void testGetUnproxiedClass_jdkProxy_returnsProxyClass() {
        //noinspection SuspiciousInvocationHandlerImplementation
        Object proxyInstance = Proxy.newProxyInstance(TestInterface.class.getClassLoader(),
                new Class<?>[] { TestInterface.class },
                (proxy, method, args) -> null);
        assertSame(Proxy.class, getUnproxiedClass(proxyInstance.getClass()));
    }

    @Test
    public void testGetUnproxiedClass_proxyExtendsObject_returnsSameClass() {
        //@formatter:off
        class Object$$Proxy {}
        //@formatter:on

        assertSame(Object$$Proxy.class, getUnproxiedClass(Object$$Proxy.class));
    }

    @Test
    public void testGetUnproxiedClass_interfaceType_returnsSameInterface() {
        //@formatter:off
        interface Interface$$Proxy {}
        //@formatter:on

        assertSame(Interface$$Proxy.class, getUnproxiedClass(Interface$$Proxy.class));
    }

    @Test(expected = NullPointerException.class)
    public void testGetUnproxiedClass_nullClass_throwsNullPointerException() {
        getUnproxiedClass(null);
    }

    @Test
    public void testResolveBeanClassName_nullBean_returnsNull() {
        assertNull(resolveBeanClassName(null));
    }

    @Test
    public void testResolveBeanClassName_directBean_returnsBeanClassName() {
        Class<?> mockFactoryClass = mock(DriverFactory.class).getClass();
        Bean<?> mockBean = mock(Bean.class);
        doReturn(mockFactoryClass).when(mockBean).getBeanClass();
        assertEquals(mockFactoryClass.getName(), resolveBeanClassName(mockBean));
    }

    @Test
    public void testResolveBeanClassName_producerBean_returnsFactoryClassName() {
        // Arrange
        // Simulates a Producer Bean which produces a concrete factory.
        // getBeanClass() return the Producer (FactoryProducer), NOT the factory.
        //@formatter:off
        @SuppressWarnings("rawtypes")
        abstract class TestDriverFactory extends DriverFactory {}
        class FactoryProducer {}
        //@formatter:on

        DriverFactory<?> mockFirstFactory = mock(DriverFactory.class);
        TestDriverFactory mockSecondFactory = mock(TestDriverFactory.class);

        // We use a LinkedHashSet to enforce a specific iteration order.
        // We place the "wrong" types first to ensure the loop logic correctly filters them out
        // before finding the correct concrete factory.
        LinkedHashSet<Type> types = new LinkedHashSet<>();
        types.add(mock(ParameterizedType.class));
        types.add(Object.class);
        types.add(DriverFactory.class);
        types.add(TestDriverFactory.class);
        types.add(mockFirstFactory.getClass());
        types.add(mockSecondFactory.getClass());

        @SuppressWarnings("unchecked")
        Bean<DriverFactory<?>> mockBean = mock(Bean.class);
        doReturn(FactoryProducer.class).when(mockBean).getBeanClass();
        doReturn(types).when(mockBean).getTypes();

        // Act
        String result = resolveBeanClassName(mockBean);

        // Assert
        assertEquals(mockFirstFactory.getClass().getName(), result);
    }

    @Test
    public void testResolveBeanClassName_nonFactoryProducer_returnsNull() {
        // Simulates a Producer Bean which does not produce a concrete factory.
        // getTypes() contains no DriverFactory implementation to resolve.
        Bean<?> mockBean = mock(Bean.class);
        doReturn(String.class).when(mockBean).getBeanClass();
        doReturn(Set.of(Object.class, Integer.class)).when(mockBean).getTypes();
        assertNull(resolveBeanClassName(mockBean));
    }

    @Test
    public void testGetDriverFactoryClassName_namedBean_returnsBeanName() {
        Bean<?> mockBean = mock(Bean.class);
        doReturn("  \u2003my-factory  \u2029  ").when(mockBean).getName();
        assertEquals("my-factory", getDriverFactoryClassName(mockBean, null));
    }

    @Test
    public void testGetDriverFactoryClassName_unnamedBean_returnsBeanClassName() {
        DriverFactory<?> mockFactory = mock(DriverFactory.class);
        Bean<?> mockBean = mock(Bean.class);
        doReturn("  \u2005  ").when(mockBean).getName();
        doReturn(mockFactory.getClass()).when(mockBean).getBeanClass();
        assertEquals(mockFactory.getClass().getName(), getDriverFactoryClassName(mockBean, null));
    }

    @Test
    public void testGetDriverFactoryClassName_proxyFactory_returnsProxyName() {
        // Simulates a proxy subclass whose superclass is an abstract factory.
        // Since the superclass is not a concrete factory, the proxy name is returned.
        //@formatter:off
        @SuppressWarnings("rawtypes")
        abstract class TestDriverFactory_$$_WeldClientProxy extends DriverFactory {}
        //@formatter:on

        DriverFactory<?> mockFactory = mock(TestDriverFactory_$$_WeldClientProxy.class);

        String result = getDriverFactoryClassName(null, mockFactory);
        assertEquals(mockFactory.getClass().getName(), result);
    }

    @Test
    public void testGetDriverFactoryClassName_factoryInstance_returnsFactoryClassName() {
        DriverFactory<?> mockFactory = mock(DriverFactory.class);
        String result = getDriverFactoryClassName(null, mockFactory);
        assertEquals(mockFactory.getClass().getName(), result);
    }

    @Test
    public void testIsDriverFactoryClass_concreteFactory_returnsTrue() {
        assertTrue(isDriverFactoryClass(mock(DriverFactory.class).getClass()));
    }

    @Test
    public void testIsDriverFactoryClass_abstractFactory_returnsFalse() {
        //@formatter:off
        @SuppressWarnings("rawtypes")
        abstract class TestDriverFactory extends DriverFactory {}
        //@formatter:on

        assertFalse(isDriverFactoryClass(TestDriverFactory.class));
    }

    @Test
    public void testIsDriverFactoryClass_nonFactory_returnsFalse() {
        assertFalse(isDriverFactoryClass(String.class));
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
