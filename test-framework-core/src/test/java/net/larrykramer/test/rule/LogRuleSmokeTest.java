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

package net.larrykramer.test.rule;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.larrykramer.test.categories.SmokeTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@Category(SmokeTest.class)
public class LogRuleSmokeTest {
    private static final String LOGGER_NAME = LogRuleSmokeTest.class.getName();
    private static final String CUSTOM_LOGGER_NAME = "net.larrykramer.test.rule.CustomLogger";

    @Rule
    public final LogRule logRule = new LogRule(LOGGER_NAME, Level.FINE);

    @Test
    @LogRule.UsesLogger
    public void testApply_withLoggerAnnotation_capturesLogRecords() {
        // Arrange
        Logger logger = Logger.getLogger(LOGGER_NAME);

        // Act
        Level level = logger.getLevel();

        logger.log(Level.FINER, "should not be captured");
        logger.log(Level.FINE, "should be captured");

        // Assert
        // The test suite and this test method write to the LogRuleSmokeTest logger so this code
        // path is non-deterministic. Consequently, we must assert that an expected message exists
        // and *not* the exact count.
        assertEquals(Level.FINE, level);
        assertTrue(logRule.getRecords().stream()
                .filter(r -> r.getLevel() == Level.FINE)
                .anyMatch(r -> "should be captured".equals(r.getMessage())));
    }

    @Test
    @LogRule.UsesLogger(level = "INFO")
    public void testApply_withOverriddenLevel_capturesAtNewLevel() {
        // Arrange
        Logger logger = Logger.getLogger(LOGGER_NAME);

        // Act
        Level level = logger.getLevel();

        logger.log(Level.FINE, "should not be captured");
        logger.log(Level.INFO, "should be captured");

        // Assert
        // The test suite and this test method write to the LogRuleSmokeTest logger so this code
        // path is non-deterministic. Consequently, we must assert an inexact count (>= 1).
        assertEquals(Level.INFO, level);

        long count = logRule.getRecords().stream()
                .filter(r -> r.getLevel() == Level.INFO)
                .filter(r -> "should be captured".equals(r.getMessage()))
                .count();
        assertTrue(count >= 1L);
    }

    @Test
    @LogRule.UsesLogger(name = CUSTOM_LOGGER_NAME, level = "WARNING")
    public void testApply_withOverriddenNameAndLevel_capturesForDifferentLoggerAtNewLevel() {
        // Arrange
        Logger defaultLogger = Logger.getLogger(LOGGER_NAME);
        Logger customLogger = Logger.getLogger(CUSTOM_LOGGER_NAME);

        // Act
        Level customLoggerLevel = customLogger.getLevel();

        defaultLogger.log(Level.WARNING, "not captured from default logger");
        customLogger.log(Level.INFO, "not captured, below threshold");
        customLogger.log(Level.WARNING, "captured from other logger");

        // Assert
        // The test suite doesn't write to the custom logger so this code path is deterministic,
        // and we can assert an exact count.
        assertEquals(Level.WARNING, customLoggerLevel);

        long count = logRule.getRecords().stream()
                .filter(r -> r.getLevel() == Level.WARNING)
                .filter(r -> "captured from other logger".equals(r.getMessage()))
                .count();
        assertEquals(1, count);
    }

    @Test
    public void testApply_withoutAnnotation_returnsBaseStatement() {
        // Arrange
        final Statement base = new Statement() {
            @Override
            public void evaluate() {
                /* no-op */
            }
        };
        final String name = "testApply_withoutAnnotation_returnsBaseStatement";
        Description description = Description.createTestDescription(LogRuleSmokeTest.class, name);
        // Act
        Statement applied = logRule.apply(base, description);
        // Assert
        assertSame(base, applied);
    }
}
