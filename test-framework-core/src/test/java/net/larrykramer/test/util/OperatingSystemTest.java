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

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class OperatingSystemTest {
    @RunWith(Parameterized.class)
    public static class ParameterizedTest {
        private final String osName;
        private final OperatingSystem expected;

        @Parameterized.Parameters(name = "os.name = \"{0}\"")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    // macOS
                    { "Mac OS X", OperatingSystem.MACOS },
                    { "maCOS 14", OperatingSystem.MACOS },
                    // Windows
                    { "Windows 11", OperatingSystem.WINDOWS },
                    { "Windows Server 2022", OperatingSystem.WINDOWS },
                    { "   Windows for Workgroups 3.11 ", OperatingSystem.WINDOWS },
                    // Linux
                    { "Linux", OperatingSystem.LINUX },
                    { "linux-gnu", OperatingSystem.LINUX },
                    { "Linux Mint", OperatingSystem.LINUX },
                    { "liNUx   6.12.55 ", OperatingSystem.LINUX },
                    // AIX is supported by the JDK but there is no Selenium driver support for it.
                    // Therefore, it is not officially supported by this project.
                    { "AIX", OperatingSystem.UNSUPPORTED },
                    // Unsupported
                    { "Solaris", OperatingSystem.UNSUPPORTED },
                    { "Plan 9", OperatingSystem.UNSUPPORTED },
                    { "FreeBSD", OperatingSystem.UNSUPPORTED },
                    { "", OperatingSystem.UNSUPPORTED },
                    { "   ", OperatingSystem.UNSUPPORTED },
                    // A null os.name System Property uses the default UNSUPPORTED value.
                    { null, OperatingSystem.UNSUPPORTED }
            });
        }

        public ParameterizedTest(String osName, OperatingSystem expected) {
            this.osName = osName;
            this.expected = expected;
        }

        @Test
        public void testCurrent_givenOSName_returnsCorrectOperatingSystemEnum() throws Throwable {
            // Arrange
            // The 'osName' and 'expected' are from the @Parameters.
            // A scoped system property sandbox ensures the "os.name" property override stays
            // isolated to the test iteration.
            try (var env = ScopedSystemProperties.open()) {
                if (osName != null) {
                    env.setProperty("os.name", osName);
                } else {
                    env.clearProperty("os.name");
                }

                // Act
                OperatingSystem result;
                result = IsolatedClassLoader.doInvoke(OperatingSystem.class, clazz -> {
                    // Reflectively invoke OperatingSystem.current and adapt its returned enum
                    // constant (which lives in the isolated class loader) into this test's
                    // OperatingSystem enum.
                    final Object os = clazz.getMethod("current").invoke(null);
                    return OperatingSystem.valueOf(os.toString());
                });

                // Assert
                assertEquals(expected, result);
            }
        }
    }

    public static class IntegrationTest {
        @Test
        public void testEnum_onMacOS_isConsistentWithActualOperatingSystem() {
            // Arrange
            // This test will only run its assertions if the current OS is macOS.
            // On other system, it will be marked as "skipped".
            assumeTrue("Skipping macOS-specific test", OperatingSystem.isMacOS());
            // Act & Assert
            assertEquals(OperatingSystem.MACOS, OperatingSystem.current());
            assertTrue(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isWindows());
            assertFalse(OperatingSystem.isLinux());
        }

        @Test
        public void testEnum_onWindows_isConsistentWithActualOperatingSystem() {
            // Arrange
            // This test will only run its assertions if the current OS is Windows.
            // On other system, it will be marked as "skipped".
            assumeTrue("Skipping Windows-specific test", OperatingSystem.isWindows());
            // Act & Assert
            assertEquals(OperatingSystem.WINDOWS, OperatingSystem.current());
            assertFalse(OperatingSystem.isMacOS());
            assertTrue(OperatingSystem.isWindows());
            assertFalse(OperatingSystem.isLinux());
        }

        @Test
        public void testEnum_onLinux_isConsistentWithActualOperatingSystem() {
            // Arrange
            // This test will only run its assertions if the current OS is Linux.
            // On other system, it will be marked as "skipped".
            assumeTrue("Skipping Linux-specific test", OperatingSystem.isLinux());
            // Act & Assert
            assertEquals(OperatingSystem.LINUX, OperatingSystem.current());
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isWindows());
            assertTrue(OperatingSystem.isLinux());
        }

        @Test
        public void testEnum_onUnsupportedOS_returnsUnsupportedAndFlagsAreFalse() {
            // Arrange
            // This test will only run if the OS is NOT one of the main supported types.
            OperatingSystem current = OperatingSystem.current();
            assumeTrue("Skipping unsupported OS test", current == OperatingSystem.UNSUPPORTED);
            // Act & Assert
            assertEquals(OperatingSystem.UNSUPPORTED, current);
            assertFalse(OperatingSystem.isMacOS());
            assertFalse(OperatingSystem.isWindows());
            assertFalse(OperatingSystem.isLinux());
        }
    }
}
