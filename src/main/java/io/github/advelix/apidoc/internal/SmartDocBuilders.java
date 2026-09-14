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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.ly.doc.builder.ApiDocBuilder;
import com.ly.doc.builder.AdocDocBuilder;
import com.ly.doc.builder.HtmlApiDocBuilder;
import com.ly.doc.builder.JMeterBuilder;
import com.ly.doc.builder.PostmanJsonBuilder;
import com.ly.doc.builder.TornaBuilder;
import com.ly.doc.builder.grpc.GrpcAsciidocBuilder;
import com.ly.doc.builder.grpc.GrpcHtmlBuilder;
import com.ly.doc.builder.grpc.GrpcMarkdownBuilder;
import com.ly.doc.builder.javadoc.JavadocAdocBuilder;
import com.ly.doc.builder.javadoc.JavadocHtmlBuilder;
import com.ly.doc.builder.javadoc.JavadocMarkdownBuilder;
import com.ly.doc.builder.openapi.SwaggerBuilder;
import com.ly.doc.builder.rpc.RpcAdocBuilder;
import com.ly.doc.builder.rpc.RpcHtmlBuilder;
import com.ly.doc.builder.rpc.RpcMarkdownBuilder;
import com.ly.doc.builder.rpc.RpcTornaBuilder;
import com.ly.doc.builder.rpc.RpcWordDocBuilder;
import com.ly.doc.builder.websocket.WebSocketAsciidocBuilder;
import com.ly.doc.builder.websocket.WebSocketHtmlBuilder;
import com.ly.doc.builder.websocket.WebSocketMarkdownBuilder;
import com.ly.doc.model.ApiConfig;
import com.thoughtworks.qdox.JavaProjectBuilder;

/**
 * Direct delegations to every Smart-doc builder the official
 * {@code smart-doc-maven-plugin} exposes.
 *
 * <p>Each entry point calls the exact same static builder method the official plugin's mojo
 * calls (for example the {@code word} goal maps to
 * {@code com.ly.doc.builder.WordDocBuilder.buildApiDoc}), so the generated artifacts are the
 * ones Smart-doc produces for the same configuration.
 *
 * <p>Smart-doc builders are static and several of them stash global state on the passed
 * {@link ApiConfig} (for example the swagger file name), so the goal-level runner passes a
 * configuration assembled by the base class; each goal run delegates to a single builder.
 */
public final class SmartDocBuilders {

    /** Word (docx) document per API group plus dict/error documents (official goal: {@code word}). */
    public static void buildWord(ApiConfig config, JavaProjectBuilder projectBuilder) {
        try {
            com.ly.doc.builder.WordDocBuilder.buildApiDoc(config, projectBuilder);
        } catch (Exception e) {
            throw asUnchecked(e);
        }
    }

