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

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.jboss.test.faces.AbstractFacesTest;
import org.junit.experimental.categories.Category;
import org.richfaces.test.ContainerRequired;

/**
 * Base class for {@link AbstractFacesTest}-style tests that need Faces 4.0 to
 * start under the {@code test-jsf} StagingServer.
 *
 * <p>Faces 4.0 (Mojarra 4) resolves a CDI {@code BeanManager} during startup and
 * fails with {@code IllegalStateException: CDI is not available} when none is
 * present. {@code AbstractFacesTest.setUp()} builds the server, calls the
 * {@code setup*} hooks and then {@code StagingServer.init()}, which fires the
 * registered web listeners in registration order — Mojarra's ConfigureListener
 * among them (added by {@code setupFacesListener()}).</p>
 *
 * <p>The mock ServletContext does not exist until {@code init()}, so we cannot
 * set the attribute up front. Instead we register our own
 * {@link ServletContextListener} <b>before</b> {@code super.setupFacesListener()}
 * adds Mojarra's, so ours runs first and publishes a real Weld SE
 * {@code BeanManager} into the ServletContext that Mojarra then reads.</p>
 *
 * @author RichFaces Jakarta migration
 */
@Category(ContainerRequired.class)
public abstract class AbstractCDIFacesTest extends AbstractFacesTest {

    @Override
    protected void setupFacesListener() {
        // Register our listener BEFORE super adds Mojarra's ConfigureListener.
        // StagingServer fires context listeners in registration order (FIFO), so
        // ours runs first and publishes the BeanManager as a ServletContext
        // attribute, which Mojarra reads via the application map (the first lookup
        // in Util.getCdiBeanManager) — no dependency on the global CDI.current().
        facesServer.addWebListener(new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent sce) {
                CDITestEnvironment.install(sce.getServletContext());
            }
        });
        super.setupFacesListener();
    }
}
