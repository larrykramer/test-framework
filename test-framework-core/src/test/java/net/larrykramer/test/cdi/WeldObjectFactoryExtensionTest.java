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
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.jboss.weld.bean.ManagedBean;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Enclosed.class)
public class WeldObjectFactoryExtensionTest {
    public abstract static class ExtensionTestBase {
        protected WeldObjectFactory factory;

        protected WeldContainer container;
        protected BeanManager manager;

        @Before
        public void setUp() {
            // The factory is created with a dummy supplier as the test will manage the Weld
            // instance directly. The supplier should not be called in these tests. If it is called,
            // something is wrong with the test setup.
            factory = new WeldObjectFactory(() -> {
                fail("The default Weld supplier should not be invoked in extension tests.");
                return null;
            });
        }

        @After
        public void tearDown() {
            if (factory != null) {
                factory.shutdownWeldContainer();
            }
            if (container != null && container.isRunning()) {
                container.shutdown();
            }
        }

        protected void initializeWeldContainer(Class<?>... beanClasses) {
            Weld weld = WeldObjectFactory.createDefaultWeld();
            weld.disableDiscovery();
            weld.addExtension(factory);
            weld.addBeanClasses(beanClasses);

            this.container = weld.initialize();
            this.manager = container.getBeanManager();
        }

        @ScenarioScoped
        public static class ScenarioScopedBean {
        }
    }

    @RunWith(Parameterized.class)
    public static class BeanRegistrationTest extends ExtensionTestBase {
        private final Class<?> beanClass;
        private final Class<? extends Annotation> expectedScope;

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Object[]> data() {
            return Arrays.asList(new Object[][] {
                    { "Unannotated", UnannotatedBean.class, ScenarioScoped.class },
                    { "Non-Scope Annotation", DeprecatedBean.class, ScenarioScoped.class },
                    { "@ScenarioScoped", ScenarioScopedBean.class, ScenarioScoped.class },
                    { "@ApplicationScoped", ApplicationScopedBean.class, ApplicationScoped.class },
                    { "@RequestScoped", RequestScopedBean.class, RequestScoped.class },
                    { "@Dependent", DependentScopedBean.class, Dependent.class }
            });
        }

        public BeanRegistrationTest(@SuppressWarnings("unused") String testDescription,
                Class<?> beanClass,
                Class<? extends Annotation> expectedScope) {
            this.beanClass = beanClass;
            this.expectedScope = expectedScope;
        }

        @Test
        public void testVetoGlueBeans_glueBean_registersWithCorrectScope() {
            factory.addClass(beanClass);

            initializeWeldContainer(beanClass);

            Set<Bean<?>> beans = manager.getBeans(beanClass);
            assertNotNull(beans);
            assertEquals(1, beans.size());
            assertEquals(expectedScope, beans.iterator().next().getScope());
        }

        // -- Bean Definitions --

        @ApplicationScoped
        public static class ApplicationScopedBean {
        }

        @RequestScoped
        public static class RequestScopedBean {
        }

        @Dependent
        public static class DependentScopedBean {
        }

        public static class UnannotatedBean {
        }

        @Deprecated
        public static class DeprecatedBean {
        }
    }

    public static class ExtensionSideEffectsTest extends ExtensionTestBase {
        @Test
        public void testVetoGlueBeans_nonGlueBean_keepsManagedBean() {
            // ScenarioScopedBean is NOT added via factory.addClass(), so it's not "glue". The veto
            // observer should ignore it, and Weld should discover it normally.

            initializeWeldContainer(ScenarioScopedBean.class);

            Set<Bean<?>> beans = manager.getBeans(ScenarioScopedBean.class);
            assertEquals(1, beans.size());

            Bean<?> bean = beans.iterator().next();
            assertEquals(ScenarioScoped.class, bean.getScope());
            assertTrue(bean instanceof ManagedBean);
        }

        @Test
        public void testVetoGlueBeans_vetoedBean_isNotRegistered() {
            factory.addClass(VetoedBean.class);

            initializeWeldContainer(VetoedBean.class);

            Set<Bean<?>> beans = manager.getBeans(VetoedBean.class);
            assertTrue(beans.isEmpty());
        }

        @Test
        public void testBeforeBeanDiscovery_extensionLoaded_registersScenarioScope() {
            // The factory is added as an extension, which will trigger the beanDiscovery event.
            initializeWeldContainer();

            assertTrue(manager.isScope(ScenarioScoped.class));
            assertTrue(manager.isNormalScope(ScenarioScoped.class));
            assertFalse(manager.isPassivatingScope(ScenarioScoped.class));
        }

        @Test
        public void testAfterBeanDiscovery_extensionLoaded_registersScenarioScopeContext() {
            // The factory is added as an extension, which will trigger the beanDiscovery event.
            initializeWeldContainer();

            // We are testing REGISTRATION, not ACTIVATION.
            // The standard BeanManager.getContext() method cannot be used here, as it only returns
            // ACTIVE contexts and would throw an exception. Instead, we rely on the
            // BeanManager.getContexts() method. This method returns all registered contexts,
            // regardless of their activation state. This allows us to correctly verify that our
            // extension's afterBeanDiscovery observer has successfully registered the ContextImpl.
            Collection<Context> contexts = manager.getContexts(ScenarioScoped.class);
            assertNotNull(contexts);
            assertEquals(1, contexts.size());
            assertTrue(contexts.iterator().next() instanceof ContextImpl);
        }

        // -- Bean Definitions --

        @Vetoed
        public static class VetoedBean {
        }
    }
}
