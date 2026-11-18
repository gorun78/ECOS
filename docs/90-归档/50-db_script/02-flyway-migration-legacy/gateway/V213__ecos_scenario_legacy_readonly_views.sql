-- V213 (卷07 §六 E 章 E-2/E-7): 场景域旧 7 表只读对账视图（v_legacy_* 前缀，W183/W184）
-- 追溯: W183/C165、W184/C166（W169 词汇映射亦由本视图承载）；需求依据 REQ-WS-02 / REQ-DB-01
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-25=①、R-26=①、R-30=①+②；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
--
-- 【2026-09-30 校订】DR03 收口：分册 07 L1073「新表统一 `_v2` 后缀」违 DR03（反例 `ecos_workflow_task_v2`），
--   V205~V211 七张目标表已改名，本文件"v2 列面"一律指下右列新名（本文件仅 FROM 旧表，无引用断裂）：
--     public.ecos_business_scenario_v2      → public.ecos_scenario_definition
--     public.ecos_scenario_binding_v2       → public.ecos_scenario_asset_binding
--     public.ecos_scenario_binding_link_v2  → public.ecos_scenario_binding_edge
--     public.ecos_scenario_mind_v2          → public.ecos_scenario_mind_variant
--     public.ecos_scenario_run_v2           → public.ecos_scenario_execution
--     public.ecos_scenario_sandbox_layout_v2→ public.ecos_scenario_canvas_layout
--     public.ecos_decision_record_v2        → public.ecos_scenario_decision_record
--   改名安全性：七表均零行（本批不实跑）+ 全仓 Java/XML/TS 0 引用；左侧旧名（停写不删，IR03）不变。
--
-- 形态（分册 07 E-2 定版）: 7 个 `v_legacy_*` 前缀只读对账视图，把旧表列面**对齐到 v2 列面**，
--   缺列显式补形（0 AS is_island / CAST(NULL AS VARCHAR(20)) AS version_no 等），**禁静默丢字段**；
--   仅供 E-7 对账与取证，**不接任何业务读写链路**。
--   ※ 任务书表述为"CREATE OR REPLACE VIEW public.v_<旧表名>"；文档 L1075/L1087 定版为 `v_legacy_*`
--     前缀（且显式否决与旧表同名/近名视图），从文档口径落地。
--   ※ 文档示例 `FALSE AS is_island` 按 DR05（is_* SMALLINT）收敛为 `0 AS is_island`。
-- 落点 (MC06): 视图落 public（与旧表、v2 表同 schema；控制域目标 ecos_control 随配置注入批再议）。
-- 演示种子登记（E-6/X-48，本脚本不删不改）: 旧表内 AOC 航空演示种子 sc001~sc003（V123:47-52）、
--   sb001~sb008（V123:55-65）、bsl001/bsl002（V160:59-63，placeholder 契约）——
--   **不修改 V123/V160 历史脚本、不删库内行**（属未授权存量动作）；待剥离至
--   `ecos_backend/database/demo_seed.sql`（剥离执行亦待授权，§14.4）。
-- 上线: psql -U postgres -d sys_man -f V213__ecos_scenario_legacy_readonly_views.sql

-- ── 1. 场景主表对账视图 ──────────────────────────────────
CREATE OR REPLACE VIEW public.v_legacy_ecos_business_scenario AS
SELECT CAST(o.id AS TEXT)                       AS id,                 -- 旧 VARCHAR(64) 原样透传（对账取证）
       o.name, o.description, o.business_goal, o.department, o.priority, o.status,
       o.budget                                 AS budget_legacy_text, -- X-75: 历史串值（如 '850万'）**不做自动解析**，
                                                                       --   原文透存；换算回填属存量迁移待授权项
       CAST(NULL AS NUMERIC(18,2))              AS budget,             -- v2 金额实列在旧面无源，显式补 NULL 防空隙误读
       CAST(NULL AS VARCHAR(8))                 AS budget_currency,
       o.safety_index_target, o.actual_safety_index,
       CAST(o.metrics AS TEXT)                  AS metrics_json,       -- 旧 JSONB 文本化取证；integrityScore/mappingCompleteness
                                                                       --   属计算产物禁进 v2 权威面（E-3/X-25），只由 /completeness 现算
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       CAST(NULL AS VARCHAR(20))                AS version_no,         -- 旧表缺基线列，显式补形（X-45）
       'default'                                AS domain
FROM public.ecos_business_scenario o;

