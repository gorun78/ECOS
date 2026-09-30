-- V170 (卷02 §E.5 / D-17): ecos_pipeline_definition / ecos_pipeline_execution 建表 DDL 补写进单源目录（关闭 W58）
-- 追溯: W58→C47（建表只在 ecos-sql/，单源缺；db-migration-lint 配对 + 空库重建演练）；需求依据 REQ-DB-02（铁律 §3.1 DDL 单源）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-1 a（管道 DAG 权威归属 ecos_data）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 原文来源: ecos-sql/postgresql/03_ecos_data.sql L145~180（本脚本逐列对齐其承流形态；**不回改 ecos-sql 侧文件**）。
-- 合规化改写（其余保持现网形态以免 lint 配对/空库重建漂移）:
--   ① 时间默认 NOW() → CURRENT_TIMESTAMP（MC03 口径统一，与现网 now() 等价）。
--   ② definition 列 2026-09-30 实测校订：现网为 `jsonb DEFAULT '{}'`（information_schema 只读取证），
--      原脚本直接写成 TEXT 会使空库重建与生产形态不一致 → CREATE 块按现网 jsonb 镜像（knownLegacy，MC02 只约束新表），
--      MC02 合规形态以下方 ALTER 的 `definition_json TEXT` 承载，读写切换属后续代码批次（§14.4 未授权项）。
-- 现网既有形态保留项（不视为违规新增）: 主键沿用 VARCHAR(64)（现网既存，MC01 只约束新表，补写禁改形态）；
--   审计列沿用 created_at/updated_at 既有列名（E.3 漂移条款同法），DR06~08 规范列以**可空新列**补齐，Mapper 新写用规范列。
-- tenant_id: 保持现网 VARCHAR(64) 可空（补写与现网/lint 配对优先；NOT NULL 收紧属后续授权批次）。
-- FK 处置: ecos-sql 侧同 schema 外键 fk_data_pexec_def 不在本脚本回写（无条件 ADD CONSTRAINT 非幂等；ST09 关系由应用层保证）。
-- 幂等: CREATE ... IF NOT EXISTS + ADD COLUMN IF NOT EXISTS，重跑无副作用。回滚说明: 补写型脚本，执行即与现网对齐；异常时停写新列即可。

-- ── 1. 管道定义表 ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_definition (
    id          VARCHAR(64)  PRIMARY KEY,               -- 现网既有主键形态（见头注保留项）
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    definition  JSONB        DEFAULT '{}',            -- 现网镜像形态（knownLegacy：MC02 只约束新表；合规 TEXT 列见 §3 ALTER）
    status      VARCHAR(32)  DEFAULT 'DRAFT',
    tenant_id   VARCHAR(64),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  ecos_data.ecos_pipeline_definition IS '管道定义表（JSON DAG 唯一承流模型，R-2 a）— 单源补写 V170';
CREATE INDEX IF NOT EXISTS idx_data_pdef_status ON ecos_data.ecos_pipeline_definition(status);
CREATE INDEX IF NOT EXISTS idx_data_pdef_tenant ON ecos_data.ecos_pipeline_definition(tenant_id);

-- ── 2. 管道执行表 ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ecos_data.ecos_pipeline_execution (
    id             VARCHAR(64)  PRIMARY KEY,            -- 现网既有主键形态
    pipeline_id    VARCHAR(64)  NOT NULL,
    status         VARCHAR(32)  DEFAULT 'PENDING',
    started_at     TIMESTAMP,
    finished_at    TIMESTAMP,
    error_message  TEXT,
    rows_processed BIGINT       DEFAULT 0,              -- 计数列 BIGINT（非金额，无参裸数值规避）
    tenant_id      VARCHAR(64),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  ecos_data.ecos_pipeline_execution IS '管道执行表 — 单源补写 V170（pipeline_id 关联由应用层保证，ST09 不建跨表外键）';
CREATE INDEX IF NOT EXISTS idx_data_pexec_pipeline ON ecos_data.ecos_pipeline_execution(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_data_pexec_status   ON ecos_data.ecos_pipeline_execution(status);
CREATE INDEX IF NOT EXISTS idx_data_pexec_tenant   ON ecos_data.ecos_pipeline_execution(tenant_id);

-- ── 3. DR06~DR08 规范列 + MC02 合规 JSON 列补齐（可空新列，不改既有列名/类型/读取语义，E.3 漂移同法）──
-- 【2026-09-30 四补收口】原稿此处只补 create_by/update_by/is_deleted/domain/version_no 五列，漏 DR06 的
--   `create_time`/`update_time`（现网 legacy 同义列为 `created_at`/`updated_at`，二者在库内实存）。
--   依据卷02 §E.3 末（分册 02:634）批准口径："现有 created_at/created_by/version **不 ALTER**（IR03），
--   改为**新增**规范列 create_time/update_time/create_by/update_by/version_no/domain（可空）" ⇒ 七列齐补；
--   同批 V164（rls/cls）与 V173（workflow/workflow_approval）均已补 time 列，本脚本属少补。
--   门禁标识 = W60/C49 `AuditColumnPresenceTest`（缺列必判 FAIL）。补列代价：仅脚本文字，未实跑（§14.4）。
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS definition_json TEXT;   -- MC02 合规形态（与现网 jsonb `definition` 双写过渡，读写切换归代码批次）
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_data.ecos_pipeline_definition ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20);
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_data.ecos_pipeline_execution  ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20);

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全部 ecos_data. 限定 ✓（无裸表名）  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] 现网 `definition`（无 _json 后缀）按镜像保留 = knownLegacy；新增 MC02 合规列 definition_json TEXT（后缀+类型双合规）
-- [DR05] is_deleted SMALLINT ✓  [DR06~DR08] 规范列以可空/默认新列补齐（既有列名不动，IR03）✓
-- [MC01] 主键形态保留现网 VARCHAR(64)（补写非新表；无自增键、无默认值方言函数）✓
-- [MC02] 新表才受 MC02 约束；本表为现网补写，jsonb 列镜像保留 + definition_json TEXT 承接合规读写（无裸 NUMERIC/无参 DECIMAL）
-- [MC03] 索引全部非条件；无库内策略语句、无分区、无数组列、无带时区时间类型、无裸 cast；时间默认 CURRENT_TIMESTAMP ✓
-- [ST07] 管道 DAG 落数据引擎 schema ecos_data（E.1 R-1 a + R-2 a）✓  [ST09] 零 FOREIGN KEY ✓
-- [IR03] 只补建不删改；与 ecos-sql/postgresql/03 列形态一致性由 db-migration-lint 检查③配对校验（E.5 lint 纪律）✓