    /** HTML document (official goal: {@code html}). */
    public static void buildHtml(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(HtmlApiDocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** Markdown document (official goal: {@code markdown}). */
    public static void buildMarkdown(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(ApiDocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** AsciiDoc document (official goal: {@code adoc}). */
    public static void buildAdoc(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(AdocDocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** Postman collection JSON (official goal: {@code postman}). */
    public static void buildPostman(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(PostmanJsonBuilder.class, "buildPostmanCollection", config, projectBuilder);
    }

    /** JMeter test plan (official goal: {@code jmeter}). */
    public static void buildJmeter(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(JMeterBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** Torna REST upload (official goal: {@code torna-rest}). */
    public static void buildTornaRest(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(TornaBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** Torna RPC upload (official goal: {@code torna-rpc}). */
    public static void buildTornaRpc(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(RpcTornaBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /**
     * Swagger 2.0 JSON (official goal: {@code swagger}). Smart-doc's swagger builder writes
     * {@code <outPath>/openapi.json} — the same file name the {@code openapi} goal writes — so
     * running both goals into one output directory means the later run wins.
     */
    public static void buildSwagger(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(SwaggerBuilder.class, "buildOpenApi", config, projectBuilder);
    }

    /** WebSocket AsciiDoc document (official goal: {@code websocket-adoc}). */
    public static void buildWebSocketAdoc(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(WebSocketAsciidocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** WebSocket HTML document (official goal: {@code websocket-html}). */
    public static void buildWebSocketHtml(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(WebSocketHtmlBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** WebSocket Markdown document (official goal: {@code websocket-markdown}). */
    public static void buildWebSocketMarkdown(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(WebSocketMarkdownBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** RPC AsciiDoc document (official goal: {@code rpc-adoc}). */
    public static void buildRpcAdoc(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(RpcAdocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** RPC HTML document (official goal: {@code rpc-html}). */
    public static void buildRpcHtml(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(RpcHtmlBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** RPC Markdown document (official goal: {@code rpc-markdown}). */
    public static void buildRpcMarkdown(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(RpcMarkdownBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** RPC Word document (official goal: {@code rpc-word}). */
    public static void buildRpcWord(ApiConfig config, JavaProjectBuilder projectBuilder) {
        try {
            RpcWordDocBuilder.buildApiDoc(config, projectBuilder);
        } catch (Exception e) {
            throw asUnchecked(e);
        }
    }

    /** Javadoc AsciiDoc document (official goal: {@code javadoc-adoc}). */
    public static void buildJavadocAdoc(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(JavadocAdocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** Javadoc HTML document (official goal: {@code javadoc-html}). */
    public static void buildJavadocHtml(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(JavadocHtmlBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** Javadoc Markdown document (official goal: {@code javadoc-markdown}). */
    public static void buildJavadocMarkdown(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(JavadocMarkdownBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** gRPC AsciiDoc document (official goal: {@code grpc-adoc}); expects .proto sources. */
    public static void buildGrpcAdoc(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(GrpcAsciidocBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** gRPC HTML document (official goal: {@code grpc-html}); expects .proto sources. */
    public static void buildGrpcHtml(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(GrpcHtmlBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /** gRPC Markdown document (official goal: {@code grpc-markdown}); expects .proto sources. */
    public static void buildGrpcMarkdown(ApiConfig config, JavaProjectBuilder projectBuilder) {
        invoke(GrpcMarkdownBuilder.class, "buildApiDoc", config, projectBuilder);
    }

    /**
     * Builds the qdox source model for one goal run: every source root registered on the
     * builder plus the dependency class loader when configured.
     */
    public static JavaProjectBuilder newJavaProjectBuilder(ApiConfig config, List<File> sourceRoots) {
        JavaProjectBuilder projectBuilder = new JavaProjectBuilder();
        for (File sourceRoot : sourceRoots) {
            projectBuilder.addSourceTree(sourceRoot);
        }
        if (config.getClassLoader() != null) {
            projectBuilder.addClassLoader(config.getClassLoader());
        }
        return projectBuilder;
    }

    private SmartDocBuilders() {
    }

    /**
     * Calls the Smart-doc static entry point by name, converting checked exceptions to
     * unchecked ones so goal code never needs to declare {@code Exception}. The method name is
     * part of the pinned Smart-doc API surface, so a miss is a build-time contract violation.
     */
    private static void invoke(Class<?> builderClass, String methodName, ApiConfig config,
                               JavaProjectBuilder projectBuilder) {
        try {
            Method method = builderClass.getMethod(methodName, ApiConfig.class, JavaProjectBuilder.class);
            method.invoke(null, config, projectBuilder);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("api-doc: cannot invoke "
                    + builderClass.getName() + "." + methodName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause.getMessage(), cause);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("api-doc: missing Smart-doc entry point "
                    + builderClass.getName() + "." + methodName, e);
        }
    }

    private static IllegalStateException asUnchecked(Exception e) {
        return new IllegalStateException(e.getMessage(), e);
    }
}
