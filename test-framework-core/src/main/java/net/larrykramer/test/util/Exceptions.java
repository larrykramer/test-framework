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

import java.security.Security;
import java.util.IllegalFormatException;
import java.util.Objects;

/**
 * Utility methods for building exception messages that may contain sensitive
 * host- or URI-related information.
 * <p>
 * This class supports secure-by-default exception text by allowing callers to
 * describe sensitive values separately from the message template. When enhanced
 * exception text is not enabled, sensitive values are replaced or omitted. When
 * enhanced exception text is enabled, the original value is included with any
 * configured prefix or suffix.
 * <p>
 * Whether host-related values are included is determined by the JDK's
 * {@code jdk.includeInExceptions} setting. If that setting includes
 * {@code hostInfo} or {@code hostInfoExclSocket}, this utility treats host
 * information as eligible for enhanced exception output. Otherwise, wrapped
 * values are omitted or replaced, allowing exception messages to avoid exposing
 * potentially sensitive connection details by default.
 * <p>
 * The {@code jdk.includeInExceptions} value may be supplied as a system
 * property or security property and is typically expressed as a comma-separated
 * list of exception-detail categories.
 * <p>
 * <b>Example:</b>
 * <pre>{@code
 * String configuredValue = "secret-value";
 *
 * SensitiveInfo value = filterHostInfo(configuredValue)
 *         .withPrefix(": [")
 *         .withSuffix("]")
 *         .withReplacement(": (***redacted***)");
 *
 * IllegalArgumentException exception =
 *         new IllegalArgumentException(formatMsg("invalid value%s", value));
 *
 * // Secure mode output:
 * // "invalid value: (***redacted***)"
 *
 * // Enhanced diagnostics output:
 * // "invalid value: [secret-value]"
 * }</pre>
 * <p>
 * The {@link SensitiveInfo#isEnhanced()} method can be used by callers to
 * decide whether additional diagnostic details, such as causes, should be
 * attached to an exception.
 */
public final class Exceptions {
    private static final boolean ENHANCED_HOST_EXCEPTION_TEXT
            = includedInExceptions("hostInfo") || includedInExceptions("hostInfoExclSocket");

    private static boolean includedInExceptions(String refName) {
        String val = System.getProperty("jdk.includeInExceptions");
        if (val == null) {
            val = Security.getProperty("jdk.includeInExceptions");
            if (val == null) {
                return false;
            }
        }

        String[] tokens = val.split(",");
        for (String token : tokens) {
            if (token.trim().equalsIgnoreCase(refName)) {
                return true;
            }
        }

        return false;
    }

    private Exceptions() {}

    /**
     * Creates a {@link SensitiveInfo} wrapper for host-related text that may be
     * conditionally included in an exception message.
     * <p>
     * The supplied value may be a host name, authority, URI, or similar text
     * that should only appear in exception output when enhanced diagnostics are
     * enabled.
     *
     * @param host the host-related text to wrap
     * @return a configurable wrapper for the supplied value
     * @throws NullPointerException if {@code host} is null
     */
    public static SensitiveInfo filterHostInfo(String host) {
        return new SensitiveInfo(host);
    }

    /**
     * Formats an exception message using sensitive-information arguments.
     * <p>
     * Each supplied {@link SensitiveInfo} contributes either its enhanced text
     * or its replacement text, depending on whether enhanced exception text is
     * enabled. The returned message is normalized so omitted sensitive values
     * do not leave unnecessary whitespace.
     *
     * @param format the message format string
     * @param infos  the sensitive-information values to insert into the format
     *               string
     * @return the formatted exception message
     * @throws IllegalFormatException if the format string is invalid or
     *                                incompatible with the supplied
     *                                arguments
     */
    public static String formatMsg(String format, SensitiveInfo... infos) {
        String[] args = new String[infos.length];
        int i = 0;
        for (SensitiveInfo info : infos) {
            args[i++] = info.output();
        }
        return trim(String.format(format, (Object[]) args));
    }

    /**
     * Formats a message from a single sensitive-information value.
     * <p>
     * The returned value is either the enhanced text or the replacement text,
     * depending on whether enhanced exception text is enabled. The returned
     * message is normalized so omitted sensitive values do not leave
     * unnecessary whitespace.
     *
     * @param info the sensitive-information value to format
     * @return the formatted message text
     */
    public static String formatMsg(SensitiveInfo info) {
        return trim(info.output());
    }

    private static String trim(String s) {
        int len = s.length();
        if (len == 0) {
            return s;
        }

        StringBuilder sb = new StringBuilder();

        // Initial value deals with leading spaces.
        boolean inSpace = true;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (inSpace) {
                    continue;
                }
                inSpace = true;
            } else {
                inSpace = false;
            }
            sb.append(c);
        }

        int sblen = sb.length();
        if (sblen > 0 && sb.charAt(sblen - 1) == ' ') {
            sb.deleteCharAt(sblen - 1);
        }

        return sb.toString();
    }

    /**
     * Represents text that may contain sensitive information and should only
     * be included in exception messages when enhanced exception text is
     * enabled.
     * <p>
     * A {@code SensitiveInfo} instance can be customized with optional text to
     * prepend or append, and with replacement. The prefix and suffix are used
     * with the original value in enhanced output. The replacement is used when
     * enhanced output is not enabled.
     */
    public static class SensitiveInfo {
        private final String info;

        private String suffix;
        private String prefix;
        private String replacement;

        private boolean enhanced;

        SensitiveInfo(String info) {
            this.info = Objects.requireNonNull(info, "info");

            this.prefix = "";
            this.suffix = "";
            this.replacement = "";
        }

        /**
         * Sets text to prepend to the sensitive value when enhanced exception
         * text is enabled.
         * <p>
         * The prefix is not included when replacement text is used.
         *
         * @param prefix the prefix to use with enhanced output
         * @return this sensitive-information value
         */
        public SensitiveInfo withPrefix(String prefix) {
            this.prefix = (prefix == null) ? "" : prefix;
            return this;
        }

        /**
         * Sets text to append to the sensitive value when enhanced exception
         * text is enabled.
         * <p>
         * The suffix is not included when replacement text is used.
         *
         * @param suffix the suffix to use with enhanced output
         * @return this sensitive-information value
         */
        public SensitiveInfo withSuffix(String suffix) {
            this.suffix = (suffix == null) ? "" : suffix;
            return this;
        }

        /**
         * Sets the text to use when enhanced exception text is not enabled.
         * <p>
         * The replacement should avoid exposing sensitive data. Use an empty
         * string to omit the sensitive value entirely.
         *
         * @param replacement the replacement text to use for secure output
         * @return this sensitive-information value
         */
        public SensitiveInfo withReplacement(String replacement) {
            this.replacement = (replacement == null) ? "" : replacement;
            return this;
        }

        /**
         * Indicates whether this value has been rendered using enhanced
         * exception text.
         * <p>
         * This method is useful after formatting a message when callers need to
         * decide whether to include additional diagnostic information.
         *
         * @return {@code true} if this value has been rendered with its
         *         original sensitive text; {@code false} otherwise
         */
        public boolean isEnhanced() {
            return enhanced;
        }

        /**
         * Returns the text to use for this sensitive value in an exception
         * message.
         * <p>
         * When enhanced exception text is enabled, the returned text contains
         * the original value with any configured prefix and suffix. Otherwise,
         * the configured replacement text is returned.
         *
         * @return the exception-message text
         */
        public String output() {
            if (ENHANCED_HOST_EXCEPTION_TEXT) {
                this.enhanced = true;
                return prefix + info + suffix;
            } else {
                return replacement;
            }
        }
    }
}
