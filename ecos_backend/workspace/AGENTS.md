# workspace — 场景层部署单元（:18090 / F07-25/X-63）

> **模块文档首版**（F07-25 W189/C171 强制要求，之前 workspace 是 `ecos_backend` 里唯一无模块文档的部署单元）。登记依据：详细设计-07 `docs/30-设计/详细设计-07-场景层-workspace场景工作台-2026-09-29.md`。与「AGENTS 分层」跟 `ecos_backend/AGENTS.md` 的关系：本文件是**部署单元侧门户**（模块树/端点/两形态/接缝），架构红线与分层铁律以**根 AGENTS.md + 后端规范**为准，不复述。

## 定位

「场景层」独立微服务（微服务 v2 七 JAR 之一），承载**场景工作台**面向用户的生产能力（场景 CRUD + 完整度/连边 + 孤岛双闸 + 沙盘布局 + 认知四端点 + 运行/演练 + 向导支撑）。同时**物理上**也承流了 36 条非场景域端点（ontology 面 25 + query 面 5 + knowledge 面 4 + workbook 面 4），这与 §R-29 归因一致，已在册中披露，属已知遗留 —— 非本仓自造。

## 工程结构（本目录）

```
workspace/
├─ pom.xml                    # 聚合 POM（两颗子模块）
├─ workspace-impl/            # 业务实现（Controller/Service/Exception/DTO/VO），无独立入口
│   └─ src/main/java/com/chinacreator/gzcm/workspace/
│       ├─ controller/        # 17 个 Controller（场景 10 + 认知读侧 1 + 非场景域 6 + 认知认知面 1 = 17）
│       ├─ scenario/          # 场景域 Service（Completeness/StatusMachine/RunService/BaselineReferenceGuard/...）
│       ├─ exception/         # 领域异常族（F07-13 落地）+ WorkspaceExceptionHandler
│       ├─ knowledge/         # knowledge/workbook 子包（在 workspace 进程里但属知识面，X-56 已披露）
│       └─ workbook/          # workbook 子包（同上）
└─ workspace-service/         # 独立 :18090 部署入口
    └─ src/main/java/.../WorkspaceServiceApplication.java
    └─ src/main/resources/application.yml      （:18090 端口 / Security 排除项 X-5，待 F07-01 解决）
    └─ src/main/resources/logback-spring.xml
```

**gateway 形态**：`gateway` 单体 fat-JAR 打包 `workspace-impl` 但**当前用 REGEX 主动排除** `workspace.controller..*` 与 `workspace.twin..*`（X-3，`GatewayApplication.java:43/:56/:58`）—— 所以 gateway 形态内只有 Service/Client/Interceptor 无端点，属「B 案 workspace-service 独立 + gateway 反向代理」前的过渡状态；F07-01/R-24 落地后本表更新。

## 端点清单（实测，2026-10-02）

| 类 | 路由前缀 | 端点数 | 域归属 |
|:--|:--|:--:|:--|
| ScenarioController | `/api/v1/workspace/scenarios` | 8 | 场景（含已新增 `?purpose=forecast`、`GET /{id}/completeness`、`PATCH /{id}/status`） |
| ScenarioMindsController | 同上 | 7 | 场景（心智树） |
| ScenarioOptionsController | 同上 | 6 | 场景（跨引擎可用性探测，F07-13 X-17 已 503 化） |
| ScenarioCognitiveController | 同上 | 4 | 场景（认知 POST 四端点，F07-13 X-16 已 503 化） |
| ScenarioCognitionReadController | 绝对路径 `/api/v1/business/scenarios/{id}/cognition/{detect,operation-eval,hypotheses,benefits}` | 4 | 场景（F07-12/N4-N7 只读四端点，503 穿透） |
| ScenarioBindingLinkController | 同 scenarios | 4 | 场景（连边 CRUD + graph —— graph 完整度旁路，未删） |
| ScenarioPreValidateController | 同 scenarios | 1 | 场景（F07-05 完整度驱动版 pre-validate） |
| ScenarioRunController | 同上 | 2 | 场景（`run_mode` 正交列已由 F07-08 支持） |
| ScenarioSandboxController | 同上 | 2 | 场景（沙盘布局 V210） |
| BindingCatalogController | `/api/v1/workspace` | 1 | 场景（F07-05 N2 六类单源词表） |
| KnowledgeWorkbenchController | `/api/v1/scenarios/knowledge` + `/api/v1/knowledge` | 4 | 知识面（在 workspace 进程但属知识面，R-29） |
| WorkbookController | `/api/workbook` | 4 | workbook 面（在 workspace 进程但属 workbook 面，R-29） |
| ObjectController | `/api/v1/ecos/objects` | 9 | ontology/query 面（R-29） |
| ObjectQLController | `/api/query` | 1 | 同上 |
| ObjectRelationshipController | `/api/v1/ecos/objects` | 4 | 同上 |
| ObjectStateMachineController | `/api/v1/ecos` | 6 | 同上 |
| ObjectTimelineController | `/api/v1/ecos/objects` | 4 | 同上 |
| ObjectActionController | `/api/v1/ecos/objects` | 2 | 同上 |
| QueryHistoryController | `/api/query` | 2 | 同上 |

**总计 75**（较设计册的 68 增加 7 = 本册 F07-02/05/07/12/14 的新增只增端点）。**场景域 39，非场景域 36**。

