/*
 * Copyright (c) 2026 Larry Kramer
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

import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import io.smallrye.config.inject.ConfigExtension;
import net.larrykramer.test.cdi.WeldObjectFactory;
import net.larrykramer.test.config.ChromiumConfig;
import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.util.ScopedSystemProperties;
import net.larrykramer.test.webdriver.browser.ChromeDriverFactory;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.selenium.chrome.ChromeOptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class DriverFactoryCDITest {
    public static abstract class DriverFactoryCDITestBase {
        protected WeldContainer container;

        @After
        public void tearDown() {
            if (container != null && container.isRunning()) {
                container.shutdown();
            }
        }

        protected void initializeWeldContainer() {
            Weld weld = WeldObjectFactory.createDefaultWeld();
            weld.disableDiscovery();

            weld.addExtension(new ConfigExtension());

            weld.addPackage(true, DriverFactory.class);
            weld.addPackage(true, DriverConfig.class);

            this.container = weld.initialize();
        }
    }

    @RunWith(Parameterized.class)
    public static class DriverFactoryRegistrationTest extends DriverFactoryCDITestBase {
        private final Class<?> factoryClass;

        @Parameterized.Parameters(name = "{0}")
        public static Iterable<Object[]> data() throws Exception {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = DriverFactoryRegistrationTest.class.getClassLoader();
                if (cl == null) {
                    cl = ClassLoader.getSystemClassLoader();
                }
            }

            URL url = DriverFactory.class.getProtectionDomain().getCodeSource().getLocation();
            String mainRoot = (url != null) ? url.toString() : null;
            String packageName = DriverFactory.class.getPackageName();

            Set<Class<?>> classes = new HashSet<>();

            final ClassLoader loader = cl;
            Enumeration<URL> roots = loader.getResources(packageName.replace('.', '/'));
            while (roots.hasMoreElements()) {
                url = roots.nextElement();
                if (!"file".equals(url.getProtocol())
                        || (mainRoot != null && !url.toString().startsWith(mainRoot))) {
                    continue;
                }

                Path rootDir = Path.of(url.toURI());
                try (var entries = Files.walk(rootDir)) {
                    entries.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".class"))
                            .map(file -> toClassName(file, rootDir, packageName))
                            .map(name -> toClass(name, loader))
                            .filter(Objects::nonNull)
                            .forEach(classes::add);
                }
            }

            if (classes.isEmpty()) {
                throw new AssertionError(
                        "No DriverFactory implementations discovered under " + packageName);
            }

            return classes.stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .map(c -> new Object[] {
                            c.getSimpleName().isBlank() ? c.getName() : c.getSimpleName(), c
                    })
                    .toList();
        }

        private static String toClassName(Path file, Path rootDir, String packageName) {
            String name = rootDir.relativize(file).toString();
            name = name.substring(0, name.length() - ".class".length());
            name = name.replace('\\', '.').replace('/', '.');
            return packageName + "." + name;
        }

        private static Class<?> toClass(String name, ClassLoader loader) {
            try {
                Class<?> clazz = Class.forName(name, false, loader);
                if (DriverFactory.class.isAssignableFrom(clazz)
                        && !Modifier.isAbstract(clazz.getModifiers())) {
                    return clazz;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // Skip classes that can't be loaded in the current runtime.
            }
            return null;
        }

        public DriverFactoryRegistrationTest(@SuppressWarnings("unused") String className,
                Class<?> factoryClass) {
            this.factoryClass = factoryClass;
        }

        @Test
        public void testFactoryResolution_givenWeldContainer_shouldResolveProxyableFactory() {
            // Arrange
            initializeWeldContainer();
            // Act & Assert
            // If the class was 'final', Weld will throw UnproxyableResolutionException here.
            assertNotNull(container.select(factoryClass).get());
        }
    }

    /**
     * Verifies that the MicroProfile Config prefix override mechanism functions
     * correctly within the CDI container.
     * <p>
     * Specifically, it ensures that {@link ChromeDriverFactory} receives
     * configuration from {@code driver.chrome.*} (via the injection point
     * override) rather than the default {@code driver.chromium.*} defined on
     * the {@link ChromiumConfig} class.
     * <p>
     * This test is located here rather than in {@code ChromeDriverFactoryTest}
     * because the latter is a unit test that manually injects configuration
     * objects, bypassing the CDI container's configuration resolution logic.
     */
    public static class ChromiumConfigMappingTest extends DriverFactoryCDITestBase {
        @Test
        public void testGetCapabilities_givenChromeSpecificConfig_shouldOverrideChromiumDefaults() {
            // Arrange
            final String expectedExecutable = "/usr/bin/google-chrome-stable";
            try (var env = ScopedSystemProperties.open()) {
                env.setProperty("driver.type", "CHROME");

                env.setProperty("driver.chrome.executable", expectedExecutable);
                env.setProperty("driver.chrome.arguments", "--headless=new,--disable-gpu");

                env.setProperty("driver.chromium.executable", "/usr/bin/chromium-browser");

                initializeWeldContainer();

                ChromeDriverFactory factory = container.select(ChromeDriverFactory.class).get();

                // Act
                ChromeOptions options = factory.getCapabilities();

                // Assert
                assertEquals(expectedExecutable, extractChromeOptions(options).get("binary"));

                List<String> args = extractArguments(options);
                assertEquals(2, args.size());
                assertTrue(args.contains("--headless=new"));
                assertTrue(args.contains("--disable-gpu"));
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> extractChromeOptions(ChromeOptions options) {
            Object raw = options.asMap().get("goog:chromeOptions");
            return (raw instanceof Map) ? (Map<String, Object>) raw : Map.of();
        }

        private List<String> extractArguments(ChromeOptions options) {
            Object args = extractChromeOptions(options).get("args");
            if (args instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) args;
                return list;
            }
            return List.of();
        }
    }
}

