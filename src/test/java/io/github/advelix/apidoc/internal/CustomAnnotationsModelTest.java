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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CustomAnnotationsModel#merge(CustomAnnotationsModel, CustomAnnotationsModel)}.
 */
class CustomAnnotationsModelTest {

    private static final String CONTROLLER = "com.acme.web.ApiController";
    private static final String OTHER_CONTROLLER = "com.acme.web.OtherController";
    private static final String GET_API = "com.acme.web.GetApi";
    private static final String POST_API = "com.acme.web.PostApi";
    private static final String EXTRA_API = "com.acme.web.ExtraApi";

    @Test
    void mergingWithEmptyExplicitReturnsDerivedUnchanged() {
        CustomAnnotationsModel derived = model(
                Arrays.asList(CONTROLLER),
                Arrays.asList(mapping(GET_API, "GET")));

        CustomAnnotationsModel merged = CustomAnnotationsModel.merge(derived,
                new CustomAnnotationsModel(Collections.<String>emptyList(),
                        Collections.<CustomAnnotationsModel.Mapping>emptyList()));

        assertEquals(Arrays.asList(CONTROLLER), merged.getEntryAnnotations());
        assertSame(derived.getMappings().get(0), merged.getMappings().get(0));
    }

    @Test
    void explicitMappingReplacesDerivedMappingInPlace() {
        CustomAnnotationsModel derived = model(
                Arrays.asList(CONTROLLER),
                Arrays.asList(mapping(GET_API, "GET"), mapping(POST_API, "POST")));
        CustomAnnotationsModel explicit = model(
                Collections.<String>emptyList(),
                Arrays.asList(mapping(GET_API, "PUT")));

        CustomAnnotationsModel merged = CustomAnnotationsModel.merge(derived, explicit);

        List<String> annotations = annotationsOf(merged);
        assertEquals(Arrays.asList(GET_API, POST_API), annotations,
                "the derived position of the overridden mapping must be kept");
        assertEquals("PUT", merged.getMappings().get(0).getMethodType());
        assertEquals("POST", merged.getMappings().get(1).getMethodType());
    }

    @Test
    void entryUnionPutsExplicitFirstAndDeduplicates() {
        CustomAnnotationsModel derived = model(
                Arrays.asList(OTHER_CONTROLLER, CONTROLLER),
                Collections.<CustomAnnotationsModel.Mapping>emptyList());
        CustomAnnotationsModel explicit = model(
                Arrays.asList(CONTROLLER, EXTRA_API_ENTRY),
                Collections.<CustomAnnotationsModel.Mapping>emptyList());

        CustomAnnotationsModel merged = CustomAnnotationsModel.merge(derived, explicit);

        assertEquals(Arrays.asList(CONTROLLER, EXTRA_API_ENTRY, OTHER_CONTROLLER),
                merged.getEntryAnnotations());
    }

    @Test
    void explicitOnlyMappingIsAppended() {
        CustomAnnotationsModel derived = model(
                Collections.<String>emptyList(),
                Arrays.asList(mapping(GET_API, "GET")));
        CustomAnnotationsModel explicit = model(
                Collections.<String>emptyList(),
                Arrays.asList(mapping(EXTRA_API, "PATCH")));

        CustomAnnotationsModel merged = CustomAnnotationsModel.merge(derived, explicit);

        assertEquals(Arrays.asList(GET_API, EXTRA_API), annotationsOf(merged));
    }

    @Test
    void duplicateExplicitMappingsKeepFirstOccurrence() {
        CustomAnnotationsModel derived = model(
                Collections.<String>emptyList(),
                Arrays.asList(mapping(GET_API, "GET")));
        CustomAnnotationsModel explicit = model(
                Collections.<String>emptyList(),
                Arrays.asList(mapping(GET_API, "PUT"), mapping(GET_API, "DELETE")));

        CustomAnnotationsModel merged = CustomAnnotationsModel.merge(derived, explicit);

        assertEquals(1, merged.getMappings().size());
        assertEquals("PUT", merged.getMappings().get(0).getMethodType());
    }

    @Test
    void nullExplicitReturnsDerivedAndNullDerivedReturnsExplicit() {
        CustomAnnotationsModel derived = model(Arrays.asList(CONTROLLER),
                Arrays.asList(mapping(GET_API, "GET")));
        CustomAnnotationsModel explicit = model(Arrays.asList(OTHER_CONTROLLER),
                Arrays.asList(mapping(POST_API, "POST")));

        assertSame(derived, CustomAnnotationsModel.merge(derived, null));
        assertSame(explicit, CustomAnnotationsModel.merge(null, explicit));
        CustomAnnotationsModel empty = CustomAnnotationsModel.merge(null, null);
        assertTrueEmpty(empty);
    }

    @Test
    void mappingCarriesExtraMethodTypes() {
        CustomAnnotationsModel.Mapping mapping = new CustomAnnotationsModel.Mapping(
                GET_API, "GET", Collections.<String>emptyList(), null, null, null, null,
                Arrays.asList("PUT", "PATCH"));

        assertEquals(Arrays.asList("PUT", "PATCH"), mapping.getExtraMethodTypes());
        // the 7-arg constructor defaults extras to empty
        assertEquals(Collections.emptyList(),
                new CustomAnnotationsModel.Mapping(GET_API, "GET",
                        Collections.<String>emptyList(), null, null, null, null)
                        .getExtraMethodTypes());
    }

    private static final String EXTRA_API_ENTRY = "com.acme.web.ExtraController";

    private static CustomAnnotationsModel model(List<String> entries,
                                                List<CustomAnnotationsModel.Mapping> mappings) {
        return new CustomAnnotationsModel(entries, mappings);
    }

    private static CustomAnnotationsModel.Mapping mapping(String annotation, String methodType) {
        return new CustomAnnotationsModel.Mapping(annotation, methodType,
                Collections.<String>emptyList(), null, null, null, null);
    }

    private static List<String> annotationsOf(CustomAnnotationsModel model) {
        List<String> names = new java.util.ArrayList<String>();
        for (CustomAnnotationsModel.Mapping mapping : model.getMappings()) {
            names.add(mapping.getAnnotation());
        }
        return names;
    }

    private static void assertTrueEmpty(CustomAnnotationsModel model) {
        assertEquals(0, model.getEntryAnnotations().size());
        assertEquals(0, model.getMappings().size());
    }
}
