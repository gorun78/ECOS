-- =============================================================================
-- ECOS dev 演示数据集独立校验脚本 v1 —— SEED-ENTMGMT-V1（企业经营管控场景）
-- =============================================================================
-- 依据  : docs/30-设计/企业经营管控演示数据集设计-2026-10-12/14 册 v1.0（T3）全量数值锚点
-- 对象  : docs/50-db_script/03-seed/entmgmt_demo_seed_v1.sql 的落库结果（T4）
-- 库    : 仅本地 dev（容器 ecos-postgres / 库 sys_man）。生产不适用。
-- 纪律  : 本脚本**零持久写入** —— 全程包在 BEGIN…ROLLBACK 内，唯一写入对象是 pg_temp 里的
--         chk_result 暂存表（事务结束自动消失），不触碰任何业务表。
--
-- 与 seed 自检的区别（为什么要独立一份）：
--   seed §13 的 DO 块判定的是"我自己生成的数是否自洽"（同一批 CTE 生成、同一批 CTE 核对 ⇒ 循环论证）。
--   本脚本的参考数据是**从设计册 §3.2/§4.1/§4.2/§4.3/§4.4/§5.3 逐字转写的常量表**，
--   与库内实数做 FULL OUTER JOIN 三向 diff（缺行 / 多行 / 值不符），并把设计册手算的
--   18 枚锚点字面值与库聚合值对撞 —— 这条路径 seed 从未走过。
--   仍然诚实声明：DQ/MD 规则是**用 SQL 复现 Java 判定谓词**（逐条注明来源文件行号），
--   不是调用 Java 本身；真跑 Java 门禁属 T6 live 复验范围。
--
-- 运行：
--   cat docs/50-db_script/03-seed/entmgmt_demo_verify_v1.sql \
--     | docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f -
--   退出码 0 = 全部校验项 viol=0；任一viol≠0 ⇒ 末尾 DO 块 RAISE EXCEPTION ⇒ psql 非 0 退出。
--
-- 判定项分组：
--   A 行数与批次隔离     B 行级三向 diff（阶段/成本/资源）    C 设计册 18 锚点
--   D 主数据完备性四规则（MD-C1/C2/C3/MD-EMPTY，复现 MasterDataCompletenessChecker）
--   E DQ 门禁九条（F01/F02/F03/F04/F06/F07/F08/F09/F11，复现 DqRowGateImpl）
--   F 三级平衡（第 12 篇 12.3.3 compareTo==0 语义）        G 口径四关可 SQL 化的部分
--   H 指标注册表面（expression/快照/发布门禁 rule 4）      I 画像独立重算
--   J 情景假设            K 期间状态                       L 场景装配（绑定/连边/孤岛）
--   M 结果面必须为空      N 保留面白名单未被污染           O 演示句柄一致性
--
-- 实跑记录（2026-10-14，dev PG 容器 ecos-postgres / 库 sys_man）：
--   ① 正式跑 exit=0 —— 64 判定项全部 viol=0（17 表 467 行 / 18 锚点 / MD 四规则 / DQ 九条 /
--      三级平衡 / 口径四关可 SQL 化部分 / 画像独立重算逐值相等 / 结果面为空 / 保留面未污染）。
--   ② 阴性对照（证明判定面非空转）：把锚点字面值 21925000.00 改为 21925001.00 后同一脚本再跑
--      ⇒ exit=3，#7「C 设计册 18 枚数值锚点对撞」与 #48「L1 场景行 metrics 三值」viol=1 FAIL，
--      末尾 DO 块如期 RAISE EXCEPTION。库未受影响（事务内异常 + 只读复算）。
-- =============================================================================

\set ON_ERROR_STOP on
\echo '>>> VERIFY SEED-ENTMGMT-V1 开始（只读复算，事务末尾无条件 ROLLBACK）'
BEGIN;

CREATE TEMP TABLE chk_result (seq serial PRIMARY KEY, name text NOT NULL, viol bigint NOT NULL);

-- ═════════════════════════════════════════════════════════════════════════════
-- A..O 逐项判定（每项 INSERT 一行 (名称, 违规数)；违规数 ≠ 0 即 FAIL）
-- ═════════════════════════════════════════════════════════════════════════════
DO $do$
BEGIN

-- ── A1 逐表行数对账（期望值 = 设计册声明值，转写自 §3.1/§6.2/§5.3/§5.4/§6.3/§4.1-4.4/§6.4/§6.5/§6.1）
INSERT INTO chk_result(name, viol)
SELECT 'A1 逐表行数对账（17 表）= 467 行', count(*) FROM (
    SELECT w.tbl, w.want, coalesce(g.got, -1) AS got
    FROM (VALUES
        ('ecos_control.td_organization',                2),
        ('ecos_ontology.ecos_ontology_entity',          3),
        ('ecos_knowledge.knowledge_article',            1),
        ('ecos_ai.ecos_agent',                          1),
        ('ecos_ontology.ecos_caliber',                  4),
        ('ecos_ontology.ecos_caliber_version',          4),
        ('ecos_ontology.ecos_metric_definition',        4),
        ('ecos_data.ecos_data_period_status',          12),
        ('ecos_dw.ecos_biz_project_attribution',        4),
        ('ecos_dw.ecos_biz_stage_fact',               160),
        ('ecos_dw.ecos_biz_cost_fact',                160),
        ('ecos_dw.ecos_biz_resource_fact',             96),
        ('ecos_knowledge.ecos_kb_profile',              4),
        ('ecos_knowledge.ecos_kb_assumption',           4),
        ('ecos_ai.ecos_business_scenario',              1),
        ('ecos_ai.ecos_scenario_binding',               4),
        ('ecos_control.ecos_scenario_binding_link',     3)
    ) AS w(tbl, want)
    LEFT JOIN (
        SELECT 'ecos_control.td_organization' tbl, count(*) got FROM ecos_control.td_organization
         WHERE remark = 'SEED-ENTMGMT-V1'
        UNION ALL SELECT 'ecos_ontology.ecos_ontology_entity', count(*)
          FROM ecos_ontology.ecos_ontology_entity WHERE domain = 'entmgmt' AND create_by = 'seed-entmgmt'
        UNION ALL SELECT 'ecos_knowledge.knowledge_article', count(*)
          FROM ecos_knowledge.knowledge_article WHERE create_by = 'seed-entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_ai.ecos_agent', count(*)
          FROM ecos_ai.ecos_agent WHERE id = md5('seed:entmgmt-v1:agent:entmgmt-operating-copilot')
        UNION ALL SELECT 'ecos_ontology.ecos_caliber', count(*)
          FROM ecos_ontology.ecos_caliber WHERE domain = 'entmgmt' AND create_by = 'seed-entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_ontology.ecos_caliber_version', count(*)
          FROM ecos_ontology.ecos_caliber_version WHERE domain = 'entmgmt' AND create_by = 'seed-entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_ontology.ecos_metric_definition', count(*)
          FROM ecos_ontology.ecos_metric_definition WHERE domain = 'entmgmt' AND create_by = 'seed-entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_data.ecos_data_period_status', count(*)
          FROM ecos_data.ecos_data_period_status WHERE domain = 'entmgmt' AND create_by = 'seed-entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_dw.ecos_biz_project_attribution', count(*)
          FROM ecos_dw.ecos_biz_project_attribution WHERE batch_id = 'SEED-ENTMGMT-V1' AND is_deleted = 0
        UNION ALL SELECT 'ecos_dw.ecos_biz_stage_fact', count(*)
          FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id = 'SEED-ENTMGMT-V1' AND is_deleted = 0
        UNION ALL SELECT 'ecos_dw.ecos_biz_cost_fact', count(*)
          FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id = 'SEED-ENTMGMT-V1' AND is_deleted = 0
        UNION ALL SELECT 'ecos_dw.ecos_biz_resource_fact', count(*)
          FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id = 'SEED-ENTMGMT-V1' AND is_deleted = 0
        UNION ALL SELECT 'ecos_knowledge.ecos_kb_profile', count(*)
          FROM ecos_knowledge.ecos_kb_profile WHERE source_query_ref = 'seed:entmgmt-v1' AND is_deleted = 0
        UNION ALL SELECT 'ecos_knowledge.ecos_kb_assumption', count(*)
          FROM ecos_knowledge.ecos_kb_assumption WHERE domain = 'entmgmt' AND create_by = 'seed-entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_ai.ecos_business_scenario', count(*)
          FROM ecos_ai.ecos_business_scenario WHERE id = 'sc_entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_ai.ecos_scenario_binding', count(*)
          FROM ecos_ai.ecos_scenario_binding WHERE scenario_id = 'sc_entmgmt' AND is_deleted = 0
        UNION ALL SELECT 'ecos_control.ecos_scenario_binding_link', count(*)
          FROM ecos_control.ecos_scenario_binding_link WHERE scenario_id = 'sc_entmgmt' AND is_deleted = 0
    ) g ON g.tbl = w.tbl
) q WHERE q.got <> q.want;

-- ── A2 批次隔离：业务事实五表内除本批次外不得有任何其它 batch（证明清理真跑 + 无第三方数据混入）
INSERT INTO chk_result(name, viol)
SELECT 'A2 事实表批次隔离（无其它 batch_id）', count(*) FROM (
    SELECT 'stage' t, batch_id b, count(*) n FROM ecos_dw.ecos_biz_stage_fact GROUP BY 1,2
    UNION ALL SELECT 'cost', batch_id, count(*) FROM ecos_dw.ecos_biz_cost_fact GROUP BY 1,2
    UNION ALL SELECT 'resource', batch_id, count(*) FROM ecos_dw.ecos_biz_resource_fact GROUP BY 1,2
    UNION ALL SELECT 'attribution', batch_id, count(*) FROM ecos_dw.ecos_biz_project_attribution GROUP BY 1,2
) q WHERE q.b IS DISTINCT FROM 'SEED-ENTMGMT-V1';

-- ── B0 参考数据自身规模（设计册转写完整性；不成立则后面所有 diff 无意义）
INSERT INTO chk_result(name, viol)
SELECT 'B0 设计册参考率表规模 = 48（6 组 × 期跨度）', count(*) FROM (
    SELECT count(*) FILTER (WHERE r.fact_type = 'ACTUAL') AS act,
           count(*) FILTER (WHERE r.fact_type = 'PLAN')   AS pln
    FROM (VALUES
        ('ACCEPTANCE','ACTUAL','2026-01',0.8000::numeric),('ACCEPTANCE','ACTUAL','2026-02',0.8200),
        ('ACCEPTANCE','ACTUAL','2026-03',0.8500),('ACCEPTANCE','ACTUAL','2026-04',0.8600),
        ('ACCEPTANCE','ACTUAL','2026-05',0.8800),('ACCEPTANCE','ACTUAL','2026-06',0.9000),
        ('ACCEPTANCE','ACTUAL','2026-07',0.9100),('ACCEPTANCE','ACTUAL','2026-08',0.9000),
        ('ACCEPTANCE','ACTUAL','2026-09',0.9200),('ACCEPTANCE','ACTUAL','2026-10',0.9300),
        ('ACCEPTANCE','PLAN','2026-07',0.9000),('ACCEPTANCE','PLAN','2026-08',0.9000),
        ('ACCEPTANCE','PLAN','2026-09',0.9000),('ACCEPTANCE','PLAN','2026-10',0.9200),
        ('ACCEPTANCE','PLAN','2026-11',0.9400),('ACCEPTANCE','PLAN','2026-12',0.9500),
        ('COLLECTION','ACTUAL','2026-01',0.7000::numeric),('COLLECTION','ACTUAL','2026-02',0.7200),
        ('COLLECTION','ACTUAL','2026-03',0.7500),('COLLECTION','ACTUAL','2026-04',0.7800),
        ('COLLECTION','ACTUAL','2026-05',0.8000),('COLLECTION','ACTUAL','2026-06',0.8200),
        ('COLLECTION','ACTUAL','2026-07',0.8400),('COLLECTION','ACTUAL','2026-08',0.8500),
        ('COLLECTION','ACTUAL','2026-09',0.8600),('COLLECTION','ACTUAL','2026-10',0.8800),
        ('COLLECTION','PLAN','2026-07',0.8600),('COLLECTION','PLAN','2026-08',0.8800),
        ('COLLECTION','PLAN','2026-09',0.9000),('COLLECTION','PLAN','2026-10',0.9000),
        ('COLLECTION','PLAN','2026-11',0.9200),('COLLECTION','PLAN','2026-12',0.9400),
        ('SUBCONTRACT','ACTUAL','2026-01',0.5500::numeric),('SUBCONTRACT','ACTUAL','2026-02',0.5600),
        ('SUBCONTRACT','ACTUAL','2026-03',0.5800),('SUBCONTRACT','ACTUAL','2026-04',0.6000),
        ('SUBCONTRACT','ACTUAL','2026-05',0.6000),('SUBCONTRACT','ACTUAL','2026-06',0.6200),
        ('SUBCONTRACT','ACTUAL','2026-07',0.6300),('SUBCONTRACT','ACTUAL','2026-08',0.6400),
        ('SUBCONTRACT','ACTUAL','2026-09',0.6500),('SUBCONTRACT','ACTUAL','2026-10',0.6600),
        ('SUBCONTRACT','PLAN','2026-07',0.6500),('SUBCONTRACT','PLAN','2026-08',0.6600),
        ('SUBCONTRACT','PLAN','2026-09',0.6700),('SUBCONTRACT','PLAN','2026-10',0.6800),
        ('SUBCONTRACT','PLAN','2026-11',0.7000),('SUBCONTRACT','PLAN','2026-12',0.7200)
    ) AS r(stage, fact_type, period, realization_rate)
) q WHERE q.act <> 30 OR q.pln <> 18;

