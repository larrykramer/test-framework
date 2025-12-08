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

package net.larrykramer.test.webdriver;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.inject.Vetoed;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

import static net.larrykramer.test.util.SharedUtils.identityToString;

/**
 * Holder for a Selenium {@link WebDriver} instance used within scenario scope.
 * <p>
 * This CDI bean provides an indirection layer between components that create a
 * {@code WebDriver} and components that use it. A WebDriver can be given
 * eagerly via the {@link #WebDriverReference(WebDriver)} constructor or lazily
 * via {@link #set(WebDriver)}, retrieved by any collaborator within the same
 * scenario, and explicitly cleared during teardown.
 * <p>
 * Typical usage pattern:
 * <ul>
 * <li>Framework code constructs a reference with an already-created unmanaged
 *   driver via {@link #WebDriverReference(WebDriver)}, <em>or</em> calls
 *   {@link #set(WebDriver)} on an empty reference created with
 *   {@link #WebDriverReference()}.
 * <li>Test code and helpers retrieve the current WebDriver using one of the
 *   retrieval methods.
 * <li>Framework code calls {@link #clear()} when the scenario ends.
 * </ul>
 *
 * This class itself is {@linkplain Vetoed vetoed} from CDI discovery and is
 * exposed as a scenario-scoped CDI bean via the
 * {@code @Produces @ScenarioScoped} producer method in
 * {@code WebDriverService}.
 *
 * @see net.larrykramer.test.service.WebDriverService WebDriverService
 */
@Vetoed
public class WebDriverReference {
    private static final Logger LOGGER = Logger.getLogger(WebDriverReference.class.getName());

    // exposed for unit tests
    /*package-private*/ static final int MAX_UNWRAP_DEPTH = 25;

    private WebDriver driver;
    private WebDriver underlyingDriver;

    /**
     * Creates a new empty {@code WebDriverReference}.
     *
     * @implNote A WebDriver must be provided later via {@link #set(WebDriver)}
     *           before any retrieval method is called.
     */
    public WebDriverReference() {
        this.driver = null;
        this.underlyingDriver = null;
    }

    /**
     * Creates a new {@code WebDriverReference} with the given WebDriver.
     *
     * @param driver the WebDriver to hold; must not be {@code null}
     * @throws NullPointerException if {@code driver} is {@code null}
     * @see #set(WebDriver)
     */
    public WebDriverReference(WebDriver driver) {
        set(driver);
    }

    /**
     * Sets the reference to {@code driver}.
     * <p>
     * This method unwraps the given {@code WebDriver} by following any chain of
     * {@link WrapsDriver} to the ultimate wrapped driver.
     *
     * @param driver the WebDriver to hold; must not be {@code null}
     * @throws NullPointerException if {@code driver} is {@code null}
     * @apiNote This method assumes that the given driver is not a CDI proxy and
     *          relies on the contract of
     *          {@link WebDriverFactory#createWebDriver()}, which specifies that
     *          factories return a new, unmanaged (non-CDI-proxied)
     *          {@link WebDriver} instance. Framework code must therefore pass
     *          the concrete driver instance returned by the factory into this
     *          method.
     */
    public void set(WebDriver driver) {
        this.driver = Objects.requireNonNull(driver);
        this.underlyingDriver = unwrap(this.driver);
    }

    /**
     * Returns the current WebDriver.
     * <p>
     * This returns the concrete {@link WebDriver} instance that was last given
     * to {@link #set(WebDriver)} (or the constructor), without any additional
     * unwrapping. In contrast, {@link #getUnderlyingDriver()} returns the
     * underlying delegate obtained from that instance (following
     * {@link WrapsDriver} chains).
     *
     * @return the current WebDriver
     * @throws IllegalStateException if no WebDriver has been set
     */
    public WebDriver get() {
        if (driver == null) {
            throw notInitialized();
        }
        return driver;
    }

    /**
     * Returns the current WebDriver as an implementation of the given type, if
     * available.
     * <p>
     * This method first checks the currently held (possibly wrapped) driver
     * and, if it does not implement the requested type, falls back to the
     * underlying unwrapped driver.
     * <p>
     * This is the preferred way to obtain Selenium extension interfaces
     * (for example {@code TakesScreenshot}, {@code JavascriptExecutor},
     * {@code HasDevTools}) while still respecting any registered wrappers.
     *
     * @param interfaceClass the interface or class that the driver is expected
     *                       to implement; must not be {@code null}
     * @return the current WebDriver that also implements {@code interfaceClass}
     * @throws ClassCastException    if the current driver does not implement
     *                               {@code interfaceClass} on either the
     *                               wrapped or underlying driver
     * @throws IllegalStateException if no WebDriver has been set
     * @throws NullPointerException  if {@code interfaceClass} is {@code null}
     */
    public WebDriver get(Class<?> interfaceClass) {
        Objects.requireNonNull(interfaceClass, "interfaceClass");
        if (driver == null) {
            throw notInitialized();
        }

        // Prefer the wrapped WebDriver.
        // Fallback to the underlying driver if wrapped WebDriver doesn't implement the interface
        // class.
        if (interfaceClass.isInstance(driver)) {
            return driver;
        } else if (interfaceClass.isInstance(underlyingDriver)) {
            return underlyingDriver;
        }

        String msg = "The current WebDriver does not support " + interfaceClass.getSimpleName();
        throw new ClassCastException(msg);
    }

