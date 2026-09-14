# api-doc

English | [简体中文](README_zh-CN.md)

`api-doc` is a standalone Maven plugin that generates **OpenAPI 3.x JSON** API
documents from Java source code and Javadoc comments.

> This project is built on [Smart-doc](https://gitee.com/shalousun/smart-doc):
> it pulls in the `com.ly.smart-doc:smart-doc:3.1.2` parsing engine as a
> dependency and invokes its **public API** in a wrapper style to generate
> documents, without modifying or forking the Smart-doc source code.
>
> Please note: `api-doc` is an independent project and **is NOT an official
> Smart-doc product**; it has no affiliation with, or endorsement by, the
> Smart-doc authors or team. "Smart-doc" is an identifier of its respective
> owner, and its mention in this README is for attribution only.

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Excel Output](#excel-output)
- [Passthrough of Native Smart-doc Output Formats](#passthrough-of-native-smart-doc-output-formats)
- [Parameters](#parameters)
- [Example Project](#example-project)
- [Combined Custom Annotations](#combined-custom-annotations)
- [Changelog](#changelog)
- [License & Compliance](#license--compliance)

## Requirements

| Dependency | Minimum | Notes |
| ---------- | ------- | ----- |
| JDK        | 1.8     | Smart-doc 3.1.2 requires JDK 1.8+ |
| Maven      | 3.8     | Smart-doc 3.1.2 requires Maven 3.8+ |

The project compiles against JDK 1.8, and `maven-enforcer-plugin` enforces the
version requirements during the build.

## Installation

The plugin is published to Maven Central
(`io.github.advelix:api-doc-maven-plugin:1.0.0`), so consuming projects can
invoke it by coordinate directly — no local install required. To build from
source (development or private versions):

```bash
git clone https://github.com/advelix/api-doc.git
cd api-doc
mvn clean install
```

## Quick Start

Invoke the plugin directly in your project (`-Dsource` sets the source path,
`-Doutput` sets the output path):

```bash
mvn io.github.advelix:api-doc-maven-plugin:1.0.0:openapi \
    -Dsource=./src/main/java -Doutput=./docs
```

Or install the plugin first and use the goal prefix (recommended, together
with the `pluginManagement` configuration below):

```bash
mvn api-doc:openapi -Dsource=./src/main/java -Doutput=./docs
```

The generated document is at `<output>/openapi.json` and can be imported into
Apifox / Postman / Swagger Editor and similar tools.

### Recommended configuration in consuming projects

```xml
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>io.github.advelix</groupId>
        <artifactId>api-doc-maven-plugin</artifactId>
        <version>1.0.0</version>
        <configuration>
          <!-- without <source>, ${project.getCompileSourceRoots()} is parsed by default -->
          <!-- <source>./src/main/java</source> -->
          <serverUrl>https://api.example.com</serverUrl>
          <projectName>My Service API</projectName>
        </configuration>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```

After that, `mvn api-doc:openapi` is all that is needed; `source`/`output` and
other parameters can also be set on the command line with `-D`:

```bash
mvn api-doc:openapi -Dsource=./src/main/java -Doutput=./docs
```

> Parameter resolution follows Maven conventions: **a parameter already set in
> the pom `<configuration>` keeps its pom value; a parameter not set in the
> pom takes its command line `-D` value; when neither is set the parameter
> default applies**. Do not repeat in the pom a parameter you want to control
> flexibly from the command line.

## Excel Output

Besides the OpenAPI JSON, the plugin can output the same parsed result as an
Excel (`.xlsx`) file for convenient human review:

```bash
mvn api-doc:excel -Dsource=./src/main/java -Doutput=./docs
```

The artifact is `<output>/api-doc.xlsx`, written alongside `openapi.json`.
`excel` reuses **all parameters** of `openapi` (`source`/`output`/
`serverUrl`/`customAnnotations`, etc.) and adds none; the resolution rules are
identical to the previous section.

Excel and JSON are **generated from the same source**: both goals run the same
Smart-doc parsing pipeline (the same `ApiConfig`, the same set of controllers,
the same template), so the operation set, the row order (controller document
order + method order), combined annotations and multi-value method expansion
all correspond one-to-one with the `paths` section of `openapi.json`. The
workbook's 16 column definitions, the parameter column rendering rules and the
empty-project behavior are documented in
[Excel output](docs/excel-output.md).

## Passthrough of Native Smart-doc Output Formats

All output formats of the official Smart-doc plugin are supported under the
same goal names: each goal invokes the official Smart-doc builder directly
(the same entry method and the same `ApiConfig` as the official
`smart-doc-maven-plugin`), produces exactly the same artifacts, and — unlike
the official plugin — **also supports `customAnnotations` combined annotation
parsing**.

| Goal | Smart-doc entry | Artifact (written to `<output>`) |
| ---- | --------------- | -------------------------------- |
| `api-doc:openapi` | `OpenApiBuilder` | `<output>/openapi.json` (OpenAPI 3.x) |
| `api-doc:word` | `WordDocBuilder` | one `<Group>index.docx` per API group + `dict.docx` + `error.docx` |
| `api-doc:html` | `HtmlApiDocBuilder` | `api/` site (index/error/dict.html + group pages + static assets) |
| `api-doc:markdown` | `ApiDocBuilder` | per group `<Group>Api.md` + `Dictionary.md` + `ErrorCodeList.md` (+ AllInOne.md depending on configuration) |
| `api-doc:adoc` | `AdocDocBuilder` | same as above (AsciiDoc version) |
| `api-doc:postman` | `PostmanJsonBuilder` | `postman.json` (Postman v2.1 collection) |
| `api-doc:jmeter` | `JMeterBuilder` | `<projectName>-V<timestamp>.jmx` |
| `api-doc:swagger` | `SwaggerBuilder` | `<output>/openapi.json` (Swagger 2.0 format; same file name as `openapi`, running both into one directory the later run wins) |
| `api-doc:torna-rest` / `api-doc:torna-rpc` | `TornaBuilder` / `RpcTornaBuilder` | no local file: uploads through the Torna API (requires network and a Torna service) |
| `api-doc:websocket-adoc` / `websocket-html` / `websocket-markdown` | `WebSocket*Builder` | WebSocket documents (corresponding formats) |
| `api-doc:rpc-adoc` / `rpc-html` / `rpc-markdown` / `rpc-word` | `Rpc*Builder` | RPC documents (corresponding formats) |
| `api-doc:javadoc-adoc` / `javadoc-html` / `javadoc-markdown` | `Javadoc*Builder` | class-level Javadoc documents (corresponding formats) |
| `api-doc:grpc-adoc` / `grpc-html` / `grpc-markdown` | `Grpc*Builder` | gRPC documents (source must be `.proto`, requires a gRPC environment) |

```bash
# Word (docx)
mvn api-doc:word -Dsource=./src/main/java -Doutput=./docs
# HTML
mvn api-doc:html -Dsource=./src/main/java -Doutput=./docs
# Markdown
mvn api-doc:markdown -Dsource=./src/main/java -Doutput=./docs
```

All passthrough goals fully reuse the parameters of `openapi` (see the table
below) and add none; parameter parsing, combined annotation activation and the
dependency class loading path are identical to `openapi`/`excel`.

## Parameters

Command line `-D` properties correspond one-to-one to pom configuration
entries (resolution rules as in the Quick Start section above):

| Parameter | `-D` property / alias | Default | Description |
| --------- | --------------------- | ------- | ----------- |
| `source` | `-Dsource` / `api-doc.source` | project compile source roots | source root path(s), multiple separated by commas or semicolons; relative paths are resolved against the project base directory |
| `output` | `-Doutput` / `api-doc.output` | `${project.build.directory}/api-doc` | output directory (`openapi` writes `<output>/openapi.json`, `excel` writes `<output>/api-doc.xlsx`, other goals see their sections) |
| `serverUrl` | `-Dapi-doc.serverUrl` | none | service base URL, written into the OpenAPI `servers` section |
| `projectName` | `-Dapi-doc.projectName` | project name | document title |
| `packageFilters` | `-Dapi-doc.packageFilters` | none | package prefix filter, e.g. `com.acme.api,com.acme.web` |
| `strict` | `-Dapi-doc.strict` | `false` | strict mode |
| `includeDependencies` | `-Dapi-doc.includeDependencies` | `true` | whether to expose the project's dependency class path to the parsing engine (to resolve enums/DTOs/framework annotations from jars) |
| `skip` | `-Dapi-doc.skip` | `false` | skip generation |
| `configFile` | `-Dapi-doc.configFile` | none | Smart-doc native JSON configuration file path (see the Smart-doc docs); command line parameters always win |
| `customAnnotations` | `-Dapi-doc.customAnnotations` | none | combined annotation configuration: `<annotations>` lists fully qualified names (auto-derived) plus optional `<declarations>` explicit JSON (overrides derivation), see [Combined Custom Annotations](#combined-custom-annotations); when set, the plugin switches to the enhanced parsing template automatically, and a failed derivation/parsing fails the build |

`excel` and all passthrough goals ([see above](#passthrough-of-native-smart-doc-output-formats))
fully reuse every parameter of the table above and add none.

## Example Project

`examples/spring-api` is a minimal runnable Spring MVC example containing two
kinds of controllers:

- `UserController`: standard Spring MVC annotations
  (`@RestController` / `@RequestMapping` / `@GetMapping` / `@PostMapping`)
- `OrderController`: combined custom annotations based on `@AliasFor`
  (`@ApiController` / `@GetApi` / `@PostApi`, located in
  `io.github.advelix.demo.annotation.combine`)
- `RequestApiController`: the same combined annotations with the HTTP method
  declared dynamically at the usage site (`@RequestApi`)

Without `customAnnotations`, only the standard-annotation `UserController` is
recognized; after declaring the combined annotations, all three controllers
are documented:

```bash
# 1. install the plugin
mvn -f pom.xml clean install -q

# 2. generate the OpenAPI JSON on the example project (with combined annotations declared, all three controllers are recognized)
cd examples/spring-api
mvn io.github.advelix:api-doc-maven-plugin:1.0.0:openapi \
    -Dapi-doc.customAnnotations.annotations=io.github.advelix.demo.annotation.combine.ApiController,io.github.advelix.demo.annotation.combine.GetApi,io.github.advelix.demo.annotation.combine.PostApi,io.github.advelix.demo.annotation.combine.RequestApi

# 3. generate the same-source Excel endpoint list (optional)
mvn io.github.advelix:api-doc-maven-plugin:1.0.0:excel

# 4. inspect the artifacts
cat target/api-doc/openapi.json
ls target/api-doc/api-doc.xlsx
```

## Combined Custom Annotations

If your project declares controllers and endpoints with Spring
`@AliasFor`-based combined annotations (for example custom
`@ApiController` / `@GetApi` / `@PostApi`), as in the example's
`OrderController`, simply list the annotation fully qualified names in the
`customAnnotations` parameter — the plugin **auto-derives** the rest: the
entry/mapping classification, the HTTP method, the path properties and so
on. When a derived value needs correction or the annotation definition cannot
be inspected, the optional `declarations` JSON declares it explicitly (an
explicit declaration wins for the same annotation name).

Full configuration examples, the auto-derivation rule table, the
`declarations` field reference and the merge behavior are documented in
[Combined custom annotations](docs/custom-annotations.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full version history.

## License & Compliance

- This project is built on Smart-doc (dependency, not source derivation);
  Smart-doc and its derivative licensing terms are in the
  [Smart-doc repository](https://gitee.com/shalousun/smart-doc).
- The `LICENSE` at the project root is a full copy of the Apache License 2.0;
  `NOTICE` carries the Smart-doc attribution.
- The `api-doc` project name is independent of Smart-doc, and this project is
  unrelated to Smart-doc officials.