-- ── B1 阶段事实三向 diff：参考 = 设计册 §3.2 合同月额 + §4.2 率表/组合范围 × 库内 §4.1 归属比率
--     违规 = 缺行 + 多行 + (amount/contract_base/attribution_ratio/realization_rate) 任一不符
INSERT INTO chk_result(name, viol)
SELECT 'B1 阶段事实行级三向 diff（缺/多/值不符）',
       q.missing + q.extra + q.val_mismatch
FROM (
    WITH scope(stage, project_id, department_id) AS (VALUES
        ('ACCEPTANCE','p-ent-001','org_ent_ops'),('ACCEPTANCE','p-ent-001','org_ent_fin'),
        ('ACCEPTANCE','p-ent-002','org_ent_ops'),('ACCEPTANCE','p-ent-003','org_ent_fin'),
        ('COLLECTION','p-ent-001','org_ent_ops'),('COLLECTION','p-ent-001','org_ent_fin'),
        ('COLLECTION','p-ent-002','org_ent_ops'),('COLLECTION','p-ent-003','org_ent_fin'),
        ('SUBCONTRACT','p-ent-001','org_ent_ops'),('SUBCONTRACT','p-ent-002','org_ent_ops')),
    rate(stage, fact_type, period, realization_rate) AS (VALUES
        ('ACCEPTANCE','ACTUAL','2026-01',0.8000::numeric),('ACCEPTANCE','ACTUAL','2026-02',0.8200),
        ('ACCEPTANCE','ACTUAL','2026-03',0.8500),('ACCEPTANCE','ACTUAL','2026-04',0.8600),
        ('ACCEPTANCE','ACTUAL','2026-05',0.8800),('ACCEPTANCE','ACTUAL','2026-06',0.9000),
        ('ACCEPTANCE','ACTUAL','2026-07',0.9100),('ACCEPTANCE','ACTUAL','2026-08',0.9000),
        ('ACCEPTANCE','ACTUAL','2026-09',0.9200),('ACCEPTANCE','ACTUAL','2026-10',0.9300),
        ('ACCEPTANCE','PLAN','2026-07',0.9000),('ACCEPTANCE','PLAN','2026-08',0.9000),
        ('ACCEPTANCE','PLAN','2026-09',0.9000),('ACCEPTANCE','PLAN','2026-10',0.9200),
        ('ACCEPTANCE','PLAN','2026-11',0.9400),('ACCEPTANCE','PLAN','2026-12',0.9500),
        ('COLLECTION','ACTUAL','2026-01',0.7000::numeric),('COLLECTION','ACTUAL','2026-02',0.7200),
        ('COLLECTION','ACTUAL','2026-03',0.7500),('COLLECTION','ACTUAL','2026-04',0.7800),
        ('COLLECTION','ACTUAL','2026-05',0.8000),('COLLECTION','ACTUAL','2026-06',0.8200),
        ('COLLECTION','ACTUAL','2026-07',0.8400),('COLLECTION','ACTUAL','2026-08',0.8500),
        ('COLLECTION','ACTUAL','2026-09',0.8600),('COLLECTION','ACTUAL','2026-10',0.8800),
        ('COLLECTION','PLAN','2026-07',0.8600),('COLLECTION','PLAN','2026-08',0.8800),
        ('COLLECTION','PLAN','2026-09',0.9000),('COLLECTION','PLAN','2026-10',0.9000),
        ('COLLECTION','PLAN','2026-11',0.9200),('COLLECTION','PLAN','2026-12',0.9400),
        ('SUBCONTRACT','ACTUAL','2026-01',0.5500::numeric),('SUBCONTRACT','ACTUAL','2026-02',0.5600),
        ('SUBCONTRACT','ACTUAL','2026-03',0.5800),('SUBCONTRACT','ACTUAL','2026-04',0.6000),
        ('SUBCONTRACT','ACTUAL','2026-05',0.6000),('SUBCONTRACT','ACTUAL','2026-06',0.6200),
        ('SUBCONTRACT','ACTUAL','2026-07',0.6300),('SUBCONTRACT','ACTUAL','2026-08',0.6400),
        ('SUBCONTRACT','ACTUAL','2026-09',0.6500),('SUBCONTRACT','ACTUAL','2026-10',0.6600),
        ('SUBCONTRACT','PLAN','2026-07',0.6500),('SUBCONTRACT','PLAN','2026-08',0.6600),
        ('SUBCONTRACT','PLAN','2026-09',0.6700),('SUBCONTRACT','PLAN','2026-10',0.6800),
        ('SUBCONTRACT','PLAN','2026-11',0.7000),('SUBCONTRACT','PLAN','2026-12',0.7200)),
    base(project_id, contract_base) AS (VALUES
        ('p-ent-001', 1200000::numeric),('p-ent-002', 800000::numeric),('p-ent-003', 500000::numeric)),
    expected AS (
        SELECT sc.stage, sc.project_id, sc.department_id, r.fact_type, r.period,
               b.contract_base, a.attribution_ratio, r.realization_rate,
               round(b.contract_base * a.attribution_ratio * r.realization_rate, 2) AS amount
        FROM scope sc
        JOIN rate r ON r.stage = sc.stage
        JOIN base b ON b.project_id = sc.project_id
        JOIN ecos_dw.ecos_biz_project_attribution a
             ON a.project_id = sc.project_id AND a.department_id = sc.department_id
            AND a.is_active = 1 AND a.is_deleted = 0),
    actual AS (
        SELECT s.stage, s.project_id, s.department_id, s.fact_type, s.period,
               s.contract_base, s.attribution_ratio, s.realization_rate, s.amount
        FROM ecos_dw.ecos_biz_stage_fact s
        WHERE s.batch_id = 'SEED-ENTMGMT-V1' AND s.is_deleted = 0)
    SELECT count(*) FILTER (WHERE a.project_id IS NULL) AS missing,
           count(*) FILTER (WHERE e.project_id IS NULL) AS extra,
           count(*) FILTER (WHERE e.project_id IS NOT NULL AND a.project_id IS NOT NULL
                                  AND (e.amount             <> a.amount
                                    OR e.contract_base      <> a.contract_base
                                    OR e.attribution_ratio  <> a.attribution_ratio
                                    OR e.realization_rate   <> a.realization_rate)) AS val_mismatch
    FROM expected e
    FULL OUTER JOIN actual a
         ON  e.stage = a.stage AND e.project_id = a.project_id
         AND e.department_id = a.department_id AND e.fact_type = a.fact_type AND e.period = a.period
) q;

-- ── B2 成本事实三向 diff（参考 = 设计册 §4.3 十条序列 × 16 期跨度）
INSERT INTO chk_result(name, viol)
SELECT 'B2 成本事实行级三向 diff（缺/多/值不符）',
       q.missing + q.extra + q.val_mismatch
FROM (
    WITH series(project_id, department_id, is_pool, cost_category, direct_or_allocated,
                allocation_rule_ref, monthly_amount) AS (VALUES
        ('p-ent-001','org_ent_ops', 0::smallint,'SALARY','DIRECT',NULL,320000::numeric),
        ('p-ent-001','org_ent_ops', 0,'MANAGEMENT','ALLOCATED','alloc-rule-001',60000::numeric),
        ('p-ent-001','org_ent_ops', 0,'MARKETING','ALLOCATED','alloc-rule-001',25000::numeric),
        ('p-ent-002','org_ent_ops', 0,'SALARY','DIRECT',NULL,210000::numeric),
        ('p-ent-002','org_ent_ops', 0,'MANAGEMENT','ALLOCATED','alloc-rule-001',45000::numeric),
        ('p-ent-002','org_ent_ops', 0,'MARKETING','ALLOCATED','alloc-rule-001',18000::numeric),
        ('p-ent-003','org_ent_fin', 0,'SALARY','DIRECT',NULL,150000::numeric),
        ('p-ent-003','org_ent_fin', 0,'MANAGEMENT','ALLOCATED','alloc-rule-001',30000::numeric),
        ('p-ent-003','org_ent_fin', 0,'MARKETING','ALLOCATED','alloc-rule-001',12000::numeric),
        (NULL,'org_ent_fin', 1,'MANAGEMENT','ALLOCATED','alloc-rule-002',80000::numeric)),
    span(fact_type, period) AS (VALUES
        ('ACTUAL','2026-01'),('ACTUAL','2026-02'),('ACTUAL','2026-03'),('ACTUAL','2026-04'),
        ('ACTUAL','2026-05'),('ACTUAL','2026-06'),('ACTUAL','2026-07'),('ACTUAL','2026-08'),
        ('ACTUAL','2026-09'),('ACTUAL','2026-10'),
        ('PLAN','2026-07'),('PLAN','2026-08'),('PLAN','2026-09'),('PLAN','2026-10'),
        ('PLAN','2026-11'),('PLAN','2026-12')),
    expected AS (
        SELECT s.project_id, s.department_id, s.is_pool, s.cost_category, s.direct_or_allocated,
               s.allocation_rule_ref, sp.fact_type, sp.period, s.monthly_amount AS amount
        FROM series s CROSS JOIN span sp),
    actual AS (
        SELECT c.project_id, c.department_id, c.is_pool, c.cost_category, c.direct_or_allocated,
               c.allocation_rule_ref, c.fact_type, c.period, c.amount
        FROM ecos_dw.ecos_biz_cost_fact c
        WHERE c.batch_id = 'SEED-ENTMGMT-V1' AND c.is_deleted = 0)
    SELECT count(*) FILTER (WHERE a.department_id IS NULL) AS missing,
           count(*) FILTER (WHERE e.department_id IS NULL) AS extra,
           count(*) FILTER (WHERE e.department_id IS NOT NULL AND a.department_id IS NOT NULL
                                  AND e.amount <> a.amount) AS val_mismatch
    FROM expected e
    FULL OUTER JOIN actual a
         ON  coalesce(e.project_id, '~POOL~') = coalesce(a.project_id, '~POOL~')
         AND e.department_id = a.department_id AND e.is_pool = a.is_pool
         AND e.cost_category = a.cost_category AND e.direct_or_allocated = a.direct_or_allocated
         AND coalesce(e.allocation_rule_ref, '') = coalesce(a.allocation_rule_ref, '')
         AND e.fact_type = a.fact_type AND e.period = a.period
) q;

