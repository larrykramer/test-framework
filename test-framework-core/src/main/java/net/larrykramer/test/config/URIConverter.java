/*
 * Copyright (c) 2025-2026 Larry Kramer
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * A {@code Converter} implementation that parses textual configuration values
 * into {@code URI} instances.
 */
public class URIConverter implements Converter<URI> {
    /*
     * Matches either [IPv6]:Port OR Host:Port
     *
     * ^                    # Start of the line
     * (?:                  # Start of non-capturing group (Host Part)
     *   \[[0-9a-fA-F:]+\]  #   Option 1: IPv6 Address enclosed in brackets
     *   |                  #   OR
     *   [^:/?#@]+          #   Option 2: Hostname/IPv4 (excludes URL delimiters)
     * )                    # End of Host Part group
     * :                    # Literal colon separator
     * \d+                  # Port number (one or more digits)
     * $                    # End of the line
     */
    private static final Pattern ADDRESS_PORT_PATTERN
            = Pattern.compile("^(?:\\[[0-9a-fA-F:]+]|[^:/?#@]+):\\d+$");

    /*
     * Matches a valid RFC 3986 Scheme start (e.g., http:, mailto:, urn:)
     * According to RFC 3986, a scheme starts with a letter, followed by any combination of
     * letters, digits, plus (+), period (.), or hyphen (-).
     *
     * ^                # Start of the line
     * [a-zA-Z]         # First character must be a letter (RFC 3986 Scheme requirement)
     * [a-zA-Z0-9+.-]*  # Followed by 0+ alphanumeric, plus, dot, or hyphen characters
     * :                # Literal colon separator
     * .*               # The rest of the string (any character)
     */
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /**
     * Converts the supplied string into a {@code URI}.
     * <p>
     * The value is parsed as a URI string, with leading and trailing whitespace
     * ignored. A blank value is treated as unset and results in {@code null}.
     * To ensure proper parsing, schemeless inputs like {@code "proxy:3128"} are
     * interpreted as a host/port authority and automatically prepended with the
     * {@code http} scheme.
     *
     * @apiNote
     * IPv6 addresses containing a port <i>must</i> be wrapped in square
     * brackets as required by RFC 2732. Inputs such as {@code 2001:db8::1:8080}
     * or {@code 2001:db8::1:12345} will either be misinterpreted as part of the
     * IPv6 address (if the port fits hex syntax), or {@code URI} parses these
     * as a registry-based authority, resulting in a null Host component. This
     * may (and correctly) cause downstream consumers to fail, effectively
     * enforcing standard bracket notation for port specification.
     *
     * @param value the URI string to convert
     * @return a {@code URI} parsed from the supplied value, or {@code null} if
     *         the value is blank
     * @throws IllegalArgumentException if the value cannot be parsed as a URI
     * @throws NullPointerException     if {@code value} is null
     */
    @Override
    public URI convert(String value) {
        if (value == null) {
            throw new NullPointerException("Value is null");
        }
        if (value.isBlank()) {
            return null;
        }

        try {
            String s = value.strip();
            // Check if the value already has a valid scheme. If not, we assume it's a scheme-less
            // authority (e.g., "host:port") and prepend a default scheme.
            //
            // Ambiguous unbracketed values such as "cafe::1" or "dead:beef" intentionally favor
            // RFC 3986 scheme interpretation over bare IPv6 normalization. Because these strings
            // satisfy the scheme grammar (letter followed by alphanumerics then ':'), they are
            // forwarded to java.net.URI as opaque URIs rather than being auto-bracketed as IPv6
            // host literals. Users who intend an IPv6 address should always supply brackets,
            // e.g. "[cafe::1]".
            //
            // We evaluate the scheme before checking for IPv6 literals. This is a deliberate
            // policy decision to favor RFC 3986 generic syntax over bare IPv6 normalization for
            // ambiguous inputs like "cafe::1".
            //
            // See: https://github.com/larrykramer/test-framework/discussions/55
            if (!SCHEME_PATTERN.matcher(s).matches() || ADDRESS_PORT_PATTERN.matcher(s).matches()) {
                // An IPv6 address must be enclosed in square brackets ('[' and ']') as
                // specified by RFC 2732. In addition, RFC 6874 requires Zone IDs to
                // be escaped with %25 in URIs.
                if (IPAddressUtil.isIPv6LiteralAddress(s)) {
                    int idx = s.indexOf('%');
                    if (idx >= 0) {
                        s = s.substring(0, idx) + "%25" + s.substring(idx + 1);
                    }
                    s = "http://[" + s + "]";
                } else {
                    s = "http://" + s;
                }
            }
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI format: " + value, e);
        }
    }
}
