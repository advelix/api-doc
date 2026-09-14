# Excel 输出

[English](excel-output.md)

`api-doc:excel` 把与 `openapi` 相同的解析结果输出为 Excel（`.xlsx`），便于人工
浏览和评审：

```bash
mvn api-doc:excel -Dsource=./src/main/java -Doutput=./docs
```

产物为 `<output>/api-doc.xlsx`，与 `openapi.json` 并列输出。`excel` 复用
`openapi` 的**全部参数**（`source`/`output`/`serverUrl`/`customAnnotations` 等），
不新增任何参数，参数取值规则与 `openapi` 完全一致。

Excel 与 JSON **同源**：两个 goal 跑的是同一条 Smart-doc 解析管道（同一
`ApiConfig`、同一批 controller、同一模板），因此 operation 集合、行顺序
（controller 文档 order + 方法 order）、组合注解与多值 method 展开行为与
`openapi.json` 的 `paths` 段一一对应。

## Excel 列说明

单 sheet（`api-doc`），区域 A 为接口清单（每个 HTTP operation 一行，顺序与
`openapi.json` 的 `paths` 遍历顺序一致）：

| 列 | 来源 |
| ---- | ---- |
| `#` | 1-based 序号 |
| `Method` | HTTP 方法（GET/POST/…） |
| `Path` | 接口路径（含 `{id}` 占位符） |
| `URL` | `serverUrl` + path |
| `OperationId` | 操作 ID（默认方法名） |
| `Summary` | 方法 Javadoc 摘要 |
| `Controller` | 所在 controller 的 Javadoc alias（兜底类简名） |
| `Tag` | controller 简名 |
| `Content-Type` | 请求 Content-Type（无 body 时留空） |
| `Deprecated` | `true`/`false` |
| `Path 参数` | path 参数（渲染规则见下） |
| `Query 参数` | query 参数 |
| `请求头` | 请求头参数 |
| `Body 参数` | 请求体参数树（嵌套对象按 children 展开） |
| `响应字段` | 响应体参数树 |
| `请求示例` | JSON 请求体示例（非 JSON 请求退化为 curl 示例文本） |

参数列渲染规则：一行一个参数，层级用每层两个前导空格缩进表示；每行格式为
`字段名 (类型) *必填* = 默认/示例值 — 描述`（必填标记与值段在无值时省略；类型已
转换为 OpenAPI 类型名，如 `int64` → `number`、`enum` → `string`；描述中的 `<br/>`
替换为空格）。

若解析结果携带错误码（`ApiExceptionStatus`），区域 B（`状态码 | 描述 | 详情 | 响应`）
会写在区域 A 下方空一行之后；当前 Spring 流程下引擎默认不产出错误码条目，区域 B 通常
为空跳过。

> 空工程（没有任何文档化 controller）时构建不会失败：输出只有表头的 xlsx 并打印
> warn（与 `openapi` 的行为不同，属有意设计：Excel 用于人工浏览，空表同样有用）。
