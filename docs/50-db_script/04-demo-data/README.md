# 04-demo-data — ECOS 独立演示数据（初始化 / 清空 / 校验）

> 来源：从真实库 `sys_man` 反向导出（2026-11-18），**非人工编造**。
> 定位：让界面有内容可看的**业务演示数据**，与 `00_init/10_system_seed.sql` 的**系统配置数据**表级互斥。
> 上游：`00_init/` 全链路执行完毕后才跑本目录。

---

## 一、三件套与执行顺序

| 文件 | 行数 | 作用 | 是否改数据 |
|------|------|------|-----------|
| `demo_seed.sql` | 1366 | 灌入 58 张业务表 / **1014 行**演示数据 | 是（幂等 INSERT） |
| `demo_cleanup.sql` | 331 | 把 58 张演示表清到 0 行，**系统配置面一行不动** | 是（DELETE） |
| `demo_verify.sql` | 368 | 逐表对账基线 + 种子面完好性 + FK 孤儿行 | **否（只读）** |

**三种用法：**

```bash
# 1) 首次初始化（00_init/ 全链路之后）
docker exec -i ecos-postgres psql -U postgres -d <db> -v ON_ERROR_STOP=1 -v ecos_commit=1 -q < demo_seed.sql

# 2) 重置到导出基线（清空 + 重灌，两条都要实跑）
docker exec -i ecos-postgres psql -U postgres -d <db> -v ON_ERROR_STOP=1 -v ecos_commit=1 -q < demo_cleanup.sql
docker exec -i ecos-postgres psql -U postgres -d <db> -v ON_ERROR_STOP=1 -v ecos_commit=1 -q < demo_seed.sql

# 3) 校验（只读，随时可跑）
docker exec -i ecos-postgres psql -U postgres -d <db> -v ON_ERROR_STOP=1 -q < demo_verify.sql
```

**dry-run 是默认行为**：三册都包在 `BEGIN; ... \if :{?ecos_commit}` 里，不传 `-v ecos_commit=1` 就 `ROLLBACK`。
任何一册在实跑前都应先 dry-run 一遍（`-v ON_ERROR_STOP=1` 必带，否则报错会被吞掉继续跑）。

> `demo_verify.sql` 无事务开关（只读，不需要）。

---

## 二、数据范围：三分法，表级互斥

全库 345 张父表按「用途」三分，**同一张表只属一类**，这是清空册能按表 DELETE 而不误伤配置面的安全前提。

| 类别 | 表数 | 行数 | 落点 | 口径 |
|------|------|------|------|------|
| 系统配置种子 | 27 | 795 | `00_init/10_system_seed.sql` | 平台运转必需：IAM / 租户 / 安全策略 / 配置字典 / 各引擎平台级注册表 |
| **演示业务数据** | **58** | **1014** | **本目录 `demo_seed.sql`** | 界面要有内容可看的业务对象 |
| 运行时轨迹 | 21 | 50723 | **刻意不导出** | 审计日志 / 执行流水 / 任务实例 / Agent 消息 / 审批流水等 |
| 0 行表 | 239 | 0 | 不导出 | 无数据可导 |

### 演示集按 schema 分布

| schema | 表数 | 行数 |
|--------|------|------|
| `ecos_control` | 28 | 473 |
| `ecos_ai` | 15 | 377 |
| `ecos_data` | 4 | 83 |
| `ecos_knowledge` | 3 | 38 |
| `ecos_ontology` | 3 | 30 |
| `ecos_cognitive` | 5 | 13 |
| 合计 | **58** | **1014** |

行数 TOP5：`ecos_business_scenario` 245 / `ecos_wagent_candidate` 134 / `ecos_wagent_readiness_gap` 80 / `ecos_wagent_readiness_item` 80 / `ecos_pipeline_definition` 41。

### 被排除的运行时面（21 表 / 50723 行）

`ecos_audit_log` 31941、`td_runtime_task_execution` 17407、`td_runtime_task` 301、`td_runtime_task_status` 307、`td_audit_log` 192、`ecos_evolution_log` 175、`agent_approval` 169、`ecos_kb_extract_audit` 45、`ecos_pipeline_execution` 39、`e2e_target` 37、`ecos_tenant_usage` 27、`ecos_wagent_run_event` 21、`ecos_scenario_query_history` 18、`ecos_wagent_run` 12、`ecos_kg_sync_log` 10、`sys_agent_message` 9、`ecos_dq_issue` 4、`ecos_ai_guardrail_decision_ledger` 4、`sys_agent_session` 2、`ecos_cognitive_outcome_evaluation` 2、`ecos_warn_log` 1。

