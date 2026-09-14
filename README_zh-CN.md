# api-doc

[English](README.md) | 简体中文

`api-doc` 是一个独立的 Maven 插件，用于从 Java 源码与 Javadoc 注释生成 **OpenAPI 3.x JSON** 接口文档。

> 本项目基于 [Smart-doc](https://gitee.com/shalousun/smart-doc) 构建：通过依赖引入
> `com.ly.smart-doc:smart-doc:3.1.2` 解析引擎，并以“包装（Wrapper）”方式调用其**公开 API**
> 完成文档生成，不对 Smart-doc 源码做任何修改或衍生。
>
> 请注意：`api-doc` 是独立项目，**不是 Smart-doc 的官方产品**，与 Smart-doc 作者/团队
> 不存在隶属或背书关系。“Smart-doc”为其权利人的标识，本 README 中的提及仅用于归属声明。

## 目录

- [环境要求](#环境要求)
- [安装](#安装)
- [快速开始](#快速开始)
- [生成 Excel](#生成-excel)
- [透传 Smart-doc 原生输出格式](#透传-smart-doc-原生输出格式)
- [参数说明](#参数说明)
- [示例工程](#示例工程)
- [自定义组合注解](#自定义组合注解)
- [版本历史](#版本历史)
- [开源合规](#开源合规)

## 环境要求

| 依赖 | 最低版本 | 说明 |
| ---- | -------- | ---- |
| JDK  | 1.8      | Smart-doc 3.1.2 要求 JDK 1.8+ |
| Maven| 3.8      | Smart-doc 3.1.2 要求 Maven 3.8+ |

项目编译级别固定为 JDK 1.8，构建过程由 `maven-enforcer-plugin` 强制校验上述版本要求。

## 安装

插件已发布到 Maven Central（`io.github.advelix:api-doc-maven-plugin:1.0.0`），
目标工程可直接按坐标调用，无需本地安装。从源码构建（开发或私有版本）：

```bash
git clone https://github.com/advelix/api-doc.git
cd api-doc
mvn clean install
```

## 快速开始

在目标工程目录下直接调用（`-Dsource` 指定源码路径，`-Doutput` 指定输出路径）：

```bash
mvn io.github.advelix:api-doc-maven-plugin:1.0.0:openapi \
    -Dsource=./src/main/java -Doutput=./docs
```

或者先安装插件后使用 goal 前缀（推荐，配合下方 `pluginManagement` 配置）：

```bash
mvn api-doc:openapi -Dsource=./src/main/java -Doutput=./docs
```

生成的文档位于 `<output>/openapi.json`，可直接导入 Apifox / Postman / Swagger Editor
等工具。

### 目标工程中的推荐配置

```xml
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>io.github.advelix</groupId>
        <artifactId>api-doc-maven-plugin</artifactId>
        <version>1.0.0</version>
        <configuration>
          <!-- 不写 source 时默认解析 ${project.getCompileSourceRoots()} -->
          <!-- <source>./src/main/java</source> -->
          <serverUrl>https://api.example.com</serverUrl>
          <projectName>示例服务 API</projectName>
        </configuration>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```

之后 `mvn api-doc:openapi` 即可；`source`/`output` 等参数也可以在命令行用 `-D` 指定：

```bash
mvn api-doc:openapi -Dsource=./src/main/java -Doutput=./docs
```

> 参数取值遵循 Maven 惯例：**pom 中 `<configuration>` 已配置的参数以其值为准；未配置的
> 参数取命令行 `-D` 值；两者都缺省时使用参数默认值**。需要命令行灵活控制的参数，
> 不要在 pom 中重复配置。

## 生成 Excel

除 OpenAPI JSON 外，插件还可以把同一套解析结果输出为 Excel（`.xlsx`），便于人工
浏览和评审：

```bash
mvn api-doc:excel -Dsource=./src/main/java -Doutput=./docs
```

产物为 `<output>/api-doc.xlsx`，与 `openapi.json` 并列输出。`excel` 复用
`openapi` 的**全部参数**（`source`/`output`/`serverUrl`/`customAnnotations` 等），
不新增任何参数，取值规则与上一节完全一致。

Excel 与 JSON **同源**：两个 goal 跑的是同一条 Smart-doc 解析管道（同一 `ApiConfig`、
同一批 controller、同一模板），因此 operation 集合、行顺序（controller 文档 order +
方法 order）、组合注解与多值 method 展开行为与 `openapi.json` 的 `paths` 段
一一对应。工作簿的 16 列定义、参数列渲染规则与空工程行为见
[Excel 输出](docs/excel-output_zh-CN.md)。

## 透传 Smart-doc 原生输出格式

Smart-doc 官方插件自带的全部输出格式，本项目以同名 goal 透传支持：每个 goal 直接调用
Smart-doc 官方的 builder（与官方 `smart-doc-maven-plugin` 同一入口方法、同一 `ApiConfig`），
产物与官方插件完全一致，且**同样支持 `customAnnotations` 组合注解解析**（官方插件不支持）。

| goal | Smart-doc 入口 | 产物（写入 `<output>`） |
| ---- | ---- | ---- |
| `api-doc:openapi` | `OpenApiBuilder` | `<output>/openapi.json`（OpenAPI 3.x） |
| `api-doc:word` | `WordDocBuilder` | 每个 API 组一份 `<Group>index.docx` + `dict.docx` + `error.docx` |
| `api-doc:html` | `HtmlApiDocBuilder` | `api/` 站点（index/error/dict.html + 各组页面 + 静态资源） |
| `api-doc:markdown` | `ApiDocBuilder` | 各组 `<Group>Api.md` + `Dictionary.md` + `ErrorCodeList.md`（+ AllInOne.md，视配置） |
| `api-doc:adoc` | `AdocDocBuilder` | 同上（AsciiDoc 版本） |
| `api-doc:postman` | `PostmanJsonBuilder` | `postman.json`（Postman v2.1 collection） |
| `api-doc:jmeter` | `JMeterBuilder` | `<projectName>-V<时间戳>.jmx` |
| `api-doc:swagger` | `SwaggerBuilder` | `<output>/openapi.json`（Swagger 2.0 格式；与 `openapi` 同文件名，同目录连跑以后者为准） |
| `api-doc:torna-rest` / `api-doc:torna-rpc` | `TornaBuilder` / `RpcTornaBuilder` | 无本地文件：调用 Torna 接口上传（需要网络与 Torna 服务配置） |
| `api-doc:websocket-adoc` / `websocket-html` / `websocket-markdown` | `WebSocket*Builder` | WebSocket 文档（对应格式） |
| `api-doc:rpc-adoc` / `rpc-html` / `rpc-markdown` / `rpc-word` | `Rpc*Builder` | RPC 文档（对应格式） |
| `api-doc:javadoc-adoc` / `javadoc-html` / `javadoc-markdown` | `Javadoc*Builder` | 类级 Javadoc 文档（对应格式） |
| `api-doc:grpc-adoc` / `grpc-html` / `grpc-markdown` | `Grpc*Builder` | gRPC 文档（源码需为 `.proto`，依赖 grpc 环境） |

```bash
# Word（docx）
mvn api-doc:word -Dsource=./src/main/java -Doutput=./docs
# HTML
mvn api-doc:html -Dsource=./src/main/java -Doutput=./docs
# Markdown
mvn api-doc:markdown -Dsource=./src/main/java -Doutput=./docs
```

所有透传 goal 完整复用 `openapi` 的全部参数（见下文参数表），不新增任何参数；
参数解析、组合注解激活、依赖类加载路径与 `openapi`/`excel` 完全一致。

## 参数说明

命令行 `-D` 与 pom 配置项一一对应（取值规则见上文快速开始一节）：

| 参数 | `-D` 属性 / 别名 | 默认值 | 说明 |
| ---- | ---------------- | ------ | ---- |
| `source` | `-Dsource` / `api-doc.source` | 项目 compile source roots | 源码根路径，多个用逗号或分号分隔；相对路径基于工程根目录 |
| `output` | `-Doutput` / `api-doc.output` | `${project.build.directory}/api-doc` | 输出目录（`openapi` 产物为 `<output>/openapi.json`，`excel` 产物为 `<output>/api-doc.xlsx`，其余 goal 见各自章节） |
| `serverUrl` | `-Dapi-doc.serverUrl` | 无 | 服务地址，写入 OpenAPI 的 `servers` 段 |
| `projectName` | `-Dapi-doc.projectName` | 工程名 | 文档标题 |
| `packageFilters` | `-Dapi-doc.packageFilters` | 无 | 包前缀过滤，如 `com.acme.api,com.acme.web` |
| `strict` | `-Dapi-doc.strict` | `false` | 严格模式 |
| `includeDependencies` | `-Dapi-doc.includeDependencies` | `true` | 是否将工程依赖类路径暴露给解析引擎（解析 jar 中的枚举/DTO/框架注解） |
| `skip` | `-Dapi-doc.skip` | `false` | 跳过生成 |
| `configFile` | `-Dapi-doc.configFile` | 无 | Smart-doc 原生 JSON 配置文件路径（详见 Smart-doc 文档），命令行参数始终优先 |
| `customAnnotations` | `-Dapi-doc.customAnnotations` | 无 | 组合注解配置：`<annotations>` 列全限定名（自动推导）+ 可选 `<declarations>` 显式 JSON（覆盖推导），见下文“自定义组合注解”；设置后自动切换到增强解析模板，推导/解析失败时构建失败 |

`excel` 与全部透传 goal（[见上文](#透传-smart-doc-原生输出格式)）完整复用上表全部参数，无新增参数。

## 示例工程

`examples/spring-api` 是一个最小可运行的 Spring MVC 示例，包含两类 Controller：

- `UserController`：使用标准 Spring MVC 注解（`@RestController` / `@RequestMapping` / `@GetMapping` / `@PostMapping`）
- `OrderController`：使用基于 `@AliasFor` 的组合自定义注解（`@ApiController` / `@GetApi` / `@PostApi`，
  位于 `io.github.advelix.demo.annotation.combine`）
- `RequestApiController`：同样的组合注解，HTTP 方法在**使用处**动态声明（`@RequestApi`）

不传 `customAnnotations` 时仅标准注解的 `UserController` 会被识别；声明组合注解后三个
Controller 都会生成文档：

```bash
# 1. 安装插件
mvn -f pom.xml clean install -q

# 2. 在示例工程上生成 OpenAPI JSON（声明组合注解后三个 Controller 均被识别）
cd examples/spring-api
mvn io.github.advelix:api-doc-maven-plugin:1.0.0:openapi \
    -Dapi-doc.customAnnotations.annotations=io.github.advelix.demo.annotation.combine.ApiController,io.github.advelix.demo.annotation.combine.GetApi,io.github.advelix.demo.annotation.combine.PostApi,io.github.advelix.demo.annotation.combine.RequestApi

# 3. 生成同源的 Excel 接口清单（可选）
mvn io.github.advelix:api-doc-maven-plugin:1.0.0:excel

# 4. 查看产物
cat target/api-doc/openapi.json
ls target/api-doc/api-doc.xlsx
```

## 自定义组合注解

如果你的工程像示例里的 `OrderController` 那样，用基于 Spring `@AliasFor` 的组合注解
（例如自定义的 `@ApiController` / `@GetApi` / `@PostApi`）声明 Controller 与接口，
在 `customAnnotations` 参数里只列注解全限定名即可，插件会**自动推导** entry/mapping
分类、HTTP 方法、path 属性等其余配置；推导结果需修正或注解定义不可见时，可用可选的
`declarations` JSON 显式声明（同名注解显式声明优先）。

完整的配置示例、自动推导规则表、`declarations` 字段说明与合并行为见
[自定义组合注解](docs/custom-annotations_zh-CN.md)。

## 版本历史

完整变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 开源合规

- 本项目基于 Smart-doc 构建（依赖引入，非源码衍生），Smart-doc 及其衍生许可条款
  见 [Smart-doc 仓库](https://gitee.com/shalousun/smart-doc)。
- 项目根目录的 `LICENSE` 为 Apache License 2.0 全文副本，`NOTICE` 保留对 Smart-doc
  的归属声明。
- `api-doc` 项目名独立于 Smart-doc，本项目与 Smart-doc 官方无关。
