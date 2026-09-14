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

package io.github.advelix.apidoc.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.ly.doc.model.ApiMethodDoc;
import com.ly.doc.model.annotation.EntryAnnotation;
import com.ly.doc.model.annotation.FrameworkAnnotations;
import com.ly.doc.model.annotation.MappingAnnotation;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import io.github.advelix.apidoc.internal.CustomAnnotationRegistry;
import io.github.advelix.apidoc.internal.CustomAnnotationsModel;
import io.github.advelix.apidoc.internal.CustomAnnotationsParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiDocSpringBootTemplateTest {

    private static final String MODEL_JSON =
            "{\"entry\":[\"com.acme.web.ApiController\"],"
                    + "\"mappings\":[{\"annotation\":\"com.acme.web.GetApi\",\"methodType\":\"GET\",\"pathProps\":[\"value\"]},"
                    + "{\"annotation\":\"com.acme.web.PostApi\",\"methodType\":\"POST\"}]}";

    private final ApiDocSpringBootTemplate template = new ApiDocSpringBootTemplate();

    @AfterEach
    void clearRegistry() {
        CustomAnnotationRegistry.clear();
    }

    @Test
    void supportsOnlyTheAdvelixSpringFramework() {
        assertTrue(template.supportsFramework(ApiDocSpringBootTemplate.FRAMEWORK));
        assertTrue(template.supportsFramework("ADVELIX-SPRING"));
        assertFalse(template.supportsFramework("spring"));
        assertFalse(template.supportsFramework("spring-boot"));
        assertFalse(template.supportsFramework(null));
        assertFalse(template.supportsFramework(""));
    }

    @Test
    void registeredAnnotationsWithoutRegistryMatchesStockSpringRegistry() {
        CustomAnnotationRegistry.set(CustomAnnotationsParser.parse("{\"entry\":[]}"));

        FrameworkAnnotations merged = template.registeredAnnotations();
        FrameworkAnnotations stock = stockRegistry();

        assertEquals(stock.getEntryAnnotations().keySet(), merged.getEntryAnnotations().keySet());
        assertEquals(stock.getMappingAnnotations().keySet(), merged.getMappingAnnotations().keySet());
        assertTrue(merged.getEntryAnnotations().containsKey("Controller"));
        assertTrue(merged.getEntryAnnotations().containsKey("RestController"));
        assertTrue(merged.getMappingAnnotations().containsKey("RequestMapping"));
        assertTrue(merged.getMappingAnnotations().containsKey("GetMapping"));
    }

    @Test
    void registersEntryAnnotationsUnderFqcnAndSimpleName() {
        CustomAnnotationRegistry.set(CustomAnnotationsParser.parse(MODEL_JSON));

        Map<String, EntryAnnotation> entries = template.registeredAnnotations().getEntryAnnotations();

        EntryAnnotation byFqcn = entries.get("com.acme.web.ApiController");
        assertNotNull(byFqcn);
        assertEquals("ApiController", byFqcn.getAnnotationName());
        assertEquals("com.acme.web.ApiController", byFqcn.getAnnotationFullyName());
        assertSame(byFqcn, entries.get("ApiController"));
    }

    @Test
    void registersClassLevelEntryAnnotationAlsoAsMappingForPathPrefix() {
        CustomAnnotationRegistry.set(CustomAnnotationsParser.parse(MODEL_JSON));

        Map<String, MappingAnnotation> mappings = template.registeredAnnotations().getMappingAnnotations();

        MappingAnnotation controller = mappings.get("com.acme.web.ApiController");
        assertNotNull(controller);
        assertEquals("ApiController", controller.getAnnotationName());
        assertEquals("com.acme.web.ApiController", controller.getAnnotationFullyName());
        assertEquals(null, controller.getMethodType());
        assertEquals(Arrays.asList("value", "path"), controller.getPathProps());
        assertSame(controller, mappings.get("ApiController"));
    }

    @Test
    void registersMethodLevelMappingsWithMethodTypeAndPathProps() {
        CustomAnnotationRegistry.set(CustomAnnotationsParser.parse(MODEL_JSON));

        Map<String, MappingAnnotation> mappings = template.registeredAnnotations().getMappingAnnotations();

        MappingAnnotation get = mappings.get("com.acme.web.GetApi");
        assertNotNull(get);
        assertEquals("GetApi", get.getAnnotationName());
        assertEquals("GET", get.getMethodType());
        assertEquals(Arrays.asList("value"), get.getPathProps());
        assertEquals(null, get.getMethodProp());
        assertSame(get, mappings.get("GetApi"));

        // pathProps omitted -> default lookup order
        MappingAnnotation post = mappings.get("com.acme.web.PostApi");
        assertNotNull(post);
        assertEquals("POST", post.getMethodType());
        assertEquals(Arrays.asList("value", "path"), post.getPathProps());
    }

    @Test
    void doesNotOverwriteStandardMappings() {
        CustomAnnotationRegistry.set(CustomAnnotationsParser.parse(
                "{\"mappings\":[{\"annotation\":\"com.acme.web.GetMapping\",\"methodType\":\"DELETE\"}]}"));

        Map<String, MappingAnnotation> mappings = template.registeredAnnotations().getMappingAnnotations();

        MappingAnnotation stock = mappings.get("GetMapping");
        assertNotNull(stock);
        assertEquals("GET", stock.getMethodType(), "the stock GetMapping registration must survive");
    }

    @Test
    void expandsMultiValueMethodIntoOneDocumentPerVerb(@TempDir File tempDir) throws IOException {
        writeControllerSource(tempDir);
        CustomAnnotationRegistry.set(new CustomAnnotationsModel(
                Collections.<String>emptyList(),
                Collections.singletonList(new CustomAnnotationsModel.Mapping(
                        "io.github.advelix.apidoc.internal.fixtures.combine.ResolvedMultiApi",
                        "GET", Collections.<String>emptyList(), null, null, null, null,
                        Collections.singletonList("PUT")))));
        JavaClass cls = parseController(tempDir);

        ApiMethodDoc get = doc("find", "GET", 1);
        ApiMethodDoc list = doc("list", "POST", 2);
        java.util.List<ApiMethodDoc> expanded = ApiDocSpringBootTemplate.expandMultiValueMethods(
                cls, Arrays.asList(get, list));

        assertEquals(3, expanded.size());
        assertSame(get, expanded.get(0));
        assertEquals("GET", expanded.get(0).getType());
        assertEquals("PUT", expanded.get(1).getType(), "the extra verb document follows the primary one");
        assertNotEquals(get.getMethodId(), expanded.get(1).getMethodId());
        assertEquals("POST", expanded.get(2).getType());
        assertEquals(Arrays.asList(1, 2, 3), ordersOf(expanded), "orders are renumbered after expansion");
    }

    @Test
    void leavesSingleValueMappingsUntouched(@TempDir File tempDir) throws IOException {
        writeControllerSource(tempDir);
        CustomAnnotationRegistry.set(new CustomAnnotationsModel(
                Collections.<String>emptyList(),
                Collections.singletonList(new CustomAnnotationsModel.Mapping(
                        "io.github.advelix.apidoc.internal.fixtures.combine.ResolvedGetApi",
                        "GET", Collections.<String>emptyList(), null, null, null, null,
                        Collections.<String>emptyList()))));
        JavaClass cls = parseController(tempDir);

        ApiMethodDoc get = doc("find", "GET", 1);
        java.util.List<ApiMethodDoc> expanded = ApiDocSpringBootTemplate.expandMultiValueMethods(
                cls, Collections.singletonList(get));

        assertEquals(1, expanded.size());
        assertSame(get, expanded.get(0));
        assertEquals(1, expanded.get(0).getOrder());
    }

    @Test
    void expansionWithoutRegistryIsANoOp(@TempDir File tempDir) throws IOException {
        writeControllerSource(tempDir);
        CustomAnnotationRegistry.clear();
        JavaClass cls = parseController(tempDir);

        ApiMethodDoc get = doc("find", "GET", 1);
        java.util.List<ApiMethodDoc> expanded = ApiDocSpringBootTemplate.expandMultiValueMethods(
                cls, Collections.singletonList(get));

        assertEquals(1, expanded.size());
        assertSame(get, expanded.get(0));
    }

    @Test
    void registryIsClearedBetweenRuns() {
        CustomAnnotationRegistry.set(CustomAnnotationsParser.parse(MODEL_JSON));
        assertTrue(template.registeredAnnotations().getMappingAnnotations().containsKey("com.acme.web.GetApi"));

        CustomAnnotationRegistry.clear();

        assertFalse(template.registeredAnnotations().getMappingAnnotations().containsKey("com.acme.web.GetApi"));
    }

    private static void writeControllerSource(File tempDir) throws IOException {
        File sourceDir = new File(tempDir, "src/demo");
        assertTrue(sourceDir.mkdirs());
        File controller = new File(sourceDir, "MultiController.java");
        PrintWriter out = new PrintWriter(controller, "UTF-8");
        try {
            out.println("package demo;");
            out.println("import io.github.advelix.apidoc.internal.fixtures.combine.ResolvedMultiApi;");
            out.println("public class MultiController {");
            out.println("    @ResolvedMultiApi(\"/x\")");
            out.println("    public String find() { return null; }");
            out.println("}");
        } finally {
            out.close();
        }
    }

    private static JavaClass parseController(File tempDir) {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        builder.addSourceTree(new File(tempDir, "src"));
        return builder.getClassByName("demo.MultiController");
    }

    private static ApiMethodDoc doc(String name, String type, int order) {
        ApiMethodDoc doc = new ApiMethodDoc();
        doc.setName(name);
        doc.setType(type);
        doc.setPath("/api/x");
        doc.setMethodId("method-" + name);
        doc.setOrder(order);
        return doc;
    }

    private static java.util.List<Integer> ordersOf(java.util.List<ApiMethodDoc> docs) {
        java.util.List<Integer> orders = new java.util.ArrayList<Integer>();
        for (ApiMethodDoc doc : docs) {
            orders.add(doc.getOrder());
        }
        return orders;
    }

    private static FrameworkAnnotations stockRegistry() {
        CustomAnnotationRegistry.clear();
        return new ApiDocSpringBootTemplate().registeredAnnotations();
    }
}
