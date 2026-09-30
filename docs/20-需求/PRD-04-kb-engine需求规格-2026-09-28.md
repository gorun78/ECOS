# PRD-04 kb-engine 需求规格（分册 04）

> 来源: 肖国荣 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.2（**v1.2（2026-09-29 批量批准定版）**：需求检视报告 **§十四** 按各表「本设计推荐」列批准 R-1~R-72 ⇒ 本册 §六 **REQ-KB-06~09 转正式需求并计入已批准基线**（随 R-8／R-12／R-40／R-46，原写 "R-08" 统一为 **R-8**）。另按 **Q1 b／报告 G2-1** 更正 §2.2/§3.1 DDL 草案：主键 DEFAULT gen_random_uuid() ×2 → 应用侧生成 UUID（MC01，DDL 无默认值）、JSONB ×2（group_dims_json / dim_scope_json）→ TEXT（MC02 受控 JSON 只准 TEXT）。**"已批准"仅指需求文本生效**：验收测试类未建者一律记"未执行"（铁律 :14／Q13）；相关 DDL 只落**迁移脚本文件**、不实跑库，改业务代码需逐项再授权（报告 §14.4）。v1.1（2026-09-29 接续 W-Agent 制品，时点标注"草案、待裁决"已被 §十四 取代）：新增 §六 REQ-KB-06~09 承接需求；§四 守护对象表名按已结案裁决 **Q11 / ADR-8 §5** 更正为物理真身；原 §六 追溯表顺延为 §七）
> 上游: [PRD-00 总纲](PRD-00-ECOS平台需求规格说明书-2026-09-28.md) · [PRD-01 平台级](PRD-01-平台级与横切需求规格-2026-09-28.md)
> 覆盖: REQ-KB-01~05（已列）+ **REQ-KB-06~09（v1.1 立，2026-09-29 §十四 **已批准**）**
> 模块: `ecos_backend/engine/kb-engine`（dccheng:18086）

---

## 一、REQ-KB-01 C3 属性完整性校验实现（P1）

### 1.1 算法规格

**位置**：`KbEntityInstanceExtractionService.extractForMapping`，在 C1（类型存在性）之后、节点 upsert 之前。

```
输入：本体实体属性集合 A（来自抽取主流程第④步 entities 定义，含 required 标记）
      待写节点属性集合 P（fieldMappings 装配结果）
规则：
  R1 未知属性：p ∈ P 且 p ∉ A           → issue C3_UNKNOWN_ATTR（WARN，不阻断，属性照常写入）
  R2 必填缺失：a ∈ A 且 a.required 且 a ∉ P 或值为空 → issue C3_MISSING_REQUIRED（WARN，不阻断）
  R3 类型不符：p 值类型与 a 声明类型不兼容（字符串→数值失败等） → issue C3_TYPE_MISMATCH（WARN + 该属性置 null 不写入）
处置：全部 WARN 级（对齐既有裁定"必填缺失告警，不阻断"）；issue 进 report.issues[]，不静默吞（铁律 §6.2.10-10）
```

**issue 结构**（对齐既有 C1/C2/C4 issues 格式）：

```json
{"code": "C3_MISSING_REQUIRED", "entityCode": "Project", "sourcePk": "p-001",
 "field": "project_type", "severity": "WARN", "message": "必填属性缺失"}
```

### 1.2 配置

`ecos.kb.extract.c3_enabled=true`（默认开）；关闭时跳过并在 report 标注 `c3Skipped=true`（防止静默关闭）。

### 1.3 验收标准

1. 缺必填属性抽取 → report.issues 含 C3_MISSING_REQUIRED，节点仍写入；
2. 未知属性 → C3_UNKNOWN_ATTR，写入不受影响；
3. dry-run 与真执行 C3 issue 集一致（联动 KB-05）；
4. c3_enabled=false 时 report 有 c3Skipped 标记。

---

## 二、REQ-KB-02 历史画像知识（P0，新能力）

### 2.1 需求陈述

从 DW 层历史事实（`ecos_biz_stage_fact` 等，只读）生成**版本化数值画像**：分组分布统计（P10/P50/P90、样本量、窗口、缺失率、置信区间）+ 分层退化 + 审批发布。画像供 C 层确定性计算取参（预测区间、缺省实现率），**数值知识不得只存文档/提示词**。

### 2.2 数据模型

