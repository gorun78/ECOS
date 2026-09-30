-- V168.1 (卷03 §E.3 / F03-01/F03-09): metric_definition 合规化 —— 旧表 ADD COLUMN 补三字段+DR 列，并建合规新表 ecos_metric_definition（MC01 PK 形态不可 ALTER 补救）
-- 追溯: W77/C61（O-11：缺 caliber_id/formula_version/caliber_snapshot 三字段 + id VARCHAR(64) 违 MC01 + 无 DR06/07/08 列）；关联 W76/C60、W78/C62
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-4=a+b 并行（metric_definition=定义表唯一可写入口；public.ecos_biz_metric 实例表只停写不迁不改名）
-- 号段: 与卷02 同版本号冲突，按定版规则取 .1 子版本（V168 整号 = 卷02）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 选择依据（任务要求说明）: 卷03 §E.3 V168 行原文同时给出两种动作 —— ①"metric_definition 补
--   caliber_id VARCHAR(36) / formula_version VARCHAR(20) / caliber_snapshot TEXT + DR06/07/08 列"
--   （ADD COLUMN，即本脚本 §1）；②"id VARCHAR(64) → 新表 ecos_metric_definition VARCHAR(36) 并迁移
--   （0 行，成本≈0）"（并建合规新表，即本脚本 §2/§3，因 MC01 主键形态无法在 IR03 只加不删下原地修复）。
--   故**两步都落**，忠实文档；新写入口终态 = ecos_metric_definition（代码切换属后续授权项）。
--   注意：ecos_ontology.metric_definition 实测 0 行（O-11/O-12），迁移 §3 为空操作，仅保证幂等可重跑。
-- 【2026-09-30 校订：新表定名收口 DR02+DR03】文档字面 `metric_definition_v2` 同时违两条红线 ——
--   DR02（新表必带 ecos_ 前缀）与 DR03（`_v2` 版本后缀即规范反例 ecos_workflow_task_v2）。只读取证：
--   本机库无该表、Java/XML/TS 零引用（仅本批脚本 V168.1/V216 注释互指）→ 改名无兼容代价，
--   定名 `ecos_ontology.ecos_metric_definition`；旧表 metric_definition（V52 实存形态）名不改（IR03）。
--   索引名同步为 idx_/uniq_ + 全表名形态。

-- ── 1. 旧表补列（ADD COLUMN IF NOT EXISTS，不动已有列；created_at 保留，改名属违 IR03，双写过渡）──
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS caliber_id       VARCHAR(36);   -- REQ-ONTO-02 三字段之一，指向 ecos_caliber.id
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS formula_version  VARCHAR(20);   -- 三字段之二，指向 ecos_caliber_version.version_no
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS caliber_snapshot TEXT;          -- 三字段之三（MC02：TEXT 非 JSON；发布时刻一次性写入后只读，409 ECOS-ONTO-041）
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS create_time      TIMESTAMP;     -- DR06（旧 created_at 停写新读，双写过渡）
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS update_time      TIMESTAMP;
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS create_by        VARCHAR(100);
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS update_by        VARCHAR(100);
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS is_deleted       SMALLINT NOT NULL DEFAULT 0; -- DR05
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS version_no       VARCHAR(20) NOT NULL DEFAULT '1'; -- DR07
ALTER TABLE IF EXISTS ecos_ontology.metric_definition ADD COLUMN IF NOT EXISTS domain           VARCHAR(50) NOT NULL DEFAULT 'default'; -- DR08