排除理由：这些是**跑出来的痕迹**，不是演示资产。灌进新库只会让审计/任务列表里塞满别的环境的历史噪声。
其中 `ecos_dw.e2e_target` 是 E2E 夹具（单列 `one integer`，37 行全 1），既非业务数据也非运行时产物，一并排除。

---

## 三、外键拓扑（全限定名实查 `pg_constraint`，36 条全库 FK）

> ⚠ 查 FK 归属**必须** join `pg_namespace` 拼全限定名。本库设了库级 `search_path`，
> `conrelid::regclass::text` 会渲染成**裸名**，直接拿裸名去比对 `schema.table` 集合会**全部落空**，
> 得出「零跨集依赖」的假结论。本项目已踩过一次，记此防再犯。

| 方向 | 边数 | 明细 | 对脚本的影响 |
|------|------|------|-------------|
| 演示 → 种子 | **2** | `ecos_control.ecos_ontology_entity.domain_id`、`ecos_control.ecos_wm_goal.domain_id` → `ecos_ontology.ecos_domain` | **演示册必须在系统种子之后跑** |
| 种子 → 演示 | **0** | — | 清空演示表不会阻塞于种子面 |
| 演示 → 演示 | 4 | `ecos_wm_goal.parent_id`→自身；`ecos_wm_causal_link.source_goal_id`/`target_goal_id`→`ecos_wm_goal`；`ecos_biz_contract.project_id`→`ecos_biz_project` | 灌入按拓扑序（父在前），清空按**逆序**（子在前） |
| 演示 → 运行时 | 1 | `ecos_cognitive.ecos_biz_project.goal_id` → `ecos_cognitive.ecos_wm_goal` | **休眠 FK**：目标 0 行空壳，3 行值全 NULL ⇒ 不构成阻塞（见 §五-3） |
| 运行时 → 演示 | **5** | 见下表 | 清空册先精确删这 5 张表里**指向演示行**的从属行 |

### 5 条跨集从属 DELETE（清空册的关键设计）

| 从属表（非演示） | 父表（演示） | 约束 | 实库行数 |
|-----------------|-------------|------|---------|
| `ecos_control.ecos_dq_issue` | `ecos_control.ecos_dq_rule` | `fk_dq_issue_rule`（**ON DELETE CASCADE**） | 4 |
| `ecos_data.ecos_pipeline_execution` | `ecos_data.ecos_pipeline_definition` | `fk_data_pexec_def`（**无 cascade**） | 39 |
| `ecos_control.ecos_goal_tracking` | `ecos_control.ecos_wm_goal` | `ecos_goal_tracking_goal_id_fkey` | 0 |
| `ecos_control.ecos_wm_goal_log` | `ecos_control.ecos_wm_goal` | `ecos_wm_goal_log_goal_id_fkey` | 0 |
| `ecos_ai.ecos_mission_task` | `ecos_ai.ecos_mission` | `fk_ai_mtask_mission` | 0 |

写法是 `DELETE FROM <从属表> WHERE <fk列> IN (SELECT id FROM <演示父表>)`，**不整表清运行时数据**。
`ecos_pipeline_execution` 那条无 cascade，若漏掉这步清空会直接撞外键失败；`ecos_dq_issue` 有 cascade，显式删属冗余但无害（保留以便口径统一）。

### 表内自引用需深度序

`ecos_control.ecos_wm_goal.parent_id → 自身 id`（21 行）。普通 `ORDER BY id` 不保证父先于子，
故该表用**递归 CTE 按 depth 排序**导出（根在前），且 `ORDER BY` 必须在**顶层**——子查询里的 ORDER BY 不保证向外传播。

---

## 四、验证证据（全部实跑，验证库 `ecos_init_verify4`）

验证库 = 用 `00_init/` 全链路从零建起的独立库，**不是** `sys_man`。

