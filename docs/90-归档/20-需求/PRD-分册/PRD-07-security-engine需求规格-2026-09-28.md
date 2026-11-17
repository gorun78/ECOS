# PRD-07 security-engine 需求规格（分册 07）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.3（**v1.3（2026-09-29 批量批准定版）**：需求检视报告 **§十四** 按各表「本设计推荐」列批准 R-1~R-72 ⇒ 本册 §五 **REQ-SEC-06~10 与 §5.6 REQ-SEC-11 全部转正式需求并计入已批准基线**（SEC-06~10 随 **R-35 / Z-7**；**SEC-11 随 R-72 并与卷 09 R-66 并案** ⇒ 新立 `MODEL_ADMINISTRATOR`／`COGNITIVE_MODEL_OWNER` 两角色及属主，只经 security-engine + sysman 门面 + OPA 生效，禁硬编码判定）。**"已批准"仅指需求文本生效**：`RoleRegistryTest`／`NoHardcodedRoleCheckTest` **未建 ⇒ 记"未执行"**；角色种子脚本只落**单源迁移目录的文件**、不实跑库，改鉴权代码需逐项再授权（报告 §14.4）。v1.2（2026-09-29 接续：§5.6 新立 **REQ-SEC-11 系统角色扩展与属主**（模型管理员／认知模型负责人，随 **R-72**，与卷 09 R-66 并案；§5.6 前原"明确不做"顺延为 §5.7；时点"草案、不计入基线"已被 §十四 取代）；v1.1（2026-09-29 接续 W-Agent 制品：新增 §五 REQ-SEC-06~10 承接需求，**全部为草案、待裁决（R-35 / R-42 组 / Z-7）**，未获批准不计入已批准基线；§5.4 依实测**纠正"审计无哈希链"的误述**——`AuditHashChainService` 已存在，缺口是 fail-open/分租户/WORM/DR01 四项）
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 覆盖: REQ-SEC-01~04（已列）+ **REQ-SEC-06~10（v1.1 立，2026-09-29 §十四 **已批准**）+ REQ-SEC-11（v1.2 立，2026-09-29 §十四 **已批准**）**
> 模块: `ecos_backend/engine/security-engine`（sysman:18081）

---

## 一、REQ-SEC-01 领域链路安全接入（P0）

### 1.1 敏感数据目录（年度经营预测场景，首批）

| 数据对象 | 字段 | 敏感级 | 防护组合 |
|---|---|:--:|---|
| 合同 | base_amount | S2 | RLS(部门域) + CLS(非财务隐藏) + mask(AMOUNT 对无权角色) |
| 资源投入 | staff_ref, hourly_rate | S3 | staff_ref 脱敏存储（工号哈希）；rate CLS 仅财务/HR |
| 成本 | amount(SALARY 类) | S3 | RLS + CLS + mask |
| 成本 | amount(MANAGEMENT/MARKETING) | S2 | RLS + CLS |
| 预测结果 | 利润/收入聚合值 | S2 | RLS（项目负责人仅本项目）；导出通道同权 |
| 下钻明细 | stage_fact 行级 | S2 | RLS 行过滤 |

> 金额列不做存储加密（PRD-02 §1.3-4 裁定：聚合计算需要，防护=RLS+CLS+mask 组合）；该例外须在 ST03 评审记录登记理由。

### 1.2 策略种子（DDL 只加不删；RLS/CLS 策略表无物理外键——既有裁定）

**RLS（ecos_rls_policy，同表多策略 AND 连接、按 priority）**：

| 策略 | 表 | 条件模板 | 角色 |
|---|---|---|---|
| rls-project-owner | ecos_biz_stage_fact / cost / resource | `project_id IN (SELECT project_id FROM ecos_biz_project_attribution WHERE department_id = :userDeptId)` | 项目负责人 |
| rls-dept-scope | 同上 | `department_id = :userDeptId` | 部门用户 |
| rls-finance-all | 同上 | `1=1` | 财务负责人 |

**CLS（ecos_cls_policy，用户级>角色级>全局）**：非财务角色隐藏 `hourly_rate`、SALARY 成本 `amount` 列。

### 1.3 三通道强制点

