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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiMethodDoc;
import com.ly.doc.model.ApiSchema;
import com.ly.doc.model.SourceCodePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ApiDocDataBuilder} against a minimal Spring MVC controller written
 * into a temporary source root.
 */
class ApiDocDataBuilderTest {

    private static final String CONTROLLER_SOURCE =
            "package demo;\n"
                    + "\n"
                    + "import org.springframework.web.bind.annotation.GetMapping;\n"
                    + "import org.springframework.web.bind.annotation.PathVariable;\n"
                    + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                    + "import org.springframework.web.bind.annotation.RestController;\n"
                    + "\n"
                    + "/**\n"
                    + " * Item endpoints.\n"
                    + " */\n"
                    + "@RestController\n"
                    + "@RequestMapping(\"/api/items\")\n"
                    + "public class ItemController {\n"
                    + "\n"
                    + "    /**\n"
                    + "     * Get one item.\n"
                    + "     */\n"
                    + "    @GetMapping(\"/{id}\")\n"
                    + "    public String getItem(@PathVariable(\"id\") long id) {\n"
                    + "        return \"\" + id;\n"
                    + "    }\n"
                    + "}\n";

    @TempDir
    Path tempDir;

    @Test
    void parsesStandardSpringControllerWithUnsetFramework() throws Exception {
        ApiSchema<ApiDoc> schema = ApiDocDataBuilder.getApiSchema(
                buildConfig(null), sourceRoots(), getClass().getClassLoader());

        assertSingleOperation(schema);
        assertNull(schema.getApiExceptionStatuses().isEmpty() ? null : "unexpected statuses",
                "exception statuses expected to be absent");
    }

    @Test
    void parsesStandardSpringControllerWithAdvelixFramework() throws Exception {
        // No custom annotation registry is published: the advelix template must degrade to the
        // stock Spring behaviour and ServiceLoader must still be able to discover it.
        ApiSchema<ApiDoc> schema = ApiDocDataBuilder.getApiSchema(
                buildConfig("advelix-spring"), sourceRoots(), getClass().getClassLoader());

        assertSingleOperation(schema);
    }

    private void assertSingleOperation(ApiSchema<ApiDoc> schema) {
        assertNotNull(schema);
        List<ApiDoc> docs = schema.getApiDatas();
        assertEquals(1, docs.size());
        ApiDoc doc = docs.get(0);
        assertEquals("ItemController", doc.getName());
        List<ApiMethodDoc> methods = doc.getList();
        assertEquals(1, methods.size());
        ApiMethodDoc method = methods.get(0);
        assertEquals("GET", method.getType());
        assertEquals("/api/items/{id}", method.getPath());
        assertEquals("getItem", method.getName());
        assertEquals("Get one item.", method.getDesc());
        assertEquals(1, method.getPathParams().size());
        assertEquals("id", method.getPathParams().get(0).getField());
        assertTrue(schema.getApiExceptionStatuses().isEmpty());
    }

    private ApiConfig buildConfig(String framework) throws Exception {
        File outDir = tempDir.resolve("out").toFile();
        assertTrue(outDir.mkdirs());
        ApiConfig config = new ApiConfig();
        List<SourceCodePath> paths = Collections.singletonList(
                SourceCodePath.builder().setPath(tempDir.toFile().getAbsolutePath()));
        config.setSourceCodePaths(paths);
        config.setCodePath(tempDir.toFile().getAbsolutePath());
        config.setOutPath(outDir.getAbsolutePath());
        config.setBaseDir(tempDir.toFile().getAbsolutePath());
        config.setProjectName("data-builder-test");
        if (framework != null) {
            config.setFramework(framework);
        }
        return config;
    }

    private List<File> sourceRoots() throws Exception {
        Path packageDir = tempDir.resolve("demo");
        Files.createDirectories(packageDir);
        Files.write(packageDir.resolve("ItemController.java"),
                CONTROLLER_SOURCE.getBytes(StandardCharsets.UTF_8));
        return Collections.singletonList(tempDir.toFile());
    }
}
