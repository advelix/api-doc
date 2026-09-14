# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-14

### Added

- `api-doc:openapi` goal: generates an OpenAPI 3.x JSON document
  (`<output>/openapi.json`) from Java source and Javadoc comments.
- `api-doc:excel` goal: generates an Excel (`.xlsx`) endpoint list
  (`<output>/api-doc.xlsx`) from the same parsing pipeline as `openapi`,
  so both artifacts always describe the same operations in the same order.
- Combined custom annotation support via `api-doc.customAnnotations`:
  - `annotations` lists only fully qualified names; entry vs. mapping
    classification, HTTP method, path properties and so on are auto-derived
    from the annotation definitions (project source first, dependency class
    path as fallback).
  - Multi-value method declarations (for example
    `@RequestMapping(method = {GET, PUT})`) are expanded into one operation
    per HTTP verb on the same path.
  - `method()`-style annotations (for example a combined `@RequestApi`)
    declare the HTTP method at the use site, single or multi value.
  - `declarations` accepts explicit JSON to declare annotations whose
    definition cannot be inspected or to override a derived value; for the
    same annotation name the explicit declaration wins.
- Passthrough goals with the same names as the official Smart-doc plugin,
  delegating to the same builders with the same artifacts, and with combined
  annotation support as well: `word`, `html`, `markdown`, `adoc`, `postman`,
  `jmeter`, `swagger`, `torna-rest`, `torna-rpc`, `websocket-adoc`,
  `websocket-html`, `websocket-markdown`, `rpc-adoc`, `rpc-html`,
  `rpc-markdown`, `rpc-word`, `javadoc-adoc`, `javadoc-html`,
  `javadoc-markdown`, `grpc-adoc`, `grpc-html`, `grpc-markdown`.
- Publishing to Maven Central via the central portal (`-P central`).
- Runnable Spring MVC example in `examples/spring-api` covering both standard
  Spring MVC annotations and combined custom annotations.
