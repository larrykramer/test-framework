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

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.context.NormalScope;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that a bean is scenario scoped.
 * <p>
 * While {@code ScenarioScoped} must be associated with the Cucumber CDI scenario context,
 * integrators are free to associate it with their own custom scenario context. Behavior described
 * below relates to the scenario context supplied with this framework.
 * <p>
 * The scenario scope is active:
 * <ul>
 * <li>for the duration of a single Cucumber scenario, beginning from the point the context is
 *   initialized is executed and continuing through all steps until it is destroyed,
 * <li>during execution of any {@code @BeforeStep} or {@code @AfterStep} hook associated with that
 *   scenario, and
 * <li>during execution of any {@code @Given}, {@code @When} or {@code @Then} step definition
 *   associated with that scenario, and
 * <li>during any CDI-managed lifecycle callback, such as {@code @PostConstruct}, invoked while the
 *   scenario context is active.
 * </ul>
 * <p>
 * The scenario context is destroyed:
 * <ul>
 * <li>after the scenario completes, once all steps and hooks, including {@code AfterStep} hooks,
 *   have finished execution, or
 * <li>immediately if the scenario is aborted before completing its normal execution flow.
 * </ul>
 * <p>
 * The scenario context is typically managed in a Cucumber {@code Before} and {@code After} hook.
 * <p>
 * An event with qualifier {@code @Initialized(ScenarioScoped.class)} is fired when the scenario
 * context is initialized and an event with qualifier {@code @Destroyed(ScenarioScoped.class)} when
 * the scenario context is destroyed. The event payload is the corresponding
 * {@link io.cucumber.java.Scenario Scenario} instance.
 */
@Target({ TYPE, METHOD, FIELD })
@Retention(RUNTIME)
@Documented
@NormalScope
@Inherited
public @interface ScenarioScoped {
}