| 通道 | 强制点 | 规格 |
|---|---|---|
| 页面查询 | datanet facts 查询端点前置 `rls/apply`，返回前 `cls/columns` + `mask` | 既有 §2.4-1/2/3 落地点清单化 |
| **导出** | 导出服务复用与页面查询**同一** RLS/CLS 管道（禁止导出走独立 SQL 绕过滤）；导出文件头写入 权限范围+口径+asOf（联动 SEC-04） | 新增导出前置检查 |
| **AI 工具** | PRD-06 AI-01 OPA 裁决 + obligations 脱敏；工具后端查询同样过 RLS/CLS（双保险，OPA 管"能不能调"，RLS/CLS 管"看到什么"） | 护栏接线联动 |

### 1.4 测试矩阵（角色 × 通道 × 期望）

| 角色 | 页面 | 导出 | AI |
|---|---|---|---|
| 财务负责人 | 全量可见 | 全量 | 全量 |
| 项目负责人(p1) | 仅 p1 行；rate 列不可见 | 仅 p1；文件头含范围 | forecast.drilldown p1 ALLOW / p2 DENY |
| 部门用户(d01) | 仅 d01 行 | 仅 d01 | 同上按部门域 |
| 无权用户 | 403/空集（不泄露存在性） | 拒绝 | GUARDRAIL_DENIED |

### 1.5 验收标准

1. 测试矩阵 12 格全过（集成测试，PMO-73 G3 联动）；
2. security 进程停止 → 三通道全部默认 DENY（页面 503 拒绝、导出拒绝、AI FAIL_CLOSED）；
3. 导出绕过滤的旁路代码 grep 审查 = 0（导出与查询共用过滤管道的代码结构证明）。

---

## 二、REQ-SEC-02 默认 DENY 守护与三滤波器 CI 化（P0）

### 2.1 whitelist 唯一清单源

`auth.whitelist.paths`（application.yml）为唯一事实源；**匿名登记文件** `docs/40-实现/auth-anonymous-registry.md`：每条匿名路径必须登记（路径 + 理由 + 批准人），未登记的 permitAll = 违规。

### 2.2 匿名回归脚本（PMO-73 G3-T3）

```
输入：whitelist 清单 + 匿名登记文件
对每条 whitelist 路径：
  case A（在匿名登记）：无 token 请求 → 期望非 401/403（真匿名）
  case B（不在登记）  ：无 token 请求 → 期望 403（默认 DENY 证明）
  两种 case：带 token 请求 → 期望非 401/403
输出：违规清单（case B 却放行的路径 = P0 事故级，直接 FAIL）
挂点：CI pr-gate 新阶段；本地可跑（脚本落 ecos-tests/ 或 gateway test）
```

### 2.3 新增端点检查单（评审模板固化）

1. 该端点应匿名？否 → permitAll 不写；
2. 前缀在 `V1_REWRITE_MAP` 内？是 → 鉴权/豁免层只写裸路径；否 → 双路径各写一遍；
3. 含连字符/近似前缀 → 显式写完整前缀（Ant 路径陷阱）；
4. 提交前跑匿名回归脚本本路径三项断言。

### 2.4 验收

1. 脚本对现网 whitelist 全量扫描 0 意外放行；
2. 故意加一条未登记 permitAll → CI FAIL（负样例演练一次）；
3. 评审模板含检查单（Reviewer skill 同步）。

---

## 三、REQ-SEC-03 前端 authHeaders 统一（P1，= D5 / PMO-73 G2-T1/T2）

### 3.1 模块规格 `ecos_frontend/src/services/auth.ts`

```typescript
export function getToken(): string | null;              // localStorage 读取（键名沿用现状）
export function authHeaders(): Record<string, string>;  // { Authorization: `Bearer ${token}` }，无 token 返回 {}
export function parseTokenPayload(): TokenPayload | null; // 唯一 JWT 解析实现（base64url + JSON，容错 null）
export function setAuthGracePeriod(ms?: number): void;  // 10s 宽限期（既有登录误伤修复，逻辑迁入）
export function handleAuthExpired(): void;              // 宽限期内 401 只 throw 不清 token
```

### 3.2 替换清单（2026-09-28 grep 实证 ≥10 文件）

`pages/data-workbench/api.ts` · `services/taskCenter.ts` · `pages/DataAssetsDashboard.tsx` · `pages/business-workbench/BusinessObjectExplorer.tsx` · `services/aiworkbenchApi.ts` · `pages/MonitoringCenter.tsx` · `pages/GuardrailsView.tsx` · `pages/EngineMonitor.tsx` · `pages/AIPKnowledgeView.tsx` · `pages/data-workbench/pipelineDebugApi.ts`（执行时以最新 grep 为准，逐一删除本地实现改导入）。

