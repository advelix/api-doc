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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.SourceCodePath;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CombinedAnnotationResolver} using the fixtures in
 * {@code internal/fixtures/combine}: {@code Resolved*} live in the parsed source tree,
 * {@code RefOnly*} only on the class path.
 */
class CombinedAnnotationResolverTest {

    private static final String FIXTURES = "io.github.advelix.apidoc.internal.fixtures.combine";

    @TempDir
    File baseDir;

    @Test
    void derivesEntryFromRestControllerMetaAnnotation() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(Collections.singletonList(
                FIXTURES + ".ResolvedApiController"));

        assertEquals(Collections.singletonList(FIXTURES + ".ResolvedApiController"),
                model.getEntryAnnotations());
        assertTrue(model.getMappings().isEmpty());
    }

    @Test
    void derivesSingleValueMethodAndAllProps() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".ResolvedGetApi"));

        CustomAnnotationsModel.Mapping mapping = singleMapping(model);
        assertEquals("GET", mapping.getMethodType());
        assertEquals(Collections.singletonList("value"), mapping.getPathProps());
        assertEquals("params", mapping.getParamsProp());
        assertEquals("consumes", mapping.getConsumesProp());
        assertEquals("produces", mapping.getProducesProp());
        assertEquals(Collections.emptyList(), mapping.getExtraMethodTypes());
    }

    @Test
    void derivesMethodPropFromOwnMethodAttribute() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".ResolvedRequestApi"));

        CustomAnnotationsModel.Mapping mapping = singleMapping(model);
        // @RequestMapping without method + own method attribute: methodProp wins over the GET default
        assertEquals(null, mapping.getMethodType());
        assertEquals("method", mapping.getMethodProp());
        // the aliased 'region' attribute is not a path carrier
        assertEquals(Collections.singletonList("value"), mapping.getPathProps());
        assertEquals("consumes", mapping.getConsumesProp());
        assertEquals("produces", mapping.getProducesProp());
        assertEquals("params", mapping.getParamsProp());
    }

    @Test
    void derivesMultiValueMethodIntoPrimaryAndExtras() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".ResolvedMultiApi"));

        CustomAnnotationsModel.Mapping mapping = singleMapping(model);
        assertEquals("GET", mapping.getMethodType());
        assertEquals(Collections.singletonList("PUT"), mapping.getExtraMethodTypes());
        assertEquals(Arrays.asList("value", "path"), mapping.getPathProps());
    }

    @Test
    void defaultsBareAnnotationToGetWithWarning() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".ResolvedBareApi"));

        CustomAnnotationsModel.Mapping mapping = singleMapping(model);
        assertEquals("GET", mapping.getMethodType());
        assertEquals(null, mapping.getMethodProp());
        assertTrue(resolver.warnings.get(0).contains("ResolvedBareApi"),
                "expected a warning for the bare annotation, got: " + resolver.warnings);
    }

    @Test
    void defaultsRequestMappingWithoutMethodToGetWithWarning() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".ResolvedNoMethodApi"));

        assertEquals("GET", singleMapping(model).getMethodType());
        assertTrue(resolver.warnings.get(0).contains("without a method value"),
                "expected the no-method warning, got: " + resolver.warnings);
    }

    @Test
    void resolvesAnnotationsOnlyOnTheClassPathThroughReflection() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(Arrays.asList(
                FIXTURES + ".RefOnlyApi",
                FIXTURES + ".RefOnlyEntryApi"));

        assertEquals(Collections.singletonList(FIXTURES + ".RefOnlyEntryApi"),
                model.getEntryAnnotations());
        CustomAnnotationsModel.Mapping mapping = singleMapping(model);
        assertEquals("DELETE", mapping.getMethodType());
        assertEquals(Collections.singletonList("value"), mapping.getPathProps());
    }

    @Test
    void classPathFallbackIsUsedWhenTheSourceTreeHasNoFixture() throws Exception {
        File otherSourceRoot = new File(baseDir, "other-src");
        otherSourceRoot.mkdirs();

        Resolving resolver = new Resolving(Arrays.asList(otherSourceRoot.getAbsolutePath()));

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".RefOnlyApi"));

        assertEquals("DELETE", singleMapping(model).getMethodType());
    }

    @Test
    void unknownAnnotationFailsWithActionableMessage() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(Collections.singletonList("com.example.DoesNotExist")));
        assertTrue(e.getMessage().contains("com.example.DoesNotExist"));
        assertTrue(e.getMessage().contains("declarations"));
    }

    @Test
    void emptyInputYieldsEmptyModel() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(new ArrayList<String>());

        assertTrue(model.getEntryAnnotations().isEmpty());
        assertTrue(model.getMappings().isEmpty());
    }

    @Test
    void deduplicatesInputKeepingFirstOccurrence() throws Exception {
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(Arrays.asList(
                FIXTURES + ".ResolvedPostApi",
                FIXTURES + ".ResolvedPostApi",
                "  " + FIXTURES + ".ResolvedPostApi" + "  "));

        assertEquals(1, model.getMappings().size());
        assertEquals("POST", singleMapping(model).getMethodType());
    }

    @Test
    void sourceDefinitionWinsOverClassPathCopy() throws Exception {
        // The fixtures are visible both as source and on the test class path; the source
        // (qdox) loader must be the one used. A tampered class-path-only expectation would
        // therefore fail, proving source-first ordering.
        Resolving resolver = new Resolving(Collections.emptyList());

        CustomAnnotationsModel model = resolver.resolve(
                Collections.singletonList(FIXTURES + ".ResolvedMultiApi"));

        assertEquals("GET", singleMapping(model).getMethodType());
        assertEquals(Collections.singletonList("PUT"), singleMapping(model).getExtraMethodTypes());
    }

    /**
     * Test wrapper building the resolver against the fixture source root (when present in
     * the working tree) with the test class path as the reflection fallback.
     */
    private final class Resolving {

        final List<String> warnings = new ArrayList<>();
        private final ApiConfig config;

        Resolving(List<String> extraSourceRoots) throws Exception {
            config = new ApiConfig();
            List<String> roots = new ArrayList<>(extraSourceRoots);
            File fixtureRoot = locateFixtureSourceRoot();
            if (fixtureRoot != null) {
                roots.add(fixtureRoot.getAbsolutePath());
            }
            List<SourceCodePath> paths = new ArrayList<>();
            for (String root : roots) {
                paths.add(SourceCodePath.builder().setPath(root));
            }
            config.setSourceCodePaths(paths);
            config.setClassLoader(fixtureClassLoader());
        }

        CustomAnnotationsModel resolve(List<String> names) {
            return CombinedAnnotationResolver.resolve(config, names, new CombinedAnnotationResolver.WarnSink() {
                @Override
                public void warn(String message) {
                    warnings.add(message);
                }
            });
        }

        /**
         * @return the directory containing the fixture sources, or null when the tests run
         *         from a jar where the sources are not on disk
         */
        private File locateFixtureSourceRoot() {
            File candidate = new File("src/test/java");
            File fixturePackageDir = new File(candidate, "io/github/advelix/apidoc/internal/fixtures/combine");
            return fixturePackageDir.isDirectory() ? candidate : null;
        }

        private URLClassLoader fixtureClassLoader() throws IOException {
            List<URL> urls = new ArrayList<>();
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
                if (!entry.isEmpty()) {
                    urls.add(new File(entry).toURI().toURL());
                }
            }
            return new URLClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader());
        }
    }

    private static CustomAnnotationsModel.Mapping singleMapping(CustomAnnotationsModel model) {
        assertEquals(1, model.getMappings().size(),
                "expected exactly one mapping, got: " + model.getMappings());
        return model.getMappings().get(0);
    }
}