| # | 腿 | 命令/动作 | 实测结果 |
|---|-----|----------|---------|
| 1 | 灌入 dry-run | `demo_seed.sql`（不传 commit） | **EXIT=0**，0 ERROR / 0 WARNING，末尾 ROLLBACK |
| 2 | 灌入实跑 | `-v ecos_commit=1` | EXIT=0，NOTICE `scenario=245 ontology_entity=18 kb_doc=36 pipeline_def=41 td_user=17 sys_config=178` |
| 3 | 首次校验 | `demo_verify.sql` | §1 **58/58 OK**，0 DRIFT / 0 EMPTY；§3 FK 孤儿 **6 项全 0** |
| 4 | **幂等** | 再跑一次 `demo_seed.sql` 实跑 + 校验 | 仍 **58/58 OK**，行数**不翻倍** |
| 5 | 清空 dry-run | `demo_cleanup.sql` | EXIT=0，守卫A「58 表齐备 + 27 种子表已快照 ✓」、守卫B「58 表全 0 行 + 27 种子表逐表未变 ✓」 |
| 6 | 清空实跑 + 独立校验 | `-v ecos_commit=1` 后跑 verify | **58 EMPTY / 种子面 OK / 0 DRIFT**（不只信守卫自述） |
| 7 | **重灌回基线** | `demo_seed.sql` 实跑 + verify | **58/58 OK**，0 EMPTY / 0 DRIFT |
| 8 | **改脏后重置**（见下） | 探针 → 清空 → 重灌 | 脏值恢复基线，无关运行时行存活 |
| 9 | 强化后总校验 | §2 扩到 27 表逐表对账 | **86 OK**（58 演示 + 27 种子 + 1 超管）/ 0 DRIFT / 0 FAIL |
| 10 | **变异探针**（证门禁能变红） | 删 1 行 `sys_dict` → verify | `sys_dict 63/64` **DRIFT** ✓；再跑 `10_system_seed.sql` 幂等补回 → **86 OK** |
| 11 | **新库可登录**（离线复算） | 按部署端 `matchesPbkdf2` 同算法复算种子册 admin 哈希 | **MATCH** ✓；3 个错误候选全 MISMATCH（有鉴别力）；17 行 = pbkdf2 3 + bcrypt 2 + NOLOGIN 哨兵 12 ⇒ 可登录面 5，与自检一致。详版见 `../00_init/README.md` §五 |
| 12 | **dry-run 行数不变**（在**已灌满**的库上跑，非空库） | seed 与 cleanup 各 dry-run 一次，前后取样计数 diff | 两册均 EXIT=0 + ROLLBACK；`demo_total=325` / `seed_total=195` **前后完全一致，diff=0** ✓ |
| 13 | **完整 11 文件链从零建库**（2026-11-18 复验，库 `ecos_demo_rt2`） | 按 `00_init/README.md` 规定顺序 `00→08→**11_public_pending**→09→10`，再 `demo_seed` → `demo_verify` | 11 文件**逐个 EXIT=0**（`TOTAL_FAIL=0`）；`public` 落 7 张待裁表；seed 自检 `scenario=245 … sys_config=178`；verify **86 OK / 0 EMPTY / 0 DRIFT**。**这一腿补的是「顺序」**——此前 12 腿建库时未纳入 `11_public_pending.sql`，缺它则 `public` 7 表缺失、从零建库不完整 |

### 第 8 腿：改脏后重置探针（清空册的真实用途）

只验「基线行能删掉」不够——清空册要能收拾**现场被改脏**的库。故先人为造脏：

| 造脏动作 | 造脏后 | 清空后 | 重灌后 |
|---------|-------|-------|-------|
| `UPDATE ecos_ai.ecos_skill SET name='PROBE_DIRTY' WHERE id=1` | name 被改 | 表 0 行 | **name 恢复「数据治理」** ✓ |
| `INSERT ecos_ai.ecos_skill (id,name) VALUES (999999,'PROBE_EXTRA')` | 4 行（基线 3） | **0 行** ✓ | 3 行 ✓ |
| `INSERT ecos_control.ecos_dq_issue` 2 行指向演示 `dq_rule` | 2 行 | **0 行** ✓（跨集从属 DELETE 真的生效） | — |
| `INSERT ecos_data.ecos_pipeline_execution` 2 行指向演示 `pipeline_definition` | 2 行 | **0 行** ✓（无 cascade，靠显式 DELETE） | — |
| `INSERT ecos_control.ecos_audit_log` 1 行 `operation='PROBE_SURVIVE'` | 1 行 | **1 行存活** ✓（无关运行时数据不被误删） | — |