-- ── B3 资源事实三向 diff + work_hours = fte × 168 的独立关系校验
--     诚实边界：staff_ref_hash 的比对是"复算 seed 声明的派生式"，非独立来源（D-06 已登记合成假名）；
--     真正独立可核的是 fte/work_hours/hourly_rate/期间跨度 与 work_hours=fte×168 这条关系。
INSERT INTO chk_result(name, viol)
SELECT 'B3 资源事实行级三向 diff + work_hours=fte×168',
       q.missing + q.extra + q.val_mismatch + q.relation_bad
FROM (
    WITH staff(project_id, department_id, staff_key, fte, work_hours, hourly_rate) AS (VALUES
        ('p-ent-001','org_ent_ops','ent-staff-01',1.00::numeric,168.0::numeric,120.00::numeric),
        ('p-ent-001','org_ent_ops','ent-staff-02',0.60,100.8,120.00),
        ('p-ent-002','org_ent_ops','ent-staff-03',0.80,134.4,110.00),
        ('p-ent-002','org_ent_ops','ent-staff-04',0.50, 84.0,110.00),
        ('p-ent-003','org_ent_fin','ent-staff-05',0.60,100.8, 95.00),
        ('p-ent-003','org_ent_fin','ent-staff-06',0.40, 67.2, 95.00)),
    span(fact_type, period) AS (VALUES
        ('ACTUAL','2026-01'),('ACTUAL','2026-02'),('ACTUAL','2026-03'),('ACTUAL','2026-04'),
        ('ACTUAL','2026-05'),('ACTUAL','2026-06'),('ACTUAL','2026-07'),('ACTUAL','2026-08'),
        ('ACTUAL','2026-09'),('ACTUAL','2026-10'),
        ('PLAN','2026-07'),('PLAN','2026-08'),('PLAN','2026-09'),('PLAN','2026-10'),
        ('PLAN','2026-11'),('PLAN','2026-12')),
    expected AS (
        SELECT t.project_id, t.department_id, sp.fact_type, sp.period,
               md5('seed:entmgmt-v1:staff:' || t.staff_key)
            || md5('seed:entmgmt-v1:staff2:' || t.staff_key) AS staff_ref_hash,
               t.fte, t.work_hours, t.hourly_rate
        FROM staff t CROSS JOIN span sp),
    actual AS (
        SELECT r.project_id, r.department_id, r.fact_type, r.period,
               r.staff_ref_hash, r.fte, r.work_hours, r.hourly_rate
        FROM ecos_dw.ecos_biz_resource_fact r
        WHERE r.batch_id = 'SEED-ENTMGMT-V1' AND r.is_deleted = 0)
    SELECT count(*) FILTER (WHERE a.department_id IS NULL) AS missing,
           count(*) FILTER (WHERE e.department_id IS NULL) AS extra,
           count(*) FILTER (WHERE e.department_id IS NOT NULL AND a.department_id IS NOT NULL
                                  AND (e.fte <> a.fte OR e.work_hours <> a.work_hours
                                    OR e.hourly_rate <> a.hourly_rate
                                    OR e.staff_ref_hash <> a.staff_ref_hash)) AS val_mismatch,
           count(*) FILTER (WHERE a.department_id IS NOT NULL
                                  AND a.work_hours <> round(a.fte * 168, 1)) AS relation_bad
    FROM expected e
    FULL OUTER JOIN actual a
         ON  e.project_id = a.project_id AND e.department_id = a.department_id
         AND e.fact_type = a.fact_type AND e.period = a.period AND e.staff_ref_hash = a.staff_ref_hash
) q;

-- ── C 设计册 18 枚锚点：手算字面值（§4.2 / §4.3 对账表）vs 库内聚合
INSERT INTO chk_result(name, viol)
SELECT 'C 设计册 18 枚数值锚点对撞', count(*) FROM (
    VALUES
        ('acc_act_p1',    10524000.00::numeric), ('acc_act_p2',   7016000.00),
        ('acc_act_p3',     4385000.00),          ('acc_act_all', 21925000.00),
        ('acc_plan_p1',    6612000.00),          ('acc_plan_all',13775000.00),
        ('col_act_all',   20000000.00),          ('col_plan_all',13500000.00),
        ('sub_act_all',    9256800.00),          ('sub_plan_all', 6201600.00),
        ('cost_act_all',   9500000.00),          ('cost_plan_all',5700000.00),
        ('rate_acc_act',         8.77::numeric), ('rate_acc_plan',      5.51),
        ('rate_col_act',         8.00),          ('rate_col_plan',      5.40),
        ('rate_sub_act',         6.09),          ('rate_sub_plan',      4.08)
) AS w(anchor, want_v)
LEFT JOIN (
    SELECT 'acc_act_p1' k, sum(amount) v FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL' AND project_id='p-ent-001'
    UNION ALL SELECT 'acc_act_p2', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL' AND project_id='p-ent-002'
    UNION ALL SELECT 'acc_act_p3', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL' AND project_id='p-ent-003'
    UNION ALL SELECT 'acc_act_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL'
    UNION ALL SELECT 'acc_plan_p1', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='PLAN' AND project_id='p-ent-001'
    UNION ALL SELECT 'acc_plan_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='PLAN'
    UNION ALL SELECT 'col_act_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='COLLECTION' AND fact_type='ACTUAL'
    UNION ALL SELECT 'col_plan_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='COLLECTION' AND fact_type='PLAN'
    UNION ALL SELECT 'sub_act_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='SUBCONTRACT' AND fact_type='ACTUAL'
    UNION ALL SELECT 'sub_plan_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='SUBCONTRACT' AND fact_type='PLAN'
    UNION ALL SELECT 'cost_act_all', sum(amount) FROM ecos_dw.ecos_biz_cost_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND fact_type='ACTUAL'
    UNION ALL SELECT 'cost_plan_all', sum(amount) FROM ecos_dw.ecos_biz_cost_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND fact_type='PLAN'
    UNION ALL SELECT 'rate_acc_act', sum(rate) FROM (
        SELECT DISTINCT stage, fact_type, period, realization_rate AS rate FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d WHERE d.stage='ACCEPTANCE' AND d.fact_type='ACTUAL'
    UNION ALL SELECT 'rate_acc_plan', sum(rate) FROM (
        SELECT DISTINCT stage, fact_type, period, realization_rate AS rate FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d WHERE d.stage='ACCEPTANCE' AND d.fact_type='PLAN'
    UNION ALL SELECT 'rate_col_act', sum(rate) FROM (
        SELECT DISTINCT stage, fact_type, period, realization_rate AS rate FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d WHERE d.stage='COLLECTION' AND d.fact_type='ACTUAL'
    UNION ALL SELECT 'rate_col_plan', sum(rate) FROM (
        SELECT DISTINCT stage, fact_type, period, realization_rate AS rate FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d WHERE d.stage='COLLECTION' AND d.fact_type='PLAN'
    UNION ALL SELECT 'rate_sub_act', sum(rate) FROM (
        SELECT DISTINCT stage, fact_type, period, realization_rate AS rate FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d WHERE d.stage='SUBCONTRACT' AND d.fact_type='ACTUAL'
    UNION ALL SELECT 'rate_sub_plan', sum(rate) FROM (
        SELECT DISTINCT stage, fact_type, period, realization_rate AS rate FROM ecos_dw.ecos_biz_stage_fact
         WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d WHERE d.stage='SUBCONTRACT' AND d.fact_type='PLAN'
) got ON got.k = w.anchor
WHERE got.v IS DISTINCT FROM w.want_v;

-- ── D 主数据完备性四规则（1:1 复现 MasterDataCompletenessChecker + Mapper.xml:10-50）
--     检验集谓词 = is_deleted=0 AND dq_status<>'REJECTED'（Mapper.selectCompletenessDetails）
INSERT INTO chk_result(name, viol)
SELECT 'D1 MD-EMPTY 检验集非空（禁静默 pass=true）', count(*) FROM (
    SELECT count(*) AS n FROM ecos_dw.ecos_biz_stage_fact
     WHERE is_deleted = 0 AND dq_status <> 'REJECTED') q WHERE q.n = 0;

INSERT INTO chk_result(name, viol)
SELECT 'D1b 检验集规模 ≤ 扫描上限 5000（结论必须覆盖全表）', count(*) FROM (
    SELECT count(*) AS n FROM ecos_dw.ecos_biz_stage_fact
     WHERE is_deleted = 0 AND dq_status <> 'REJECTED') q WHERE q.n > 5000;

INSERT INTO chk_result(name, viol)
SELECT 'D2 MD-C1 合同基数缺失 = 0 笔', count(*) FROM ecos_dw.ecos_biz_stage_fact s
WHERE s.is_deleted = 0 AND s.dq_status <> 'REJECTED' AND s.contract_base IS NULL;

INSERT INTO chk_result(name, viol)
SELECT 'D3 MD-C2 归属缺失 = 0 笔（覆盖窗 effective_from≤period≤effective_to，NULL=开区间）', count(*)
FROM ecos_dw.ecos_biz_stage_fact s
WHERE s.is_deleted = 0 AND s.dq_status <> 'REJECTED'
  AND NOT EXISTS (
        SELECT 1 FROM ecos_dw.ecos_biz_project_attribution a
         WHERE a.is_deleted = 0 AND a.is_active = 1
           AND a.project_id = s.project_id
           AND a.effective_from IS NOT NULL AND a.effective_from <= s.period
           AND (a.effective_to IS NULL OR a.effective_to >= s.period));

INSERT INTO chk_result(name, viol)
SELECT 'D4 MD-C3 归属超配 = 0 组（判定 compareTo(ONE)>0 ⇒ 恰为 1 合法，压线可验）', count(*) FROM (
    SELECT a.project_id, s.period, sum(a.attribution_ratio) AS ratio_sum
    FROM (SELECT DISTINCT project_id, period FROM ecos_dw.ecos_biz_stage_fact
           WHERE is_deleted = 0 AND dq_status <> 'REJECTED') s
    JOIN ecos_dw.ecos_biz_project_attribution a
      ON  a.project_id = s.project_id AND a.is_deleted = 0 AND a.is_active = 1
      AND a.effective_from IS NOT NULL AND a.effective_from <= s.period
      AND (a.effective_to IS NULL OR a.effective_to >= s.period)
    GROUP BY a.project_id, s.period
) q WHERE q.ratio_sum > 1;

INSERT INTO chk_result(name, viol)
SELECT 'D4b 三项目 Σratio 恰为 1.0000（全归属覆盖，无缺口无超配）', count(*) FROM (
    SELECT project_id, sum(attribution_ratio) AS ratio_sum
    FROM ecos_dw.ecos_biz_project_attribution
    WHERE batch_id = 'SEED-ENTMGMT-V1' AND is_deleted = 0 AND is_active = 1
    GROUP BY project_id) q WHERE q.ratio_sum <> 1.0000;

INSERT INTO chk_result(name, viol)
SELECT 'D5 REJECTED 行数 = 0（blockedCount，fc_dq_block_trend 数据源）', count(*)
FROM ecos_dw.ecos_biz_stage_fact WHERE is_deleted = 0 AND dq_status = 'REJECTED';

-- ── E DQ 九条门禁形态（复现 DqRowGateImpl.java:60-360；直连 SQL 不经 REST 门禁，本项证明"seed 形状 = 门禁放行形状"）
INSERT INTO chk_result(name, viol)
SELECT 'E-F01 批内业务键唯一（四表，键定义同 businessKey():308-323）',
       q.d1 + q.d2 + q.d3 + q.d4 FROM (
    SELECT
      (SELECT count(*) FROM (
         SELECT project_id, contract_id, department_id, attribution_type, count(*) c
           FROM ecos_dw.ecos_biz_project_attribution WHERE batch_id='SEED-ENTMGMT-V1'
          GROUP BY 1,2,3,4 HAVING count(*) > 1) x) AS d1,
      (SELECT count(*) FROM (
         SELECT project_id, department_id, period, stage, fact_type, count(*) c
           FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1'
          GROUP BY 1,2,3,4,5 HAVING count(*) > 1) y) AS d2,
      (SELECT count(*) FROM (
         SELECT coalesce(project_id,'~'), department_id, period, staff_ref_hash, fact_type, count(*) c
           FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1'
          GROUP BY 1,2,3,4,5 HAVING count(*) > 1) z) AS d3,
      (SELECT count(*) FROM (
         SELECT coalesce(project_id,'~'), department_id, period, cost_category,
                direct_or_allocated, fact_type, count(*) c
           FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1'
          GROUP BY 1,2,3,4,5,6 HAVING count(*) > 1) w) AS d4
) q;

INSERT INTO chk_result(name, viol)
SELECT 'E-F02 比率 ∈ [0,1]（attribution_ratio/realization_rate）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND (attribution_ratio < 0 OR attribution_ratio > 1
        OR realization_rate   < 0 OR realization_rate   > 1)
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_project_attribution
     WHERE batch_id='SEED-ENTMGMT-V1' AND (attribution_ratio < 0 OR attribution_ratio > 1)) q;

INSERT INTO chk_result(name, viol)
SELECT 'E-F03 金额非负 + amount 与三因子精确相等（列精度另见 E-P 项）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND (amount < 0 OR contract_base < 0)
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND amount <> round(contract_base * attribution_ratio * realization_rate, 2)
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1' AND amount < 0
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_resource_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND (hourly_rate < 0 OR fte < 0 OR work_hours < 0)) q;

-- 精度表 12.3.7 的列形态权威核对（information_schema 实跑，非引用 DDL 文本）
INSERT INTO chk_result(name, viol)
SELECT 'E-P 列精度符合第 12 篇 12.3.7（金额 18/2、比率 5/4、分位数 18/4）', count(*) FROM (
    VALUES
      ('ecos_dw','ecos_biz_stage_fact','amount','18','2'),
      ('ecos_dw','ecos_biz_stage_fact','contract_base','18','2'),
      ('ecos_dw','ecos_biz_stage_fact','attribution_ratio','5','4'),
      ('ecos_dw','ecos_biz_stage_fact','realization_rate','5','4'),
      ('ecos_dw','ecos_biz_cost_fact','amount','18','2'),
      ('ecos_dw','ecos_biz_resource_fact','hourly_rate','18','2'),
      ('ecos_dw','ecos_biz_resource_fact','fte','5','2'),
      ('ecos_dw','ecos_biz_project_attribution','attribution_ratio','5','4'),
      ('ecos_knowledge','ecos_kb_profile','p10','18','4'),
      ('ecos_knowledge','ecos_kb_profile','p50','18','4'),
      ('ecos_knowledge','ecos_kb_profile','p90','18','4'),
      ('ecos_knowledge','ecos_kb_profile','mean_value','18','4'),
      ('ecos_knowledge','ecos_kb_profile','ci_low','18','4'),
      ('ecos_knowledge','ecos_kb_profile','ci_high','18','4'),
      ('ecos_knowledge','ecos_kb_profile','missing_rate','5','4')
) AS e(ns, tbl, col, want_p, want_s)
LEFT JOIN information_schema.columns c
       ON c.table_schema = e.ns AND c.table_name = e.tbl AND c.column_name = e.col
WHERE c.column_name IS NULL
   OR coalesce(c.numeric_precision::text,'-') <> e.want_p
   OR coalesce(c.numeric_scale::text,'-')      <> e.want_s;

INSERT INTO chk_result(name, viol)
SELECT 'E-F04 period 形态 YYYY-MM（PERIOD_PAT :71）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact   WHERE batch_id='SEED-ENTMGMT-V1' AND period !~ '^\d{4}-(0[1-9]|1[0-2])$'
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_cost_fact     WHERE batch_id='SEED-ENTMGMT-V1' AND period !~ '^\d{4}-(0[1-9]|1[0-2])$'
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1' AND period !~ '^\d{4}-(0[1-9]|1[0-2])$'
    UNION ALL SELECT 1 FROM ecos_data.ecos_data_period_status WHERE domain='entmgmt'
          AND period !~ '^\d{4}-(0[1-9]|1[0-2])$') q;

INSERT INTO chk_result(name, viol)
SELECT 'E-F06 同 (project, contract) Σratio ≤ 1（归因表批内，:272-299）', count(*) FROM (
    SELECT project_id, contract_id, sum(attribution_ratio) s
      FROM ecos_dw.ecos_biz_project_attribution WHERE batch_id='SEED-ENTMGMT-V1'
     GROUP BY 1,2 HAVING sum(attribution_ratio) > 1) q;

INSERT INTO chk_result(name, viol)
SELECT 'E-F07 模板必填列非空（REQUIRED_FIELDS :350-361 按 FactType 分组）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1'
      AND (project_id IS NULL OR department_id IS NULL OR period IS NULL OR stage IS NULL
        OR fact_type IS NULL OR contract_base IS NULL OR attribution_ratio IS NULL OR currency IS NULL)
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1'
      AND (project_id IS NULL OR department_id IS NULL OR period IS NULL
        OR cost_category IS NULL OR fact_type IS NULL)
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1'
      AND (department_id IS NULL OR period IS NULL OR cost_category IS NULL
        OR direct_or_allocated IS NULL OR amount IS NULL OR currency IS NULL)
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_project_attribution WHERE batch_id='SEED-ENTMGMT-V1'
      AND (project_id IS NULL OR attribution_ratio IS NULL)) q;

INSERT INTO chk_result(name, viol)
SELECT 'E-F08 ALLOCATED 成本必带 allocation_rule_ref（:224-235）', count(*)
FROM ecos_dw.ecos_biz_cost_fact
WHERE batch_id='SEED-ENTMGMT-V1' AND upper(direct_or_allocated)='ALLOCATED'
  AND (allocation_rule_ref IS NULL OR btrim(allocation_rule_ref)='');

INSERT INTO chk_result(name, viol)
SELECT 'E-F09 COST 全量 / STAGE·RESOURCE 的 ACTUAL 行必带 evidence_ref 或 source_ref（:237-251）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1'
      AND (coalesce(btrim(evidence_ref),'')='' AND coalesce(btrim(source_ref),'')='')
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND upper(fact_type)='ACTUAL'
      AND (coalesce(btrim(evidence_ref),'')='' AND coalesce(btrim(source_ref),'')='')
    UNION ALL
    SELECT 1 FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1' AND upper(fact_type)='ACTUAL'
      AND (coalesce(btrim(evidence_ref),'')='' AND coalesce(btrim(source_ref),'')='')) q;

