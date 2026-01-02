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

package net.larrykramer.test.webdriver;

import java.lang.reflect.Field;

import jakarta.inject.Inject;
import net.larrykramer.test.config.DriverConfig;
import org.eclipse.microprofile.config.inject.ConfigProperties;

/**
 * Utility helpers for test suites that need to override factory configuration.
 * <p>
 * Provides convenient methods to replace the {@link DriverConfig} used by a
 * {@link DriverFactory} and to inject configuration objects into fields
 * annotated with {@code @Inject} and {@code @ConfigProperties}.
 */
public final class DriverFactoryTestHelper {
    private DriverFactoryTestHelper() {
        // Ensure there is only one instance of this utility class.
    }

    /**
     * Overrides the {@code DriverConfig} instance held by the supplied
     * {@code DriverFactory}.
     * <p>
     * This helper is intended for use in tests where direct control over the
     * factory's configuration is required.
     *
     * @param factory the factory whose configuration should be replaced
     * @param config  the configuration instance to inject
     * @apiNote Tests may deliberately pass {@code null} to simulate
     *          misconfiguration.
     */
    public static void setDriverConfig(DriverFactory<?> factory, DriverConfig config) {
        factory.config = config;
    }

    /**
     * Reflectively assigns a configuration object to a field that is expected
     * to be annotated with both {@code @Inject} and {@code @ConfigProperties}.
     *
     * @param target the object containing the field to update
     * @param name   the name of the field to set
     * @param config the configuration instance to assign to the field
     * @throws IllegalArgumentException if the field cannot be found
     * @throws IllegalStateException    if the field is not annotated with both
     *                                  {@code @Inject} and
     *                                  {@code @ConfigProperties}
     * @throws RuntimeException         if the field cannot be set due to access
     *                                  restrictions or type mismatches
     */
    public static void setConfigField(Object target, String name, Object config) {
        Class<?> clazz = target.getClass();
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);

            boolean hasInject = field.isAnnotationPresent(Inject.class);
            boolean hasConfigProperties = field.isAnnotationPresent(ConfigProperties.class);
            if (!hasInject || !hasConfigProperties) {
                String msg = "Field '" + name + "' in " + clazz.getName()
                        + " must be annotated with both @Inject and @ConfigProperties. Found: "
                        + (hasInject ? "@Inject" : "no @Inject")
                        + " and "
                        + (hasConfigProperties ? "@ConfigProperties" : "no @ConfigProperties");
                throw new IllegalStateException(msg);
            }

            field.set(target, config);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new RuntimeException(
                    "Unable to set field '" + name + "' in class " + clazz.getName(), e);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                    "Field '" + name + "' not found in class " + clazz.getName(), e);
        }
    }
}
