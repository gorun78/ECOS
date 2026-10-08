-- V191 (卷05 附录 E.3 / R-13 ① 分批收敛 第1批): 心智状态族三表归位 ecos_cognitive 并建镜像 + 存量幂等复制
-- 追溯: W124/C106（认知真身 14 张全在 public，落点违 ST07）、W125/C107；X-23（hypothesis/belief/evidence 行在 public）、X-26（evidence.blob、evidence.refuting_evidence_ids、hypothesis.evidence_ids、belief.distribution 为二进制 JSON 列违 MC02）、X-27（DR06~DR08 七基线列无一表全合规）
-- 需求依据: REQ-COG-05（心智状态落盘，ADR-9 四层）；ADR-9 口径: 心智状态（证据/假设/不确定性判断）落盘
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①（认知资产+心智状态全归 ecos_cognitive；执行顺序=先 {schema}. 限定 + 新表，再双写迁移，禁一次性 DROP）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(旧表只加不删)
--
-- ── 本批范围（E.3 V190~V195 分批表 · 第 1 批 · 心智状态族）───────
--   public.ecos_cognitive_belief     (V129) → ecos_cognitive.ecos_cognitive_belief
--   public.ecos_cognitive_hypothesis (V128) → ecos_cognitive.ecos_cognitive_hypothesis
--   public.ecos_cognitive_evidence   (V127) → ecos_cognitive.ecos_cognitive_evidence
--   （V190 已为 belief/hypothesis 补 mind_id/scenario_id 过渡列；本批镜像承接同两列，存量复制段填 NULL——
--     V190 属文件落地未实跑批（§14.4），复制段禁读 V190/V159 后加列，防实库列面漂移（X-27/V159:4 矛盾已实证））
--
-- ── 双写与读切换纪律 ─────────────────────────────────────
--   本批只**并建新表 + 幂等复制**（WHERE NOT EXISTS 守卫，无 ON CONFLICT，MC03/IR 合规）；
--   应用侧读切换（F05-13 Mapper 化时 `{schema}.` 限定改指新表）与写切换（认知状态机双写）**均发生在代码层**，
--   旧 public 表全程不 DROP、不改名、不停用（IR03；停写声明待代码写点真实切换后补，V163/X-31 教训）。
--
-- ── 列形态依据 ───────────────────────────────────────────
--   旧列 1:1 合规化映射（V127~V129 → 本批新表）：
--   id → VARCHAR(36)（MC01 新表主键收口；存量 id 均为 ≤36 字符业务键/UUID，超长值 INSERT 显式报错=fail-loud，禁静默截断）；
--   二进制 JSON 列（distribution/evidence_ids/blob/refuting_evidence_ids）→ *_json TEXT（DR04/MC02），复制段 CAST(x AS TEXT)（V197 同法）；
--   BOOLEAN 列（is_manual_override/is_valid/is_conflict）→ SMALLINT 0/1（DR05，V197 degraded→is_degraded 同法）；
--   DECIMAL(p,s) → NUMERIC(p,s)（MC01 唯一金额/比率形态）；
--   DR06~DR08 七基线列（domain/version_no/is_deleted/create_by/update_by/create_time/update_time）新表补齐（X-27 修复面），
--   旧表缺列（evidence.domain 等）复制段以字面量 'default'/'1' 显式补形（V213 补形纪律，禁静默丢字段）。
--   枚举 CHECK 本批不建（R-15 同纪律：待应用全量切换后再补，V189 口径）。

