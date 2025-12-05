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

import java.net.*;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import net.larrykramer.test.cdi.ScenarioScoped;
import net.larrykramer.test.config.GridConfig;
import net.larrykramer.test.config.WebDriverConfig;
import net.larrykramer.test.config.WebDriverType;
import net.larrykramer.test.webdriver.WebDriverFactory;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.openqa.selenium.remote.CapabilityType.BROWSER_NAME;
import static org.openqa.selenium.remote.CapabilityType.BROWSER_VERSION;
import static org.openqa.selenium.remote.CapabilityType.PLATFORM_NAME;

/**
 * Provides centralized creation and lifecycle management of Selenium
 * {@link WebDriver} instances for the test suite.
 * <p>
 * {@code WebDriverService} is an
 * {@link ApplicationScoped @ApplicationScoped} CDI bean that ties together the
 * resolved {@link WebDriverConfig configuration} and all discovered
 * {@link WebDriverFactory} implementations. At startup, it classifies every
 * factory by {@link WebDriverType}, allowing the framework to efficiently
 * resolve the correct factory when a WebDriver is requested.
 * <p>
 * The service exposes a {@link Produces @Produces}
 * {@link ScenarioScoped @ScenarioScoped} method that creates and configures a
 * {@link WebDriver} for the current scenario. It supports both local execution
 * and remote execution on a Selenium Grid by instantiating a
 * {@link RemoteWebDriver} with the capabilities derived from the configuration.
 * <p>
 * Complementing the producer, the {@link #destroyWebDriver(WebDriver)} disposer
 * method ensures WebDriver sessions are terminated and resources are released,
 * while gracefully handling any driver-specific shutdown errors.
 */
@ApplicationScoped
public class WebDriverService {
    private static final Logger LOGGER = Logger.getLogger(WebDriverService.class.getName());

    private final WebDriverConfig webDriverConfig;
    private final GridConfig gridConfig;

    private final Map<WebDriverType, Map<String, WebDriverFactory<?>>> factories;

    /**
     * Constructs the service and indexes available {@link WebDriverFactory}
     * instances by {@link WebDriverType}.
     * <p>
     * For non-SPI driver types, the inner map contains a single factory keyed
     * by the canonical driver name returned by
     * {@link WebDriverType#getCanonicalName()}. If multiple factories are
     * discovered for the same non-SPI type, the previously registered factory
     * is replaced and a warning is logged.
     * <p>
     * For {@link WebDriverType#SPI}, multiple factories may coexist and are
     * keyed by their fully qualified class name. The SPI map is intentionally
     * left mutable to avoid repeated defensive copying when many SPI factories
     * are present; the outer map is made unmodifiable.
     *
     * @param webDriverConfig the resolved, type-safe WebDriver configuration
     * @param gridConfig      the resolved, type-safe grid configuration
     * @param factories       all discovered {@link WebDriverFactory}
     *                        implementations
     */
    @Inject
    public WebDriverService(@ConfigProperties WebDriverConfig webDriverConfig,
            @ConfigProperties GridConfig gridConfig, Instance<WebDriverFactory<?>> factories) {
        this.webDriverConfig = webDriverConfig;
        this.gridConfig = gridConfig;

        Map<WebDriverType, Map<String, WebDriverFactory<?>>> m = new EnumMap<>(WebDriverType.class);
        for (var factory : factories) {
            WebDriverType type = factory.getType();
            if (type != WebDriverType.SPI) {
                if (m.containsKey(type)) {
                    Object[] params = { m.get(type).values().iterator().next(), type };
                    LOGGER.log(Level.WARNING, "Replacing factory {0} associated with {1}", params);
                }
                m.put(type, Map.of(type.getCanonicalName(), factory));
            } else {
                // NOTE: We don't use an unmodifiable map for the SPI case.
                // Each new SPI factory would copy the existing immutable map to a mutable map, put
                // itself into the mutable map and then create a new immutable copy. That process
                // could be resource-intensive with many SPI factories.
                String name = factory.getClass().getName();
                m.computeIfAbsent(type, k -> new HashMap<>()).put(name, factory);
            }
        }
        this.factories = Collections.unmodifiableMap(m);
    }

