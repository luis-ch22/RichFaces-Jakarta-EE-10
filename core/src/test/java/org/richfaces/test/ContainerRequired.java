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
package org.richfaces.test;

/**
 * JUnit category marker for tests that need a real Jakarta EE / CDI container.
 *
 * <p>These tests boot a full {@code FacesContext} through the {@code test-jsf}
 * {@code StagingServer}. Under Faces 4.0 (Mojarra 4) that requires a live CDI
 * container (active request/application contexts, Faces' own CDI producers and
 * portable extensions), which the lightweight servlet mock does not provide.
 * They are therefore excluded from the plain unit test run (surefire
 * {@code excludedGroups}) and are meant to be executed against a real container
 * in the integration phase (Arquillian + WildFly/Liberty).</p>
 *
 * <p>Usage: annotate the test class with
 * {@code @org.junit.experimental.categories.Category(ContainerRequired.class)}.</p>
 *
 * @author RichFaces Jakarta migration
 */
public interface ContainerRequired {
}
