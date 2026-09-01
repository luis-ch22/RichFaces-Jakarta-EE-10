/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.richfaces.integration.jakarta;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.time.Duration;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Smoke integration test for the Jakarta-native RichFaces library.
 *
 * <p>Deploys a minimal WAR (a {@code <rich:panel>} containing an
 * {@code <a4j:commandButton>} bound to a CDI bean) to a managed WildFly 35
 * (Jakarta EE 10) and drives it with headless Chrome via Selenium 4. It asserts
 * that the RichFaces panel renders and that the a4j Ajax request updates the
 * page without a full reload.</p>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class RichFacesSmokeIT {

    @Deployment(testable = false)
    public static WebArchive createDeployment() {
        // Resolve the Jakarta-native RichFaces jars (and transitive deps) from the
        // local build and bundle them into the WAR's WEB-INF/lib.
        File[] richfaces = Maven.resolver()
                .loadPomFromFile("pom.xml")
                .resolve(
                        "com.github.luisch22.richfaces:richfaces-core",
                        "com.github.luisch22.richfaces:richfaces-a4j",
                        "com.github.luisch22.richfaces:richfaces")
                .withTransitivity()
                .asFile();

        return ShrinkWrap.create(WebArchive.class, "richfaces-smoke.war")
                .addClass(SmokeBean.class)
                .addAsLibraries(richfaces)
                .addAsWebResource(new File("src/test/resources/webapp/smoke.xhtml"), "smoke.xhtml")
                .addAsWebInfResource(new File("src/test/resources/webapp/WEB-INF/beans.xml"), "beans.xml")
                .addAsWebInfResource(new File("src/test/resources/webapp/WEB-INF/faces-config.xml"), "faces-config.xml")
                .setWebXML(new File("src/test/resources/webapp/WEB-INF/web.xml"));
    }

    @ArquillianResource
    private URL contextRoot;

    @Test
    public void panelRendersAndAjaxUpdatesCount() {
        WebDriver driver = newHeadlessChrome();
        try {
            driver.get(contextRoot.toExternalForm() + "smoke.xhtml");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            // The RichFaces panel must render (rf-p is the rich:panel CSS marker).
            WebElement panel = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.id("form:panel")));
            assertTrue("rich:panel should render with the RichFaces 'rf-p' style class",
                    panel.getAttribute("class").contains("rf-p"));

            WebElement count = driver.findElement(By.id("form:count"));
            assertTrue("initial count should be 0", count.getText().contains("Count: 0"));

            // Click the a4j:commandButton; it fires an Ajax request that re-renders
            // only the count output. Wait for the text to change (no full reload).
            driver.findElement(By.id("form:inc")).click();
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.id("form:count"), "Count: 1"));

            assertTrue("a4j Ajax should have incremented the count to 1",
                    driver.findElement(By.id("form:count")).getText().contains("Count: 1"));
        } finally {
            driver.quit();
        }
    }

    private static WebDriver newHeadlessChrome() {
        ChromeOptions options = new ChromeOptions();
        // Selenium Manager (built into Selenium 4) auto-provisions the driver.
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--disable-gpu", "--window-size=1280,1024");
        return new ChromeDriver(options);
    }
}
