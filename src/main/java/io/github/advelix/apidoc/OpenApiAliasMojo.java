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
import java.util.List;

import com.ly.doc.builder.openapi.OpenApiBuilder;
import com.ly.doc.model.ApiConfig;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Generates an OpenAPI 3.x JSON document by delegating to the Smart-doc
 * {@link OpenApiBuilder} public API, mirroring the same-named goal of the official
 * Smart-doc plugin. The document is written to {@code openapi.json} inside the
 * output directory.
 *
 * <p>Example:
 *
 * <pre>
 * mvn api-doc:openapi -Dsource=./src/main/java -Doutput=./docs
 * </pre>
 */
@Mojo(name = "openapi",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresProject = true,
        threadSafe = false)
public class OpenApiAliasMojo extends AbstractApiDocMojo {

    @Override
    protected void generate(ApiConfig config, File outputDirectory, List<File> sourceRoots)
            throws MojoExecutionException, MojoFailureException {
        try {
            OpenApiBuilder.buildOpenApi(config);
        } catch (Throwable t) {
            // Smart-doc surfaces problems such as missing framework classes as Error subtypes as
            // well; either way a failed generation must fail the build.
            throw new MojoExecutionException("api-doc: failed to generate OpenAPI JSON: " + t.getMessage(), t);
        }

        File generated = new File(outputDirectory, OPEN_API_FILE_NAME);
        if (!generated.isFile()) {
            throw new MojoExecutionException("api-doc: expected document " + generated.getAbsolutePath()
                    + " was not created. Check that the configured source root contains documented "
                    + "request handler classes (for example Spring MVC controllers).");
        }
        getLog().info("api-doc: OpenAPI JSON written to " + generated.getAbsolutePath());
    }
}
