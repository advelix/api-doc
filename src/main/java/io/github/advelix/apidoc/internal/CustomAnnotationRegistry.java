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

/**
 * Process-wide handoff between the Mojo and the custom {@code IDocBuildTemplate}.
 *
 * <p>The template is discovered through {@code ServiceLoader}, which requires a public
 * no-arg constructor, so the parsed custom annotation model cannot be injected through the
 * constructor. The Mojo therefore publishes the model here right before calling the
 * Smart-doc builder and clears it afterwards. Mojos are {@code threadSafe = false} and
 * generation runs sequentially inside one JVM, so a single static slot is sufficient; the
 * volatile write ensures the value is visible to the template instance created by the
 * ServiceLoader during the same generation run.
 */
public final class CustomAnnotationRegistry {

    private static volatile CustomAnnotationsModel model;

    private CustomAnnotationRegistry() {
    }

    /**
     * Publishes the parsed model for the current generation run.
     *
     * @param parsed model to hand to the template, or null to reset
     */
    public static void set(CustomAnnotationsModel parsed) {
        model = parsed;
    }

    /**
     * @return the model published for the current run, or null when no custom annotations
     *         are configured (template then behaves exactly like the standard Spring template)
     */
    public static CustomAnnotationsModel get() {
        return model;
    }

    /**
     * Removes the published model. Always called from the Mojo's finally block.
     */
    public static void clear() {
        model = null;
    }
}
