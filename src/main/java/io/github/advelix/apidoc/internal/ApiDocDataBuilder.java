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
import java.util.List;

import com.ly.doc.builder.DocBuilderTemplate;
import com.ly.doc.builder.ProjectDocConfigBuilder;
import com.ly.doc.factory.BuildTemplateFactory;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiSchema;
import com.ly.doc.template.IDocBuildTemplate;
import com.thoughtworks.qdox.JavaProjectBuilder;

/**
 * Runs the Smart-doc parsing pipeline over a set of source roots and returns the in-memory
 * {@link ApiSchema} result, without rendering any HTML/JSON artifact.
 *
 * <p>The pipeline mirrors the first half of {@code OpenApiBuilder.getOpenApiDocs} so that the
 * result is exactly the data the JSON goal renders:
 * <ol>
 * <li>{@link DocBuilderTemplate#checkAndInit(ApiConfig, boolean)} initialises the engine
 *     global state (it also validates that the output path is configured);</li>
 * <li>{@link ApiConfig#setParamsDataToTree(boolean)} switches parameters into the tree form
 *     that nested request/response bodies rely on;</li>
 * <li>{@link JavaProjectBuilder} is filled with the same source roots and dependency
 *     class loader the JSON goal uses;</li>
 * <li>the build template is resolved through
 *     {@link BuildTemplateFactory#getDocBuildTemplate(String, ClassLoader)} so both the stock
 *     Spring template (framework unset) and the plugin's combined-annotation template
 *     (framework {@code advelix-spring}) are supported transparently.</li>
 * </ol>
 *
 * <p>The caller is expected to pass a fully populated {@link ApiConfig} (source code paths,
 * output path, class loader, framework and, when active, the custom annotation registry
 * already published). This class holds no mutable static state of its own.
 */
public final class ApiDocDataBuilder {

    private ApiDocDataBuilder() {
    }

    /**
     * Parses the given source roots with the given configuration and returns the document
     * schema. The schema's operation order is the engine rendering order, which is also the
     * order the JSON goal writes into the {@code paths} section.
     *
     * @param config       fully populated Smart-doc configuration; its {@code outPath} must be
     *                     non-empty (the engine rejects an empty value during
     *                     {@code checkAndInit}) and its class loader is used to see dependency
     *                     types, when set
     * @param sourceRoots  validated source directories to parse, must not be empty
     * @param classLoader  class loader able to load the plugin classes for ServiceLoader-based
     *                     template discovery, must not be null
     * @return the parsed document schema, never null; may carry no operations when no
     *         documented controller was found
     */
    public static ApiSchema<ApiDoc> getApiSchema(ApiConfig config, List<File> sourceRoots,
                                                 ClassLoader classLoader) {
        new DocBuilderTemplate().checkAndInit(config, true);
        config.setParamsDataToTree(true);
        config.setShowJavaType(Boolean.FALSE);

        JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();
        for (File sourceRoot : sourceRoots) {
            javaProjectBuilder.addSourceTree(sourceRoot);
        }
        if (config.getClassLoader() != null) {
            javaProjectBuilder.addClassLoader(config.getClassLoader());
        }

        ProjectDocConfigBuilder projectDocConfigBuilder =
                new ProjectDocConfigBuilder(config, javaProjectBuilder);
        IDocBuildTemplate<ApiDoc> template =
                BuildTemplateFactory.getDocBuildTemplate(config.getFramework(), classLoader);
        return template.getApiData(projectDocConfigBuilder);
    }
}
