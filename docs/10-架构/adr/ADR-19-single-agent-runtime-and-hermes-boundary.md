# ADR-19 — 单一 Agent Runtime 与 Hermes 边界（含引擎→services 编译期依赖的方向修正）

> 来源: W-Agent落地检视报告 WC-07（附件第一册 A-08 / 第二册 §1.6、§2.7、§11.3） | 日期: 2026-09-29 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 用户批量批准 = 推荐项 **① A 单一自建**，凭证见需求检视报告 §十四 14.1 之 **R-39**；§2.1/§2.2 的依赖方向与"两套并存宿主"事实属**已实测确定**，其文档更正与门禁补齐本就不等裁决）
> 上游: 架构铁律 v2.0 §2.5（底座收口：任务一律走 runtime-task、LLM 一律走 llm-gateway）、:292（模块基线 7 部署 JAR + 49 reactor）、PMO-74 H11-T2（agent-service 挂回 reactor）、PMO-C 依赖治理门禁 C1~C4
> 状态: **Accepted（推荐项 ① A 单一自建）** — W-Agent 第一阻塞项已解除定性；Skill/Cron/Run 写入方按 A 案写作，运行期整改属"改业务 Java/TS 代码"档，需逐项再授权（报告 §14.4）

---

## 1. 背景（Context）

### 1.1 附件主张与代码事实相反

附件第一册 A-08 / 第二册 §1.6 写"Hermes 仅作为设计参考，不直接嵌入其代码"。只读实测（`grep`，无修改）：

| # | 事实 | 证据 |
|:--|:--|:--|
| ① | `HermesMCPClient` 被 ai-engine 两个控制器注入使用 | `engine/ai-engine/ai-engine-impl/.../controller/CronJobController.java:4,15,26-27`、`SkillController.java:4,15,26-27`（`@Qualifier("ecosHermesMCPClient")`，注释自认 "backed by Hermes MCP cronjob_* tools / Hermes skills"） |
| ② | `HermesMCPClient` 类本体在 **`services/agent-service`** | `services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/mcp/`：`HermesMCPClient` / `HermesDelegationAdapter` / `HermesMemoryAdapter` / `HermesSessionAdapter`（4 个类） |
| ③ | 另有以"Hermes 引擎未就绪"为失败态的三处 + 计量默认值 | `AgentConfigService.java:178`、`AgentCallController.java:53`、`ClassificationController.java:68`；`QuotaFilter.java:207` 含 `/api/hermes/`；`TokenMeterAspect.java:51/87/95` 默认 model `"hermes-agent"` |
| ④ | 自建 Loop 与 Hermes 并存且被测试承认 | `AgentLoopService.java:32` 自称"替代 Hermes Agent Loop 的纯 Java 实现"；`gateway/src/test/java/com/chinacreator/gzcm/gateway/ModuleDependencyArchTest.java`（:275 附近）承认两套并存 |

### 1.2 本 ADR 的两项新实测发现（原检视报告未覆盖）

**(a) 引擎对未部署模块存在 compile scope 直接依赖。**
`engine/ai-engine/ai-engine-impl/pom.xml:83-88` 显式声明依赖 `agent-service`（**无 scope，即 compile**），其唯一用途是取得 ② 的 `HermesMCPClient` 全限定名。分层方向因此倒置：**engine 层依赖 services 层实现**（而非 services 组装 engine）。同时该 pom 还直依 `dccheng-impl`(:34)、`sysman-impl`(:79)，即引擎直依他域 impl。

**(b) `agent-service` 的代码确实运行在生产进程内，"未部署"仅指"非独立 JAR"。**
`services/aiming/pom.xml` 头部注释自己写明了这一点：
- `:15` "聚合 ai-engine (火·W: Agent/Loop/Memory/LLM) + agent-service runtime"
- `:18` `ai-engine-impl ──import──> services.agent.* (runtime model/service)`
- `:21-22` `aiming -> ai-engine-impl (传递带入 agent-service 依赖? 不, 需显式)` / `aiming -> agent-service (runtime 实现)`
- `:24-25` "ai-engine-impl 已 import services.agent.* ⇒ 必须显式声明 agent-service 依赖，否则 aiming 独立打包时 agent runtime 类不在 classpath"
- `:41-45` 该依赖实体存在