```sql
CREATE TABLE IF NOT EXISTS ecos_kb_profile (
    id               VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 禁 gen_random_uuid()
    profile_key      VARCHAR(255) NOT NULL,        -- 规范化维度键：metric|k1=v1|k2=v2（排序后拼接）
    metric_code      VARCHAR(64) NOT NULL,         -- 对齐 PRD-03 指标编码（如 M_REALIZATION_RATE）
    group_dims_json  TEXT NOT NULL,               -- MC02: 受控 JSON 只准 TEXT（{"project_type":"交付类","department_id":"d01","stage":"ACCEPTANCE"}）
    window_from      VARCHAR(7) NOT NULL,          -- 观察窗口起 YYYY-MM
    window_to        VARCHAR(7) NOT NULL,
    sample_count     INTEGER NOT NULL,
    missing_rate     NUMERIC(5,4) NOT NULL DEFAULT 0,
    p10              NUMERIC(18,4),
    p50              NUMERIC(18,4),
    p90              NUMERIC(18,4),
    mean_value       NUMERIC(18,4),
    ci_low           NUMERIC(18,4),                -- 95% 置信区间（bootstrap 或 t 近似，方法记录于 stats_method）
    ci_high          NUMERIC(18,4),
    stats_method     VARCHAR(32) NOT NULL DEFAULT 'T_APPROX',
    confidence_level VARCHAR(10) NOT NULL,         -- HIGH/MEDIUM/LOW（规则见 2.3-3）
    degrade_from     VARCHAR(36),                  -- 退化来源画像 id（NULL=本维度组样本充足）
    applicable_condition VARCHAR(500),             -- 适用条件说明（人读）
    profile_version  VARCHAR(20) NOT NULL,         -- 语义化版本，同 profile_key 单调递增
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED/SUPERSEDED
    approved_by      VARCHAR(100),
    -- 审计 5 字段 + version_no + domain
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kbp_key_ver ON ecos_kb_profile(profile_key, profile_version);
CREATE INDEX IF NOT EXISTS idx_kbp_metric_status ON ecos_kb_profile(metric_code, status);
```

> 归属说明：画像属 K 层知识资产，表落 kb 管辖（`ecos_kb_` 前缀），**不违反** "cognitive 不新增业务事实表"（画像非业务事实，是知识制品）。

### 2.3 生成规则

1. **分组维度默认**：`项目类型 × 部门/团队 × 环节(stage)`；指标默认 `M_REALIZATION_RATE`、`M_COLLECTION_CYCLE`、成本率类；
2. **取数**：只读 DW 层 `dq_status=PUBLISHED` 的 ACTUAL 行（经 datanet REST 或 runtime-access 只读查询，禁绕 DW 直读 RAW）；窗口默认最近 24 个月，样本 <12 个月自动缩窗并降置信；
3. **分层退化优先级**（样本不足时逐级上退，记录 degrade_from）：
   `项目×部门×环节` → `项目类型×部门×环节` → `项目类型×环节` → `部门×环节` → `全局×环节`；
   样本阈值：n≥30 → HIGH；10≤n<30 → MEDIUM（本级）；n<10 → 触发退化，退化后 n≥30 → MEDIUM，仍 <30 → LOW；
4. **版本化**：每次生成产生新 profile_version（旧版自动 SUPERSEDED，但已发布版本永不可改/删——预测快照按版本引用复现）；
5. **审批**：DRAFT → PUBLISHED 须 approved_by（知识管理员角色）；PUBLISHED 画像才可被预测快照引用。

