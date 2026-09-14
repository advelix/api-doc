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

import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.SourceCodePath;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaType;
import com.thoughtworks.qdox.model.expression.AnnotationValue;
import com.thoughtworks.qdox.model.expression.AnnotationValueList;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the {@link CustomAnnotationsModel} of combined annotations from the annotation
 * definitions themselves, so users only have to list the fully qualified names.
 *
 * <p>Definition lookup order: the parsed project source first (qdox, fed with the configured
 * source directories and class loader), the reflection class loader second (annotations
 * living in dependency jars).
 *
 * <p>Derivation rules:
 * <ul>
 * <li>entry annotation: the definition carries a {@code @RestController} or
 *     {@code @Controller} meta-annotation;</li>
 * <li>method-level mapping: the first value of a {@code @RequestMapping(method = X)}
 *     meta-annotation (single or multi value) becomes {@code methodType}; the remaining
 *     values of a multi-value declaration become {@code extraMethodTypes};</li>
 * <li>a self-declared {@code method} attribute of type
 *     {@code (org.springframework.web.bind.annotation.RequestMethod[])} becomes
 *     {@code methodProp} (the HTTP method is then read at each use site);</li>
 * <li>none of the above: {@code methodType} defaults to {@code GET} (with a warning);</li>
 * <li>{@code pathProps}: self-declared {@code String} attributes in source order that alias
 *     {@code RequestMapping} {@code value}/{@code path} (via {@code @AliasFor}) or are named
 *     {@code value}/{@code path}; empty falls back to the default {@code value, path} order;</li>
 * <li>self-declared attributes named {@code params}/{@code consumes}/{@code produces} map to
 *     the corresponding property names.</li>
 * </ul>
 *
 * <p>Errors: an annotation whose definition cannot be found anywhere is a hard error (the
 * message suggests explicit declarations); every other gap degrades to a warning with the
 * best-effort derivation.
 */
public final class CombinedAnnotationResolver {

    static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    static final String CONTROLLER = "org.springframework.stereotype.Controller";
    static final String REQUEST_METHOD = "org.springframework.web.bind.annotation.RequestMethod";

    /**
     * Warning sink for non-fatal derivation problems; implementations must never throw.
     */
    public interface WarnSink {
        void warn(String message);
    }

    private final ApiConfig config;
    private final List<String> annotationNames;
    private final WarnSink warnSink;

    private CombinedAnnotationResolver(ApiConfig config, List<String> annotationNames, WarnSink warnSink) {
        this.config = config;
        this.annotationNames = Collections.unmodifiableList(new ArrayList<>(annotationNames));
        this.warnSink = warnSink == null ? new WarnSink() {
            @Override
            public void warn(String message) {
                System.err.println("[api-doc] " + message);
            }
        } : warnSink;
    }

    /**
     * Derives the model for the given annotation names from their definitions.
     *
     * @param config          the Smart-doc configuration carrying the source directories and
     *                        (optionally) the dependency class loader
     * @param annotationNames fully qualified annotation names, deduplicated keeping order
     * @param warnSink        where non-fatal warnings go, or null for {@code System.err}
     * @return the derived model; an empty name list yields an empty model
     * @throws IllegalStateException when a definition is neither in the parsed source nor
     *                               loadable by the configured class loader
     */
    public static CustomAnnotationsModel resolve(ApiConfig config, List<String> annotationNames,
                                                 WarnSink warnSink) {
        List<String> deduplicated = new ArrayList<>();
        if (annotationNames != null) {
            for (String name : annotationNames) {
                if (name != null && !name.trim().isEmpty() && !deduplicated.contains(name.trim())) {
                    deduplicated.add(name.trim());
                }
            }
        }
        if (deduplicated.isEmpty()) {
            return new CustomAnnotationsModel(new ArrayList<String>(),
                    new ArrayList<CustomAnnotationsModel.Mapping>());
        }
        return new CombinedAnnotationResolver(config, deduplicated, warnSink).doResolve();
    }

    private CustomAnnotationsModel doResolve() {
        JavaProjectBuilder sourceBuilder = buildSourceParser();
        List<String> entries = new ArrayList<>();
        List<CustomAnnotationsModel.Mapping> mappings = new ArrayList<>();
        for (String name : annotationNames) {
            Definition definition = loadDefinition(name, sourceBuilder);
            if (definition == null) {
                throw new IllegalStateException("api-doc: cannot find the definition of annotation '" + name
                        + "' in the project source or on the dependency class path. "
                        + "Provide an explicit api-doc.customAnnotations.declarations entry instead, "
                        + "or make the annotation resolvable (for example via "
                        + "api-doc.includeDependencies).");
            }
            if (definition.kind == Definition.Kind.ENTRY) {
                entries.add(name);
            } else {
                mappings.add(deriveMapping(name, definition));
            }
        }
        return new CustomAnnotationsModel(entries, mappings);
    }

