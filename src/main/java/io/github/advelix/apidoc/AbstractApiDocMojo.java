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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.advelix.apidoc.internal.CombinedAnnotationResolver;
import io.github.advelix.apidoc.internal.CustomAnnotationRegistry;
import io.github.advelix.apidoc.internal.CustomAnnotationsModel;
import io.github.advelix.apidoc.internal.CustomAnnotationsParser;
import io.github.advelix.apidoc.internal.DependencyClassLoaderFactory;
import io.github.advelix.apidoc.internal.SourcePathResolver;
import io.github.advelix.apidoc.model.CustomAnnotations;
import io.github.advelix.apidoc.template.ApiDocSpringBootTemplate;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.SourceCodePath;
import com.ly.doc.utils.JsonUtil;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Shared plumbing for every api-doc goal: parameter binding, source root resolution and
 * {@link ApiConfig} assembly.
 *
 * <p>api-doc is an independent project that wraps the Smart-doc parsing engine through its public
 * API. Concrete goals only implement {@link #generate(ApiConfig, File, List)}.
 */
public abstract class AbstractApiDocMojo extends AbstractMojo {

    /** File name Smart-doc uses for the generated OpenAPI document inside the output directory. */
    protected static final String OPEN_API_FILE_NAME = "openapi.json";

    private static final String POM_PACKAGING = "pom";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * Skip documentation generation.
     */
    @Parameter(property = "api-doc.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * Source roots to parse, comma or semicolon separated. Relative paths are resolved against the
     * project base directory. When omitted, the compile source roots of the current project are used.
     * <p>Example: {@code -Dsource=./src/main/java}
     */
    @Parameter(property = "source", alias = "source")
    protected String source;

    /**
     * Output directory for the generated document. Relative paths are resolved against the project
     * base directory.
     * <p>Example: {@code -Doutput=./docs}
     */
    @Parameter(property = "output", alias = "output", defaultValue = "${project.build.directory}/api-doc")
    protected File output;

    /**
     * Optional Smart-doc JSON configuration file. Values bound from the command line or from the
     * plugin configuration always win over the values read from this file.
     */
    @Parameter(property = "api-doc.configFile")
    protected File configFile;

    /**
     * Document title, defaults to the Maven project name.
     */
    @Parameter(property = "api-doc.projectName")
    protected String projectName;

    /**
     * Base URL of the deployed service, written into the OpenAPI {@code servers} section.
     */
    @Parameter(property = "api-doc.serverUrl")
    protected String serverUrl;

    /**
     * Comma separated package prefixes to include, for example {@code com.acme.api,com.acme.web}.
     */
    @Parameter(property = "api-doc.packageFilters")
    protected String packageFilters;

    /**
     * Ask the parsing engine to be strict about incomplete or inconsistent documentation.
     */
    @Parameter(property = "api-doc.strict", defaultValue = "false")
    protected boolean strict;

    /**
     * Expose the resolved project dependencies to the parsing engine so that types coming from
     * jars (enums, shared DTOs, framework annotations) can be resolved.
     */
    @Parameter(property = "api-doc.includeDependencies", defaultValue = "true")
    protected boolean includeDependencies;

    /**
     * Combined (Spring-style {@code @AliasFor}) annotations to register.
     * <p>{@code annotations} lists the fully qualified names; the plugin inspects each
     * annotation definition (project source first, dependency class path as fallback) and
     * derives everything else: entry vs. mapping classification, HTTP method, path
     * properties and so on. {@code declarations} is an optional explicit JSON document
     * (shape: {@code {"entry": [...], "mappings": [{"annotation": ..., "methodType": ...}]}})
     * used for annotations whose definition cannot be inspected or to override a derived
     * value; for the same annotation name the explicit declaration wins.
     * <p>When the parameter is absent or both fields are empty the stock Spring behavior
     * is used unchanged.
     * <p>Example:
     * <pre>
     * &lt;customAnnotations&gt;
     *   &lt;annotations&gt;
     *     &lt;annotation&gt;com.acme.web.ApiController&lt;/annotation&gt;
     *     &lt;annotation&gt;com.acme.web.GetApi&lt;/annotation&gt;
     *   &lt;/annotations&gt;
     *   &lt;!-- optional &lt;declarations&gt;{...}&lt;/declarations&gt; --&gt;
     * &lt;/customAnnotations&gt;
     * </pre>
     */
    @Parameter(property = "api-doc.customAnnotations")
    protected CustomAnnotations customAnnotations;

    /**
     * Command line form of {@code customAnnotations.annotations}: comma separated fully
     * qualified annotation names (for example
     * {@code -Dapi-doc.customAnnotations.annotations='com.acme.web.ApiController,
     * com.acme.web.GetApi'}). Maven cannot bind command line properties to nested POJO
     * fields, so this top level parameter carries the same names when the plugin is
     * invoked from the command line; names from both forms are combined.
     */
    @Parameter(property = "api-doc.customAnnotations.annotations")
    protected String customAnnotationNames;

    /**
     * Command line form of {@code customAnnotations.declarations}: the explicit JSON
     * document (for example {@code -Dapi-doc.customAnnotations.declarations='{...}'}).
     */
    @Parameter(property = "api-doc.customAnnotations.declarations")
    protected String customAnnotationDeclarations;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("api-doc: skip is enabled, no documentation generated.");
            return;
        }

        List<File> sourceRoots = resolveSourceRoots();
        if (sourceRoots.isEmpty()) {
            return;
        }

        File outputDirectory = prepareOutputDirectory();
        ApiConfig config = createApiConfig(sourceRoots, outputDirectory);

        URLClassLoader dependencyClassLoader = null;
        try {
            if (includeDependencies) {
                dependencyClassLoader = createDependencyClassLoader();
                config.setClassLoader(dependencyClassLoader);
            }
            // The dependency class loader must be on the configuration before the custom
            // annotation model is derived: combined annotation definitions commonly live in a
            // dependency module/jar (multi-module projects), and the resolver falls back to
            // loading them through this class loader when they are not in the parsed source.
            activateCustomAnnotations(config);
            getLog().info("api-doc: parsing " + describe(sourceRoots));
            generate(config, outputDirectory, sourceRoots);
        } finally {
            CustomAnnotationRegistry.clear();
            DependencyClassLoaderFactory.close(dependencyClassLoader, getLog());
        }
    }

    /**
     * When {@code api-doc.customAnnotations} is effectively configured, derives and merges
     * the custom annotation model, switches the Smart-doc configuration to the custom
     * annotation template framework and publishes the model so the template picks it up.
     * When the parameter is absent or empty nothing is changed and the generation behaves
     * exactly like the standard Spring flow.
     */
    private void activateCustomAnnotations(ApiConfig config) throws MojoExecutionException {
        CustomAnnotationsModel model = buildCustomAnnotationsModel(config);
        if (model == null) {
            return;
        }
        if (isNotBlank(config.getFramework())
                && !ApiDocSpringBootTemplate.FRAMEWORK.equalsIgnoreCase(config.getFramework())) {
            throw new MojoExecutionException("api-doc: api-doc.customAnnotations requires the "
                    + ApiDocSpringBootTemplate.FRAMEWORK + " framework, but the configuration already "
                    + "sets framework='" + config.getFramework() + "'. Remove the explicit framework "
                    + "value (for example from api-doc.configFile) or drop api-doc.customAnnotations.");
        }
        config.setFramework(ApiDocSpringBootTemplate.FRAMEWORK);
        CustomAnnotationRegistry.set(model);
        getLog().info("api-doc: custom combined annotations enabled ("
                + model.getEntryAnnotations().size() + " entry, "
                + model.getMappings().size() + " mapping)");
    }

    /**
     * Builds the effective custom annotation model from the two declaration forms:
     * <ol>
     * <li>{@code api-doc.customAnnotations} (single JSON string);</li>
     * <li>{@code customAnnotations} (nested POJO: {@code annotations} names auto-derived
     *     from the definitions plus optional {@code declarations} JSON).</li>
     * </ol>
     *
     * @return the merged model, or null when neither form is configured
     * @throws MojoExecutionException when a declaration is invalid or a name cannot be
     *                                 resolved
     */
    private CustomAnnotationsModel buildCustomAnnotationsModel(ApiConfig config)
            throws MojoExecutionException {
        List<String> names = new ArrayList<String>();
        String declarations = null;
        if (customAnnotations != null) {
            if (customAnnotations.getAnnotations() != null) {
                names.addAll(customAnnotations.getAnnotations());
            }
            declarations = customAnnotations.getDeclarations();
        }
        if (isNotBlank(customAnnotationNames)) {
            for (String name : customAnnotationNames.split("[,;]")) {
                if (!name.trim().isEmpty() && !names.contains(name.trim())) {
                    names.add(name.trim());
                }
            }
        }
        if (isNotBlank(customAnnotationDeclarations)) {
            declarations = isNotBlank(declarations)
                    ? declarations + "," + customAnnotationDeclarations
                    : customAnnotationDeclarations;
        }
        if (names.isEmpty() && !isNotBlank(declarations)) {
            return null;
        }
        CustomAnnotationsModel derived = null;
        if (!names.isEmpty()) {
            derived = CombinedAnnotationResolver.resolve(config, names,
                    new CombinedAnnotationResolver.WarnSink() {
                        @Override
                        public void warn(String message) {
                            getLog().warn(message);
                        }
                    });
        }
        CustomAnnotationsModel explicit = null;
        if (customAnnotations != null && isNotBlank(customAnnotations.getDeclarations())) {
            explicit = parseDeclarations(customAnnotations.getDeclarations(), "customAnnotations.declarations");
        }
        if (isNotBlank(customAnnotationDeclarations)) {
            CustomAnnotationsModel cli = parseDeclarations(customAnnotationDeclarations,
                    "api-doc.customAnnotations.declarations");
            explicit = CustomAnnotationsModel.merge(explicit, cli);
        }
        return CustomAnnotationsModel.merge(derived, explicit);
    }

    private CustomAnnotationsModel parseDeclarations(String json, String parameterName)
            throws MojoExecutionException {
        try {
            return CustomAnnotationsParser.parse(json);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("api-doc: invalid " + parameterName + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * Runs the wrapped Smart-doc builder and reports the generated document.
     *
     * @param config          fully populated Smart-doc configuration (already final: custom
     *                        annotation activation, class loader and all user parameters applied)
     * @param outputDirectory directory that already exists and receives the generated document
     * @param sourceRoots     the validated source roots that produced the configuration; goals
     *                        that re-run the parsing pipeline over the source code need them
     */
    protected abstract void generate(ApiConfig config, File outputDirectory, List<File> sourceRoots)
            throws MojoExecutionException, MojoFailureException;

    /**
     * Resolves the source roots to parse.
     *
     * @return existing directories to parse, or an empty list when generation should be skipped
     */
    protected List<File> resolveSourceRoots() throws MojoExecutionException {
        boolean explicit = isNotBlank(source);
        List<File> candidates = explicit
                ? SourcePathResolver.resolveExplicit(source, basedir())
                : SourcePathResolver.resolveDefaults(project.getCompileSourceRoots(), basedir());

        List<File> existing = new ArrayList<>(candidates.size());
        for (File candidate : candidates) {
            if (isSourceRoot(candidate)) {
                existing.add(candidate);
            } else if (explicit) {
                throw new MojoExecutionException("api-doc: source path does not exist: " + candidate
                        + " (resolved from -Dsource=" + source + ")");
            }
        }

        if (existing.isEmpty()) {
            if (POM_PACKAGING.equals(project.getPackaging())) {
                getLog().info("api-doc: aggregator project with packaging 'pom', nothing to parse.");
            } else {
                getLog().warn("api-doc: no source root found, nothing to parse. Set -Dsource=<dir> explicitly.");
            }
        }
        return existing;
    }

    /**
     * Builds the Smart-doc configuration from the bound parameters, optionally seeded with the
     * JSON configuration file.
     */
    protected ApiConfig createApiConfig(List<File> sourceRoots, File outputDirectory) throws MojoExecutionException {
        ApiConfig config = loadConfigFile();

        Set<SourceCodePath> paths = new LinkedHashSet<>();
        for (File sourceRoot : sourceRoots) {
            paths.add(SourceCodePath.builder().setPath(sourceRoot.getAbsolutePath()));
        }
        config.setSourceCodePaths(new ArrayList<>(paths));
        // DocBuildHelper (called by every Smart-doc builder) requires a non-empty codePath as
        // well; the first source root is the one the engine scans.
        config.setCodePath(sourceRoots.get(0).getAbsolutePath());
        config.setOutPath(outputDirectory.getAbsolutePath());
        config.setBaseDir(basedir().getAbsolutePath());
        config.setStrict(strict);

        applyProjectName(config);
        if (isNotBlank(serverUrl)) {
            config.setServerUrl(serverUrl.trim());
        }
        if (isNotBlank(packageFilters)) {
            config.setPackageFilters(packageFilters.trim());
        }
        return config;
    }

    private void applyProjectName(ApiConfig config) {
        String value = normalizeExpression(projectName);
        if (!isNotBlank(value)) {
            value = resolveProjectName();
        }
        if (isNotBlank(value)) {
            config.setProjectName(value.trim());
        }
    }

    /**
     * The project name may arrive as an unresolved {@code ${project.name}} expression when the pom
     * does not declare one, so it is resolved here against the model instead.
     */
    private String resolveProjectName() {
        if (isNotBlank(project.getName())) {
            return project.getName();
        }
        return project.getArtifactId();
    }

    private ApiConfig loadConfigFile() throws MojoExecutionException {
        if (configFile == null) {
            return new ApiConfig();
        }
        File file = configFile.isAbsolute() ? configFile : new File(basedir(), configFile.getPath());
        if (!file.isFile()) {
            throw new MojoExecutionException("api-doc: configFile does not exist: " + file.getAbsolutePath());
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            ApiConfig config = JsonUtil.toObject(json, ApiConfig.class);
            if (config == null) {
                throw new MojoExecutionException("api-doc: configFile is empty: " + file.getAbsolutePath());
            }
            getLog().info("api-doc: using configuration file " + file.getAbsolutePath());
            return config;
        } catch (IOException e) {
            throw new MojoExecutionException("api-doc: cannot read configFile " + file.getAbsolutePath(), e);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("api-doc: cannot parse configFile " + file.getAbsolutePath()
                    + " as a Smart-doc ApiConfig JSON document", e);
        }
    }

    private URLClassLoader createDependencyClassLoader() throws MojoExecutionException {
        File outputDirectory = null;
        if (project.getBuild() != null && isNotBlank(project.getBuild().getOutputDirectory())) {
            outputDirectory = new File(project.getBuild().getOutputDirectory());
        }
        List<URL> urls = DependencyClassLoaderFactory.toUrls(outputDirectory, project.getArtifacts());
        getLog().debug("api-doc: exposing " + urls.size() + " dependency entries to the parsing engine");
        return DependencyClassLoaderFactory.newClassLoader(urls, getClass().getClassLoader());
    }

    private File prepareOutputDirectory() throws MojoExecutionException {
        File outputDirectory = SourcePathResolver.toAbsoluteFile(output.getPath(), basedir());
        try {
            Files.createDirectories(outputDirectory.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("api-doc: cannot create output directory "
                    + outputDirectory.getAbsolutePath(), e);
        }
        return outputDirectory;
    }

    private File basedir() {
        File basedir = project.getBasedir();
        return basedir != null ? basedir : new File(System.getProperty("user.dir"));
    }

    /**
     * A usable source root is a readable directory that holds at least one {@code .java} file.
     */
    private static boolean isSourceRoot(File directory) {
        if (!directory.isDirectory()) {
            return false;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (isSourceRoot(file)) {
                    return true;
                }
            } else if (file.getName().endsWith(".java")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unresolved Maven expressions such as {@code ${project.name}} are treated as absent.
     */
    static String normalizeExpression(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("${") ? null : trimmed;
    }

    private static String describe(List<File> sourceRoots) {
        StringBuilder builder = new StringBuilder();
        for (File sourceRoot : sourceRoots) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(sourceRoot.getAbsolutePath());
        }
        return builder.toString();
    }

    static boolean isNotBlank(String value) {
        return value != null && value.trim().length() > 0;
    }
}
