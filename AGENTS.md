# AGENTS.md

Instructions for AI coding agents (Codex, Claude Code, Cursor, etc.) working
in this repository. Human-facing contribution and release process lives in
[CONTRIBUTING.md](CONTRIBUTING.md) — read it before making changes that touch
docs or versions.

## What this is

`api-doc-maven-plugin` (goal prefix `api-doc`): a standalone Maven plugin that
wraps the Smart-doc 3.1.2 parsing engine through its public API to generate
OpenAPI 3.x JSON, Excel, and all other Smart-doc output formats from Java
source + Javadoc. It is NOT an official Smart-doc project; never modify
Smart-doc sources, only call public API.

## Build & test

- Requires JDK 1.8+ and Maven 3.8+ (enforced by the enforcer plugin).
- Full validation before finishing any change: `mvn clean install`
  (compiles, runs all unit tests, builds the plugin jar). All tests must pass.
- No network access needed for tests; do not add network-dependent tests.
- The example project `examples/spring-api` is NOT part of the build; to try
  it: `mvn clean install` here, then run the mvn commands documented in the
  README inside `examples/spring-api/`.

## Layout

- `src/main/java/io/github/advelix/apidoc/` — one `@Mojo` class per goal
  (naming: `Generate<Mime>Mojo`, `OpenApiAliasMojo`), all extending
  `AbstractApiDocMojo`.
- `.../model/` — public configuration POJOs (must stay plain for Maven
  parameter binding).
- `.../internal/` — internal machinery (annotation resolver, JSON parser,
  Excel writer, class loader factory, Smart-doc builder delegation). New
  internal classes go here.
- `.../template/` — the `advelix-spring` `IDocBuildTemplate` discovered by
  Smart-doc via `META-INF/services` (do not break that wiring).
- `src/test/java/.../fixtures/combine/` — annotation fixtures; `Resolved*`
  are parsed as source, `RefOnly*` are class-path only.
- Docs: `README.md` (English, default), `README_zh-CN.md` (Chinese),
  `docs/` (deep topics), `CHANGELOG.md`, `docs/releasing.md` (maintainers).

## Hard rules

- Java syntax stays JDK 1.8-compatible (no `var`, no 9+ APIs).
- Every `.java` file carries the Apache 2.0 file header (copy the format
  from any existing file); exactly one header, at the top of the file.
- Goal names stay aligned with the official `smart-doc-maven-plugin`; do not
  rename goals without updating both READMEs' passthrough table.
- The Excel column headers (Chinese strings like `Path 参数`, `请求示例`) in
  `ExcelWriter` are literal output values — never translate or rename them
  casually.
- Log/error messages start with the `api-doc:` prefix.
- Comments describe the current design. Never commit temporary state,
  development-phase history, "legacy/old version" references, `TODO` /
  `FIXME`, or `@link`s to classes that do not exist.

## Docs sync (do this in the same change as the code)

- User-visible behavior change → update `README.md` AND `README_zh-CN.md`
  (keep their section structure identical), plus `docs/` for deep detail.
- New/renamed/removed goals or parameters → update the passthrough table and
  the parameter table in both READMEs.
- Version bump → 4 places: `pom.xml` (version + scm tag), `README.md`,
  `README_zh-CN.md`, `examples/spring-api/pom.xml`; plus a `CHANGELOG.md`
  entry (Keep a Changelog format).
- Git release tags NEVER use a `v` prefix and must be byte-identical to the
  pom's `<scm><tag>` (e.g. `1.0.1`, not `v1.0.1`); the tag, the scm tag and
  the CHANGELOG heading always carry the same string.
- Release steps: `docs/releasing.md`.

## Git & GitHub

- Default branch is `main`; never force-push to it.
- Commit messages: English, imperative, one sentence
  (e.g. `Fix pom scm connection scheme`).
- Small changes go straight to `main`; larger work uses short-lived
  `feat/`/`fix/` branches, deleted after merge.
- GitHub releases carry notes only (copy the `CHANGELOG.md` entry); never
  upload artifacts — the jar lives on Maven Central.
- Dependabot PRs (`.github/dependabot.yml`, Maven, weekly) are dependency
  upgrades: after applying one, run `mvn clean install` and, for smart-doc
  upgrades, verify the existing test suite before merging.
