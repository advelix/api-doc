# 自定义组合注解

[English](custom-annotations.md)

如果你的工程像 [示例工程](../README_zh-CN.md#示例工程) 里的 `OrderController` 那样，
用基于 Spring `@AliasFor` 的组合注解（例如自定义的 `@ApiController` / `@GetApi` /
`@PostApi`）声明 Controller 与接口，在 `customAnnotations` 参数里只列注解全限定名
即可，插件会**自动推导**其余配置：

```xml
<configuration>
  <customAnnotations>
    <annotations>
      <annotation>com.acme.web.ApiController</annotation>
      <annotation>com.acme.web.GetApi</annotation>
      <annotation>com.acme.web.PostApi</annotation>
      <annotation>com.acme.web.RequestApi</annotation>
    </annotations>
    <!-- 可选：显式声明，用于定义不可见或推导结果需修正的注解 -->
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

## 自动推导规则

注解定义先从工程源码解析，找不到再走依赖类路径反射：

| 规则 | 推导结果 |
| ---- | ---- |
| 类级带 `@RestController` / `@Controller` 元注解 | 入口注解（等价于 `entry`） |
| 元注解 `@RequestMapping(method = X)` 单值 | `methodType = X` |
| 元注解 `@RequestMapping(method = {X, Y, ...})` 多值 | `methodType = X` + 其余方法展开（同 path 每个 verb 一条 operation，Swagger 风格） |
| 自身声明 `method()` 属性（`RequestMethod`/`RequestMethod[]` 类型） | `methodProp = method`，HTTP 方法由**使用处**指定，支持单值与多值 |
| 以上都没有 | `methodType = GET`（并打印警告） |
| 自身 `String` 属性名是 `value`/`path`，或 `@AliasFor` 指向 `RequestMapping` 的 `value`/`path` | `pathProps`（按声明顺序）；都没有则缺省 `["value", "path"]` |
| 自身属性名 `params` / `consumes` / `produces` | 对应 `paramsProp` / `consumesProp` / `producesProp` |

## `declarations` 显式声明

可选 JSON，用于注解定义不可见（如第三方 jar 未暴露源码且未加依赖类路径）或推导
结果需修正的场景：

| 字段 | 位置 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `entry` | 顶层 | 与 `mappings` 至少一个 | 类级入口注解的**全限定名**数组，等价于 `@RestController` |
| `mappings[].annotation` | mapping | 是 | 方法级映射注解的全限定名 |
| `mappings[].methodType` | mapping | 与 `methodProp` 至少一个 | HTTP 方法：`GET`/`POST`/`PUT`/`PATCH`/`DELETE`/`HEAD`/`OPTIONS`（大小写不敏感） |
| `mappings[].methodProp` | mapping | 否 | 注解中动态声明 HTTP 方法的属性名（等价于 `@RequestMapping` 的 `method`） |
| `mappings[].pathProps` | mapping | 否 | 存放路径的属性名，按顺序查找；缺省为 `["value", "path"]` |
| `mappings[].paramsProp` / `consumesProp` / `producesProp` | mapping | 否 | 存放 `params` / `consumes` / `produces` 声明的属性名 |

## 合并与行为

- 同一注解名**显式 `declarations` 优先**：mapping 用显式整条替换，entry 取并集
  （显式在前）；
- 设置该参数后插件自动切换到增强解析模板（Smart-doc framework 值
  `advelix-spring`），不设置时行为与标准 Spring 流程完全一致；
- 若 `api-doc.configFile` 或命令行已显式指定其它 `framework` 值，会与
  `customAnnotations` 冲突并构建失败；
- 注解定义完全找不到（源码与依赖类路径都没有）会构建失败，提示改用
  `declarations` 显式声明；推导缺项（如 `@RequestMapping` 未写 `method`）只打印
  警告并按缺省值继续；
- 注解定义在**其他模块/依赖 jar**（多模块工程常见：注解在 `xxx-framework`，
  Controller 在业务模块）时，只要业务模块依赖该 jar 且 `includeDependencies` 为
  `true`（默认），插件会从依赖类路径反射读取完整注解定义；文档生成应运行在
  **包含 Controller 的模块**上（注解定义模块本身没有 Controller，跑出来是 0 接口）；
- 命令行写法：`-Dapi-doc.customAnnotations.annotations='a.B,c.D'`（逗号分隔
  全限定名），`-Dapi-doc.customAnnotations.declarations='{...}'`。
