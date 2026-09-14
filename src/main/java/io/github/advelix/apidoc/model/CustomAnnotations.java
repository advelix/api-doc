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
package io.github.advelix.apidoc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the {@code api-doc.customAnnotations} plugin parameter.
 *
 * <p>Two complementary ways of declaring combined (Spring-style {@code @AliasFor}
 * meta-) annotations:
 *
 * <ul>
 * <li>{@link #annotations}: fully qualified annotation names only. The plugin inspects
 * the annotation definitions (project sources first, dependency classpath as fallback)
 * and derives everything else automatically: entry vs. mapping classification, HTTP
 * method, path property and so on.</li>
 * <li>{@link #declarations}: an optional explicit JSON document of the fixed shape
 * <pre>
 * {"entry": ["com.acme.web.ApiController"],
 *  "mappings": [
 *    {"annotation": "com.acme.web.GetApi", "methodType": "GET", "pathProps": ["value"]}
 *  ]}
 * </pre>
 * Use it for annotations whose definition cannot be inspected, or to override a value
 * the auto-derivation got wrong. For the same annotation name the explicit declaration
 * always wins.</li>
 * </ul>
 *
 * <p>Example (Maven plugin configuration):
 *
 * <pre>
 * <customAnnotations>
 *   <annotations>
 *     <annotation>com.acme.web.ApiController</annotation>
 *     <annotation>com.acme.web.GetApi</annotation>
 *   </annotations>
 *   &lt;!-- optional &lt;declarations&gt;{...}&lt;/declarations&gt; --&gt;
 * </customAnnotations>
 * </pre>
 *
 * <p>Both lists empty is the default: the generation behaves exactly like the standard
 * Spring flow.
 */
public class CustomAnnotations {

    /**
     * Fully qualified names of combined annotations to register. Each entry may name a
     * class-level entry annotation (meta-annotated with {@code @RestController} or
     * {@code @Controller}) or a method-level mapping annotation (meta-annotated with
     * {@code @RequestMapping}, or declaring a {@code method} property of its own such as
     * a combined {@code @RequestApi}). Blank entries are ignored.
     */
    private List<String> annotations = new ArrayList<>();

    /**
     * Optional explicit declarations in JSON, see the class-level documentation for the
     * shape. Wins over the auto-derived rules for the same annotation name.
     */
    private String declarations;

    /**
     * @return fully qualified names of combined annotations to register, never null
     */
    public List<String> getAnnotations() {
        return annotations;
    }

    /**
     * Sets the fully qualified names of combined annotations to register.
     *
     * @param annotations annotation names, may be null
     */
    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations == null ? new ArrayList<>() : annotations;
    }

    /**
     * @return explicit JSON declarations, or null when absent
     */
    public String getDeclarations() {
        return declarations;
    }

    /**
     * Sets the explicit JSON declarations.
     *
     * @param declarations JSON document, may be null
     */
    public void setDeclarations(String declarations) {
        this.declarations = declarations;
    }
}
