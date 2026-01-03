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

package net.larrykramer.test.junit.categories;

/**
 * Marker interface used with JUnit 4's {@code Category} annotation to identify
 * fuzzing-based tests.
 * <p>
 * Apply this category to tests that rely on automated input generation
 * (fuzzers) to stress the system with varied or unexpected data. These tests
 * complement conventional unit and integration suites by helping uncover
 * robustness issues such as crashes, unhandled exceptions, or security
 * vulnerabilities.
 * <p>
 * <b>Example</b>
 * <pre>{@code
 * @Category(FuzzerTest.class)
 * public class ParserFuzzerTest {
 *     @Test
 *     public void fuzzParserInputs() {
 *         // fuzzing logic
 *     }
 * }
 * }</pre>
 * <p>
 * The interface is intentionally empty; it serves solely as a semantic tag.
 * You can include or exclude fuzzing-oriented tests by configuring your build
 * tool or IDE to filter on this category when running the JUnit suite.
 */
public interface FuzzerTest {
    /* category marker */
}
