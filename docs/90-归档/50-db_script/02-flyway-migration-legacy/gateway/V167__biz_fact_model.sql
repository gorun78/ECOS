-- V167 (卷02 §E.2): 业务事实五表落业务域 ecos_dw（改写 PRD-02 §1.2 草案违规项：MC01/MC02/MC04 + DR06~08 补齐）
-- 追溯: W59→C48（DDL 违规成片）/ F02-06-3（is_active 唯一活跃方案）；需求依据 REQ-DATA-03、REQ-SCN（09 册预测场景）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1 a（业务域五层落 ecos_dw）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 展开说明: 表 1 为文档 §E.2 字面 SQL（时间默认按本批红线归一为 CURRENT_TIMESTAMP，与 NOW() 等值）；
--   其余四表按 §E.2「同构」表列清单逐表展开，未标型列按 ECOS 类型映射展开（*_id→VARCHAR(36)，*_ref→VARCHAR(255)，
--   status/type/kind→VARCHAR(20)，period→VARCHAR(7) 既有口径）；全部表含同一套审计五列 + is_active + domain + version_no。
-- 唯一性策略: 走 is_active 组合唯一索引（F02-06-3），非条件索引、非表达式索引（MC03/MC04 合规）；
--   cost_fact 以 is_pool 列取代草案 COALESCE(project_id,'POOL') 表达式唯一索引（差异③）。
-- ST03-A 登记（E.2 必做，本批仅汇报不改登记表文件）: amount（stage_fact/cost_fact）、hourly_rate（resource_fact）、
--   contract_base（stage_fact）为聚合计算需要豁免列；staff_ref_hash 为**不可逆脱敏存储**非豁免。
-- 幂等: 全部 CREATE ... IF NOT EXISTS，重跑无副作用。回滚说明: 空表新表，回滚=停写（禁 DROP，IR03）。
--
-- 【2026-09-30 校订：布尔语义列名收口 DR05】文档字面 `active_flag`/`pool_flag` 无 `is_` 前缀，违 DR05
--   （正例 is_active/is_deleted，反例 active/deleted）。只读取证：本机库与 Java/XML 侧对两列**零引用**
--   （新表未被代码消费），故直接定名 `is_active`/`is_pool`，无兼容代价；卷02 §E.2/§E.5 与卷03 相应文字随后同步
--   （F02-06-3 的"唯一活跃"方案本身不变，仅列名收口）。V171 同批同改。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_dw 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。

-- ── 表 1: 项目经营归属（§E.2 字面块）───────────────────────
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_biz_project_attribution (
    id                 VARCHAR(36) PRIMARY KEY,           -- MC01：应用侧 UUID
    project_id         VARCHAR(36)  NOT NULL,
    contract_id        VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,
    attribution_ratio  NUMERIC(5,4) NOT NULL,             -- 金额/比率唯一形态 NUMERIC(p,s)
    attribution_type   VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY',
    effective_from     VARCHAR(7)   NOT NULL,
    effective_to       VARCHAR(7),
    source_evidence    VARCHAR(255),
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active        SMALLINT     NOT NULL DEFAULT 1,   -- 1=存活，替代后置 NULL
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    domain             VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)  NOT NULL,
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(100),
    update_by          VARCHAR(100),
    CONSTRAINT ck_bpa_ratio CHECK (attribution_ratio >= 0 AND attribution_ratio <= 1)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bpa_active
    ON ecos_dw.ecos_biz_project_attribution(project_id, contract_id, department_id, effective_from, is_active);
CREATE INDEX IF NOT EXISTS idx_bpa_project ON ecos_dw.ecos_biz_project_attribution(project_id);
CREATE INDEX IF NOT EXISTS idx_bpa_dept    ON ecos_dw.ecos_biz_project_attribution(department_id);

