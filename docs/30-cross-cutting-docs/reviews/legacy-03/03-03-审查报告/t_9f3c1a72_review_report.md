# REVIEW_REPORT — 数据工作台血缘地图丢失恢复批次（t_9f3c1a72）

> 审查对象：`release/v2.1-alpha` 工作区未提交变更（13 改动文件 + 1 新增脚本）
> 审查模式：**L2 标准**（全量）
> 审查日期：2026-09-16
> 审查技能：`reviewer-code-review` v3（交付门禁版）
> 关联审计：`docs/plans/merge-audit-v2.1-alpha.md` §十二（§12.1~§12.9）

---

## 一、Step 0 — SOURCE_PATCH 前置校验（第一门禁）

| 校验项 | 结果 | 说明 |
|:--|:--|:--|
| SOURCE_PATCH 状态为 APPROVED |  **降级** | 本项目为**直派模式**（`project_memory`：本环境不使用 Hermes 看板/swarm，daemon 不认领任务已实证空转 49 分钟），无 `SOURCE_PATCH_APPROVAL_RECORD` 制品链 |
| 有批准记录 | ⚠ **降级** | 同上级 |
| deliverable_allowed = true | ⚠ **降级** | 同上级 |
| PRD 引用 | ⚠ 无独立 PRD 制品 | 需求来源为用户口述 5 项要求（完整性检查 / 根因 / 恢复 / 跟踪机制 / 测试），已结构化落于 §12.1~§12.7 |
| OpenAPI 引用 | ✅ `docs/plans/api-contract.md` | 集合B 按既有契约**补齐后端**实现，未改契约 |

**降级等价替代**（据实标注，不虚报）：以 §11.4/§12.6 **编译门**（后端 `mvn install` BUILD SUCCESS + 前端 `tsc --noEmit` exit=0）+ §12.5/§12.5.1/§12.6 的 **curl/E2E 实测**替代上游批准记录。

→ 第一门禁以「降级通过」记录，**不阻断**审查；该降级属项目协同机制既定事实，非本批次引入。

---

## 二、Step 1 — 变更范围

| # | 文件 | 层 | 归属批次 |
|:--|:--|:--|:--|
| 1 | `ecos_backend/engine/data-engine/.../controller/DataLineageController.java` | 后端 | A·血缘 |
| 2 | `ecos_backend/engine/data-engine/.../service/DataLineageService.java` | 后端 | A·血缘 |
| 3 | `ecos_frontend/src/pages/DataLineage.tsx` | 前端 | A·血缘 |
| 4 | `ecos_frontend/src/pages/data-workbench/api.ts` | 前端 | A·血缘 |
| 5 | `ecos_frontend/src/pages/data-workbench/tabs/DataLineageTab.tsx` | 前端 | A·血缘 |
| 6 | `ecos_frontend/src/pages/DataWorkbenchLayout.tsx` | 前端 | C·跳转链路 |
| 7 | `ecos_frontend/src/pages/data-workbench/CatalogContextMenu.tsx` | 前端 | C·跳转链路 |
| 8 | `ecos_backend/gateway/.../filter/VersionPrefixRewriteFilter.java` | 网关 | B·契约 |
| 9 | `ecos_backend/services/sysman/.../security/ClearanceInterceptor.java` | 安全 | B·契约 |
| 10 | `ecos_backend/services/sysman/.../security/SecurityConfig.java` | 安全 | B·契约 |
| 11 | `ecos_backend/engine/data-engine/.../integration/IntegrationMetadataService.java` | 后端 | B·契约 |
| 12 | `ecos_backend/engine/data-engine/.../controller/IntegrationMetadataController.java` | 后端 | B·契约 |
| 13 | `docs/plans/merge-audit-v2.1-alpha.md` | 文档 | D·审计 |
| 14 | `ecos_backend/scripts/check-dangling-changes.sh`（新增） | 工具 | D·审计 |

`git diff --stat HEAD`：**13 files changed, 1648 insertions(+), 225 deletions(-)**

---

## 三、Step 3 — 安全审计（`reviewer-security-audit` 关注面）

### 🔴 S-001 未认证可读取基础设施元数据（HIGH / REGRESSION，**审查中自查发现并已闭合**）

**发现方式**：不是用户报告，而是本审查在 `SECURITY_GATE` 环节**主动执行匿名访问回归**时暴露。

| 请求（无 token） | 修复后·撤销前 | 修复前基线 |
|:--|:--|:--|
| `/api/integration/metadata` | **200**（返 `connections` 含 host/port/username/jdbcUrl） | 403 |
| `/api/v1/integration/metadata` | **200** | 403 |
| `/api/integration/logs` | **200** | **403**（§12.5 表首行已记载，可复算） |

**定性**：暴露数据源拓扑（host/port/username/jdbcUrl，**不含密码**）。与 M0 改造（2026-09-01）移除 `/datanet/**` permitAll 的缺陷**同类** —— 当时判据为"数据源连接池凭据 (username/password) 不可匿名（QA T3-006）"。违反架构铁律 **§2.4-6 默认 DENY**。

