-- V192 (卷05 附录 E.3 / R-13 ① 分批收敛 第2批): 认知模型与管线族归位 ecos_cognitive 并建镜像 + 存量幂等复制
-- 追溯: W124/C106（认知真身 14 张全在 public）、W125/C107（model.features 二进制 JSON 列违 MC02、X-26；DR 列缺 X-27）；X-23（model 1 行 / run_invalidation 1 行 / kb_cognitive_pipeline 0 行在 public）
-- 需求依据: ADR-8（模型资产可落盘、推理结果不落盘）、ADR-9（失效→作废留痕）、REQ-COG-05
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①；执行边界 §14.4 = 脚本文件落地（**不实跑库**）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(旧表只加不删)
--
-- ── 本批范围（E.3 V190~V195 分批表 · 第 2 批 · 模型/管线族）───────
--   public.ecos_cognitive_model          (V124) → ecos_cognitive.ecos_cognitive_model          （本批并建镜像 + 复制）
--   public.ecos_cognitive_run_invalidation (V130) → ecos_cognitive.ecos_cognitive_run_invalidation（本批并建镜像 + 复制）
--   public.kb_cognitive_pipeline         (V117) → ecos_cognitive.ecos_cognitive_pipeline       （**已由 V163 收编**，本批不重复建表/复制，仅做停写登记，见 §3）
--
-- ── 双写与读切换纪律 ─────────────────────────────────────
--   同 V191：本批只并建新表 + 幂等复制；读切换（Mapper 层 {schema}. 限定改指新表）与写切换均在应用侧（F05-13），
--   旧 public 表不 DROP、不改名（IR03）。

