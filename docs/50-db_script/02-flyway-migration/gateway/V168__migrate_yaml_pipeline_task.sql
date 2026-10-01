-- V168 (卷02 §E.5 / F02-03): 4 行 YAML pipeline task 一次性迁移为 JSON DAG definition（失败即整批拒，禁静默跳过）
-- 追溯: W44→C33（管道双模型双执行器，单一事实源）；需求依据 REQ-DATA-01、REQ-DB-02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-2 a（JSON definition 为唯一承流模型，YAML 只读归档）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 前置（IR02 手工执行顺序）: 先 V170（补写 ecos_data.ecos_pipeline_definition 单源 DDL）后本脚本；
--   ecos_data.ecos_pipeline_node / ecos_pipeline_edge 现状 DDL 仅存 ecos-sql/postgresql/03_ecos_data.sql（本批不重复建表，仅消费）。
-- 执行方式: psql -v ON_ERROR_STOP=1 -f 本文件；脚本自管事务块：
--   Phase B 报告分类先行提交 → Phase C 迁移事务（入口门：存在 REJECTED 即 RAISE EXCEPTION 整批拒，
--   事务回滚但报告表已提交留痕=「拒绝并出报告」，禁静默）。
-- 转换语义（可解析判据，SQL 可判定部分）: YAML task 的 DAG 以已解析步骤表 public.ecos_pipeline_step 为准；
--   无步骤行 / yaml_content 为空 / node_id|node_type 缺失 / 同任务 node_id 重复 → 判「解析失败」不迁移。
--   迁移产物: ecos_data.ecos_pipeline_definition（status=DRAFT 待人工签核）+ 规范化 node/edge（step→node，
--   相邻 step_order→edge；步骤配置列原样随行迁移）。definition 大 JSON 载荷的 YAML 深解析重建属业务改造批次
--   （PipelineGitService/F02-03 步骤 3），本批以 node/edge 规范化行保证无损可追溯。
-- 幂等条件: 全部按确定性键 NOT EXISTS（ytm_<task_id> / ytn_<task_id>_<node_id> / yte_<task_id>_<to_node>）；重跑 0 行新增。
-- 回滚说明: 迁入行 id 前缀 ytm_/ytn_/yte_ + 报告表全量留痕；回滚=将迁入 definition 置 status='REJECTED'（禁 DELETE/DROP，IR03）；
--   源表 public.ecos_pipeline_task/step 不动（YAML 侧按 R-2 a 转只读归档，删除动作待授权）。

