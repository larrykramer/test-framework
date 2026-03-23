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

import java.lang.reflect.Method;
import java.util.*;

import net.larrykramer.test.util.IsolatedClassLoader;
import net.larrykramer.test.util.ScopedSystemProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class DefaultTypeConfigSourceTest {
    @RunWith(Parameterized.class)
    public static class ParameterizedTest {
        private final String osName;
        private final String expected;

        @Parameterized.Parameters(name = "OS: {0} -> Driver: {1}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    // Windows
                    { "Windows 11", "EDGE" },
                    // macOS
                    { "macOS", "SAFARI" },
                    // Linux
                    { "Linux", "FIREFOX" },
                    // Unsupported
                    // Note: AIX is supported by the JDK, but it is not officially supported by
                    // this project. We just default to the Firefox Driver similar to other
                    // unsupported operating systems.
                    { "AIX", "FIREFOX" },
                    { "Solaris", "FIREFOX" },
                    { "Plan 9", "FIREFOX" },
                    { "FreeBSD", "FIREFOX" },
                    // Edge cases
                    { "", "FIREFOX" },
                    { null, "FIREFOX" }
            });
        }

        public ParameterizedTest(String osName, String expected) {
            this.osName = osName;
            this.expected = expected;
        }

        @Test
        public void testGetValue_givenOSName_returnsCorrectDefaultDriverType() throws Throwable {
            // A scoped system property sandbox ensures the "os.name" property override stays
            // isolated to the test iteration.
            try (var env = ScopedSystemProperties.open()) {
                if (osName != null) {
                    env.setProperty("os.name", osName);
                } else {
                    env.clearProperty("os.name");
                }

                String result;
                result = IsolatedClassLoader.doInvoke(DefaultTypeConfigSource.class, clazz -> {
                    // Reflectively invoke 'new DefaultTypeConfigSource().getValue' and return
                    // its result. A String from the bootstrap loader can cross the class-loader
                    // boundaries without adaptation.
                    final Object instance = clazz.getConstructor().newInstance();
                    final Method method = clazz.getMethod("getValue", String.class);
                    return (String) method.invoke(instance, "driver.type");
                });

                assertEquals(expected, result);
            }
        }
    }

    public static class NonParameterizedTest {
        private DefaultTypeConfigSource configSource;

        @Before
        public void setUp() {
            configSource = new DefaultTypeConfigSource();
        }

        @Test
        public void testGetProperties_whenCalled_returnsMapWithCorrectStructure() {
            Map<String, String> properties = configSource.getProperties();
            assertNotNull(properties);
            assertEquals(1, properties.size());
            assertTrue(properties.containsKey("driver.type"));
            assertNotNull(properties.get("driver.type"));
        }

        @Test
        public void testGetPropertyNames_whenCalled_returnsCorrectPropertySet() {
            Set<String> propertyNames = configSource.getPropertyNames();
            assertNotNull(propertyNames);
            assertEquals(Set.of("driver.type"), propertyNames);
        }

        @Test
        public void testGetOrdinal_whenCalled_returnsLowPriorityValue() {
            assertTrue(configSource.getOrdinal() < 100);
        }

        @Test
        public void testGetValue_withNullPropertyName_returnsNull() {
            assertNull(configSource.getValue(null));
        }

        @Test
        public void testGetValue_withMissingPropertyName_returnsNull() {
            assertNull(configSource.getValue("absent"));
        }

        @Test
        public void testGetName_whenCalled_returnsDescriptiveName() {
            assertEquals("OS-Aware Default WebDriver Config Source", configSource.getName());
        }
    }
}
