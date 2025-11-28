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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.cucumber.core.backend.ObjectFactory;
import io.cucumber.java.Scenario;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.*;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import org.jboss.weld.config.ConfigurationKey;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

/**
 * Cucumber {@link ObjectFactory} and CDI {@link Extension} that boots a Weld SE container and
 * provides the custom {@link ScenarioScoped} context.
 * <p>
 * Cucumber creates exactly one instance of this class per test run. The constructor stores a
 * static reference so glue code (typically hooks) can obtain it via {@link #getInstance()} and
 * interact with the scenario lifecycle.
 * <p>
 * Lifecycle overview for each scenario:
 * <ol>
 * <li>{@link #start()} – invoked by Cucumber before the first glue class is instantiated. Boots
 *   Weld on first use, registers this object as an extension, and activates the scenario scope
 *   with no associated scenario.
 * <li>{@link #associate(Scenario)} – should be called from an {@code @Before} hook with a low
 *   order value. Associates the current Cucumber {@link Scenario} with the already-active scope
 *   and fires {@code @Initialized(ScenarioScoped.class)} with the scenario payload.
 * <li>{@link #getInstance(Class)} – returns CDI-managed glue beans. Glue classes collected via
 *   {@link #addClass(Class)} are registered dynamically as {@code @ScenarioScoped} beans, ensuring
 *   they are unique per scenario.
 * <li>{@link #stop()} – invoked by Cucumber after each scenario. Disposes any unmanaged glue
 *   instances and deactivates the current scenario context, firing
 *   {@code @Destroyed(ScenarioScoped.class)}.
 * </ol>
 * <p>
 * To cleanly shut down Weld when all scenarios in the JVM have finished, call
 * {@link #shutdownWeldContainer()} from a Cucumber {@code @AfterAll} hook (or let the JVM shutdown
 * hook added by {@link #start()} handle it).
 * <p>
 * <strong>Note:</strong> This implementation assumes that scenarios in a single JVM execute
 * sequentially on a single thread. If parallel execution is enabled, each thread must activate,
 * associate, and deactivate its own scope instance.
 */
public class WeldObjectFactory implements ObjectFactory, Extension {
    /**
     * {@return a default Weld builder used to configure the Weld container}
     */
    public static Weld createDefaultWeld() {
        return new Weld().property(ConfigurationKey.CONCURRENT_DEPLOYMENT.get(), false);
    }

    private static final Logger LOGGER = Logger.getLogger(WeldObjectFactory.class.getName());

    private static final AtomicReference<WeldObjectFactory> SELF;
    private static final Set<WeldObjectFactory> FACTORIES;

