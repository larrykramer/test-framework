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

import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import net.larrykramer.test.cdi.ScenarioScoped;
import net.larrykramer.test.config.DriverConfig;
import net.larrykramer.test.config.DriverType;
import net.larrykramer.test.config.GridConfig;
import net.larrykramer.test.webdriver.DriverFactory;
import net.larrykramer.test.webdriver.WebDriverReference;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebDriver;

import static net.larrykramer.test.util.SharedUtils.getUnproxiedClass;
import static net.larrykramer.test.util.SharedUtils.identityToString;
import static org.openqa.selenium.remote.CapabilityType.BROWSER_NAME;
import static org.openqa.selenium.remote.CapabilityType.BROWSER_VERSION;
import static org.openqa.selenium.remote.CapabilityType.PLATFORM_NAME;

/**
 * Provides centralized creation and lifecycle management of Selenium
 * {@code WebDriver} instances for the test suite.
 * <p>
 * {@code WebDriverService} is an {@code @ApplicationScoped} CDI bean that ties
 * together the resolved {@linkplain DriverConfig configuration} and all
 * discovered {@code DriverFactory} implementations.
 * <p>
 * The service exposes a {@code @Produces} {@code @ScenarioScoped} method that
 * creates and configures a {@code WebDriver} for the current scenario. It
 * supports both local execution and remote execution on a Selenium Grid by
 * instantiating a {@code RemoteWebDriver} with the capabilities derived from
 * the configuration.
 * <p>
 * Complementing the producer, the
 * {@linkplain #disposeWebDriver(WebDriverReference) disposer} method ensures
 * {@code WebDriver} sessions are terminated and resources are released, while
 * gracefully handling any driver-specific shutdown errors.
 */
@ApplicationScoped
public class WebDriverService {
    private static final Logger LOGGER = Logger.getLogger(WebDriverService.class.getName());

    private final DriverConfig driverConfig;
    private final GridConfig gridConfig;

    private final Map<DriverType, Map<String, DriverFactory<?>>> factories;

    /**
     * Constructs the service and indexes available {@code DriverFactory}
     * instances by {@code DriverType}.
     * <p>
     * For non-SPI driver types, the inner map contains a single factory keyed
     * by the canonical driver name returned by
     * {@link DriverType#getCanonicalName()}. If multiple factories are
     * discovered for the same non-SPI type, the previously registered factory
     * is replaced and a warning is logged.
     * <p>
     * For SPI types, multiple factories may coexist. SPI factories are resolved
     * primarily by their fully qualified implementation class name (FQCN). If
     * an SPI factory is a {@code @Named} CDI bean (including a producer
     * annotated with {@code @Named}), the CDI bean name is also registered as
     * an alias key, allowing {@code driver.spi} to match either the FQCN or the
     * {@code @Named} value.
     *
     * @param driverConfig the resolved, type-safe driver configuration
     * @param gridConfig   the resolved, type-safe grid configuration
     * @param factories    all discovered {@code DriverFactory} implementations
     */
    @Inject
    public WebDriverService(@ConfigProperties DriverConfig driverConfig,
            @ConfigProperties GridConfig gridConfig, Instance<DriverFactory<?>> factories) {
        this.driverConfig = driverConfig;
        this.gridConfig = gridConfig;

        Map<DriverType, Map<String, DriverFactory<?>>> m = new EnumMap<>(DriverType.class);
        for (var handle : factories.handles()) {
            DriverFactory<?> factory = handle.get();
            DriverType type = factory.getDriverType();
            if (type == null) {
                LOGGER.log(Level.WARNING, "DriverFactory.getDriverType() returned null for {0}",
                        getFactoryClassName(handle.getBean(), factory));
            } else if (type != DriverType.SPI) {
                if (m.containsKey(type)) {
                    Object[] params = { m.get(type).values().iterator().next(), type };
                    LOGGER.log(Level.WARNING, "Replacing factory {0} associated with {1}", params);
                }
                m.put(type, Map.of(type.getCanonicalName(), factory));
            } else {
                // Note: We don't use an unmodifiable map for the SPI case.
                // Each new SPI factory would copy the existing immutable map to a mutable map, put
                // itself into the mutable map and then create a new immutable copy. That process
                // could be resource-intensive with many SPI factories.
                Bean<?> bean = handle.getBean();
                registerSPIFactory(m.computeIfAbsent(type, k -> new HashMap<>()), bean, factory);
            }
        }
        this.factories = Collections.unmodifiableMap(m);
    }