    /**
     * Returns the current WebDriver as the specified type.
     * <p>
     * This is a type-safe convenience wrapper around {@link #get(Class)} that
     * both locates a driver implementing the requested interface (checking the
     * wrapped driver first and then the underlying unwrapped driver) and
     * performs the cast.
     *
     * @param <T>            the interface or class that the driver is expected
     *                       to implement
     * @param interfaceClass the interface or class token; must not be
     *                       {@code null}
     * @return the current WebDriver as {@code T}
     * @throws ClassCastException    if the current driver does not implement
     *                               {@code interfaceClass} on either the
     *                               wrapped or underlying driver
     * @throws IllegalStateException if no WebDriver has been set
     * @see #get(Class)
     */
    public <T> T as(Class<T> interfaceClass) {
        return interfaceClass.cast(get(interfaceClass));
    }

    /**
     * Attempts to view the current WebDriver as the specified type.
     * <p>
     * This is a non-throwing variant of {@link #as(Class)}: it tries to obtain
     * the current driver as the requested interface (checking the wrapped
     * driver first and then the underlying unwrapped driver), but returns
     * {@link Optional#empty()} instead of throwing a {@link ClassCastException}
     * when the driver does not implement the given type.
     *
     * @param <T>            the interface or class that the driver is expected
     *                       to implement
     * @param interfaceClass the interface or class token; must not be
     *                       {@code null}
     * @return an {@code Optional} containing the current WebDriver as
     *         {@code T} if supported; otherwise {@code Optional.empty()}
     * @throws IllegalStateException if no WebDriver has been set
     * @see #as(Class)
     */
    public <T> Optional<T> tryAs(Class<T> interfaceClass) {
        try {
            return Optional.of(as(interfaceClass));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the underlying unwrapped driver.
     * <p>
     * <strong>Usage note:</strong><br>
     * This method should only be used when you strictly need access to
     * vendor-specific methods and explicitly want to bypass any registered
     * {@link WrapsDriver}-based wrappers. For standard WebDriver interactions
     * (including Selenium extension interfaces such as {@code TakesScreenshot},
     * {@code JavascriptExecutor}, etc.), prefer {@link #get()} or
     * {@link #get(Class)}.
     *
     * @return the underlying unwrapped driver of the current WebDriver
     * @throws IllegalStateException if no WebDriver has been set
     */
    public WebDriver getUnderlyingDriver() {
        if (underlyingDriver == null) {
            throw notInitialized();
        }
        return underlyingDriver;
    }

    /**
     * Clears this reference.
     *
     * @implNote After calling this method, all retrieval methods will fail
     *           until a new WebDriver is given via {@link #set(WebDriver)}. The
     *           String representation returned by {@link #toString()} will also
     *           revert to the uninitialized form.
     */
    public void clear() {
        this.driver = null;
        this.underlyingDriver = null;
    }

    /**
     * {@return the String representation of the current WebDriver}
     */
    @Override
    public String toString() {
        return identityToString(driver);
    }

    private WebDriver unwrap(WebDriver driver) {
        WebDriver current = driver;

        for (int depth = 0; depth < MAX_UNWRAP_DEPTH; depth++) {
            WebDriver next = null;

            // Unwrap the WrapsDriver chain.
            if (current instanceof WrapsDriver wraps) {
                next = wraps.getWrappedDriver();
            }

            if (next == null || next == current) {
                return current;
            }

            current = next;
        }

        LOGGER.log(Level.FINE, "Maximum WebDriver unwrap depth reached - wrapper chain may be "
                        + "cyclic or deeper than expected\nMaximum unwrap depth: {0}\nLast "
                        + "resolved WebDriver instance: {1}",
                new Object[] { MAX_UNWRAP_DEPTH, identityToString(current) });
        return current;
    }

    private static IllegalStateException notInitialized() {
        return new IllegalStateException("WebDriverReference not initialized");
    }
}
