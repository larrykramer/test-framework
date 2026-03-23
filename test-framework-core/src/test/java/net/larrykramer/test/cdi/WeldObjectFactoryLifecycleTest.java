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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.cucumber.java.Scenario;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Vetoed;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class WeldObjectFactoryLifecycleTest {
    private WeldObjectFactory factory;

    @Before
    public void setUp() {
        ApplicationScopedBean.reset();
        ScenarioScopedBean.reset();
        UnmanagedBean.reset();
        FaultyUnmanagedBean.reset();
        ScenarioEventTracker.reset();

        factory = new WeldObjectFactory();
    }

    @After
    public void tearDown() {
        if (factory != null) {
            factory.stop();
            factory.shutdownWeldContainer();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testConstructor_whenAnotherInstanceIsActive_throwsIllegalStateException() {
        // The @Before method has already created an active 'factory' instance.
        // The static SELF field now holds a reference to it.
        new WeldObjectFactory();
    }

    @Test
    public void testGetInstance_givenFactoryConstructed_returnsSingletonReference() {
        assertSame(factory, WeldObjectFactory.getInstance());
    }

    @Test
    public void testShutdownWeldContainer_whenContainerIsRunning_stopsAndPreventsReuse() {
        factory.start();

        factory.stop();
        factory.shutdownWeldContainer();

        var expected = IllegalStateException.class;
        var e = assertThrows(expected, () -> factory.getInstance(ApplicationScopedBean.class));
        assertTrue(e.getMessage().contains("not started"));
    }

    @Test
    public void testAssociate_withActiveContext_firesInitializedEventOnce() {
        Scenario mockScenario = mock(Scenario.class);
        factory.start();
        resolveInstance(ScenarioEventTracker.class);

        factory.associate(mockScenario);
        factory.associate(mockScenario);

        assertEquals(1, ScenarioEventTracker.getInitializedCount());
        assertEquals(0, ScenarioEventTracker.getDestroyedCount());
    }

    @Test(expected = IllegalStateException.class)
    public void testAssociate_withoutActiveContext_throwsIllegalStateException() {
        factory.associate(mock(Scenario.class));
    }

    @Test
    public void testStart_whenContainerNotStarted_initializesContainerAndActivatesContext() {
        factory.addClass(ScenarioScopedBean.class);

        factory.start();

        ScenarioScopedBean instance = resolveInstance(ScenarioScopedBean.class);
        assertNotNull(instance);
        assertEquals(1, ScenarioScopedBean.getCreatedCount());
        assertEquals(0, ScenarioScopedBean.getDestroyedCount());
    }

    @Test(expected = NullPointerException.class)
    public void testStart_whenWeldInitializationFails_rethrowsException() {
        // Create a factory with a supplier that is guaranteed to fail by returning null. This will
        // cause Objects.requireNonNull() to throw a NullPointerException. We assign it to the
        // class-level factory so the @After tearDown method can clean it up.
        if (factory != null) {
            factory.shutdownWeldContainer();
        }
        this.factory = new WeldObjectFactory(() -> null);
        this.factory.start();
    }

    @Test
    public void testStop_withActiveScenario_firesDestroyedEvent() {
        factory.start();
        resolveInstance(ScenarioEventTracker.class);
        factory.associate(mock(Scenario.class));

        factory.stop();

        assertEquals(1, ScenarioEventTracker.getInitializedCount());
        assertEquals(1, ScenarioEventTracker.getDestroyedCount());
    }

    @Test
    public void testStop_whenUnmanagedPreDestroyThrows_continuesCleanupAndDoesNotThrow() {
        factory.start();
        factory.getInstance(UnmanagedBean.class);
        factory.getInstance(FaultyUnmanagedBean.class);

        // The stop() method should catch the exception from FaultyUnmanagedBean and
        // continue.
        factory.stop();

        assertEquals(1, UnmanagedBean.getDestroyedCount());
        assertTrue(FaultyUnmanagedBean.wasPreDestroyAttempted());
    }

    @Test(expected = ContextNotActiveException.class)
    public void testStop_whenScenarioScopeDeactivated_blocksScenarioScopedLookup() {
        factory.addClass(ScenarioScopedBean.class);
        factory.start();
        factory.associate(mock(Scenario.class));

        resolveInstance(ScenarioScopedBean.class);

        // Deactivate the context.
        factory.stop();

        // Attempting to get AND USE the instance should fail.
        factory.getInstance(ScenarioScopedBean.class).toString();
    }

    @Test
    public void testAddClass_addGlueClass_returnsTrue() {
        assertTrue(factory.addClass(ScenarioScopedBean.class));
    }

    @Test
    public void testGetInstance_withScenarioScopedBean_returnsNewInstancePerScenario() {
        /*
         * This test verifies that a new @ScenarioScoped bean instance is created for each distinct
         * scenario lifecycle (start -> stop -> start -> stop).
         *
         * IMPORTANT: Understanding CDI Client Proxy Behavior
         * The variables first and second do not hold direct references to the ScenarioScopedBean
         * instances. They hold references to a CDI "client proxy". This proxy is a lightweight,
         * stateless object that delegates any method call to the actual bean instance residing in
         * the *currently active* context.
         *
         * Because of this "live" delegation, we must capture the state of each bean (like its
         * instance ID) into a local variable while its corresponding context is active.
         *
         * A direct comparison in the final assertion like:
         *
         *   assertNotEquals(first.getInstanceId(), second.getInstanceId()); // <-- THIS WOULD FAIL
         *
         * would fail because, at the time of assertion, the second context is active, so *both*
         * calls would be routed to the second bean instance, yielding the same ID.
         *
         * The correct pattern is to capture the state into firstId and secondId at the appropriate
         * time, and then compare those captured, historical values.
         */
        // Arrange
        factory.addClass(ScenarioScopedBean.class);
        factory.start();
        factory.associate(mock(Scenario.class));

        ScenarioScopedBean first = factory.getInstance(ScenarioScopedBean.class);
        int firstId = first.getInstanceId();

        factory.stop();

        // Act
        factory.start();
        factory.associate(mock(Scenario.class));

        ScenarioScopedBean second = factory.getInstance(ScenarioScopedBean.class);
        int secondId = second.getInstanceId();

        // Assert
        assertTrue(firstId > 0);
        assertTrue(secondId > 0);
        assertEquals(2, ScenarioScopedBean.getCreatedCount());
        assertEquals(1, ScenarioScopedBean.getDestroyedCount());
    }

    @Test
    public void testGetInstance_withApplicationScopedBean_returnsSingleInstance() {
        factory.start();

        ApplicationScopedBean first = resolveInstance(ApplicationScopedBean.class);
        ApplicationScopedBean second = resolveInstance(ApplicationScopedBean.class);

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(1, ApplicationScopedBean.getCreatedCount());
    }

    @Test
    public void testGetInstance_withUnsatisfiedClass_returnsCachedUnmanagedInstance() {
        // Arrange
        factory.start();

        // Act (Scenario 1)
        UnmanagedBean first = factory.getInstance(UnmanagedBean.class);
        UnmanagedBean second = factory.getInstance(UnmanagedBean.class);
        factory.stop();
        // Act (Scenario 2)
        factory.start();
        UnmanagedBean third = factory.getInstance(UnmanagedBean.class);

        // Assert
        assertNotNull(first);
        assertSame(first, second);
        assertNotNull(third);
        assertNotSame(first, third);
        assertEquals(2, UnmanagedBean.getCreatedCount());
        assertEquals(1, UnmanagedBean.getDestroyedCount());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetInstance_whenCalledBeforeStart_throwsIllegalStateException() {
        factory.getInstance(ApplicationScopedBean.class);
    }

    private <T> T resolveInstance(Class<T> beanClass) {
        T instance = factory.getInstance(beanClass);
        assertNotNull(instance);

        // Call a method to force proxy initialization for normal-scoped beans.
        //noinspection ResultOfMethodCallIgnored
        instance.toString();

        return instance;
    }

    @ApplicationScoped
    public static class ApplicationScopedBean {
        private static final AtomicInteger created = new AtomicInteger();

        public ApplicationScopedBean() {
            created.incrementAndGet();
        }

        public static int getCreatedCount() {
            return created.get();
        }

        static void reset() {
            created.set(0);
        }
    }

    @ScenarioScoped
    public static class ScenarioScopedBean {
        private static final AtomicInteger created = new AtomicInteger();
        private static final AtomicInteger destroyed = new AtomicInteger();
        private final int instanceId;

        public ScenarioScopedBean() {
            this.instanceId = created.incrementAndGet();
        }

        @PreDestroy
        public void destroy() {
            destroyed.incrementAndGet();
        }

        public int getInstanceId() {
            return instanceId;
        }

        public static int getCreatedCount() {
            return created.get();
        }

        public static int getDestroyedCount() {
            return destroyed.get();
        }

        static void reset() {
            created.set(0);
            destroyed.set(0);
        }
    }

    /*
     * A plain old Java object used to test the factory's fallback mechanism for non-CDI beans.
     * It is annotated with @Vetoed and has a private constructor to ensure it is never discovered
     * or managed by the CDI container, forcing the test to exercise the "unmanaged instance"
     * code path.
     */
    @Vetoed
    public static class UnmanagedBean {
        private static final AtomicInteger created = new AtomicInteger();
        private static final AtomicInteger destroyed = new AtomicInteger();

        // Private constructor prevents instantiation by CDI, acting as a fail-safe if @Vetoed
        // were ever accidentally removed.
        private UnmanagedBean() {
            created.incrementAndGet();
        }

        @PreDestroy
        public void destroy() {
            destroyed.incrementAndGet();
        }

        public static int getCreatedCount() {
            return created.get();
        }

        public static int getDestroyedCount() {
            return destroyed.get();
        }

        static void reset() {
            created.set(0);
            destroyed.set(0);
        }
    }

    @Vetoed
    public static class FaultyUnmanagedBean {
        private static final AtomicBoolean preDestroyedAttempted = new AtomicBoolean(false);

        private FaultyUnmanagedBean() {
        }

        @PreDestroy
        public void destroy() {
            preDestroyedAttempted.set(true);
            throw new RuntimeException("Simulated @PreDestroy failure");
        }

        public static boolean wasPreDestroyAttempted() {
            return preDestroyedAttempted.get();
        }

        static void reset() {
            preDestroyedAttempted.set(false);
        }
    }

    @ApplicationScoped
    public static class ScenarioEventTracker {
        private static final AtomicInteger initializedCount = new AtomicInteger();
        private static final AtomicInteger destroyedCount = new AtomicInteger();

        public void initScenario(@Observes @Initialized(ScenarioScoped.class) Scenario scenario) {
            initializedCount.incrementAndGet();
        }

        public void endScenario(@Observes @Destroyed(ScenarioScoped.class) Scenario scenario) {
            destroyedCount.incrementAndGet();
        }

        public static int getInitializedCount() {
            return initializedCount.get();
        }

        public static int getDestroyedCount() {
            return destroyedCount.get();
        }

        static void reset() {
            initializedCount.set(0);
            destroyedCount.set(0);
        }
    }
}
