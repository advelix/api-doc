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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ly.doc.builder.ProjectDocConfigBuilder;
import com.ly.doc.handler.IHeaderHandler;
import com.ly.doc.handler.IRequestMappingHandler;
import com.ly.doc.model.ApiConfig;
import com.ly.doc.model.ApiMethodDoc;
import com.ly.doc.model.ApiReqParam;
import com.ly.doc.model.annotation.EntryAnnotation;
import com.ly.doc.model.annotation.FrameworkAnnotations;
import com.ly.doc.model.annotation.MappingAnnotation;
import com.ly.doc.template.SpringBootDocBuildTemplate;
import com.ly.doc.utils.DocUtil;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.expression.AnnotationValue;
import com.thoughtworks.qdox.model.expression.AnnotationValueList;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import io.github.advelix.apidoc.internal.CustomAnnotationRegistry;
import io.github.advelix.apidoc.internal.CustomAnnotationsModel;
import io.github.advelix.apidoc.internal.CustomAnnotationsParser;

/**
 * Smart-doc build template for the {@value #FRAMEWORK} framework.
 *
 * <p>api-doc is an independent project that wraps the Smart-doc parsing engine through its
 * public template SPI; this class is NOT an official Smart-doc component. It extends the
 * standard Spring template and only adds one capability: registering user-declared combined
 * annotations (Spring-style {@code @AliasFor} meta-annotations) so that controllers written
 * with them are documented just like standard Spring MVC controllers.
 *
 * <p>The template is selected when {@code ApiConfig.framework} is set to
 * {@value #FRAMEWORK} by the api-doc Mojo; any other framework value (including the Smart-doc
 * default) keeps the stock behavior untouched.
 */
public class ApiDocSpringBootTemplate extends SpringBootDocBuildTemplate {

    /**
     * Framework identifier the api-doc Mojo activates for custom annotation support.
     * Deliberately different from every built-in Smart-doc framework name so this template
     * never competes with the stock templates.
     */
    public static final String FRAMEWORK = "advelix-spring";

    /**
     * Fallback lookup order for the path property when a mapping does not configure
     * {@code pathProps}; mirrors the standard Spring mapping registrations.
     */
    private static final List<String> DEFAULT_PATH_PROPS = Arrays.asList("value", "path");

    /**
     * Only the {@value #FRAMEWORK} framework is supported; the stock "spring" template keeps
     * matching its own framework value.
     *
     * @param framework framework name from the Smart-doc configuration
     * @return true only for {@value #FRAMEWORK} (case-insensitive)
     */
    @Override
    public boolean supportsFramework(String framework) {
        return FRAMEWORK.equalsIgnoreCase(framework);
    }

    /**
     * Starts from the standard Spring annotation registry and appends the combined annotations
     * published by the Mojo through {@link CustomAnnotationRegistry} (empty when the
     * {@code customAnnotations} parameter is absent, in which case the returned registry is
     * identical to the stock one).
     *
     * @return the merged annotation registry
     */
    @Override
    public FrameworkAnnotations registeredAnnotations() {
        FrameworkAnnotations annotations = super.registeredAnnotations();
        CustomAnnotationsModel model = CustomAnnotationRegistry.get();
        if (model == null) {
            return annotations;
        }
        appendEntryAnnotations(annotations, model.getEntryAnnotations());
        appendMappingAnnotations(annotations, model.getEntryAnnotations(), model.getMappings());
        return annotations;
    }

    /**
     * Registers class-level combined annotations as entry annotations. The stock matching
     * compares the annotation type's {@code getValue()} against the registry keys, which is
     * the fully qualified name when the import resolves and the simple name otherwise, so
     * both keys are added to stay robust in both cases.
     */
    private void appendEntryAnnotations(FrameworkAnnotations annotations, List<String> entries) {
        Map<String, EntryAnnotation> entryAnnotations = annotations.getEntryAnnotations();
        for (String fullyQualifiedName : entries) {
            String simple = CustomAnnotationsParser.simpleName(fullyQualifiedName);
            EntryAnnotation entry = EntryAnnotation.builder()
                    .setAnnotationName(simple)
                    .setAnnotationFullyName(fullyQualifiedName);
            putIfAbsent(entryAnnotations, fullyQualifiedName, entry);
            putIfAbsent(entryAnnotations, simple, entry);
        }
    }

