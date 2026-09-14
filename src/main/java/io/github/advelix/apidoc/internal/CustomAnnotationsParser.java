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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the {@code api-doc.customAnnotations} parameter value, a JSON document of the fixed
 * shape
 *
 * <pre>
 * {
 *   "entry": ["com.acme.web.ApiController"],
 *   "mappings": [
 *     {"annotation": "com.acme.web.GetApi", "methodType": "GET", "pathProps": ["value"]}
 *   ]
 * }
 * </pre>
 *
 * <p>The parser is deliberately hand-rolled instead of pulling in a JSON library: the document
 * shape is fixed and small, keeping the plugin dependency-free beyond Smart-doc itself.
 * Structural problems are reported as {@link IllegalArgumentException} with the offending
 * position so the build fails with an actionable message.
 */
public final class CustomAnnotationsParser {

    private static final String KEY_ENTRY = "entry";
    private static final String KEY_MAPPINGS = "mappings";

    private static final String KEY_ANNOTATION = "annotation";
    private static final String KEY_METHOD_TYPE = "methodType";
    private static final String KEY_PATH_PROPS = "pathProps";
    private static final String KEY_METHOD_PROP = "methodProp";
    private static final String KEY_PARAMS_PROP = "paramsProp";
    private static final String KEY_CONSUMES_PROP = "consumesProp";
    private static final String KEY_PRODUCES_PROP = "producesProp";

    private static final List<String> KNOWN_MAPPING_KEYS = Arrays.asList(
            KEY_ANNOTATION, KEY_METHOD_TYPE, KEY_PATH_PROPS,
            KEY_METHOD_PROP, KEY_PARAMS_PROP, KEY_CONSUMES_PROP, KEY_PRODUCES_PROP);

    private final String text;
    private int pos;

    private CustomAnnotationsParser(String text) {
        this.text = text;
        this.pos = 0;
    }

