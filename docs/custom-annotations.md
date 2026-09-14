# Combined Custom Annotations

[简体中文](custom-annotations_zh-CN.md)

If your project declares controllers and endpoints with combined annotations
built on Spring `@AliasFor` (for example a custom `@ApiController` /
`@GetApi` / `@PostApi`, as in the example project's `OrderController`), just
list the fully qualified annotation names in the `customAnnotations`
parameter — the plugin **auto-derives** the rest of the configuration:

```xml
<configuration>
  <customAnnotations>
    <annotations>
      <annotation>com.acme.web.ApiController</annotation>
      <annotation>com.acme.web.GetApi</annotation>
      <annotation>com.acme.web.PostApi</annotation>
      <annotation>com.acme.web.RequestApi</annotation>
    </annotations>
    <!-- Optional: explicit declarations for annotations that cannot be
         inspected or whose derived values need correction -->
    <!--
    <declarations>{
      "entry": ["com.acme.web.ApiController"],
      "mappings": [
        {"annotation": "com.acme.web.GetApi", "methodType": "GET", "pathProps": ["value"]}
      ]
    }</declarations>
    -->
  </customAnnotations>
</configuration>
```

## Auto-derivation Rules

Annotation definitions are first parsed from project sources; when not found
there, they are read by reflection from the dependency classpath:

| Rule | Derived result |
| ---- | -------------- |
| Class-level annotation meta-annotated with `@RestController` / `@Controller` | Entry annotation (equivalent to `entry`) |
| Meta-annotation `@RequestMapping(method = X)` with a single value | `methodType = X` |
| Meta-annotation `@RequestMapping(method = {X, Y, ...})` with multiple values | `methodType = X` + expansion of the remaining methods (one operation per verb on the same path, Swagger-style) |
| Own `method()` property of type `RequestMethod`/`RequestMethod[]` | `methodProp = method`; the HTTP method is specified **at the usage site**, single- and multi-value supported |
| None of the above | `methodType = GET` (a warning is printed) |
| Own `String` property named `value`/`path`, or an `@AliasFor` pointing at `RequestMapping`'s `value`/`path` | `pathProps` (in declaration order); none found → default `["value", "path"]` |
| Own property named `params` / `consumes` / `produces` | `paramsProp` / `consumesProp` / `producesProp` respectively |

## Explicit `declarations`

Optional JSON for scenarios where the annotation definition is not visible
(e.g. a third-party jar exposes no source and is not on the dependency
classpath) or the derived values need correction:

| Field | Location | Required | Description |
| ----- | -------- | -------- | ----------- |
| `entry` | top level | at least one of `entry`/`mappings` | Array of fully qualified names of class-level entry annotations, equivalent to `@RestController` |
| `mappings[].annotation` | mapping | yes | Fully qualified name of the method-level mapping annotation |
| `mappings[].methodType` | mapping | at least one of `methodType`/`methodProp` | HTTP method: `GET`/`POST`/`PUT`/`PATCH`/`DELETE`/`HEAD`/`OPTIONS` (case-insensitive) |
| `mappings[].methodProp` | mapping | no | Name of the annotation property that declares the HTTP method dynamically (equivalent to `@RequestMapping`'s `method`) |
| `mappings[].pathProps` | mapping | no | Property names holding the path, looked up in order; defaults to `["value", "path"]` |
| `mappings[].paramsProp` / `consumesProp` / `producesProp` | mapping | no | Property names holding the `params` / `consumes` / `produces` declarations |

## Merging and Behavior

- For the same annotation name, **explicit `declarations` win**: a mapping is
  replaced wholesale, entries are unioned (explicit ones first);
- Setting this parameter switches the plugin to the enhanced parsing
  template (Smart-doc framework value `advelix-spring`); when not set,
  behavior is identical to the standard Spring flow;
- If `api-doc.configFile` or the command line explicitly sets a different
  `framework` value, it conflicts with `customAnnotations` and the build
  fails;
- If an annotation definition cannot be found at all (neither in sources nor
  on the dependency classpath), the build fails and suggests using explicit
  `declarations`; missing derivations (e.g. `@RequestMapping` without
  `method`) only print a warning and continue with defaults;
- When annotation definitions live in **another module or a dependency jar**
  (common in multi-module projects: annotations in `xxx-framework`,
  controllers in a business module), as long as the business module depends
  on that jar and `includeDependencies` is `true` (the default), the plugin
  reads the full annotation definitions by reflection from the dependency
  classpath. Run document generation in the **module containing the
  controllers** (running in the annotation-definition module itself yields
  zero operations);
- Command-line usage: `-Dapi-doc.customAnnotations.annotations='a.B,c.D'`
  (comma-separated fully qualified names) and
  `-Dapi-doc.customAnnotations.declarations='{...}'`.