    /**
     * Registers combined annotations as mapping annotations.
     *
     * <p>Method-level entries carry their HTTP method and path property lookup order.
     * Class-level entry annotations are additionally registered as mappings without a method
     * type so that a class-level path (for example an {@code @AliasFor} of
     * {@code RequestMapping#value()}) is picked up as the controller base URL.
     *
     * <p>Every entry is indexed under both its fully qualified name and its simple name
     * because Smart-doc resolves class-level annotations by {@code getValue()} while
     * method-level annotations are resolved by simple name.
     */
    private void appendMappingAnnotations(FrameworkAnnotations annotations, List<String> entries,
                                          List<CustomAnnotationsModel.Mapping> mappings) {
        Map<String, MappingAnnotation> mappingAnnotations = annotations.getMappingAnnotations();
        for (String fullyQualifiedName : entries) {
            registerMapping(mappingAnnotations, fullyQualifiedName, null, null,
                    DEFAULT_PATH_PROPS, null, null, null);
        }
        for (CustomAnnotationsModel.Mapping mapping : mappings) {
            registerMapping(mappingAnnotations, mapping.getAnnotation(), mapping.getMethodType(),
                    mapping.getMethodProp(), pathPropsOf(mapping), mapping.getParamsProp(),
                    mapping.getConsumesProp(), mapping.getProducesProp());
        }
    }

    /**
     * Registers a single mapping annotation under both the fully qualified and the simple
     * name key, without overwriting an existing (standard) registration.
     */
    private void registerMapping(Map<String, MappingAnnotation> mappingAnnotations,
                                 String fullyQualifiedName, String methodType, String methodProp,
                                 List<String> pathProps, String paramsProp,
                                 String consumesProp, String producesProp) {
        String simple = CustomAnnotationsParser.simpleName(fullyQualifiedName);
        MappingAnnotation mapping = MappingAnnotation.builder()
                .setAnnotationName(simple)
                .setAnnotationFullyName(fullyQualifiedName)
                .setPathProps(pathProps.toArray(new String[0]));
        if (methodType != null) {
            mapping.setMethodType(methodType);
        }
        if (methodProp != null) {
            mapping.setMethodProp(methodProp);
        }
        if (paramsProp != null) {
            mapping.setParamsProp(paramsProp);
        }
        if (consumesProp != null) {
            mapping.setConsumesProp(consumesProp);
        }
        if (producesProp != null) {
            mapping.setProducesProp(producesProp);
        }
        putIfAbsent(mappingAnnotations, fullyQualifiedName, mapping);
        putIfAbsent(mappingAnnotations, simple, mapping);
    }

    /**
     * Expands multi-value method mappings: a method annotated with a combined annotation
     * whose declaration carries {@code extraMethodTypes} (for example
     * {@code @RequestMapping(method = {GET, PUT})}) produces one additional
     * {@link ApiMethodDoc} per extra method, so the same path shows up under every verb
     * like in Swagger. Single-method documents pass through unchanged and the order
     * values are renumbered to a contiguous 1..n over the expanded list.
     *
     * @param cls     the entry class being built
     * @param methods the documents produced by the standard Spring flow, may be null
     * @return the expanded list, or the input as-is when no multi-value mapping applies
     */
    static List<ApiMethodDoc> expandMultiValueMethods(JavaClass cls, List<ApiMethodDoc> methods) {
        if (methods == null || methods.isEmpty() || cls == null) {
            return methods;
        }
        Map<String, List<String>> extrasByMethod = multiValueExtras(cls);
        if (extrasByMethod.isEmpty()) {
            return methods;
        }
        List<ApiMethodDoc> expanded = new ArrayList<ApiMethodDoc>(methods.size());
        for (ApiMethodDoc doc : methods) {
            expanded.add(doc);
            List<String> extras = doc.getName() == null ? null : extrasByMethod.get(doc.getName());
            if (extras == null) {
                continue;
            }
            int baseOrder = doc.getOrder();
            for (String extra : extras) {
                ApiMethodDoc copy = doc.clone();
                copy.setType(extra);
                copy.setMethodId(DocUtil.generateId(cls.getCanonicalName() + doc.getName()
                        + baseOrder + extra));
                expanded.add(copy);
            }
        }
        for (int i = 0; i < expanded.size(); i++) {
            expanded.get(i).setOrder(i + 1);
        }
        return expanded;
    }

    /**
     * Overrides the standard entry point building to add the multi-value method expansion
     * after the stock Spring flow.
     */
    @Override
    public List<ApiMethodDoc> buildEntryPointMethod(JavaClass cls, ApiConfig apiConfig,
                                                    ProjectDocConfigBuilder projectBuilder,
                                                    FrameworkAnnotations frameworkAnnotations,
                                                    List<ApiReqParam> configApiReqParams,
                                                    IRequestMappingHandler baseMappingHandler,
                                                    IHeaderHandler headerHandler) {
        List<ApiMethodDoc> methods = super.buildEntryPointMethod(cls, apiConfig, projectBuilder,
                frameworkAnnotations, configApiReqParams, baseMappingHandler, headerHandler);
        return expandMultiValueMethods(cls, methods);
    }