### 3.3 验收

1. grep `function authHeaders|const authHeaders` 全仓仅 auth.ts 1 处；
2. tsc 0 错误；登录→数据工作台→任务中心→AI 工作台冒烟通过（宽限期行为不回归）；
3. Token 解析行为单测（合法/过期/畸形 token 三用例）。

---

## 四、REQ-SEC-04 审计包导出（P2）

### 4.1 包结构（zip）

```
forecast_{runId}_audit_{yyyyMMddHHmmss}.zip
├── manifest.json          // 元数据：runId/caliberId@version/asOfTime/导出人/导出时间/权限范围/各文件 checksum
├── input-snapshot.json    // 快照引用清单（factRefs 计数+ID 列表，不含越权行）
├── formulas.json          // 口径公式快照（caliber_snapshot_json）+ 指标版本
├── assumptions.csv        // 引用的画像/假设版本清单
├── results.csv            // 结果明细（导出人权限范围内，RLS/CLS 已过滤）
├── actions.csv            // 关联经营动作及审批记录
└── evidence-index.csv     // 证据引用索引（ref → 定位信息）
```

### 4.2 规则

1. 导出内容 = 导出人权限范围（复用 SEC-01 管道，禁全量旁路）；
2. manifest.json 头部元数据完整（币种/单位/口径版本/截至时点——对齐附件 §6.1-5）；
3. 导出动作发审计事件（eventType=`AUDIT_PACK_EXPORT`）；
4. 端点：`GET /api/v1/workspace/forecast-runs/{runId}/audit-pack`（workspace 聚合各 service 数据打包，OPA 裁决 `audit:export`）。

### 4.3 验收

1. 财务角色导出包含全量；项目负责人导出仅本项目行且 manifest 标明范围；
2. zip 各文件 checksum 与 manifest 一致；
3. 审计事件可查。

---

## 五、W-Agent 承接需求（REQ-SEC-06~11，v1.1/v1.2 立，**2026-09-29 §十四 已批准**）

> **来源**：附件第二册 §12（Agent Policy Guard：PEP/五检查点/决策输入输出）、第一册 §模型清单（密级上限）、第三册 §9（CLS 绕过与小样本）、§10（审计哈希链与 WORM）；登记动因见 [W-Agent 落地检视报告 §七 逐字计划](./W-Agent详细设计落地检视报告-2026-09-29.md)。
> **裁决状态**：**已批准**（2026-09-29 需求检视报告 §十四 14.1）——随 **R-35 ①（附件约 20 表按 ECOS 模板重写；附件 §12.3 的 PG `CREATE POLICY` RLS 代码块**整体作废**并给替换方案 = 经 security-engine REST 单点裁决）**，小样本聚合保护另挂 **Z-7**，角色扩展另随 **R-72 ①**（§5.6）；**R-18 ②**（llm-gateway 的取密/ABAC 一律经 security-engine REST，**废除 `abac-eval-enabled` 一键绕过**）与 **R-66 ①**（聚合保护阈值不由调用方操纵）同批生效。计入已批准基线；**批准仅表示需求文本生效**——§5.7「明确不做」仍成立，改业务代码/动 `seed.sql` 存量需逐项再授权（报告 §14.4）。
> **总前提（不可让渡）**：security-engine 是**唯一 PDP**，任何引擎/Agent/CLI 侧的判断都只是 PEP 执行点；OPA 或 security-engine 不可用时**默认 DENY**（fail-closed）。REQ-PLT-25（PRD-01 v1.1）已裁定 RLS/CLS 由 security-engine REST 单点裁决、引擎侧禁 `CREATE POLICY` 与本地租户过滤——本节各项均在该前提下展开。

### 5.1 REQ-SEC-06 Policy Guard 五检查点接线 PDP（P0，随 R-35）

