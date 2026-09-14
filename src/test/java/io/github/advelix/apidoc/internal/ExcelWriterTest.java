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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiExceptionStatus;
import com.ly.doc.model.ApiMethodDoc;
import com.ly.doc.model.ApiParam;
import com.ly.doc.model.ApiReqParam;
import com.ly.doc.model.ApiSchema;
import com.ly.doc.model.request.ApiRequestExample;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link ExcelWriter}: the workbook is written to a temporary file and read
 * back with POI; cell values are asserted, bytes are not.
 */
class ExcelWriterTest {

    private static final String LINE = System.lineSeparator();

    @TempDir
    Path tempDir;

    @Test
    void writesHeaderRowWithExactColumnNamesAndOrder() throws Exception {
        File file = write(goldenSchema());
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            String[] expected = {
                    "#", "Method", "Path", "URL", "OperationId", "Summary", "Controller", "Tag",
                    "Content-Type", "Deprecated", "Path \u53c2\u6570", "Query \u53c2\u6570",
                    "\u8bf7\u6c42\u5934", "Body \u53c2\u6570", "\u54cd\u5e94\u5b57\u6bb5", "\u8bf7\u6c42\u793a\u4f8b"
            };
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], cellText(header, i));
            }
            assertTrue(isBold(wb, header.getCell(0)), "header must be bold");
        }
    }

    @Test
    void writesGoldenValuesForUserController() throws Exception {
        File file = write(goldenSchema());
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            Row getUser = sheet.getRow(1);
            assertEquals("1", cellText(getUser, 0));
            assertEquals("GET", cellText(getUser, 1));
            assertEquals("/api/users/{id}", cellText(getUser, 2));
            assertEquals("https://api.example.com/api/users/{id}", cellText(getUser, 3));
            assertEquals("getUser", cellText(getUser, 4));
            assertEquals("Get a single user by id.", cellText(getUser, 5));
            assertEquals("UserController", cellText(getUser, 6));
            assertEquals("UserController", cellText(getUser, 7));
            assertEquals("application/x-www-form-urlencoded", cellText(getUser, 8));
            assertEquals("false", cellText(getUser, 9));
            // int64 converts to "number" through DocUtil, matching the JSON schema type
            assertEquals("id (number) *必填* = 0 — user id", cellText(getUser, 10));
            assertEquals("", cellText(getUser, 11));
            assertEquals("", cellText(getUser, 12));
            assertEquals("", cellText(getUser, 13));
            String response = cellText(getUser, 14);
            String[] responseLines = response.split(LINE, -1);
            assertEquals(4, responseLines.length);
            assertEquals("id (number) = 0 — User identifier.", responseLines[0]);
            assertEquals("status (string) = ACTIVE — Account status. (See: Account status.)",
                    responseLines[3]);
            assertEquals("curl -X GET -k -i 'https://api.example.com/api/users/{id}'",
                    cellText(getUser, 15));

            Row createUser = sheet.getRow(2);
            assertEquals("2", cellText(createUser, 0));
            assertEquals("POST", cellText(createUser, 1));
            assertEquals("/api/users", cellText(createUser, 2));
            assertEquals("createUser", cellText(createUser, 4));
            assertEquals("application/json", cellText(createUser, 8));
            String body = cellText(createUser, 13);
            String[] bodyLines = body.split(LINE, -1);
            assertEquals(2, bodyLines.length);
            assertEquals("name (string) — Display name.", bodyLines[0]);
            assertEquals("email (string) — E-mail address.", bodyLines[1]);
            assertEquals("{\n  \"name\": \"\",\n  \"email\": \"\"\n}",
                    cellText(createUser, 15));
        }
    }

    @Test
    void indentsNestedChildrenAndJoinsMultipleParamsWithLineBreaks() throws Exception {
        ApiSchema<ApiDoc> schema = nestedSchema();
        File file = write(schema);
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            Row row = sheet.getRow(1);
            String body = cellText(row, 13);
            String[] lines = body.split(LINE, -1);
            assertEquals(3, lines.length);
            assertEquals("user (object) *必填* — payload", lines[0]);
            assertEquals("  name (string) — name", lines[1]);
            assertEquals("    id (number) = 0 — deep", lines[2]);
            // multi-line cell must be created with wrapText
            assertTrue(row.getCell(13).getCellStyle().getWrapText(), "multi-line cell must wrap");
        }
    }

    @Test
    void writesRequestHeadersColumn() throws Exception {
        ApiMethodDoc method = method("GET", "/h", "op");
        List<ApiReqParam> headers = new ArrayList<ApiReqParam>();
        ApiReqParam auth = new ApiReqParam();
        auth.setName("Authorization");
        auth.setType("string");
        auth.setRequired(true);
        auth.setDesc("Bearer token<br/>v2");
        headers.add(auth);
        method.setRequestHeaders(headers);
        File file = write(schemaOf(docOf(method)));
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            assertEquals("Authorization (string) *必填* — Bearer token v2",
                    cellText(sheet.getRow(1), 12));
        }
    }

    @Test
    void emptySchemaWritesOnlyHeaderRow() throws Exception {
        ApiSchema<ApiDoc> schema = new ApiSchema<ApiDoc>();
        schema.setApiDatas(new ArrayList<ApiDoc>());
        schema.setApiExceptionStatuses(new ArrayList<ApiExceptionStatus>());
        File file = write(schema);
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            assertEquals(0, sheet.getLastRowNum());
            assertEquals(16, sheet.getRow(0).getLastCellNum());
        }
    }

    @Test
    void flattensChildrenApiDocsAfterParentMethods() throws Exception {
        ApiMethodDoc parentMethod = method("GET", "/p", "parentOp");
        ApiDoc parent = docOf(parentMethod);
        parent.setOrder(1);
        ApiMethodDoc childMethod = method("POST", "/c", "childOp");
        ApiDoc child = docOf(childMethod);
        child.setOrder(1);
        child.setName("ChildController");
        child.setAlias("ChildController");
        childMethod.setClazzDoc(child);
        parent.setChildrenApiDocs(Collections.singletonList(child));

        List<ApiMethodDoc> operations = ExcelWriter.collectOperations(schemaOf(parent));
        assertEquals(2, operations.size());
        assertEquals("parentOp", operations.get(0).getName());
        assertEquals("childOp", operations.get(1).getName());

        File file = write(schemaOf(parent));
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            assertEquals("parentOp", cellText(sheet.getRow(1), 4));
            assertEquals("ChildController", cellText(sheet.getRow(2), 7));
            assertEquals("childOp", cellText(sheet.getRow(2), 4));
        }
    }

    @Test
    void writesErrorCodesRegionOnlyWhenPresent() throws Exception {
        // without exception statuses: no second region
        File plain = write(goldenSchema());
        try (Workbook wb = open(plain)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            assertEquals(2, sheet.getLastRowNum(), "only header + 2 operations expected");
        }

        // with exception statuses: blank row, bold header, one data row
        ApiSchema<ApiDoc> schema = goldenSchema();
        ApiExceptionStatus notFound = new ApiExceptionStatus();
        notFound.setStatus("404");
        notFound.setDesc("Not found.");
        notFound.setDetail("The resource is missing.");
        notFound.setResponseUsage("plain text");
        schema.setApiExceptionStatuses(Collections.singletonList(notFound));
        File file = write(schema);
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheet(ExcelWriter.SHEET_NAME);
            // rows: 0 header, 1-2 operations, 3 blank separator (no cells -> null),
            // 4 error header, 5 data
            assertEquals(5, sheet.getLastRowNum());
            Row separator = sheet.getRow(3);
            assertTrue(separator == null || separator.getCell(0) == null,
                    "row 3 must be the blank separator");
            Row header = sheet.getRow(4);
            assertTrue(isBold(wb, header.getCell(0)));
            assertEquals("\u72b6\u6001\u7801", cellText(header, 0));
            assertEquals("\u63cf\u8ff0", cellText(header, 1));
            assertEquals("\u8be6\u60c5", cellText(header, 2));
            assertEquals("\u54cd\u5e94", cellText(header, 3));
            Row data = sheet.getRow(5);
            assertEquals("404", cellText(data, 0));
            assertEquals("Not found.", cellText(data, 1));
            assertEquals("The resource is missing.", cellText(data, 2));
            assertEquals("plain text", cellText(data, 3));
        }
    }

    @Test
    void truncatesOverlongCellValues() {
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 33000; i++) {
            longValue.append('a');
        }
        String truncated = ExcelWriter.truncate(longValue.toString());
        assertNotNull(truncated);
        assertEquals(32000, truncated.length());
        assertTrue(truncated.endsWith("\u2026"));
        assertEquals("short", ExcelWriter.truncate("short"));
    }

    // ---- fixtures ----

    /** Mirrors the S1 probe golden values of examples/spring-api (no custom annotations). */
    private ApiSchema<ApiDoc> goldenSchema() {
        ApiDoc userController = new ApiDoc();
        userController.setName("UserController");
        userController.setAlias("UserController");
        userController.setDesc("User endpoints.");
        userController.setOrder(1);

        ApiMethodDoc getUser = method("GET", "/api/users/{id}", "getUser");
        getUser.setUrl("https://api.example.com/api/users/{id}");
        getUser.setDesc("Get a single user by id.");
        getUser.setContentType("application/x-www-form-urlencoded");
        ApiParam id = param("id", "int64", true, "0", "user id");
        getUser.setPathParams(Collections.singletonList(id));
        getUser.setQueryParams(new ArrayList<ApiParam>());
        getUser.setRequestParams(new ArrayList<ApiParam>());
        getUser.setResponseParams(userResponseParams());
        getUser.setRequestHeaders(new ArrayList<ApiReqParam>());
        ApiRequestExample getExample = new ApiRequestExample();
        getExample.setJson(false);
        getExample.setExampleBody("curl -X GET -k -i 'https://api.example.com/api/users/{id}'");
        getUser.setRequestExample(getExample);

        ApiMethodDoc createUser = method("POST", "/api/users", "createUser");
        createUser.setUrl("https://api.example.com/api/users");
        createUser.setDesc("Create a new user.");
        createUser.setContentType("application/json");
        createUser.setPathParams(new ArrayList<ApiParam>());
        createUser.setQueryParams(new ArrayList<ApiParam>());
        createUser.setRequestParams(Arrays.asList(
                param("name", "string", false, "", "Display name."),
                param("email", "string", false, "", "E-mail address.")));
        createUser.setResponseParams(userResponseParams());
        createUser.setRequestHeaders(new ArrayList<ApiReqParam>());
        ApiRequestExample postExample = new ApiRequestExample();
        postExample.setJson(true);
        postExample.setJsonBody("{\n  \"name\": \"\",\n  \"email\": \"\"\n}");
        createUser.setRequestExample(postExample);

        userController.setList(Arrays.asList(getUser, createUser));
        userController.getList().get(0).setOrder(1);
        userController.getList().get(1).setOrder(2);
        // In real engine runs the template links every method back to its owning doc
        // (see ApiMethodDoc#setClazzDoc); the fixture reproduces that link.
        userController.getList().forEach(m -> m.setClazzDoc(userController));

        ApiSchema<ApiDoc> schema = new ApiSchema<ApiDoc>();
        schema.setApiDatas(new ArrayList<ApiDoc>(Arrays.asList(userController)));
        schema.setApiExceptionStatuses(new ArrayList<ApiExceptionStatus>());
        return schema;
    }

    private List<ApiParam> userResponseParams() {
        List<ApiParam> params = new ArrayList<ApiParam>();
        params.add(param("id", "int64", false, "0", "User identifier."));
        params.add(param("name", "string", false, "", "Display name."));
        params.add(param("email", "string", false, "", "E-mail address."));
        params.add(param("status", "enum", false, "ACTIVE",
                "Account status.<br/>(See: Account status.)"));
        return params;
    }

    private ApiSchema<ApiDoc> nestedSchema() {
        ApiParam user = param("user", "object", true, null, "payload");
        ApiParam name = param("name", "string", false, null, "name");
        ApiParam deepId = param("id", "int64", false, "0", "deep");
        name.setChildren(Collections.singletonList(deepId));
        user.setChildren(Collections.singletonList(name));
        ApiMethodDoc method = method("POST", "/n", "nested");
        method.setRequestParams(Collections.singletonList(user));
        return schemaOf(docOf(method));
    }

    private static ApiDoc docOf(ApiMethodDoc method) {
        ApiDoc doc = new ApiDoc();
        doc.setName("PlainController");
        doc.setAlias("PlainController");
        doc.setOrder(1);
        method.setOrder(1);
        method.setClazzDoc(doc);
        doc.setList(Collections.singletonList(method));
        return doc;
    }

    private static ApiSchema<ApiDoc> schemaOf(ApiDoc doc) {
        ApiSchema<ApiDoc> schema = new ApiSchema<ApiDoc>();
        schema.setApiDatas(new ArrayList<ApiDoc>(Arrays.asList(doc)));
        schema.setApiExceptionStatuses(new ArrayList<ApiExceptionStatus>());
        return schema;
    }

    private static ApiMethodDoc method(String type, String path, String name) {
        ApiMethodDoc method = new ApiMethodDoc();
        method.setType(type);
        method.setPath(path);
        method.setName(name);
        method.setUrl("https://api.example.com" + path);
        return method;
    }

    private static ApiParam param(String field, String type, boolean required, String value,
                                  String desc) {
        ApiParam param = new ApiParam();
        param.setField(field);
        param.setType(type);
        param.setRequired(required);
        param.setValue(value);
        param.setDesc(desc);
        param.setChildren(new ArrayList<ApiParam>());
        return param;
    }

    private File write(ApiSchema<ApiDoc> schema) throws Exception {
        File file = tempDir.toFile().getCanonicalFile();
        File target = new File(file, "api-doc.xlsx");
        ExcelWriter.write(schema, target);
        return target;
    }

    private static Workbook open(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        return WorkbookFactory.create(fis);
    }

    private static boolean isBold(Workbook wb, Cell cell) {
        int fontIndex = cell.getCellStyle().getFontIndex();
        return fontIndex > 0 && wb.getFontAt(fontIndex).getBold();
    }

    private static String cellText(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "";
    }
}
