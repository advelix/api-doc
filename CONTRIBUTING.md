# Contributing | 贡献指南

[English](#english) | [中文](#中文)

---

## 中文

面向本仓库开发者的构建、维护与发版规范。AI 编码代理请参考根目录
[AGENTS.md](AGENTS.md)。

### 构建与验证

- 环境：JDK 1.8+，Maven 3.8+（`maven-enforcer-plugin` 会强制校验）。
- 提交前必须全量构建通过：

  ```bash
  mvn clean install
  ```

- 所有单元测试必须通过；测试不依赖网络，也不要新增依赖网络的测试。

### 开发后的文件维护清单

按变更类型对照检查，缺一项文档就可能和实现脱节：

#### 版本号变更（发版）

版本号的每一处引用都要同步，共 4 处：

1. `pom.xml`（`<version>` 与 `<scm><tag>`）
2. `README.md`
3. `README_zh-CN.md`
4. `examples/spring-api/pom.xml`

另外在 [CHANGELOG.md](CHANGELOG.md) 顶部按 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
格式新增版本条目。

#### 用户可见文档变更

- `README.md`（英文）与 `README_zh-CN.md`（中文）**成对修改**，保持章节结构一致；
- README 只保留概览 + 链接，深度内容放 `docs/` 并在 README 对应章节引用；
- 维护者向内容（发布流程等）放 `docs/`，不写进 README；
- 两个 README 顶部有目录（TOC），增删章节时同步锚点；
- `docs/` 目录结构：英文为主 + `_zh-CN` 后缀配对；10 篇以内保持平铺，
  超过后加 `docs/README.md` 索引页，量级到了再按主题分子目录。

#### 新增 / 改名 / 删除 goal

- goal 名保持与官方 `smart-doc-maven-plugin` 同名对齐；
- 同步两个 README 的「透传 Smart-doc 原生输出格式」表；
- Mojo 的 `@Mojo(name = ...)` 注解、类 Javadoc 示例命令与 README 保持一致；
- 涉及产物文件名变化时，同步 `docs/excel-output.md` 与 `docs/excel-output_zh-CN.md`（如适用）与参数表的默认值说明。

#### Excel 列定义 / 渲染规则变更

- 同步 `docs/excel-output.md` 与 `docs/excel-output_zh-CN.md`（成对）、`ExcelWriter.java` 与 `ExcelWriterTest`；
- Excel 列头（`Path 参数`、`请求示例` 等中文串）是生成文件的**字面值**，
  不得翻译或随意改名；确需修改时按上面三条同步。

#### 组合注解行为变更

- 同步 `docs/custom-annotations.md` 与 `docs/custom-annotations_zh-CN.md`（成对）、两个 README 的「自定义组合注解」概览、
  `CustomAnnotations` / `CombinedAnnotationResolver` 的 Javadoc；
- 推导规则变化必须在 `CombinedAnnotationResolverTest` 中有对应 fixture。

#### 参数（-D 属性）变更

- `AbstractApiDocMojo` 的 `@Parameter`、两个 README 的参数表、
  相关 docs 三处同步。

### 代码约定

- 所有 Java 文件（`src/main` 与 `src/test`）必须携带 Apache 2.0 文件头
  （格式与现有文件一致）；
- 语法保持 JDK 1.8 兼容（不使用 `var` 及 9+ API）；
- 只调用 Smart-doc 公开 API，不修改其源码；
- 分层：`Mojo`（每个 goal 一个类）→ `model/`（公开配置 POJO）→
  `internal/`（内部机制）→ `template/`（自定义 `IDocBuildTemplate`），
  新增内部类放 `internal/`；
- 日志与错误信息统一以 `api-doc:` 前缀开头；
- 注释纪律（发布质量要求，代码评审时检查）：
  - 注释描述当前设计，不写临时状态、开发阶段历史、"旧版/legacy"之类的演进痕迹；
  - 合入前不留 `TODO` / `FIXME` / `XXX`，要么解决要么删除；
  - Javadoc 中不引用不存在的类（`{@link}` 必须可解析）；
  - 不出现 AI 生成的占位式注释（如"这里做了 XX 处理"式的空转述）。

### 测试

- 行为变更必须带测试：解析/推导逻辑改 `internal/` 下对应测试；
  Excel 渲染改 `ExcelWriterTest`；模板行为改 `ApiDocSpringBootTemplateTest`；
- fixture 放在 `src/test/java/.../fixtures/` 下，命名表达其用途；
- 单元测试不启动 Maven 运行时、不访问网络。

### 发版流程

完整步骤见 [docs/releasing.md](docs/releasing.md)，摘要：

1. 按上面「版本号变更」一节同步 4 处版本号 + 写 CHANGELOG 条目；
2. `mvn -P central clean verify org.sonatype.central:central-publishing-maven-plugin:publish`
   （前置条件与 GPG/central portal 细节见 releasing.md）；
3. 在 central portal 点击 Publish，跟踪健康页；
4. `git tag X.Y.Z`（**不带 `v` 前缀**，必须与 `pom.xml` 的 `<scm><tag>` 完全一致）并创建 GitHub release（release 描述可直接用 CHANGELOG 对应条目）。

### 仓库卫生

- `.gitignore` 已覆盖 `target/`、`.idea/`、`.vscode/`、`.DS_Store`、`smart-doc-tmp/`；
- 不提交构建产物（含 `examples/*/target`）、IDE 个人配置、macOS 元数据文件；
- 示例工程 `examples/spring-api` 保持最小可运行：新增演示能力时同步
  README 的「示例工程」一节命令。

### GitHub 使用规范

#### 一次性设置（已完成，重建仓库时参照）

- 公开仓库，默认分支 `main`；
- 仓库描述与 topics：`maven`、`maven-plugin`、`java`、`openapi`、`api-documentation`、`smart-doc`；
- Dependabot：`.github/dependabot.yml`（Maven 生态，根模块与 `examples/spring-api`，每周）；
- 不配置 branch protection、CODEOWNERS、issue/PR 模板、release 模板；
  出现外部贡献者后再按需添加。

#### 日常

- 小改动直接进 `main`；有独立性的工作开短命分支（`feat/xxx`、`fix/xxx`），
  合并后立即删除；
- 提交信息：英文、祈使句、一句话说清（如 `Fix pom scm connection scheme`）；
- `main` 上禁止 force-push；
- issue 标签按需创建，起步：`bug`、`enhancement`、`documentation`；
- GitHub Release 只承载 release notes（描述复制 `CHANGELOG.md` 对应条目），
  **不上传构建产物**，jar 以 Maven Central 为准。

#### 发版 tag

见「发版流程」：tag 与 `pom.xml` 的 `<scm><tag>` 完全一致，不带 `v` 前缀。

---

## English

Contribution guide for external contributors. The full maintenance and
release process is documented in the Chinese section above (machine
translation is acceptable); the parts below are what you need for a
first PR. AI coding agents: see [AGENTS.md](AGENTS.md).

### Build & Validate

- Requires JDK 1.8+ and Maven 3.8+ (enforced by `maven-enforcer-plugin`).
- Before submitting, the full build must pass: `mvn clean install`.
  All unit tests must pass; tests never require network access.
- `examples/spring-api` is not part of the build. To try it, run
  `mvn clean install` in the project root first, then follow the
  commands in the README's Example Project section.

### Docs That Must Stay in Sync

- `README.md` (English) and `README_zh-CN.md` (Chinese) are a pair:
  always change both, keep the section structure and TOC anchors
  identical. Deep content lives in `docs/` (same pairing convention,
  `_zh-CN` suffix); the READMEs hold overview + links only.
- Version bump → 4 places: `pom.xml` (`<version>` and `<scm><tag>`),
  `README.md`, `README_zh-CN.md`, `examples/spring-api/pom.xml`, plus a
  `CHANGELOG.md` entry (Keep a Changelog format).
- Goal/parameter changes → the passthrough table and parameter table in
  both READMEs, plus the matching `docs/` pages. Excel column headers
  (Chinese strings) are literal output values: never translate them.

### Code Conventions

- JDK 1.8-compatible syntax only (no `var`, no 9+ APIs).
- Every Java file carries the Apache 2.0 header (copy the format from
  any existing file).
- Smart-doc public API only; never modify Smart-doc sources.
- Layering: Mojo (one class per goal) → `model/` (public POJOs) →
  `internal/` → `template/`; new internal classes go in `internal/`.
- Log/error messages start with the `api-doc:` prefix.
- No `TODO`/`FIXME`, no `@link`s to non-existent classes, no comments
  about temporary state or development history.

### Testing

- Behavioral changes require tests (resolver logic in `internal/`
  tests, Excel rendering in `ExcelWriterTest`, template behavior in
  `ApiDocSpringBootTemplateTest`).
- Fixtures live under `src/test/java/.../fixtures/`; tests do not
  start the Maven runtime and do not access the network.

### Releasing (maintainers)

See [docs/releasing.md](docs/releasing.md). The git release tag is
byte-identical to the pom's `<scm><tag>` — no `v` prefix (e.g. `1.0.1`).
The GitHub release body copies the matching `CHANGELOG.md` entry; no
artifacts are uploaded (the jar is distributed via Maven Central).