-- ── 2. 绑定表对账视图（W169：target_type 旧前端词表 → 后端六值新值域映射）──
CREATE OR REPLACE VIEW public.v_legacy_ecos_scenario_binding AS
SELECT CAST(o.id AS TEXT) AS id, CAST(o.scenario_id AS TEXT) AS scenario_id,
       o.binding_type,                                                  -- 旧列已是后端词表名（无 CHECK 保护，X-73）
       CASE o.target_type                                                -- W169 值映射（仅视图面，不 UPDATE 存量）
           WHEN 'DATASOURCE'       THEN 'DATASET'
           WHEN 'ONTOLOGY_ENTITY'  THEN 'OBJECT_TYPE'
           WHEN 'KNOWLEDGE_ARTICLE' THEN 'KNOWLEDGE_BASE'
           WHEN 'AGENT_PROFILE'    THEN 'AI_AGENT'
           WHEN 'INTERFACE_REF'    THEN 'INTERFACE'
           ELSE o.target_type END                    AS target_type_mapped,
       o.target_ref, o.target_id, o.remark,
       0                                        AS is_island,          -- 岛标语义只在新表面生效（文档 FALSE，按 DR05 补 0）
       CAST(NULL AS VARCHAR(64))                AS island_reason,
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       CAST(NULL AS VARCHAR(20))                AS version_no,
       'default'                                AS domain
FROM public.ecos_scenario_binding o;

-- ── 3. 边表对账视图 ──────────────────────────────────────
CREATE OR REPLACE VIEW public.v_legacy_ecos_scenario_binding_link AS
SELECT CAST(o.id AS TEXT) AS id, CAST(o.scenario_id AS TEXT) AS scenario_id,
       CAST(o.source_binding_id AS TEXT) AS source_binding_id,
       CAST(o.target_binding_id AS TEXT) AS target_binding_id,
       o.link_type,
       o.source_contract,                        -- 旧 placeholder-* 占位契约原样取证（V207 CHECK 禁新数据再写入）
       o.remark,
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       o.version_no, o.domain                    -- V160 基线列本已齐备，直传
FROM public.ecos_scenario_binding_link o;

-- ── 4. 心智表对账视图 ────────────────────────────────────
CREATE OR REPLACE VIEW public.v_legacy_ecos_scenario_mind AS
SELECT CAST(o.id AS TEXT)                       AS id,                 -- 旧 BIGINT IDENTITY 文本化取证
       CAST(o.scenario_id AS TEXT)              AS scenario_id,
       o.mind_label,
       o.active_mind                                                    -- 旧哨兵列原样取证（v2 已改 ecos_scenario_active_mind）
       ,
       CAST(o.initial_belief_jsonb  AS TEXT)    AS initial_belief_json, -- 旧 _jsonb 后缀违例，视图面对齐 _json 名
       CAST(o.evidence_refs         AS TEXT)    AS evidence_refs_json,  -- 明细化（mind_ref 表）后本列仅快照取证
       CAST(o.hypothesis_refs       AS TEXT)    AS hypothesis_refs_json,
       CAST(o.model_refs            AS TEXT)    AS model_refs_json,
       CAST(o.cognitive_endpoints   AS TEXT)    AS cognitive_endpoints_json, -- E-3: 新表面已移出该列（经 sysman 配置单源），
                                                                              --   本视图保留仅供取证，不接业务读
       o.initial_confidence,
       CASE WHEN COALESCE(o.is_deleted, 0) = 0
            THEN TIMESTAMP '1970-01-01 00:00:00'
            ELSE o.update_time END              AS deleted_guard,       -- 对齐 V208 纪元值语义（MC03：typed literal 非裸 cast）
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       CAST(NULL AS VARCHAR(20))                AS version_no,
       'default'                                AS domain
FROM public.ecos_scenario_mind o;

-- ── 5. 沙盘布局对账视图 ──────────────────────────────────
CREATE OR REPLACE VIEW public.v_legacy_ecos_scenario_sandbox_layout AS
SELECT CAST(o.id AS TEXT)                       AS id,                 -- 旧 BIGINT IDENTITY 文本化取证
       CAST(o.scenario_id AS TEXT)              AS scenario_id,
       CAST(o.layout_jsonb AS TEXT)             AS layout_json,         -- 旧 _jsonb 违例名对齐 _json
       o.layout_version,
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       CAST(NULL AS VARCHAR(20))                AS version_no,
       'default'                                AS domain
FROM public.ecos_scenario_sandbox_layout o;

-- ── 6. 决策回执对账视图 ──────────────────────────────────
CREATE OR REPLACE VIEW public.v_legacy_ecos_decision_record AS
SELECT CAST(o.id AS TEXT)                       AS id,
       CAST(o.scenario_id AS TEXT)              AS scenario_id,
       CAST(o.mind_id AS TEXT)                  AS mind_id,            -- 旧 BIGINT 分叉文本化
       CAST(o.action_plan       AS TEXT)        AS action_plan_json,   -- 五必填当年嵌于 JSONB，不猜测抽取（头注 §回滚）
       CAST(o.source_refs       AS TEXT)        AS source_refs_json,   -- 明细化（ecos_decision_source_ref）后仅快照取证
       CAST(o.compliance_check  AS TEXT)        AS compliance_detail_json,
       CAST(NULL AS VARCHAR(16))                AS compliance_verdict, -- v2 实列旧面无源，显式补形
       o.proposed_by, o.accepted_by, o.accepted_at,
       CAST(NULL AS VARCHAR(36))                AS owner_user_id,      -- 五必填 v2 实列：旧表无对应，不擅自以
       CAST(NULL AS DATE)                       AS due_date,           -- proposed_by 等旧列冒充映射（X-78 语义不同），
       CAST(NULL AS VARCHAR(36))                AS approver_user_id,   -- 全部显式补 NULL 禁静默丢字段
       CAST(NULL AS NUMERIC(18,2))              AS expected_impact,
       CAST(NULL AS VARCHAR(128))               AS control_metric,
       CAST(NULL AS VARCHAR(36))                AS forecast_run_id,
       CAST(NULL AS VARCHAR(16))                AS run_mode,
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       CAST(NULL AS VARCHAR(20))                AS version_no,
       'default'                                AS domain
