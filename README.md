# Test Framework

## Overview

The Test Framework is an extensible Java framework for end-to-end browser automation.

It integrates:

* **Cucumber** for Behavior-Driven Development (BDD) specifications written in plain language.
* **Selenium WebDriver** for browser interaction.
* **Jakarta Contexts and Dependency Injection (CDI)** for managing components and dependencies, enabling modularity, testability, and scenario-level state isolation.
* **SmallRye Config** for flexible and hierarchical configuration management.

It is designed to be extensible and to simplify the creation and execution of reliable end-to-end tests across browsers and platforms.

### Features

* **Behavior-Driven Development (BDD):**
  Write clear, executable Gherkin specifications to improve collaboration between technical and non-technical stakeholders.
* **Scenario-Scoped State Isolation:**
  Ensure repeatable results by preventing state leakage between tests with the `@ScenarioScoped` CDI scope, which runs each Cucumber scenario in complete isolation.
* **Dependency Injection:**
  Leverage Jakarta CDI for dependency injection to keep test code clean, maintainable, and extensible.
* **Flexible Configuration:**
  Manage test environments with SmallRye Config, which supports layered configuration from files, environment variables, and system properties.
* **Browser Automation:**
  Interact with web applications across various browsers and platforms using Selenium WebDriver.
* **Screenshot Capture on Failure:**
  Automatically capture and attach screenshots to Cucumber reports when a scenario fails, enabling faster debugging.
* **Internationalization (i18n):**
  Adapt tests for different locales with built-in support for internationalized strings.
* **Parallel Test Execution:**
  Execute scenarios in parallel with Maven Surefire to reduce test suite runtime.

### Modules

This repository is a multi-module Maven project:

* Core Module (`test-framework-core`):
  The core library. This module contains all the reusable framework components, including CDI, WebDriver, config, and other support utilities.
* Test Suite Module (`test-suite`):
  A test suite that depends on the core module and contains Cucumber integration tests.
  Your work will primarily be within this module.

## Getting Started

This framework serves as a template for your test automation projects.
Follow these steps to set up and run tests.

### Prerequisites

* Java Development Kit (JDK) 21 or higher
* Apache Maven 3.8.8 or higher
* At least one supported browser installed (Chrome, Edge, Firefox, or Safari on macOS)

### How to Use

#### 1. Fork and Clone this Repository

Fork this repository to your GitHub account, then clone it locally.

```shell
# Replace <your-username> with your GitHub username
git clone https://github.com/<your-username>/test-framework.git
cd test-framework
```

#### 2. Write Tests and Configure

1. Create Cucumber `.feature` files under `test-suite/src/test/resources/features`.
2. Implement step definitions and support classes under `test-suite/src/test/java`.
   Use CDI to inject components such as `WebDriver` and `LocalizationService` into your step definitions.
3. Configure test settings in `test-suite/src/test/resources/application.properties`.
   You can override properties with system properties or environment variables; see [Configuration](#configuration) for details.

Example `application.properties`:

```properties
# -----------------------------------------------------------------------------
# Driver Configuration
# -----------------------------------------------------------------------------
# The WebDriver to use for testing.
# Supported values: CHROME, EDGE, SAFARI (macOS-only), FIREFOX, SPI (custom factory)
driver.type=CHROME

# Run the browser in headless mode.
# Headless mode is recommended for CI environments.
# Set to false to watch the tests execute in a visible browser window.
# Note: Safari does not support headless mode, so this property is ignored.
driver.headless=true

# Base implicit wait timeout in milliseconds.
# The driver will wait this long for elements to appear.
driver.implicit-timeout=0
```

#### 3. Run Tests

This repository separates unit tests in the core module from integration tests (Cucumber scenarios) in the test suite module.
Run them together or individually.

* **Run all integration tests (Cucumber scenarios) in the test suite locally:**
  ```shell
  mvn -pl test-suite -am verify
  ```

* **Run all scenarios on Selenium Grid:**
  ```shell
  mvn -pl test-suite -am verify -Dgrid.url=http://your-selenium-grid-url.example:4444/
  ```

  **Note:**
  Selenium Grid 3 commonly uses `/wd/hub` instead of the root path `/`.
  Adjust the URL to match your deployment.

* **Run a specific subset of scenarios using tags:**
  ```shell
  mvn -pl test-suite -am verify -Dcucumber.filter.tags="@your-tag"
  ```

* **Run only unit tests in the core module (and skip integration tests):**
  ```shell
  mvn -pl test-framework-core -am test
  ```

* **Run all unit and integration tests from the repository root:**
  ```shell
  mvn clean verify
  ```

**Note on parallel execution:**
Integration tests run in parallel across forks, controlled by the `forkCount` property.
The default is `0.5C` (half the number of CPU cores).
You can override it at runtime:

```shell
mvn -pl test-suite -am verify -DforkCount=1
```

## Configuration

The framework is configured via `test-suite/src/test/resources/application.properties` using SmallRye Config.
You can override properties with system properties (e.g., `-Ddriver.type=CHROME`) or environment variables (e.g., `DRIVER_TYPE=CHROME`).
Environment variables use the property name in uppercase, with dots (`.`) and hyphens (`-`) replaced by underscores (`_`).

Common configuration properties include:

| Property                      | Description                                                                                                                                        | Default                                                                  |
|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `driver.type`                 | Target driver. Supported: `EDGE`, `SAFARI`, `FIREFOX`, `CHROME`, `SPI`.                                                                            | OS-dependent (`EDGE` on Windows, `SAFARI` on macOS, `FIREFOX` otherwise) |
| `driver.spi`                  | Fully qualified class name of a custom driver factory implementation that creates Selenium `WebDriver` instances. Required when `driver.type=SPI`. | _(none)_                                                                 |
| `driver.headless`             | Run the browser in headless mode.                                                                                                                  | `false`                                                                  |
| `driver.maximize`             | Maximize the browser window on startup.                                                                                                            | `false`                                                                  |
| `driver.window-size`          | Set a specific window size. Overrides `driver.maximize`. Format: `<width>x<height>` or `<width>,<height>`.                                         | _(none)_                                                                 |
| `driver.allow-insecure-certs` | Accept invalid TLS certificates.                                                                                                                   | `false`                                                                  |
| `driver.implicit-timeout`     | Implicit wait timeout in milliseconds. `0` disables it.                                                                                            | `0`                                                                      |
| `grid.url`                    | URL of the remote Selenium Grid hub for distributed testing.                                                                                       | _(none)_                                                                 |

For the complete list of driver-specific, proxy, and advanced settings, see the configuration and driver classes under the
[config](test-framework-core/src/main/java/net/larrykramer/test/config)
and
[webdriver](test-framework-core/src/main/java/net/larrykramer/test/webdriver)
packages.

## Continuous Integration

This project uses GitHub Actions for Continuous Integration (CI).
Every push or pull request targeting the `main` branch triggers a build that runs tests on Linux, Windows, and macOS using JDK 21 and 25.
The workflow runs unit tests in the core module. Cucumber integration tests are not executed in CI by default.

For more details, see the [CI workflow file](.github/workflows/ci.yml).

## Contributing

Contributions are welcome!
Please read the [Contributing Guidelines](CONTRIBUTING.md) for details on how to get started, coding standards, and testing.

For questions or general help, see the project [Discussions](https://github.com/larrykramer/test-framework/discussions).

To report a security vulnerability, please disclose it privately by emailing [larry@larrykramer.net](mailto:larry@larrykramer.net).

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for the full license text.