-- ── 表 2: 阶段事实（收入/成本/回款等阶段快照）───────────────
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_biz_stage_fact (
    id                 VARCHAR(36) PRIMARY KEY,           -- MC01：应用侧 UUID
    project_id         VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,
    period             VARCHAR(7)   NOT NULL,             -- yyyy-MM
    stage              VARCHAR(20)  NOT NULL,
    fact_type          VARCHAR(10)  NOT NULL,
    contract_base      NUMERIC(18,2),                     -- ST03-A 豁免登记候选（聚合计算需要）
    attribution_ratio  NUMERIC(5,4),
    realization_rate   NUMERIC(5,4),
    amount             NUMERIC(18,2),                     -- ST03-A 豁免登记候选（聚合计算需要）
    currency           VARCHAR(3),
    source_type        VARCHAR(20),
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active        SMALLINT     NOT NULL DEFAULT 1,
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    domain             VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)  NOT NULL,
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(100),
    update_by          VARCHAR(100),
    CONSTRAINT ck_bsf_attr_ratio CHECK (attribution_ratio IS NULL OR (attribution_ratio >= 0 AND attribution_ratio <= 1)),
    CONSTRAINT ck_bsf_real_rate  CHECK (realization_rate IS NULL OR (realization_rate >= 0 AND realization_rate <= 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bsf_active
    ON ecos_dw.ecos_biz_stage_fact(project_id, department_id, period, stage, fact_type, is_active);
CREATE INDEX IF NOT EXISTS idx_bsf_project ON ecos_dw.ecos_biz_stage_fact(project_id, period);
CREATE INDEX IF NOT EXISTS idx_bsf_dept    ON ecos_dw.ecos_biz_stage_fact(department_id);

-- ── 表 3: 资源投入事实（工号哈希脱敏存储，替代草案 staff_ref 明文）──
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_biz_resource_fact (
    id                 VARCHAR(36) PRIMARY KEY,           -- MC01：应用侧 UUID
    project_id         VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,
    period             VARCHAR(7)   NOT NULL,
    staff_ref_hash     VARCHAR(64)  NOT NULL,             -- 工号单向哈希（不可逆脱敏，非 ST03-A 豁免）
    staff_hash_algo    VARCHAR(20)  NOT NULL DEFAULT 'SHA-256',
    fte                NUMERIC(5,2),
    work_hours         NUMERIC(8,1),
    hourly_rate        NUMERIC(18,2),                     -- ST03-A 豁免登记候选（聚合计算需要；01 册 CLS/mask 侧裁剪）
    cost_category      VARCHAR(20),
    fact_type          VARCHAR(10)  NOT NULL,
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active        SMALLINT     NOT NULL DEFAULT 1,
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    domain             VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20)  NOT NULL,
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(100),
    update_by          VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_brf_active
    ON ecos_dw.ecos_biz_resource_fact(project_id, department_id, period, staff_ref_hash, fact_type, is_active);
CREATE INDEX IF NOT EXISTS idx_brf_project ON ecos_dw.ecos_biz_resource_fact(project_id, period);
CREATE INDEX IF NOT EXISTS idx_brf_hash    ON ecos_dw.ecos_biz_resource_fact(staff_ref_hash);

-- ── 表 4: 成本事实（is_pool 取代 COALESCE 表达式唯一索引）──
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_biz_cost_fact (
    id                    VARCHAR(36) PRIMARY KEY,        -- MC01：应用侧 UUID
    project_id            VARCHAR(36),                    -- 可空；为空时 is_pool=1（公共池成本）
    is_pool             SMALLINT     NOT NULL DEFAULT 0,
    department_id         VARCHAR(36)  NOT NULL,
    period                VARCHAR(7)   NOT NULL,
    cost_category         VARCHAR(20),
    direct_or_allocated   VARCHAR(10),
    allocation_rule_ref   VARCHAR(36),
    amount                NUMERIC(18,2),                  -- ST03-A 豁免登记候选（聚合计算需要）
    currency              VARCHAR(3),
    fact_type             VARCHAR(10)  NOT NULL,
    dq_status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active           SMALLINT     NOT NULL DEFAULT 1,
    batch_id              VARCHAR(36),
    source_ref            VARCHAR(255),
    evidence_ref          VARCHAR(255),
    domain                VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no            VARCHAR(20)  NOT NULL,
    is_deleted            SMALLINT     NOT NULL DEFAULT 0,
    create_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by             VARCHAR(100),
    update_by             VARCHAR(100),
    CONSTRAINT ck_bcf_is_pool CHECK (is_pool IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_bcf_active
    ON ecos_dw.ecos_biz_cost_fact(project_id, is_pool, department_id, period, cost_category, direct_or_allocated, fact_type, is_active);
CREATE INDEX IF NOT EXISTS idx_bcf_project ON ecos_dw.ecos_biz_cost_fact(project_id, period);
CREATE INDEX IF NOT EXISTS idx_bcf_dept    ON ecos_dw.ecos_biz_cost_fact(department_id, period);

-- ── 表 5: 预测输入快照（*_json 仅描述用，禁 WHERE/索引，MC02）──
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_forecast_input_snapshot (
    id                           VARCHAR(36) PRIMARY KEY, -- MC01：snapshot_id 即 id（E.2）
    forecast_run_id              VARCHAR(36) NOT NULL,
    as_of_time                   TIMESTAMP    NOT NULL,
    scope_hash                   VARCHAR(64)  NOT NULL,
    fact_refs_json               TEXT,                    -- DR04：_json 后缀 + TEXT，不参与检索
    metric_versions_json         TEXT,
    profile_versions_json        TEXT,
    assumption_versions_json     TEXT,
    caliber_id                   VARCHAR(36),
    caliber_version              VARCHAR(20),
    checksum                     VARCHAR(64),
    dq_status                    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active                  SMALLINT     NOT NULL DEFAULT 1,
    batch_id                     VARCHAR(36),
    source_ref                   VARCHAR(255),
    evidence_ref                 VARCHAR(255),
    domain                       VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no                   VARCHAR(20)  NOT NULL,
    is_deleted                   SMALLINT     NOT NULL DEFAULT 0,
    create_time                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by                    VARCHAR(100),
    update_by                    VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_fis_run ON ecos_dw.ecos_forecast_input_snapshot(forecast_run_id);
CREATE INDEX IF NOT EXISTS idx_fis_asof        ON ecos_dw.ecos_forecast_input_snapshot(as_of_time);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全部 ecos_dw. 限定 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] 全部 JSON 语义列 _json 后缀且类型 TEXT ✓  [DR05] is_active/is_pool/is_deleted SMALLINT ✓
-- [DR06] 审计五列 create_time/update_time/create_by/update_by/is_deleted ✓
-- [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓（文档字面 'forecast' 已按 DR08 规定形态收口，见头注）
-- [MC01] 主键 VARCHAR(36) 应用侧 UUID，DDL 无默认值方言函数 ✓（无自增技术键、无 UUID 函数默认）
-- [MC02] 金额 NUMERIC(18,2)、比率 NUMERIC(5,4)、无裸 NUMERIC/无参 DECIMAL、无 JSON 二进制列 ✓
-- [MC03] 唯一索引均为**非条件、非表达式**组合索引（is_active/is_pool 方案替代 WHERE/COALESCE 形态）✓；
--        无库内策略语句、无分区、无数组列、无带时区时间类型、无裸 cast；时间默认 CURRENT_TIMESTAMP ✓
-- [ST03-A] amount/hourly_rate/contract_base 已按 E.2 汇报待逐列登记豁免；staff_ref_hash=脱敏存储非豁免 ✓
-- [ST07] 业务域事实表落 ecos_dw（CURATED/APPLICATION）✓  [ST09] 零 FOREIGN KEY（关系由应用层保证）✓
-- [IR03] 只建不删；不触碰 PRD-02 草案旧表 ✓