FROM public.ecos_decision_record o;

-- ── 7. 运行表对账视图 ────────────────────────────────────
CREATE OR REPLACE VIEW public.v_legacy_ecos_scenario_run AS
SELECT CAST(o.id AS TEXT)                       AS id,
       CAST(o.scenario_id AS TEXT)              AS scenario_id,
       o.run_type, o.status, o.metric, o.deviation,
       CAST(o.diagnosis_result  AS TEXT)        AS diagnosis_result_json,
       CAST(o.forecast_result   AS TEXT)        AS forecast_result_json,
       CAST(o.simulation_result AS TEXT)        AS simulation_result_json,
       CAST(o.strategy_result   AS TEXT)        AS strategy_result_json,
       CAST(o.decision_id AS TEXT)              AS decision_id,        -- 旧空引用原样取证（R-30②：v2 废弃该列语义）
       CAST(NULL AS VARCHAR(36))                AS decision_record_id, -- 不猜测映射（decision_id 本为空引用 X-37）
       CASE WHEN COALESCE(o.degraded, FALSE) THEN 1 ELSE 0 END AS is_degraded, -- BOOLEAN→SMALLINT 对齐 DR05
       'FORMAL'                                 AS run_mode,           -- 旧表无该维度，按 V209 默认语义补形（存量登记：
                                                                       --   不代表历史真实运行曾做过模式区分）
       CAST(NULL AS VARCHAR(64))                AS trace_id,
       o.create_time, o.update_time, o.create_by, o.update_by, o.is_deleted,
       CAST(NULL AS VARCHAR(20))                AS version_no,
       'default'                                AS domain
FROM public.ecos_scenario_run o;

-- ── 视图注释（取证用途声明）──────────────────────────────
COMMENT ON VIEW public.v_legacy_ecos_business_scenario      IS '只读对账视图（V213）：旧场景主表→v2 列面；budget 历史串值不解析；仅供 E-7 对账，不接业务链路';
COMMENT ON VIEW public.v_legacy_ecos_scenario_binding       IS '只读对账视图（V213）：target_type 前端词表→后端六值映射（W169）；is_island 补 0；不接业务链路';
COMMENT ON VIEW public.v_legacy_ecos_scenario_binding_link  IS '只读对账视图（V213）：边表列面对齐；placeholder 契约原样取证；不接业务链路';
COMMENT ON VIEW public.v_legacy_ecos_scenario_mind          IS '只读对账视图（V213）：JSONB 文本化对齐 _json 名；cognitive_endpoints 仅取证（v2 已移出）；不接业务链路';
COMMENT ON VIEW public.v_legacy_ecos_scenario_sandbox_layout IS '只读对账视图（V213）：layout_jsonb→layout_json 对齐；不接业务链路';
COMMENT ON VIEW public.v_legacy_ecos_decision_record        IS '只读对账视图（V213）：五必填 v2 实列旧面无源显式补 NULL（禁冒充映射）；不接业务链路';
COMMENT ON VIEW public.v_legacy_ecos_scenario_run           IS '只读对账视图（V213）：degraded→is_degraded、decision_id 空引用取证、run_mode 补 FORMAL；不接业务链路';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 视图为纯只读投影、CREATE OR REPLACE 天然幂等可重复执行；回滚 = 应用/对账脚本不再查询这些视图
-- （视图本身不 DROP，IR03；旧表数据零触碰）。对账 SQL 示例（只读）：
--   SELECT COUNT(*) FROM public.v_legacy_ecos_business_scenario;   -- 旧表行数快照
--   SELECT COUNT(*) FROM public.ecos_scenario_definition;         -- v2 行数快照（迁移授权后比对）

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 显式限定 + 小写下划线 ✓  [DR02] v_legacy_ecos_* 基名保留 ecos_ 前缀 ✓
-- [DR04/05] 输出列名对齐 v2（*_json TEXT / is_island/is_degraded 0-1 整型）✓
-- [MC02/MC03] 视图不改任何类型入表；JSONB 仅 CAST(x AS TEXT) 文本化取证，零裸 `::` cast；
--   零 partial/RLS/PARTITION/text[]/timestamptz ✓（旧表内存量 jsonb 列在 CAST 中出现，属存量取证描述，合规）
-- [IR03] 零 DROP/零 ALTER/零 INSERT/UPDATE（只读视图）；演示种子不删不改仅登记（E-6）✓
-- [ST07] 视图与被读旧表同落 public（控制域现基线）✓
