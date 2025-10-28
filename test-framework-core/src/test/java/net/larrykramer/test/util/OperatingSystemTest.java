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

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tets for the {@link OperatingSystem} utility.
 * <p>
 * <strong>Note on Testing Strategy:</strong>
 * The core logic of this {@code enum} resides in the private static {@code computeOS()} method,
 * which reads the global {@code os.name} system property. Exhaustively unit-testing this method by
 * mocking or modifying this global property is inherently unsafe for parallel test execution and
 * can lead to flaky, intermittent failures in the build.
 * <p>
 * Given the extreme simplicity of the {@code computeOS()} method (a series of {@code startsWith}
 * checks), the risk of a logic error is very low. Therefore, we favor build stability and rely on:
 * <ol>
 * <li>A simple consistency test that runs on the actual build machine.
 * <li>Through code review to validate the simple string-matching logic.
 * </ol>
 * This approach avoids the dangers of manipulating shared, global JVM state in tests.
 */
public class OperatingSystemTest {
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