    /**
     * Parses a {@code customAnnotations} document.
     *
     * @param json the JSON document, must not be blank
     * @return the parsed model
     * @throws IllegalArgumentException when the document is not valid or violates the shape
     */
    public static CustomAnnotationsModel parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("customAnnotations is empty");
        }
        CustomAnnotationsParser parser = new CustomAnnotationsParser(json);
        Map<String, Object> root = parser.parseObject();
        parser.skipWhitespace();
        if (parser.pos != parser.text.length()) {
            throw parser.error("unexpected trailing content");
        }

        for (String key : root.keySet()) {
            if (!KEY_ENTRY.equals(key) && !KEY_MAPPINGS.equals(key)) {
                throw new IllegalArgumentException("unknown top-level key \"" + key + "\"; "
                        + "expected only \"" + KEY_ENTRY + "\" and/or \"" + KEY_MAPPINGS + "\"");
            }
        }
        if (!root.containsKey(KEY_ENTRY) && !root.containsKey(KEY_MAPPINGS)) {
            throw new IllegalArgumentException("customAnnotations must contain at least one of "
                    + "the keys \"" + KEY_ENTRY + "\" or \"" + KEY_MAPPINGS + "\", got: " + root.keySet());
        }

        List<String> entry = new ArrayList<String>();
        Object entryValue = root.get(KEY_ENTRY);
        if (entryValue != null) {
            entry.addAll(parser.stringArray(entryValue, "\"" + KEY_ENTRY + "\""));
        }

        List<CustomAnnotationsModel.Mapping> mappings = new ArrayList<CustomAnnotationsModel.Mapping>();
        Object mappingsValue = root.get(KEY_MAPPINGS);
        if (mappingsValue != null) {
            List<Object> rawMappings = parser.objectArray(mappingsValue, "\"" + KEY_MAPPINGS + "\"");
            for (Object rawMapping : rawMappings) {
                mappings.add(parser.parseMapping((Map<String, Object>) rawMapping));
            }
        }
        return new CustomAnnotationsModel(entry, mappings);
    }

    /**
     * @return the simple name of a fully qualified class name, e.g.
     *         {@code com.acme.web.GetApi} becomes {@code GetApi}
     */
    public static String simpleName(String fullyQualifiedName) {
        int index = fullyQualifiedName.lastIndexOf('.');
        return index < 0 ? fullyQualifiedName : fullyQualifiedName.substring(index + 1);
    }

    private CustomAnnotationsModel.Mapping parseMapping(Map<String, Object> raw) {
        for (String key : raw.keySet()) {
            if (!KNOWN_MAPPING_KEYS.contains(key)) {
                throw new IllegalArgumentException("unknown key \"" + key + "\" in mappings[]; "
                        + "expected one of " + KNOWN_MAPPING_KEYS);
            }
        }
        Object annotation = raw.get(KEY_ANNOTATION);
        if (!(annotation instanceof String) || ((String) annotation).trim().isEmpty()) {
            throw new IllegalArgumentException("mappings[] entry requires a non-empty string "
                    + "key \"" + KEY_ANNOTATION + "\": " + raw);
        }
        String methodType = optionalString(raw, KEY_METHOD_TYPE);
        if (methodType != null) {
            String normalized = methodType.toUpperCase();
            if (!Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(normalized)) {
                throw new IllegalArgumentException("mappings[\"" + annotation + "\"].methodType must be one of "
                        + "GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS, got: " + methodType);
            }
            methodType = normalized;
        }
        if (methodType == null && raw.get(KEY_METHOD_PROP) == null) {
            throw new IllegalArgumentException("mappings[\"" + annotation + "\"] requires either "
                    + "\"methodType\" or \"methodProp\"");
        }
        List<String> pathProps = new ArrayList<String>();
        Object pathPropsValue = raw.get(KEY_PATH_PROPS);
        if (pathPropsValue != null) {
            pathProps.addAll(stringArray(pathPropsValue, "\"" + KEY_PATH_PROPS + "\" in mappings[\""
                    + annotation + "\"]"));
        }
        return new CustomAnnotationsModel.Mapping((String) annotation, methodType, pathProps,
                optionalString(raw, KEY_METHOD_PROP), optionalString(raw, KEY_PARAMS_PROP),
                optionalString(raw, KEY_CONSUMES_PROP), optionalString(raw, KEY_PRODUCES_PROP));
    }

    private String optionalString(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("key \"" + key + "\" must be a non-empty string: " + raw);
        }
        return (String) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringArray(Object value, String context) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(context + " must be an array of strings: " + value);
        }
        List<Object> items = (List<Object>) value;
        List<String> result = new ArrayList<String>(items.size());
        for (Object item : items) {
            if (!(item instanceof String) || ((String) item).trim().isEmpty()) {
                throw new IllegalArgumentException(context + " entries must be non-empty strings: "
                        + items);
            }
            result.add((String) item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> objectArray(Object value, String context) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(context + " must be an array of objects: " + value);
        }
        List<Object> items = (List<Object>) value;
        for (Object item : items) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException(context + " entries must be objects: " + items);
            }
        }
        return items;
    }

    // ---- minimal JSON reader (objects, arrays, strings, numbers, literals) ----

    private Map<String, Object> parseObject() {
        skipWhitespace();
        expect('{');
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            object.put(key, parseValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return object;
            }
            if (c != ',') {
                throw error("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> parseArray() {
        skipWhitespace();
        expect('[');
        List<Object> array = new ArrayList<Object>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            array.add(parseValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return array;
            }
            if (c != ',') {
                throw error("expected ',' or ']' in array");
            }
        }
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        if (c == '{') {
            return parseObject();
        }
        if (c == '[') {
            return parseArray();
        }
        if (c == '"') {
            return parseString();
        }
        if (c == 't' || c == 'f' || c == 'n') {
            return parseLiteral();
        }
        return parseNumber();
    }

    private String parseString() {
        skipWhitespace();
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char escape = next();
                switch (escape) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw error("invalid escape character '\\" + escape + "'");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object parseLiteral() {
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw error("unexpected character '" + peek() + "'");
    }

    private Object parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < text.length() && "0123456789+-.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String number = text.substring(start, pos);
        if (number.isEmpty()) {
            throw error("unexpected character '" + peek() + "'");
        }
        return new Double(Double.parseDouble(number));
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw error("expected '" + expected + "' but found '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos + " in: " + text);
    }
}