-- ── Phase A. 迁移报告表（新表，落 ecos_data，ST07 白名单）────
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_yaml_migration_report (
    id                     VARCHAR(36) PRIMARY KEY,      -- MC01：确定性键 ytmrep_<task_id>
    migration_batch        VARCHAR(64)  NOT NULL DEFAULT 'V168',
    task_id                VARCHAR(36)  NOT NULL,        -- 源 public.ecos_pipeline_task 主键
    task_name              VARCHAR(200),
    outcome                VARCHAR(32)  NOT NULL,        -- PENDING_MIGRATE | REJECTED | MIGRATED
    reason                 TEXT,                          -- 拒因（REJECTED 行必填，禁静默）
    migrated_definition_id VARCHAR(64),
    create_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   -- DR06
    update_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by              VARCHAR(100),
    update_by              VARCHAR(100),
    is_deleted             SMALLINT NOT NULL DEFAULT 0,
    domain                 VARCHAR(50) NOT NULL DEFAULT 'default',         -- DR08
    version_no             VARCHAR(20) NOT NULL DEFAULT '1'                -- DR07
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ypm_report_task
    ON ecos_data.ecos_pipeline_yaml_migration_report(migration_batch, task_id);

-- ── Phase B. 逐任务可解析性分类登记（独立提交，保证拒因在批失败后仍留痕）──
-- 可解析判据（SQL 可判定部分，全文内联于下方 CASE）:
--   yaml_content 非空 ∧ 存在已解析步骤行 ∧ 步骤无 node_id/node_type 缺失 ∧ 同任务 node_id 无重复
BEGIN;
INSERT INTO ecos_data.ecos_pipeline_yaml_migration_report
    (id, migration_batch, task_id, task_name, outcome, reason, migrated_definition_id,
     create_time, update_time, create_by, update_by, is_deleted, domain, version_no)
SELECT CONCAT('ytmrep_', t.id), 'V168', t.id, t.name,
       CASE WHEN t.yaml_content IS NOT NULL AND TRIM(t.yaml_content) <> ''
             AND EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id)
             AND NOT EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id
                             AND (s.node_id IS NULL OR TRIM(s.node_id) = ''
                                  OR s.node_type IS NULL OR TRIM(s.node_type) = ''))
             AND NOT EXISTS (SELECT 1 FROM public.ecos_pipeline_step s1
                             JOIN public.ecos_pipeline_step s2
                               ON s2.task_id = s1.task_id AND s2.node_id = s1.node_id AND s2.id <> s1.id
                             WHERE s1.task_id = t.id)
            THEN 'PENDING_MIGRATE' ELSE 'REJECTED' END,
       CASE WHEN t.yaml_content IS NULL OR TRIM(t.yaml_content) = '' THEN 'yaml_content 为空'
            WHEN NOT EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id)
                 THEN '无已解析步骤行（ecos_pipeline_step 0 行），DAG 不可派生'
            WHEN EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id
                         AND (s.node_id IS NULL OR TRIM(s.node_id) = ''
                              OR s.node_type IS NULL OR TRIM(s.node_type) = ''))
                 THEN '存在 node_id/node_type 缺失步骤'
            WHEN EXISTS (SELECT 1 FROM public.ecos_pipeline_step s1
                         JOIN public.ecos_pipeline_step s2
                           ON s2.task_id = s1.task_id AND s2.node_id = s1.node_id AND s2.id <> s1.id
                         WHERE s1.task_id = t.id)
                 THEN '同任务内 node_id 重复'
            ELSE NULL END,
       CONCAT('ytm_', t.id),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'V168_script', 'V168_script', 0, 'default', '1'
FROM public.ecos_pipeline_task t
WHERE NOT EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_definition d WHERE d.id = CONCAT('ytm_', t.id))
  AND NOT EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_yaml_migration_report r
                  WHERE r.migration_batch = 'V168' AND r.task_id = t.id);
COMMIT;

-- ── Phase C. 迁移事务（入口门：任一 REJECTED → 整批拒）────────
BEGIN;
DO $do$
DECLARE n_rejected INTEGER;
BEGIN
    SELECT COUNT(*) INTO n_rejected
      FROM ecos_data.ecos_pipeline_yaml_migration_report
     WHERE migration_batch = 'V168' AND outcome = 'REJECTED';
    IF n_rejected > 0 THEN
        RAISE EXCEPTION 'V168: % 条 YAML task 解析失败，整批拒绝（禁静默跳过；拒因见 ecos_data.ecos_pipeline_yaml_migration_report）', n_rejected;
    END IF;
END
$do$;

-- C1. definition 骨架行（DRAFT 待人工签核，同 V166 精神）
INSERT INTO ecos_data.ecos_pipeline_definition (id, name, description, status, created_at, updated_at)
SELECT CONCAT('ytm_', t.id), t.name,
       CONCAT('V168 迁移自 YAML task ', t.id, '；DAG 已规范化至 ecos_pipeline_node/edge，definition 载荷重建归 F02-03 业务批次'),
       'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM public.ecos_pipeline_task t
WHERE EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_yaml_migration_report r
              WHERE r.migration_batch = 'V168' AND r.task_id = t.id AND r.outcome = 'PENDING_MIGRATE')
  AND NOT EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_definition d WHERE d.id = CONCAT('ytm_', t.id));

-- C2. 步骤 → 规范化节点（配置与依赖列随行原样迁移）
INSERT INTO ecos_data.ecos_pipeline_node
    (id, definition_id, node_id, type, config, depends_on, position_x, position_y, created_at, updated_at)
SELECT CONCAT('ytn_', s.task_id, '_', s.node_id), CONCAT('ytm_', s.task_id),
       s.node_id, s.node_type, s.config_json, s.depends_on,
       COALESCE(s.position_x, 0), COALESCE(s.position_y, 0), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM public.ecos_pipeline_step s
