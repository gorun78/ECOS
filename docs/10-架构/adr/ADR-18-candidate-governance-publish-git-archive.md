# ADR-18 — Candidate 统一治理与发布的 Git 归档链路（一套候选池 + 发布即 commit+tag）

> 来源: W-Agent落地检视报告 WC-08（附件第三册 §6/§7.4） | 日期: 2026-09-29 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 用户批量批准，凭证 = 需求检视报告 §十四 14.1 之 **R-40**，按「本设计推荐」列定版；§2.4 的"发布未接 Git"是**需求文本缺陷**，其更正本就不等裁决）
> 上游: 架构铁律 v2.0 :549（Git 唯一出口 = runtime-access；六类设计资产；DB 只存在用版本）、:530（ABAC 经 security-engine evaluate）、§2.5（底座收口）、数据库访问规范 v1.2 MC01/DR01/ST06/ST07
> 状态: **Accepted** — 分册 10 的 `ecos_wagent_candidate` 属 **V227~V240** 范围，现按批准口径落**迁移脚本文件**（不实跑库，报告 §14.4）

---

## 1. 背景（Context）

### 1.1 附件主张

附件第三册 §6 定义**单一** `agt_candidate`：承载 12 种候选类型 + 评审工作流 + 发布 + TTL 30 天 + 四眼原则（§6.3、AC-07）；§7.4 定义"发布"动作，但**全文未提 Git 归档**。

### 1.2 ECOS 实测：同类语义已有四套载体

| 载体 | 位置（单源脚本） | 实测缺陷 |
|:--|:--|:--|
| KB 抽取候选池 | `ecos_knowledge.kb_extract_candidate`（V140:21，另 :42/:44 两条索引） | `id BIGSERIAL` 违 **MC01**；语义已含 status/review_note/confidence/ontology_id |
| Agent 审批 | `ecos_agent.agent_approval`（V50） | 所依赖的 `ecos_agent` schema **库内不存在**（见 **ADR-17 §1.2 / C230**） |
| 决策审批 | `ecos_decision_approval` | **裸表名**，违 **DR01**（schema 限定必填） |
| 工单审批 | `ecos_workflow_approval` | **裸表名**，违 **DR01** |

> 复现：`grep -rhoiE "CREATE TABLE( IF NOT EXISTS)? [a-z_.]*approval[a-z_]*" ecos_backend/gateway/src/main/resources/db/migration/`，实测仅上述 3 条；候选类仅 `kb_extract_candidate` 1 条。另 `ecos_decision_record`（V151，卷 07 R-30 已登记其 Java 零读零写）与 `ecos_decision`（V103）也承载决策语义，属**同族重复**但不在本 ADR 范围。

**后果**：附件若按 §6 直接建第五套，则评审中心与审计源双头，"谁被评审过 / 谁发布了什么"无法单点回答。

### 1.3 发布链路与铁律 :549 冲突

铁律 :549 规定：设计资产（含"Agent 协同模型""认知模型"两类，共六类）**历史版本唯一出口是 runtime-access 的 Git 链路，DB 只存"在用版本"**。实测出口组件确实存在且唯一：
`ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/GitService.java`（+ `GitServiceImpl.java`）。
附件 §7.4 把"发布"写成纯状态位翻转，**没有 Git 动作**，属对铁律的违背，不可原样采纳。

## 2. 决策（Decision）

### 2.1 Candidate 控制面单源（随 R-40）

- 统一候选池落 **`ecos_ai.ecos_wagent_candidate`**（分册 10 §六，V227~V240 范围），**不新建 schema**（ST07 五枚举，ADR-17 §2.2）。
- 12 类型用显式列 `candidate_type VARCHAR(32) NOT NULL`（枚举值走 sysman 配置字典单表分组，禁止硬编码在引擎里）。
- 跨域来源用 `source_domain VARCHAR(50)` + `source_ref VARCHAR(100)`（值可为他域表主键或对象定位符），**不建跨引擎外键**（ST09）。
- 检索字段显式成列（`ontology_id`/`confidence`/`status`/`expires_at`），快照正文 `payload_json` 且**不参与 WHERE/JOIN/索引**（MC02）。
- PK `VARCHAR(36)` 应用侧 UUID（MC01）；审计列齐 DR06，`version_no VARCHAR(20)` DR07、`domain` DR08，并带 `tenant_id`/`org_id`（随 R-36 的 REQ-PLT-24/25 口径）。

### 2.2 审批与评审

