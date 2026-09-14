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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CustomAnnotationsParserTest {

    private static final String VALID = "{\"entry\":[\"com.acme.web.ApiController\"],"
            + "\"mappings\":[{\"annotation\":\"com.acme.web.GetApi\",\"methodType\":\"GET\",\"pathProps\":[\"value\"]},"
            + "{\"annotation\":\"com.acme.web.PostApi\",\"methodType\":\"POST\"}]}";

    @Test
    void parsesValidDocument() {
        CustomAnnotationsModel model = CustomAnnotationsParser.parse(VALID);

        assertEquals(1, model.getEntryAnnotations().size());
        assertEquals("com.acme.web.ApiController", model.getEntryAnnotations().get(0));
        assertEquals(2, model.getMappings().size());

        CustomAnnotationsModel.Mapping get = model.getMappings().get(0);
        assertEquals("com.acme.web.GetApi", get.getAnnotation());
        assertEquals("GET", get.getMethodType());
        assertEquals(java.util.Arrays.asList("value"), get.getPathProps());
        assertEquals(null, get.getMethodProp());
        assertEquals(null, get.getParamsProp());
        assertEquals(null, get.getConsumesProp());
        assertEquals(null, get.getProducesProp());

        CustomAnnotationsModel.Mapping post = model.getMappings().get(1);
        assertEquals("POST", post.getMethodType());
        assertEquals(0, post.getPathProps().size());
    }

    @Test
    void acceptsEntryOnlyDocument() {
        CustomAnnotationsModel model = CustomAnnotationsParser.parse(
                "{\"entry\":[\"com.acme.web.ApiController\"]}");

        assertEquals(1, model.getEntryAnnotations().size());
        assertEquals(0, model.getMappings().size());
    }

    @Test
    void normalizesMethodTypeCaseAndOptionalProps() {
        CustomAnnotationsModel model = CustomAnnotationsParser.parse(
                "{\"mappings\":[{\"annotation\":\"com.acme.web.PutApi\",\"methodType\":\"put\","
                        + "\"methodProp\":\"httpMethod\",\"paramsProp\":\"params\","
                        + "\"consumesProp\":\"consumes\",\"producesProp\":\"produces\"}]}");

        CustomAnnotationsModel.Mapping put = model.getMappings().get(0);
        assertEquals("PUT", put.getMethodType());
        assertEquals("httpMethod", put.getMethodProp());
        assertEquals("params", put.getParamsProp());
        assertEquals("consumes", put.getConsumesProp());
        assertEquals("produces", put.getProducesProp());
    }

    @Test
    void acceptsMethodPropWithoutMethodType() {
        CustomAnnotationsModel model = CustomAnnotationsParser.parse(
                "{\"mappings\":[{\"annotation\":\"com.acme.web.AnyApi\",\"methodProp\":\"httpMethod\"}]}");

        assertEquals(null, model.getMappings().get(0).getMethodType());
        assertEquals("httpMethod", model.getMappings().get(0).getMethodProp());
    }

    @Test
    void rejectsBlankDocument() {
        assertThrows(IllegalArgumentException.class, () -> CustomAnnotationsParser.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> CustomAnnotationsParser.parse(null));
    }

    @Test
    void RejectsUnknownTopLevelKey() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse("{\"entry\":[],\"entries\":[]}"));

        assertTrue(e.getMessage().contains("unknown top-level key"), e.getMessage());
    }

    @Test
    void rejectsEmptyObject() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse("{}"));
        assertTrue(e.getMessage().contains("at least one of"), e.getMessage());
    }

    @Test
    void rejectsUnknownMappingKey() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse(
                        "{\"mappings\":[{\"annotation\":\"a.B\",\"methodType\":\"GET\",\"bogus\":1}]}"));

        assertTrue(e.getMessage().contains("bogus"), e.getMessage());
    }

    @Test
    void rejectsMissingAnnotationName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse("{\"mappings\":[{\"methodType\":\"GET\"}]}"));

        assertTrue(e.getMessage().contains("annotation"), e.getMessage());
    }

    @Test
    void rejectsMissingMethodTypeAndMethodProp() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse(
                        "{\"mappings\":[{\"annotation\":\"com.acme.web.GetApi\"}]}"));

        assertTrue(e.getMessage().contains("methodType"), e.getMessage());
    }

    @Test
    void rejectsUnknownMethodType() {
        assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse(
                        "{\"mappings\":[{\"annotation\":\"com.acme.web.GetApi\",\"methodType\":\"FETCH\"}]}"));
    }

    @Test
    void rejectsNonStringEntry() {
        assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse("{\"entry\":[42]}"));
    }

    @Test
    void rejectsMalformedJsonWithPosition() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CustomAnnotationsParser.parse("{\"entry\":[\"a.B\",}"));

        assertTrue(e.getMessage().contains("position"), e.getMessage());
    }

    @Test
    void simpleNameExtractsLastSegment() {
        assertEquals("GetApi", CustomAnnotationsParser.simpleName("com.acme.web.GetApi"));
        assertEquals("TopLevel", CustomAnnotationsParser.simpleName("TopLevel"));
    }
}