    private void registerSPIFactory(Map<String, DriverFactory<?>> m,
            Bean<?> bean,
            DriverFactory<?> factory) {
        String name;

        if (bean != null) {
            // Try to resolve the concrete factory class name from the CDI metadata.
            // This works for managed beans and concrete producers.
            name = resolveBeanClassName(bean);
            if (name != null) {
                m.put(name, factory);
            }

            // Alias the CDI bean name if present.
            // This works for producers that return DriverFactory<?>.
            name = normalizeKey(bean.getName());
            if (name != null) {
                DriverFactory<?> prev = m.put(name, factory);
                if (prev != null && prev != factory) {
                    LOGGER.log(Level.WARNING, "Replacing SPI factory {0} associated with {1}",
                            new Object[] { prev, name });
                }
            }
        }

        // Always also register by (un)proxied class name if not already present.
        // If unproxying yields a concrete DriverFactory implementation, prefer that name.
        Class<?> unproxiedClass = getUnproxiedClass(factory.getClass());
        if (isDriverFactoryClass(unproxiedClass)) {
            name = unproxiedClass.getName();
        } else {
            // Last resort. Might be a proxy name, but avoids NPEs / empty keys.
            name = factory.getClass().getName();
            LOGGER.log(Level.FINER, "Unable to resolve DriverFactory type for {0} from bean "
                            + "types - runtime class name {1} is used",
                    new Object[] { (bean == null) ? "<unknown>" : bean, name });
        }

        m.putIfAbsent(name, factory);
    }

    private String resolveBeanClassName(Bean<?> bean) {
        // Managed bean case: bean class is the implementation class.
        Class<?> beanClass = bean.getBeanClass();
        if (isDriverFactoryClass(beanClass)) {
            return beanClass.getName();
        }

        // Producer case.
        // beanClass is producer holder: scan types for a concrete factory class.
        beanClass = null;
        for (var t : bean.getTypes()) {
            if (t instanceof Class<?> clazz
                    && isDriverFactoryClass(clazz)
                    && (beanClass == null || beanClass.isAssignableFrom(clazz))) {
                beanClass = clazz;
            }
        }

        return (beanClass != null) ? beanClass.getName() : null;
    }

    private String getFactoryClassName(Bean<?> bean, DriverFactory<?> factory) {
        String name = null;

        if (bean != null) {
            // Bean name (if present/non-blank) has priority over the bean class name, if both are
            // defined.
            name = bean.getName();
            if (name != null && !name.isBlank()) {
                name = name.strip();
            } else {
                name = resolveBeanClassName(bean); // maybe null
            }
        }

        if (name == null) {
            Class<?> clazz = getUnproxiedClass(factory.getClass());
            if (!isDriverFactoryClass(clazz)) {
                // Last resort. Might be a proxy name.
                clazz = factory.getClass();
            }
            name = clazz.getName();
        }

        return name;
    }