    static {
        SELF = new AtomicReference<>();

        FACTORIES = ConcurrentHashMap.newKeySet();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LinkedHashSet<WeldObjectFactory> factories = new LinkedHashSet<>(FACTORIES);
                for (var factory : factories) {
                    // The shutdownWeldContainer method is idempotent and checks the running state.
                    // Therefore, it is safe to call this method unconditionally.
                    factory.shutdownWeldContainer();
                }
            }
        });
    }

    private final AtomicBoolean closed;

    private final Supplier<Weld> weldFactory;

    private final AtomicBoolean started;
    private volatile WeldContainer container;
    private volatile ContextImpl context = null;

    private final Map<Class<?>, Unmanaged.UnmanagedInstance<?>> instances;
    private final Set<Class<?>> glueClasses;

    /**
     * Constructs a {@code WeldObjectFactory} that uses the default Weld builder supplied by
     * {@link #createDefaultWeld()}.
     * <p>
     * This constructor is the one invoked by Cucumber via reflection when the object factory is
     * listed in the runtime options. It delegates to {@link #WeldObjectFactory(Supplier)} with a
     * supplier that returns a fresh {@link Weld} instance for each container boot.
     */
    public WeldObjectFactory() {
        this(WeldObjectFactory::createDefaultWeld);
    }

    /**
     * Creates a new Cucumber {@link ObjectFactory} that obtains its {@link Weld} builder from the
     * supplied builder factory.
     * <p>
     * <strong>Important:</strong> The supplier must return a <em>fresh</em> {@link Weld} builder
     * on every invocation. A {@link Weld} instance is single-use. Once {@link Weld#initialize()}
     * has been called the builder cannot be reused&mdash;callers must not cache or recycle the
     * same builder.
     *
     * @param weldFactory function that supplies the {@code Weld} builder to initialize the
     *                    container
     */
    WeldObjectFactory(Supplier<Weld> weldFactory) {
        this.instances = new HashMap<>();
        this.glueClasses = new HashSet<>();

        this.weldFactory = Objects.requireNonNull(weldFactory);

        this.started = new AtomicBoolean(false); // Weld container not started

        this.closed = new AtomicBoolean(false);

        while (true) {
            WeldObjectFactory previous = SELF.get();
            if (previous == null || previous.closed.get()) {
                if (SELF.compareAndSet(previous, this)) {
                    break; // successfully installed this object factory
                }
                continue; // CAS failed; retry
            }
            if (previous != this) {
                throw new IllegalStateException(previous.getClass().getName() + " is still active");
            }

            // Same instance reentrantly constructed (shouldn't happen).
            break;
        }
    }

    /**
     * Returns the singleton {@code WeldObjectFactory} instance created by Cucumber.
     * <p>
     * <strong>Lifecycle Note:</strong> The static reference to this factory is deliberately
     * retained even after {@link #shutdownWeldContainer()} is invoked. This is a defensive measure
     * that allows multiple {@code @AfterAll} hooks to safely obtain the factory instance without
     * encountering an {@link IllegalStateException} due to a non-deterministic teardown order.
     * <p>
     * Callers receiving the factory after shutdown will get a valid, non-null instance, but it
     * will be in a "closed" state. Attempting retrieve beans from a closed factory will result in
     * an exception, as the underlying CDI container will have been stopped.
     *
     * @return the active or closed singleton instance of the factory
     * @throws IllegalStateException if Cucumber has not yet constructed the factory
     */
    public static WeldObjectFactory getInstance() {
        WeldObjectFactory instance = SELF.get();
        if (instance == null) {
            throw new IllegalStateException("WeldObjectFactory has not been created yet");
        }
        return instance;
    }

    /**
     * Stops the Weld SE container and clears any state associated with the previous run.
     * <p>
     * This should be called once after all scenarios have finished (for example from a Cucumber
     * {@code @AfterAll} hook or a JVM shutdown hook). Once the container is shut down, the next
     * call to {@link #start()} will boot a fresh instance and the glue classes will be
     * rediscovered.
     */
    public void shutdownWeldContainer() {
        final WeldContainer container = this.container; // local snapshot
        try {
            instances.clear();
            context = null;
            this.container = null;
            if (container != null && container.isRunning()) {
                container.shutdown();
                LOGGER.log(Level.CONFIG, "Weld container {0} stopped", container.getId());
            }
        } catch (Throwable t) {
            String id = (container == null) ? "<uninitialized>" : container.getId();
            LOGGER.log(Level.WARNING, "Unable to stop Weld container {0}", id);
            LOGGER.throwing(getClass().getName(), "shutdownWeldContainer", t);
        } finally {
            started.set(false);
            closed.set(true);
            FACTORIES.remove(this);
            // Note: The static SELF reference is intentionally NOT reset to null.
            // See the Javadoc for #getInstance() for the detailed rationale.
        }
    }

    /**
     * Associates the current Cucumber {@link Scenario} with the already–activated
     * {@code @ScenarioScoped} CDI context.
     * <p>
     * The scenario scope is activated during {@link #start()}; when Cucumber later supplies the
     * {@link Scenario} object to a {@code @Before} hook, the hook should call this method so that
     * the context can record the scenario and fire the {@code @Initialized(ScenarioScoped.class)}
     * CDI event with the correct payload.
     * <p>
     * <strong>Important:</strong> This method should be invoked once per scenario, prior to
     * accessing any scenario-scoped beans.
     *
     * @param scenario the Cucumber scenario that is currently executing
     * @throws IllegalStateException if the scenario-scoped context has not been registered yet
     */
    public void associate(Scenario scenario) {
        ContextImpl context = this.context; // local snapshot
        checkContext(context);
        context.associate(scenario);
    }

    private boolean isRunning() {
        WeldContainer container = this.container; // local snapshot
        return container != null && container.isRunning();
    }

    private static void checkContext(ContextImpl context) {
        if (context == null) {
            throw new IllegalStateException("@ScenarioScoped context not added yet");
        }
    }

    // -- ObjectFactory methods --

    /**
     * Instantiate glue code <strong>before</strong> scenario execution.
     * Called once per scenario.
     */
    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            try {
                Weld weld = Objects.requireNonNull(this.weldFactory.get());
                weld.addExtension(this);
                container = weld.initialize();
                closed.set(false);
                FACTORIES.add(this);
                LOGGER.log(Level.CONFIG, "Started Weld container {0}", container.getId());
            } catch (Throwable t) {
                started.set(false);
                closed.set(true);
                FACTORIES.remove(this);
                LOGGER.log(Level.SEVERE, "Unable to start Weld container");
                LOGGER.throwing(getClass().getName(), "start", t);
                throw t;
            }
        }

        ContextImpl context = this.context; // local snapshot
        checkContext(context);
        context.activate(null);
    }

    /**
     * Dispose glue code <strong>after</strong> scenario execution.
     * Called once per scenario.
     */
    @Override
    public void stop() {
        ContextImpl context = this.context; // local snapshot
        if (context != null) {
            context.deactivate();
        }

        final WeldContainer container = this.container; // local snapshot
        String id = (container == null) ? "<uninitialized>" : container.getId();

        // Clean up any unmanaged glue classes.
        for (var it = instances.entrySet().iterator(); it.hasNext(); ) {
            Unmanaged.UnmanagedInstance<?> instance = it.next().getValue();
            Object[] params = { instance, id };
            it.remove();
            try {
                instance.preDestroy();
                instance.dispose();
                LOGGER.log(Level.FINER, "Removed {0} from Weld container {1}", params);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Unable to remove {0} from Weld container {1}", params);
                LOGGER.throwing(getClass().getName(), "stop", t);
            }
        }
    }

    /**
     * Collects glue classes in the classpath.
     * Called once on init.
     *
     * @param glueClass glue class containing {@code cucumber.api} annotations ({@code Before},
     *                  {@code Given}, {@code When}, ...)
     * @return {@code true} if step definitions and hooks in this class should be used,
     *         {@code false} if they should be ignored.
     */
    @Override
    public boolean addClass(Class<?> glueClass) {
        glueClasses.add(glueClass);
        return true;
    }

    /**
     * Provides the glue instances used to execute the current scenario.
     *
     * @param <T>       the type of glue class
     * @param glueClass type of instance of be created
     * @return new glue instance of type {@code T}
     */
    @Override
    public <T> T getInstance(Class<T> glueClass) {
        Unmanaged.UnmanagedInstance<?> instance = instances.get(glueClass);
        if (instance != null) {
            return glueClass.cast(instance.get());
        }

        if (!isRunning()) {
            throw new IllegalStateException("Weld container not started");
        }

        WeldContainer container = this.container; // local snapshot

        Instance<T> selected = container.select(glueClass);
        if (!selected.isUnsatisfied()) {
            return selected.get();
        }

        // Should ideally *not* be hit for glueClasses after afterBeanDiscovery.
        // It would be hit for other classes Cucumber might request that are neither glueClasses
        // nor other pre-existing CDI beans.
        LOGGER.log(Level.WARNING,
                "Using Unmanaged instance for non-CDI type {0}",
                glueClass.getName());

        Unmanaged.UnmanagedInstance<T> unmanaged;
        unmanaged = new Unmanaged<>(container.getBeanManager(), glueClass).newInstance();
        unmanaged.produce();
        unmanaged.inject();
        unmanaged.postConstruct();
        this.instances.put(glueClass, unmanaged);
        return unmanaged.get();
    }

    // -- CDI extension callbacks --

    <T> void vetoGlueBeans(@Observes ProcessAnnotatedType<T> event, BeanManager manager) {
        AnnotatedType<T> annotatedType = event.getAnnotatedType();
        Class<T> beanClass = annotatedType.getJavaClass();

        if (!glueClasses.contains(beanClass)) {
            return;
        }

        // Check if the bean class has an explicit scope other than @ScenarioScoped. If it does, we
        // should NOT veto it.
        for (var a : annotatedType.getAnnotations()) {
            Class<? extends Annotation> klass = a.annotationType();
            if (manager.isScope(klass) && !klass.equals(ScenarioScoped.class)) {
                LOGGER.log(Level.FINER, "Preserving bean {0} with explicit scope @{1}",
                        new Object[] { beanClass, klass.getSimpleName() });
                return;
            }
        }

        // At this point, the bean is either unannotated or it has been annotated with
        // @ScenarioScoped. In any case, veto it so our afterBeanDiscovery method can register it
        // with the correct custom lifecycle.
        LOGGER.log(Level.FINER, "Vetoing auto-discovery of bean {0}", beanClass.getName());
        event.veto();
    }

    void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        event.addScope(ScenarioScoped.class, true, false);
        LOGGER.log(Level.CONFIG, "Added @ScenarioScoped scope");
    }

    void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager manager) {
        this.context = new ContextImpl(manager);
        event.addContext(this.context);
        LOGGER.log(Level.CONFIG, "Added @ScenarioScoped context");

        // Register the glue beans.
        for (var glueClass : glueClasses) {
            if (glueClass.isAnnotationPresent(Vetoed.class)) {
                LOGGER.log(Level.FINER, "Vetoing {0}", glueClass.getName());
                continue;
            }
            if (!manager.getBeans(glueClass).isEmpty()) {
                LOGGER.log(Level.FINER,
                        "Skipping registration of {0} as it is already a managed bean",
                        glueClass.getName());
                continue;
            }

            @SuppressWarnings("unchecked")
            InjectionTarget<Object> target = (InjectionTarget<Object>) manager
                    .getInjectionTargetFactory(manager.createAnnotatedType(glueClass))
                    .createInjectionTarget(null);

            BeanConfigurator<Object> bean = event.addBean();
            bean.addQualifiers(Any.Literal.INSTANCE, Default.Literal.INSTANCE);
            bean.scope(ScenarioScoped.class);
            bean.addTransitiveTypeClosure(glueClass);
            bean.beanClass(glueClass);
            bean.createWith(ctx -> {
                Object instance = target.produce(ctx);
                target.inject(instance, ctx);
                target.postConstruct(instance);
                return instance;
            });
            bean.destroyWith((instance, ctx) -> {
                target.preDestroy(instance);
                target.dispose(instance);
            });
        }
    }
}
