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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed form of the {@code api-doc.customAnnotations} parameter.
 *
 * <p>The model is a plain value object: entry annotations are declared as fully qualified
 * names, method-level mapping annotations carry the HTTP method and the property names used
 * to extract the path (and optionally the params/consumes/produces declarations).
 */
public final class CustomAnnotationsModel {

    private final List<String> entryAnnotations;
    private final List<Mapping> mappings;

    /**
     * Creates a new model.
     *
     * @param entryAnnotations fully qualified names of class-level entry (controller) annotations
     * @param mappings         method-level mapping annotations
     */
    public CustomAnnotationsModel(List<String> entryAnnotations, List<Mapping> mappings) {
        this.entryAnnotations = Collections.unmodifiableList(new ArrayList<>(entryAnnotations));
        this.mappings = Collections.unmodifiableList(new ArrayList<>(mappings));
    }

    /**
     * @return fully qualified names of class-level entry annotations, never null
     */
    public List<String> getEntryAnnotations() {
        return entryAnnotations;
    }

    /**
     * @return method-level mapping annotations, never null
     */
    public List<Mapping> getMappings() {
        return mappings;
    }

    /**
     * Merges an auto-derived model with an explicit declaration model. Explicit
     * declarations always win:
     * <ul>
     * <li>entry annotations form the union with the explicit entries first, deduplicated
     *     while keeping order;</li>
     * <li>a derived mapping whose annotation is declared explicitly is replaced by the
     *     explicit mapping in place (the derived position is kept);</li>
     * <li>explicit mappings whose annotation was not derived are appended at the end;</li>
     * <li>duplicate explicit mappings keep the first occurrence.</li>
     * </ul>
     *
     * @param derived  the auto-derived model, may be null
     * @param explicit the explicitly declared model, may be null
     * @return the merged model, never null
     */
    public static CustomAnnotationsModel merge(CustomAnnotationsModel derived,
                                               CustomAnnotationsModel explicit) {
        if (derived == null) {
            return explicit == null ? new CustomAnnotationsModel(new ArrayList<String>(),
                    new ArrayList<Mapping>()) : explicit;
        }
        if (explicit == null) {
            return derived;
        }
        List<String> entries = new ArrayList<String>();
        for (String entry : explicit.getEntryAnnotations()) {
            if (!entries.contains(entry)) {
                entries.add(entry);
            }
        }
        for (String entry : derived.getEntryAnnotations()) {
            if (!entries.contains(entry)) {
                entries.add(entry);
            }
        }
        Map<String, Mapping> explicitByAnnotation = new LinkedHashMap<String, Mapping>();
        for (Mapping mapping : explicit.getMappings()) {
            if (!explicitByAnnotation.containsKey(mapping.getAnnotation())) {
                explicitByAnnotation.put(mapping.getAnnotation(), mapping);
            }
        }
        List<Mapping> mappings = new ArrayList<Mapping>();
        Set<String> usedExplicit = new HashSet<String>();
        for (Mapping mapping : derived.getMappings()) {
            Mapping override = explicitByAnnotation.get(mapping.getAnnotation());
            if (override != null) {
                mappings.add(override);
                usedExplicit.add(mapping.getAnnotation());
            } else {
                mappings.add(mapping);
            }
        }
        for (Mapping mapping : explicitByAnnotation.values()) {
            if (!usedExplicit.contains(mapping.getAnnotation())) {
                mappings.add(mapping);
            }
        }
        return new CustomAnnotationsModel(entries, mappings);
    }

    /**
     * Method-level combined annotation declaration.
     */
    public static final class Mapping {

        private final String annotation;
        private final String methodType;
        private final List<String> pathProps;
        private final String methodProp;
        private final String paramsProp;
        private final String consumesProp;
        private final String producesProp;
        private final List<String> extraMethodTypes;

        /**
         * Creates a new mapping declaration.
         *
         * @param annotation     fully qualified name of the annotation, required
         * @param methodType     HTTP method (GET/POST/...), required for method-level annotations
         * @param pathProps      property names holding the path, in lookup order; empty means
         *                       the default order {@code value, path}
         * @param methodProp     optional property holding a dynamic HTTP method
         * @param paramsProp     optional property holding parameter conditions
         * @param consumesProp   optional property holding the consumes media type
         * @param producesProp   optional property holding the produces media type
         */
        public Mapping(String annotation, String methodType, List<String> pathProps, String methodProp,
                       String paramsProp, String consumesProp, String producesProp) {
            this(annotation, methodType, pathProps, methodProp, paramsProp, consumesProp, producesProp,
                    Collections.<String>emptyList());
        }

        /**
         * Creates a new mapping declaration with extra HTTP methods for a multi-value
         * method declaration (for example {@code @RequestMapping(method = {GET, PUT})}).
         *
         * @param annotation       fully qualified name of the annotation, required
         * @param methodType       primary HTTP method, required for method-level annotations
         * @param pathProps        property names holding the path, in lookup order; empty means
         *                         the default order {@code value, path}
         * @param methodProp       optional property holding a dynamic HTTP method
         * @param paramsProp       optional property holding parameter conditions
         * @param consumesProp     optional property holding the consumes media type
         * @param producesProp     optional property holding the produces media type
         * @param extraMethodTypes additional HTTP methods besides {@code methodType}, in
         *                         declaration order; empty for single-value mappings
         */
        public Mapping(String annotation, String methodType, List<String> pathProps, String methodProp,
                       String paramsProp, String consumesProp, String producesProp,
                       List<String> extraMethodTypes) {
            this.annotation = annotation;
            this.methodType = methodType;
            this.pathProps = Collections.unmodifiableList(new ArrayList<>(pathProps));
            this.methodProp = methodProp;
            this.paramsProp = paramsProp;
            this.consumesProp = consumesProp;
            this.producesProp = producesProp;
            this.extraMethodTypes = Collections.unmodifiableList(new ArrayList<>(extraMethodTypes));
        }

        /**
         * @return fully qualified annotation name, never null
         */
        public String getAnnotation() {
            return annotation;
        }

        /**
         * @return HTTP method or null when resolved through {@link #getMethodProp()}
         */
        public String getMethodType() {
            return methodType;
        }

        /**
         * @return path property names in lookup order, never null (possibly empty)
         */
        public List<String> getPathProps() {
            return pathProps;
        }

        /**
         * @return property holding a dynamic HTTP method, or null
         */
        public String getMethodProp() {
            return methodProp;
        }

        /**
         * @return property holding parameter conditions, or null
         */
        public String getParamsProp() {
            return paramsProp;
        }

        /**
         * @return property holding the consumes media type, or null
         */
        public String getConsumesProp() {
            return consumesProp;
        }

        /**
         * @return property holding the produces media type, or null
         */
        public String getProducesProp() {
            return producesProp;
        }

        /**
         * @return additional HTTP methods for a multi-value method declaration, in
         *         declaration order, never null (possibly empty)
         */
        public List<String> getExtraMethodTypes() {
            return extraMethodTypes;
        }
    }
}
