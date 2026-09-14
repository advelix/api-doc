/*
 * Copyright 2026 the api-doc authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.advelix.apidoc.internal;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;

/**
 * Builds a {@link URLClassLoader} over the project's own output directory and its resolved
 * artifacts so the Smart-doc engine can resolve classes that live in jars.
 */
public final class DependencyClassLoaderFactory {

    private DependencyClassLoaderFactory() {
    }

    /**
     * Collects classpath entry URLs, de-duplicated and in a stable order (output directory first,
     * then artifacts in resolution order).
     */
    public static List<URL> toUrls(File outputDirectory, Set<Artifact> artifacts) {
        Set<URL> urls = new LinkedHashSet<>();
        if (outputDirectory != null && outputDirectory.isDirectory()) {
            addIfNotNull(urls, urlOf(outputDirectory));
        }
        if (artifacts != null) {
            for (Artifact artifact : artifacts) {
                if (artifact == null || artifact.getFile() == null) {
                    continue;
                }
                File file = artifact.getFile();
                if (file.isFile()) {
                    addIfNotNull(urls, urlOf(file));
                }
            }
        }
        return new ArrayList<>(urls);
    }

    /**
     * Creates the class loader handed to the parsing engine. The plugin's own class loader is the
     * parent so Smart-doc classes resolve consistently.
     */
    public static URLClassLoader newClassLoader(List<URL> urls, ClassLoader parent) {
        URL[] urlArray = urls.toArray(new URL[0]);
        return new URLClassLoader(urlArray, parent);
    }

    /**
     * Closes the class loader if one was created, ignoring failures so that it never masks the real
     * build result.
     */
    public static void close(URLClassLoader classLoader, Log log) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (IOException e) {
            if (log != null) {
                log.debug("api-doc: cannot close dependency class loader: " + e.getMessage());
            }
        }
    }

    private static void addIfNotNull(Set<URL> urls, URL url) {
        if (url != null) {
            urls.add(url);
        }
    }

    private static URL urlOf(File file) {
        try {
            return file.toURI().toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            return null;
        }
    }
}
