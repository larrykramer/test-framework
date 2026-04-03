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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class URIConverterTest {
    private URIConverter converter;

    @Before
    public void setUp() {
        converter = new URIConverter();
    }

    @Test
    public void testConvert_blankInput_returnsNull() {
        assertNull(converter.convert("   "));
    }

    @Test
    public void testConvert_httpsURI_returnsParsedURI() {
        URI result = converter.convert("  https://example.org/resource  ");
        assertEquals("https://example.org/resource", result.toString());
        assertEquals("https", result.getScheme());
        assertEquals("example.org", result.getHost());
    }

    @Test
    public void testConvert_hostPort_returnsHttpURIWithPort() {
        URI result = converter.convert("proxy.example.com:3128");
        assertEquals("http", result.getScheme());
        assertEquals("proxy.example.com", result.getHost());
        assertEquals(3128, result.getPort());
        assertEquals("", result.getPath());
    }

    @Test
    public void testConvert_hostOnly_returnsHttpURI() {
        URI result = converter.convert("proxy");
        assertEquals("http", result.getScheme());
        assertEquals("proxy", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_leadingZeroPort_preservesTextAndParsesPort() {
        URI result = converter.convert("example:0123");
        assertEquals("http://example:0123", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("example", result.getHost());
        assertEquals(123, result.getPort());
    }

    @Test
    public void testConvert_largePort_returnsHttpURIWithPort() {
        URI result = converter.convert("example.com:999999");
        assertEquals("http://example.com:999999", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("example.com", result.getHost());
        assertEquals(999999, result.getPort());
    }

    @Test
    public void testConvert_httpHost_treatsHttpAsHost() {
        URI result = converter.convert("http:8080");
        assertEquals("http://http:8080", result.toString());
        assertEquals("http", result.getHost());
        assertEquals(8080, result.getPort());
    }

    @Test
    public void testConvert_IPv6Loopback_returnsHttpURI() {
        URI result = converter.convert("[::1]");
        assertEquals("http://[::1]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[::1]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_bracketedIPv6_returnsHttpURI() {
        URI result = converter.convert("[cafe::1]");
        assertEquals("http://[cafe::1]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[cafe::1]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_bracketedIPv6WithPort_returnsHttpURI() {
        URI result = converter.convert("[fe80::1]:8080");
        assertEquals("http://[fe80::1]:8080", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[fe80::1]", result.getHost());
        assertEquals(8080, result.getPort());
    }

    @Test
    public void testConvert_bracketedIPv6ZoneId_preservesZoneId() {
        URI result = converter.convert("[fe80::1%25eth0]");
        assertEquals("http://[fe80::1%25eth0]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[fe80::1%25eth0]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_bareIPv6_bracketsAddress() {
        URI result = converter.convert("2001:db8:85a3::8a2e:370:7334");
        assertEquals("http://[2001:db8:85a3::8a2e:370:7334]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[2001:db8:85a3::8a2e:370:7334]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_bareIPv6ZoneId_encodesZoneId() {
        URI result = converter.convert("2001:db8::1%eth0");
        assertEquals("http://[2001:db8::1%25eth0]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[2001:db8::1%25eth0]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_bareIPv6EncodedZoneId_preservesEncoding() {
        URI result = converter.convert("2001:db8::1%25eth0");
        assertEquals("http://[2001:db8::1%25eth0]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[2001:db8::1%25eth0]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_schemeLikeIPv6_returnsOpaqueURI() {
        URI result = converter.convert("cafe::1");
        assertEquals(URI.create("cafe::1"), result);
        assertEquals("cafe", result.getScheme());
        assertEquals(":1", result.getSchemeSpecificPart());
        assertNull(result.getHost());
        assertTrue(result.isOpaque());
    }

    @Test
    public void testConvert_schemeLikeIPv6EndingWithColon_returnsOpaqueURI() {
        URI result = converter.convert("face::");
        assertEquals(URI.create("face::"), result);
        assertEquals("face", result.getScheme());
        assertEquals(":", result.getSchemeSpecificPart());
        assertNull(result.getHost());
        assertTrue(result.isOpaque());
    }

    @Test
    public void testConvert_schemeLikeHexToken_returnsOpaqueURI() {
        URI result = converter.convert("dead:beef");
        assertEquals(URI.create("dead:beef"), result);
        assertEquals("dead", result.getScheme());
        assertEquals("beef", result.getSchemeSpecificPart());
        assertNull(result.getHost());
        assertTrue(result.isOpaque());
    }

    @Test
    public void testConvert_IPv4MappedIPv6_returnsHttpURI() {
        URI result = converter.convert("::ffff:192.168.1.1");
        assertEquals("http://[::ffff:192.168.1.1]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[::ffff:192.168.1.1]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_wssURI_returnsParsedURI() {
        URI result = converter.convert("wss://securechat.example.com");
        assertEquals("wss://securechat.example.com", result.toString());
        assertEquals("wss", result.getScheme());
        assertEquals("securechat.example.com", result.getHost());
    }

    @Test
    public void testConvert_wsURI_returnsParsedURI() {
        URI result = converter.convert("ws://chat.example.com");
        assertEquals("ws://chat.example.com", result.toString());
        assertEquals("ws", result.getScheme());
        assertEquals("chat.example.com", result.getHost());
    }

    @Test
    public void testConvert_mailtoURI_returnsExactURI() {
        assertEquals(URI.create("mailto:a@b"), converter.convert("mailto:a@b"));
    }

    @Test
    public void testConvert_urnURI_returnsExactURI() {
        assertEquals(URI.create("urn:foo:bar"), converter.convert("urn:foo:bar"));
    }

    @Test
    public void testConvert_fileURI_returnsExactURI() {
        assertEquals(URI.create("file:/tmp/a"), converter.convert("file:/tmp/a"));
    }

    @Test
    public void testConvert_jarURI_returnsExactURI() {
        URI result = converter.convert("jar:file:/opt/lib/test.jar!/com/foo/Bar.class");
        URI expected = URI.create("jar:file:/opt/lib/test.jar!/com/foo/Bar.class");
        assertEquals(expected, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvert_invalidURI_throwsIllegalArgumentException() {
        converter.convert("http://exa mple.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvert_zoneIdWithSchemeLikePrefix_throwsIllegalArgumentException() {
        // Unbracketed "fe80::1%eth0" matches the URI scheme grammar ("fe80:"),
        // so the converter does not normalize it as IPv6; java.net.URI then rejects
        // the raw zone ID because '%' must be URI-escaped (%25) and IPv6 literals in
        // URIs must be bracketed.
        converter.convert("fe80::1%eth0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvert_numericZoneIdWithSchemeLikePrefix_throwsIllegalArgumentException() {
        // Zone IDs can also be numeric interface indices.
        converter.convert("fe80::1%3");
    }

    @Test(expected = NullPointerException.class)
    public void testConvert_nullInput_throwsNullPointerException() {
        converter.convert(null);
    }
}