-- ══════════════════════════════════════════════════════════
-- ── 1. ecos_cognitive.ecos_cognitive_model（认知模型注册，V124 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_model (
    id             VARCHAR(36)  NOT NULL,                  -- MC01: 应用侧 UUID；存量复制沿用旧业务 id（如 model_fcst_baseline_1，≤36 字符，超长=fail-loud）
    model_id       VARCHAR(64)  NOT NULL,                  -- 模型标识（baseline-linear / causal-v1）
    model_type     VARCHAR(32)  NOT NULL,                  -- FORECAST/CAUSAL/SIMULATION/DECISION
    version        INTEGER      NOT NULL DEFAULT 1,        -- 模型版本（V124 语义列，与乐观锁 version_no 区分保留）
    features_json  TEXT,                                   -- DR04/MC02: 原 features 二进制 JSON（特征清单/超参数）→TEXT（X-26 点名列）
    backtest_score NUMERIC(8,6),                           -- 回测评分 0~1（MC01 唯一高精度形态，DECIMAL→NUMERIC(8,6)）
    status         VARCHAR(16)  NOT NULL DEFAULT 'active', -- active/retired（CHECK 延后，R-15 纪律）
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',-- DR08（旧表列面系 V159 后加、实库未证实，新表自带；复制段以 'default' 补形）
    version_no     VARCHAR(20)  NOT NULL DEFAULT '1',      -- DR07（同上）
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    create_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by      VARCHAR(64),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_model_cv192 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cv192_model_id_ver
    ON ecos_cognitive.ecos_cognitive_model (model_id, version, is_deleted); -- 旧 uniq(model_id,version)+is_deleted 代偿（禁条件索引 MC03）
CREATE INDEX IF NOT EXISTS idx_cv192_model_type   ON ecos_cognitive.ecos_cognitive_model (model_type, status);
CREATE INDEX IF NOT EXISTS idx_cv192_model_domain ON ecos_cognitive.ecos_cognitive_model (domain, is_deleted);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_model IS '认知模型注册表（ADR-8 模型资产落盘，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_model 双写期保留）';

INSERT INTO ecos_cognitive.ecos_cognitive_model
    (id, model_id, model_type, version, features_json, backtest_score, status,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.model_id, o.model_type, COALESCE(o.version, 1),
       CAST(o.features AS TEXT),
       o.backtest_score, COALESCE(o.status, 'active'),
       'default', '1',                                    -- V159 后加列不读（漂移面 X-27），补形
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_cognitive_model o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_cognitive_model n WHERE n.id = o.id
);

-- ══════════════════════════════════════════════════════════
-- ── 2. ecos_cognitive.ecos_cognitive_run_invalidation（失效→作废留痕，V130 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_run_invalidation (
    id             VARCHAR(36)  NOT NULL,                  -- MC01 同上
    event_id       VARCHAR(64)  NOT NULL,                  -- 触发作废的 ecos.cognitive 事件 id（cog_evt_ 前缀）
    hypothesis_id  VARCHAR(64)  NOT NULL,                  -- 失效假设引用（归位后指 ecos_cognitive.ecos_cognitive_hypothesis.id，V191；跨表无 FK，应用层校验）
    run_id         VARCHAR(64)  NOT NULL,                  -- 被作废推演 run 引用（产物终态随 R-17 ① 指向 ecos_dw.ecos_application_forecast_run.forecast_run_id，V197）
    auto_detected  SMALLINT     NOT NULL DEFAULT 1,        -- DR05: 原 BOOLEAN→SMALLINT
    superseded_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 作废时刻
    detail         TEXT,                                   -- 作废摘要
    domain         VARCHAR(50)  NOT NULL DEFAULT 'default',-- DR08（旧表 V130 无 domain，新表补齐，复制段补形）
    version_no     VARCHAR(20)  NOT NULL DEFAULT '1',      -- DR07（同上）
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    create_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by      VARCHAR(64),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_run_invalid_cv192 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cv192_run_inv_evt_run
    ON ecos_cognitive.ecos_cognitive_run_invalidation (event_id, run_id, is_deleted); -- 幂等键（旧 uniq(event_id,run_id)+is_deleted 代偿）
CREATE INDEX IF NOT EXISTS idx_cv192_run_inv_run ON ecos_cognitive.ecos_cognitive_run_invalidation (run_id);
CREATE INDEX IF NOT EXISTS idx_cv192_run_inv_hyp ON ecos_cognitive.ecos_cognitive_run_invalidation (hypothesis_id);
CREATE INDEX IF NOT EXISTS idx_cv192_run_inv_time ON ecos_cognitive.ecos_cognitive_run_invalidation (superseded_at);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_run_invalidation IS '失效→作废推演结论关联留痕表（ADR-9 心智事件，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_run_invalidation 双写期保留）';

INSERT INTO ecos_cognitive.ecos_cognitive_run_invalidation
    (id, event_id, hypothesis_id, run_id, auto_detected, superseded_at, detail,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.event_id, o.hypothesis_id, o.run_id,
       CASE WHEN o.auto_detected THEN 1 ELSE 0 END,
       COALESCE(o.superseded_at, CURRENT_TIMESTAMP), o.detail,
       'default', '1',
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_cognitive_run_invalidation o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_cognitive_run_invalidation n WHERE n.id = o.id
);

-- ══════════════════════════════════════════════════════════
-- ── 3. kb_cognitive_pipeline 停写登记（不重复建表：V163 已收编）──
-- ══════════════════════════════════════════════════════════
-- 认知真身 14 张中的管线表收敛路径已由 V163 落地：
--   public.kb_cognitive_pipeline (V117, BIGSERIAL PK, knownLegacy)
--     → ecos_cognitive.ecos_cognitive_pipeline (V163, VARCHAR(36) PK + 幂等复制 + 兼容视图 public.v_kb_cognitive_pipeline)
-- 本批**不重复 CREATE/INSERT**（避免与 V163 列面分叉；V163 新表 config_json/result_json 的列型修正属 V197 平行 TEXT 列轨道，X-26/W125）。
-- X-31 实证：代码仍全部读写旧表（CognitivePipelineRepository.java:62/68/82/91/98），新表实库不存在且 Java 侧零引用 ⇒
--   停写声明**待应用侧写点真实切换后补**（E.3 "V163 声明停写但未落地"整改项，代码先行，DDL 不越权）。
-- 过渡读纪律（应用侧）：新 Mapper 一律 {schema}. 限定读 ecos_cognitive.ecos_cognitive_pipeline；
--   未切契约只读方经 V163 兼容视图 public.v_kb_cognitive_pipeline，禁再新增对旧表裸名读写。
COMMENT ON TABLE public.kb_cognitive_pipeline IS 'knownLegacy（V163 收编轨道）：权威已定 ecos_cognitive.ecos_cognitive_pipeline；停写声明待代码写点切换后生效（X-31）；本列注释由 V192 登记收敛状态';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 同 V191：新表并建、旧表未动，回滚=读路径保持旧表；新表清理只置 is_deleted=1（人工裁决面）。
-- 幂等性: CREATE IF NOT EXISTS + INSERT ... WHERE NOT EXISTS；COMMENT ON 可重跑。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓  [DR04] features_json TEXT（_json 后缀）✓  [DR05] auto_detected SMALLINT ✓
-- [DR06/07/08] 七基线列齐备（X-27 修复面）✓
-- [MC01] 主键 VARCHAR(36) 无默认值；backtest_score NUMERIC(8,6) 带精度 ✓  [MC02] 无二进制 JSON 列 ✓
-- [MC03] CAST(x AS TEXT) 形态、无 ON CONFLICT/RETURNING/ILIKE/条件索引 ✓
-- [ST07] 归 ecos_cognitive（R-13 ①）✓  [IR02] 手动 psql ✓  [IR03] 只加不删 ✓
-- [R-13 ①] 第 2 批（模型/管线族）✓  [V163] 管线表不重复建 ✓  [X-31] 停写待代码先行 ✓