⇒ **更正既有表述**：此前 R-41 / WC-09 写作"`ecos_agent.*` 仅被不在 7 部署 JAR 的 `services/agent-service` 引用"，容易被读成"写入方不运行"。准确口径是：**写入方代码随 `aiming`（:18084）一起部署并运行**，只是其宿主模块没有独立 JAR 身份。

**(c) 由 (b) + ADR-17 得到的待复核风险（标注为推断，非实测结论）**：`agent-service` 内以 `ecos_agent.` 限定读写，而**库内无 `ecos_agent` schema**（ADR-17 §1.2 实测①）。因此该路径在 service 态要么抛错、要么被 `search_path`（V47 曾 `ALTER DATABASE … SET search_path`，即 R-22/C142 待撤项）兜到其它 schema —— `public` 下存在同名双镜像与此相容。**必须以运行期证据（日志 + 会话级 `SHOW search_path`）复核后再定结论**，本 ADR 不据推断改代码。

### 1.3 合规问题清单

| 问题 | 违反 |
|:--|:--|
| 定时任务经 Hermes MCP `cronjob_*` 而非 runtime-task | 铁律 §2.5 |
| Hermes 依赖的许可/供应链审查**仓内 0 登记** | 附件第二册 §14.1 自己把它列为威胁；ARCH_SPEC **C238** |
| engine→services compile 依赖、且 `ModuleDependencyArchTest` **无"engine 不得依赖 services"规则**（实测 grep 零命中） | PMO-C 依赖治理门禁 C1~C4；ARCH_SPEC **C257** |
| 双 Runtime 并存且都在同一进程 | 可审计性；ARCH_SPEC **C236** |

## 2. 决策（Decision）

### 2.1 依赖方向修正（M0，不等裁决）

1. 新增 ArchUnit 规则：`engine..` 不得依赖 `com.chinacreator.gzcm.services..`（与既有 5 条依赖规则同处、同基线，不改 12 项 baseline 计数）。规则未通过前，**禁止**向 `ai-engine-impl` 新增任何 `services.agent.*` import。
2. 把 ai-engine 对 Hermes 的取用改为**面向 api 的 SPI**：在 `ai-engine-api`（或 `llm-gateway`，按 §2.5 底座收口择一，默认 `llm-gateway`，因 Agent 外呼本质是模型/工具调用）定义 `AgentRuntimeProvider` 接口，`services/agent-service` 提供实现，`aiming` 在组装期注入。`ai-engine-impl` 只依赖接口。
3. `TokenMeterAspect` 默认 model `"hermes-agent"` **必须去除硬编码**（改由 sysman 配置字典 `config_group` 读取，缺失即 FAIL 而非静默用外部默认值）。
4. `QuotaFilter` 的 `/api/hermes/` 前缀属**路由单源**问题，随分册 10 前缀单源化（ARCH_SPEC **C234/C249**）一并处理。

### 2.2 单一 Runtime 裁定口径（随 R-39，三选一）

| 选项 | 内容 | 代价 |
|:--|:--|:--|
| **A（推荐）** | **单一 Agent Runtime = ECOS 自建**（`ai-engine` 内的 `AgentLoopService` 系）。Skill/Cron 定义迁 `ecos_ai.ecos_wagent_*`，调度改投 **runtime-task**；Hermes 降级为**可选外部 provider**，须经 `AgentRuntimeProvider` SPI 接入，且**许可 + 供应链审查登记为前置件** | 需迁移 2 个控制器的调用点；需补许可证据 |
| B | 承认 Hermes 为**运行期硬依赖**：改写附件 A-08/§1.6，并把许可/供应链审查列为 P0 前置件 | 平台对外部进程产生硬依赖；现状无许可证据；单点故障进入主链路 |
| C | 维持并存但显式划边界 | **不推荐**：双套不可审计，附件自己把许可列为威胁 |