> 前两腿在验证库上原本是**空跑**（那两张运行时表 0 行），跨集 DELETE 等于没验到。
> 造脏后才真正照出这条腿的行为。探针行已在验证后清除。

### 身份列序列校准

`ecos_control.ecos_scenario_sandbox_layout.id` 是 `bigint GENERATED ALWAYS AS IDENTITY`（全库仅 2 个身份列之一）。
显式插值必须 `OVERRIDING SYSTEM VALUE`，且插后 `setval` 校准，否则应用下次 `nextval` 撞键。
实测：重灌后 `MAX(id)=1`、`ecos_scenario_sandbox_layout_id_seq.last_value=1` ⇒ 下次取 2，**不撞键** ✓。

---

## 五、登记偏差与待裁定面（不在本批整改）

1. **身份列违反 MC01/MC02**
   `ecos_scenario_sandbox_layout.id` / `ecos_scenario_mind.id` 是 `bigint GENERATED ALWAYS AS IDENTITY`，
   与「主键 `VARCHAR(36)` 应用侧 UUID、DDL 禁自增」冲突。本册用 `OVERRIDING SYSTEM VALUE` + `setval` 适配现状，不改结构。

2. **演示集内 2 个前缀变体重复族**（去 `ecos_` 前缀后同名）
   | 族 | 成员 | 代码引用实查 | 处置 |
   |----|------|-------------|------|
   | `dq_rule` | `ecos_data.dq_rule`(37) + `ecos_control.ecos_dq_rule`(5) | 写方 = `ecos_data.dq_rule`（`DqRuleMapper` / `DqRuleInsertSqlFaceLiveTest`）；但 `ecos_control.ecos_dq_rule` 是 `ecos_dq_issue` 的**真实 FK 父** | 属已登记的 11 个 `dq_*` 族（V111 `ecos_dq` vs V339/V342 `ecos_data`），**两份都导出**，待逐族裁定 |
   | `agent_registry` | `ecos_ai.ecos_agent_registry`(13) + `ecos_ai.agent_registry`(4) | 全后端 grep `[^_]agent_registry` = **0 命中**；13 处引用全指 `ecos_agent_registry` | `agent_registry` 是**零引用孤儿表**（4 行）。仍导出以保全唯一副本，但**不当它是活面**；待裁定 |

3. **休眠 FK 与 0 行空壳锚定**
   `fk_cog_proj_goal` 把 `ecos_cognitive.ecos_biz_project.goal_id` 锚在 `ecos_cognitive.ecos_wm_goal`（**0 行空壳**），
   而 21 行真实数据在 `ecos_control.ecos_wm_goal`。3 行 `goal_id` 值全 NULL ⇒ FK 休眠、不阻塞插入。
   这是重名表族未裁定的**具体实例**：空壳可能是迁移目标而非垃圾，**不得先删**。
   `demo_verify.sql §3b` 每次输出这三项现状，供裁定时对照。

4. **`td_user` 基线 17 而非实库 18**
   实库多的一行是 `tdv01`（`user_id=bc7afcc0…`，`status=DELETED`，`real_name='登录迁移验证账号'`，`created_by=admin`，
   建账时刻 **2026-11-17 19:37:32**，晚于种子册导出时刻 **19:18:52**，0 条角色隶属）。
   属登录迁移验证的**测试残留**，刻意不进系统种子。
   ⇒ `demo_verify.sql §2` 的基线取**种子册实际 INSERT 语句数**，不取实库 `count(*)`，否则新库会假报 DRIFT。

5. **DRIFT 不必然是缺陷**
   §1 判 DRIFT 只说明「与导出基线不一致」。界面新增/修改业务数据属正常演进。
   要回到基线跑「清空 + 重灌」；要更新基线则重新导出（生成器口径见 §六）。

6. **MC03 口径**
   三册均**不使用** `ON CONFLICT` / `RETURNING` / `::` cast。幂等靠 `INSERT ... SELECT ... WHERE NOT EXISTS`（按主键判重）。
   58 张演示表**全部有主键**，无需退化到全列比对。
   已存在的行**不覆盖**（保护现场修改）——这也是「重置必须先清空」的原因。

