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
package org.richfaces.resource.optimizer.resource.scan.impl.reflections;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;

/**
 * Classpath scanning helper backed by <a href="https://github.com/classgraph/classgraph">ClassGraph</a>.
 *
 * <p>Replaces the previous {@code ReflectionsExt}/{@code MarkerResourcesScanner} pair built on
 * Reflections 0.9.8. Reflections 0.9.8 bundled a 2013-era Javassist that cannot read Java 21
 * bytecode (it needed a forced Javassist 3.33 patch to work at all), and its 0.10.x line removes
 * the {@code AbstractScanner}/{@code Store} API this code relied on. ClassGraph reads modern
 * bytecode natively, so the build is reproducible on JDK 21 without any Javassist pinning.</p>
 *
 * <p>The scanner is restricted to the supplied classpath URLs (the RichFaces resource jars/dirs
 * already filtered by the callers) and loads classes with the thread context class loader,
 * preserving the original behaviour.</p>
 *
 * @author Nick Belaevski
 */
public class ClassGraphScanner implements AutoCloseable {

    /** Marker files written by the CDK for dynamic resources: {@code META-INF/<class-name>.resource.properties}. */
    private static final String META_INF = "META-INF/";
    private static final String RESOURCE_PROPERTIES_EXT = ".resource.properties";

    private final ScanResult scanResult;
    private final ClassLoader classLoader;

    public ClassGraphScanner(Collection<URL> urls) {
        this.classLoader = Thread.currentThread().getContextClassLoader();
        this.scanResult = new ClassGraph()
                .overrideClasspath(urls.toArray())
                .ignoreParentClassLoaders()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan();
    }

    /**
     * Returns the concrete/abstract classes (and interfaces) directly or indirectly annotated with the
     * given annotation, mirroring {@code Reflections.getTypesAnnotatedWith(annotation)}.
     */
    public Collection<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotationClass) {
        Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(annotationClass.getName())) {
            Class<?> loaded = loadClass(classInfo.getName());
            if (loaded != null) {
                result.add(loaded);
            }
        }
        return result;
    }

    /**
     * Returns the subtypes (subclasses or implementations) of the given type, mirroring
     * {@code Reflections.getSubTypesOf(type)}.
     */
    public Collection<Class<?>> getSubTypesOf(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        ClassInfo typeInfo = scanResult.getClassInfo(type.getName());
        if (typeInfo == null) {
            return result;
        }
        Collection<ClassInfo> subtypes = type.isInterface()
                ? scanResult.getClassesImplementing(type.getName())
                : scanResult.getSubclasses(type.getName());
        for (ClassInfo classInfo : subtypes) {
            Class<?> loaded = loadClass(classInfo.getName());
            if (loaded != null) {
                result.add(loaded);
            }
        }
        return result;
    }

    /**
     * Returns the classes that ship a CDK marker file {@code META-INF/<class-name>.resource.properties},
     * mirroring the old {@code MarkerResourcesScanner} + {@code ReflectionsExt.getMarkedClasses()}.
     */
    public Collection<Class<?>> getMarkedClasses() {
        Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        for (Resource resource : scanResult.getAllResources()) {
            String path = resource.getPath();
            if (path.startsWith(META_INF) && path.endsWith(RESOURCE_PROPERTIES_EXT)) {
                String className = path.substring(META_INF.length(), path.length() - RESOURCE_PROPERTIES_EXT.length());
                Class<?> loaded = loadClass(className);
                if (loaded != null) {
                    result.add(loaded);
                }
            }
        }
        return result;
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void close() {
        if (scanResult != null) {
            scanResult.close();
        }
    }
}