INSERT INTO chk_result(name, viol)
SELECT 'E-F11 ACTUAL 期间不得晚于当前月（:201-222，脚本运行日动态判定）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact   WHERE batch_id='SEED-ENTMGMT-V1' AND upper(fact_type)='ACTUAL'
      AND period > to_char(now(),'YYYY-MM')
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1' AND upper(fact_type)='ACTUAL'
      AND period > to_char(now(),'YYYY-MM')
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1' AND upper(fact_type)='ACTUAL'
      AND period > to_char(now(),'YYYY-MM')) q;

-- ── F 三级平衡（第 12 篇 12.3.3，判等一律 = 精确相等，等价于 BigDecimal compareTo==0）
INSERT INTO chk_result(name, viol)
SELECT 'F1 明细层=项目期层：Σ部门 amount = round(base × Σratio × rate, 2)（ACCEPTANCE/COLLECTION 全归属口径）', count(*)
FROM (
    SELECT s.stage, s.project_id, s.period, s.fact_type,
           max(s.contract_base) AS base, max(s.realization_rate) AS rate, sum(s.amount) AS detail_sum
      FROM ecos_dw.ecos_biz_stage_fact s
     WHERE s.batch_id='SEED-ENTMGMT-V1' AND s.is_deleted=0 AND s.stage IN ('ACCEPTANCE','COLLECTION')
     GROUP BY 1,2,3,4
) d
JOIN (SELECT project_id, sum(attribution_ratio) AS ratio_sum
        FROM ecos_dw.ecos_biz_project_attribution
       WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND is_active=1
       GROUP BY project_id) r ON r.project_id = d.project_id
WHERE d.detail_sum <> round(d.base * r.ratio_sum * d.rate, 2);

INSERT INTO chk_result(name, viol)
SELECT 'F2 项目期层=场景年度层：Σ明细 = Σ(项目期合计)，且 Σ项目期合计 = 设计册锚点（同 C 项）', count(*) FROM (
    SELECT y.stage, y.fact_type, y.annual, p.annual_by_period
    FROM (SELECT stage, fact_type, sum(amount) AS annual
            FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0
           GROUP BY 1,2) y
    JOIN (SELECT stage, fact_type, sum(sub) AS annual_by_period FROM (
            SELECT stage, fact_type, project_id, period, round(sum(amount),2) AS sub
              FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0
             GROUP BY 1,2,3,4) t GROUP BY 1,2) p
      ON p.stage = y.stage AND p.fact_type = y.fact_type
    WHERE y.annual <> p.annual_by_period
) q;

INSERT INTO chk_result(name, viol)
SELECT 'F3 成本层无聚合漂移：Σ明细 = Σ(项目/类别/期 取整合计)', count(*) FROM (
    SELECT sum(amount) a FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0
) x
JOIN (SELECT sum(sub) a FROM (
        SELECT coalesce(project_id,'~POOL~') pid, department_id, cost_category, fact_type, period,
               round(sum(amount),2) AS sub
          FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0
         GROUP BY 1,2,3,4,5) t) y ON TRUE
WHERE x.a <> y.a;

-- ── G 口径面（CaliberDao.getByCaliberCodeAndCurrentVersion :73-89 生效读法 + CaliberDslParser 文法可 SQL 化部分）
INSERT INTO chk_result(name, viol)
SELECT 'G1 生效读法：四枚口径均为 status=ACTIVE × 恰 1 条 APPROVED 版本', count(*) FROM (
    SELECT cc.id, (SELECT count(*) FROM ecos_ontology.ecos_caliber_version cv
                    WHERE cv.caliber_id = cc.id AND cv.status='APPROVED' AND cv.is_deleted=0) AS approved_n
      FROM ecos_ontology.ecos_caliber cc
     WHERE cc.domain='entmgmt' AND cc.is_deleted=0
) q WHERE q.approved_n <> 1
   OR NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_caliber cc2 WHERE cc2.id = q.id AND cc2.status='ACTIVE');

INSERT INTO chk_result(name, viol)
SELECT 'G1b 四枚口径码齐备且状态 ACTIVE×APPROVED 联查命中 4 行', count(*) FROM (
    SELECT count(*) AS n
      FROM ecos_ontology.ecos_caliber c
      JOIN ecos_ontology.ecos_caliber_version cv ON cv.caliber_id = c.id AND cv.status='APPROVED'
     WHERE c.domain='entmgmt' AND c.status='ACTIVE' AND c.is_deleted=0
       AND c.code IN ('accrual_revenue_amount','cash_collection_amount','operating_profit_amount','ent_realization_ratio')
) q WHERE q.n <> 4;

INSERT INTO chk_result(name, viol)
SELECT 'G2 公式形态：赋值式 左端IDENT=code、无比较运算符、无非文法字符（V1 可 SQL 化子集）', count(*)
FROM ecos_ontology.ecos_caliber_version cv
JOIN ecos_ontology.ecos_caliber cc ON cc.id = cv.caliber_id
WHERE cv.domain='entmgmt' AND cv.is_deleted=0
  AND (cc.code IS NULL
       OR cv.formula IS NULL
       OR cv.formula !~ ('^' || cc.code || ' = ')
       OR cv.formula ~ '[<>]'
       OR cv.formula ~ '=='  OR cv.formula ~ '!='
       OR cv.formula ~ '&&'  OR cv.formula ~ '\|\|'
       OR cv.formula ~ '[^A-Za-z0-9_ =()+*/\[\],-]');