7. **多行字符串字面量**
   `quote_nullable` 会把文本值里的真实换行原样输出 ⇒ 一条 INSERT 可能跨多行。
   **统计行数必须按 `^INSERT INTO <表名> ` 计语句，不能按文件行数计**。
   本项目曾因此误报 `ecos_agent`「14 vs 5」、`ecos_workflow`「35 vs 5」，实际语句数恰为 5/5。

---

## 六、与 `00_init/` 的关系 / 重新导出

```
00_init/00_create_schemas.sql      8 schema + vector/pg_trgm 扩展
00_init/01~08_<schema>.sql         345 张父表 DDL（分区只保父表 + DEFAULT）
00_init/11_public_pending.sql      7 张 public 独有表（归属待裁定）
00_init/09_foreign_keys.sql        36 条 FK（跨 schema，统一下沉）
00_init/10_system_seed.sql         27 表 / 795 行系统配置
        ↓
04-demo-data/demo_seed.sql         58 表 / 1014 行演示业务数据   ← 本目录
04-demo-data/demo_cleanup.sql      清空演示面（种子面不动）
04-demo-data/demo_verify.sql       只读对账
```

- **表级互斥已实查**：27 种子表 ∩ 58 演示表 = ∅；去前缀同名族也已扫（§五-2）。
- 历史迁移脚本（`02-flyway-migration/gateway/`，298 个 `V*.sql`，MAX=V349 / NEXT=V350）是**演进轨迹**；
  `00_init/` + `04-demo-data/` 是**从零建库的目标态**。两者并存，归档属后续阶段。
- 重新导出：分类 + FK 拓扑 + 深度序 + 身份列处理全部由 catalog 查询驱动，不靠人工假设。
  改基线后需同步更新 `demo_verify.sql` 的逐表 baseline 与 `demo_cleanup.sql` 的表清单（二者与 seed 同源生成）。

---

## 七、在真实库 `sys_man` 上跑清空 —— 需授权

本目录全部验证都在**独立验证库** `ecos_init_verify4` 上完成。
在 `sys_man` 上跑 `demo_cleanup.sql` 会**删掉 1014 行现场业务数据**（含界面上已改过的内容），属不可逆操作。

按项目红线（IR03 / 破坏性 DB 操作需备份 + 逐项授权 + 先 ROLLBACK 演练）：
**未获明确授权前不在 `sys_man` 上实跑清空。** 需要时应先：

1. 全库备份（`pg_dump`）并确认备份覆盖全部待删表；
2. `demo_cleanup.sql` dry-run（默认即回滚）+ 看守卫A/B 通知；
3. 取得逐项授权后再 `-v ecos_commit=1`；
4. 跑 `demo_verify.sql` 确认 58 EMPTY + 种子面 27 表未变；
5. 需要恢复基线再跑 `demo_seed.sql`。

---

## 八、活体 UI 端到端实测（2026-11-18，真实库 `sys_man` + 7 JAR + 前端 :3000）

前面 §四 证的是「脚本能灌进去、行数对得上」。本节证的是**灌进去的数据在界面上真的看得见**——
两者不是一回事，故单独实测。全程只读，未改任何业务数据。

登录走**真实表单**（填用户名/口令 → 点登录），不是直接塞 token：
`#/login` → `#/decision`，`localStorage` 落 `token`/`roles`/`username`，
roles = `ROLE_SUPER_ADMIN` / `ADMIN` / `SECURITY_AUDITOR`（V348 恢复的三码，活体再确认）。

### 看得见的面（演示数据确实渲染）

| 面 | 实测渲染内容 |
|---|---|
| 决策面板 | 内核状态「运行中」、认知引擎「运行中」、任务引擎 8 排队 / 301 总计、活跃场景 **232** |
| 场景工作台 `sc001` | 「飞行运行安全」ACTIVE、蓝图 **v13**（v1~v13 版本轨）、安全指标 **0.87**、更新时间 2026-11-10 |
| 本体工作台 | 业务对象实体 **4**（2 已归域 / 2 未归类）、关系链接 **2**、系统业务域 **5**、接口与属性指标 **11**（5 契约 + 6 共享属性）、逻辑函数 **7**、原始数据集 **2**；图谱渲染 4 节点 `ent001 用户` / `ent002 组织机构` / `ent003 审批案例` / `ent18 Probe` |
| 知识文档接入面 | `GET /api/v1/knowledge/ingest/documents` → **total=36**，与实库 `ecos_knowledge.kb_doc` 36 行逐数相符 |
| 数据工作台 | 数据源 **DemoPG**（PostgreSQL，指向 `ecos-postgres/sys_man`）；`GET /api/v1/pipeline/definitions` 返回真实管道行 |

