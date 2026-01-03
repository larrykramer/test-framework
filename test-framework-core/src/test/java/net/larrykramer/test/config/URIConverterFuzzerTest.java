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
public class URIConverterFuzzerTest {
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
        for (String s : generator.generateRandomStrings(2000, 100)) {
            params.add(new Object[] { s, "Random" });
        }

        // Category 3: Mutated valid URI strings.
        for (String s : generator.generateMutatedUris(2000, 5)) {
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

    public URIConverterFuzzerTest(String fuzzedInput, String testCaseName) {
        this.fuzzedInput = fuzzedInput;
        this.testCaseName = testCaseName;
    }

    @Test
    public void testConvert_givenFuzzedInput_doesNotCrash() {
        try {
            new URIConverter().convert(fuzzedInput);
            // If conversion succeeds, that's acceptable. The fuzzer might occasionally produce
            // valid URIs. We don't validate the output, just that the converter didn't crash.
        } catch (IllegalArgumentException e) {
            // This is the expected and desired outcome for invalid URI input.
            // The converter correctly identified a syntax error.
        } catch (StackOverflowError | OutOfMemoryError e) {
            // These are critical failures indicating the converter cannot handle certain inputs
            // gracefully.
            fail("Converter crashed with '" + e.getClass().getName()
                    + "' on test case: '" + testCaseName
                    + "'. Input: \"" + escapeFuzzedInput() + "\"");
        } catch (Throwable t) {
            // Any other throwable is a failure.
            // The converter should be robust enough to only throw IllegalArgumentException for
            // parsing errors.
            fail("Converter threw an unexpected exception '" + t.getClass().getName()
                    +  "' for input: \"" + escapeFuzzedInput() + "\"");
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
        private static final String URI_CHARS
                = "abcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=";
        private static final String WHITESPACE = " \t\r\n";
        //@formatter:off
        private static final String[] VALID_URI_SAMPLES = {
                "http://example.com",
                "https://user:pass@example.com:8443/path/to/resource?query=value#fragment",
                "ftp://ftp.is.co.za/rfc/rfc1808.txt",
                "custom-scheme://opaque/part",
                "proxy.example.com:3128",
                "localhost",
                "127.0.0.1",
                "192.168.1.1:8080",
                "[::1]",
                "[fe80::1ff:fe23:4567:89ab]:443"
        };
        //@formatter:on

        private final Random random;

        FuzzGenerator(Random random) {
            this.random = random;
        }

        public List<String> generateEdgeCases() {
            List<String> cases = new ArrayList<>();

            // Empty/Whitespace.
            cases.add("");
            cases.add(" ");
            cases.add("\t\r\n ");

            // Invalid schemes.
            cases.add("://example.com");
            cases.add("1http://example.com");
            cases.add("http:/example.com");
            cases.add("http//example.com");
            cases.add("http:example.com"); // Should be handled, but good to test.
            cases.add("a b://c");

            // Invalid ports.
            cases.add("example.com:");
            cases.add("example.com:abc");
            cases.add("example.com:65536");
            cases.add("example.com:-1");
            cases.add("[::1]:");
            cases.add("[::1]:xyz");

            // Malformed IPv6.
            cases.add("[fe80::1"); // Missing closing bracket
            cases.add("fe80::1]"); // Missing opening bracket
            cases.add("[fe80::1]extra"); // Trailing garbage
            cases.add("[[fe80::1]]"); // Nested brackets
            cases.add("[]");
            cases.add("[:]");
            cases.add("[]:8080");

            // Malformed IPv4.
            cases.add("256.0.0.1");
            cases.add("127.0.0");
            cases.add("127.0.0.1.2");

            // General malformations.
            cases.add(":");
            cases.add("::");
            cases.add(":8080");
            cases.add("http://");
            cases.add("http://:8080");
            cases.add("http://user@");
            cases.add("http://\nexample.com"); // Control characters
            cases.add("http://example.com\0"); // Null byte

            return cases;
        }

        public String[] generateRandomStrings(int count, int maxLength) {
            String[] strings = new String[count];
            for (int i = 0; i < count; i++) {
                int length = random.nextInt(maxLength) + 1;
                StringBuilder sb = new StringBuilder(length);
                for (int j = 0; j < length; j++) {
                    int type = random.nextInt(10);
                    if (type < 5) { // 50% URI-specific chars
                        sb.append(URI_CHARS.charAt(random.nextInt(URI_CHARS.length())));
                    } else if (type < 7) { // 20% whitespace
                        sb.append(WHITESPACE.charAt(random.nextInt(WHITESPACE.length())));
                    } else { // 30% random chars (up to BMP)
                        sb.append((char) random.nextInt(0xD7FF));
                    }
                }
                strings[i] = sb.toString();
            }
            return strings;
        }

        public String[] generateMutatedUris(int count, int maxMutations) {
            String[] strings = new String[count];
            for (int i = 0; i < count; i++) {
                String base = VALID_URI_SAMPLES[random.nextInt(VALID_URI_SAMPLES.length)];
                char[] chars = base.toCharArray();
                int mutations = random.nextInt(maxMutations) + 1;
                for (int j = 0; j < mutations && chars.length > 0; j++) {
                    int pos = random.nextInt(chars.length);
                    switch (random.nextInt(3)) {
                        case 0: // Substitute a character
                            chars[pos] = (char) random.nextInt(0xD7FF);
                            break;
                        case 1: // Delete a character
                            chars = deleteCharAt(chars, pos);
                            break;
                        case 2: // Insert a character
                            chars = insertCharAt(chars, pos, (char) random.nextInt(0xD7FF));
                            break;
                    }
                }
                strings[i] = new String(chars);
            }
            return strings;
        }

        private char[] deleteCharAt(char[] src, int pos) {
            char[] dest = new char[src.length - 1];
            if (pos > 0) {
                System.arraycopy(src, 0, dest, 0, pos);
            }
            if (pos < dest.length) {
                System.arraycopy(src, pos + 1, dest, pos, dest.length - pos);
            }
            return dest;
        }

        private char[] insertCharAt(char[] src, int pos, char c) {
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
