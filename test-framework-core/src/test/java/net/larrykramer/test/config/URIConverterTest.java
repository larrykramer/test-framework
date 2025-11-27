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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class URIConverterTest {
    private URIConverter converter;

    @Before
    public void setUp() {
        converter = new URIConverter();
    }

    @Test
    public void testConvert_withBlankValue_returnsNull() {
        assertNull(converter.convert("   "));
    }

    @Test
    public void testConvert_givenValueWithScheme_returnsParsedURI() {
        // Act
        URI result = converter.convert("  https://example.org/resource  ");
        // Assert
        assertEquals("https://example.org/resource", result.toString());
        assertEquals("https", result.getScheme());
        assertEquals("example.org", result.getHost());
    }

    @Test
    public void testConvert_givenHostAndPortWithoutScheme_returnsHttpURI() {
        // Act
        URI result = converter.convert("proxy.example.com:3128");
        // Assert
        assertEquals("http", result.getScheme());
        assertEquals("proxy.example.com", result.getHost());
        assertEquals(3128, result.getPort());
        assertEquals("", result.getPath());
    }

    @Test
    public void testConvert_givenHostWithoutScheme_returnsHttpURIWithoutPort() {
        // Act
        URI result = converter.convert("proxy");
        // Assert
        assertEquals("http", result.getScheme());
        assertEquals("proxy", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_givenHostWithLeadingZeroPort_returnsHttpURIWithPort() {
        // Act
        URI result = converter.convert("example:0123");
        // Assert
        assertEquals("http://example:0123", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("example", result.getHost());
        assertEquals(123, result.getPort());
    }

    @Test
    public void testConvert_givenHostWithVeryLargePort_returnsHttpURIWithLargePort() {
        // Act
        URI result = converter.convert("example.com:999999");
        // Assert
        assertEquals("http://example.com:999999", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("example.com", result.getHost());
        assertEquals(999999, result.getPort());
    }

    @Test
    public void testConvert_givenHttpLikeAuthority_returnsHttpURI() {
        // Act
        URI result = converter.convert("http:8080");
        // Assert
        assertEquals("http://http:8080", result.toString());
        assertEquals("http", result.getHost());
        assertEquals(8080, result.getPort());
    }

    @Test
    public void testConvert_givenIPv6HostAndPortWithoutScheme_returnsHttpURI() {
        // Act
        URI result = converter.convert("[fe80::1]:8080");
        // Assert
        assertEquals("http://[fe80::1]:8080", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[fe80::1]", result.getHost());
        assertEquals(8080, result.getPort());
    }

    @Test
    public void testConvert_givenIPv6HostWithoutSchemeAndPort_returnsHttpURIWithoutPort() {
        // Act
        URI result = converter.convert("[::1]");
        // Assert
        assertEquals("http://[::1]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[::1]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_givenUnbracketedIPv6Address_returnsHttpURIWithoutPort() {
        // Act
        URI result = converter.convert("2001:db8:85a3::8a2e:370:7334");
        // Assert
        assertEquals("http://[2001:db8:85a3::8a2e:370:7334]", result.toString());
        assertEquals("http", result.getScheme());
        assertEquals("[2001:db8:85a3::8a2e:370:7334]", result.getHost());
        assertEquals(-1, result.getPort());
    }

    @Test
    public void testConvert_givenWssScheme_returnsParsedURI() {
        // Act
        URI result = converter.convert("wss://securechat.example.com");
        // Assert
        assertEquals("wss://securechat.example.com", result.toString());
        assertEquals("wss", result.getScheme());
        assertEquals("securechat.example.com", result.getHost());
    }

    @Test
    public void testConvertWsScheme_returnsExactURI() {
        assertEquals(URI.create("ws:chat"), converter.convert("ws:chat"));
    }

    @Test
    public void testConvert_givenMailtoScheme_returnsExactURI() {
        assertEquals(URI.create("mailto:a@b"), converter.convert("mailto:a@b"));
    }

    @Test
    public void testConvert_givenUrnScheme_returnsExactURI() {
        assertEquals(URI.create("urn:foo:bar"), converter.convert("urn:foo:bar"));
    }

    @Test
    public void testConvert_givenFileScheme_returnsExactURI() {
        assertEquals(URI.create("file:/tmp/a"), converter.convert("file:/tmp/a"));
    }

    @Test
    public void testConvert_givenJarScheme_returnsExactURI() {
        // Act
        URI result = converter.convert("jar:file:/opt/lib/test.jar!/com/foo/Bar.class");
        // Assert
        URI expected = URI.create("jar:file:/opt/lib/test.jar!/com/foo/Bar.class");
        assertEquals(expected, result);
    }

    @Test
    public void testConvert_withInvalidSchemeSyntax_throwsIllegalArgumentException() {
        // Arrange
        String value = "http://exa mple.com";
        // Act & Assert
        var e = assertThrows(IllegalArgumentException.class, () -> converter.convert(value));
        assertTrue(e.getMessage().contains("Invalid URI format: http://exa mple.com"));
        assertNotNull(e.getCause());
    }

    @Test(expected = NullPointerException.class)
    public void testConvert_withNullValue_throwsNullPointerException() {
        converter.convert(null);
    }
}
