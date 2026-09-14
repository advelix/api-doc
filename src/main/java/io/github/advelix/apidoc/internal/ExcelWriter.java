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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.ly.doc.model.ApiDoc;
import com.ly.doc.model.ApiExceptionStatus;
import com.ly.doc.model.ApiMethodDoc;
import com.ly.doc.model.ApiParam;
import com.ly.doc.model.ApiReqParam;
import com.ly.doc.model.ApiSchema;
import com.ly.doc.model.request.ApiRequestExample;
import com.ly.doc.utils.DocUtil;

/**
 * Renders an in-memory {@link ApiSchema} produced by {@link ApiDocDataBuilder} into a single
 * sheet {@code .xlsx} workbook.
 *
 * <p>The workbook contains two regions in one sheet named {@code api-doc}:
 * <ol>
 * <li><b>Endpoint list</b>: one row per HTTP operation in engine order (controller document
 *     order plus method order, which is also the order the JSON goal writes into the
 *     {@code paths} section). The sixteen columns are {@code #, Method, Path, URL,
 *     OperationId, Summary, Controller, Tag, Content-Type, Deprecated, Path parameters,
 *     Query parameters, Request headers, Body parameters, Response fields, Request example};</li>
 * <li><b>Error codes</b>: written only when the schema carries exception statuses
 *     ({@link ApiSchema#getApiExceptionStatuses()}); when the list is empty the whole region is
 *     skipped, not rendered as a "(none)" placeholder.</li>
 * </ol>
 *
 * <p>Parameter lists follow the column conventions documented in the README: one entry per line
 * (cells are created with {@code wrapText}), two leading spaces per nesting level, format
 * {@code name (type) *必填* = value — description} where the type is converted from the
 * Smart-doc Java type name to the OpenAPI type name through
 * {@link DocUtil#javaTypeToOpenApiTypeConvert(String)}, the required marker and the value
 * segment are omitted when not present, and {@code <br/>} in descriptions is replaced by a
 * space. Cells longer than 32000 characters are truncated with a trailing ellipsis to stay
 * below the Excel 32767 character limit.
 */
public final class ExcelWriter {

    /** Sheet name for every generated workbook. */
    public static final String SHEET_NAME = "api-doc";

    /** Hard Excel limit per cell is 32767; values longer than this are truncated. */
    private static final int MAX_CELL_LENGTH = 32000;

    private static final String ELLIPSIS = "\u2026";

    private static final String REQUIRED_MARKER = " *必填*";

    private static final String DESCRIPTION_SEPARATOR = " — ";

    private static final String BR_TAG = "<br/>";

    private static final String LINE_BREAK = System.lineSeparator();

    /** Column order of the endpoint list region; also used for the header row. */
    private static final String[] AREA_A_HEADERS = {
            "#", "Method", "Path", "URL", "OperationId", "Summary", "Controller", "Tag",
            "Content-Type", "Deprecated", "Path 参数", "Query 参数",
            "请求头", "Body 参数", "响应字段", "请求示例"
    };

    /** Column order of the error code region. */
    private static final String[] AREA_B_HEADERS = {
            "状态码", "描述", "详情", "响应"
    };

    private ExcelWriter() {
    }

