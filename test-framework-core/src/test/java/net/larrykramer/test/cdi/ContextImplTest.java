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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import io.cucumber.java.Scenario;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ContextImplTest {
    @Mock
    private BeanManager mockBeanManager;

    @Mock
    private Event<Object> mockRootEvent;
    @Mock
    private Event<Object> mockInitializedEvent;
    @Mock
    private Event<Object> mockDestroyedEvent;

    @Mock(name = "Scenario 1")
    private Scenario mockScenario;
    @Mock(name = "Scenario 2")
    private Scenario mockAnotherScenario;

    private ContextImpl context;

    @Before
    public void setUp() {
        Annotation initializedLiteral = Initialized.Literal.of(ScenarioScoped.class);
        Annotation destroyedLiteral = Destroyed.Literal.of(ScenarioScoped.class);

        lenient().when(mockBeanManager.getEvent()).thenReturn(mockRootEvent);
        lenient().when(mockRootEvent.select(initializedLiteral)).thenReturn(mockInitializedEvent);
        lenient().when(mockRootEvent.select(destroyedLiteral)).thenReturn(mockDestroyedEvent);

        context = new ContextImpl(mockBeanManager);
    }

    @Test
    public void testDestroy_existingInstance_invokesContextualDestroy() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        String instance = "stored-instance";
        when(mockContextual.create(mockCreationalContext)).thenReturn(instance);

        context.activate(mockScenario);
        context.get(mockContextual, mockCreationalContext);

        // Act
        context.destroy(mockContextual);

        // Assert
        verify(mockContextual).destroy(instance, mockCreationalContext);
        assertNull(context.get(mockContextual));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDestroy_missingInstance_doesNotInvokeContextualDestroy() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        context.activate(mockScenario);
        // Act
        context.destroy(mockContextual);
        // Assert
        verify(mockContextual, never()).destroy(anyString(), any(CreationalContext.class));
    }

    @Test(expected = ContextNotActiveException.class)
    public void testDestroy_withoutActiveContext_throwsContextNotActiveException() {
        context.destroy(createMockContextual());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDestroy_withNullContext_throwsIllegalArgumentException() {
        context.destroy(null);
    }

    @Test
    public void testGetScope_noState_returnsScenarioScopedClass() {
        assertEquals(ScenarioScoped.class, context.getScope());
    }

    @Test
    public void testGet_withCreationalContext_createsAndStoresInstance() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        String instance = "created-instance";
        when(mockContextual.create(mockCreationalContext)).thenReturn(instance);
        context.activate(mockScenario);

        // Act
        String result = context.get(mockContextual, mockCreationalContext);

        // Assert
        assertEquals(instance, result);
        verify(mockContextual).create(mockCreationalContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGet_existingContextual_returnsCachedInstance() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        String instance = "cached-instance";
        when(mockContextual.create(mockCreationalContext)).thenReturn(instance);

        context.activate(mockScenario);
        context.get(mockContextual, mockCreationalContext);

        // Act
        String result = context.get(mockContextual);

        // Assert
        assertEquals(instance, result);
        verify(mockContextual, times(1)).create(any(CreationalContext.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGet_withoutCreationalContextAndMissingBean_returnsNull() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        context.activate(mockScenario);
        // Act & Assert
        assertNull(context.get(mockContextual));
        verify(mockContextual, never()).create(any(CreationalContext.class));
    }

    @Test
    public void testGet_contextualCreateReturnsNull_returnsNullAndDoesNotStore() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        when(mockContextual.create(mockCreationalContext)).thenReturn(null);

        context.activate(mockScenario);

        // Act
        String firstResult = context.get(mockContextual, mockCreationalContext);
        String secondResult = context.get(mockContextual);

        // Assert
        assertNull(firstResult);
        assertNull(secondResult);
        verify(mockContextual, times(1)).create(mockCreationalContext);
    }

    @Test(expected = ContextNotActiveException.class)
    public void testGet_withoutActiveContext_throwsContextNotActiveException() {
        context.get(createMockContextual());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGet_withNullContext_throwsIllegalArgumentException() {
        // Arrange
        CreationalContext<Object> mockCreationalContext = createMockCreationalContext();
        context.activate(mockScenario);
        // Act
        context.get(null, mockCreationalContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGet_threadIsolation_preventsCrossThreadVisibility() throws Exception {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        when(mockContextual.create(any(CreationalContext.class)))
                .thenAnswer(invocation -> Thread.currentThread().getName() + "-bean");

        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch doneSignal = new CountDownLatch(2);
        AtomicReference<String> threadAStoredValue = new AtomicReference<>();
        AtomicReference<String> threadBInitialLookup = new AtomicReference<>();
        AtomicReference<String> threadBStoredValue = new AtomicReference<>();
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try {
                context.activate(null);
                threadAStoredValue.set(context.get(mockContextual, createMockCreationalContext()));
                barrier.await(); // release thread B to do its lookup
                barrier.await(); // wait until B is finished
            } catch (Throwable t) {
                threadFailure.compareAndSet(null, t);
            } finally {
                doneSignal.countDown();
                context.deactivate();
            }
        }, "thread-A");

        Thread threadB = new Thread(() -> {
            try {
                barrier.await(); // wait for A to have stored its bean
                context.activate(null);
                threadBInitialLookup.set(context.get(mockContextual));
                threadBStoredValue.set(context.get(mockContextual, createMockCreationalContext()));
                barrier.await(); // signal to A that B is done
            } catch (Throwable t) {
                threadFailure.compareAndSet(null, t);
            } finally {
                doneSignal.countDown();
                context.deactivate();
            }
        }, "thread-B");

        // Act
        threadA.start();
        threadB.start();
        assertTrue("Threads did not complete in time", doneSignal.await(2, TimeUnit.SECONDS));
        threadA.join(100);
        threadB.join(100);

        Throwable t = threadFailure.get();
        if (t != null) {
            throw new AssertionError("Thread execution failed", t);
        }

        // Assert
        assertEquals("thread-A-bean", threadAStoredValue.get());
        assertNull(threadBInitialLookup.get());
        assertEquals("thread-B-bean", threadBStoredValue.get());
        assertNotEquals(threadAStoredValue.get(), threadBStoredValue.get());
    }

    @Test
    public void testIsActive_withoutActivation_returnsFalse() {
        assertFalse(context.isActive());
    }

    @Test
    public void testIsActive_afterActivation_returnsTrue() {
        context.activate(mockScenario);
        assertTrue(context.isActive());
    }

    @Test
    public void testAssociate_withoutActiveContext_activatesAndFiresInitializedEvent() {
        // Act
        context.associate(mockScenario);
        // Assert
        assertTrue(context.isActive());
        verify(mockInitializedEvent).fire(mockScenario);
    }

    @Test
    public void testAssociate_withSameScenario_doesNotRefireInitializedEvent() {
        context.activate(mockScenario);
        context.associate(mockScenario);
        verify(mockInitializedEvent, times(1)).fire(any(Scenario.class));
    }

    @Test
    public void testAssociate_withDifferentScenario_firesInitializedEventForNewScenario() {
        // Arrange
        context.activate(mockScenario);

        // Act
        context.associate(mockAnotherScenario);

        // Assert
        // The initialization event is fired in order for each scenario.
        var inOrder = inOrder(mockInitializedEvent);
        inOrder.verify(mockInitializedEvent).fire(mockScenario);
        inOrder.verify(mockInitializedEvent).fire(mockAnotherScenario);
        assertTrue(context.isActive());
    }

    @Test(expected = NullPointerException.class)
    public void testAssociate_withNullScenario_throwsNullPointerException() {
        context.associate(null);
    }

    @Test
    public void testActivate_withNullScenario_firesInitializedEvent() {
        context.activate(null);
        verify(mockInitializedEvent, never()).fire(any(Scenario.class));
    }

    @Test
    public void testActivate_whenContextAlreadyActive_replacesPreviousContext() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        String instance = "first-instance";
        when(mockContextual.create(mockCreationalContext)).thenReturn(instance);

        context.activate(mockScenario);
        context.get(mockContextual, mockCreationalContext);

        // Act
        context.activate(mockAnotherScenario);

        // Assert
        // Verify the sequence: destroy instance, fire destroyed event, fire initialized event
        var inOrder = inOrder(mockContextual, mockDestroyedEvent, mockInitializedEvent);
        inOrder.verify(mockContextual).destroy(instance, mockCreationalContext);
        inOrder.verify(mockDestroyedEvent).fire(mockScenario);
        inOrder.verify(mockInitializedEvent).fire(mockAnotherScenario);
        assertTrue(context.isActive());
    }

    @Test
    public void testDeactivate_withActiveContext_destroysInstancesAndFiresDestroyedEvent() {
        // Arrange
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        String instance = "deactivate-instance";
        when(mockContextual.create(mockCreationalContext)).thenReturn(instance);

        context.activate(mockScenario);
        context.get(mockContextual, mockCreationalContext);

        // Act
        context.deactivate();

        // Assert
        assertFalse(context.isActive());
        verify(mockContextual).destroy(instance, mockCreationalContext);
        verify(mockDestroyedEvent, times(1)).fire(mockScenario);
    }

    @Test
    public void testDeactivate_withNullScenario_doesNotFireDestroyedEvent() {
        // Arrange
        context.activate(null);
        // Act
        context.deactivate();
        // Assert
        assertFalse(context.isActive());
        verify(mockDestroyedEvent, never()).fire(any(Scenario.class));
    }

    @Test
    public void testDeactivate_withoutActiveContext_doesNothing() {
        // Act
        context.deactivate();
        // Assert
        assertFalse(context.isActive());
        verify(mockBeanManager, never()).getEvent();
    }

    @Test
    public void testDeactivate_instanceDestroyThrows_continuesToDestroyOtherInstances() {
        // Arrange
        // Create two distinct beans to manage. The first bean should behave normally and the
        // second "faulty" bean's destroy method should throw an exception.
        Contextual<String> mockContextual = createMockContextual();
        CreationalContext<String> mockCreationalContext = createMockCreationalContext();
        String instance = "destroyed-instance";
        when(mockContextual.create(mockCreationalContext)).thenReturn(instance);

        Contextual<String> mockFaultyContextual = createMockContextual();
        CreationalContext<String> mockFaultyCreationalContext = createMockCreationalContext();
        String faultyInstance = "faulty-instance";
        when(mockFaultyContextual.create(mockFaultyCreationalContext)).thenReturn(faultyInstance);
        doThrow(new RuntimeException("Simulate @PreDestroy failure"))
                .when(mockFaultyContextual).destroy(faultyInstance, mockFaultyCreationalContext);

        context.activate(mockScenario);
        context.get(mockFaultyContextual, mockFaultyCreationalContext);
        context.get(mockContextual, mockCreationalContext);

        // Act
        // This should not throw an exception.
        context.deactivate();

        // Assert
        verify(mockContextual).destroy(instance, mockCreationalContext);
        verify(mockFaultyContextual).destroy(faultyInstance, mockFaultyCreationalContext);
    }

    private <T> Contextual<T> createMockContextual() {
        @SuppressWarnings("unchecked")
        Contextual<T> contextual = (Contextual<T>) mock(Contextual.class);
        return contextual;
    }

    private <T> CreationalContext<T> createMockCreationalContext() {
        @SuppressWarnings("unchecked")
        CreationalContext<T> creationalContext
                = (CreationalContext<T>) mock(CreationalContext.class);
        return creationalContext;
    }
}