INSERT INTO chk_result(name, viol)
SELECT 'G2b ROUND 元数 1~2 且形态合规（FUNC 白名单 SUM/ROUND/MIN/MAX/IF）', count(*) FROM (
    SELECT cv.id,
           (length(cv.formula) - length(replace(cv.formula, 'ROUND(', ''))) / 6 AS round_calls,
           (SELECT count(*) FROM regexp_matches(cv.formula, 'ROUND\([^(),]+, ?[0-9]+\)', 'g')) AS round_ok
      FROM ecos_ontology.ecos_caliber_version cv WHERE cv.domain='entmgmt' AND cv.is_deleted=0
) q WHERE q.round_calls <> q.round_ok;

INSERT INTO chk_result(name, viol)
SELECT 'G3 V2 引用关：公式标识符 ∈ 事实字段登记册 ∪ 限定符 ∪ 已注册指标码（CaliberFactFieldRegistry）', count(*) FROM (
    WITH allowed(tok) AS (VALUES
        ('contract_base'),('amount'),('cost_amount'),('attribution_ratio'),('realization_rate'),
        ('collection_rate'),('hourly_rate'),('fte'),('head_count'),('work_hours'),('months'),
        ('ACCEPTANCE'),('COLLECTION'),('SUBCONTRACT'),('PLAN'),('ACTUAL')),
    met(code) AS (SELECT m.code FROM ecos_ontology.ecos_metric_definition m WHERE m.is_deleted = 0),
    toks AS (
        -- regexp_matches 返回 text[]（非 text），单捕获组须显式取 [1]
        SELECT cv.id AS vid, (t.m)[1] AS token
          FROM ecos_ontology.ecos_caliber_version cv
         CROSS JOIN LATERAL regexp_matches(
                regexp_replace(cv.formula, '[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(', '(', 'g'),
                '[A-Za-z_][A-Za-z0-9_]*', 'g') AS t(m)
         WHERE cv.domain='entmgmt' AND cv.is_deleted=0)
    SELECT 1 FROM toks k
     WHERE NOT EXISTS (SELECT 1 FROM allowed a WHERE a.tok = k.token)
       AND NOT EXISTS (SELECT 1 FROM met m WHERE m.code = k.token)
) q;

INSERT INTO chk_result(name, viol)
SELECT 'G4 V4 判环：指标依赖图无环（递归闭包自可达 = 0）', count(*) FROM (
    WITH RECURSIVE edges AS (
        SELECT m.code AS src, (t.tm)[1] AS dst
          FROM ecos_ontology.ecos_metric_definition m
         CROSS JOIN LATERAL regexp_matches(
                regexp_replace(m.expression, '[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(', '(', 'g'),
                '[A-Za-z_][A-Za-z0-9_]*', 'g') AS t(tm)
         WHERE m.domain='entmgmt' AND m.is_deleted=0
           AND (t.tm)[1] <> m.code
           AND (t.tm)[1] IN (SELECT code FROM ecos_ontology.ecos_metric_definition WHERE is_deleted=0)),
    reach AS (
        SELECT src, dst, 1 AS depth FROM edges
        UNION ALL
        SELECT r.src, e.dst, r.depth + 1 FROM reach r JOIN edges e ON e.src = r.dst AND r.depth < 5)
    SELECT src, dst FROM reach WHERE src = dst
) q;

INSERT INTO chk_result(name, viol)
SELECT 'G4b 依赖边恰为 2 条（operating_profit→accrual、ent_realization_ratio→accrual，设计册 §5.3）', count(*) FROM (
    WITH actual AS (
        SELECT DISTINCT m.code AS src, (t.tm)[1] AS dst
          FROM ecos_ontology.ecos_metric_definition m
         CROSS JOIN LATERAL regexp_matches(
                regexp_replace(m.expression, '[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(', '(', 'g'),
                '[A-Za-z_][A-Za-z0-9_]*', 'g') AS t(tm)
         WHERE m.domain='entmgmt' AND m.is_deleted=0 AND (t.tm)[1] <> m.code
           AND (t.tm)[1] IN (SELECT code FROM ecos_ontology.ecos_metric_definition WHERE is_deleted=0)
    ), expected(src, dst) AS (
        VALUES ('operating_profit_amount', 'accrual_revenue_amount'),
               ('ent_realization_ratio',   'accrual_revenue_amount')
    )
    -- 双向差集 = 违规：少一条边或多一条边都计 1
    SELECT 1 FROM expected e FULL OUTER JOIN actual a ON a.src = e.src AND a.dst = e.dst
     WHERE a.src IS NULL OR e.src IS NULL
) q;

-- ── H 指标注册表面（发布门禁 rule 4 = CaliberService.assertMetricCaliberApprovedForPublish :252-281）
INSERT INTO chk_result(name, viol)
SELECT 'H1 指标四枚：expression 与 caliber_version.formula 逐字一致（写法规则 R-公式-1）', count(*)
FROM ecos_ontology.ecos_metric_definition m
LEFT JOIN ecos_ontology.ecos_caliber cc ON cc.id = m.caliber_id
LEFT JOIN ecos_ontology.ecos_caliber_version cv
       ON cv.caliber_id = cc.id AND cv.status = 'APPROVED' AND cv.is_deleted = 0
WHERE m.domain='entmgmt' AND m.is_deleted=0
  AND (m.code IS DISTINCT FROM cc.code
    OR m.expression IS DISTINCT FROM cv.formula
    OR cc.status <> 'ACTIVE'
    OR cv.id IS NULL);

INSERT INTO chk_result(name, viol)
SELECT 'H2 快照冻结：caliber_snapshot 四键齐备（caliber_id/formula/version_no/frozen_at）且 formula 与实值相等', count(*) FROM (
    -- 先把范围钳到本数据集 4 行，再在外层做 jsonb 判定：
    -- 1) 避免 AND/OR 优先级把别域指标卷进来；2) 别域快照非 JSON 文本时 ::jsonb 不会炸本判定
    SELECT m.caliber_snapshot, m.caliber_id, m.expression
      FROM ecos_ontology.ecos_metric_definition m
     WHERE m.domain='entmgmt' AND m.is_deleted=0
) q WHERE q.caliber_snapshot IS NULL
    OR  NOT (q.caliber_snapshot::jsonb ?& ARRAY['caliber_id','formula','version_no','frozen_at'])
    OR  (q.caliber_snapshot::jsonb ->> 'formula') <> q.expression
    OR  (q.caliber_snapshot::jsonb ->> 'caliber_id') <> q.caliber_id
    OR  (q.caliber_snapshot::jsonb ->> 'version_no') <> '1';

INSERT INTO chk_result(name, viol)
SELECT 'H3 发布面合规：PUBLISHED + caliber_id 非空 + formula_version=1 + aggregation 与单位同形（RATE⇒RATIO）', count(*) FROM (
    SELECT m.id, m.status, m.caliber_id, m.formula_version, m.aggregation, cc.unit
      FROM ecos_ontology.ecos_metric_definition m
      LEFT JOIN ecos_ontology.ecos_caliber cc ON cc.id = m.caliber_id
     WHERE m.domain='entmgmt' AND m.is_deleted=0
) q WHERE q.status <> 'PUBLISHED'
     OR q.caliber_id IS NULL
     OR q.formula_version <> '1'
     OR q.aggregation <> CASE WHEN q.unit = 'RATE' THEN 'RATIO' ELSE 'SUM' END;

INSERT INTO chk_result(name, viol)
SELECT 'H4 entity_code 如实 NULL（D 项：无 ecos_entity_table_mapping 行指向本数据集实体 ⇒ 禁悬空引用）', count(*)
FROM ecos_ontology.ecos_metric_definition m
WHERE m.domain='entmgmt' AND m.is_deleted=0
  AND m.entity_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_entity_table_mapping t WHERE t.entity_code = m.entity_code);

-- ── I 画像独立重算（分位数必须由事实现算，禁手填统计数字）
INSERT INTO chk_result(name, viol)
SELECT 'I1 画像四枚与事实现算完全相等（sample_count/mean/p10/p50/p90/ci_low/ci_high）', count(*) FROM (
    WITH monthly_base AS (
        SELECT s.project_id, s.period,
               sum(CASE WHEN s.stage='ACCEPTANCE' THEN s.amount ELSE 0::numeric END) AS accrual_revenue,
               max(s.contract_base) AS contract_base
          FROM ecos_dw.ecos_biz_stage_fact s
         WHERE s.batch_id='SEED-ENTMGMT-V1' AND s.fact_type='ACTUAL' AND s.is_deleted=0
         GROUP BY 1,2),
    monthly_cost AS (
        SELECT c.project_id, c.period, sum(c.amount) AS cost_amount
          FROM ecos_dw.ecos_biz_cost_fact c
         WHERE c.batch_id='SEED-ENTMGMT-V1' AND c.fact_type='ACTUAL' AND c.is_deleted=0
           AND c.project_id IS NOT NULL
         GROUP BY 1,2),
    series(metric_code, value) AS (
        SELECT 'accrual_revenue_amount', s.amount FROM ecos_dw.ecos_biz_stage_fact s
         WHERE s.batch_id='SEED-ENTMGMT-V1' AND s.stage='ACCEPTANCE' AND s.fact_type='ACTUAL' AND s.is_deleted=0
        UNION ALL
        SELECT 'cash_collection_amount', s.amount FROM ecos_dw.ecos_biz_stage_fact s
         WHERE s.batch_id='SEED-ENTMGMT-V1' AND s.stage='COLLECTION' AND s.fact_type='ACTUAL' AND s.is_deleted=0
        UNION ALL
        SELECT 'operating_profit_amount', b.accrual_revenue - coalesce(k.cost_amount, 0::numeric)
          FROM monthly_base b LEFT JOIN monthly_cost k ON k.project_id=b.project_id AND k.period=b.period
        UNION ALL
        SELECT 'ent_realization_ratio', round(b.accrual_revenue / b.contract_base, 4) FROM monthly_base b),
    agg AS (
        SELECT metric_code,
               count(*)::int AS n,
               round(avg(value)::numeric, 4) AS mean_v,
               round(percentile_cont(0.10) WITHIN GROUP (ORDER BY value)::numeric, 4) AS p10,
               round(percentile_cont(0.50) WITHIN GROUP (ORDER BY value)::numeric, 4) AS p50,
               round(percentile_cont(0.90) WITHIN GROUP (ORDER BY value)::numeric, 4) AS p90
          FROM series GROUP BY metric_code)
    SELECT p.metric_code, g.n, p.sample_count, p.p10, g.p10, p.p50, g.p50, p.p90, g.p90
      FROM ecos_knowledge.ecos_kb_profile p
      JOIN agg g ON g.metric_code = p.metric_code
     WHERE p.source_query_ref='seed:entmgmt-v1' AND p.is_deleted=0
       AND (p.sample_count <> g.n OR p.p10 <> g.p10 OR p.p50 <> g.p50 OR p.p90 <> g.p90
         OR p.mean_value <> g.mean_v OR p.ci_low <> g.p10 OR p.ci_high <> g.p90)
) q;

INSERT INTO chk_result(name, viol)
SELECT 'I2 BUILD_INTERVAL 单调性 p10 ≤ p50 ≤ p90（第 12 篇 12.3.4）', count(*)
FROM ecos_knowledge.ecos_kb_profile
WHERE source_query_ref='seed:entmgmt-v1' AND is_deleted=0
  AND NOT (p10 <= p50 AND p50 <= p90);