    /**
     * Writes the given schema to {@code target} as an {@code .xlsx} workbook.
     *
     * @param schema the parsed document schema, may be empty (headers are still written)
     * @param target the file to write, parent directory must exist
     * @throws IOException when the workbook cannot be written
     */
    public static void write(ApiSchema<ApiDoc> schema, File target) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        try {
            render(workbook, schema);
            try (OutputStream out = new FileOutputStream(target)) {
                workbook.write(out);
            }
        } finally {
            workbook.close();
        }
    }

    /**
     * Renders both regions into a fresh workbook; visible for tests that want to inspect the
     * workbook before it is closed.
     *
     * @param workbook the workbook to render into
     * @param schema   the parsed document schema, may be empty
     */
    static void render(Workbook workbook, ApiSchema<ApiDoc> schema) {
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle wrapStyle = createWrapStyle(workbook);
        Sheet sheet = workbook.createSheet(SHEET_NAME);

        int nextRow = writeAreaA(sheet, schema, headerStyle, wrapStyle);
        nextRow = writeAreaB(sheet, schema, nextRow, headerStyle);

        for (int column = 0; column < AREA_A_HEADERS.length; column++) {
            sheet.autoSizeColumn(column);
        }
    }

    /**
     * Writes the endpoint list region and returns the index of the next free row.
     */
    private static int writeAreaA(Sheet sheet, ApiSchema<ApiDoc> schema, CellStyle headerStyle,
                                  CellStyle wrapStyle) {
        Row header = sheet.createRow(0);
        for (int column = 0; column < AREA_A_HEADERS.length; column++) {
            Cell cell = header.createCell(column, CellType.STRING);
            cell.setCellValue(AREA_A_HEADERS[column]);
            cell.setCellStyle(headerStyle);
        }

        List<ApiMethodDoc> operations = collectOperations(schema);
        int row = 1;
        for (ApiMethodDoc operation : operations) {
            ApiDoc owner = operation.getClazzDoc();
            Row data = sheet.createRow(row);
            setCell(data, 0, String.valueOf(row), wrapStyle);
            setCell(data, 1, operation.getType(), wrapStyle);
            setCell(data, 2, operation.getPath(), wrapStyle);
            setCell(data, 3, operation.getUrl(), wrapStyle);
            setCell(data, 4, operation.getName(), wrapStyle);
            setCell(data, 5, operation.getDesc(), wrapStyle);
            setCell(data, 6, controllerName(owner), wrapStyle);
            setCell(data, 7, owner == null ? null : owner.getName(), wrapStyle);
            setCell(data, 8, operation.getContentType(), wrapStyle);
            setCell(data, 9, operation.isDeprecated() ? Boolean.TRUE.toString()
                    : Boolean.FALSE.toString(), wrapStyle);
            setCell(data, 10, renderParams(operation.getPathParams()), wrapStyle);
            setCell(data, 11, renderParams(operation.getQueryParams()), wrapStyle);
            setCell(data, 12, renderHeaders(operation.getRequestHeaders()), wrapStyle);
            setCell(data, 13, renderParams(operation.getRequestParams()), wrapStyle);
            setCell(data, 14, renderParams(operation.getResponseParams()), wrapStyle);
            setCell(data, 15, renderRequestExample(operation.getRequestExample()), wrapStyle);
            row++;
        }
        return row;
    }

    /**
     * Writes the error code region below the endpoint list, separated by one blank row, only
     * when the schema actually carries exception statuses.
     *
     * @return the index of the next free row
     */
    private static int writeAreaB(Sheet sheet, ApiSchema<ApiDoc> schema, int firstRow,
                                  CellStyle headerStyle) {
        List<ApiExceptionStatus> statuses = schema.getApiExceptionStatuses();
        if (statuses == null || statuses.isEmpty()) {
            return firstRow;
        }
        int row = firstRow + 1;
        Row header = sheet.createRow(row);
        for (int column = 0; column < AREA_B_HEADERS.length; column++) {
            Cell cell = header.createCell(column, CellType.STRING);
            cell.setCellValue(AREA_B_HEADERS[column]);
            cell.setCellStyle(headerStyle);
        }
        row++;
        for (ApiExceptionStatus status : statuses) {
            Row data = sheet.createRow(row);
            setCell(data, 0, status.getStatus(), null);
            setCell(data, 1, status.getDesc(), null);
            setCell(data, 2, status.getDetail(), null);
            setCell(data, 3, status.getResponseUsage(), null);
            row++;
        }
        return row;
    }

    /**
     * Flattens the schema into one operation list in engine order: documents in list order,
     * their methods in list order, and any {@code childrenApiDocs} (allInOne grouping)
     * flattened after the parent's own methods.
     *
     * @param schema the parsed document schema, may be empty
     * @return the operations in the order they are written to the sheet, never null
     */
    public static List<ApiMethodDoc> collectOperations(ApiSchema<ApiDoc> schema) {
        List<ApiMethodDoc> operations = new ArrayList<ApiMethodDoc>();
        List<ApiDoc> docs = schema.getApiDatas();
        if (docs == null) {
            return operations;
        }
        for (ApiDoc doc : docs) {
            collectDocOperations(doc, operations);
        }
        return operations;
    }

    private static void collectDocOperations(ApiDoc doc, List<ApiMethodDoc> operations) {
        if (doc.getList() != null) {
            operations.addAll(doc.getList());
        }
        List<ApiDoc> children = doc.getChildrenApiDocs();
        if (children != null) {
            for (ApiDoc child : children) {
                collectDocOperations(child, operations);
            }
        }
    }

    /**
     * Renders a parameter tree into one cell value: one line per node, depth first, two
     * leading spaces per nesting level.
     */
    static String renderParams(List<ApiParam> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        for (ApiParam param : params) {
            renderParam(param, 0, lines);
        }
        return String.join(LINE_BREAK, lines);
    }

    private static void renderParam(ApiParam param, int depth, List<String> lines) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            line.append("  ");
        }
        line.append(param.getField()).append(" (").append(openApiType(param.getType()))
                .append(")");
        if (param.isRequired()) {
            line.append(REQUIRED_MARKER);
        }
        if (param.getValue() != null && !param.getValue().isEmpty()) {
            line.append(" = ").append(param.getValue());
        }
        String description = normalizeDescription(param.getDesc());
        if (description != null) {
            line.append(DESCRIPTION_SEPARATOR).append(description);
        }
        lines.add(line.toString());
        if (param.getChildren() != null) {
            for (ApiParam child : param.getChildren()) {
                renderParam(child, depth + 1, lines);
            }
        }
    }

    /**
     * Renders request headers, comma separated on a single line per header group.
     */
    static String renderHeaders(List<ApiReqParam> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        for (ApiReqParam header : headers) {
            StringBuilder line = new StringBuilder();
            line.append(header.getName()).append(" (").append(openApiType(header.getType()))
                    .append(")");
            if (header.isRequired()) {
                line.append(REQUIRED_MARKER);
            }
            String description = normalizeDescription(header.getDesc());
            if (description != null) {
                line.append(DESCRIPTION_SEPARATOR).append(description);
            }
            lines.add(line.toString());
        }
        return String.join(LINE_BREAK, lines);
    }

    /**
     * Returns the JSON body of the request example when it is a JSON example, otherwise the
     * plain example body; both absent means the column stays empty.
     */
    static String renderRequestExample(ApiRequestExample example) {
        if (example == null) {
            return null;
        }
        if (example.isJson()) {
            return example.getJsonBody();
        }
        return example.getExampleBody();
    }

    /**
     * Converts a Smart-doc Java type name into the OpenAPI type name shown in the sheet.
     */
    private static String openApiType(String javaType) {
        return DocUtil.javaTypeToOpenApiTypeConvert(javaType);
    }

    /**
     * Replaces HTML line breaks with spaces and trims whitespace; empty results become null.
     */
    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.replace(BR_TAG, " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String controllerName(ApiDoc owner) {
        if (owner == null) {
            return null;
        }
        if (owner.getAlias() != null && !owner.getAlias().trim().isEmpty()) {
            return owner.getAlias();
        }
        return owner.getName();
    }

    private static void setCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column, CellType.STRING);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(truncate(value));
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * Keeps a value below the Excel per-cell character limit.
     */
    static String truncate(String value) {
        if (value == null || value.length() <= MAX_CELL_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CELL_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle createWrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }
}
