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

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Objects;

import jakarta.enterprise.inject.spi.Bean;
import net.larrykramer.test.webdriver.DriverFactory;

/**
 * Utility methods for resolving the concrete {@link DriverFactory} type and
 * name from either CDI {@code Bean} metadata or a factory instance.
 * <p>
 * This helper is intended for environments where driver factories may be
 * represented by managed beans or framework-generated proxies, and a stable
 * class-based identifier is needed for lookup, registration, or reporting.
 * <p>
 * The resolver favors bean metadata when available and otherwise derives the
 * result from the supplied factory instance.
 */
final class DriverFactoryResolver {
    private DriverFactoryResolver() {}

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
     * method will typically return {@code Proxy}.
     *
     * @param c the class to inspect
     * @return the unproxied/base class, or {@code c} if {@code c} is not
     *         recognized as a proxy (or if unproxying cannot proceed beyond
     *         {@code Object})
     * @throws NullPointerException if {@code c} is null
     */
    static Class<?> getUnproxiedClass(Class<?> c) {
        Objects.requireNonNull(c, "c is null");
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
                || Proxy.isProxyClass(c); // JDK
    }

    /**
     * Resolves the fully qualified class name of the concrete
     * {@link DriverFactory} type represented by the supplied bean.
     * <p>
     * This method is intended for cases where a driver factory name should be
     * derived from bean metadata rather than from a factory instance.
     *
     * @param bean the bean whose driver factory class name should be resolved
     * @return the fully qualified class name of the represented concrete
     *         {@link DriverFactory}, or {@code null} if {@code bean} is
     *         {@code null} or no suitable driver factory class can be
     *         determined
     */
    static String resolveBeanClassName(Bean<?> bean) {
        if (bean == null) {
            return null;
        }

        // Managed bean case: bean class is the implementation class.
        if (isDriverFactoryClass(bean.getBeanClass())) {
            return bean.getBeanClass().getName();
        }

        // Producer case.
        // beanClass is producer holder: scan types for a concrete factory class.
        Class<?> beanClass = null;
        for (var t : bean.getTypes()) {
            if (t instanceof Class<?> clazz
                    && isDriverFactoryClass(clazz)
                    && (beanClass == null || beanClass.isAssignableFrom(clazz))) {
                beanClass = clazz;
            }
        }

        return (beanClass != null) ? beanClass.getName() : null;
    }

    /**
     * Determines a name for the supplied driver factory.
     * <p>
     * When bean metadata is available, the resolved name is derived from that
     * metadata. Otherwise, the name is derived from the factory instance
     * itself.
     *
     * @param bean    the bean associated with the factory, or {@code null} if
     *                no bean metadata is available
     * @param factory the driver factory instance for which a name is required
     * @return the resolved driver factory name
     */
    static String getDriverFactoryClassName(Bean<?> bean, DriverFactory<?> factory) {
        String name = null;

        if (bean != null) {
            // Bean name (if present/non-blank) has priority over the bean class name, if both are
            // defined.
            name = bean.getName();
            if (name != null && !name.isBlank()) {
                name = name.strip();
            } else {
                name = resolveBeanClassName(bean); // maybe null
            }
        }

        if (name == null) {
            Class<?> clazz = getUnproxiedClass(factory.getClass());
            if (!isDriverFactoryClass(clazz)) {
                // Last resort. Might be a proxy name.
                clazz = factory.getClass();
            }
            name = clazz.getName();
        }

        return name;
    }

    /**
     * Determines whether the supplied class is a concrete {@link DriverFactory}
     * type.
     *
     * @param c the class to test
     * @return {@code true} if {@code c} represents a non-abstract
     *         {@link DriverFactory} class; {@code false} otherwise
     */
    static boolean isDriverFactoryClass(Class<?> c) {
        return DriverFactory.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers());
    }
}
