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

package net.larrykramer.test.config;

/**
 * Utility class for working with IP address literals.
 */
final class IPAddressUtil {
    private static final int INADDR4SZ = 4;
    private static final int INADDR16SZ = 16;
    private static final int INT16SZ = 2;

    private static final boolean ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP_VALUE =
            Boolean.getBoolean("jdk.net.allowAmbiguousIPAddressLiterals");

    private IPAddressUtil() {}

    /**
     * Tests whether the given text is a valid IPv6 literal address.
     *
     * @param src the address text to test
     * @return {@code true} if {@code src} is a valid IPv6 literal address;
     *         {@code false} otherwise
     */
    public static boolean isIPv6LiteralAddress(String src) {
        return textToNumericFormatV6(src) != null;
    }

    /*
     * Convert IPv6 presentation level address to network order binary form.
     *
     * credit:
     *  Converted from C code from Solaris 8 (inet_pton)
     *
     * Any component of the string following a percent (%) is ignored.
     */
    private static byte[] textToNumericFormatV6(String src) {
        // Shortest valid string is "::", hence at least 2 chars.
        if (src.length() < 2) {
            return null;
        }

        int len = src.length();
        int pc = src.indexOf('%');
        if (pc == len - 1) {
            return null;
        }

        if (pc != -1) {
            len = pc;
        }

        int i = 0;

        // Leading :: requires some special handling.
        if (src.charAt(i) == ':') {
            if (src.charAt(++i) != ':') {
                return null;
            }
        }

        byte[] dst = new byte[INADDR16SZ];
        int colonp = -1;
        int curtok = i;
        boolean sawXdigit = false; // true if we've read ≥1 hex digit in the current hextet
        int j = 0;
        int val = 0;
        while (i < len) {
            final char ch = src.charAt(i++);
            int chval = digit(ch, 16);
            if (chval != -1) {
                val <<= 4;
                val |= chval;
                if (val > 0xffff) {
                    return null;
                }
                sawXdigit = true;
                continue;
            }
            if (ch == ':') {
                curtok = i;
                if (!sawXdigit) {
                    if (colonp != -1) {
                        return null;
                    }
                    colonp = j;
                    continue;
                } else if (i == len) {
                    return null;
                }
                if (j + INT16SZ > INADDR16SZ) {
                    return null;
                }
                dst[j++] = (byte) ((val >> 8) & 0xff);
                dst[j++] = (byte) (val & 0xff);
                sawXdigit = false;
                val = 0;
                continue;
            }
            if (ch == '.' && ((j + INADDR4SZ) <= INADDR16SZ)) {
                String ia4 = src.substring(curtok, len);
                // check this IPv4 address has 3 dots, i.e. A.B.C.D
                int dotCount = 0;
                int index = 0;
                while ((index = ia4.indexOf('.', index)) != -1) {
                    dotCount++;
                    index++;
                }
                if (dotCount != 3) {
                    return null;
                }

                byte[] v4addr = textToNumericFormatV4(ia4);
                if (v4addr == null) {
                    return null;
                }
                for (int k = 0; k < INADDR4SZ; k++) {
                    dst[j++] = v4addr[k];
                }
                sawXdigit = false;
                break; // parsed embedded IPv4 tail; stop processing remaining chars
            }
            return null;
        }

        if (sawXdigit) {
            if (j + INT16SZ > INADDR16SZ) {
                return null;
            }
            dst[j++] = (byte) ((val >> 8) & 0xff);
            dst[j++] = (byte) (val & 0xff);
        }

        if (colonp != -1) {
            int n = j - colonp;
            if (j == INADDR16SZ) {
                return null;
            }
            for (i = 1; i <= n; i++) {
                dst[INADDR16SZ - i] = dst[colonp + n - i];
                dst[colonp + n - i] = 0;
            }
            j = INADDR16SZ;
        }
        if (j != INADDR16SZ) {
            return null;
        }

        byte[] newdst = convertFromIPv4MappedAddress(dst);
        return (newdst != null) ? newdst : dst;
    }

    /*
     * Converts IPv4 address in its textual presentation form into its numeric
     * binary form.
     */
    private static byte[] textToNumericFormatV4(String src) {
        byte[] res = new byte[INADDR4SZ];

        long tmpValue = 0;
        int currByte = 0;
        boolean newOctet = true;

        int len = src.length();
        if (len == 0 || len > 15) {
            return null;
        }

        for (int i = 0; i < len; i++) {
            char c = src.charAt(i);
            if (c == '.') {
                if (newOctet || tmpValue > 0xff || currByte == 3) {
                    return null;
                }
                res[currByte++] = (byte) (tmpValue & 0xff);
                tmpValue = 0;
                newOctet = true;
            } else {
                int digit = digit(c, 10);
                if (digit < 0) {
                    return null;
                }
                tmpValue *= 10;
                tmpValue += digit;
                newOctet = false;
            }
        }
        if (newOctet || tmpValue >= (1L << ((4 - currByte) * 8))) {
            return null;
        }

        assert currByte == 3;
        res[3] = (byte) (tmpValue & 0xff);
        return res;
    }

    /*
     * Convert IPv4-Mapped address to IPv4 address. Both input and returned
     * value are in network order binary form.
     */
    private static byte[] convertFromIPv4MappedAddress(byte[] addr) {
        if (isIPv4MappedAddress(addr)) {
            byte[] newAddr = new byte[INADDR4SZ];
            System.arraycopy(addr, 12, newAddr, 0, INADDR4SZ);
            return newAddr;
        }
        return null;
    }

    /*
     * Utility routine to check if the InetAddress is an IPv4 mapped IPv6
     * address.
     */
    private static boolean isIPv4MappedAddress(byte[] addr) {
        assert addr.length == INADDR16SZ;
        return addr[0] == 0x00 && addr[1] == 0x00
                && addr[2] == 0x00 && addr[3] == 0x00
                && addr[4] == 0x00 && addr[5] == 0x00
                && addr[6] == 0x00 && addr[7] == 0x00
                && addr[8] == 0x00 && addr[9] == 0x00
                && addr[10] == (byte) 0xff
                && addr[11] == (byte) 0xff;
    }

    private static int digit(char ch, int radix) {
        if (ALLOW_AMBIGUOUS_IPADDRESS_LITERALS_SP_VALUE) {
            return Character.digit(ch, radix);
        } else {
            assert radix == 10 || radix == 16;
            if (radix == 16) {
                char c = Character.toLowerCase(ch);
                if (c >= 'a' && c <= 'f') {
                    return c - 'a' + 10;
                }
                radix = 10;
            }
            int val = ch - '0';
            return (val < 0 || val >= radix) ? -1 : val;
        }
    }
}
