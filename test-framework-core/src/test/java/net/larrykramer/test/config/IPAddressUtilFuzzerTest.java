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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import net.larrykramer.test.junit.categories.FuzzerTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
@Category(FuzzerTest.class)
public class IPAddressUtilFuzzerTest {
    private final String fuzzedInput;
    private final String testCaseName;

    @Parameterized.Parameters(name = "{index}: {1}")
    public static Iterable<Object[]> data() {
        long seed = getFuzzSeed();
        System.out.println("Using fuzz seed: " + seed);

        FuzzGenerator generator = new FuzzGenerator(new Random(seed));
        List<Object[]> params = new ArrayList<>();

        // Category 1: Handcrafted edge cases.
        for (String s : generator.generateEdgeCases()) {
            params.add(new Object[] { s, "Edge Case" });
        }

        // Category 2: Randomly generated strings.
        for (String s : generator.generateRandomStrings(4000, 200)) {
            params.add(new Object[] { s, "Random" });
        }

        // Category 3: Mutations of valid IPv6 literals.
        for (String s : generator.generateMutatedIPv6Literals(4000, 8)) {
            params.add(new Object[] { s, "Mutation" });
        }

        return params;
    }

    private static long getFuzzSeed() {
        String seed = System.getProperty("fuzz.seed");
        if (seed != null) {
            if ("random".equalsIgnoreCase(seed)) {
                return System.nanoTime();
            } else {
                try {
                    return Long.parseLong(seed);
                } catch (NumberFormatException ignored) {
                    System.err.println("Invalid fuzz.seed value '" + seed  + "'. Using default.");
                }
            }
        }
        return 12345L;
    }

    public IPAddressUtilFuzzerTest(String fuzzedInput, String testCaseName) {
        this.fuzzedInput = fuzzedInput;
        this.testCaseName = testCaseName;
    }

    @Test
    public void testIsIPv6LiteralAddress_givenFuzzedInput_doesNotCrash() {
        try {
            IPAddressUtil.isIPv6LiteralAddress(fuzzedInput);
            // If the input is a valid IPv6 address, that's acceptable. The fuzzer might
            // occasionally produce valid IPv6 addresses. We don't validate the output, just that
            // it didn't crash.
        } catch (StackOverflowError | OutOfMemoryError e) {
            // These are critical failures indicating the isIPv6LiteralAddress(String) can't handle
            // certain inputs gracefully.
            fail("IPAddressUtil crashed with '" + e.getClass().getName()
                    + "' on test case: '" + testCaseName
                    + "'. Input: \"" + escapeFuzzedInput() + "\"");
        } catch (Throwable t) {
            // isIPv6LiteralAddress(String) is not specified to throw.
            // Treat any throwable as a failure.
            fail("IPAddressUtil threw an unexpected exception '" + t.getClass().getName()
                    + "' on test case: '" + testCaseName
                    + "'. Input: \"" + escapeFuzzedInput() + "\"");
        }
    }

    private String escapeFuzzedInput() {
        String escaped = Pattern.compile("[\0\r\n\t]").matcher(fuzzedInput)
                .replaceAll(m -> switch (m.group().charAt(0)) {
                    case '\0' -> "\\0";
                    case '\r' -> "\\r";
                    case '\n' -> "\\n";
                    case '\t' -> "\\t";
                    default -> m.group();
                });
        return (escaped.length() > 100) ? (escaped.substring(0, 97) + "...") : escaped;
    }

    static class FuzzGenerator {
        private static final String IPV6_CHARS = "0123456789abcdefABCDEF:%.";
        private static final String EXTRA_NOISE = "[](){}<>\"'\\/@!?;=,_-+*#& \t\r\n";
        private static final String ZONE_CHARS
                = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-";
        //@formatter:off
        private static final String[] VALID_IPV6_SAMPLES = {
                // Basic
                "::",
                "::1",
                "0:0:0:0:0:0:0:1",
                "2001:db8::",
                "2001:db8::1",
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
                // Mixed / embedded IPv4 tail.
                "::ffff:192.0.2.128",
                "2001:db8::192.0.2.33",
                // Zone identifiers.
                // Everything after % is ignored by IPAddressUtil, but must not be empty.
                "fe80::1%eth0",
                "fe80::a:b:c:d%en0",
                "fe80::1%1"
        };
        //@formatter:on

        private final Random random;

        FuzzGenerator(Random random) {
            this.random = random;
        }

