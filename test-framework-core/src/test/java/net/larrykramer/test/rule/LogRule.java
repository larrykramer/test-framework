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

import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A JUnit 4 {@code TestRule} for tests that interact with a
 * {@linkplain Logger}'s shared configuration.
 * <p>
 * For test methods annotated with {@code @LogRule.UsesLogger}, this rule:
 * <ul>
 * <li><b>Globally serializes</b> execution of all {@code @LogRule.UsesLogger}
 *   tests, preventing concurrent {@code java.util.logging} configuration
 *   changes by other {@code @LogRule.UsesLogger} tests.
 * <li>Temporarily applies a logger level for the duration of the test.
 * <li>Captures log output emitted during the test for later inspection via
 *   {@link #getRecords()}.
 * </ul>
 * <p>
 * Test methods <em>not</em> annotated with {@code @LogRule.UsesLogger} are not
 * serialized and do not capture logs via this rule. They may run concurrently
 * with each other (and potentially with one {@code @LogRule.UsesLogger} test).
 * However, if they use the same target logger while an annotated test is
 * running, they may be affected by the temporary level change.
 * <p>
 * <b>Note on Child Loggers:</b> This rule sets the level on the exact logger
 * targeted. If the code logs to a child logger (e.g.,
 * {@code com.example.child}) that has its own explicit {@code Level}
 * configuration, that child will <b>not</b> be updated by this rule. You must
 * target the child logger directly in {@code @LogRule.UsesLogger} or ensure the
 * child is configured to inherit its level (i.e., its level is {@code null}).
 *
 * <h2>Concurrent Logging Interference</h2>
 * This rule captures only records whose {@link LogRecord#getLoggerName()}
 * exactly matches the configured target logger name. Records emitted by child
 * (descendant) loggers are ignored, even if they propagate to the target
 * logger via {@code java.util.logging}'s hierarchical handler model. This keeps
 * assertions focused on the component under test and avoids noise from
 * unrelated loggers in the same package hierarchy.
 * <p>
 * Even with exact-name filtering and global serialization of annotated tests,
 * unannotated tests may still run concurrently and may emit records to the
 * <em>same</em> target logger. Such records will be captured as well. For that
 * reason:
 * <ul>
 * <li><b>Prefer</b> assertions that the expected message exists (e.g.,
 *   {@link java.util.stream.Stream#anyMatch Stream.anyMatch}) rather than
 *   asserting that no other messages exist.
 * <li>Asserting an <b>exact</b> count (e.g., {@code assertEquals(1, count)})
 *   is appropriate only when the code path is deterministic and the test
 *   suite does not concurrently emit the same message on the same logger.
 * </ul>
 *
 * <h2>Configuring the target logger</h2>
 * The target logger name and level are chosen per test.
 * <p>
 * The {@code name} attribute from the test's {@code @LogRule.UsesLogger}
 * annotation is used, if it is not blank. Otherwise, the default logger name
 * provided to the {@code LogRule} constructor is used. A logger name must be
 * provided either by the rule constructor or by {@code @LogRule.UsesLogger} on
 * the test method.
 * <p>
 * Similarly, if the {@code level} attribute from the test's
 * {@code @LogRule.UsesLogger} annotation is not blank, it is parsed by
 * {@link Level#parse(String)}. An invalid level name will cause the test to
 * fail immediately. If the {@code level} attribute is blank, the rule's default
 * level is used. The default level is {@code ALL} if not otherwise specified
 * in the {@code LogRule} constructor.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class ATest {
 *     @Rule
 *     public LogRule logRule = new LogRule(ATest.class.getName(), Level.FINE);
 *
 *     @Test
 *     @LogRule.UsesLogger
 *     public void capturesLogsAndRunsExclusivelyWithOtherUsesLoggerTests() {
 *         // This test uses the rule's default logger name and default
 *         // level (FINE).
 *         Logger.getLogger(ATest.class.getName()).fine("hello");
 *         assertTrue(logRule.getRecords().stream()
 *                 .anyMatch(r -> "hello".equals(r.getMessage())));
 *     }
 *
 *     @Test
 *     @LogRule.UsesLogger(level = "INFO")
 *     public void overridesLevelPerTest() {
 *         // Overrides only the level for this test.
 *     }
 *
 *     @Test
 *     public void notAnnotated_runsNormally() {
 *         // Not serialized by this rule and does not capture logs via this
 *         // rule.
 *         //
 *         // Do NOT modify the Logger configuration in this test method.
 *         // Modifying the Logger configuration may result in undefined
 *         // test behavior.
 *     }
 * }
 * }</pre>
 */
public class LogRule implements TestRule {
    private static final Lock GLOBAL_LOCK = new ReentrantLock();

    private final String defaultLoggerName;
    private final Level defaultLevel;

    private final Queue<LogRecord> capturedLogs = new ConcurrentLinkedQueue<>();

    /**
     * Marks a JUnit test method as interacting with (and potentially mutating) a
     * shared {@link Logger Logger} configuration.
     *
     * @see LogRule
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface UsesLogger {
        /**
         * The name of the {@link Logger} used by the test and the logger name
         * whose records will be captured.
         * <p>
         * If blank, the rule's default logger name is used. If both are
         * unspecified, the test is considered misconfigured.
         *
         * @return the logger name, or blank to use the rule's default
         */
        String name() default "";

        /**
         * The logger {@code Level} to apply for the duration of the test.
         * <p>
         * This value must be a name understood by {@link Level#parse(String)}
         * (for example, {@code "INFO"}, {@code "FINE"}, {@code "WARNING"}).
         * If blank, the rule's default level is used.
         *
         * @return the level name, or blank to use the rule's default
         */
        String level() default "";
    }

    /**
     * Creates a rule with no default logger name and a default level of
     * {@code ALL}.
     * <p>
     * When using this constructor, each annotated test method must provide a
     * logger name via {@code @LogRule.UsesLogger(name = "...")}.
     */
    public LogRule() {
        this(null, Level.ALL);
    }

    /**
     * Creates a rule with the given default logger name and a default level of
     * {@code ALL}.
     * <p>
     * Annotated test methods may omit the {@code name} attribute from the
     * {@code @LogRule.UsesLogger} annotation to use this default.
     *
     * @param loggerName the default logger name used by annotated tests when
     *                   the {@code name} attribute from
     *                   {@code @LogRule.UsesLogger} is blank
     */
    public LogRule(String loggerName) {
        this(loggerName, Level.ALL);
    }

    /**
     * Creates a rule with the given default logger name and default level.
     * <p>
     * Annotated test methods may omit the {@code name} and {@code level}
     * attributes from the {@code @LogRule.UsesLogger} annotation to use this
     * default.
     *
     * @param loggerName the default logger name used by annotated tests when
     *                   the {@code name} attribute from
     *                   {@code @LogRule.UsesLogger} is blank
     * @param level      the default logger level used by annotated tests when
     *                   the {@code level} attribute from
     *                   {@code @LogRule.UsesLogger} is blank; must not be
     *                   {@code null}
     */
    public LogRule(String loggerName, Level level) {
        this.defaultLoggerName = loggerName;
        this.defaultLevel = Objects.requireNonNull(level);
    }

    /**
     * {@return captured log records in the order they were enqueued; with
     *          concurrent logging, interleaving is nondeterministic}
     */
    public List<LogRecord> getRecords() {
        return new ArrayList<>(capturedLogs);
    }

    /**
     * Returns a {@link Statement} that applies this rule to the supplied
     * {@code base} statement.
     * <p>
     * If the described test method is annotated with {@link UsesLogger}, the
     * returned statement coordinates execution with other
     * {@code @LogRule.UsesLogger} tests, applies the target logger level for
     * the duration of the test, and captures log output.
     * <p>
     * If the test method is not annotated with {@link UsesLogger}, this method
     * returns {@code base} unchanged.
     *
     * @param base        the original statement to evaluate
     * @param description the JUnit description of the test being run
     * @return a statement that is either unchanged or guarded/augmented for
     *         {@code @LogRule.UsesLogger} tests
     */
    @Override
    public Statement apply(Statement base, Description description) {
        UsesLogger ann = description.getAnnotation(UsesLogger.class);
        return (ann == null) ? base : new LogCaptureStatement(base, ann);
    }

    private class LogCaptureStatement extends Statement {
        private final Statement base;
        private final UsesLogger annotation;

        LogCaptureStatement(Statement base, UsesLogger annotation) {
            this.base = base;
            this.annotation = annotation;
        }

        @Override
        public void evaluate() throws Throwable {
            // Acquire Global Lock.
            // This forces all @LogRule.UsesLogger tests to run one at a time.
            GLOBAL_LOCK.lock();
            try {
                final String targetName = getLoggerName();
                final Level level = getLoggerLevel();

                // Clear previous logs (in case of rule reuse).
                capturedLogs.clear();

                Logger logger = Logger.getLogger(targetName);
                Level originalLevel = logger.getLevel();
                CapturingHandler handler = new CapturingHandler(targetName);

                try {
                    // Note: We don't change useParentHandlers here, we only attach our listener.
                    // If the user needs to suppress console output, they should do so in the test
                    // or via a separate mechanism, as toggling it globally is risky.
                    logger.setLevel(level);
                    logger.addHandler(handler);

                    base.evaluate();
                } finally {
                    logger.removeHandler(handler);
                    logger.setLevel(originalLevel);
                }
            } finally {
                GLOBAL_LOCK.unlock();
            }
        }

        private String getLoggerName() {
            if (!annotation.name().isBlank()) {
                return annotation.name();
            } else if (defaultLoggerName != null) {
                return defaultLoggerName;
            }
            throw new IllegalStateException("Logger name not configured: "
                    + "set LogRule default loggerName or @LogRule.UsesLogger(name=...).");
        }

        private Level getLoggerLevel() {
            return !annotation.level().isBlank() ? Level.parse(annotation.level()) : defaultLevel;
        }
    }

    private final class CapturingHandler extends Handler {
        private final String loggerName;

        CapturingHandler(String loggerName) {
            this.loggerName = loggerName;
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record) && loggerName.equals(record.getLoggerName())) {
                capturedLogs.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