**规格**：
1. 五检查点枚举冻结：`pre-plan`（Playbook 可否发起、是否允许探索规划）/ `pre-step`（资源权限 + 工具 automation_level + 数据密级 + 是否需审批）/ `post-step`（输出脱敏 + 越权数据检查）/ `pre-commit`（审批凭证有效 + 影响范围在授权内 + **回滚方案已登记**）/ `pre-output`（结果不含无权对象 + 数字与证据校验通过）（附件第二册 §12.2）；
2. **决策输入四元**（§12.3）：主体（user/role/org）、动作（tool + level）、资源（object_type/classification/org）、上下文（tenant/run/time/risk_score/is_scheduled）——字段名与类型由本册契约单源（REQ-PLT-26）定义，Agent 侧不得自造 input 形态；
3. **决策输出四态**：`allow` / `deny(reason)` / `require_approval(approvers)` / `allow_with_mask(fields)`；四态都是合法响应，**任何"解析失败按 allow"的实现判 FAIL**（现 `AgentToolSqlSecurityGate.java:235` 已按 DENY 处理，本项要求把该口径上升为契约测试而非注释承诺）；
4. 所有决策（含 allow）写审计（ST06 Kafka `ecos.audit`），带 `run_id/step_id/checkpoint`；
5. **策略正文落 OPA，不落 Java 分支**：`default deny` 必须在 Rego 侧存在；Java 侧只组装 input；
6. **待复核（不得据推断改代码）**：security-engine 内实测存在两个 `/evaluate` 控制器（`PolicyEngineController.java:27-32` 与 `SecurityPolicyController.java:28-50`，后者走 `opaService.evaluate("abac", ...)`），二者类级前缀与是否同一路径**未运行期复核** ⇒ 本项先要求"路由单源"（与 ARCH_SPEC **C234/C249** 同批），复核结论出之前不合并、不删任一控制器。

**验收**：`mvn -Dtest=PolicyGuardCheckpointTest#fiveCheckpointsEachHitPdpOncePerStep`、`#denyAndAllowBothAudited`、`mvn -Dtest=PolicyDecisionParseTest#unparsableDecisionFailsClosed`、`mvn -Dtest=SecurityRouteSingleSourceTest#onlyOneEvaluatePathIsRoutable`（前置：路由前缀实测复核；未复核前记"未执行"）。

### 5.2 REQ-SEC-07 模型密级上限 × 白名单（P0，随 R-35）

**实测现状**：`DataClassificationServiceImpl` 与 `SecuritySandboxService` 已存在（密级能力在位），但**模型侧密级上限与白名单未见实现**（全仓 grep `fourEyes|minSampleCount|k_anonymity|smallSample` 命中 0 文件）。

**规格**：
1. 模型注册必须带 `classification_ceiling`（该模型被允许处理的最高密级）与部署位置（第一册 §模型列表）；
2. 路由前置校验：`data_classification(resource) <= model.classification_ceiling` 且该模型在该租户/场景的白名单内，否则 `deny`（由 PDP 裁决，Model Router 不得自行放行，ADR-20 §2.2）；
3. 出网模型（外部 LLM provider）默认上限 **低于**内网模型一档；超限内容不得进入 prompt，**截断前先拒**；
4. Prompt 与补全内容本身按密级对待，不得因其"是模型生成的"而降级保护；
5. 密级判定与列过滤仍由 security-engine 提供（ST04/ST05），llm-gateway 只消费裁决结果。

**验收**：`mvn -Dtest=ModelClassificationCeilingTest#overCeilingDeniedBeforePromptBuild`、`#whitelistMembershipRequired`、`mvn -Dtest=LlmGatewayMaskingTest#cannotDowngradeClassificationOfGeneratedText`。

### 5.3 REQ-SEC-08 四眼原则（P0，随 R-35；载体随 ADR-18 统一）

**规格**：
1. Candidate→Review→Approve→Publish 链路中 **approver ≠ submitter**（同一 subject 自批 = FAIL），并且**同一 org 不等于同一人**；
2. 适用面：知识/口径/模型版本/Playbook/L3 动作/工具契约的发布与 Kill Switch 恢复；
3. 审批凭证一次性、绑定 `candidate_id + digest`，内容变了凭证失效；`require_approval` 输出必须携带 approver 集合来源（PDP 决定，不由 Agent 猜）；
4. **载体单源**：审批落 ADR-18 统一载体，禁止再加第五套审批表（现实测已存在 `agent_approval` / `ecos_decision_approval` / `ecos_workflow_approval` / `ecos_knowledge.kb_extract_candidate` 四套重复载体，见 PRD-04 §6.4 与 ARCH_SPEC **C229/C230**）；存量表只定性、只停写，不擅自 DROP；
5. 全仓 `four_eyes/fourEyes` 命中 0 ⇒ 本项是**新立能力**，不得写成"已实现"。