WHERE EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_yaml_migration_report r
              WHERE r.migration_batch = 'V168' AND r.task_id = s.task_id AND r.outcome = 'PENDING_MIGRATE')
  AND NOT EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_node n
                  WHERE n.id = CONCAT('ytn_', s.task_id, '_', s.node_id));

-- C3. 相邻步骤 → 边（step_order 链式依赖）
INSERT INTO ecos_data.ecos_pipeline_edge (id, definition_id, from_node_id, to_node_id, created_at)
SELECT CONCAT('yte_', a.task_id, '_', b.node_id), CONCAT('ytm_', a.task_id),
       a.node_id, b.node_id, CURRENT_TIMESTAMP
FROM public.ecos_pipeline_step a
JOIN public.ecos_pipeline_step b
  ON b.task_id = a.task_id AND b.step_order = a.step_order + 1
WHERE EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_yaml_migration_report r
              WHERE r.migration_batch = 'V168' AND r.task_id = a.task_id AND r.outcome = 'PENDING_MIGRATE')
  AND NOT EXISTS (SELECT 1 FROM ecos_data.ecos_pipeline_edge e
                  WHERE e.id = CONCAT('yte_', a.task_id, '_', b.node_id));

-- C4. 报告收口：PENDING_MIGRATE → MIGRATED
UPDATE ecos_data.ecos_pipeline_yaml_migration_report
   SET outcome = 'MIGRATED', update_time = CURRENT_TIMESTAMP, update_by = 'V168_script'
 WHERE migration_batch = 'V168' AND outcome = 'PENDING_MIGRATE';
COMMIT;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- 可解析判据已全文内联于 Phase B（无占位符残留，脚本即完整可执行文件）。
-- [DR01] 读写均 schema 限定（public./ecos_data.）✓  [DR02] 新表 ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06~DR08] 报告表审计五列 + version_no + domain ✓
-- [MC01] 报告表主键 VARCHAR(36) 确定性应用侧键 ✓  [MC02] reason 用 TEXT；无 JSON 二进制列新建 ✓
-- [MC03] 无非条件/条件索引混写（唯一索引均无条件子句）、无库内策略语句、无分区、无数组列、无带时区时间类型、
--        无裸 cast、无 JSON 检索操作符（列对列原样搬运，不做 SQL 侧 JSON 解析）✓
-- [ST07] 新表落 ecos_data（管道 DAG 归属，E.1 R-1 a）✓  [ST09] 零新增 FOREIGN KEY ✓
-- [IR03] 源表 task/step 不删不改；失败整批拒 + 报告留痕（禁静默）✓
--
-- 附录 A. <可解析> / <拒因> 判据对照（与 Phase B 内联表达式逐字一致，评审比对用）:
-- <可解析> =
--   t.yaml_content IS NOT NULL AND TRIM(t.yaml_content) <> ''
--   AND EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id)
--   AND NOT EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id
--                   AND (s.node_id IS NULL OR TRIM(s.node_id) = '' OR s.node_type IS NULL OR TRIM(s.node_type) = ''))
--   AND NOT EXISTS (SELECT 1 FROM public.ecos_pipeline_step s1
--                   JOIN public.ecos_pipeline_step s2
--                     ON s2.task_id = s1.task_id AND s2.node_id = s1.node_id AND s2.id <> s1.id
--                   WHERE s1.task_id = t.id)
-- <拒因> =
--   CASE WHEN t.yaml_content IS NULL OR TRIM(t.yaml_content) = '' THEN 'yaml_content 为空'
--        WHEN NOT EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id)
--             THEN '无已解析步骤行（ecos_pipeline_step 0 行），DAG 不可派生'
--        WHEN EXISTS (SELECT 1 FROM public.ecos_pipeline_step s WHERE s.task_id = t.id
--                     AND (s.node_id IS NULL OR TRIM(s.node_id) = '' OR s.node_type IS NULL OR TRIM(s.node_type) = ''))
--             THEN '存在 node_id/node_type 缺失步骤'
--        WHEN EXISTS (SELECT 1 FROM public.ecos_pipeline_step s1
--                     JOIN public.ecos_pipeline_step s2
--                       ON s2.task_id = s1.task_id AND s2.node_id = s1.node_id AND s2.id <> s1.id
--                     WHERE s1.task_id = t.id)
--             THEN '同任务内 node_id 重复'
--        ELSE NULL END