-- ── 2. 合规新表 ecos_metric_definition（MC01 PK VARCHAR(36)；唯一可写入口终态，F03-09 语义二分"定义"侧）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_metric_definition (
    id                VARCHAR(36) PRIMARY KEY,        -- MC01: 应用侧 UUID
    code              VARCHAR(128) NOT NULL,          -- 指标编码（F03-02 十项 M_* 编码；E.4 检索列）
    name              VARCHAR(255),
    expression        TEXT,                           -- 公式明文（V2 单位推导输入）
    aggregation       VARCHAR(16) DEFAULT 'SUM',
    entity_code       VARCHAR(128),
    caliber_id        VARCHAR(36),                    -- DRAFT 可空；PUBLISHED 必填（400 ECOS-ONTO-040，F03-01 规则 4）
    formula_version   VARCHAR(20),
    caliber_snapshot  TEXT,                           -- 发布冻结快照，只读（PRD 回写第 1 条：TEXT 非 JSON）
    status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- F03-01 状态机 DRAFT→PUBLISHED→SUPERSEDED（E.4 检索列）
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- DR06
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         VARCHAR(100),
    update_by         VARCHAR(100),
    is_deleted        SMALLINT NOT NULL DEFAULT 0,    -- DR05
    version_no        VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain            VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE ecos_ontology.ecos_metric_definition IS '指标定义合规表（V168.1；MC01 主键形态；口径绑定承载表；旧 ecos_ontology.metric_definition 转停写只读过渡）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_metric_definition_code
    ON ecos_ontology.ecos_metric_definition(code, is_deleted); -- 复合唯一含 is_deleted（不用条件索引，与 V163/V173 同形态）
CREATE INDEX IF NOT EXISTS idx_ecos_metric_definition_domain ON ecos_ontology.ecos_metric_definition(domain);
CREATE INDEX IF NOT EXISTS idx_ecos_metric_definition_status ON ecos_ontology.ecos_metric_definition(status);

-- ── 3. 旧表 → v2 一次性复制（实测 0 行，成本≈0；幂等按 id 不存在才插）────────
-- 回滚说明: 本段为纯增量 INSERT；回滚 = DELETE FROM ecos_ontology.ecos_metric_definition
--   WHERE id IN (本批复制 id)。旧表数据零改动，无逆向恢复需求。
INSERT INTO ecos_ontology.ecos_metric_definition
    (id, code, name, expression, aggregation, entity_code,
     create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
SELECT left('mdv2_' || o.id, 36),
       o.code, o.name, o.expression, o.aggregation, o.entity_code,
       COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.created_at, CURRENT_TIMESTAMP),
       NULL, NULL, 0, '1', 'default'
FROM ecos_ontology.metric_definition o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_ontology.ecos_metric_definition n
    WHERE n.id = left('mdv2_' || o.id, 36)
);

-- ── 4. 旧表停写登记（不 DROP、不改名；R-4 a+b 口径下"定义表唯一可写"终态落 v2）──
COMMENT ON TABLE ecos_ontology.metric_definition IS '指标定义旧表（V52 建，id VARCHAR(64) 违 MC01、created_at 违 DR06，O-11/W77）：V168.1 起停写，新写一律 ecos_ontology.ecos_metric_definition；只加不删（IR03），清理走 PMO 专项';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] 新表 ecos_metric_definition 带 ecos_ 前缀 ✓（文档字面 metric_definition_v2 违 DR02，已收口，见头注；旧表名遵 IR03 不改）
-- [DR03] 单数且无 _v2 版本后缀 ✓（文档字面 _v2 违 DR03 反例形态，已随 DR02 一并收口）  [DR04] caliber_snapshot TEXT（文档定版名，语义为快照文本，非 _json 形态）✓  [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 新表五列 + version_no + domain 齐 ✓  [MC01] v2 PK VARCHAR(36) 无默认值，无 SERIAL ✓
-- [MC02] 无 JSON 类型列；金额列无新增（ST03-A 无登记项）✓
-- [MC03] 无 partial index / :: cast / CREATE POLICY / PARTITION BY；时间默认 CURRENT_TIMESTAMP ✓
-- [ST07] 落 ecos_ontology ✓  [IR02] 手动 psql，未实跑 ✓  [IR03] 旧表旧列零删改，仅 ADD COLUMN + 停写注释 ✓