INSERT INTO chk_result(name, viol)
SELECT 'I3 画像形态列：status=PUBLISHED（实跑 ck_kbp_status 三值，非设计册原文 ACTIVE）/ window / ci_level / stats_method / group_dims', count(*)
FROM ecos_knowledge.ecos_kb_profile
WHERE source_query_ref='seed:entmgmt-v1' AND is_deleted=0
  AND (status <> 'PUBLISHED' OR window_from <> '2026-01' OR window_to <> '2026-10'
    OR ci_level <> '95' OR stats_method <> 'T_APPROX' OR profile_version <> '1'
    OR group_dims_json <> '{"department":"ALL"}' OR missing_rate <> 0.0000
    OR confidence <> CASE WHEN sample_count >= 30 THEN 'MEDIUM' ELSE 'LOW' END);

INSERT INTO chk_result(name, viol)
SELECT 'I4 画像 metric_code 全部指向已注册 PUBLISHED 指标（不留悬空指标引用）', count(*)
FROM ecos_knowledge.ecos_kb_profile p
WHERE p.source_query_ref='seed:entmgmt-v1' AND p.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_metric_definition m
                   WHERE m.code = p.metric_code AND m.status='PUBLISHED' AND m.is_deleted=0);

-- ── J 情景假设
INSERT INTO chk_result(name, viol)
SELECT 'J1 假设四枚：枚举命中实跑 CHECK / value_ref 如实 NULL（D-02 值表未在库）/ 指标已注册 / 期间在 FY2026 内', count(*)
FROM ecos_knowledge.ecos_kb_assumption a
WHERE a.domain='entmgmt' AND a.is_deleted=0
  AND (a.status NOT IN ('DRAFT','PENDING_APPROVAL','ACTIVE','EXPIRED','SUPERSEDED')
    OR a.value_semantic NOT IN ('ABSOLUTE','DELTA')
    OR a.value_type NOT IN ('RATE','AMOUNT','DAYS')
    OR a.scenario_type NOT IN ('BASE','CONSERVATIVE','AGGRESSIVE')
    OR a.value_ref IS NOT NULL
    OR coalesce(btrim(a.reason),'')=''
    OR coalesce(btrim(a.evidence_ref),'')=''
    OR a.is_unverified <> 0
    OR a.valid_from < '2026-01' OR a.valid_to > '2026-12'
    OR NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_metric_definition m
                    WHERE m.code = a.metric_code AND m.is_deleted=0));

INSERT INTO chk_result(name, viol)
SELECT 'J2 假设血缘可查：base_assumption_id 非空的行必须指向同指标、BASE、ACTIVE 的真实行', count(*)
FROM ecos_knowledge.ecos_kb_assumption a
JOIN ecos_knowledge.ecos_kb_assumption b ON b.id = a.base_assumption_id
WHERE a.domain='entmgmt' AND a.is_deleted=0
  AND (b.metric_code <> a.metric_code OR b.scenario_type <> 'BASE' OR b.status <> 'ACTIVE' OR b.is_deleted <> 0);

INSERT INTO chk_result(name, viol)
SELECT 'J2b 血缘行数 = 1（CONSERVATIVE 收入一条，其余全 NULL ⇒ 既非摆设也非虚增）', count(*)
FROM (SELECT count(*) AS n FROM ecos_knowledge.ecos_kb_assumption
       WHERE domain='entmgmt' AND is_deleted=0 AND base_assumption_id IS NOT NULL) q WHERE q.n <> 1;

-- ── K 期间状态（PeriodStatusMapper.selectClosedPeriods 谓词复现：status='CLOSED' AND is_deleted=0）
INSERT INTO chk_result(name, viol)
SELECT 'K1 期间 12 行状态逐一命中（≤2026-06 CLOSED 且带 closed_by/at，≥2026-07 OPEN 且不带）', count(*) FROM (
    VALUES ('2026-01','CLOSED'),('2026-02','CLOSED'),('2026-03','CLOSED'),('2026-04','CLOSED'),
           ('2026-05','CLOSED'),('2026-06','CLOSED'),('2026-07','OPEN'),('2026-08','OPEN'),
           ('2026-09','OPEN'),('2026-10','OPEN'),('2026-11','OPEN'),('2026-12','OPEN')
) AS e(period, want_status)
FULL OUTER JOIN ecos_data.ecos_data_period_status p
         ON p.period = e.period AND p.domain='entmgmt' AND p.is_deleted = 0
WHERE e.period IS NULL OR p.period IS NULL
   OR p.status <> e.want_status
   OR (e.want_status='CLOSED' AND (p.closed_by IS NULL OR p.closed_at IS NULL))
   OR (e.want_status='OPEN'   AND (p.closed_by IS NOT NULL OR p.closed_at IS NOT NULL));

INSERT INTO chk_result(name, viol)
SELECT 'K2 ACTUAL 事实只落在 ≤2026-10，PLAN 只落在 07..12（第 12 篇输入面语义）', count(*) FROM (
    SELECT 'stage' t, fact_type, min(period) lo, max(period) hi
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' GROUP BY 1,2
    UNION ALL SELECT 'cost', fact_type, min(period), max(period)
      FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1' GROUP BY 1,2
    UNION ALL SELECT 'resource', fact_type, min(period), max(period)
      FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1' GROUP BY 1,2
) q WHERE (q.fact_type='ACTUAL' AND (q.lo <> '2026-01' OR q.hi <> '2026-10'))
     OR (q.fact_type='PLAN'    AND (q.lo <> '2026-07' OR q.hi <> '2026-12'));

-- ── L 场景装配（六类词表 = BindingType.java:14-19；连边类型 = EdgeType.java:8-12）
INSERT INTO chk_result(name, viol)
SELECT 'L1 场景行：ACTIVE/HIGH、department 为真实组织行、budget 文本形态、metrics 为 JSON 对象且三值等于锚点', count(*)
FROM ecos_ai.ecos_business_scenario s
WHERE s.id='sc_entmgmt' AND s.is_deleted=0
  AND (s.status <> 'ACTIVE' OR s.priority NOT IN ('CRITICAL','HIGH','MEDIUM','LOW')
    OR NOT EXISTS (SELECT 1 FROM ecos_control.td_organization o
                    WHERE o.org_id = s.department AND o.status='ACTIVE')
    OR s.budget <> '24000000.00'
    OR jsonb_typeof(s.metrics) <> 'object'
    OR (s.metrics->>'targetRevenue')::numeric  <> 21925000.00
    OR (s.metrics->>'targetCollection')::numeric <> 20000000.00
    OR (s.metrics->>'actualCost')::numeric    <> 9500000.00);

INSERT INTO chk_result(name, viol)
SELECT 'L2 绑定四类：binding_type ∈ 六值词表 且 target_ref=target_id 双写一致（DAO 新旧列并存）', count(*)
FROM ecos_ai.ecos_scenario_binding b
WHERE b.scenario_id='sc_entmgmt' AND b.is_deleted=0
  AND (b.binding_type NOT IN ('DATASET','OBJECT_TYPE','KNOWLEDGE_BASE','AI_AGENT','SECURITY_POLICY','INTERFACE')
    OR b.binding_type IN ('SECURITY_POLICY','INTERFACE')
    OR coalesce(b.target_ref,'') = ''
    OR b.target_ref IS DISTINCT FROM b.target_id);

INSERT INTO chk_result(name, viol)
SELECT 'L3 零悬空 target_ref：四类绑定各指向真实存在的载体行（铁律：不许造悬空引用）', count(*)
FROM ecos_ai.ecos_scenario_binding b
WHERE b.scenario_id='sc_entmgmt' AND b.is_deleted=0
  AND NOT (
        CASE b.binding_type
          WHEN 'DATASET'        THEN EXISTS (SELECT 1 FROM ecos_data.td_datasource d
                                              WHERE d.datasource_id = b.target_ref)
          WHEN 'OBJECT_TYPE'    THEN EXISTS (SELECT 1 FROM ecos_ontology.ecos_ontology_entity e
                                              WHERE e.id = b.target_ref AND e.is_deleted = 0)
          WHEN 'KNOWLEDGE_BASE' THEN EXISTS (SELECT 1 FROM ecos_knowledge.knowledge_article k
                                              WHERE k.id = b.target_ref AND k.is_deleted = 0)
          WHEN 'AI_AGENT'       THEN EXISTS (SELECT 1 FROM ecos_ai.ecos_agent a
                                              WHERE a.id = b.target_ref)
          ELSE false
        END);

INSERT INTO chk_result(name, viol)
SELECT 'L4 连边三枚：类型 ⊂ EdgeType、两端绑定同场景且存在、链形 DATASET→OBJECT_TYPE→KNOWLEDGE_BASE→AI_AGENT', count(*) FROM (
    SELECT l.id, l.link_type,
           bs.binding_type AS src_type, bt.binding_type AS dst_type
      FROM ecos_control.ecos_scenario_binding_link l
      JOIN ecos_ai.ecos_scenario_binding bs ON bs.id = l.source_binding_id AND bs.is_deleted=0
      JOIN ecos_ai.ecos_scenario_binding bt ON bt.id = l.target_binding_id AND bt.is_deleted=0
     WHERE l.scenario_id='sc_entmgmt' AND l.is_deleted=0
) q
WHERE q.link_type NOT IN ('MAPPING','EXTRACTION','COGNITION','GOVERN','EXPOSE')
   OR (q.link_type='MAPPING'    AND (q.src_type <> 'DATASET'      OR q.dst_type <> 'OBJECT_TYPE'))
   OR (q.link_type='EXTRACTION' AND (q.src_type <> 'OBJECT_TYPE'  OR q.dst_type <> 'KNOWLEDGE_BASE'))
   OR (q.link_type='COGNITION'  AND (q.src_type <> 'KNOWLEDGE_BASE' OR q.dst_type <> 'AI_AGENT'));

INSERT INTO chk_result(name, viol)
SELECT 'L4b 连边数 = 3 且 source_contract 全 NULL（ContractProbeFacade 无主 bean ⇒ 不声明未验证契约，D-12/B-2）', count(*) FROM (
    SELECT count(*) AS n,
           count(*) FILTER (WHERE source_contract IS NULL) AS null_contract
      FROM ecos_control.ecos_scenario_binding_link
     WHERE scenario_id='sc_entmgmt' AND is_deleted=0) q
WHERE q.n <> 3 OR q.null_contract <> 3;

INSERT INTO chk_result(name, viol)
SELECT 'L5 孤岛复现（IslandBindingGuard.findIslands）：四枚绑定全部有入边或出边 ⇒ 孤岛数 = 0', count(*)
FROM ecos_ai.ecos_scenario_binding b
WHERE b.scenario_id='sc_entmgmt' AND b.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM ecos_control.ecos_scenario_binding_link l
                   WHERE l.scenario_id = b.scenario_id AND l.is_deleted = 0
                     AND (l.source_binding_id = b.id OR l.target_binding_id = b.id));

INSERT INTO chk_result(name, viol)
SELECT 'L6 组织载体：两部门挂在真实 org_root 下且 ACTIVE（RLS/组织维度 join 可落）', count(*)
FROM ecos_control.td_organization o
WHERE o.remark = 'SEED-ENTMGMT-V1'
  AND (o.status <> 'ACTIVE' OR o.parent_org_id <> 'org_root'
    OR NOT EXISTS (SELECT 1 FROM ecos_control.td_organization r
                    WHERE r.org_id = 'org_root' AND r.status = 'ACTIVE'));

INSERT INTO chk_result(name, viol)
SELECT 'L7 事实表 department_id 全部命中真实组织行（无 d-01 之类无载体编码）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact s
      WHERE s.batch_id='SEED-ENTMGMT-V1' AND NOT EXISTS
            (SELECT 1 FROM ecos_control.td_organization o WHERE o.org_id = s.department_id)
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_cost_fact c
      WHERE c.batch_id='SEED-ENTMGMT-V1' AND NOT EXISTS
            (SELECT 1 FROM ecos_control.td_organization o WHERE o.org_id = c.department_id)
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_resource_fact r
      WHERE r.batch_id='SEED-ENTMGMT-V1' AND NOT EXISTS
            (SELECT 1 FROM ecos_control.td_organization o WHERE o.org_id = r.department_id)
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_project_attribution a
      WHERE a.batch_id='SEED-ENTMGMT-V1' AND NOT EXISTS
            (SELECT 1 FROM ecos_control.td_organization o WHERE o.org_id = a.department_id)) q;

