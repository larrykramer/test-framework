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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Test support class that plays a dual role: it is both a custom
 * {@code ClassLoader} capable of injecting test-controlled resources and a
 * utility providing the static helper for executing code under that loader.
 * <p>
 * Production code under test typically resolves configuration through
 * {@link Thread#getContextClassLoader()}. Creating temporary files or altering
 * the global class path would make tests brittle and difficult to run in
 * parallel. This class removes that burden by letting tests swap in an isolated
 * loader instance that serves in-memory resources while still delegating
 * bytecode lookup to the real parent.
 *
 * <h2>Usage overview</h2>
 * <ol>
 * <li>{@link #doInvoke(Class, Map, ThrowingFunction)} instantiates a fresh
 *   {@code IsolatedClassLoader}, installs it as the thread context loader, and
 *   reloads the requested class through it.
 * <li>The provided {@link ThrowingFunction} performs whatever reflective work
 *   the test requires while the custom loader is active.
 * <li>After the callback finishes (or throws), the original context loader is
 *   restored and any relevant exception causes are unwrapped to keep assertions
 *   readable.
 * </ol>
 *
 * The class loader instance returned by
 * {@link #doInvoke(Class, Map, ThrowingFunction)} behaves in a child-first
 * manner only for the target class (and other allow-listed packages), ensuring
 * that production bytecode is reused while tests can precisely control
 * resource loading.
 *
 * @see Thread#getContextClassLoader()
 * @see ClassLoader#getResourceAsStream(String)
 */
public final class IsolatedClassLoader extends ClassLoader {
    /*
     * Packages that may be resolved through this loader. Defining an explicit allow-list keeps the
     * child-first behavior limited to classes under test and avoids maintaining a brittle exclusion
     * list.
     */
    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "net.larrykramer.test"        // any class within our package is allowed
    );

    private final Class<?> isolatedClass;
    private final Map<String, byte[]> resources;

    private IsolatedClassLoader(ClassLoader parent, Class<?> c, Map<String, ?> resources) {
        super(parent);
        this.isolatedClass = c;
        this.resources = copyResourceMap(resources);
    }

    private static Map<String, byte[]> copyResourceMap(Map<String, ?> resources) {
        if (resources == null) {
            return null;
        }

        Map<String, byte[]> m = HashMap.newHashMap(resources.size());
        for (var resource : resources.entrySet()) {
            String name = resource.getKey();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Resource name must not be null or blank");
            }
            if (name.endsWith(".class")) {
                throw new IllegalArgumentException("Resource map can't contain classes: " + name);
            }

            switch (resource.getValue()) {
                case null -> m.put(name, null);
                case String s -> {
                    // A simple heuristic for converting simulated properties files to raw bytes.
                    // The ISO-8859-1 charset matches the semantics of Properties.load, which is
                    // what production code is expected to use. All other simulated resource files
                    // use UTF-8. If a different encoding is needed, encode the string into a byte
                    // array before passing it to the isolated class loader.
                    if (name.endsWith(".properties")) {
                        m.put(name, s.getBytes(StandardCharsets.ISO_8859_1));
                    } else {
                        m.put(name, s.getBytes(StandardCharsets.UTF_8));
                    }
                }
                case byte[] ba -> m.put(name, ba);
                default -> throw new IllegalArgumentException("Unknown resource type for " + name);
            }
        }

        return m;
    }

    /**
     * Executes the supplied {@link ThrowingFunction} while the specified class
     * is reloaded through an {@code IsolatedClassLoader}, without supplying any
     * resource overrides.
     *
     * @implSpec This method is equivalent to {@code doInvoke(c, null, fn)}.
     *
     * @param <T> result type produced by the supplied function
     * @param c   class that should be (re)loaded within an isolated class
     *            loader
     * @param fn  operation to perform while the isolated loader is active
     * @return value returned by {@code fn}
     * @throws Throwable if class loading or the supplied function fails
     * @see #doInvoke(Class, Map, ThrowingFunction)
     */
    public static <T> T doInvoke(Class<?> c, ThrowingFunction<Class<?>, T> fn) throws Throwable {
        return doInvoke(c, null, fn);
    }

    /**
     * Core utility responsible for wiring the thread context
     * {@code ClassLoader} and executing operations under that loader.
     * <p>
     * A distinct loader instance is created per invocation so that:
     * <ul>
     * <li>every test receives a clean-slate set of in-memory resource
     *   overrides;
     * <li>tests can run in parallel without sharing state;
     * <li>the parent class path is still available for loading the actual
     *   class implementation while we inject only the requested resource
     *   overrides.
     * </ul>
     * <p>
     * If the class's static initializer throws an
     * {@link ExceptionInInitializerError}, the helper unwraps the original
     * cause so callers can assert on the production exception.
     * <p>
     * The {@code resources} map values may be {@code String}, {@code byte[]},
     * or {@code null} to simulate a missing resource.
     *
     * @param <T>        type of value returned by the function
     * @param c          production class that should be (re)loaded with the
     *                   isolated class loader
     * @param resources  simulated resource overrides keyed by resource name,
     *                   or {@code null} to disable resource overriding
     * @param fn         operation to perform while the isolated loader is
     *                   active
     * @return result produced by the supplied function
     * @throws Throwable if class loading or the function execution fails
     */
    public static <T> T doInvoke(Class<?> c,
            Map<String, ?> resources,
            ThrowingFunction<Class<?>, T> fn) throws Throwable {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        IsolatedClassLoader loader = new IsolatedClassLoader(original, c, resources);
        thread.setContextClassLoader(loader);
        try {
            Class<?> clazz = Class.forName(c.getName(), true, loader);
            T result = fn.apply(clazz);
            return result;
        } catch (InvocationTargetException | ExceptionInInitializerError e) {
            // Make unit-test failures more readable by rethrowing the underlying cause.
            throw unwrapThrowable(e);
        } finally {
            // Critical to avoid contaminating other tests or production code paths.
            thread.setContextClassLoader(original);
        }
    }

    private static Throwable unwrapThrowable(Throwable t) {
        Throwable cause = t.getCause();
        return switch (cause) {
            case null -> new IllegalStateException("No cause associated with throwable", t);
            case Error error -> error;
            case Exception exception -> exception;
            default -> new RuntimeException("Unexpected failure", cause);
        };
    }

    /**
     * Intercepts requests for any resource whose content has been supplied in
     * the override map and serves those bytes (or signals absence when the
     * mapped value is {@code null}). All other resources fall through to the
     * parent chain.
     *
     * @param name the resource name
     * @return an {@link InputStream} for the resource, or {@code null} if it
     *         does not exist
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        if (resources != null && resources.containsKey(name)) {
            byte[] contents = resources.get(name);
            if (contents != null) {
                return new ByteArrayInputStream(contents);
            }
            // Explicitly return null to ensure tests behave as though the resource is absent.
            return null;
        }
        return super.getResourceAsStream(name);
    }

    /**
     * Ensures the target API classes themselves are loaded through this custom
     * loader so that the lookup for the overridden resource sees the injected
     * content. All other classes delegate to the parent to avoid duplicate
     * definitions.
     *
     * @param name    the binary name of the class to load
     * @param resolve if {@code true} then resolve the class
     * @return the loaded {@link Class} instance
     * @throws ClassNotFoundException if the class cannot be located
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (delegateToParent(name)) {
            return super.loadClass(name, resolve);
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = findClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    /**
     * Loads the actual class bytes for the target API classes by delegating to
     * the parent loader's resources and then defining the class inside this
     * loader's namespace. This is standard child-first loading boilerplate when
     * you want resource injection while still trusting the parent for bytecode.
     *
     * @implNote
     * The parent class's {@link ProtectionDomain} is intentionally reused when
     * defining the reloaded class. Preserving the original domain keeps
     * metadata such as the {@code CodeSource} aligned with the parent
     * definition, which avoids breaking coverage/instrumentation tooling that
     * expects the isolated test copy to look like the same class origin. Do
     * not "simplify" this to a default or {@code null} protection domain
     * unless that tooling behavior is no longer required.
     *
     * @param name the binary name of the class to locate
     * @return the defined {@link Class} instance
     * @throws ClassNotFoundException if the class bytecode cannot be found or
     *                                read
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = getParent().getResourceAsStream(path)) {
            if (in == null) {
                throw new ClassNotFoundException("Could not find class bytes for " + name);
            }

            ProtectionDomain pd = null;
            try {
                // Intentionally preserve the parent's ProtectionDomain so the isolated definition
                // retains the original CodeSource and remains visible to coverage/instrumentation
                // tooling.
                pd = getParent().loadClass(name).getProtectionDomain();
            } catch (ClassNotFoundException | LinkageError e) {
                // Parent can't load the class; fall back to default ProtectionDomain
            }

            byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length, pd);
        } catch (IOException e) {
            throw new ClassNotFoundException("Could not load class " + name, e);
        }
    }

    private boolean delegateToParent(String name) {
        assert (name != null) : "Trusted caller missed null check";
        String className = isolatedClass.getName();
        if (name.equals(className) || name.startsWith(className + "$")) {
            // The class itself or an inner class is a direct match.
            return false;
        }
        for (String pn : ALLOWED_PACKAGES) {
            if (name.startsWith(pn)) {
                return false;
            }
        }
        return true;
    }
}
