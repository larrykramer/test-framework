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

import java.util.Locale;

/**
 * Enumeration of operating system types and testing for current OS. The
 * enumeration can be used to dispatch to OS specific code or values. Checking
 * if a specific operating system is current uses a simple static method for
 * each operating system.
 * <p>
 * For example,
 * <pre>{@code
 * if (OperatingSystem.isWindows()) {
 *     // Windows only code.
 * } else if (OperatingSystem.isMacOS()) {
 *     // macOS only code.
 * }
 * }</pre>
 *
 * Alternatively, compare with the {@linkplain #current() current} operating
 * system.
 * For example,
 * <pre>{@code
 * if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
 *     // Windows only code.
 * }
 * }</pre>
 *
 * Dispatch based on the current operating system or choose a value.
 * For example,
 * <pre>{@code
 * int port() {
 *     return switch (OperatingSystem.current()) {
 *         case WINDOWS, MACOS -> 49152;
 *         case LINUX -> 32768;
 *     };
 * }
 * }</pre>
 */
public enum OperatingSystem {
    /** The macOS operating system. */
    MACOS,

    /** The Windows operating system. */
    WINDOWS,

    /** The Linux operating system. */
    LINUX,

    /** An unsupported operating system. */
    UNSUPPORTED,
    ;

    // The current OperatingSystem.
    private static final OperatingSystem CURRENT_OS = computeOS();

    /**
     * {@return <code>true</code> if the current operating system is macOS}
     */
    public static boolean isMacOS() {
        return CURRENT_OS == MACOS;
    }

    /**
     * {@return <code>true</code> if the current operating system is Windows}
     */
    public static boolean isWindows() {
        return CURRENT_OS == WINDOWS;
    }

    /**
     * {@return <code>true</code> if the current operating system is Linux}
     */
    public static boolean isLinux() {
        return CURRENT_OS == LINUX;
    }

    /**
     * {@return the current operating system}
     */
    public static OperatingSystem current() {
        return CURRENT_OS;
    }

    private static OperatingSystem computeOS() {
        String osName = System.getProperty("os.name");
        if (osName != null) {
            osName = osName.stripLeading().toLowerCase(Locale.ROOT);
            if (osName.startsWith("mac")) {
                return MACOS;
            } else if (osName.startsWith("windows")) {
                return WINDOWS;
            } else if (osName.startsWith("linux")) {
                return LINUX;
            } else {
                return UNSUPPORTED;
            }
        } else {
            return UNSUPPORTED;
        }
    }
}
