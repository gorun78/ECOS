-- V163 (PMO-74.8 H8-T2): kb_cognitive_pipeline 归属定性 → 认知域 schema 收编
-- 背景: `kb_cognitive_pipeline` 为 cognitive-engine 认知管线定义表（写入方
--       CognitivePipelineRepository 在 cognitive-engine-impl），但历史上落在
--       public（主控制 schema 无主）。按 ST07 引擎数据落自家控制 schema，
--       收编为 `ecos_cognitive.ecos_cognitive_pipeline`。
-- 策略（IR03 只加不删）:
--   1) 新表 = DR 规范形态（ecos_ 前缀 DR02、审计 5 字段 DR06、version_no DR07、
--      domain DR08、JSON 列 _json 后缀 DR04、主键 VARCHAR(36) 应用侧 UUID MC01）
--   2) 数据一次性 INSERT 复制（幂等：按 pipeline_id 不存在才插）
--   3) 旧表 public.kb_cognitive_pipeline 不 DROP、不改名，停写（knownLegacy）
--   4) 兼容视图 public.v_kb_cognitive_pipeline：以旧列形态投影新表（活数据），
--      供尚未切 REST 契约的只读方（如 H8-T3 前的统计聚合）过渡使用
-- 上线: psql -U postgres -d sys_man -f V163__h8t2_ecos_cognitive_pipeline.sql

-- ── 1. 新表 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_pipeline (
    id          VARCHAR(36) PRIMARY KEY,              -- MC01: 应用侧生成 UUID，DDL 无默认值
    pipeline_id VARCHAR(64) NOT NULL,                 -- 业务管线键（原 pipeline_id，非自增技术键）
    name        VARCHAR(255),
    status      VARCHAR(32) DEFAULT 'DRAFT',
    config_json JSONB,                                -- DR04: JSON 语义列 _json 后缀
    result_json JSONB,                                -- DR04
    create_time TIMESTAMP NOT NULL DEFAULT NOW(),     -- DR06 审计 5 字段
    update_time TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by   VARCHAR(100),
    update_by   VARCHAR(100),
    is_deleted  SMALLINT NOT NULL DEFAULT 0,          -- DR05
    domain      VARCHAR(50) NOT NULL DEFAULT 'default', -- DR08
    version_no  VARCHAR(20) NOT NULL DEFAULT '1'        -- DR07
);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_pipeline IS '认知管线定义表（cognitive-engine 自有，ST07 收编；旧 public.kb_cognitive_pipeline 停写保留）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_cognitive_pipeline_pid
    ON ecos_cognitive.ecos_cognitive_pipeline(pipeline_id) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ecos_cognitive_pipeline_domain ON ecos_cognitive.ecos_cognitive_pipeline(domain);

-- ── 2. 数据复制（旧表 → 新表，幂等按 pipeline_id 去重）────────
INSERT INTO ecos_cognitive.ecos_cognitive_pipeline
    (id, pipeline_id, name, status, config_json, result_json,
     create_time, update_time, create_by, update_by, is_deleted, domain, version_no)
SELECT left('cogpip_' || o.pipeline_id, 36),
       o.pipeline_id, o.name, o.status, o.config, o.result,
       COALESCE(o.created_at, NOW()), COALESCE(o.updated_at, o.created_at, NOW()),
       o.created_by, o.created_by, COALESCE(o.is_deleted, 0), 'default', '1'
FROM public.kb_cognitive_pipeline o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_cognitive_pipeline n
    WHERE n.pipeline_id = o.pipeline_id
);

-- ── 3. 兼容视图（旧列形态投影新表活数据；旧表本身停写不删）──────
CREATE OR REPLACE VIEW public.v_kb_cognitive_pipeline AS
SELECT n.id,
       n.pipeline_id,
       n.name,
       n.status,
       n.config_json  AS config,
       n.result_json  AS result,
       n.create_by    AS created_by,
       n.create_time  AS created_at,
       n.update_time  AS updated_at,
       n.is_deleted,
       n.domain,
       n.version_no
FROM ecos_cognitive.ecos_cognitive_pipeline n;
COMMENT ON VIEW public.v_kb_cognitive_pipeline IS '兼容视图（H8-T2）：旧 kb_cognitive_pipeline 列形态 → ecos_cognitive.ecos_cognitive_pipeline 活数据；跨域只读过渡用，消费方终态走引擎 REST';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR02] ecos_ 前缀 ✓  [DR04] _json 后缀 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 审计 5 字段 + version_no + domain ✓
-- [IR03] 旧表不 DROP 不改名 ✓  [MC01] VARCHAR(36) 应用侧 UUID ✓
-- [ST07] 新表落认知引擎 schema ecos_cognitive ✓
