# ADR-16 — W 语义澄清与 W Agent 编排域落位（不新增模块）

> 来源: 详细设计 10 册 §〇/§一（用户附件三册 V1.1 落地检视） | 日期: 2026-09-29 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 用户批量批准，凭证 = 需求检视报告 §十四 14.1 之 **R-31／R-32／R-37**，按各表「本设计推荐」列定版）
> 上游: 架构铁律 v2.0 :63/:221/:235 与 §0.6、ADR-15（承流口径）、ADR-14（确定性计算属主）、PRD-10 REQ-WAG-01~04
> 状态: **Accepted** — 各分册与 PRD-10 已按本 ADR 推荐项定版；DDL 现按批准口径落**迁移脚本文件**（不实跑库），业务代码接线属"改业务 Java/TS 代码"档，需逐项再授权（报告 §14.4）

---

## 1. 背景（Context）

用户提供的《ECOS W-Agent 详细设计》三册（V1.1）把平台描述为"五大 Engine"，其一为 **Decision Engine，代号 W**（一册 A-01），并在 §1.5 建议拆出 `agent-runtime / agent-api / agent-engine / agent-cli / model-runtime / cognitive-runtime` 六个新模块。三条实测与规则事实与此前提冲突：

| 实测/规则 | 证据 | 结果 |
|:--|:--|:--|
| W 的语义已被宪法锁定 | `.trae/rules/架构铁律.md` :63/:221 | **明文禁止** AI=W / LLM=W / Agent=W 三种写法；W = 五行之「火」= 明 = **C→W 转化** |
| W 域的职责归属 | 铁律 :235 | ai-engine 持有 "Decision Action Orchestration"，即决策**编排**在 ai，决策**依据**在 cognitive |
| 工程基线 | 铁律 :292 + 本 ADR 复验 `find ecos_backend -name pom.xml -not -path '*/target/*' -not -path '*/archive/*'` = **50**（含根聚合 ⇒ 49 reactor + 7 部署 JAR） | 新增 Maven 模块同时触发 `check-legacy-modules.ps1 baselineCount=12` 与 `ArchitectureTest.baselineModules=12` 两处门禁 |
| 编排能力已有存量 | 分册 10 X-1：ai-engine 内 **23 个** Controller 命中 agent/loop/skill/cron 语义 | 新建平行 Runtime 会造出第二套可绕过入口（违铁律 §2.5 底座收口） |

若照附件落位，代价是：术语层与宪法反义（已被 ADR-8/9 的"认知仅 4 表"同类问题灼伤过一次）+ 模块基线重定 + 双套 Agent 实现不可审计。

## 2. 决策（Decision）

1. **术语单源**：ECOS 内 **W ≠ Agent、W ≠ AI、W ≠ LLM**。附件的 "Decision Engine(W)" 在本仓一律改称 **W Agent 编排域**（英文 `wagent`），指"以 Agent 形态把认知（C）转化为行动与影响（W）"的编排能力域。附件"五大 Engine"表述替换为 ECOS 既有映射：认知引擎→cognitive-engine、决策编排→ai-engine、知识引擎→kb-engine、信息/本体→ontology-engine、数据引擎→data-engine，横切 security/runtime 单列。
2. **属主划分**：W Agent 编排域属主 = **ai-engine**（`engine/ai-engine`，寄居 `services/aiming:18084`）；cognitive 侧既有 `ecos_decision*`（V103/V151）定性为附件 §16.2 的 **Decision Basis（属 C）**，**不改表、不改 API**（API 只增不改），由编排域新增 Decision/Option/Action 对象经 cognitive api 门面消费。
3. **落位形态**：编排域落 ai-engine 现有 **api/impl/boot 三模块制内**，包结构 `com.chinacreator.gzcm.ai.wagent.{web|orchestrator|readiness|tool|candidate|evidence|port}`（分册 10 §四）。**不新增 Maven 模块、不新增部署 JAR、不新增 Docker 容器、不新增 schema**。
4. **API 前缀单源化**：新增 `/api/v1/wagent/*`（24 端点，见分册 10 §五）；附件 `/api/v1/agent`、`/api/v1/cognition/*` 一律不采纳；既有八种 agent 族前缀（X-2）保留不动并标 legacy（只增不改）。
5. **模块基线不变**：附件六模块诉求按"能力→既有载体"映射消解：`agent-*`→ai-engine、`model-runtime`→llm-gateway（铁律 §2.5 LLM 统一出口）、`cognitive-runtime`→cognitive-engine、`agent-cli`→仓根 `tools/`（见 ADR-21）。

## 3. 后果（Consequences）

- **正面**：术语不再与宪法反义；门禁零改动；编排域与护栏/审计/LLM 底座复用同一套接线（C122~C126 族）；附件对象链（Goal→Mission→Question→Run→Step）可在不改部署结构下实现。
- **代价**：ai-engine 体积增大（已 32 Controller），需以 ArchUnit 谓词约束"编排域不得直依其他引擎 impl"；`/api/v1/wagent/*` 新前缀必须三条滤波器逐条登记，否则复现 C234 鉴权空洞。
- **不可逆性**：低。若用户裁 R-37 选"新建模块"，包结构与契约可整体平移，但需同批改两处门禁基线并重开 PMO 基线记录。
- **未裁而先行写作的影响面**：PRD-10 全部 REQ-WAG、分册 10 §〇~§八、ARCH_SPEC v1.7 §1.4/§3.1/§十一 C227~C232 均标注"随 R-31/R-32/R-37"。

## 4. 关联

| 关联 | 位置 |
|:--|:--|
| 需求 | PRD-10 REQ-WAG-01/02/03；PRD-00 §1.3「W Agent（代号）」辨析行（v1.7 已落） |
| 设计 | 详细设计-10 §〇（八条产品红线采纳判定）、§四（包落位）、§五（前缀策略） |
| 偏差 | ARCH_SPEC §十一 **C227（W 语义）/ C232（六模块）/ C234（前缀漏配）** |
| 裁决 | 需求检视报告 §13.7 **R-31 / R-32 / R-37 / R-43** |
| 其他 ADR | ADR-15（承流口径不可假设独立）、ADR-14（确定性计算属主）、ADR-17（落位与 DDL）、ADR-19（Runtime 唯一性）、ADR-21（CLI） |

## 5. 变更日志

| 版本 | 日期 | 内容 |
|:--|:--|:--|
| v1.0 | 2026-09-29 | 首版（Proposed）。编号说明：ADR-15 已被卷 07 作为"1 承流单体"基线引用且实体存在，故本 ADR 集取 **16~21**（实测 `docs/10-架构/adr/` 原 10 个文件 = ADR-3/7/8/9/10/11/12/13/14/15）。 |
| v1.1 | 2026-09-29 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**（R-31／R-32／R-37 按推荐项定版）。正文决策未变，仅状态行与"未落 DDL"表述更正为"落迁移脚本文件、对库实跑待逐项再授权"。 |
