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

package net.larrykramer.test.service;

import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import net.larrykramer.test.cdi.WeldObjectFactory;
import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.config.DriverType;
import net.larrykramer.test.config.GridConfig;
import net.larrykramer.test.webdriver.DriverFactory;
import net.larrykramer.test.webdriver.WebDriverReference;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.proxy.WeldClientProxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebDriverServiceCDITest {
    protected WeldContainer container = null;

    @Before
    public void setUp() {
        Weld weld = WeldObjectFactory.createDefaultWeld();
        weld.disableDiscovery();
        weld.addBeanClasses(WebDriverService.class, ConfigProducer.class, SPIDriverFactory.class);
        // Explicitly enable the ConfigProducer alternative.
        // This ensures the container prefers our test configuration over the default
        // SmallRye Config beans.
        weld.addAlternative(ConfigProducer.class);

        this.container = weld.initialize();
    }

    @After
    public void tearDown() {
        if (container != null && container.isRunning()) {
            container.shutdown();
        }
    }

    @Test
    public void testCreateWebDriver_givenFactoryIsCDIProxy_resolvesFactoryAndReturnsDriverRef() {
        // Arrange
        // Precondition: Ensure CDI gave us a proxy, not the raw factory.
        DriverFactory<?> factory = container.select(SPIDriverFactory.class).get();
        assertTrue("Expected Weld client proxy but got: "
                        + ((factory == null) ? "null" : factory.getClass().getName()),
                factory instanceof WeldClientProxy);

        WebDriverService service = container.select(WebDriverService.class).get();

        // Act
        // Attempt to retrieve the driver factory.
        // This will fail if the map was keyed by the proxy class but the lookup uses the raw
        // class name.
        WebDriverReference driverRef = service.createWebDriver();

        // Assert
        assertNotNull(driverRef);
        assertNotNull(driverRef.get());
    }

    /*
     * This class provides mock configuration beans to replace the default SmallRye Config
     * implementation during this specific unit test.
     *
     * IMPORTANT: Resolving Ambiguous Dependencies with Isolated Alternatives
     * The `DriverConfig` and `GridConfig` beans are normally provided automatically by the
     * SmallRye Config library. Since this test defines producers for the same types, CDI
     * detects an ambiguity (two sources for the same bean type).
     *
     * To resolve this, we mark these producers with `@Alternative`. However, we intentionally
     * do NOT annotate this class with `@Priority`.
     *
     * 1. If we used `@Priority`, this alternative would become globally active for the entire test
     *    classpath. This would accidentally override the real configuration in other integration
     *    tests, causing them to fail.
     * 2. By omitting `@Priority`, this alternative remains "disabled by default." It is invisible
     *    to other tests.
     * 3. We then explicitly activate this alternative ONLY for this test's container instance by
     *    calling `weld.addAlternative(ConfigProducer.class)` in the `setUp()` method.
     */
    @ApplicationScoped
    public static class ConfigProducer {
        @Produces
        @ConfigProperties
        @Alternative
        public DriverConfig createConfig() {
            DriverConfig config = new DriverConfig();

            config.type = DriverType.SPI;
            config.spi = Optional.of(SPIDriverFactory.class.getName());

            config.headless = false;

            config.maximize = false;
            config.windowSize = Optional.empty();

            config.implicitTimeout = 0L;

            config.allowInsecureCerts = false;

            config.proxyAddress = Optional.empty();
            config.proxyUser = Optional.empty();
            config.proxyPassword = Optional.empty();
            config.nonProxyHosts = Optional.empty();

            return config;
        }

        @Produces
        @ConfigProperties
        @Alternative
        public GridConfig createGridConfig() {
            GridConfig grid = new GridConfig();

            grid.uri = Optional.empty();

            grid.browserVersion = Optional.empty();
            grid.platform = Optional.empty();
            grid.applicationName = Optional.empty();

            grid.capabilities = Map.of();

            return grid;
        }
    }

    @ApplicationScoped
    public static class SPIDriverFactory extends DriverFactory<MutableCapabilities> {
        @Override
        public DriverType getDriverType() {
            return DriverType.SPI;
        }

        @Override
        public WebDriver create() {
            // Return a mock to avoid spinning up a real browser during tests.
            return Mockito.mock(WebDriver.class);
        }

        @Override
        public void configure(WebDriver driver) {
            // No-op
        }

        @Override
        public MutableCapabilities getCapabilities() {
            return new MutableCapabilities();
        }
    }
}
