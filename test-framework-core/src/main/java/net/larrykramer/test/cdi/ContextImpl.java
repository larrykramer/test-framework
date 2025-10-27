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

package net.larrykramer.test.cdi;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.cucumber.java.Scenario;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanManager;

/**
 * Custom CDI context implementation of {@link AlterableContext} backing the
 * {@link ScenarioScoped @ScenarioScoped} scope used in acceptance tests.
 * <p>
 * The context behaves much like Weld’s unbound request context: contextual instances are stored in
 * a {@link ThreadLocal} map, so each thread activating the scope receives its own bean store. The
 * recommended lifecycle is:
 * <ol>
 * <li>{@link #activate(Scenario)} – invoked by the test runtime before any scenario-scoped beans
 *   are resolved. This prepares a bean store and may immediately receive the scenario if known at
 *   activation time.
 * <li>{@link #associate(Scenario)} – typically triggered from a {@code @Before} hook once Cucumber
 *   supplies the scenario object. This records the scenario for the current thread and fires
 *   {@code @Initialized(ScenarioScoped.class)}.
 * <li>Resolve and use CDI beans annotated with {@code @ScenarioScoped} while the scenario executes.
 * <li>{@link #deactivate()} – called from a matching teardown step (for example, a {@code @After}
 *   hook) once the scenario finishes. Destroys all contextual instances, fires
 *   {@code @Destroyed(ScenarioScoped.class)}, and clears the thread-local state.
 * </ol>
 * <p>
 * <strong>Thread confinement:</strong> The scope is designed for single-threaded scenario
 * execution. {@code @ScenarioScoped} beans need not be thread-safe as long as they are accessed
 * solely from the scenario thread. If background callbacks are involved (e.g., WebDriver CDP
 * events), they should marshal their work back to the scenario thread or manage their own
 * synchronization without sharing un-synchronized state.
 *
 * @see ScenarioScoped
 */
class ContextImpl implements AlterableContext {
    private static final Logger LOGGER = Logger.getLogger(ContextImpl.class.getName());

    private static final Initialized.Literal INITIALIZED_LITERAL;
    private static final Destroyed.Literal DESTROYED_LITERAL;

    static {
        INITIALIZED_LITERAL = Initialized.Literal.of(ScenarioScoped.class);
        DESTROYED_LITERAL = Destroyed.Literal.of(ScenarioScoped.class);
    }

    private final BeanManager beanManager;

    // It's a normal scope so there may be no more than one mapped instance per contextual type per
    // thread.
    private final ThreadLocal<Map<Contextual<?>, ContextualInstance<?>>> currentContext;

    private final ThreadLocal<Scenario> lastScenario;

    ContextImpl(BeanManager beanManager) {
        this.beanManager = beanManager;
        this.currentContext = new ThreadLocal<>();
        this.lastScenario = new ThreadLocal<>();
    }

    /**
     * Destroy the existing contextual instance. If there is no existing instance, no action is
     * taken.
     *
     * @param contextual the contextual type
     * @throws ContextNotActiveException if the context is not active
     */
    @Override
    public void destroy(Contextual<?> contextual) {
        if (contextual == null) {
            throw new IllegalArgumentException("No contextual specified to retrieve (null)");
        }

        Map<Contextual<?>, ContextualInstance<?>> ctx = currentContext.get();
        if (ctx == null) {
            // Thread local not set. Context is not active!
            throw new ContextNotActiveException();
        }

        ContextualInstance<?> instance = ctx.remove(contextual);
        if (instance != null) {
            instance.destroy();
        }
    }

    /**
     * Get the scope type of the context object.
     *
     * @return the scope
     */
    @Override
    public Class<? extends Annotation> getScope() {
        return ScenarioScoped.class;
    }

    /**
     * Return an existing instance of certain contextual type or create a new instance by calling
     * {@link Contextual#create(CreationalContext)} and return the new instance.
     *
     * @param <T>               the type of contextual type
     * @param contextual        the contextual type
     * @param creationalContext the context in which the new instance will be created
     * @return the contextual instance
     * @throws ContextNotActiveException if the context is not active
     */
    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        Map<Contextual<?>, ContextualInstance<?>> ctx = currentContext.get();
        if (ctx == null) {
            // Thread local not set. Context is not active!
            throw new ContextNotActiveException();
        }
        if (contextual == null) {
            throw new IllegalArgumentException("No contextual specified to retrieve (null)");
        }