        public List<String> generateEdgeCases() {
            List<String> cases = new ArrayList<>();

            // Empty / short.
            cases.add("");
            cases.add(" ");
            cases.add("\t\r\n ");
            cases.add(":"); // too short to be valid
            cases.add("::"); // valid minimal
            cases.add(":::"); // invalid
            cases.add("::::"); // invalid

            // Leading/trailing garbage.
            cases.add("::1 ");
            cases.add(" ::1");
            cases.add("::1\0");
            cases.add("\n::1");

            // Percent/zone edge cases.
            cases.add("%"); // too short, invalid
            cases.add("fe80::1%"); // explicitly invalid (percent at end)
            cases.add("::%"); // invalid
            cases.add("::% "); // zone isn't validated, but '%' not at end => should parse
            cases.add("fe80::1%eth0%w"); // multiple '%'; ignore from first '%', shouldn't crash

            // Hextet / colon structure edge cases.
            cases.add(":1");
            cases.add("1:"); // trailing colon
            cases.add("1::");
            cases.add("::1:"); // ends with colon
            cases.add("0:0:0:0:0:0:0"); // too few groups
            cases.add("0:0:0:0:0:0:0:0");
            cases.add("0:0:0:0:0:0:0:0:0"); // too many groups
            cases.add("gggg::1"); // non-hex
            cases.add("10000::"); // hextet > 0xffff
            cases.add("fffff::"); // hextet > 0xffff
            cases.add("1::1::1"); // double '::'

            // Embedded IPv4 tail edge cases.
            cases.add("::ffff:256.0.0.1"); // invalid v4
            cases.add("::ffff:192.168.0"); // invalid v4
            cases.add("::ffff:192.168.0.1.2"); // invalid v4
            cases.add("::ffff:192.168.0.1"); // valid v4-mapped
            cases.add("::192.168.0.1"); // valid v4-embedded
            cases.add("::ffff:1.2.3"); // wrong dot count
            cases.add("::ffff:1.2.3.4.5"); // wrong dot count

            // Brackets (valid in URIs, but this util expects the literal without brackets).
            cases.add("[::1]");
            cases.add("[fe80::1%eth0]");
            cases.add("[]");
            cases.add("[::]");
            cases.add("::1]");

            return cases;
        }

        public String[] generateRandomStrings(int count, int maxLength) {
            String[] strings = new String[count];
            for (int i = 0; i < count; i++) {
                int length = random.nextInt(maxLength) + 1;
                StringBuilder sb = new StringBuilder(length);
                for (int j = 0; j < length; j++) {
                    int type = random.nextInt(10);
                    if (type < 6) { // 60% IPv6-specific chars
                        sb.append(IPV6_CHARS.charAt(random.nextInt(IPV6_CHARS.length())));
                    } else if (type < 8) { // 20% noise
                        sb.append(EXTRA_NOISE.charAt(random.nextInt(EXTRA_NOISE.length())));
                    } else { // 20% random BMP (up to BMP)
                        sb.append((char) random.nextInt(0xD7FF));
                    }
                }
                strings[i] = sb.toString();
            }
            return strings;
        }

        public String[] generateMutatedIPv6Literals(int count, int maxMutations) {
            String[] strings = new String[count];
            for (int i = 0; i < count; i++) {
                String base = VALID_IPV6_SAMPLES[random.nextInt(VALID_IPV6_SAMPLES.length)];
                char[] chars = base.toCharArray();
                char c;

                int mutations = random.nextInt(maxMutations) + 1;
                for (int j = 0; j < mutations; j++) {
                    if (chars.length == 0) {
                        break;
                    }
                    int pos = random.nextInt(chars.length);
                    switch (random.nextInt(5)) {
                        case 0: // Substitute with IPv6-specific char
                            chars[pos] = IPV6_CHARS.charAt(random.nextInt(IPV6_CHARS.length()));
                            break;
                        case 1: // Substitute with random BMP
                            chars[pos] = (char) random.nextInt(0xD7FF);
                            break;
                        case 2: // Delete
                            chars = deleteCharAt(chars, pos);
                            break;
                        case 3: // Insert IPv6 char
                            c = IPV6_CHARS.charAt(random.nextInt(IPV6_CHARS.length()));
                            chars = insertCharAt(chars, pos, c);
                            break;
                        case 4: // Insert zone-id char (to stress % handling)
                            c = ZONE_CHARS.charAt(random.nextInt(ZONE_CHARS.length()));
                            chars = insertCharAt(chars, pos, c);
                            break;
                        default:
                            break;
                    }
                }
                strings[i] = new String(chars);
            }
            return strings;
        }

        private static char[] deleteCharAt(char[] src, int pos) {
            if (src.length <= 1) {
                return new char[0];
            }
            char[] dest = new char[src.length - 1];
            if (pos > 0) {
                System.arraycopy(src, 0, dest, 0, pos);
            }
            if (pos < dest.length) {
                System.arraycopy(src, pos + 1, dest, pos, dest.length - pos);
            }
            return dest;
        }

        private static char[] insertCharAt(char[] src, int pos, char c) {
            char[] dest = new char[src.length + 1];
            if (pos > 0) {
                System.arraycopy(src, 0, dest, 0, pos);
            }
            dest[pos] = c;
            if (pos < src.length) {
                System.arraycopy(src, pos, dest, pos + 1, src.length - pos);
            }
            return dest;
        }
    }
}