**根因**：铁律 §1.2「双路径各写一遍」的默认前提不成立 —— `VersionPrefixRewriteFilter` 标注 `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`（≈ `Integer.MIN_VALUE+10`），**远早于** Spring Security 的 `FilterChainProxy`（默认 order `-100`），即**路径重写先于鉴权**，鉴权层看到的**永远是裸路径**。故补 `/api/integration/**` 到 permitAll ＝直接放行未认证访问。

**处置（已执行）**：撤销该条 permitAll（仅回退本批次新增条目），**不动**既有的 `/api/v1/integration/**`（禁止魔改既有全局配置；且其对已入重写表的前缀不可达，属等效死配置，无安全影响）。**未新增任何豁免**。

**复验（撤销后实测，本报告 ground truth）**

| 请求 | 无 token | 带 token |
|:--|:--:|:--:|
| `/api/integration/metadata` | **403** | **200** |
| `/api/v1/integration/metadata` | **403** | **200** |
| `/api/integration/logs` | **403** | **200** |
| `/api/v1/integration/logs` | **403** | **200** |
| `/api/v1/integration/metadata/drift?sample=true` | **403** | **200** |

**前端影响面：零**（结构性论证 + 实证双重）—— 全库 **8 处**调用面（`data-workbench/api.ts:10`、`knowledgeApi.ts:325/333/341/798/820`、`SyncTab.tsx:42`、`DataWorkbenchImportTab.tsx:52/72`）**全部携带 `Authorization: Bearer`**；全库检索确认**无任何匿名调用方**（含 `ecos-tests/`、docker health、定时任务）；浏览器 E2E 实测 `/api/integration/metadata` **200**。

→ **该缺陷已闭合，不计入当前交付物的开放缺陷**；但作为"审查发现的 HIGH 回归"如实归档，并驱动流程修正（见 §五）。

### 其他安全项

| 项 | 检查法 | 结果 |
|:--|:--|:--|
| §2.4-8 安全集成强制卡（涉敏感数据须见 `encrypt/decrypt/SecretService`） | grep 本批次 4 个后端文件 | **N/A**：本批次**不引入**敏感数据存取（仅读 `td_datasource` 展示字段 + 血缘表），不涉明文落盘；唯一敏感面是 S-001 的**访问控制**，已闭合 |
| 假数据 / mock 排查 | 人工读 `IntegrationMetadataService` 新增 277 行 | **PASS**：`fetchDriftSample` 复用真实 DQ 结论（`kind=DQ_FAILURE`）+ `dq_rule_check.sample_failures` 真实样本；无真实样本**返回空数组**，未塞假数据；`dsId` 无法归因的设计取舍已写入 javadoc |
| SQL 注入 | 读新增查询 | **PASS**：全部走既有 `JdbcTemplate` 预编译/既有 Service |

---

## 四、Step 2/4 — 架构一致性（`reviewer-arch-consistency` 关注面）

| 铁律 | 检查点 | 结果 |
|:--|:--|:--|
| §5.1 #5 / §1.1 **API 只增不改** | `DataLineageController`：移除 `/nodes`,`/edges`,`/build`，新增 `/topology`、`/topology/rebuild`、`/impact` |  **P2 见下 A-1**（本次回放属"恢复被丢失的原契约"，非新增破坏性变更，已核验前端同步） |
| §1.2 三滤波器 | 本批次**未新增 Controller**（`IntegrationMetadataController` 既有，仅加 2 个方法）；网关重写映射 + 安全层均已随动 | **PASS**（且据此发现 §1.2 表述缺陷 → S-001 根因） |
| §2.4-6 默认 DENY | 见 S-001 | **PASS（已闭合）** |
| §4.1 主题令牌 | `DataLineage.tsx` 回放 691 行版含 **17 处 `slate-*` 硬编码**，已逐处改写为主题令牌；另修 1 处既有 bug（`style={{ borderColor: styles.cardBorder }}` 把主题 class 当颜色值） | **PASS** |
| §4.3 i18n | 本批次未新增硬编码中文显示文案 | **PASS** |
| §4.6 组件 ≤800 行 | `DataLineage.tsx` 717 行 | **PASS**（边界内） |
| 分层职责 / 依赖方向 | `IntegrationMetadataService` 新增注入 `DataSourceService`、`DataLineageService`（同引擎内 Service，非跨层、非跨引擎直连 Impl） | **PASS** |
| §0.4 不新增 Maven 模块 / Docker 容器 | 本批次零新增 | **PASS** |

### 缺陷 A-1（P2）血缘控制器端点契约替换

`DataLineageController` 由 `/nodes`,`/edges`,`/build` 改为 `/topology`,`/topology/rebuild`,`/impact`。形式上触碰"只增不改"，但实质是**恢复悬空 stash 中已被前端消费的原契约**（现状 HEAD 的前端本就调用新契约），且新旧端点**无第三方消费方**（全库检索确认）。**裁定**：可接受，但需在 commit message 与报告中留痕（已在 §12.4 记录）。

---

## 五、Step 5 — 缺陷 → 需求追溯表