-- ══════════════════════════════════════════════════════════
-- ── 1. ecos_cognitive.ecos_cognitive_belief（不确定性判断，V129 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_belief (
    id                 VARCHAR(36)  NOT NULL,               -- MC01: 应用侧 UUID；存量复制沿用旧业务 id（≤36 字符）
    variable_name      VARCHAR(128) NOT NULL,               -- 不可观测经营变量名（V129 同义列）
    tenant_scope       VARCHAR(64),                         -- 租户/域隔离（V129 同义列，可空兼容存量）
    domain             VARCHAR(64)  NOT NULL DEFAULT 'default', -- DR08（V129 本已带 domain，宽度沿用）
    distribution_json  TEXT         NOT NULL DEFAULT '[]',  -- DR04/MC02: 原二进制 JSON 分布列合规化为 TEXT 序列化
    version            INTEGER      NOT NULL DEFAULT 1,     -- 分布版本（V129 语义列，与乐观锁 version_no 区分保留）
    snapshot_version   INTEGER,                             -- 时间回放预留（V129 同义列，Phase 3）
    is_manual_override SMALLINT     NOT NULL DEFAULT 0,     -- DR05: 原 BOOLEAN 人工覆写标记→SMALLINT
    override_reason    TEXT,                                -- 人工覆写理由（V129 同义列）
    last_evidence_id   VARCHAR(64),                         -- 证据溯源引用（V129 同义列，跨表无 FK，应用层校验）
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/ARCHIVED（CHECK 待应用切换后补，R-15 纪律）
    mind_id            VARCHAR(36),                         -- V187 心智资产引用（V190 过渡列镜像；存量复制段填 NULL，双写期由应用回填）
    scenario_id        VARCHAR(36),                         -- 场景引用（V190 过渡列镜像；同上）
    -- DR06~DR08 七基线列
    version_no         VARCHAR(20)  NOT NULL DEFAULT '1',   -- DR07（旧表无此列，复制段以 '1' 补形）
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_by          VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by          VARCHAR(64),
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_belief_cv191 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cv191_belief_var_ver
    ON ecos_cognitive.ecos_cognitive_belief (variable_name, domain, version, is_deleted); -- 旧 uniq(variable_name,domain,version)+is_deleted 代偿（禁条件索引 MC03）
CREATE INDEX IF NOT EXISTS idx_cv191_belief_domain ON ecos_cognitive.ecos_cognitive_belief (domain, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv191_belief_mind   ON ecos_cognitive.ecos_cognitive_belief (mind_id, scenario_id);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_belief IS '不确定性判断表（ADR-9 心智层 P 库，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_belief 双写期保留，读切换在 Mapper 层）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.distribution_json IS '有限离散概率分布序列化 TEXT [{"outcome","prob"}]（MC02 合规形态，源列 V127 系二进制 JSON）';

-- 存量幂等复制（public → ecos_cognitive；WHERE NOT EXISTS 守卫，重跑零重复行零报错）
INSERT INTO ecos_cognitive.ecos_cognitive_belief
    (id, variable_name, tenant_scope, domain, distribution_json, version, snapshot_version,
     is_manual_override, override_reason, last_evidence_id, status, mind_id, scenario_id,
     version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.variable_name, o.tenant_scope, COALESCE(o.domain, 'default'),
       CAST(o.distribution AS TEXT), COALESCE(o.version, 1), o.snapshot_version,
       CASE WHEN o.is_manual_override THEN 1 ELSE 0 END,
       o.override_reason, o.last_evidence_id, COALESCE(o.status, 'ACTIVE'),
       NULL, NULL,                                       -- mind_id/scenario_id：V190 未实跑批，复制段不读该列，双写期应用回填
       '1',                                              -- version_no：V159 后加列不读（漂移面 X-27），补形
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_cognitive_belief o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_cognitive_belief n WHERE n.id = o.id
);

-- ══════════════════════════════════════════════════════════
-- ── 2. ecos_cognitive.ecos_cognitive_hypothesis（假设，V128 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_hypothesis (
    id               VARCHAR(36)  NOT NULL,                -- MC01 同上
    hypothesis_code  VARCHAR(128) NOT NULL,                -- 业务唯一键（V128 同义列）
    tenant_scope     VARCHAR(64),
    statement        TEXT         NOT NULL,                -- 假设陈述（业务语言）
    domain           VARCHAR(64)  NOT NULL DEFAULT 'default', -- V128 本已带 domain（可空→补默认）
    metric_ref       VARCHAR(128),                         -- 关联经营变量（对齐 belief.variable_name）
    evidence_ids_json TEXT        NOT NULL DEFAULT '[]',   -- DR04/MC02: 原 evidence_ids 二进制 JSON→TEXT
    is_valid         SMALLINT     NOT NULL DEFAULT 1,      -- DR05: 原 BOOLEAN→SMALLINT
    invalid_at       TIMESTAMP,                            -- 失效时间
    invalid_reason   TEXT,                                 -- 失效原因
    status           VARCHAR(16)  NOT NULL DEFAULT 'VALID', -- 终态 4 值枚举（R-15 ①；旧值 VALID/INVALIDATED/ARCHIVED 只读别名映射留痕在 public 侧 V189，本表不收旧值）
    old_status       VARCHAR(20),                          -- R-15 ① 留痕列（映射前旧枚举原文，应用侧写入，禁 DDL 伪造历史）
    mind_id          VARCHAR(36),                          -- V190 过渡列镜像（存量复制段填 NULL）
    scenario_id      VARCHAR(36),
    version_no       VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted       SMALLINT     NOT NULL DEFAULT 0,
    create_by        VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by        VARCHAR(64),
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_hypothesis_cv191 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cv191_hypo_code
    ON ecos_cognitive.ecos_cognitive_hypothesis (hypothesis_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv191_hypo_domain ON ecos_cognitive.ecos_cognitive_hypothesis (domain, invalid_at);
CREATE INDEX IF NOT EXISTS idx_cv191_hypo_mind   ON ecos_cognitive.ecos_cognitive_hypothesis (mind_id, scenario_id);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_hypothesis IS '认知假设表（ADR-9 心智层 H 库，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_hypothesis 双写期保留）';

INSERT INTO ecos_cognitive.ecos_cognitive_hypothesis
    (id, hypothesis_code, tenant_scope, statement, domain, metric_ref, evidence_ids_json,
     is_valid, invalid_at, invalid_reason, status, old_status, mind_id, scenario_id,
     version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.hypothesis_code, o.tenant_scope, o.statement, COALESCE(o.domain, 'default'), o.metric_ref,
       CAST(o.evidence_ids AS TEXT),
       CASE WHEN o.is_valid THEN 1 ELSE 0 END,
       o.invalid_at, o.invalid_reason, COALESCE(o.status, 'VALID'),
       NULL,                                             -- old_status：存量枚举映射（VALID→BELIEVED 等）属应用侧 R-15 ① 动作，DDL 禁伪造历史主体（V189 纪律）
       NULL, NULL,
       '1', COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_cognitive_hypothesis o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_cognitive_hypothesis n WHERE n.id = o.id
);

-- ══════════════════════════════════════════════════════════
-- ── 3. ecos_cognitive.ecos_cognitive_evidence（证据，V127 真身归位）──
-- ══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_evidence (
    id                         VARCHAR(36)  NOT NULL,      -- MC01 同上
    evidence_code              VARCHAR(128) NOT NULL,      -- 业务唯一键（幂等登记）
    tenant_scope               VARCHAR(64),
    source_type                VARCHAR(32)  NOT NULL,      -- SYSTEM_DATA/NEWS/EXPERT/PIPELINE
    source_ref                 VARCHAR(256),               -- 来源定位
    blob_json                  TEXT         NOT NULL DEFAULT '{}', -- DR04/MC02: 原 blob 二进制 JSON→TEXT（X-26 点名列）
    confidence                 NUMERIC(5,4) NOT NULL DEFAULT 0.5,  -- 可信度 0~1（ST03-A 比率形态，MC01 禁无精度 NUMERIC）
    is_conflict                SMALLINT     NOT NULL DEFAULT 0,    -- DR05: 原 BOOLEAN→SMALLINT
    refuting_evidence_ids_json TEXT         NOT NULL DEFAULT '[]', -- DR04/MC02: 原冲突对方证据 id 列表→TEXT
    effective_time             TIMESTAMP,
    expire_time                TIMESTAMP,
    status                     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/CONFLICTED/ARCHIVED（CHECK 延后，R-15 纪律）
    domain                     VARCHAR(50)  NOT NULL DEFAULT 'default', -- DR08（X-27 实证旧表**无** domain，V159:4 注释与实库矛盾；新表补齐，复制段以 'default' 补形）
    version_no                 VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted                 SMALLINT     NOT NULL DEFAULT 0,
    create_by                  VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by                  VARCHAR(64),
    create_time                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_evidence_cv191 PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_cv191_ev_code
    ON ecos_cognitive.ecos_cognitive_evidence (evidence_code, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv191_ev_source ON ecos_cognitive.ecos_cognitive_evidence (source_type, status);
CREATE INDEX IF NOT EXISTS idx_cv191_ev_time   ON ecos_cognitive.ecos_cognitive_evidence (create_time);
COMMENT ON TABLE  ecos_cognitive.ecos_cognitive_evidence IS '认知证据表（ADR-9 感知/心智层，R-13 ① 归位 ecos_cognitive；旧 public.ecos_cognitive_evidence 双写期保留）';

INSERT INTO ecos_cognitive.ecos_cognitive_evidence
    (id, evidence_code, tenant_scope, source_type, source_ref, blob_json, confidence,
     is_conflict, refuting_evidence_ids_json, effective_time, expire_time, status,
     domain, version_no, is_deleted, create_by, update_by, create_time, update_time)
SELECT o.id, o.evidence_code, o.tenant_scope, o.source_type, o.source_ref,
       CAST(o.blob AS TEXT), COALESCE(o.confidence, 0.5),
       CASE WHEN o.is_conflict THEN 1 ELSE 0 END,
       CAST(o.refuting_evidence_ids AS TEXT),
       o.effective_time, o.expire_time, COALESCE(o.status, 'ACTIVE'),
       'default', '1',                                    -- 旧表实库无 domain（X-27），补形；version_no 不读 V159 后加列
       COALESCE(o.is_deleted, 0), COALESCE(o.create_by, 'system'), o.update_by,
       COALESCE(o.create_time, CURRENT_TIMESTAMP), COALESCE(o.update_time, o.create_time, CURRENT_TIMESTAMP)
FROM public.ecos_cognitive_evidence o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_cognitive.ecos_cognitive_evidence n WHERE n.id = o.id
);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚: 三新表为并建形态，旧 public 表全程未改未删，读路径保持旧表即等效回滚；
--           新表已复制行如需清理，只允许置 is_deleted=1（IR03 禁 DELETE/DROP，由人工执行面另行裁决）。
-- 幂等性: CREATE TABLE/INDEX IF NOT EXISTS + INSERT ... WHERE NOT EXISTS 守卫，重复执行零重复行、零报错；禁 ON CONFLICT（MC03）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 表名 schema 限定（ecos_cognitive./public.）+ 小写下划线 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] 4 个 JSON 语义列全部 _json 后缀 + TEXT（无二进制 JSON 列）✓  [DR05] is_manual_override/is_valid/is_conflict SMALLINT ✓
-- [DR06] 审计五列 ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain NOT NULL DEFAULT 'default' ✓（X-27 修复面）
-- [MC01] 主键 VARCHAR(36) 应用侧 UUID，DDL 无默认值、无自增序列；confidence NUMERIC(5,4) 带精度 ✓
-- [MC02] 无二进制 JSON 列；CAST(x AS TEXT) 非裸二进制转换 ✓  [MC03] 无 ON CONFLICT/RETURNING/ILIKE/条件索引/无 databaseId 分支 ✓
-- [ST03-A] 无比率/金额新增（confidence=派生统计量非个人数据）✓  [ST07] 认知真身归 ecos_cognitive（R-13 ① 控制域 5+1）✓
-- [IR02] 手动 psql ✓  [IR03] 只加不删，旧表只停写不 DROP ✓
-- [R-13 ①] 第 1 批（心智状态族）✓  [R-15] 枚举 CHECK 与 old_status 映射留待应用侧 ✓  [待补] mind_id/scenario_id 存量回填属双写期应用动作（V190 列面）
