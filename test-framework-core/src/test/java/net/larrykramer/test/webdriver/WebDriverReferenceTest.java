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

package net.larrykramer.test.webdriver;

import java.util.Optional;

import net.larrykramer.test.categories.SmokeTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.Interactive;

import static net.larrykramer.test.util.SharedUtils.identityToString;
import static net.larrykramer.test.webdriver.WebDriverReference.MAX_UNWRAP_DEPTH;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WebDriverReferenceTest {
    private WebDriverReference driverRef;

    @Before
    public void setUp() {
        driverRef = new WebDriverReference();
    }

    @Test
    public void testConstructor_whenDriverProvided_returnsStoredDriver() {
        WebDriver mockDriver = mockWebDriver();
        WebDriver result = new WebDriverReference(mockDriver).get();
        assertSame(mockDriver, result);
    }

    @Test
    public void testConstructor_givenWrappedDriver_storesDriverAndUnwrapsToTarget() {
        // Arrange
        WebDriver mockDriver = mockWebDriver();
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        // Act
        WebDriverReference result = new WebDriverReference(mockWrappedDriver);
        // Assert
        assertSame(mockWrappedDriver, result.get());
        assertSame(mockDriver, result.getUnderlyingDriver());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_givenNullDriver_throwsNullPointerException() {
        new WebDriverReference(null);
    }

    @Test
    public void testSet_withPlainWebDriver_storesSameInstance() {
        WebDriver mockDriver = mockWebDriver();
        driverRef.set(mockDriver);
        assertSame(mockDriver, driverRef.get());
    }

    @Test
    public void testSet_withWrapsDriverChain_returnsOutermostDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver();
        WebDriver mockSecondWrappedDriver = createWrapsDriver(mockDriver);
        WebDriver mockFirstWrappedDriver = createWrapsDriver(mockSecondWrappedDriver);
        // Act
        driverRef.set(mockFirstWrappedDriver);
        // Assert
        assertSame(mockFirstWrappedDriver, driverRef.get());
    }

    @Test
    public void testSet_withDeepWrapsDriverChain_stopsAtMaxUnwrapDepth() {
        // Arrange
        // Build a chain of 30 WrapsDriver instances around a plain WebDriver so that the chain
        // depth is greater than the maximum unwrap depth (25).
        WebDriver mockDriver = mockWebDriver();
        WebDriver[] chain = new WebDriver[MAX_UNWRAP_DEPTH + 5];
        WebDriver next = mockDriver;
        for (int i = chain.length - 1; i >= 0; i--) {
            WebDriver mockWrappedDriver = createWrapsDriver(next);
            chain[i] = mockWrappedDriver;
            next = mockWrappedDriver;
        }

        // Act
        driverRef.set(chain[0]);

        // Assert
        // With a maximum unwrap depth of 25, we expect to end on the wrapper at index 25.
        final WebDriver driver = driverRef.getUnderlyingDriver();
        assertSame(chain[MAX_UNWRAP_DEPTH], driver);
        assertNotSame(chain[MAX_UNWRAP_DEPTH + 1], driver);
        assertNotSame(mockDriver, driver);

    }

    @Test(expected = NullPointerException.class)
    public void testSet_givenNullDriver_throwsNullPointerException() {
        driverRef.set(null);
    }

    @Test
    public void testGet_givenStateNotInitialized_throwsIllegalStateException() {
        var e = assertThrows(IllegalStateException.class, driverRef::get);
        assertEquals("WebDriverReference not initialized", e.getMessage());
    }

    @Test
    public void testGet_givenStateInitialized_returnsStoredDriver() {
        WebDriver mockDriver = mockWebDriver();
        driverRef.set(mockDriver);
        assertSame(mockDriver, driverRef.get());
    }

    @Test
    public void testGetClass_givenStateNotInitialized_throwsIllegalStateException() {
        var expected = IllegalStateException.class;
        var e = assertThrows(expected, () -> driverRef.get(TakesScreenshot.class));
        assertEquals("WebDriverReference not initialized", e.getMessage());
    }

    @Test
    public void testGetClass_whenDriverImplementsInterface_returnsDriver() {
        WebDriver mockDriver = mockWebDriver(TakesScreenshot.class);
        driverRef.set(mockDriver);
        assertSame(mockDriver, driverRef.get(TakesScreenshot.class));
    }

    @Test
    public void testGetClass_whenOnlyUnderlyingDriverImplementsInterface_returnsUnderlyingDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(JavascriptExecutor.class);
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        assertSame(mockDriver, driverRef.get(JavascriptExecutor.class));
    }

    @Test
    public void testGetClass_whenWrappedAndUnderlyingImplementInterface_prefersWrappedDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(TakesScreenshot.class);
        WebDriver mockWrappedDriver = mockWebDriver(WrapsDriver.class, TakesScreenshot.class);
        when(((WrapsDriver) mockWrappedDriver).getWrappedDriver()).thenReturn(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        assertSame(mockWrappedDriver, driverRef.get(TakesScreenshot.class));
    }

    @Test
    public void testGetClass_whenNoDriverImplementsInterface_throwsClassCastException() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(); // implements nothing extra
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver); // implements nothing extra
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        var e = assertThrows(ClassCastException.class, () -> driverRef.get(TakesScreenshot.class));
        assertEquals("The current WebDriver does not support TakesScreenshot", e.getMessage());
    }

    @Category(SmokeTest.class)
    @Test
    public void testGetClass_whenWrappedDriverIsMissingInteractive_worksWithSeleniumActions() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(Interactive.class);
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act
        // Request a driver that supports Interactive and pass it to Selenium Actions which casts
        // it to Interactive.
        Actions actions = new Actions(driverRef.get(Interactive.class));
        actions.click().perform();
        // Assert
        verify((Interactive) mockDriver).perform(any());
    }

    @Test(expected = NullPointerException.class)
    public void testGetClass_givenNullInterfaceClass_throwsNullPointerException() {
        driverRef.set(mockWebDriver());
        driverRef.get(null);
    }

    @Test
    public void testAs_whenDriverImplementsInterface_returnsTypedDriver() {
        WebDriver mockDriver = mockWebDriver(TakesScreenshot.class);
        driverRef.set(mockDriver);
        assertSame(mockDriver, driverRef.as(TakesScreenshot.class));
    }

    @Test
    public void testAs_whenOnlyUnderlyingDriverImplementsInterface_returnsUnderlyingDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(JavascriptExecutor.class);
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        assertSame(mockDriver, driverRef.as(JavascriptExecutor.class));
    }

    @Test
    public void testAs_whenWrappedDriverIsMissingInterface_invokesMethodOnUnderlyingDriver() {
        // Arrange
        // language=JavaScript
        final String js = "console.log('test');";
        WebDriver mockDriver = mockWebDriver(JavascriptExecutor.class);
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act
        driverRef.as(JavascriptExecutor.class).executeScript(js);
        // Assert
        verify((JavascriptExecutor) mockDriver).executeScript(js);
    }

    @Test(expected = ClassCastException.class)
    public void testAs_whenNoDriverImplementsInterface_throwsClassCastException() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(); // implements nothing extra
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver); // implements nothing extra
        driverRef.set(mockWrappedDriver);
        // Act
        driverRef.as(JavascriptExecutor.class);
    }

    @Test
    public void testTryAs_whenDriverImplementsInterface_returnsOptionalWithDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(TakesScreenshot.class);
        driverRef.set(mockDriver);
        // Act
        Optional<TakesScreenshot> result = driverRef.tryAs(TakesScreenshot.class);
        // Assert
        assertTrue(result.isPresent());
        assertSame(mockDriver, result.get());
    }

    @Test
    public void testTryAs_whenWrappedDriverMissingInterface_returnsOptionalWithUnderlyingDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(JavascriptExecutor.class);
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act
        Optional<JavascriptExecutor> result = driverRef.tryAs(JavascriptExecutor.class);
        // Assert
        assertTrue(result.isPresent());
        assertSame(mockDriver, result.get());
    }

    @Test
    public void testTryAs_whenNoDriverImplementsInterface_returnEmptyOptional() {
        // Arrange
        WebDriver mockDriver = mockWebDriver(); // implements nothing extra
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver); // implements nothing extra
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        assertFalse(driverRef.tryAs(TakesScreenshot.class).isPresent());
    }

    @Test
    public void testGetUnderlyingDriver_givenStateNotInitialized_throwsIllegalStateException() {
        var e = assertThrows(IllegalStateException.class, driverRef::getUnderlyingDriver);
        assertEquals("WebDriverReference not initialized", e.getMessage());
    }

    @Test
    public void testGetUnderlyingDriver_givenStateInitialized_returnsSameInstanceAsSupplied() {
        WebDriver mockDriver = mockWebDriver();
        driverRef.set(mockDriver);
        assertSame(mockDriver, driverRef.getUnderlyingDriver());
    }

    @Test
    public void testGetUnderlyingDriver_givenSelfReferencingWrapsDriver_returnsSelfImmediately() {
        // Arrange
        // Simulate a driver that wraps itself (a bad, but possible, implementation).
        WebDriver mockWrappedDriver = mockWebDriver(WrapsDriver.class);
        WrapsDriver wrapsDriver = (WrapsDriver) mockWrappedDriver;
        when(wrapsDriver.getWrappedDriver()).thenReturn(mockWrappedDriver);
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        assertSame(mockWrappedDriver, driverRef.getUnderlyingDriver());
    }

    @Test
    public void testGetUnderlyingDriver_withWrapsDriverChain_returnsInnermostDriver() {
        // Arrange
        WebDriver mockDriver = mockWebDriver();
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        driverRef.set(mockWrappedDriver);
        // Act & Assert
        assertSame(mockDriver, driverRef.getUnderlyingDriver());
    }

    @Test(expected = IllegalStateException.class)
    public void testClear_whenStateWasInitializedThenCleared_clearsReference() {
        driverRef.set(mockWebDriver());
        driverRef.clear();
        driverRef.get();
    }

    @Test
    public void testToString_whenUninitialized_returnsUninitializedMarker() {
        assertEquals(identityToString(null), driverRef.toString());
    }

    @Test
    public void testToString_whenInitialized_usesWrappedDriverIdentity() {
        // Arrange
        WebDriver mockDriver = mockWebDriver();
        WebDriver mockWrappedDriver = createWrapsDriver(mockDriver);
        // Act
        driverRef.set(mockWrappedDriver);
        // Assert
        assertEquals(identityToString(mockWrappedDriver), driverRef.toString());
    }

    private WebDriver createWrapsDriver(WebDriver driver) {
        WebDriver mockWrappedDriver = mockWebDriver(WrapsDriver.class);
        WrapsDriver wrapsDriver = (WrapsDriver) mockWrappedDriver;
        when(wrapsDriver.getWrappedDriver()).thenReturn(driver);
        return mockWrappedDriver;
    }

    private static WebDriver mockWebDriver(Class<?>... interfaceClasses) {
        if (interfaceClasses != null && interfaceClasses.length > 0) {
            return mock(WebDriver.class, withSettings().extraInterfaces(interfaceClasses));
        }
        return mock(WebDriver.class);
    }
}
