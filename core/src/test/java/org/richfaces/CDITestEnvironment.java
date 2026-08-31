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
package org.richfaces;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.servlet.ServletContext;

/**
 * Provides a real CDI 4.0 {@link BeanManager} to the JSF test environment.
 *
 * <p>Faces 4.0 (Mojarra 4) requires CDI at application startup: its
 * {@code ConfigureListener.contextInitialized} calls
 * {@code com.sun.faces.util.Util.getCdiBeanManager(...)} and aborts with
 * {@code IllegalStateException: CDI is not available} if no {@code BeanManager}
 * can be resolved. The {@code test-jsf} {@code StagingServer} is a lightweight
 * servlet mock with no CDI container, so we boot <b>Weld SE</b> once per JVM and
 * publish its {@code BeanManager} into the mock {@code ServletContext} under the
 * key Mojarra looks up first ({@value #MOJARRA_BEAN_MANAGER_ATTRIBUTE}).</p>
 *
 * <p>The container is started lazily and reused across all tests (Weld SE boot
 * is relatively expensive); it is shut down via a JVM shutdown hook.</p>
 *
 * @author RichFaces Jakarta migration
 */
public final class CDITestEnvironment {

    /**
     * ServletContext attribute key that Mojarra 4's {@code Util.getCdiBeanManager}
     * inspects (via the FacesContext application map) before falling back to JNDI
     * and {@code CDI.current()}. Verified against jakarta.faces 4.0.24 bytecode.
     */
    public static final String MOJARRA_BEAN_MANAGER_ATTRIBUTE = "com.sun.faces.cdi.BeanManager";

    private static volatile SeContainer container;

    private CDITestEnvironment() {
    }

    /**
     * Boots Weld SE on first use and returns its {@link BeanManager}. The
     * container is a singleton reused by every test in the JVM (it is never closed
     * between tests, so the same BeanManager backs every Faces startup/shutdown
     * cycle).
     *
     * <p>Classpath discovery is left <b>enabled</b> so Weld picks up Mojarra's own
     * CDI portable extensions and producers shipped in {@code jakarta.faces}
     * (e.g. the {@code FacesContext}/{@code ExternalContext} producers and
     * {@code FlowDiscoveryCDIExtension}). Without them Mojarra 4 NPEs while
     * releasing the FacesContext because it cannot resolve its own CDI beans.</p>
     */
    public static synchronized BeanManager getBeanManager() {
        if (container == null) {
            SeContainerInitializer initializer = SeContainerInitializer.newInstance();
            container = initializer.initialize();
            Runtime.getRuntime().addShutdownHook(new Thread(CDITestEnvironment::shutdown, "weld-se-shutdown"));
        }
        return container.getBeanManager();
    }

    /**
     * Publishes the CDI {@link BeanManager} into the given (mock) ServletContext
     * so that Mojarra's startup listener can resolve it. Must be called before
     * the JSF environment is started (i.e. before {@code StagingServer.init()}).
     */
    public static void install(ServletContext servletContext) {
        servletContext.setAttribute(MOJARRA_BEAN_MANAGER_ATTRIBUTE, getBeanManager());
    }

    private static synchronized void shutdown() {
        if (container != null && container.isRunning()) {
            container.close();
        }
        container = null;
    }
}