### 显示为 0 / 不可达的面 —— 逐项归因，不含糊

| 面 | 现象 | 实测真因 | 是否本批引入 |
|---|---|---|---|
| 知识总览 6 KPI | 知识资产/图谱实体/图谱关系/向量索引/待审核/抽取任务 全 0 | 这 6 张卡读 `knowledge_article` / `graph_node` / `graph_edge` / `knowledge_embedding`，实库**精确 count 均为 0 行**。`kb_doc`(36) 是**另一张表**，总览不读它（`KnowledgeStatsAggregator:58-61`）⇒ 显示 0 是**正确**的，不是缺陷 | 否 |
| 本体列表 | `GET /api/v1/ecos/ontologies` → `data: []` | **按设计停用**：`OntologyDao.findAllOntologies():410-413` 直接 `return Collections.emptyList()`，注释「V200: 表已 DROP，无实存源」。权威域源是 `/api/v1/ontology/domains`（返回 5 域，UI 正常渲染）。创建路径同样已改显式 409 指向域源（D54，2026-11-13） | 否 |
| 场景动作记录 | `GET /workspace/scenarios/sc001/actions` → **503**，UI 文案「认知引擎暂不可用」 | 既有缺陷面（该文案把一切失败都显示成引擎不可用，属误导文案），与本批脚本无关 | 否 |
| 年度经营预测图表 | 无结果数据 | **结构性不可达**：`ForecastRunGateway` 是 SPI，main 代码无任何 bean（实现只在测试里）⇒ 编排服务抛 501。且既有裁决明令**禁止 seed 任何 `*_result_json` / `ecos_application_forecast_run` 行**（无徽标的演示数字按虚假验收处理）。故 `ecos_dw.ecos_application_forecast_run` **0 行是正确状态**，本册刻意不灌 | 否 |
| **工作流列表**（2026-11-18 补测） | `GET /api/v1/ontology/workflows` 与 `GET /api/v1/ecos/workflows` **均 200 但 `total=0`**，而本册导出的 `ecos_control.ecos_workflow` 有 **5 行真实命名数据**（供应商准入审批 / 数据质量巡检 / 智能客服问答 / 供应商风险审查 / 设备故障应急响应） | **重名表族分叉**：`WorkflowDao:27` 注入 `@Value("${ecos.ontology.control-schema:ecos_ontology}")`，全仓**无任何配置覆盖** ⇒ 读写落 `ecos_ontology.ecos_workflow`（**0 行**）；control 那 5 行**无任何生产读方**，是孤儿。DDL 意图（V173）与代码默认值**一致指向 ontology** | 分叉否；**但本册把 5 行孤儿导进了基线**，见下方订正 |
| **知识总览「本体快照」4 项**（2026-11-18 补测） | `GET /api/v1/knowledge/stats` → `ontologySnapshotCount=0` / `ontologyVersionCount=0` / `publishedOntologyVersionCount=0` / `lastOntologySnapshotAt=null`，而 `ecos_knowledge.ecos_kb_ontology_snapshot` 实存 **1 行**（`is_deleted=0`） | **同族分叉**：写方 `KbOntologySnapshotDao:36` 注入 `ecos_knowledge`；读方 `KnowledgeStatsAggregator:38/67/69/76/80` 用**裸名** ⇒ 命中 `ecos_control.kb_ontology_snapshot`（0 行空壳）。**同方法内对照腿**：`kgSyncLogCount=10` 正确，因为 `:72/:79` 是显式限定的 | 否 |