    /**
     * Produces a scenario-scoped {@link WebDriver} according to
     * {@link WebDriverConfig} and the available factories.
     * <p>
     * Resolution rules:
     * <ul>
     * <li>Non-SPI: the factory is selected by {@link WebDriverType}.
     * <li>SPI: the configuration must specify {@code webdriver.spi} with the
     *   fully qualified factory class name; that specific SPI factory is
     *   used.
     * </ul>
     *
     * If {@link GridConfig#uri} is present, a remote session is created with a
     * {@link RemoteWebDriver} and the configured capabilities; otherwise a
     * local WebDriver is created via the resolved {@link WebDriverFactory}.
     * After creation, the selected {@link WebDriverFactory} is given an
     * opportunity to perform driver-specific configuration via
     * {@link WebDriverFactory#configureWebDriver(WebDriver)}. If configuration
     * or logging fails, this method attempts to quit the driver before
     * rethrowing the original error.
     *
     * @return an initialized WebDriver bound to the current scenario scope
     * @throws IllegalArgumentException if the configured WebDriver type is
     *                                  unsupported, no matching SPI factory is
     *                                  found, the SPI class name is missing
     *                                  when {@code webdriver.type=spi}, or the
     *                                  Grid URL is invalid
     * @throws IllegalStateException    if the selected factory cannot create a
     *                                  WebDriver locally or indicates that Grid
     *                                  execution is not supported for the
     *                                  chosen WebDriver type
     */
    @Produces
    @ScenarioScoped
    public WebDriver createWebDriver() {
        WebDriverFactory<?> factory = getWebDriverFactory();
        if (factory == null) {
            String msg;
            if (webDriverConfig.type == WebDriverType.SPI && webDriverConfig.spi.isPresent()) {
                msg = "No SPI factory found for " + webDriverConfig.spi.get();
            } else {
                msg = "Unsupported WebDriver " + webDriverConfig.type;
            }
            throw new IllegalArgumentException(msg);
        }
        LOGGER.log(Level.FINER, "Using WebDriver factory {0}", factory.getClass().getName());

        // Create a remote WebDriver when grid settings are present, otherwise fall back to the
        // local factory, and fail if the WebDriver cannot be constructed.
        WebDriver driver;
        if (gridConfig.uri.isPresent()) {
            MutableCapabilities options = factory.getOptions();
            if (options == null) {
                String msg = "Grid execution not supported for WebDriver " + webDriverConfig.type;
                throw new IllegalStateException(msg);
            }
            driver = createRemoteWebDriver(options);
        } else {
            driver = factory.createWebDriver();
        }
        if (driver == null) {
            String name = factory.getClass().getName();
            throw new IllegalStateException("Unable to create WebDriver using factory " + name);
        }

        try {
            factory.configureWebDriver(driver);
            LOGGER.log(Level.CONFIG, "Created WebDriver {0}", driver);
            return driver;
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
     * Disposes a scenario-scoped {@link WebDriver} by invoking
     * {@link WebDriver#quit()}.
     * <p>
     * This method is idempotent and safe to call with {@code null}. Any
     * exceptions thrown by the underlying WebDriver during quit are caught and
     * logged.
     *
     * @param driver the WebDriver instance to dispose; may be {@code null}
     */
    public void destroyWebDriver(@Disposes WebDriver driver) {
        try {
            if (driver != null) {
                driver.quit();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unable to destroy WebDriver {0}", driver);
            LOGGER.throwing(getClass().getName(), "destroyWebDriver", t);
        }
    }

    /*
     * Creates a RemoteWebDriver that connects to the configured Selenium Grid.
     * The supplied options (capabilities) are defensively copied before augmented
     * with Grid-specific capabilities.
     */
    private WebDriver createRemoteWebDriver(MutableCapabilities options) {
        assert gridConfig.uri.isPresent() : "Trusted caller missed precondition";
        URL gridURL;
        try {
            gridURL = gridConfig.uri.get().toURL();
            String path = gridURL.getPath();
            if (path != null && !path.isEmpty() && !path.endsWith("/")
                    && !path.endsWith("/wd/hub")) {
                LOGGER.log(Level.INFO, "Configured Selenium URL is ''{0}''.\nIf you encounter "
                                + "connection issues, ensure {0} is the correct session "
                                + "endpoint.",
                        sanitizeURI(gridConfig.uri.get()));
            }
        } catch (MalformedURLException | NoSuchElementException e) {
            String msg = "Invalid Selenium Grid URL '"
                    + (gridConfig.uri.isPresent() ? sanitizeURI(gridConfig.uri.get()) : "")
                    + "'";
            throw new IllegalArgumentException(msg, e);
        }

        // Create a defensive copy of the factory options to ensure isolation.
        // We are about to mutate these capabilities by merging Grid-specific configuration
        // (browser name, version, platform, etc.). Copying ensures that the original options
        // instance provided by the factory remains unmodified, preventing side effects if
        // the factory returns a shared or cached instance.
        MutableCapabilities capabilities = new MutableCapabilities(options);

        if (webDriverConfig.type != WebDriverType.SPI) {
            // Only fall back to the canonical name when the copied factory options didn't already
            // provide one.
            if (capabilities.getCapability(BROWSER_NAME) == null) {
                capabilities.setCapability(BROWSER_NAME, webDriverConfig.type.getCanonicalName());
            }
        } else if (capabilities.getCapability(BROWSER_NAME) == null) {
            LOGGER.log(Level.WARNING, "Missing capability {0} from SPI Grid session", BROWSER_NAME);
        }

        gridConfig.browserVersion.ifPresent(v -> capabilities.setCapability(BROWSER_VERSION, v));

        gridConfig.platform.ifPresent(v -> {
            try {
                Platform p = Platform.fromString(v);
                capabilities.setCapability(PLATFORM_NAME, p);
            } catch (WebDriverException e) {
                LOGGER.log(Level.WARNING, "Unable to set platform name to {0}", v);
                LOGGER.throwing(WebDriverService.class.getName(), "createRemoteWebDriver", e);
            }
        });

        gridConfig.applicationName.ifPresent(v -> {
            capabilities.setCapability("applicationName", v);
            capabilities.setCapability("se:applicationName", v);
        });

        gridConfig.capabilities.forEach(capabilities::setCapability);

        return new RemoteWebDriver(gridURL, capabilities);
    }

    /*
     * Resolve the WebDriver factory based on the configuration properties.
     * See the constructor for map construction.
     */
    private WebDriverFactory<?> getWebDriverFactory() {
        var factoryMap = factories.get(webDriverConfig.type);
        if (factoryMap == null) {
            return null; // unknown driver type
        }

        if (webDriverConfig.type != WebDriverType.SPI) {
            // For non-SPI WebDriver factory, each inner factory map contains a single WebDriver
            // factory. That inner map is keyed by the canonical WebDriver type name.
            return factoryMap.get(webDriverConfig.type.getCanonicalName());
        }

        // For SPI WebDriver factories, they are keyed by their class name (see constructor). The
        // configuration must specify which SPI implementation to use via the 'webdriver.spi'
        // property. It specifies the fully qualified class name of the SPI WebDriver factory.
        // Without it, we can't resolve the correct SPI WebDriver factory.
        if (webDriverConfig.spi.isEmpty()) {
            throw new IllegalArgumentException("webdriver.spi must be set when webdriver.type=SPI");
        }

        return factoryMap.get(webDriverConfig.spi.get());
    }

    private static String sanitizeURI(URI uri) {
        try {
            // Attempt to reconstruct the URI without the user-info.
            //@formatter:off
            return new URI(uri.getScheme(),
                    null, // masks user information
                    uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            //@formatter:on
        } catch (URISyntaxException e) {
            // Reconstruction failed.
            // Use a manually constructed fallback which identifies the host.
            String host = (uri.getHost() != null) ? uri.getHost() : "unknown-host";
            return (uri.getScheme() != null) ? (uri.getScheme() + "://<masked>@" + host) : host;
        }
    }
}
