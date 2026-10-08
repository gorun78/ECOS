-- V193 (卷05 附录 E.3 / R-13 ① 分批收敛 第3批): 场景定义与绑定族归位 ecos_cognitive 并建镜像 + 存量幂等复制
-- 追溯: W124/C106（认知真身 14 张全在 public：business_scenario 3 行 / scenario_binding 8 行 / scenario_binding_link 2 行，X-23）、W125/C107（business_scenario.metrics 二进制 JSON 列违 MC02，X-26）、X-30（binding 实库 10 列、无 target_id/target_type）
-- 需求依据: REQ-COG-05 / §0.6.2（六类绑定三层）；架构铁律 §四附则1（{schema}. 限定）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①；执行边界 §14.4 = 脚本文件落地（**不实跑库**）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(旧表只加不删)
--
-- ── 本批范围（E.3 V190~V195 分批表 · 第 3 批 · 场景/绑定族）───────
--   public.ecos_business_scenario       (V123) → ecos_cognitive.ecos_business_scenario
--   public.ecos_scenario_binding        (V123+V147/V199 面) → ecos_cognitive.ecos_scenario_binding
--   public.ecos_scenario_binding_link   (V160) → ecos_cognitive.ecos_scenario_binding_link
--   （第 4 批场景族之 public.ecos_scenario_run 的产物收敛已由 **V197**（R-17 ①：ecos_dw.ecos_application_forecast_run）承接，
--     旧 run 表只停写不镜像，避免双权威；见 V195 总登记。）
--
-- ── 分册 07 交叉声明 ────────────────────────────────────
--   场景工作台 v2 表族（V205 ecos_scenario_definition / V209 ecos_scenario_execution / V213 对账视图）属**场景层编排面**（workspace，卷07 口径）；
--   本批镜像是**认知域归位面**（R-13 ①，卷05 E.3 权威清单），两者读切换均以应用侧 Mapper 收敛为准，互不 DROP（IR03）。