**验收**：`mvn -Dtest=CandidateFourEyesTest#approverMustDifferFromSubmitter`、`#credentialInvalidAfterContentDigestChange`、`mvn -Dtest=ApprovalCarrierUniquenessTest#noFifthApprovalTableRegistered`。

### 5.4 REQ-SEC-09 审计哈希链补强与 WORM 锚定（P1，随 R-35）

**实测现状（必须先纠正"没有哈希链"的误述）**：`AuditHashChainService.java` **已实现**链式戳记与校验——`curr_hash = SHA-256(prev_hash || username || operation || entity_type || entity_id || created_at)`（`:15-16`）、`stampHashChain(auditLogId)`（`:36-67`）、按 `id < ? ORDER BY id DESC LIMIT 1` 取前驱（`:44-46`）、以及验证方法（`:80-84` 起）。缺口是四项：

| 缺口 | 实测证据 | 要求 |
|---|---|---|
| 戳记失败静默 | `:64-66` 仅 `log.warn` 后返回 ⇒ 审计行已入库但 `prev_hash/curr_hash` 为 NULL，链**悄悄断开** | 戳记失败必须 fail-closed：回滚该审计写入或置 `chain_broken` 告警态，禁"记日志即算完成" |
| 单全局链 | 前驱按全表 `id` 序取，未按租户分链 | 按 `tenant_id` 分链（与 REQ-PLT-24 多租户列同批），否则任一租户写入都会影响他链可验证性 |
| 无外部锚定 | 仓内无 WORM/对象存储锚定代码 | 定期把链头（区间摘要）写入 MinIO **object lock / WORM** 桶并登记锚点记录，使"整段重写"可被外部证据否证 |
| SQL 裸表名 | `FROM ecos_audit_log`（`:41,59,60,80`）**未 schema 限定** | 违 **DR01**；订正为 schema 限定 + `databaseId` 方言分支（MC03），属确定性违规、可随本册登记后直改 |

**边界**：哈希链只保证"未篡改"，不替代 ST06 审计事件完整性与保留期策略；附件第三册 `:1005` 的 `prev_hash text NOT NULL` 表形态**不直接采纳**（现网列已存在且可空，属存量，只加不改）。

**验收**：`mvn -Dtest=AuditHashChainTest#stampFailureCannotLeaveSilentGap`、`#perTenantChainVerifiable`、`#anchorRecordWrittenToWormBucket`（WORM 需 MinIO object lock 实环境：未开通前记"未执行"）、`mvn -Dtest=AuditSqlComplianceTest#auditLogQueriesAreSchemaQualified`。

### 5.5 REQ-SEC-10 小样本聚合保护（P1，随 Z-7）

**规格**（附件第三册 :979）：
1. Agent 不得通过聚合、拼接绕过 CLS（多次查询推断被隐藏字段）；Policy Guard 对**分组人数 < 5** 的聚合结果执行最小分组保护：抑制该组或返回上卷值并说明；
2. 阈值 5 为默认值，落在配置字典（`config_group` 单表分组，经 sysman api 门面），**禁硬编码在 SQL**；
3. 跨请求累计也受约束：同一 `run_id` 内对同一维度的多次细分查询计入同一保护窗；
4. 保护判定在 **security-engine 侧**完成（PEP 送聚合意图与 group-by 元数据，PDP 返 allow/suppress）；禁止各引擎各写一份"行数 < 5 就不返回"；
5. 阈值调整属策略变更，走 OPA 策略版本与 Git 归档（铁律 :549）。

**验收**：`mvn -Dtest=MinSampleGuardTest#groupBelowThresholdSuppressedOrRolledUp`、`#cumulativeRefinementWithinRunIsCounted`、`mvn -Dtest=MinSampleGuardTest#thresholdComesFromConfigNotSql`（源码 regex：SQL 内无 `< 5` 常量）。

### 5.6 REQ-SEC-11 系统角色扩展与属主（P1，**2026-09-29 §十四 已批准**，随 **R-72**；与卷 09 的 R-66 并案）