-- ── M 结果面必须为空（seed 不产结果，编造即虚假验收；设计册 §7 B-1）
INSERT INTO chk_result(name, viol)
SELECT 'M1 结果载体六表行数全为 0', count(*) FROM (
    SELECT 'ecos_dw.ecos_application_forecast_run' t, count(*) n FROM ecos_dw.ecos_application_forecast_run
    UNION ALL SELECT 'ecos_dw.ecos_forecast_input_snapshot', count(*) FROM ecos_dw.ecos_forecast_input_snapshot
    UNION ALL SELECT 'ecos_control.ecos_fc_action_ext',      count(*) FROM ecos_control.ecos_fc_action_ext
    UNION ALL SELECT 'ecos_dw.ecos_application_action_outcome', count(*) FROM ecos_dw.ecos_application_action_outcome
    UNION ALL SELECT 'ecos_cognitive.ecos_cognitive_outcome_evaluation', count(*) FROM ecos_cognitive.ecos_cognitive_outcome_evaluation
    UNION ALL SELECT 'ecos_ai.forecast',                     count(*) FROM ecos_ai.forecast
) q WHERE q.n <> 0;

-- ── N 保留面白名单未被污染（清理脚本头声明的 23 表基线，seed 只应增组织/骨架/事实/装配面）
INSERT INTO chk_result(name, viol)
SELECT 'N1 保留面白名单行数未变（2026-10-14 T2 后实测基线）', count(*) FROM (
    VALUES ('ecos_control.sys_config', 112), ('ecos_control.sys_dict', 49),
           ('ecos_data.ecos_pipeline_function', 120), ('ecos_infra.schema_changes', 191),
           ('ecos_infra.schema_snapshots', 191), ('ecos_data.dq_rule', 10),
           ('ecos_data.ecos_dq_rule', 5), ('ecos_control.td_user', 1),
           ('ecos_control.td_audit_log', 5), ('ecos_data.td_datasource', 1)
) AS w(tbl, want)
LEFT JOIN (
    SELECT 'ecos_control.sys_config' tbl, count(*) got FROM ecos_control.sys_config
    UNION ALL SELECT 'ecos_control.sys_dict', count(*) FROM ecos_control.sys_dict
    UNION ALL SELECT 'ecos_data.ecos_pipeline_function', count(*) FROM ecos_data.ecos_pipeline_function
    UNION ALL SELECT 'ecos_infra.schema_changes', count(*) FROM ecos_infra.schema_changes
    UNION ALL SELECT 'ecos_infra.schema_snapshots', count(*) FROM ecos_infra.schema_snapshots
    UNION ALL SELECT 'ecos_data.dq_rule', count(*) FROM ecos_data.dq_rule
    UNION ALL SELECT 'ecos_data.ecos_dq_rule', count(*) FROM ecos_data.ecos_dq_rule
    UNION ALL SELECT 'ecos_control.td_user', count(*) FROM ecos_control.td_user
    UNION ALL SELECT 'ecos_control.td_audit_log', count(*) FROM ecos_control.td_audit_log
    UNION ALL SELECT 'ecos_data.td_datasource', count(*) FROM ecos_data.td_datasource
) g ON g.tbl = w.tbl WHERE coalesce(g.got,-1) <> w.want;

INSERT INTO chk_result(name, viol)
SELECT 'N2 登录/治理/配置面零写入（seed-entmgmt 句柄不得出现在用户、角色、权限、审计、配置、字典、数据源面）', count(*) FROM (
    -- 列名实测（2026-10-14 information_schema，禁凭记忆）：td_user/td_role/td_permission/td_role_permission
    -- 用 created_by；td_audit_log 无创建者列（仅 user_id）；sys_config/sys_dict 无任何创建者列
    -- ⇒ 这两面改查"句柄字面量是否被写进业务键/值"，等价语义、不造悬空列引用。
    SELECT 'td_user' t, count(*) n FROM ecos_control.td_user
     WHERE coalesce(created_by,'')='seed-entmgmt'
    UNION ALL SELECT 'td_role', count(*) FROM ecos_control.td_role
     WHERE coalesce(created_by,'')='seed-entmgmt'
    UNION ALL SELECT 'td_permission', count(*) FROM ecos_control.td_permission
     WHERE coalesce(created_by,'')='seed-entmgmt'
    UNION ALL SELECT 'td_role_permission', count(*) FROM ecos_control.td_role_permission
     WHERE coalesce(created_by,'')='seed-entmgmt'
    UNION ALL SELECT 'td_audit_log', count(*) FROM ecos_control.td_audit_log
     WHERE coalesce(user_id,'')='seed-entmgmt'
    UNION ALL SELECT 'sys_config', count(*) FROM ecos_control.sys_config
     WHERE config_key LIKE '%entmgmt%'
        OR coalesce(config_value,'') LIKE '%seed-entmgmt%'
        OR coalesce(description,'') LIKE '%SEED-ENTMGMT-V1%'
    UNION ALL SELECT 'sys_dict', count(*) FROM ecos_control.sys_dict
     WHERE dict_type LIKE '%entmgmt%' OR dict_code LIKE '%entmgmt%'
        OR coalesce(ext_value,'') LIKE '%seed-entmgmt%'
        OR coalesce(dict_label,'') LIKE '%SEED-ENTMGMT-V1%'
    UNION ALL SELECT 'td_datasource', count(*) FROM ecos_data.td_datasource
     WHERE coalesce(create_by,'')='seed-entmgmt'
) q WHERE q.n <> 0;

INSERT INTO chk_result(name, viol)
SELECT 'N3 航空 AOC 遗留场景已清空（G1=B 裁决落地：sc001/sc002/sc003 不得残留）', count(*)
FROM ecos_ai.ecos_business_scenario WHERE id IN ('sc001','sc002','sc003');

-- ── O 演示句柄一致性（G3=甲 零 DDL 约定句柄，可整批反查/删除）
INSERT INTO chk_result(name, viol)
SELECT 'O1 公共列取值与 FactImportService.hydrate()（:530-549）对齐 + 句柄齐全（四张事实表）', count(*) FROM (
    SELECT 1 FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND (dq_status <> 'PASSED' OR is_active <> 1 OR version_no <> '1' OR is_deleted <> 0
         OR domain <> 'entmgmt' OR source_ref <> 'seed:entmgmt-v1'
         OR evidence_ref <> 'seed:entmgmt-v1:evidence' OR create_by <> 'seed-entmgmt'
         OR update_by <> 'seed-entmgmt' OR currency <> 'CNY' OR source_type <> 'MANUAL')
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_cost_fact
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND (dq_status <> 'PASSED' OR is_active <> 1 OR version_no <> '1' OR is_deleted <> 0
         OR domain <> 'entmgmt' OR source_ref <> 'seed:entmgmt-v1' OR currency <> 'CNY')
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_resource_fact
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND (dq_status <> 'PASSED' OR is_active <> 1 OR version_no <> '1' OR is_deleted <> 0
         OR domain <> 'entmgmt' OR staff_hash_algo <> 'SHA-256' OR cost_category <> 'SALARY')
    UNION ALL SELECT 1 FROM ecos_dw.ecos_biz_project_attribution
     WHERE batch_id='SEED-ENTMGMT-V1'
       AND (dq_status <> 'PASSED' OR is_active <> 1 OR version_no <> '1' OR is_deleted <> 0
         OR domain <> 'entmgmt' OR source_ref <> 'seed:entmgmt-v1')) q;

INSERT INTO chk_result(name, viol)
SELECT 'O2 主键形态 MC01：全批 id 均为 32 位 hex 或语义前缀（sb_/bsl_ 12 位 hex），长度 ≤ 36 且无重复', count(*) FROM (
    SELECT 'stage' t, id FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1'
    UNION ALL SELECT 'cost', id FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1'
    UNION ALL SELECT 'res', id FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1'
    UNION ALL SELECT 'attr', id FROM ecos_dw.ecos_biz_project_attribution WHERE batch_id='SEED-ENTMGMT-V1'
    UNION ALL SELECT 'cal', id FROM ecos_ontology.ecos_caliber WHERE domain='entmgmt'
    UNION ALL SELECT 'calv', id FROM ecos_ontology.ecos_caliber_version WHERE domain='entmgmt'
    UNION ALL SELECT 'met', id FROM ecos_ontology.ecos_metric_definition WHERE domain='entmgmt'
    UNION ALL SELECT 'per', id FROM ecos_data.ecos_data_period_status WHERE domain='entmgmt'
    UNION ALL SELECT 'pro', id FROM ecos_knowledge.ecos_kb_profile WHERE source_query_ref='seed:entmgmt-v1'
    UNION ALL SELECT 'ass', id FROM ecos_knowledge.ecos_kb_assumption WHERE domain='entmgmt'
    UNION ALL SELECT 'ent', id FROM ecos_ontology.ecos_ontology_entity WHERE domain='entmgmt'
    UNION ALL SELECT 'ka', id FROM ecos_knowledge.knowledge_article WHERE create_by='seed-entmgmt'
    UNION ALL SELECT 'agt', id FROM ecos_ai.ecos_agent WHERE id = md5('seed:entmgmt-v1:agent:entmgmt-operating-copilot')
    UNION ALL SELECT 'bnd', id FROM ecos_ai.ecos_scenario_binding WHERE scenario_id='sc_entmgmt'
    UNION ALL SELECT 'lnk', id FROM ecos_control.ecos_scenario_binding_link WHERE scenario_id='sc_entmgmt'
) q WHERE length(q.id) > 36
   OR (q.id !~ '^[0-9a-f]{32}$' AND q.id !~ '^(sb|bsl)_[0-9a-f]{12}$');

INSERT INTO chk_result(name, viol)
SELECT 'O2b id 全局唯一（同 id 出现在多表/多行 = md5 逻辑键撞车，必须 0）', count(*) FROM (
    SELECT id, count(*) AS n FROM (
        SELECT id FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1'
        UNION ALL SELECT id FROM ecos_dw.ecos_biz_cost_fact WHERE batch_id='SEED-ENTMGMT-V1'
        UNION ALL SELECT id FROM ecos_dw.ecos_biz_resource_fact WHERE batch_id='SEED-ENTMGMT-V1'
        UNION ALL SELECT id FROM ecos_ontology.ecos_caliber WHERE domain='entmgmt'
        UNION ALL SELECT id FROM ecos_ontology.ecos_caliber_version WHERE domain='entmgmt'
        UNION ALL SELECT id FROM ecos_ontology.ecos_metric_definition WHERE domain='entmgmt'
        UNION ALL SELECT id FROM ecos_knowledge.ecos_kb_profile WHERE source_query_ref='seed:entmgmt-v1'
        UNION ALL SELECT id FROM ecos_knowledge.ecos_kb_assumption WHERE domain='entmgmt') t
    GROUP BY id HAVING count(*) > 1) q;

INSERT INTO chk_result(name, viol)
SELECT 'O3 staff_ref_hash 形态 = 64 位十六进制（DQ 形态校验；真摘要缺口按 D-06 登记，不在数据面伪造来源）', count(*)
FROM ecos_dw.ecos_biz_resource_fact
WHERE batch_id='SEED-ENTMGMT-V1' AND staff_ref_hash !~ '^[0-9a-f]{64}$';

INSERT INTO chk_result(name, viol)
SELECT 'O4 git_ref 如实 NULL（后端规范 §十一：无历史版本可指则不编造 commit hash）', count(*)
FROM ecos_ontology.ecos_caliber_version
WHERE domain='entmgmt' AND is_deleted=0 AND git_ref IS NOT NULL;

