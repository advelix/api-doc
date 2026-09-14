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
package io.github.advelix.apidoc;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.advelix.apidoc.internal.ApiDocDataBuilder;
import io.github.advelix.apidoc.internal.ExcelWriter;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiSchema;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Generates an Excel (xlsx) document from the same Smart-doc parsing pipeline the JSON goal
 * uses, so both artifacts always describe exactly the same operations in the same order.
 *
 * <p>The result is written to {@code api-doc.xlsx} inside the output directory, next to
 * {@code openapi.json}. All parameters of the {@code openapi} goal apply unchanged.
 *
 * <p>Example:
 *
 * <pre>
 * mvn api-doc:excel -Dsource=./src/main/java -Doutput=./docs
 * </pre>
 */
@Mojo(name = "excel",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresProject = true,
        threadSafe = false)
public class GenerateExcelMojo extends AbstractApiDocMojo {

    /** File name the Excel goal writes inside the output directory. */
    private static final String EXCEL_FILE_NAME = "api-doc.xlsx";

    @Override
    protected void generate(ApiConfig config, File outputDirectory, List<File> sourceRoots)
            throws MojoExecutionException, MojoFailureException {
        ApiSchema<ApiDoc> schema;
        try {
            schema = ApiDocDataBuilder.getApiSchema(config, sourceRoots, getClass().getClassLoader());
        } catch (Throwable t) {
            // Smart-doc surfaces problems such as missing framework classes as Error subtypes as
            // well; either way a failed generation must fail the build.
            throw new MojoExecutionException("api-doc: failed to parse API for Excel: " + t.getMessage(), t);
        }

        if (ExcelWriter.collectOperations(schema).isEmpty()) {
            getLog().warn("api-doc: no documented operations found; writing a header-only "
                    + EXCEL_FILE_NAME + ". Check that the configured source root contains "
                    + "documented request handler classes (for example Spring MVC controllers).");
        }

        File generated = new File(outputDirectory, EXCEL_FILE_NAME);
        try {
            ExcelWriter.write(schema, generated);
        } catch (IOException e) {
            throw new MojoExecutionException("api-doc: failed to write Excel document "
                    + generated.getAbsolutePath(), e);
        }
        getLog().info("api-doc: Excel document written to " + generated.getAbsolutePath());
    }
}