**规格**（附件第一册 §9.1 十角色表，由详细设计-10 §9.5 回填暴露）：
1. 新增两角色：**模型管理员**（`MODEL_ADMINISTRATOR`）属主 Model & Capability Center（模型注册、能力配置、路由与健康监控，卷 06）；**认知模型负责人**（`COGNITIVE_MODEL_OWNER`）属主 Cognitive Model 定义/版本/评估与四类发布门禁（卷 05 CM Registry）；
2. 角色定义只经 **security-engine 裁决 + sysman api 门面登记 + OPA 策略**三处生效（权限单点裁决铁律），**禁**在前端路由表、BFF 或各引擎里以字符串常量硬编码角色判定；
3. 与既有三档 `td_role_security_profile.clearance_level`（`V24__ecos_security_profile.sql:55-73`）对齐：模型管理员是否可触达模型密级上限配置（REQ-SEC-07）须与本项同批裁决；
4. **现状实测**：`ecos_backend/database/seed.sql:5-8` 仅 **3** 个角色（`ADMIN`／`OPERATOR`／`VIEWER`）；全仓 `*.sql`／`*.java`／`*.ts`／`*.tsx` 检索"模型管理员／认知模型负责人"及其命名变体（`model_admin`／`model-manager`／`cognitive_model`／`cm_owner`）**0 命中** ⇒ 附件十角色中这两项**无载体**；其余 8 角色与既有角色的映射**属待只读复核**，复核前不合并、不改名、不删；
5. 角色相关脚本只落**单源目录** `ecos_backend/gateway/src/main/resources/db/migration/`，接续号须排在 `V227~V240` 之后（现实测最大 **V163**；R-33/R-35 已于 2026-09-29 §十四 批准 ⇒ `V227~V240` 现按批准口径**落迁移脚本文件**，实际接续号在脚本落地批次完成后复测）；**对 `ecos-postgres` 实跑迁移属基建动作，需逐项再授权（报告 §14.4）**。

**验收（只写可执行标识）**：`mvn -Dtest=RoleRegistryTest#modelAdministratorAndCognitiveModelOwnerAreRegistered` + `mvn -Dtest=NoHardcodedRoleCheckTest#roleCodesResolveViaSysmanFacade`（两个测试类**均未建** ⇒ 记"未执行"）；前端可见性用例 `pw role-model-admin.spec.ts` 依赖 **P-3 Playwright 工程**，建成前一律记"未执行"，禁记"通过"。

### 5.7 明确不做

- 不在 Agent 侧、CLI 侧、引擎侧本地判权限（含"缓存判定"作为唯一依据）；
- 不引入新 PDP（OPA 唯一），不把策略写成 Java 常量分支；
- 不 DROP 既有审计/审批表，不改既有审计列语义（只加列、只停写）。

---

## 六、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| SEC-01 | PRD-02 事实表落地、OPA 策略管理 | PRD-06 AI-01/04、演练全项 | 场景批次 A（P0 门槛） |
| SEC-02 | whitelist 清单 | 全部新增端点批次 | **PMO-73 G3** |
| SEC-03 | — | PRD-08 FE-06 迁移 | **PMO-73 G2** |
| SEC-04 | FC-02 运行服务、SEC-01 管道 | 附件 §6.3-4 | 场景批次 C（P2） |
| SEC-06 | 路由单源复核（C234/C249）、PRD-06 AI-07 契约、REQ-PLT-26 | PRD-06 AI-06/09、PRD-10 WAG-15/16 | **已批准（随 R-35 · §十四）** |
| SEC-07 | ADR-20 模型注册表、DataClassificationServiceImpl 现状 | PRD-06 AI-08 路由 | **已批准（随 R-35 · §十四）** |
| SEC-08 | ADR-18 载体统一、PRD-04 KB-09 | PRD-06 AI-09（L3）、PRD-10 WAG-19/20 | **已批准（随 R-35 · §十四）** |
| SEC-09 | REQ-PLT-24（tenant_id）、MinIO object lock 实环境 | PRD-06 AI-06 决策审计、AM-19 | **已批准（随 R-35 · §十四）**（DR01 订正属直改） |
| SEC-10 | SEC-06 PEP 通道、配置字典单表分组 | PRD-09 场景下钻、PRD-06 AI-04 | **已批准（随 Z-7 · §十四）** |

<!-- PRD-07-security-engine需求规格 / 2026-09-29 / v1.3（§五 REQ-SEC-06~11 已批准 R-35/R-42 组 / Z-7 / R-72，凭证 = 需求检视报告 §十四 2026-09-29） -->