    /**
     * @return the extra HTTP methods per controller method name for every method that
     *         carries a multi-value mapping: either the annotation definition declares
     *         {@code extraMethodTypes} ({@code @RequestMapping(method = {GET, PUT})}
     *         meta-annotation) or the method uses a {@code methodProp} mapping with a
     *         multi-value method at the use site ({@code @RequestApi(method = {GET, PUT})})
     */
    private static Map<String, List<String>> multiValueExtras(JavaClass cls) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        CustomAnnotationsModel model = CustomAnnotationRegistry.get();
        if (model == null || model.getMappings().isEmpty()) {
            return result;
        }
        Map<String, CustomAnnotationsModel.Mapping> mappingBySimpleName = new LinkedHashMap<String, CustomAnnotationsModel.Mapping>();
        Map<String, CustomAnnotationsModel.Mapping> mappingByFqcn = new LinkedHashMap<String, CustomAnnotationsModel.Mapping>();
        for (CustomAnnotationsModel.Mapping mapping : model.getMappings()) {
            boolean multiValue = !mapping.getExtraMethodTypes().isEmpty() || mapping.getMethodProp() != null;
            if (!multiValue) {
                continue;
            }
            mappingBySimpleName.put(CustomAnnotationsParser.simpleName(mapping.getAnnotation()), mapping);
            mappingByFqcn.put(mapping.getAnnotation(), mapping);
        }
        if (mappingBySimpleName.isEmpty()) {
            return result;
        }
        for (JavaMethod method : safeMethods(cls)) {
            Set<String> extras = new LinkedHashSet<String>();
            for (JavaAnnotation annotation : safeAnnotations(method)) {
                CustomAnnotationsModel.Mapping mapping = annotationMapping(mappingByFqcn, mappingBySimpleName, annotation);
                if (mapping == null) {
                    continue;
                }
                if (!mapping.getExtraMethodTypes().isEmpty()) {
                    extras.addAll(mapping.getExtraMethodTypes());
                } else if (mapping.getMethodProp() != null) {
                    extras.addAll(useSiteMethodExtras(annotation, mapping.getMethodProp()));
                }
            }
            if (!extras.isEmpty()) {
                result.put(method.getName(), new ArrayList<String>(extras));
            }
        }
        return result;
    }

    /**
     * @return the HTTP methods declared at the use site in the mapping's method property
     *         beyond the first one (the engine uses the first value as the primary method)
     */
    private static List<String> useSiteMethodExtras(JavaAnnotation annotation, String methodProp) {
        List<String> result = new ArrayList<String>();
        Object value;
        try {
            value = annotation.getNamedParameter(methodProp);
        } catch (RuntimeException e) {
            return result;
        }
        if (value == null) {
            return result;
        }
        List<String> methods = new ArrayList<String>();
        if (value instanceof AnnotationValueList) {
            for (AnnotationValue item : ((AnnotationValueList) value).getValueList()) {
                methods.add(methodName(item));
            }
        } else if (value instanceof List) {
            // qdox exposes some property values as plain lists (not AnnotationValueList)
            for (Object item : (List<?>) value) {
                methods.add(methodName(item));
            }
        } else {
            methods.add(methodName(value));
        }
        for (int i = 1; i < methods.size(); i++) {
            if (methods.get(i) != null) {
                result.add(methods.get(i));
            }
        }
        return result;
    }

    /**
     * @return the simple HTTP method name for a {@code RequestMethod} reference
     *         ({@code RequestMethod.GET} becomes {@code GET}), or null for anything else
     */
    private static String methodName(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        int dot = text.lastIndexOf('.');
        if (dot <= 0 || dot == text.length() - 1) {
            return null;
        }
        String owner = text.substring(0, dot);
        String constant = text.substring(dot + 1);
        return CustomAnnotationsParser.simpleName(owner).equals("RequestMethod") ? constant : null;
    }

    private static CustomAnnotationsModel.Mapping annotationMapping(
            Map<String, CustomAnnotationsModel.Mapping> byFqcn,
            Map<String, CustomAnnotationsModel.Mapping> bySimpleName, JavaAnnotation annotation) {
        String type;
        try {
            type = annotation.getType().getValue();
        } catch (RuntimeException e) {
            return null;
        }
        if (type == null) {
            return null;
        }
        CustomAnnotationsModel.Mapping mapping = byFqcn.get(type);
        return mapping != null ? mapping : bySimpleName.get(CustomAnnotationsParser.simpleName(type));
    }

    private static List<JavaMethod> safeMethods(JavaClass cls) {
        try {
            return cls.getMethods() == null ? java.util.Collections.<JavaMethod>emptyList() : cls.getMethods();
        } catch (RuntimeException e) {
            return java.util.Collections.emptyList();
        }
    }

    private static List<JavaAnnotation> safeAnnotations(JavaMethod method) {
        try {
            return method.getAnnotations() == null ? java.util.Collections.<JavaAnnotation>emptyList()
                    : method.getAnnotations();
        } catch (RuntimeException e) {
            return java.util.Collections.emptyList();
        }
    }

    private static List<String> pathPropsOf(CustomAnnotationsModel.Mapping mapping) {
        List<String> pathProps = mapping.getPathProps();
        return pathProps.isEmpty() ? DEFAULT_PATH_PROPS : pathProps;
    }

    private static <V> void putIfAbsent(Map<String, V> map, String key, V value) {
        if (!map.containsKey(key)) {
            map.put(key, value);
        }
    }
}