## 两形态可达性（R-24 已批 B，F07-01 待落地）

| 形态 | 状态 | 备注 |
|:--|:--|:--|
| gateway 单包 :8080 | **生产 0 端点可达** | `GatewayApplication.java` 用 REGEX 排除 `workspace.controller..*`；F07-01 落地后此处补路由 |
| workspace-service :18090 | **可达但锁 Security AutoConfiguration** | `application.yml` `exclude: SecurityAutoConfiguration` + `ecos.header-auth.enabled: false`（X-5/X-19），68+ 端点在生产形态欲达需 F07-01 前提条件 |

## 数据面（当前 Java 引用的表）

- **主场景** `ecos_business_scenario`（V205 目标表 `ecos_scenario_definition` 已出切线，Java 现役引用主场景表未切）
- **绑定** `ecos_scenario_binding`（V206 目标表 `ecos_scenario_asset_binding`（含 `is_island`/`island_reason`）已出切线）
- **连边** `ecos_scenario_binding_link`（V207 目标 `ecos_scenario_binding_edge` 已出切线）
- **运行** `ecos_scenario_execution`（V209 目标同名字表已出切线，含 `run_mode`）
- **沙盘** V210 `ecos_scenario_sandbox_layout` 目标表（现役引用版本见 `ScenarioSandboxLayoutService`）
- **心智** `ecos_scenario_mind`（V208 目标 `ecos_scenario_mind_v2` 同名 —— V208 已出切线）
- **查询历史** V212（Java `QueryHistoryService` 现用内存 `CopyOnWriteArrayList`，F07-21 待切库）

**切线**：V205~V213 **仅落脚本文件、未对 PG 实跑**（R-14.4 未授权项，需逐项再确认）；实跑前 Java 一律指向旧表名，不引入新表 SQL（§E-3 批次纪律 零 DROP/零 ALTER 既有表）。

## 异常面（F07-13 已落地）

- 11 个领域异常类（`exception/`）：`WorkspaceException` 为根，`ScenarioNotFound` / `IllegalScenarioTransition` / `IslandBinding`（409 带清单）/ `SandboxReferenceForbidden` / `CognitiveEngineUnavailable`（503 穿透）/ `ExternalServiceUnavailable`（503）/ `ForbiddenOperation` / `OptimisticLockConflict` / `ActionValidation` / `WorkspaceInternal`
- `WorkspaceExceptionHandler` 13 个 `@ExceptionHandler`：涵盖上述 + `DataBridgeException`（common-api）→ 4xx 真实态 + `DataAccessException`/`Exception` 兜底 → 500 + traceId
- **红线**：不再出现 `ApiResponse.error(code,...)` 把 HTTP 200 当 4xx 用的“200-swallow”形态

## 承流前工作清单

以下项都是 F07 册内其余批次，是按册优先级列出**给下一窗**：
1. **F07-01/R-24**（P0 红线）：gateway REGEX 排除拆卸或反向代理路由 + `:18090` 等价 Security 链 + `preflight Test-WorkspaceAuthBaseline`
2. **F07-16**：拆除 `workspace-service/pom.xml` 对 4 个 `*-impl` 的直接依赖（sysman/data-engine/ontology-engine/buszhi），全部改经各引擎 api 门面 + REST 调 5 service（三层纪律 / X-8，见下节）
3. **F07-19**：JdbcTemplate 14 类 → Mapper + XML（MyBatis，schema 限定）
4. **F07-20**：V205~V213 实跑审批（PG 执行 + V213 对账视图对账），本表切换 Java 引用
5. **F07-18**：ABAC/RLS/审计全端点接线（等分册 01 SEC-02/03 契约）
6. **F07-24**：建 `workspace-impl/src/test` + `workspace-service/src/test`（P-2 底座），P-3 Playwright 工程落地本册 E2E
7. **F07-22/23**：前端假真值清零 + 主题/i18n/移动端/type 合规收口

## 依赖纪律（三层 / X-8 / F07-16）

- workspace-service **禁直引任何引擎或服务的 `*-impl`**（架构铁律 §服务间仅调 api 门面 + REST）。本仓当前**仍有 4 处未合规**（`workspace-service/pom.xml`）：`sysman-impl` / `data-engine-impl` / `ontology-engine-impl` / `buszhi-impl` —— 属 F07-16 待拆项，**存量只定性、不新增**；新代码一律只 `import` 各引擎 `*-api` 接口或经 `ScenarioOptionsController` 一类 REST 门面调 5 service，**禁止**把 `*-impl` 的 `@Service`/`@Component` 直接 `@Autowired` 进本模块 Bean。
- `-api` 允许直引（接口 + DTO/VO 契约），`-boot`/`-impl` 禁止。

## 反身门禁（这类仓独特有）

- **ArchitectureTest 不在本模块**：仓库级 `ArchUnit` 门禁在 gateway 部，本仓不重复登记
- **`_win_tasks/check-legacy-modules.ps1` baseline = 12**（含 workspace 两子模块）不改本模块的模块树拓扑
- 新增 Controller 必过三滤波器（根 AGENTS.md「其他约束」2）：`VersionPrefixRewriteFilter` + `SecurityConfig permitAll` + `ClearanceInterceptor` 豁免三处同步（双路径各写一遍）

```
