-- V336: ecos_control.ecos_business_scenario 补建（从零建库断链修复）
-- 追溯: 2026-11-15 世界模型承流批 D108 修 ScenarioDao 时，把读取从裸表名改成显式限定
--       ecos_control 后，gateway 的 DdlCodeSchemaAlignmentGateTest 判红：
--       「代码读端 schema 与 DDL 单源不一致（从零建库必断）：[ecos_control.ecos_business_scenario]」。
--       根因：V123 当初用裸表名建表（落 public），V193 建了 ecos_cognitive 副本，V315 建了 ecos_ai 副本；
--       **ecos_control 版本由代码 search_path 落库产生，单源目录里从来没有它的建表脚本**。
--       实测（2026-11-15 dev PG）：ecos_control 152 行（真实业务数据）、ecos_ai 0 行、ecos_cognitive 0 行
--       ⇒ 真身确在 ecos_control，缺的是 DDL 单源侧，不是代码侧。
-- 纪律: IR03 只加不删；幂等（IF NOT EXISTS）；ST07 显式限定 schema；
--       列类型按 dev PG 实存结构逐列对齐（不是按设计臆造）；主键 VARCHAR(64) 沿用既有宽度。
-- 归属: 控制域 ecos_control（与 V123/V193/V315 三副本登记口径一致：只停写不迁移）。
-- 上线: psql -U postgres -d sys_man -f V336__ecos_control_business_scenario.sql

CREATE TABLE IF NOT EXISTS ecos_control.ecos_business_scenario (
    id                   VARCHAR(64)   NOT NULL,
    name                 VARCHAR(255)  NOT NULL,
    description          TEXT          DEFAULT '',
    business_goal        TEXT          DEFAULT '',
    department           VARCHAR(128),
    priority             VARCHAR(16)   DEFAULT 'MEDIUM',
    status               VARCHAR(32)   DEFAULT 'DRAFT',
    budget               VARCHAR(64),
    safety_index_target  NUMERIC(8,4),
    actual_safety_index  NUMERIC(8,4),
    metrics              JSONB         DEFAULT '{}',
    create_time          TIMESTAMP     NOT NULL DEFAULT now(),
    update_time          TIMESTAMP     NOT NULL DEFAULT now(),
    create_by            VARCHAR(64)   DEFAULT 'system',
    update_by            VARCHAR(64)   DEFAULT 'system',
    is_deleted           SMALLINT      NOT NULL DEFAULT 0,
    budget_amount        NUMERIC(18,2),
    domain               VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no           VARCHAR(20)   NOT NULL DEFAULT '1',
    CONSTRAINT ecos_business_scenario_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE ecos_control.ecos_business_scenario
    IS '场景主表真身（workspace 唯一写入口落点；V123/V193/V315 另有两个只停写副本：ecos_ai / ecos_cognitive）';

-- 已建库补 DR06/07/08 基线列（幂等；dev PG 中该表由 search_path 落库而来、没有这两列）
ALTER TABLE ecos_control.ecos_business_scenario ADD COLUMN IF NOT EXISTS domain     VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_control.ecos_business_scenario ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) NOT NULL DEFAULT '1';

-- 按软删 + 名称查（workspace 列表面）
CREATE INDEX IF NOT EXISTS idx_business_scenario_deleted
    ON ecos_control.ecos_business_scenario(is_deleted);