- 审批动作与状态迁移一律记 **Kafka `ecos.audit`**（ST06），**不再新建审批表**；四眼（提交人 ≠ 审批人）在应用层强校验。
- 权限判定经 security-engine ABAC（`POST /api/v1/security/policy-engine/evaluate`，铁律 :530），**禁止引擎内自写角色判断**（此项即 ARCH_SPEC **C255**，M0 直改）。
- security 不可用时默认 **DENY**（fail-closed）。

### 2.3 发布 = Git commit + tag（写进需求，不写成默认）

```
CandidateService.publish(candidateId, expectedVersion)
  1) ABAC evaluate（approve 权限 + 四眼校验）      → DENY 则 403，状态不变
  2) 组装资产正文（当前在用版本快照）
  3) runtime-access GitService：commit + tag        → 失败则整体中止，DB 不动
  4) DB 写：status=PUBLISHED + active_version + git_ref（仅此三列语义）
  5) Kafka ecos.audit 发发布事件（ST06）
```

- **无 Git ref 即不得存在 `PUBLISHED` 状态**：`git_ref` 为 `PUBLISHED` 的必要条件（应用层断言，随表补齐列）。
- DB 只存在用版本，历史由 Git 承担（:549）；重复发布按 `expected_version` 做乐观锁。

### 2.4 需求文本更正（M0，不等裁决）

PRD-10 / 分册 10 中凡述及"发布"处，**必须**显式写出"发布 = runtime-access GitService commit + tag + DB 仅存在用版本"，不得保留"状态置为已发布"的口径。此项属对铁律 :549 的一致性修复，属 M0。

### 2.5 TTL 与清扫

TTL 30 天的过期清扫走 **runtime-task**（铁律 §2.5 任务一律经 runtime-task），**不引入新调度器、不新增容器**。

## 3. 后果（Consequences）

- **正面**：评审中心单源，审计只有一条 Kafka 通道；发布动作可被 Git 历史证明（谁、何时、哪个版本），消除"DB 说已发布但无凭据"的虚假验收。
- **代价**：`kb_extract_candidate` 的既有链路（卷 04 K 系列）需加适配层；`ecos_decision_approval`/`ecos_workflow_approval` 的裸表名订正属于**存量 schema 限定改造**（与 C142/W160 同族），不可与本 ADR 混做。
- **风险**：若 R-40 反向裁"各域各留一套 Candidate"，则 §2.1/§2.3 作废，评审中心与审计永久双源。
- **验收标识**：
  - `mvn -Dtest=PublishServiceTest#publishWritesGitRefAndDbActiveVersionOnly`
  - `mvn -Dtest=PublishServiceTest#failsWhenGitCommitFails`
  - `mvn -Dtest=CandidateFourEyesTest#approverMustDifferFromSubmitter`
  - 已登记于 ARCH_SPEC §12.2 门禁表；Playwright 侧用例在 **P-3** 工程建成前一律记"未执行"。

## 4. 关联

| 关联 | 位置 |
|:--|:--|
| 需求 | PRD-10 REQ-WAG-14（候选治理）、REQ-WAG-15（发布链路）；PRD-04 **REQ-KB-06~09**（KB 候选收编口径；前缀定版 REQ-KB，原 REQ-KNO 作废） |
| 设计 | 详细设计-10 §三（Candidate 状态机）、§六（`ecos_wagent_candidate`）、§七（W258）；详细设计-04（KB 抽取候选既有链路） |
| 偏差 | ARCH_SPEC **C240**（四套候选/审批表）、**C241**（发布未接 Git，M0）、**C255**（引擎内自实现安全判定，M0） |
| 规则 | 铁律 :549 / :530 / §2.5；MC01/MC02/DR01/DR06~DR08/ST06/ST07/ST09 |
| 裁决 | **R-40**（本项）；与 **R-12**（零行/夹具表可否 DROP 重建）、**R-36**（tenant 前提）、**R-30**（决策落库属主）同批语境 |
| 其他 ADR | ADR-16（编排域属主）、ADR-17（落位与 DDL 形态）、ADR-19（单一 Runtime，Candidate 写入方） |

## 5. 变更日志

| 版本 | 日期 | 内容 |
|:--|:--|:--|
| v1.0 | 2026-09-29 | 首版（Proposed）。实测确认单源仅 1 张候选表 + 3 张审批表（其中 2 张裸表名违 DR01、1 张落在库内不存在的 `ecos_agent`），并据铁律 :549 把"发布"重定义为 Git commit+tag + DB 只存在用版本。 |
| v1.1 | 2026-09-29 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**（R-40 按推荐项定版）。正文决策未变；`ecos_wagent_candidate` 由"不落迁移脚本"改为"**落迁移脚本文件、不实跑库**"（§14.4 执行边界）。 |
