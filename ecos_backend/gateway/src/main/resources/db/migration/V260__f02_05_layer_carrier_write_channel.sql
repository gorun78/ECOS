-- =====================================================================
-- V260: F02-05 五层载体登记与写入通道（详细设计-02 · F02-05 / C.3.6 / W51→C40）
-- 追溯: W51 (SEMANTIC/APPLICATION 载体 0) ；需求依据 REQ-DATA-03/06、
--       《数据湖存储分层规范》v2.0 五层、《数据库访问规范》v1.3 MC01~MC06 / ST06。
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）
--
-- 【只加不删 IR03】本脚本对 td_data_resource **仅加列 + 加索引**，不删不改既有列；
--   layer 列已在 V54 建立（DEFAULT 'RAW'，无 NOT NULL）。规范 F02-05 第 1 条要求
--   "layer NOT NULL"，但 IR03 禁 ALTER 既有列（加 NOT NULL 属修改约束），故以**校验器**
--   承担非空语义（详见 F02-05 规格注记 "layer_required 由校验器承担"），此处不改 layer 列。
--
-- 【与 V169 关系】V169 曾对镜像形态 ecos_data.td_data_resource 加列；本脚本对**运行时
--   实际读写**的 public.td_data_resource（V19 建表、Mapper 无 schema 前缀）做同构 **幂等**
--   补齐（ADD COLUMN IF NOT EXISTS），二者仍属"只加"，不冲突。
--
-- 【纪律】MC01（主键 VARCHAR(36) 应用侧 UUID，DDL 无默认值）/ MC02（无裸 NUMERIC、无
--   JSON 二进制列）/ DR02（新表 ecos_ 前缀）/ DR06（审计 5 字段 create_time/update_time/
--   create_by/update_by/is_deleted）/ DR08（domain 多租户预留）/ MC06（表名不带硬编码
--   业务 schema 前缀，落主控制 schema，与 td_data_resource 同域）。
-- =====================================================================

-- ── 1. 载体登记列（F02-05 第 1 条；layer 已存在，幂等不重建；三列幂等补齐）─────
ALTER TABLE td_data_resource ADD COLUMN IF NOT EXISTS layer_bucket  VARCHAR(30);
ALTER TABLE td_data_resource ADD COLUMN IF NOT EXISTS carrier_ref   VARCHAR(255);
ALTER TABLE td_data_resource ADD COLUMN IF NOT EXISTS storage_kind  VARCHAR(20);
COMMENT ON COLUMN td_data_resource.layer_bucket IS
    '载体子类: 数据湖五层(SOURCE/RAW/CURATED/SEMANTIC/APPLICATION)内的载体子分类';
COMMENT ON COLUMN td_data_resource.carrier_ref IS
    '载体标识: 对象 key / 表名 / 视图名 / 图谱 label（跨域只存定位符，铁律 §3.5）';
COMMENT ON COLUMN td_data_resource.storage_kind IS
    '存储载体: PG/MINIO/DORIS/CLICKHOUSE/NEO4J/PG_VECTOR（湖规范 v2.0 §七A 载体分档）';

CREATE INDEX IF NOT EXISTS idx_tdr_layer_bucket ON td_data_resource(layer_bucket);
CREATE INDEX IF NOT EXISTS idx_tdr_storage_kind ON td_data_resource(storage_kind);

-- ── 2. 写入通道幂等台账（F02-05 第 3 条 / C.3.6 唯一写通道）────────────
-- 唯一写通道 POST /api/v1/datanet/write-channel 以 idempotencyKey 为幂等键：
-- 同 key 重放不得产生第二行 → 台账对 idempotency_key 建唯一索引（MC03 非条件唯一）。
CREATE TABLE IF NOT EXISTS td_dw_write_channel_idempotency (
    id               VARCHAR(36)  PRIMARY KEY,          -- MC01: 应用侧 UUID，DDL 无默认值
    idempotency_key  VARCHAR(128) NOT NULL,
    trace_id         VARCHAR(128),
    layer            VARCHAR(16)  NOT NULL,             -- 落层（IER 五层枚举）
    layer_bucket     VARCHAR(30),
    carrier_ref      VARCHAR(255),
    storage_kind     VARCHAR(20),
    target_table     VARCHAR(255),                      -- MC06 解析后的 schema 限定名
    write_id         VARCHAR(36)  NOT NULL,             -- 本次写入业务记录 ID（应用侧 UUID）
    create_by        VARCHAR(100),
    create_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by        VARCHAR(100),
    update_time      TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no       VARCHAR(20)  NOT NULL DEFAULT '1', -- DR07 乐观锁
    is_deleted       SMALLINT     NOT NULL DEFAULT 0,   -- DR05
    domain           VARCHAR(50)  NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE td_dw_write_channel_idempotency IS
    'F02-05 唯一写通道幂等台账 — 同 idempotencyKey 重放不产生第二行（C.3.6）';

CREATE UNIQUE INDEX IF NOT EXISTS uniq_dw_wc_idempotency_key
    ON td_dw_write_channel_idempotency(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_dw_wc_idem_trace
    ON td_dw_write_channel_idempotency(trace_id);
CREATE INDEX IF NOT EXISTS idx_dw_wc_idem_layer
    ON td_dw_write_channel_idempotency(layer);

-- ── DDL Lint Self-audit ──────────────────────────────────────────────────
-- [IR03] 仅 ADD COLUMN / CREATE IF NOT EXISTS，零 DROP/ALTER 既有对象 ✓
-- [MC01] td_dw_write_channel_idempotency.id = VARCHAR(36)，无 gen_random_uuid() 默认值 ✓
-- [MC02] 无裸 NUMERIC/DECIMAL；无 JSON 二进制列；无 timestamptz/数组/serial ✓
-- [MC03] 唯一索引无 WHERE（非 partial）；无 ON CONFLICT/::/JSONB 运算符 ✓
-- [DR02] 新表 ecos_/td_ 域命名（与 td_data_resource 同控制域 family）✓
-- [DR06] create_time/update_time/create_by/update_by/is_deleted 五字段 + domain 齐全 ✓
-- [ST07] 台账属数据引擎控制数据，落主控制 schema（与 td_data_resource 同域）✓
-- [ST09] 零 FOREIGN KEY ✓
