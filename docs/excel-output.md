# Excel Output

[简体中文](excel-output_zh-CN.md)

`api-doc:excel` renders the same parsed result as `openapi` into an Excel
workbook (`.xlsx`) for human browsing and review:

```bash
mvn api-doc:excel -Dsource=./src/main/java -Doutput=./docs
```

The artifact is `<output>/api-doc.xlsx`, emitted alongside `openapi.json`.
`excel` reuses **all** parameters of `openapi` (`source`/`output`/
`serverUrl`/`customAnnotations`, etc.) and adds none; the resolution rules
are identical to `openapi`.

Excel and JSON are **generated from the same source**: both goals run the
same Smart-doc parsing pipeline (the same `ApiConfig`, the same set of
controllers, the same template), so the operation set, the row order
(controller document order + method order), combined-annotation handling and
multi-value method expansion all correspond one-to-one with the `paths`
section of `openapi.json`.

## Workbook Layout

Single sheet (`api-doc`); area A is the operation list (one row per HTTP
operation, in the same order as the `paths` traversal of `openapi.json`):

| Column | Source |
| ------ | ------ |
| `#` | 1-based index |
| `Method` | HTTP method (GET/POST/…) |
| `Path` | Operation path (including `{id}` placeholders) |
| `URL` | `serverUrl` + path |
| `OperationId` | Operation ID (defaults to the method name) |
| `Summary` | Method Javadoc summary |
| `Controller` | Controller Javadoc alias (falls back to the simple class name) |
| `Tag` | Simple controller class name |
| `Content-Type` | Request Content-Type (empty when there is no body) |
| `Deprecated` | `true`/`false` |
| `Path 参数` | Path parameters (rendering rules below) |
| `Query 参数` | Query parameters |
| `请求头` | Request header parameters |
| `Body 参数` | Request body parameter tree (nested objects expanded via children) |
| `响应字段` | Response body parameter tree |
| `请求示例` | JSON request body example (non-JSON requests degrade to curl example text) |

Note: the column headers are part of the output format — the mixed
English/Chinese names above are the literal headers written to the workbook
and must not be translated.

Parameter column rendering rules: one parameter per line; nesting levels are
indented by two leading spaces per level; each line has the form
`name (type) *required* = default/example — description` (the required mark
and the value segment are omitted when absent; types are converted to OpenAPI
type names, e.g. `int64` → `number`, `enum` → `string`; `<br/>` in
descriptions is replaced with a space).

When the parsed result carries error codes (`ApiExceptionStatus`), area B
(`状态码 | 描述 | 详情 | 响应`) is written one blank line below area A; in the
current Spring flow the engine does not produce error-code entries by default,
so area B is normally empty and skipped.

> An empty project (no documented controllers at all) does not fail the
> build: a header-only workbook is written and a warning is printed. This
> differs from `openapi` on purpose — Excel is for human browsing, where an
> empty sheet is still useful.