    private static boolean isDriverFactoryClass(Class<?> c) {
        return DriverFactory.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers());
    }

    /**
     * Produces a scenario-scoped {@code WebDriverReference} that encapsulates
     * a {@code WebDriver} created according to {@code DriverConfig} and the
     * available factories.
     * <p>
     * Resolution rules:
     * <ul>
     * <li><b>Non-SPI:</b> the factory is selected by {@code DriverType}.
     * <li><b>SPI:</b> the configuration must specify {@code driver.spi} with
     *   the SPI factory identifier. This may be the factory implementation's
     *   fully qualified class name (FQCN) or (if the factory is a
     *   {@code @Named} CDI bean or produced by a {@code @Named} producer) the
     *   CDI bean name.
     * </ul>
     *
     * If {@link GridConfig#uri} is present, a remote session is created with a
     * {@code RemoteWebDriver} and the configured capabilities; otherwise a
     * local WebDriver is created via the resolved {@code DriverFactory}. After
     * creation, the selected {@code DriverFactory} is given an opportunity to
     * perform driver-specific configuration via
     * {@link DriverFactory#configure(WebDriver)}. If configuration or logging
     * fails, this method attempts to quit the driver before rethrowing the
     * original error.
     *
     * @return a scenario-scoped {@code WebDriverReference} encapsulating an
     *         initialized {@code WebDriver} for the current scenario
     * @throws IllegalArgumentException if {@code driver.type} is not set, the
     *                                  configured driver type is unsupported,
     *                                  no matching SPI factory is found, the
     *                                  SPI factory identifier is missing when
     *                                  {@code driver.type=SPI}, or the Grid URL
     *                                  is invalid
     * @throws IllegalStateException    if the selected factory cannot create a
     *                                  WebDriver locally or indicates that
     *                                  Grid execution is not supported for the
     *                                  chosen driver type
     * @see #disposeWebDriver(WebDriverReference)
     */
    @Produces
    @ScenarioScoped
    public WebDriverReference createWebDriver() {
        if (driverConfig.type == null) {
            throw new IllegalArgumentException("Required config property not set: driver.type");
        }

        DriverFactory<?> factory = findDriverFactory();
        if (factory == null) {
            String msg;
            if (driverConfig.type == DriverType.SPI) {
                msg = "No matching SPI driver factory: ";
                msg += normalizeKey(driverConfig.spi.orElse(null));
            } else {
                msg = "Unsupported driver type: " + driverConfig.type;
            }
            throw new IllegalArgumentException(msg);
        }
        LOGGER.log(Level.FINER, "Using driver factory {0}", factory.getClass().getName());

        // Create a remote WebDriver when grid settings are present, otherwise fall back to the
        // local factory, and fail if the WebDriver cannot be constructed.
        WebDriver driver;
        if (gridConfig.uri.isPresent()) {
            MutableCapabilities capabilities = factory.getCapabilities();
            if (capabilities == null) {
                throw new IllegalStateException(
                        "Grid execution not supported for driver type: " + driverConfig.type);
            }
            driver = createRemoteWebDriver(capabilities);
        } else {
            driver = factory.create();
        }
        if (driver == null) {
            throw new IllegalStateException(factory.getClass().getName() + ": null driver created");
        }

        try {
            factory.configure(driver);
            LOGGER.log(Level.CONFIG, "Created WebDriver {0}", identityToString(driver));
            return new WebDriverReference(driver);
        } catch (Throwable t) {
            try {
                driver.quit();
            } catch (Throwable x) {
                t.addSuppressed(x);
            }
            throw t;
        }
    }

    /**
     * Disposes the scenario-scoped WebDriver produced by this service by
     * invoking {@code WebDriver.quit()}.
     * <p>
     * This method is idempotent and safe to call with {@code null} or with a
     * {@link WebDriverReference} that was never initialized. Any exceptions
     * thrown by the underlying WebDriver during quit are caught and logged.
     *
     * @param driverRef the {@code WebDriverReference} holding the WebDriver to
     *                  dispose
     * @see #createWebDriver()
     */
    public void disposeWebDriver(@Disposes WebDriverReference driverRef) {
        if (driverRef == null) {
            return;
        }

        WebDriver driver = null;
        try {
            try {
                // Use the wrapped driver instead of the unwrapped driver.
                // If a factory returns a wrapper (e.g., EventFiringWebDriver or other WrapsDriver
                // decorators), calling quit() on the underlying unwrapped driver may bypass
                // wrapper-specific cleanup.
                driver = driverRef.get();
            } catch (IllegalStateException ignored) {
                // The reference was never initialized; safe to ignore.
            }
            if (driver != null) {
                driver.quit();
                LOGGER.log(Level.CONFIG, "Disposed WebDriver {0}", identityToString(driver));
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unable to dispose WebDriver {0}", identityToString(driver));
            LOGGER.throwing(getClass().getName(), "disposeWebDriver", t);
        } finally {
            try {
                driverRef.clear();
            } catch (Throwable ignored) {
                // If we can't clear the reference, the reference is likely broken in an
                // unrecoverable way.
                // It is safe to ignore as we are cleaning up.
            }
        }
    }

    /*
     * Creates a RemoteWebDriver that connects to the configured Selenium Grid.
     * The supplied capabilities are defensively copied before augmented with
     * Grid-specific capabilities.
     */
    private WebDriver createRemoteWebDriver(MutableCapabilities capabilities) {
        assert gridConfig.uri.isPresent() : "Trusted caller missed precondition";
        URI uri = gridConfig.uri.get();
        URL gridURL;
        try {
            if (uri.isOpaque()) {
                throw new IllegalArgumentException("URI is not hierarchical");
            }

            // Check for query/fragment which are known to break RemoteWebDriver URL construction.
            // See https://github.com/SeleniumHQ/selenium/issues/9011
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("URI has a query or fragment");
            }

            gridURL = uri.toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Grid URL: " + uri, e);
        }

        // Create a defensive copy of the capabilities options to ensure isolation.
        // We are about to mutate these capabilities by merging Grid-specific configuration
        // (browser name, version, platform, etc.). Copying ensures that the original options
        // instance provided by the factory remains unmodified, preventing side effects if
        // the factory returns a shared or cached instance.
        MutableCapabilities remoteCaps = new MutableCapabilities(capabilities);

        if (driverConfig.type != DriverType.SPI) {
            // Only fall back to the canonical name when the copied factory options didn't already
            // provide one.
            if (remoteCaps.getCapability(BROWSER_NAME) == null) {
                remoteCaps.setCapability(BROWSER_NAME, driverConfig.type.getCanonicalName());
            }
        } else if (remoteCaps.getCapability(BROWSER_NAME) == null) {
            LOGGER.log(Level.WARNING, "Missing capability {0} from SPI Grid session", BROWSER_NAME);
        }

        gridConfig.browserVersion.ifPresent(v -> remoteCaps.setCapability(BROWSER_VERSION, v));

        gridConfig.platform.ifPresent(v -> {
            try {
                Platform p = Platform.fromString(v);
                remoteCaps.setCapability(PLATFORM_NAME, p);
            } catch (WebDriverException e) {
                LOGGER.log(Level.WARNING, "Unable to set platform name to {0}", v);
                LOGGER.throwing(WebDriverService.class.getName(), "createRemoteWebDriver", e);
            }
        });

        gridConfig.applicationName.ifPresent(v -> {
            remoteCaps.setCapability("applicationName", v);
            remoteCaps.setCapability("se:applicationName", v);
        });

        gridConfig.capabilities.forEach(remoteCaps::setCapability);

        return new RemoteWebDriver(gridURL, remoteCaps);
    }

    /*
     * Find the driver factory based on the configuration properties.
     * See the constructor for map construction.
     */
    private DriverFactory<?> findDriverFactory() {
        String factoryName;
        if (driverConfig.type == DriverType.SPI) {
            // For SPI factories, they are keyed by their class name or CDI bean name (see
            // constructor). The configuration must specify which SPI implementation to use via the
            // 'driver.spi' property. It specifies the fully qualified class name or CDI bean name
            // of the SPI factory. Without it, we can't resolve the correct SPI factory.
            factoryName = normalizeKey(driverConfig.spi.orElse(null));
            if (factoryName == null) {
                throw new IllegalArgumentException("driver.spi must be set when driver.type=SPI");
            }
        } else {
            // For non-SPI factories, each inner factory map contains a single driver factory. That
            // inner map is keyed by the DriverType canonical name.
            factoryName = driverConfig.type.getCanonicalName();
        }

        var factoryMap = factories.get(driverConfig.type);
        return (factoryMap == null) ? null : factoryMap.get(factoryName);
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        key = key.strip();
        return key.isEmpty() ? null : key;
    }
}