> **本册基线的一处订正（工作流 5 行）**：`demo_seed.sql` 按「实库有行就导」的口径把
> `ecos_control.ecos_workflow` 5 行纳入了演示集，但**这 5 行生产代码根本不读**（读的是 ontology 侧空表）。
> 即「导出了 ≠ 界面看得见」。这不影响 §四 的行数对账（对账只比行数），但**影响演示效果**。
> 处置属逐族裁定 R4（把 5 行迁往 `ecos_ontology`，或改代码读 control），**需授权 + 备份 + dry-run**，
> 本批不擅自动数据。详版证据与 36 族全集见 `../03-baseline/dup-family-adjudication.md` §3.1 / §四 #31。
>
> **快照族相反：本册导对了，是代码读错了。** 实查 `demo_seed.sql:1234-1236` / `demo_cleanup.sql:157` /
> `demo_verify.sql:227-229`，演示集纳入的是 **`ecos_knowledge.ecos_kb_ontology_snapshot`（权威 1 行）**，
> 而 `ecos_control.kb_ontology_snapshot`（0 行空壳）按「0 行表不导出」规则**正确排除**。
> ⇒ 这一族**不需要改基线**，只需修读腿限定（裁定册 R1）。
> （此处一度误写成「空壳被导进基线」，经逐文件 grep 反证推翻，记此防再犯。）

> 「企业经营管控」在工程里是**年度经营预测场景页**（`pages/workbench/[scenarioId]/forecast.tsx`），
> 不是 `ecos_business_scenario` 里的一行——实库该表 245 行中无此名。
> 该页的模式开关 `FORECAST_REAL_SOURCES_CONNECTED = false`（`services/forecastScenarioMode.ts:23`），
> 即**恒演示态、必须打 DEMO 徽标**，翻牌前置条件（H-1/H-5 四源串通）尚未结案。
> 所以「图表有数据」这一条**在当前工程状态下不可能诚实达成**，本批不伪造数据去凑它。

### 两条方法论教训（都已踩过，记此防再犯）

1. **导航后立刻读 `innerText` 会读到加载态。** 本体面首次捕获显示「业务对象实体 0 / 本体数据加载中…」，
   等 3.5 s  settled 后同一页是 **4**。若据首次捕获下结论，会误报「演示数据没灌进去」。
   ⇒ 断言 UI 前必须等加载结束（判 `加载中` 字样消失或显式等待）。
2. **「实体 0」一度被我归因为重名表族分叉，实测反证推翻。**
   实库 `ecos_control.ecos_ontology_entity` 18 行 / `ecos_ontology.ecos_ontology_entity` 0 行，
   看似「UI 读了引擎侧空副本」。但 `OntologyDao` 全部是**裸名** SQL，按 `currentSchema` 首位命中的是
   `ecos_control`（18 行），根本不会读到引擎侧；且 18 行里**只有 4 行 `is_deleted=0`**（其余 14 行软删，
   分散在 `Finance`/`Procurement`/`DOM_D139` 等 ontology_id 下）。真因是上面那条「列表端点按设计返回空」。
   ⇒ 差一点登记一条假缺陷。**归因必须有反证，不能停在「看起来像」。**

### 自检措辞订正后的复验（改过产物就必须重跑）

本节实测发现 `demo_seed.sql` 后置自检里「`kb_doc` 为空 ⇒ **知识库面**无内容」措辞过宽
（知识总览 6 KPI 并不读 `kb_doc`），已把该句改为「**文档接入记录面**无内容」并注明总览读哪四张表。
生成器与产物同批改，改完重跑：

| 腿 | 结果 |
|---|---|
| 改后 `demo_seed.sql` dry-run（`ecos_init_verify4`，已灌满） | **EXIT=0**，0 ERROR / 0 WARNING，NOTICE 仍 `scenario=245 ontology_entity=18 kb_doc=36 pipeline_def=41 td_user=17 sys_config=178`，末尾 ROLLBACK |
| **变异探针：证该断言能变红** | 事务内 `DELETE FROM ecos_knowledge.kb_doc` 后单跑自检 DO 块 ⇒ **EXIT=3**，`ERROR: kb_doc 为空 ⇒ 文档接入记录面无内容（…）` 逐字命中；`ROLLBACK` 后 `kb_doc` 仍 **36** 行 ✓ |

探针本身踩了两次坑，记下来：

1. **`\i demo_seed.sql` 在容器内解析路径** ⇒ 报 `No such file or directory`、EXIT=3。
   这是**假红**（文件找不到，不是断言生效）。改为把产物内容**内联进探针流**才真跑起来。
2. **先 DELETE 再跑整册 seed 无法触发断言** ⇒ seed 是幂等的，自检之前已把 36 行 `kb_doc` 补回，
   计数自然非 0（EXIT=0）。要验断言有牙，必须**只跑自检 DO 块**、不让 INSERT 先补回。
   ⇒ 「探针没红」先怀疑探针没真改上，别急着说断言失效。