**本 ADR 按 A 写作**，分册 10 的 Skill/Cron 需求文本亦按 A 描述；若用户裁 B，则附件前提与 ADR-19 §2.2 一并改写。

### 2.3 宿主模块身份（随 R-41）

`services/agent-service` 三选一：① 收编为 W Agent 载体（进部署基线，需重开 PMO 基线）；② **定性遗留、停写并归档**（默认推荐，代码保留在 reactor 但不再承接新语义）；③ 维持现状。无论选何项，**必须先逐表核对 `ecos_agent.` 前缀的实际读写方**（含 §1.2(c) 的运行期复核）。

### 2.4 不做的事

不新增 Maven 模块（:292，随 R-37）、不新增 Docker 容器、不把 Hermes 变成新部署单元。

## 3. 后果（Consequences）

- **正面**：Agent 执行路径唯一 ⇒ Run/Step 审计可闭环；引擎不再反向依赖 services；外部依赖若保留也是"可插拔 provider"而非硬编码。
- **代价**：SPI 化涉及 `ai-engine-impl` 的 2 个控制器 + `aiming` 组装；`AgentLoopService` 需补齐 Hermes 侧已有的 cron/skill 语义后才可切换（分册 10 F10-11/F10-12）。
- **风险**：若 R-39 裁 B，则 §2.1-2 的 SPI 化仍是必需（方向修正与宿主选择是两件事），但"许可登记"升为 P0 阻塞；若裁 C，ARCH_SPEC §12.2 需新增"双 Runtime 边界"门禁否则不可证。
- **验收标识**：
  - `mvn -Dtest=ModuleDependencyArchTest#engineMustNotDependOnServices`（新增规则，M0）
  - `mvn -Dtest=WAgentScheduleTest#cronExecutionGoesThroughRuntimeTask`
  - `mvn -Dtest=TokenMeterTest#modelComesFromConfigDictNotHardcodedDefault`

## 4. 关联

| 关联 | 位置 |
|:--|:--|
| 需求 | PRD-10 REQ-WAG-05（单一 Runtime）、REQ-WAG-11/12（Skill/Cron 落位）；PRD-06 REQ-AI-06~11；**PRD-01 REQ-PLT-28（engine→services 依赖门禁，v1.1 已落地，随本 ADR §2.1 的 M0 项）** |
| 设计 | 详细设计-10 §二（编排控制流）、§三（Skill/Cron）、§七（W236→C236、W255→C237、W238→C238 等，新增 **W275/W276**）；详细设计-00（runtime 底座） |
| 偏差 | ARCH_SPEC **C236**（A-08 与实测相反）、**C237**（调度绕 runtime-task）、**C238**（许可 0 登记）、**C251**（写入方宿主身份）、新增 **C257**（engine→services compile 依赖 + 无 ArchUnit 规则）、**C258**（agent-service 随 aiming 部署，更正"未部署"表述） |
| 规则 | 铁律 §2.5 / :292 / :435；PMO-C 依赖治理门禁 |
| 裁决 | **R-39**（第一阻塞项）、**R-41**（宿主身份）、与 **R-22**（`search_path` 撤销）联动复核 |
| 其他 ADR | ADR-16（编排属主）、ADR-17（表落位）、ADR-20（模型与能力路由，SPI 落点候选）、ADR-10（对外只经 gateway） |

## 5. 变更日志

| 版本 | 日期 | 内容 |
|:--|:--|:--|
| v1.0 | 2026-09-29 | 首版（Proposed）。新增两项实测发现：`ai-engine-impl/pom.xml:83-88` compile 直依 `agent-service`（且 `ModuleDependencyArchTest` 无 engine→services 禁令）；`services/aiming/pom.xml:15,18-25,41-45` 自证 `agent-service` 随 `aiming` 部署运行。据此更正 R-41/WC-09 的"未部署"表述，并把 `ecos_agent.` 写入路径的运行期行为标为**待复核推断**。 |
| v1.1 | 2026-09-29 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**，取推荐项 **① A 单一自建**（R-39）；"待复核推断"一项仍保留为只读复核前置（§14.4 残留项），不因批准而升格为事实。 |