        @SuppressWarnings("unchecked")
        ContextualInstance<T> beanInstance = (ContextualInstance<T>) ctx.get(contextual);
        if (beanInstance != null) {
            return beanInstance.value();
        } else if (creationalContext != null) {
            // Bean instance doesn't exist. Create one if we have a CreationalContext.
            T instance = contextual.create(creationalContext);
            if (instance != null) {
                beanInstance = new ContextualInstance<>(instance, creationalContext, contextual);
                ctx.put(contextual, beanInstance);
            }
            return instance;
        }

        return null;
    }

    /**
     * Return an existing instance of a certain contextual type or a null value.
     *
     * @param <T>        the type of the contextual type
     * @param contextual the contextual type
     * @return the contextual instance, or a null value
     * @throws ContextNotActiveException if the context is not active
     */
    @Override
    public <T> T get(Contextual<T> contextual) {
        return get(contextual, null);
    }

    /**
     * Determines if the context object is active.
     *
     * @return {@code true} if if the context is active, or {@code false} otherwise
     */
    @Override
    public boolean isActive() {
        return currentContext.get() != null;
    }

    /**
     * Associates the context with the Cucumber scenario (for this thread).
     *
     * @param scenario the Cucumber scenario
     */
    public void associate(Scenario scenario) {
        Objects.requireNonNull(scenario);
        if (!isActive()) {
            activate(scenario);
        } else {
            setScenario(scenario);
        }
    }

    /**
     * Activate the Context.
     */
    public void activate(Scenario scenario) {
        if (currentContext.get() != null) {
            // If the previous context was not deactivated properly for some reason, make sure it
            // is destroyed now.
            LOGGER.log(Level.WARNING, "Replacing already active context {0} associated with {1}",
                    new Object[] { this, lastScenario.get() });
            deactivate();
        }

        currentContext.set(new HashMap<>());
        setScenario(scenario);
    }

    private void setScenario(Scenario scenario) {
        if (scenario == null) {
            lastScenario.remove();
        } else {
            // Ensures initialized event is only fired once per scenario.
            if (!scenario.equals(lastScenario.get())) {
                lastScenario.set(scenario);
                beanManager.getEvent().select(INITIALIZED_LITERAL).fire(scenario);
            }
        }
    }

    /**
     * Deactivate the Context.
     */
    public void deactivate() {
        Map<Contextual<?>, ContextualInstance<?>> ctx = currentContext.get();
        if (ctx == null) {
            // Note: We deviate from the CDI spec here to avoid failing every scenario if an
            //       "activate" hook didn't run (e.g., bad hook order, dry-run, or someone forget
            //       to register it).
            LOGGER.log(Level.WARNING, "Context {0} is not active", this);
            return;
        }

        LOGGER.log(Level.FINER, "Current Context thread-local removed: {0}", this);
        currentContext.remove();

        LOGGER.log(Level.FINER, "Context {0} cleared", this);

        for (var it = ctx.entrySet().iterator(); it.hasNext(); ) {
            final ContextualInstance<?> instance = it.next().getValue();
            Object[] params = { instance, this };
            it.remove();
            try {
                instance.destroy();
                LOGGER.log(Level.FINER, "Removed {0} from {1}", params);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Unable to remove {0} from {1}", params);
                LOGGER.throwing(getClass().getName(), "deactivate", t);
            }
        }

        Scenario scenario = lastScenario.get();
        LOGGER.log(Level.FINER, "Last Scenario thread-local removed: {0}", this);
        lastScenario.remove();
        if (scenario != null) {
            beanManager.getEvent().select(DESTROYED_LITERAL).fire(scenario);
        }
    }

    /**
     * Lightweight container that ties together a contextual instance with the metadata required to
     * dispose of it later.
     * <p>
     * Each entry represents one bean stored in the thread-local context map. When the scope is
     * deactivated the container’s {@link #destroy()} method is invoked to ensure the instance is
     * released via {@link Contextual#destroy(Object, CreationalContext)}.
     *
     * @param <T>               the bean type held in the context
     * @param value             the contextual instance created for the scenario
     * @param creationalContext the CDI creational context that must be passed back on destroy
     * @param contextual        the CDI contextual (bean) that produced the instance
     */
    private record ContextualInstance<T>(T value, CreationalContext<T> creationalContext,
            Contextual<T> contextual) {
        void destroy() {
            contextual.destroy(value, creationalContext);
        }
    }
}
