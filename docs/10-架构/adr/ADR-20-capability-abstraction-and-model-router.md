# ADR-20 — 模型与能力抽象、Model Router 的单源落位（与 06 册 F06-18 合并立项）

> 来源: W-Agent落地检视报告 WC-15（附件第二册 §4/§5 Model & Capability Center + Model Router、第三册附录 A `aim_*`） | 日期: 2026-09-29 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 用户批量批准，凭证 = 需求检视报告 §十四 14.1 之 **R-47**，按「本设计推荐」列定版；§2.3 的"默认 model 硬编码"与 §2.5 的"裸表名"属确定性违规，其直改本就不等裁决）
> 上游: 架构铁律 v2.0 §2.5（LLM 调用一律走 llm-gateway）、:292（模块基线）、:530（ABAC）、数据库访问规范 v1.2 ST07/MC01/MC02/DR01、ADR-11（向量载体 pgvector）、ADR-13（列存二选一）、ADR-16（编排属主）、ADR-17（DDL 形态）
> 状态: **Accepted** — `aim_*` 相关建表在 **V227~V240** 范围，现按批准口径落**迁移脚本文件**（不实跑库，报告 §14.4）

---

## 1. 背景（Context）

### 1.1 附件主张

附件第二册要求建立 **Model & Capability Center**（模型/供应商/能力注册 + 评分 + 灰度）与 **Model Router**（按成本/延迟/能力/风险选择模型），并给出 **Tool Contract**（工具契约 + 渐进披露）。第三册附录 A 用 `aim_*` 前缀为其建表。第二册 §1.5 另建议拆出 `model-runtime` 模块。

### 1.2 ECOS 实测底座

| 项 | 实测 | 证据 |
|:--|:--|:--|
| LLM 统一出口已存在 | `runtime/llm-gateway` 内已有 `LLMGatewayService(Impl)` 及 10 个子包：`callback / config / gateway / metrics / model / profile / repository / scheduler / security / session` | 目录实测 |
| 模型画像已在 llm-gateway | `llm/model/ProfileConfig.java`、`llm/profile/ProfileManager(Impl).java`（配置态画像 + 管理器）；`llm/model/AgentCallLog.java`（调用日志载体） | 文件实测 |
| **无模型/能力注册表** | 单源 125 脚本内，`model/capability/provider` 语义的建表**只有 1 条**：`V124__ecos_cognitive_model.sql:7` `CREATE TABLE IF NOT EXISTS ecos_cognitive_model`——且**裸表名违 DR01**，语义属认知模型而非 LLM 供应商 | `grep -rhoiE "CREATE TABLE( IF NOT EXISTS)? [a-z_.]*(model\|capability\|provider)[a-z_]*" …` |
| 能力（Skill/Tool）注册表近空 | 同批 grep 下 skill/tool 语义仅 `public.ecos_skill` 1 条（落 `public`，不在引擎 schema 五枚举内） | 同上 |
| 06 册已立同类项 | 详细设计-06 **F06-18**（模型注册/路由）已在册，与附件 Model Center 语义重合 | ARCH_SPEC **C245** |
| 默认值硬编码 | `TokenMeterAspect.java:51/87/95` 默认 model `"hermes-agent"` | 见 ADR-19 §1.1③ |

**结论**：附件所提能力**不是空白**，而是"底座已在 llm-gateway、注册态数据缺表、命名与前缀另立一套"。若照附件建 `aim_*` + `model-runtime` 模块，将造出**第二个模型注册与路由源**，并直接违 :292 与 §2.5。

## 2. 决策（Decision）

### 2.1 落位（随 R-47，与 06 册 F06-18 合并为同一立项）

| 附件对象 | ECOS 落位 | 说明 |
|:--|:--|:--|
| Model Registry / Provider / 能力评分 | **`ecos_ai`**（控制域五枚举内） | 表名 `ecos_wagent_model_def` / `ecos_wagent_model_version` / `ecos_wagent_provider`（V227~V240 范围）；**不新建 `aim_*`、不新建 schema**（ST07） |
| Model Router 决策 | **`llm-gateway` 模块内扩展**（`profile`/`gateway` 子包既有线上） | 与 06 册 F06-18 合并立项，**不得两处各写一套路由算法**；`model-runtime` 新模块提案作废（:292） |
| Tool Contract | **`ecos_ai.ecos_wagent_tool_contract`** | 与分册 10 的 `ecos_wagent_skill` 同批；契约正文 `contract_text`（MC02），检索字段显式成列 |
| 运行期调用日志 | 复用 llm-gateway `AgentCallLog` + Kafka `ecos.audit`（ST06） | **不在 `ecos_ai` 再造一张调用日志表**（避免 C240 同族重复） |
| 模型能力/成本统计 | 派生统计量落业务域 `ecos_dw`，经 data-engine 写通道 | ADR-14 口径；**不落 `ecos_ai`** |

### 2.2 路由判据与治理

