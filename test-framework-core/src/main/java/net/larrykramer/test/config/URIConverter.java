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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * A {@link Converter} implementation that translates textual configuration
 * values into {@link URI} instances.
 * <p>
 * The converter trims incoming values and accepts any string understood by
 * {@link URI#URI(String)}. Blank values are considered unset and yield
 * {@code null}. When no scheme is present, inputs such as {@code "proxy:3128"}
 * are interpreted as host/port pairs and normalized into an {@code http}
 * {@link URI}, preserving the parsed host and port components.
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
     * Converts the supplied string into a {@link URI}.
     *
     * @param value the URI string to convert; must not be {@code null}
     * @return a {@link URI} parsed from the supplied value, or {@code null} if
     *         the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if the value cannot be parsed as a URI
     */
    @Override
    public URI convert(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (value.isBlank()) {
            return null;
        }

        try {
            String s = value.strip();
            // Check if the value already has a valid scheme. If not, we assume it's a schemeless
            // authority (e.g., "host:port") and prepend a default scheme. This allows the URI
            // constructor to correctly parse hostnames, IPv4, and bracketed IPv6 literals, which
            // it would otherwise fail on.
            if (ADDRESS_PORT_PATTERN.matcher(s).matches() || !SCHEME_PATTERN.matcher(s).matches()) {
                //noinspection HttpUrlsUsage
                s = "http://" + s;
            }
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI format: " + value, e);
        }
    }
}