| 缺陷 ID | 描述 | 优先级 | 需求来源 | 文件位置 | 状态 |
|:--|:--|:--:|:--|:--|:--|
| S-001 | 未认证可读基础设施元数据（放行 `/api/integration/**`） | **HIGH** | 需求 ①②③（恢复过程引入的回归） | `SecurityConfig.java` permitAll | **已闭合**（撤销 + 复验 403/200） |
| A-1 | 血缘控制器端点契约替换（触碰"只增不改"字面） | P2 | 需求 ③恢复 | `DataLineageController.java` | 已裁定可接受（留痕） |
| T-1 | `ecos-tests/` 命中 `.gitignore`，E2E 脚本无法作为 commit 凭证 | P2 | 需求 ④⑤ | `ecos-tests/lineage-smoke.mjs` | 遗留（未擅自改 ignore） |
| T-2 | 既有 `/api/v1/integration/**` permitAll 属等效死条目 | P2 | 需求 ②根因 | `SecurityConfig.java:85` | 遗留（禁止魔改，已注释警示） |
| L-1 | `ID_SEQ` 静态内存序列重启回退 → 409 | P2 | 上轮遗留 | — | 遗留 |
| L-2 | `kg_sync_log` 成功路径不落台账 | P2 | 上轮遗留 | — | 遗留 |
| L-3 | `ExtractionReviewPanel` 硬编码 `bg-violet-600` + 异常仅 `console.warn` | P3 | 上轮遗留 | — | 遗留（非本批次文件） |
| L-4 | kb/ontology `AGENTS.md` 依赖描述订正 | P3 | 上轮遗留 | — | 遗留 |

---

## 六、Step 6 — 质量门禁判定

| 门禁 | 判定条件 | 实测 | 结论 |
|:--|:--|:--|:--:|
| **P0_GATE** | P0 缺陷数 = 0 | **0** | **PASS** |
| **P1_GATE** | P1 缺陷数 ≤ 3 | **0**（S-001 为 HIGH 安全回归，已闭合，不计开放 P1） | **PASS** |
| **SECURITY_GATE** | 无 CRITICAL/HIGH 开放漏洞 | S-001 已闭合（匿名 403 / 带 token 200 实测）；无其他 CRITICAL/HIGH | **PASS** |
| **ARCH_GATE** | 无 P0/P1 架构违规 | 无（§1.2 表述缺陷已通过注释固化，见下） | **PASS** |

**审查历史（诚实留痕）**：首轮 SECURITY_GATE 判定为 **FAIL（HIGH=1）** → 触发修复循环 → 撤销 permitAll + 全量复验 → 二轮 **PASS**。`deliverable_allowed=false → 修复 → true` 的闭环已完整走完，**非**"一次通过"。

### `deliverable_allowed = true`

四项门禁全 PASS → `status = AUTO_CLOSED`，无需人工签字放行（按 skill 定义）。

---

## 七、Step 7 — 改进建议（非阻断）

1. **修正架构铁律 §1.2 表述**（🔴 建议优先级最高）：现表述"新增 Controller 必过三滤波器，双路径 `/api/v1/` 与 `/api/` 各写一遍"在**重写先于鉴权**的既有实现下会误导（本批次即因此踩坑）。建议改为：
   > 先判该前缀是否已入 `VersionPrefixRewriteFilter.V1_REWRITE_MAP` —— 若在，鉴权层只见**裸路径**，只写裸路径；再判该端点是否应匿名 —— 业务数据端点一律**不写** permitAll。
   已先行在该文件注释中固化警示（未改铁律正文，须用户批准后方可改宪法）。
2. **收敛历史过宽 permitAll**：现清单含 `/api/v1/engine/**`、`/api/v1/ontology/**`、`/api/v1/datasource/**` 等宽前缀，与 M0 改造"默认 DENY"方向不一致；建议另立专项按 QA 判据逐条收窄（本批次不越界处理）。
3. **`ecos-tests/` 纳入版本控制或 CI**：现被 `*ecos-tests*` 整体排除，测试资产无法成为凭证。

---

## 八、审查结论

| 项 | 结论 |
|:--|:--|
| 交付门禁 | **四门禁全 PASS，`deliverable_allowed = true`** |
| 开放缺陷 | P0=0 / P1=0 / P2=4 / P3=2（P2/P3 均为遗留或已留痕，不阻断） |
| 安全 | 发现并闭合 1 处 HIGH 回归；当前匿名访问**全部 403** |
| 编译门 | 后端 `mvn install` BUILD SUCCESS（7:20）；前端 `tsc --noEmit` exit=0 |
| 契约 | 9 条端点实测：匿名 5×403 / 带 token 5×200 / 回归 2×200 |
| E2E | Playwright 六项全 PASS（A1~A6），浏览器侧 `/api/integration/metadata` 200 |
| 凭证 | 待 §12.9 commit 表回填（按用户决策"分批 clean commit，暂不推送"） |

**审查人**：PM/Reviewer（直派模式，`reviewer-code-review` v3）
**时间**：2026-09-16T13:35+08:00