    /**
     * Builds the qdox parser for the configured source directories, registering the
     * configured class loader so imported framework types resolve.
     */
    private JavaProjectBuilder buildSourceParser() {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        List<SourceCodePath> sourcePaths = config.getSourceCodePaths();
        if (sourcePaths != null) {
            for (SourceCodePath sourcePath : sourcePaths) {
                String path = sourcePath == null ? null : sourcePath.getPath();
                if (path == null || path.trim().isEmpty()) {
                    continue;
                }
                File directory = new File(path);
                if (directory.isDirectory()) {
                    builder.addSourceTree(directory);
                }
            }
        }
        ClassLoader classLoader = config.getClassLoader();
        if (classLoader != null) {
            builder.addClassLoader(classLoader);
        }
        return builder;
    }

    /**
     * Loads the definition of one annotation: qdox source first, reflection as fallback.
     *
     * <p>The qdox lookup is only trusted when the annotation class is actually declared in one
     * of the configured source roots. When the class is not in the parsed source, qdox still
     * returns a class loaded from the registered class loader, but as a binary stub that drops
     * all meta-annotations and attributes; using it would silently misclassify the annotation
     * and lose the HTTP method / path properties, so in that case derivation goes straight to
     * the reflection class loader, which reads the full definition from the jar.
     */
    private Definition loadDefinition(String name, JavaProjectBuilder sourceBuilder) {
        if (!isDeclaredInConfiguredSource(name)) {
            return loadFromReflection(name);
        }
        try {
            JavaClass sourceClass = sourceBuilder.getClassByName(name);
            if (sourceClass != null && sourceClass.isAnnotation()) {
                return Definition.fromQdox(name, sourceClass);
            }
        } catch (RuntimeException e) {
            warnSink.warn("api-doc: source-based derivation of '" + name + "' failed ("
                    + e.getMessage() + "); falling back to the class path");
        }
        return loadFromReflection(name);
    }