- 路由输入四要素：**能力标签 / 成本 / 延迟 / 风险等级**。风险等级来自 security-engine ABAC（铁律 :530），**引擎内不得自写风险判定**（ARCH_SPEC **C255**，M0）。
- 灰度与回滚：`model_version.status` 只允许 `DRAFT/CANARY/ACTIVE/RETIRED`，切换必须走 Candidate 发布链路（ADR-18 §2.3），即**路由配置的变更也是设计资产变更，需 Git 归档**。
- **grade 禁 LLM 写入**：能力评分只能由确定性计算或人工评审写入，不得由模型自评回填（分册 10 DIK/C Readiness 同一条纪律）。

### 2.3 确定性直改项（M0，不等裁决）

1. `TokenMeterAspect` 默认 model `"hermes-agent"` **去除硬编码**，改由 sysman 配置字典（`config_group` 单表分组）读取；缺失即 FAIL。
2. 计量与配额必须与路由同源：`QuotaFilter` 的前缀表随路由单源化（ARCH_SPEC **C234/C249**）一并收敛。

### 2.4 渐进披露（附件 §5 的正实现）

Tool Contract 采用"摘要常驻 + 全文按需"：列表接口只返 `name/summary/risk_level/contract_digest`；全文经 `GET /api/v1/wagent/tools/{id}/contract` 单取。**不得**在每次 Run 的 context 里塞全部工具全文（成本与安全面都不可控）。

### 2.5 存量整改

`V124__ecos_cognitive_model.sql:7` 的裸表名属 **DR01 违规**，订正为 schema 限定（按语义应属 `ecos_cognitive`）。`public.ecos_skill` 的归位属存量动作，**只定性、不擅迁**，登记待裁（与 R-19/R-33 同族）。

## 3. 后果（Consequences）

- **正面**：模型注册/路由只有 1 个源、1 个进程出口；Agent 侧只消费路由结果，不复制路由算法；工具契约可审计、可灰度、可回滚。
- **代价**：06 册 F06-18 与本 ADR 必须合并为一份需求条目（否则 REQ-AI 与 REQ-WAG 双写）；`ecos_ai` 新增 4 张表的 DDL 需随 R-47 定稿后才落脚本。
- **风险**：若 R-47 反向裁"另立 model-gateway 独立模块"，则同时触发 R-37 的基线重开与两处门禁（`check-legacy-modules.ps1 baselineCount=12` / `ArchitectureTest.baselineModules=12`）同批改。
- **验收标识**：
  - `mvn -Dtest=ModelRouterTest#routeRespectsCapabilityCostLatencyAndRisk`
  - `mvn -Dtest=ModelRouterTest#riskLevelComesFromSecurityEngineNotLocalRule`
  - `mvn -Dtest=ToolContractTest#listEndpointReturnsDigestOnly`
  - `mvn -Dtest=WAgentDdlShapeTest#cognitiveModelDdlIsSchemaQualified`（DR01）

## 4. 关联

| 关联 | 位置 |
|:--|:--|
| 需求 | PRD-06 REQ-AI-06~11（含 F06-18 合并项）、PRD-10 REQ-WAG-08/09/10（模型中心 / 路由 / 工具契约） |
| 设计 | 详细设计-06 §（F06-18 模型注册与路由）、详细设计-10 §三（Tool Contract 与渐进披露）、§六（4 张 `ecos_wagent_model_*` 草案） |
| 偏差 | ARCH_SPEC **C245**（Model Center 与 F06-18 重复）、**C228**（三前缀违 ST07）、**C255**（引擎内自实现安全判定）、**C234/C249**（前缀与路由单源） |
| 规则 | 铁律 §2.5 / :292 / :530；ST07 / MC01 / MC02 / DR01 / DR06~DR08 / ST06 |
| 裁决 | **R-47**（本项）、**R-37**（不新增模块）、**R-33**（前缀收编）、**R-18**（llm-gateway 对 security 的取密/ABAC 形态：本 ADR §2.2 的 ABAC 判据落地依赖 R-18 结案）、**R-19**（AI 数据层归属） |
| 其他 ADR | ADR-16（W 语义与编排属主）、ADR-17（落位与 DDL 形态）、ADR-18（变更走 Candidate 发布）、ADR-19（`AgentRuntimeProvider` SPI 候选落点即本模块） |

## 5. 变更日志

| 版本 | 日期 | 内容 |
|:--|:--|:--|
| v1.0 | 2026-09-29 | 首版（Proposed）。实测确立"llm-gateway 已有 profile/metrics/session/security 出口、单源内无任何模型/能力注册表、唯一近义表 `ecos_cognitive_model` 违 DR01"，据此把附件 Model & Capability Center 定性为"补注册态数据 + 合并 06 册 F06-18"，而非新建模块/新建中心。 |
| v1.1 | 2026-09-29 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**（R-47 按推荐项定版，与 06 册 F06-18 合并立项）。正文决策未变；`aim_*` 建表由"不落迁移脚本"改为"**落迁移脚本文件、不实跑库**"（§14.4 执行边界）。 |