-- ══════════════════════════════════════════════════════════
-- ── 1. ecos_cognitive.ecos_business_scenario（业务场景主表，V123 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_business_scenario (
    id                  VARCHAR(36)  NOT NULL,             -- MC01: 应用侧 UUID；存量 id（sc001 等）≤36 字符沿用，超长=fail-loud
    name                VARCHAR(255) NOT NULL,
    description         TEXT         DEFAULT '',
    business_goal       TEXT         DEFAULT '',
    department          VARCHAR(128),
    priority            VARCHAR(16)  DEFAULT 'MEDIUM',     -- CRITICAL/HIGH/MEDIUM/LOW
    status              VARCHAR(32)  DEFAULT 'DRAFT',      -- DRAFT/ACTIVE/COMPLETED/SUSPENDED
    budget              VARCHAR(64),                       -- 旧串值形态原样承接（如 '850万'，**不自动解析**，V213 同纪律）；金额实列化属场景层 v2（V205 NUMERIC(18,2)）轨道，本批不建金额列（MC01 无裸数值列）
    safety_index_target NUMERIC(8,4),                      -- MC01: DECIMAL→NUMERIC(8,4)（指数/比率类，带精度）
    actual_safety_index NUMERIC(8,4),
    metrics_json        TEXT,                              -- DR04/MC02: 原 metrics 二进制 JSON→TEXT（X-26 点名列）
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default', -- DR08（旧表无，新表补齐，复制段补形）
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1', -- DR07（同上）
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by           VARCHAR(64),
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_business_scenario_cv193 PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_cv193_bizscn_status ON ecos_cognitive.ecos_business_scenario (status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv193_bizscn_dept   ON ecos_cognitive.ecos_business_scenario (department, is_deleted);
COMMENT ON TABLE  ecos_cognitive.ecos_business_scenario IS '业务场景主表（认知域真身 14 张之一，R-13 ① 归位 ecos_cognitive；旧 public.ecos_business_scenario 双写期保留；场景层 v2 权威面另见 V205/卷07）';

INSERT INTO ecos_cognitive.ecos_business_scenario
    (id, name, description, business_goal, department, priority, status, budget,
     safety_index_target, actual_safety_index, metrics_json, domain, version_no,
     is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.name, o.description, o.business_goal, o.department, o.priority,
       COALESCE(o.status, 'DRAFT'), o.budget,
       o.safety_index_target, o.actual_safety_index,
       CAST(o.metrics AS TEXT),
       'default', '1',
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_business_scenario o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_business_scenario n WHERE n.id = o.id
);

-- ══════════════════════════════════════════════════════════
-- ── 2. ecos_cognitive.ecos_scenario_binding（六类绑定表，V123 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_scenario_binding (
    id           VARCHAR(36)  NOT NULL,                    -- MC01 同上（存量 sb001~sb008 沿用）
    scenario_id  VARCHAR(64)  NOT NULL,                    -- 场景引用（归位后指 ecos_cognitive.ecos_business_scenario.id，旧宽度沿用）
    binding_type VARCHAR(32)  NOT NULL,                    -- DATASET/OBJECT_TYPE/KNOWLEDGE_BASE/AI_AGENT/SECURITY_POLICY/INTERFACE（§0.6.2；CHECK 延后，R-15 纪律）
    target_ref   VARCHAR(255) NOT NULL,                    -- 资源标识（旧字符串别名，保留只读兼容，V147 口径）
    target_id    VARCHAR(36),                              -- 真引用 id（V147/V199 契约列；**实库旧表无此列，X-30 实证** ⇒ 复制段不读、填 NULL，双写期应用回填）
    target_type  VARCHAR(20),                              -- 真资源类型（同上；值域以 V199 契约为准）
    remark       TEXT         DEFAULT '',
    domain       VARCHAR(50)  NOT NULL DEFAULT 'default',  -- DR08（旧表无，补形）
    version_no   VARCHAR(20)  NOT NULL DEFAULT '1',        -- DR07（同上）
    is_deleted   SMALLINT     NOT NULL DEFAULT 0,
    create_by    VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by    VARCHAR(64),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP,
    CONSTRAINT pk_ecos_scenario_binding_cv193 PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_cv193_bind_scn  ON ecos_cognitive.ecos_scenario_binding (scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv193_bind_type ON ecos_cognitive.ecos_scenario_binding (binding_type, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv193_bind_tid  ON ecos_cognitive.ecos_scenario_binding (target_id, target_type); -- V199 列面镜像（新表先建列，读切换后置）
COMMENT ON TABLE  ecos_cognitive.ecos_scenario_binding IS '场景六类绑定表（认知域真身 14 张之一，R-13 ① 归位 ecos_cognitive；旧 public.ecos_scenario_binding 双写期保留；X-30 列面漂移在新表以 NULL 列承接，应用回填）';

INSERT INTO ecos_cognitive.ecos_scenario_binding
    (id, scenario_id, binding_type, target_ref, target_id, target_type, remark,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.scenario_id, o.binding_type, o.target_ref,
       NULL, NULL,                                        -- X-30：实库旧表止于 10 列，复制段禁读 V147/V199 后加列
       o.remark,
       'default', '1',
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_scenario_binding o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_scenario_binding n WHERE n.id = o.id
);

-- ══════════════════════════════════════════════════════════
-- ── 3. ecos_cognitive.ecos_scenario_binding_link（绑定关系边表，V160 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_scenario_binding_link (
    id                VARCHAR(36)  NOT NULL,               -- MC01 同上（存量 bsl001/bsl002 沿用）
    scenario_id       VARCHAR(64)  NOT NULL,
    source_binding_id VARCHAR(64)  NOT NULL,               -- 端点引用 ecos_cognitive.ecos_scenario_binding.id（应用层校验，无跨表 FK）
    target_binding_id VARCHAR(64)  NOT NULL,
    link_type         VARCHAR(16)  NOT NULL,               -- MAPPING/EXTRACTION/COGNITION/GOVERN/EXPOSE（§0.6.2）
    source_contract   VARCHAR(128),                        -- 跨工作台契约引用（§0.6.2.2；存量 placeholder-* 原样承接，V213 取证纪律，新值禁再写占位由应用侧把关）
    remark            TEXT         DEFAULT '',
    domain            VARCHAR(50)  NOT NULL DEFAULT 'default', -- DR08（V160 本已带，直传）
    version_no        VARCHAR(20)  NOT NULL DEFAULT '1',   -- DR07（V160 本已带，直传）
    is_deleted        SMALLINT     NOT NULL DEFAULT 0,
    create_by         VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by         VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP,
    CONSTRAINT pk_ecos_scenario_binding_link_cv193 PRIMARY KEY (id),
    CONSTRAINT ck_cv193_blink_link_type CHECK (
        link_type IN ('MAPPING', 'EXTRACTION', 'COGNITION', 'GOVERN', 'EXPOSE')
    )                                                      -- 值域与 V160 完全一致（非新枚举收敛，R-15 不适用）
);
CREATE INDEX IF NOT EXISTS idx_cv193_blink_scn   ON ecos_cognitive.ecos_scenario_binding_link (scenario_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv193_blink_src   ON ecos_cognitive.ecos_scenario_binding_link (source_binding_id);
CREATE INDEX IF NOT EXISTS idx_cv193_blink_tgt   ON ecos_cognitive.ecos_scenario_binding_link (target_binding_id);
CREATE INDEX IF NOT EXISTS idx_cv193_blink_type  ON ecos_cognitive.ecos_scenario_binding_link (link_type);
COMMENT ON TABLE  ecos_cognitive.ecos_scenario_binding_link IS '场景绑定关系边表（认知域真身 14 张之一，§0.6.2，R-13 ① 归位 ecos_cognitive；旧 public.ecos_scenario_binding_link 双写期保留）';

INSERT INTO ecos_cognitive.ecos_scenario_binding_link
    (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract, remark,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.scenario_id, o.source_binding_id, o.target_binding_id, o.link_type, o.source_contract, o.remark,
       COALESCE(o.domain, 'default'), COALESCE(o.version_no, '1'),
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_scenario_binding_link o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_scenario_binding_link n WHERE n.id = o.id
);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 同 V191：三新表并建、旧表未动，回滚=读路径保持旧表；新表清理只置 is_deleted=1（人工裁决面）。
-- 幂等性: CREATE TABLE/INDEX IF NOT EXISTS + INSERT ... WHERE NOT EXISTS 守卫；禁 ON CONFLICT（MC03，V123/V160 旧种子段的 ON CONFLICT 不效仿）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓  [DR04] metrics_json TEXT ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06/07/08] 七基线列齐备（X-27 修复面）✓
-- [MC01] 主键 VARCHAR(36) 无默认值；safety_index_* NUMERIC(8,4) 带精度；budget 沿用旧 VARCHAR 串形态（非金额列，不触发 NUMERIC 红线）✓
-- [MC02] 无二进制 JSON 列；CAST(o.metrics AS TEXT) ✓  [MC03] 无 ON CONFLICT/RETURNING/ILIKE/条件索引；CHECK 值域与 V160 一致 ✓
-- [ST03-A] 无个人数据敏感列 ✓  [ST07] 归 ecos_cognitive（R-13 ①）✓  [IR02] 手动 psql ✓  [IR03] 只加不删 ✓
-- [R-13 ①] 第 3 批（场景/绑定族）✓  [X-30] target_id/target_type 以 NULL 列承接 ✓  [V197] scenario_run 产物轨道不重复镜像 ✓
