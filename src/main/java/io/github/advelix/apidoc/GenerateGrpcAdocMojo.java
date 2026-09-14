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

import com.ly.doc.model.ApiConfig;
import io.github.advelix.apidoc.internal.SmartDocBuilders;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Generates the gRPC AsciiDoc document by delegating to the same Smart-doc builder the official
 * smart-doc-maven-plugin {@code grpc-adoc} goal uses, so the artifact is exactly what Smart-doc
 * produces for the same configuration.
 *
 * <p>Artifacts are written to the output directory: the gRPC AsciiDoc document inside the output directory.
 *
 * <p>All parameters of the {@code openapi} goal apply unchanged ({@code source},
 * {@code output}, {@code serverUrl}, {@code customAnnotations}, ...).
 *
 * <p>Example:
 *
 * <pre>
 * mvn api-doc:grpc-adoc -Dsource=./src/main/java -Doutput=./docs
 * </pre>
 */
@Mojo(name = "grpc-adoc",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresProject = true,
        threadSafe = false)
public class GenerateGrpcAdocMojo extends AbstractApiDocMojo {

    @Override
    protected void generate(ApiConfig config, File outputDirectory, List<File> sourceRoots)
            throws MojoExecutionException, MojoFailureException {
        // Pure delegation: the Smart-doc builder re-runs checkAndInit and the schema build on
        // the configuration assembled by the base class (custom annotations already active).
        SmartDocBuilders.buildGrpcAdoc(config, SmartDocBuilders.newJavaProjectBuilder(config, sourceRoots));
    }
}