### 2.4 API（dccheng 新增）

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/v1/knowledge/profiles/generate` | POST | body={metricCodes[], dims, windowFrom, windowTo}；走 `ITaskManagementService.submitTask+executeTask`（铁律 §1.6，禁自建调度）；响应 {taskId, status:"SUBMITTED"} |
| `/api/v1/knowledge/profiles` | GET | 查询：metric/dims/status/version 过滤，分页 |
| `/api/v1/knowledge/profiles/{id}/publish` | POST | 审批发布（权限：知识管理员） |
| `/api/v1/knowledge/profiles/resolve` | GET | **消费端点**：?metric=&dims=（JSON）&atVersion= → 返回最匹配画像（含退化链说明）；预测运行快照冻结用 |

### 2.5 验收标准

1. 按"项目类型×部门×环节"分组生成画像，样本充足组 HIGH、不足组按 2.3-3 退化且 degrade_from 正确；
2. 同参数重复生成 → 新版本，旧版本 SUPERSEDED，已发布版本内容不可变（UPDATE 拒绝）；
3. `resolve` 返回带完整退化链；预测运行引用 profile_version 后，重跑复现同参数（联动 PRD-09 FC-02）；
4. 生成任务在异步任务中心可见（taskId 反查 progress）。

---

## 三、REQ-KB-03 假设库版本化（P0，新能力）

### 3.1 数据模型

```sql
CREATE TABLE IF NOT EXISTS ecos_kb_assumption (
    id               VARCHAR(36) PRIMARY KEY,          -- MC01: 应用侧生成 UUID，DDL 禁 gen_random_uuid()
    assumption_key   VARCHAR(255) NOT NULL,        -- scenarioType|metric|dims 规范化键
    scenario_type    VARCHAR(32) NOT NULL,         -- BASE/CONSERVATIVE/AGGRESSIVE/自定义情景编码
    metric_code      VARCHAR(64) NOT NULL,
    dim_scope_json   TEXT NOT NULL DEFAULT '{}',  -- MC02: 受控 JSON 只准 TEXT（适用范围 {"project_id":"p1","periods":["2027-07","2027-08","2027-09"]}）
    value_type       VARCHAR(10) NOT NULL,         -- RATE/AMOUNT/DAYS
    value            NUMERIC(18,4) NOT NULL,       -- RATE 时存小数（-0.10 = 下降10个百分点须用 delta 语义，见 3.2-2）
    value_semantic   VARCHAR(20) NOT NULL DEFAULT 'ABSOLUTE',  -- ABSOLUTE 绝对值 / DELTA 相对基准增量
    base_assumption_id VARCHAR(36),                -- DELTA 时指向 BASE 假设
    valid_from       VARCHAR(7) NOT NULL,
    valid_to         VARCHAR(7),                   -- NULL=至失效审批
    reason           VARCHAR(500) NOT NULL,        -- 假设理由（必填）
    evidence_ref     VARCHAR(255),                 -- 证据引用
    expire_at        TIMESTAMP,                    -- 失效日期（到期自动 EXPIRED）
    assumption_version VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- 状态机见 3.2
    approved_by      VARCHAR(100),
    -- 审计 5 字段 + version_no + domain
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kba_key_ver ON ecos_kb_assumption(assumption_key, assumption_version);
CREATE INDEX IF NOT EXISTS idx_kba_scenario_status ON ecos_kb_assumption(scenario_type, status);
```

### 3.2 规则

1. **生命周期状态机**：`DRAFT → PENDING_APPROVAL → APPROVED(ACTIVE) → {EXPIRED | SUPERSEDED}`；仅 ACTIVE 可被预测引用；EXPIRED 由 expire_at 定时判定（走 runtime-task，禁自建定时器）；
2. **DELTA 语义**：情景假设推荐 `value_semantic=DELTA`（如"验收率下降 10 个百分点"= DELTA value=-0.10 指向 BASE 假设），避免绝对值歧义；
3. 每条假设必填 reason；evidence_ref 缺失时状态只能到 APPROVED 但标记 `unverified=true`（消费端显示"待验证假设"，联动 PRD-05 COG-02 证据分离）；
4. 版本不可变：APPROVED 后修改 = 新版本 + 旧版本 SUPERSEDED；
5. 审批权限：知识管理员（与画像一致）。

### 3.3 API

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/v1/knowledge/assumptions` | POST/GET | 创建（DRAFT）/查询 |
| `/api/v1/knowledge/assumptions/{id}/submit` | POST | 提交审批 |
| `/api/v1/knowledge/assumptions/{id}/approve` | POST | 审批（approved_by 落库，发审计事件） |
| `/api/v1/knowledge/assumptions/resolve` | GET | ?scenarioType=&metric=&dims=&period= → 匹配 ACTIVE 假设（`dim_scope_json` 包含匹配，精确优先于宽泛）**【2026-09-30 G2 传播】**原字面 `dim_scope` 已按 DR04 定名为 `dim_scope_json`（V177 落地脚本 + 卷04 §E.2 同步） |

### 3.4 验收标准

1. 基准/保守/进取三情景假设各建一版并审批 → resolve 按情景正确返回；
2. 预测响应显示假设 version + unverified 标记（联动 PRD-09 FC-02）；
3. expire_at 到期 → 定时任务置 EXPIRED，resolve 不再返回；
4. APPROVED 假设 UPDATE 被拒（409，指引建新版本）。

---

## 四、REQ-KB-04 场景认知契约守护（P0，守护型）

**规格**（契约本体在 PRD-05 COG-01，本册定义 kb 侧守护义务）：
1. 守护对象（v1.1 按 **Q11 / ADR-8 §5** 更正：三个 `kb_*` 名字均为 PRD 误名、禁止再使用，见 ARCH_SPEC **C94** / 详细设计-04 **K-49**）= 心智状态三表真身 `public.ecos_cognitive_hypothesis`（`V128`）/ `public.ecos_cognitive_belief`（`V129`）/ `public.ecos_cognitive_evidence`（`V127`），对场景侧（workspace/business 服务）**只读开放**；`kb_mind_registry` **物理不存在**（全库无 `%mind%` 表 = ARCH_SPEC **C98** / 详细设计-05 **X-19**），Mind 载体以详细设计-05 §Cg-2 新建的 `cognitive_mind` + `cognitive_scenario_mind` 为目标，未建表前本项判「未执行」而非「通过」；kb 不提供写代理端点给场景侧；
2. hypothesis.status 枚举冻结：`PROPOSED/EVIDENCED/BELIEVED/REFUTED`（新枚举值必须先改契约文档 BUSINESS_SCENARIO_COGNITION_DOC §3.2 再入码）；
3. cognitive 域不反向写 business 表（ARCH 规则 + 评审）；
4. 表存在性预检：QA 开测前跑 `to_regclass` 三表检查（PRD-01 DB-03 脚本），V127~V129 迁移未跑到本环境时先补迁移再测。

**验收**：`ScenarioCognitionIntegrationTest` 四组用例（空场景/多 Mind/无 Mind/503）通过（依赖 E1–E4 交付，测试落 PMO-66 收口后）。

---

## 五、REQ-KB-05 dry-run 真口径守护（P1，守护型）

**规格**：
1. dry-run 与真执行使用同一预查存在性集合计算 created/updated（既有实现口径），差异只允许出现在"实际写库"动作本身；
2. 契约测试（PMO-73 G3-T2）：同一输入先 dry-run 记录 {created,updated,issues[]}，再真执行，断言三项 diff=0；
3. 水位线守护：dry-run 不得更新 `kb_extract_watermark`（既有规则，测试断言 watermark 不变）。

**验收**：契约测试绿；dry-run 后 watermark/表行数均不变。

---

## 六、W-Agent 承接需求（REQ-KB-06~09，v1.1 立，**2026-09-29 §十四 已批准**）

> 来源：《ECOS W Agent 详细设计》附件一册 §5/§6（K 层对象与回流）、三册 §5（Candidate 治理）；检视结论见 `W-Agent详细设计落地检视报告-2026-09-29.md` **WC-12/WC-13/WC-08**，裁决 **R-40 / R-46**。
> **编号更正（重要）**：PRD-10 §六 与 ARCH_SPEC 曾把这四项写作 `REQ-KNO-06~09`，与本册既有前缀 `REQ-KB-`（PRD-00 附录 C / ARCH_SPEC §十三 矩阵同用 `REQ-KB`）冲突 ⇒ **定版为 REQ-KB-06~09，`REQ-KNO` 前缀作废不得使用**（避免同一族出现两个前缀的第三套口径）。
> **边界**：本四项属 **K 层（水·dccheng/kb-engine）对象自身**，W Agent 只消费不定义（PRD-10 §六 同口径，防双主责）。

### 6.1 REQ-KB-06 K 类型白名单与 C 类型写入拒绝（P0，守护型，随 R-46）

**规格**：
1. kb-engine 写入面**只接受 K 层类型**（附件一册 §5 的 12 类为**候选集**，是否全数采纳随 **R-46**；本册不预设 12 类已批准）；
2. 认知层（C）对象类型经 kb 写通道提交时**必须拒绝**并返回明确错误码，不得静默落 K 层表（防止 C 类对象寄生在 `ecos_knowledge`，与 REQ-COG-06 的 C-11 对象补齐互为正面/反面）；
3. 类型集为**封闭枚举 + 防膨胀钩子**：新增类型必须先改本册与 PRD-05 的对象清单再入码（钩子归属随 **R-46**）；
4. 禁在 Agent 控制面（`ecos_ai.ecos_wagent_*`）复制 K 类定义（ADR-16 / ADR-17 §2.2）。

**验收**：`KTypeWhitelistGuardTest#rejectsCognitiveObjectTypeOnKbWritePath`（C 类提交 → 拒绝且 0 行落库）+ `#unknownTypeRequiresContractChangeFirst`（未登记类型 FAIL）。

### 6.2 REQ-KB-07 知识回流候选（origin + Evidence 门槛）（P1，随 R-40/R-46）

**规格**：
1. 由 W Agent 运行产生的"应回填 K 层"的内容，**只能以 Candidate 形态**进入既有唯一正发路径（Candidate→Review→Approve→**Publish 接 Git commit+tag**，**ADR-18**），kb 侧**不得**存在绕过 Candidate 的直写端点；
2. 每条候选必须携带 `origin`（来源 Run/Step 与模型版本）与 **Evidence 引用集合**；无证据或证据不可解析者判 FAIL（禁"AI 文本即知识"）；
3. 候选正文**不入 DB**（只存 Git ref + 元数据，铁律 :549 / ADR-18 §2.3）；
4. 四眼原则：提交人 ≠ 审批人；security 不可用默认 **DENY**。

**验收**：`KbBackflowCandidateTest#candidateRequiresResolvableEvidenceAndOrigin` + `#publishWritesGitRefNotBody` + `CandidateFourEyesTest#approverMustDifferFromSubmitter`。

### 6.3 REQ-KB-08 画像（Profile）规格化（P0，新能力细化，随 R-08/R-46）

**规格**（把 REQ-KB-02 的画像从"有产出"升级为"可判定"）：
1. 画像版本必须显式记录四要素：**样本量 `min_sample`、分位数集（P10/P50/P90）、统计窗口、缺失率**；缺一不得置为可引用版本；
2. 画像的**区间**是 ADR-14 确定性计算（`环节额 = 合同基数 × 归属比例 × 实现率`）的唯一区间来源，cognitive 只引用版本不自造区间；
3. 画像数值列（含比率/分位数）属受控数值：金额类按 `NUMERIC(18,2)` 且**逐列登记 ST03-A 豁免登记表**，文档不得以"明文即可"替代登记表条目；
4. 画像为 **SEMANTIC 数值知识**（PRD-00 附录 C 同口径），不落 A3 非结构化载体。

**验收**：`ProfileSpecTest#quantilesRequireSampleWindowAndMissingRate` + `#forecastIntervalReadsOnlyPublishedProfileVersion`。

### 6.4 REQ-KB-09 `kb_extract_candidate` 收编与停写（P1，随 R-40/R-12）

**规格**：
1. 实测现状（ARCH_SPEC **C229/C230**、ADR-18 §1.2）：`ecos_knowledge.kb_extract_candidate`（`V140:21`）PK 为 `id BIGSERIAL`，**违 MC01**，且与 `agent_approval` / `ecos_decision_approval` / `ecos_workflow_approval` 构成四套重复候选/审批载体；
2. 处置：**只定性 + 停写 + 不 DROP**（存量动作待 **R-12/R-40** 裁决）；W Agent 候选统一落 `ecos_ai.ecos_wagent_candidate`（合规形态见 ADR-17 §2.3）；
3. 迁移期允许**只读**旧表（历史候选可查），禁止新增写入；切换完成判据 = 代码侧对旧表的 INSERT/UPDATE 引用为 0；
4. 若 R-40 反向裁"以 `kb_extract_candidate` 为唯一载体"，则本项作废、改由 ADR-18 §2.2 的表形态在该表上重述（**不改列、只加列**）。

**验收**：`CandidateUnificationTest#kbExtractCandidateHasNoWritePathAfterCutover`（源码扫描 INSERT/UPDATE 引用 = 0）。

---

## 七、追溯与依赖

| REQ | 依赖 | 被依赖 | 批次 |
|---|---|---|---|
| KB-01 | 抽取主流程第④步实体定义 | report.issues 消费方（前端抽取报告） | PMO-73 G5 |
| KB-02 | PRD-02 DATA-01/02（PUBLISHED 事实）、PRD-03 指标编码 | PRD-09 FC-02 区间与缺省参数 | 场景批次 A |
| KB-03 | 同上 | PRD-09 FC-02/情景、PRD-05 COG-02 | 场景批次 A |
| KB-04 | PMO-66 E1–E4 交付 | 场景工作台 | PMO-66 收口后 |
| KB-05 | — | — | **PMO-73 G3** |
| **KB-06** | R-46（类型集与钩子归属） | PRD-05 COG-06（C-11 对象）、PRD-10 WAG-17 | **已批准（§十四）** |
| **KB-07** | ADR-18 候选单源 + security ABAC（:530） | PRD-10 WAG-19/20（Candidate 正发路径） | **已批准（随 R-40 · §十四）** |
| **KB-08** | REQ-KB-02 画像表、R-08（画像落点与写通道） | ADR-14 确定性计算（PRD-05/09） | 场景批次 A |
| **KB-09** | R-12/R-40（存量处置） | ADR-18 §1.2 四套载体收敛 | **已批准（§十四）** |

<!-- PRD-04-kb-engine需求规格 / 2026-09-28 / v1.2（2026-09-29 随需求检视报告 §十四 批量批准定版） -->