END
$do$;

-- ═════════════════════════════════════════════════════════════════════════════
-- 报告面（信息展示，判定以上面 chk_result 为准）
-- ═════════════════════════════════════════════════════════════════════════════

\echo ' '
\echo '── 报告 1：判定项清单'
SELECT seq AS "#",
       split_part(name, ' ', 1) AS code,
       left(regexp_replace(name, '^[^ ]+ ', ''), 46) AS chk_name,
       viol,
       CASE WHEN viol = 0 THEN 'OK' ELSE 'FAIL' END AS verdict
FROM chk_result ORDER BY seq;

\echo ' '
\echo '── 报告 2：18 枚锚点（库聚合 vs 设计册手算）'
SELECT w.anchor, w.want_v AS design_book_value, got.v AS db_value,
       CASE WHEN got.v IS NOT DISTINCT FROM w.want_v THEN 'OK' ELSE 'FAIL' END AS verdict
FROM (VALUES
        ('acc_act_p1', 10524000.00::numeric), ('acc_act_p2',  7016000.00),
        ('acc_act_p3',  4385000.00),          ('acc_act_all',21925000.00),
        ('acc_plan_p1', 6612000.00),          ('acc_plan_all',13775000.00),
        ('col_act_all',20000000.00),          ('col_plan_all',13500000.00),
        ('sub_act_all', 9256800.00),          ('sub_plan_all', 6201600.00),
        ('cost_act_all',9500000.00),          ('cost_plan_all',5700000.00),
        ('rate_acc_act',     8.77::numeric),  ('rate_acc_plan',     5.51),
        ('rate_col_act',     8.00),           ('rate_col_plan',     5.40),
        ('rate_sub_act',     6.09),           ('rate_sub_plan',     4.08)
     ) AS w(anchor, want_v)
LEFT JOIN (
    SELECT 'acc_act_p1' k, sum(amount) v FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL' AND project_id='p-ent-001'
    UNION ALL SELECT 'acc_act_p2', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL' AND project_id='p-ent-002'
    UNION ALL SELECT 'acc_act_p3', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL' AND project_id='p-ent-003'
    UNION ALL SELECT 'acc_act_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='ACTUAL'
    UNION ALL SELECT 'acc_plan_p1', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='PLAN' AND project_id='p-ent-001'
    UNION ALL SELECT 'acc_plan_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='ACCEPTANCE' AND fact_type='PLAN'
    UNION ALL SELECT 'col_act_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='COLLECTION' AND fact_type='ACTUAL'
    UNION ALL SELECT 'col_plan_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='COLLECTION' AND fact_type='PLAN'
    UNION ALL SELECT 'sub_act_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='SUBCONTRACT' AND fact_type='ACTUAL'
    UNION ALL SELECT 'sub_plan_all', sum(amount) FROM ecos_dw.ecos_biz_stage_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND stage='SUBCONTRACT' AND fact_type='PLAN'
    UNION ALL SELECT 'cost_act_all', sum(amount) FROM ecos_dw.ecos_biz_cost_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND fact_type='ACTUAL'
    UNION ALL SELECT 'cost_plan_all', sum(amount) FROM ecos_dw.ecos_biz_cost_fact
     WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0 AND fact_type='PLAN'
    UNION ALL SELECT 'rate_acc_act', sum(rate) FROM (SELECT DISTINCT stage, fact_type, period, realization_rate AS rate
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d
      WHERE d.stage='ACCEPTANCE' AND d.fact_type='ACTUAL'
    UNION ALL SELECT 'rate_acc_plan', sum(rate) FROM (SELECT DISTINCT stage, fact_type, period, realization_rate AS rate
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d
      WHERE d.stage='ACCEPTANCE' AND d.fact_type='PLAN'
    UNION ALL SELECT 'rate_col_act', sum(rate) FROM (SELECT DISTINCT stage, fact_type, period, realization_rate AS rate
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d
      WHERE d.stage='COLLECTION' AND d.fact_type='ACTUAL'
    UNION ALL SELECT 'rate_col_plan', sum(rate) FROM (SELECT DISTINCT stage, fact_type, period, realization_rate AS rate
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d
      WHERE d.stage='COLLECTION' AND d.fact_type='PLAN'
    UNION ALL SELECT 'rate_sub_act', sum(rate) FROM (SELECT DISTINCT stage, fact_type, period, realization_rate AS rate
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d
      WHERE d.stage='SUBCONTRACT' AND d.fact_type='ACTUAL'
    UNION ALL SELECT 'rate_sub_plan', sum(rate) FROM (SELECT DISTINCT stage, fact_type, period, realization_rate AS rate
      FROM ecos_dw.ecos_biz_stage_fact WHERE batch_id='SEED-ENTMGMT-V1' AND is_deleted=0) d
      WHERE d.stage='SUBCONTRACT' AND d.fact_type='PLAN'
) got ON got.k = w.anchor
ORDER BY w.anchor;

\echo ' '
\echo '── 报告 3：画像存库值 vs 事实现算值（分位数不是手填的）'
WITH series(metric_code, value) AS (
    SELECT 'accrual_revenue_amount', s.amount FROM ecos_dw.ecos_biz_stage_fact s
     WHERE s.batch_id='SEED-ENTMGMT-V1' AND s.stage='ACCEPTANCE' AND s.fact_type='ACTUAL' AND s.is_deleted=0
    UNION ALL
    SELECT 'cash_collection_amount', s.amount FROM ecos_dw.ecos_biz_stage_fact s
     WHERE s.batch_id='SEED-ENTMGMT-V1' AND s.stage='COLLECTION' AND s.fact_type='ACTUAL' AND s.is_deleted=0
    UNION ALL
    SELECT 'operating_profit_amount', b.accrual_revenue - coalesce(k.cost_amount, 0::numeric)
      FROM (SELECT project_id, period,
                   sum(CASE WHEN stage='ACCEPTANCE' THEN amount ELSE 0::numeric END) AS accrual_revenue
              FROM ecos_dw.ecos_biz_stage_fact
             WHERE batch_id='SEED-ENTMGMT-V1' AND fact_type='ACTUAL' AND is_deleted=0
             GROUP BY 1,2) b
      LEFT JOIN (SELECT project_id, period, sum(amount) AS cost_amount
                   FROM ecos_dw.ecos_biz_cost_fact
                  WHERE batch_id='SEED-ENTMGMT-V1' AND fact_type='ACTUAL' AND is_deleted=0
                    AND project_id IS NOT NULL GROUP BY 1,2) k
             ON k.project_id = b.project_id AND k.period = b.period
    UNION ALL
    SELECT 'ent_realization_ratio', round(b.accrual_revenue / b.contract_base, 4)
      FROM (SELECT project_id, period,
                   sum(CASE WHEN stage='ACCEPTANCE' THEN amount ELSE 0::numeric END) AS accrual_revenue,
                   max(contract_base) AS contract_base
              FROM ecos_dw.ecos_biz_stage_fact
             WHERE batch_id='SEED-ENTMGMT-V1' AND fact_type='ACTUAL' AND is_deleted=0
             GROUP BY 1,2) b),
agg AS (
    SELECT metric_code, count(*)::int AS n,
           round(avg(value)::numeric, 4) AS mean_v,
           round(percentile_cont(0.10) WITHIN GROUP (ORDER BY value)::numeric, 4) AS p10,
           round(percentile_cont(0.50) WITHIN GROUP (ORDER BY value)::numeric, 4) AS p50,
           round(percentile_cont(0.90) WITHIN GROUP (ORDER BY value)::numeric, 4) AS p90
      FROM series GROUP BY metric_code)
SELECT p.metric_code, p.sample_count AS n_stored, g.n AS n_recomputed,
       p.p10, g.p10 AS p10_calc, p.p50, g.p50 AS p50_calc, p.p90, g.p90 AS p90_calc,
       CASE WHEN p.p10 <= p.p50 AND p.p50 <= p.p90 THEN 'OK' ELSE 'FAIL' END AS monotonic
FROM ecos_knowledge.ecos_kb_profile p
JOIN agg g ON g.metric_code = p.metric_code
WHERE p.source_query_ref='seed:entmgmt-v1' AND p.is_deleted=0
ORDER BY p.metric_code;

\echo ' '
\echo '── 报告 4：场景装配可解析性（target_ref 是否指向真实载体行）'
SELECT b.binding_type, left(b.target_ref, 24) AS target_ref,
       CASE WHEN (
              CASE b.binding_type
                WHEN 'DATASET'        THEN EXISTS (SELECT 1 FROM ecos_data.td_datasource d
                                                    WHERE d.datasource_id = b.target_ref)
                WHEN 'OBJECT_TYPE'    THEN EXISTS (SELECT 1 FROM ecos_ontology.ecos_ontology_entity e
                                                    WHERE e.id = b.target_ref AND e.is_deleted = 0)
                WHEN 'KNOWLEDGE_BASE' THEN EXISTS (SELECT 1 FROM ecos_knowledge.knowledge_article k
                                                    WHERE k.id = b.target_ref AND k.is_deleted = 0)
                WHEN 'AI_AGENT'       THEN EXISTS (SELECT 1 FROM ecos_ai.ecos_agent a
                                                    WHERE a.id = b.target_ref)
                ELSE false END)
            THEN '可解析' ELSE '悬空' END AS resolvable,
       coalesce((SELECT string_agg(l.link_type, ',' ORDER BY l.link_type)
                   FROM ecos_control.ecos_scenario_binding_link l
                  WHERE l.is_deleted = 0
                    AND (l.source_binding_id = b.id OR l.target_binding_id = b.id)), '无连边') AS edges
FROM ecos_ai.ecos_scenario_binding b
WHERE b.scenario_id = 'sc_entmgmt' AND b.is_deleted = 0
ORDER BY b.binding_type;

\echo ' '
\echo '── 报告 5：口径与公式（UI 口径页 /calibers/{code}/current 应可读）'
SELECT c.code, c.unit, c.status AS caliber_status, cv.status AS version_status,
       cv.additive, cv.period_granularity, cv.git_ref, m.status AS metric_status, m.aggregation
FROM ecos_ontology.ecos_caliber c
JOIN ecos_ontology.ecos_caliber_version cv ON cv.caliber_id = c.id
JOIN ecos_ontology.ecos_metric_definition m ON m.caliber_id = c.id
WHERE c.domain = 'entmgmt' AND c.is_deleted = 0
ORDER BY c.code;

-- ═════════════════════════════════════════════════════════════════════════════
-- 终局判定：任一 viol ≠ 0 ⇒ RAISE EXCEPTION ⇒ psql 退出码非 0
-- ═════════════════════════════════════════════════════════════════════════════
DO $do$
DECLARE
    bad   integer;
    total integer;
    r     record;
BEGIN
    SELECT count(*) INTO bad FROM chk_result WHERE viol <> 0;
    SELECT count(*) INTO total FROM chk_result;

    IF bad > 0 THEN
        FOR r IN SELECT seq, name, viol FROM chk_result WHERE viol <> 0 ORDER BY seq LOOP
            RAISE WARNING 'FAIL #%  %  （违规 %）', r.seq, r.name, r.viol;
        END LOOP;
        RAISE EXCEPTION 'VERIFY-SEED-ENTMGMT-V1 不通过：% / % 判定项存在违规', bad, total;
    END IF;

    RAISE NOTICE 'VERIFY-SEED-ENTMGMT-V1 通过：% 判定项全部 viol=0（17 表 467 行 / 18 锚点 / MD 四规则 / DQ 九条 / 三级平衡 / 口径四关可 SQL 化部分 / 结果面为空 / 保留面未污染）',
        total;
END
$do$;

ROLLBACK;
\echo '>>> VERIFY SEED-ENTMGMT-V1 结束（事务已回滚，库未改变）'
