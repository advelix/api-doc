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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyClassLoaderFactoryTest {

    @TempDir
    File tempDir;

    @Test
    void emptyClasspathYieldsEmptyUrlList() {
        List<URL> urls = DependencyClassLoaderFactory.toUrls(null, new LinkedHashSet<Artifact>());

        assertTrue(urls.isEmpty());
    }

    @Test
    void keepsOnlyUsableEntries() throws IOException {
        File jar = new File(tempDir, "demo-1.0.jar");
        jar.createNewFile();

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("demo-1.0.jar", jar));
        artifacts.add(artifact("demo-missing.jar", null));

        List<URL> urls = DependencyClassLoaderFactory.toUrls(new File(tempDir, "no-such-dir"), artifacts);

        assertEquals(1, urls.size());
        assertEquals(jar.toURI().toURL(), urls.get(0));
    }

    @Test
    void existingOutputDirectoryIsIncluded() throws IOException {
        File classes = new File(tempDir, "classes");
        classes.mkdirs();

        List<URL> urls = DependencyClassLoaderFactory.toUrls(classes, new LinkedHashSet<Artifact>());

        assertEquals(1, urls.size());
        assertEquals(classes.toURI().toURL(), urls.get(0));
    }

    /**
     * Minimal no-op Artifact so the factory can be unit tested without a Maven runtime.
     */
    private static Artifact artifact(String id, final File file) {
        return new Artifact() {
            @Override
            public String getGroupId() {
                return "com.example";
            }

            @Override
            public String getArtifactId() {
                return id;
            }

            @Override
            public String getVersion() {
                return "1.0";
            }

            @Override
            public void setVersion(String version) {
            }

            @Override
            public String getScope() {
                return SCOPE_COMPILE;
            }

            @Override
            public String getType() {
                return "jar";
            }

            @Override
            public String getClassifier() {
                return null;
            }

            @Override
            public boolean hasClassifier() {
                return false;
            }

            @Override
            public File getFile() {
                return file;
            }

            @Override
            public void setFile(File file) {
            }

            @Override
            public String getBaseVersion() {
                return "1.0";
            }

            @Override
            public void setBaseVersion(String version) {
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getDependencyConflictId() {
                return id;
            }

            @Override
            public void addMetadata(ArtifactMetadata metadata) {
            }

            @Override
            public Collection<ArtifactMetadata> getMetadataList() {
                return new LinkedHashSet<ArtifactMetadata>();
            }

            @Override
            public void setRepository(ArtifactRepository repository) {
            }

            @Override
            public ArtifactRepository getRepository() {
                return null;
            }

            @Override
            public void updateVersion(String version, ArtifactRepository repository) {
            }

            @Override
            public String getDownloadUrl() {
                return null;
            }

            @Override
            public void setDownloadUrl(String downloadUrl) {
            }

            @Override
            public ArtifactFilter getDependencyFilter() {
                return null;
            }

            @Override
            public void setDependencyFilter(ArtifactFilter dependencyFilter) {
            }

            @Override
            public ArtifactHandler getArtifactHandler() {
                return null;
            }

            @Override
            public List<String> getDependencyTrail() {
                return java.util.Collections.emptyList();
            }

            @Override
            public void setDependencyTrail(List<String> dependencyTrail) {
            }

            @Override
            public void setScope(String scope) {
            }

            @Override
            public VersionRange getVersionRange() {
                return null;
            }

            @Override
            public void setVersionRange(VersionRange versionRange) {
            }

            @Override
            public void selectVersion(String version) {
            }

            @Override
            public void setGroupId(String groupId) {
            }

            @Override
            public void setArtifactId(String artifactId) {
            }

            @Override
            public boolean isSnapshot() {
                return false;
            }

            @Override
            public void setResolved(boolean resolved) {
            }

            @Override
            public boolean isResolved() {
                return true;
            }

            @Override
            public void setResolvedVersion(String version) {
            }

            @Override
            public void setArtifactHandler(ArtifactHandler artifactHandler) {
            }

            @Override
            public boolean isRelease() {
                return true;
            }

            @Override
            public void setRelease(boolean release) {
            }

            @Override
            public List<ArtifactVersion> getAvailableVersions() {
                return java.util.Collections.emptyList();
            }

            @Override
            public void setAvailableVersions(List<ArtifactVersion> availableVersions) {
            }

            @Override
            public boolean isOptional() {
                return false;
            }

            @Override
            public void setOptional(boolean optional) {
            }

            @Override
            public ArtifactVersion getSelectedVersion() throws OverConstrainedVersionException {
                return null;
            }

            @Override
            public boolean isSelectedVersionKnown() throws OverConstrainedVersionException {
                return true;
            }

            @Override
            public int compareTo(Artifact o) {
                return 0;
            }
        };
    }
}
