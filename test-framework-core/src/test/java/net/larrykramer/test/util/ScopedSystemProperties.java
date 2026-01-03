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

package net.larrykramer.test.util;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Test utility for scoping changes made to {@link System#getProperties()}
 * within a block of code.
 * <p>
 * Many tests (and some production code) temporarily modify system properties
 * and rely on a predictable environment afterward. Repeatedly saving and
 * restoring the global {@code Properties} instance is error-prone—especially
 * when tests run in parallel or spawn child threads.
 * {@code ScopedSystemProperties} exists to make those temporary overrides safe,
 * composable, and thread-aware by providing an isolated snapshot that is
 * automatically restored when the scope ends.
 * <p>
 * This utility supports nested scopes on a per-thread basis, which are also
 * inherited by child threads. The recommended usage is within a
 * try-with-resources statement to ensure that the environment is closed and
 * previous property values are restored automatically.
 */
public class ScopedSystemProperties {
    private static final ManagedProperties DELEGATE;
    private static final Properties ROOT;

    static {
        ROOT = System.getProperties();
        DELEGATE = new ManagedProperties(ROOT);
        System.setProperties(DELEGATE);
    }

    private ScopedSystemProperties() {
        // Ensure there is only one instance of this test utility class.
    }

    /**
     * Opens a new scoped environment for system properties.
     * <p>
     * The returned {@linkplain Environment scoped environment} maintains a
     * snapshot of the properties visible at the time of invocation. All
     * subsequent reads and writes to {@link System#getProperties()} are
     * confined to this new scope and will be discarded when the environment is
     * closed.
     *
     * @return a newly opened environment that should be closed to restore the
     *         previous state
     */
    public static Environment open() {
        return new Environment(DELEGATE);
    }

    /**
     * Represents a scoped view of system properties.
     * <p>
     * Instances are created via {@link ScopedSystemProperties#open()} and hold
     * an isolated copy of the properties stack for the current thread. Closing
     * the environment restores the prior state, ensuring temporary property
     * overrides do not leak outside the scope.
     */
    public static final class Environment implements AutoCloseable {
        private final ManagedProperties properties;
        private final Properties snapshot;
        private final AtomicBoolean closed;

        private Environment(ManagedProperties properties) {
            this.properties = properties;
            this.snapshot = properties.enter(); // push a cloned view
            this.closed = new AtomicBoolean(false);
        }

        /**
         * Sets a system property within this scoped environment.
         * <p>
         * The change affects only the current scope (and any nested scopes) and
         * is discarded when the environment is closed. Attempting to modify the
         * scope after it has been closed results in an
         * {@link IllegalStateException}.
         *
         * @param key   the name of the system property
         * @param value the value of the system property
         * @return the previous string value of the system property, or
         *         {@code null} if there was no property with that key
         * @throws IllegalArgumentException if {@code key} is empty
         * @throws IllegalStateException    if the environment has already been
         *                                  closed
         * @throws NullPointerException     if {@code key} is null
         */
        public String setProperty(String key, String value) {
            checkKey(key);
            ensureOpen();
            return (String) properties.current().setProperty(key, value);
        }

        /**
         * Removes a system property within this scoped environment.
         * <p>
         * The change affects only the current scope (and any nested scopes) and
         * is discarded when the environment is closed. Attempting to modify the
         * scope after it has been closed results in an
         * {@link IllegalStateException}.
         *
         * @param key   the name of the system property to be removed
         * @return the previous string value of the system property, or
         *         {@code null} if there was no property with that key
         * @throws IllegalArgumentException if {@code key} is empty
         * @throws IllegalStateException    if the environment has already been
         *                                  closed
         * @throws NullPointerException     if {@code key} is null
         */
        public String clearProperty(String key) {
            checkKey(key);
            ensureOpen();
            return (String) properties.current().remove(key);
        }

        /**
         * Closes this environment and restores the system properties to their
         * previous state.
         * <p>
         * The isolated property view associated with this environment is
         * deactivated, ensuring that any temporary overrides applied within
         * this scope are discarded. Closing more than once has no effect
         * beyond the first invocation.
         *
         * @throws IllegalStateException if the scope is closed out of LIFO
         *                               order
         */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                // Pop view that belongs to us.
                properties.exit(snapshot);
            }
        }

        private void ensureOpen() {
            if (closed.get()) {
                throw new IllegalStateException("Scoped property environment already closed");
            }
        }

        private static void checkKey(String key) {
            if (key == null) {
                throw new NullPointerException("Key is null");
            }
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Key is empty");
            }
        }
    }

    /*
     * A Properties implementation that maintains a per-thread stack of property
     * views.
     *
     * The top of the stack represents the active view for the thread (and any child
     * threads), enabling nested, scoped modifications. All Properties operations
     * delegate to the currently active view, providing transparent isolation for
     * clients.
     */
    private static final class ManagedProperties extends Properties {
        private final Properties root;
        private final InheritableThreadLocal<ArrayDeque<Properties>> stack;

        ManagedProperties(Properties root) {
            super();
            this.root = Objects.requireNonNull(root, "Root is null");
            this.stack = new InheritableThreadLocal<>() {
                @Override
                protected ArrayDeque<Properties> initialValue() {
                    ArrayDeque<Properties> deque = new ArrayDeque<>();
                    deque.push(ManagedProperties.this.root);
                    return deque;
                }

                @Override
                protected ArrayDeque<Properties> childValue(ArrayDeque<Properties> parentValue) {
                    ArrayDeque<Properties> child = new ArrayDeque<>(parentValue.size());
                    var it = parentValue.descendingIterator();
                    while (it.hasNext()) {
                        Properties view = it.next();
                        child.addFirst((view == ManagedProperties.this.root) ? view : copyOf(view));
                    }
                    return child;
                }
            };
        }

        /**
         * Pushes a cloned copy of the current properties onto the stack for
         * the active thread.
         */
        Properties enter() {
            Properties copy = copyOf(current());
            stack.get().push(copy);
            return copy;
        }

        /**
         * Pops the most recently pushed properties snapshot from the stack.
         * <p>
         * The supplied {@code expected} reference is compared against the
         * snapshot at the top of the stack to enforce LIFO ordering. Attempting
         * to exit the root scope or closing scopes out of order results in an
         * {@link IllegalStateException}.
         */
        void exit(Properties expected) {
            ArrayDeque<Properties> deque = stack.get();
            if (deque.size() == 1) {
                throw new IllegalStateException("No scoped property environment to exit");
            }

            Properties actual = deque.pop();
            if (actual != expected) {
                deque.push(actual);
                throw new IllegalStateException(
                        "Property environment must be closed in LIFO order");
            }
        }

        private Properties current() {
            return stack.get().peek();
        }

        private static Properties copyOf(Properties source) {
            Properties copy = new Properties();
            copy.putAll(source);
            return copy;
        }

        // -- Properties overrides --
        // Every call that System (or user code) makes against the Properties instance ends up
        // delegating to the top-of-stack view for the current thread (and its children).

        @Override
        public synchronized Object setProperty(String key, String value) {
            return current().setProperty(key, value);
        }

        @Override
        public synchronized void load(Reader reader) throws IOException {
            current().load(reader);
        }

        @Override
        public synchronized void load(InputStream inStream) throws IOException {
            current().load(inStream);
        }

        @Override
        @SuppressWarnings("deprecation")
        public synchronized void save(OutputStream out, String comments) {
            current().save(out, comments);
        }

        @Override
        public void store(Writer writer, String comments) throws IOException {
            current().store(writer, comments);
        }

        @Override
        public void store(OutputStream out, String comments) throws IOException {
            current().store(out, comments);
        }

        @Override
        public synchronized void loadFromXML(InputStream in) throws IOException {
            current().loadFromXML(in);
        }

        @Override
        public void storeToXML(OutputStream os, String comment) throws IOException {
            current().storeToXML(os, comment);
        }

        @Override
        public void storeToXML(OutputStream os, String comment, String encoding)
                throws IOException {
            current().storeToXML(os, comment, encoding);
        }

        @Override
        public void storeToXML(OutputStream os, String comment, Charset charset)
                throws IOException {
            current().storeToXML(os, comment, charset);
        }

        @Override
        public String getProperty(String key) {
            return current().getProperty(key);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return current().getProperty(key, defaultValue);
        }

        @Override
        public Enumeration<?> propertyNames() {
            return current().propertyNames();
        }

        @Override
        public Set<String> stringPropertyNames() {
            return current().stringPropertyNames();
        }

        @Override
        public void list(PrintStream out) {
            current().list(out);
        }

        @Override
        public void list(PrintWriter out) {
            current().list(out);
        }

        @Override
        public int size() {
            return current().size();
        }

        @Override
        public boolean isEmpty() {
            return current().isEmpty();
        }

        @Override
        public Enumeration<Object> keys() {
            return current().keys();
        }

        @Override
        public Enumeration<Object> elements() {
            return current().elements();
        }

        @Override
        public boolean contains(Object value) {
            return current().contains(value);
        }

        @Override
        public boolean containsValue(Object value) {
            return current().containsValue(value);
        }

        @Override
        public boolean containsKey(Object key) {
            return current().containsKey(key);
        }

        @Override
        public Object get(Object key) {
            return current().get(key);
        }

        @Override
        public synchronized Object put(Object key, Object value) {
            return current().put(key, value);
        }

        @Override
        public synchronized Object remove(Object key) {
            return current().remove(key);
        }

        @Override
        public synchronized void putAll(Map<?, ?> t) {
            current().putAll(t);
        }

        @Override
        public synchronized void clear() {
            current().clear();
        }

        @Override
        public synchronized String toString() {
            return current().toString();
        }

        @Override
        public Set<Object> keySet() {
            return current().keySet();
        }

        @Override
        public Collection<Object> values() {
            return current().values();
        }

        @Override
        public Set<Map.Entry<Object, Object>> entrySet() {
            return current().entrySet();
        }

        @Override
        public synchronized boolean equals(Object o) {
            return current().equals(o);
        }

        @Override
        public synchronized int hashCode() {
            return current().hashCode();
        }

        @Override
        public Object getOrDefault(Object key, Object defaultValue) {
            return current().getOrDefault(key, defaultValue);
        }

        @Override
        public synchronized void forEach(BiConsumer<? super Object, ? super Object> action) {
            current().forEach(action);
        }

        @Override
        public synchronized void replaceAll(
                BiFunction<? super Object, ? super Object, ?> function) {
            current().replaceAll(function);
        }

        @Override
        public synchronized Object putIfAbsent(Object key, Object value) {
            return current().putIfAbsent(key, value);
        }

        @Override
        public synchronized boolean remove(Object key, Object value) {
            return current().remove(key, value);
        }

        @Override
        public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
            return current().replace(key, oldValue, newValue);
        }

        @Override
        public synchronized Object replace(Object key, Object value) {
            return current().replace(key, value);
        }

        @Override
        public synchronized Object computeIfAbsent(Object key,
                Function<? super Object, ?> mappingFunction) {
            return current().computeIfAbsent(key, mappingFunction);
        }

        @Override
        public synchronized Object computeIfPresent(Object key,
                BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            return current().computeIfPresent(key, remappingFunction);
        }

        @Override
        public synchronized Object compute(Object key,
                BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            return current().compute(key, remappingFunction);
        }

        @Override
        public synchronized Object merge(Object key, Object value,
                BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            return current().merge(key, value, remappingFunction);
        }

        @Override
        public synchronized Object clone() {
            return current().clone();
        }
    }
}