    private Definition loadFromReflection(String name) {
        ClassLoader classLoader = config.getClassLoader();
        if (classLoader == null) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(name, false, classLoader);
            if (clazz.isAnnotation()) {
                return Definition.fromReflection(name, clazz);
            }
        } catch (Exception e) {
            warnSink.warn("api-doc: class-path derivation of '" + name + "' failed (" + e.getMessage() + ")");
        }
        return null;
    }

    /**
     * @return true when {@code name} has a matching {@code <package>/<simple>.java} file under
     *         one of the configured source roots, i.e. the class is declared in the parsed source
     */
    private boolean isDeclaredInConfiguredSource(String name) {
        String relative = name.replace('.', '/') + ".java";
        List<SourceCodePath> sourcePaths = config.getSourceCodePaths();
        if (sourcePaths == null) {
            return false;
        }
        for (SourceCodePath sourcePath : sourcePaths) {
            String path = sourcePath == null ? null : sourcePath.getPath();
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            if (new File(new File(path.trim()), relative).isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Derives one method-level mapping from the definition, warning about every gap.
     */
    private CustomAnnotationsModel.Mapping deriveMapping(String name, Definition definition) {
        String methodType = null;
        String methodProp = findMethodProp(definition);
        List<String> extraMethodTypes = new ArrayList<>();
        if (definition.hasMeta("RequestMapping")) {
            List<String> methods = definition.metaEnumValues("RequestMapping", "method", REQUEST_METHOD);
            if (!methods.isEmpty()) {
                methodType = methods.get(0);
                extraMethodTypes.addAll(methods.subList(1, methods.size()));
            }
        }
        if (methodType == null && methodProp == null) {
            if (definition.hasMeta("RequestMapping")) {
                warnSink.warn("api-doc: '" + name + "' is meta-annotated with @RequestMapping "
                        + "without a method value; defaulting to GET");
            } else {
                warnSink.warn("api-doc: '" + name + "' is neither meta-annotated with @RequestMapping "
                        + "nor declaring a 'method' attribute; defaulting to GET");
            }
            methodType = "GET";
        }
        return new CustomAnnotationsModel.Mapping(name, methodType, definition.pathProps(), methodProp,
                attributeName(definition, "params"), attributeName(definition, "consumes"),
                attributeName(definition, "produces"), extraMethodTypes);
    }

    /**
     * @return the name of the self-declared attribute whose (array) component type is
     *         {@code RequestMethod} ({@code org.springframework.web.bind.annotation.RequestMethod},
     *         accepted by simple name when the import does not resolve), or null
     */
    private static String findMethodProp(Definition definition) {
        for (Attribute attribute : definition.attributes.values()) {
            if (REQUEST_METHOD.equals(attribute.componentType)
                    || "RequestMethod".equals(simpleName(attribute.componentType))) {
                return attribute.name;
            }
        }
        return null;
    }

    private static String attributeName(Definition definition, String name) {
        return definition.attributes.containsKey(name) ? name : null;
    }

    /**
     * Raw form of an annotation definition: the kind (entry vs. method mapping), the
     * meta-annotations and the attributes in source/declaration order. Both loaders
     * (qdox and reflection) produce the same shape so the derivation logic is
     * source-format agnostic.
     */
    static final class Definition {

        enum Kind {
            ENTRY, MAPPING
        }

        final String name;
        final Kind kind;
        final Map<String, MetaAnnotation> metadata;
        final Map<String, Attribute> attributes;

        private Definition(String name, Kind kind, Map<String, MetaAnnotation> metadata,
                           Map<String, Attribute> attributes) {
            this.name = name;
            this.kind = kind;
            this.metadata = metadata;
            this.attributes = attributes;
        }

        static Definition fromQdox(String name, JavaClass sourceClass) {
            Map<String, MetaAnnotation> metadata = new LinkedHashMap<>();
            for (JavaAnnotation meta : nullSafe(sourceClass.getAnnotations())) {
                String type = typeName(meta);
                if (type == null) {
                    continue;
                }
                Map<String, AnnotationValue> values = new LinkedHashMap<>();
                for (Map.Entry<String, AnnotationValue> entry : meta.getPropertyMap().entrySet()) {
                    values.put(entry.getKey(), entry.getValue());
                }
                metadata.put(simpleName(type), new MetaAnnotation(type, values));
            }
            Map<String, Attribute> attributes = new LinkedHashMap<>();
            for (JavaMethod method : nullSafe(sourceClass.getMethods())) {
                String rawType = returnTypeValue(method);
                attributes.put(method.getName(), new Attribute(method.getName(), rawType,
                        componentType(rawType), aliasTargetsOf(method)));
            }
            return new Definition(name, classify(metadata), metadata, attributes);
        }

        static Definition fromReflection(String name, Class<?> annotationClass) {
            Map<String, MetaAnnotation> metadata = new LinkedHashMap<>();
            for (Annotation meta : annotationClass.getDeclaredAnnotations()) {
                String type = meta.annotationType().getName();
                Map<String, AnnotationValue> values = new LinkedHashMap<>();
                for (Method member : meta.annotationType().getDeclaredMethods()) {
                    String key = member.getName();
                    if (member.getParameterCount() > 0 || "annotationType".equals(key)
                            || "toString".equals(key) || "hashCode".equals(key) || "equals".equals(key)) {
                        continue;
                    }
                    values.put(key, renderReflectionValue(invoke(member, meta)));
                }
                metadata.put(simpleName(type), new MetaAnnotation(type, values));
            }
            Map<String, Attribute> attributes = new LinkedHashMap<>();
            for (Method method : annotationClass.getDeclaredMethods()) {
                if (method.getParameterCount() > 0) {
                    continue;
                }
                String rawType = method.getReturnType().getName();
                attributes.put(method.getName(), new Attribute(method.getName(), rawType,
                        componentType(rawType), aliasTargetsOf(method)));
            }
            return new Definition(name, classify(metadata), metadata, attributes);
        }

        boolean hasMeta(String metaSimpleName) {
            return metadata.containsKey(metaSimpleName);
        }

        /**
         * @return the enum simple names of a meta-annotation property ({@code RequestMethod.GET}
         *         or a list of them), in declaration order; empty when absent or not enum
         *         values of {@code enumFqcn}
         */
        List<String> metaEnumValues(String metaSimpleName, String property, String enumFqcn) {
            List<String> result = new ArrayList<>();
            MetaAnnotation meta = metadata.get(metaSimpleName);
            if (meta == null) {
                return result;
            }
            AnnotationValue value = meta.values.get(property);
            if (value == null) {
                return result;
            }
            if (value instanceof AnnotationValueList) {
                for (AnnotationValue item : ((AnnotationValueList) value).getValueList()) {
                    addEnumNames(item.toString(), enumFqcn, result);
                }
                return result;
            }
            addEnumNames(value.toString(), enumFqcn, result);
            return result;
        }

        /**
         * Collects the simple enum constant names referenced by the text form of an
         * annotation value: a single reference ({@code RequestMethod.GET}), or a rendered
         * array of references ({@code [GET, POST]} / {@code [RequestMethod.GET,
         * RequestMethod.POST]} as produced by the reflection loader)
         */
        private static void addEnumNames(String text, String enumFqcn, List<String> result) {
            if (text == null) {
                return;
            }
            text = text.trim();
            if (text.startsWith("[") && text.endsWith("]")) {
                String inner = text.substring(1, text.length() - 1).trim();
                if (inner.isEmpty()) {
                    return;
                }
                int start = 0;
                while (start <= inner.length()) {
                    int comma = inner.indexOf(',', start);
                    String item = (comma < 0 ? inner.substring(start) : inner.substring(start, comma)).trim();
                    if (!item.isEmpty()) {
                        addEnumNames(item, enumFqcn, result);
                    }
                    if (comma < 0) {
                        break;
                    }
                    start = comma + 1;
                }
                return;
            }
            int dot = text.lastIndexOf('.');
            if (dot <= 0) {
                return;
            }
            String owner = text.substring(0, dot);
            String constant = text.substring(dot + 1);
            if (simpleName(owner).equals(simpleName(enumFqcn))) {
                result.add(constant);
            }
        }

        /**
         * @return the self-declared {@code String} attributes in source order that are path
         *         carriers: named {@code value}/{@code path}, or aliasing the
         *         {@code RequestMapping} attribute {@code value}/{@code path}
         */
        List<String> pathProps() {
            List<String> result = new ArrayList<>();
            for (Attribute attribute : attributes.values()) {
                if (!"java.lang.String".equals(attribute.componentType) && !"String".equals(attribute.componentType)) {
                    continue;
                }
                boolean candidate = "value".equals(attribute.name) || "path".equals(attribute.name);
                if (!candidate) {
                    for (Map<String, String> target : nullSafe(attribute.aliasTargets)) {
                        if (!"RequestMapping".equals(simpleName(target.get("annotation")))) {
                            continue;
                        }
                        String member = target.get("member");
                        if (member == null || "value".equals(member) || "path".equals(member)) {
                            candidate = true;
                            break;
                        }
                    }
                }
                if (candidate) {
                    result.add(attribute.name);
                }
            }
            return result;
        }

        private static String componentType(String rawType) {
            return rawType != null && rawType.endsWith("[]")
                    ? rawType.substring(0, rawType.length() - 2) : rawType;
        }

        private static Kind classify(Map<String, MetaAnnotation> metadata) {
            for (MetaAnnotation meta : metadata.values()) {
                if (isEntryMeta(meta.type)) {
                    return Kind.ENTRY;
                }
            }
            return Kind.MAPPING;
        }

        private static boolean isEntryMeta(String type) {
            if (type == null) {
                return false;
            }
            String simple = simpleName(type);
            return REST_CONTROLLER.equals(type) || CONTROLLER.equals(type)
                    || "RestController".equals(simple) || "Controller".equals(simple);
        }

        private static AnnotationValue textValue(String text) {
            return new TextValue(text);
        }

        /**
         * @return the raw value of a zero-argument annotation member (arrays are returned
         *         as arrays so the text rendering can join elements by name), or
         *         {@code null} when the invocation fails (reflection values are best effort)
         */
        private static Object invoke(Method member, Object target) {
            try {
                return member.invoke(target);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * @return the annotation value holder for a raw reflection value, or {@code null};
         *         enum arrays are joined as {@code [GET]} because an array
         *         {@link Object#toString} would yield the identity hash code instead of
         *         the element names
         */
        private static AnnotationValue renderReflectionValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value.getClass().isArray()) {
                StringBuilder builder = new StringBuilder("[");
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(enumText(Array.get(value, i)));
                }
                return new TextValue(builder.append(']').toString());
            }
            return new TextValue(enumText(value));
        }

        /**
         * @return the text form of an enum constant with its fully qualified type
         *         ({@code org.springframework.web.bind.annotation.RequestMethod.GET}) so the
         *         enum reference detection works the same as for source parsed values;
         *         non enum values keep their {@link String#valueOf(Object)} form
         */
        private static String enumText(Object value) {
            if (value instanceof Enum<?>) {
                return value.getClass().getName() + '.' + value;
            }
            return String.valueOf(value);
        }

        private static String returnTypeValue(JavaMethod method) {
            try {
                JavaType type = method.getReturnType();
                if (type == null) {
                    return null;
                }
                String fullyQualified = type.getFullyQualifiedName();
                return fullyQualified == null || fullyQualified.isEmpty()
                        ? type.getValue() : fullyQualified;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static String typeName(JavaAnnotation meta) {
            try {
                JavaType type = meta.getType();
                String fullyQualified = type.getFullyQualifiedName();
                return fullyQualified == null || fullyQualified.isEmpty()
                        ? type.getValue() : fullyQualified;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static List<Map<String, String>> aliasTargetsOf(JavaMethod method) {
            List<Map<String, String>> targets = new ArrayList<>();
            for (JavaAnnotation annotation : nullSafe(method.getAnnotations())) {
                Map<String, String> target = targetOf(typeName(annotation), propertyMap(annotation));
                if (target != null) {
                    targets.add(target);
                }
            }
            return targets.isEmpty() ? null : targets;
        }

        private static List<Map<String, String>> aliasTargetsOf(Method method) {
            List<Map<String, String>> targets = new ArrayList<>();
            for (Annotation annotation : method.getAnnotations()) {
                Map<String, String> target = targetOf(annotation.annotationType().getName(),
                        reflectionProperties(annotation));
                if (target != null) {
                    targets.add(target);
                }
            }
            return targets.isEmpty() ? null : targets;
        }

        private static Map<String, String> targetOf(String annotationType,
                                                    Map<String, AnnotationValue> properties) {
            if (annotationType == null || !"AliasFor".equals(simpleName(annotationType))) {
                return null;
            }
            Map<String, String> target = new LinkedHashMap<>();
            for (Map.Entry<String, AnnotationValue> entry : properties.entrySet()) {
                target.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
            }
            return target;
        }

        private static Map<String, AnnotationValue> propertyMap(JavaAnnotation annotation) {
            Map<String, AnnotationValue> values = new LinkedHashMap<>();
            try {
                values.putAll(annotation.getPropertyMap());
            } catch (RuntimeException e) {
                // unreadable property map: treat as type-only
            }
            return values;
        }

        private static Map<String, AnnotationValue> reflectionProperties(Annotation annotation) {
            Map<String, AnnotationValue> values = new LinkedHashMap<>();
            try {
                for (Method member : annotation.annotationType().getDeclaredMethods()) {
                    if (member.getParameterCount() > 0) {
                        continue;
                    }
                    String key = member.getName();
                    if ("annotationType".equals(key) || "toString".equals(key)
                            || "hashCode".equals(key) || "equals".equals(key)) {
                        continue;
                    }
                    values.put(key, renderReflectionValue(invoke(member, annotation)));
                }
            } catch (RuntimeException e) {
                // best effort only
            }
            return values;
        }

        private static <T> List<T> nullSafe(List<T> list) {
            return list == null ? Collections.<T>emptyList() : list;
        }

    }

    /**
     * One meta-annotation of the definition: the resolved type and its property values
     * (qdox values as-is, reflection values stringified into {@link TextValue}).
     */
    static final class MetaAnnotation {

        final String type;
        final Map<String, AnnotationValue> values;

        MetaAnnotation(String type, Map<String, AnnotationValue> values) {
            this.type = type;
            this.values = values;
        }
    }

    /**
     * One annotation attribute (the annotation method) in source/declaration order.
     */
    static final class Attribute {

        final String name;
        final String rawType;
        final String componentType;
        final List<Map<String, String>> aliasTargets;

        Attribute(String name, String rawType, String componentType, List<Map<String, String>> aliasTargets) {
            this.name = name;
            this.rawType = rawType;
            this.componentType = componentType;
            this.aliasTargets = aliasTargets;
        }
    }

    /**
     * Plain string-backed annotation value used by the reflection loader (stringified enum
     * constants, class literals, ...); the derivation logic only needs the text form.
     */
    static final class TextValue implements AnnotationValue {

        private final String text;

        TextValue(String text) {
            this.text = text;
        }

        @Override
        public Object getParameterValue() {
            return text;
        }

        @Override
        public Object accept(com.thoughtworks.qdox.model.expression.ExpressionVisitor visitor) {
            return null;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    static String simpleName(String fullyQualifiedName) {
        if (fullyQualifiedName == null) {
            return null;
        }
        int index = fullyQualifiedName.lastIndexOf('.');
        return index < 0 ? fullyQualifiedName : fullyQualifiedName.substring(index + 1);
    }

    /**
     * @param stackTrace the throwable to render
     * @return its full stack trace as text (test helper for diagnosing loader failures)
     */
    static String stackTraceText(Throwable stackTrace) {
        StringWriter writer = new StringWriter();
        stackTrace.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
