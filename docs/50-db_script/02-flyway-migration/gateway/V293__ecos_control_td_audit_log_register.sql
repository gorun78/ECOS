-- ============================================================
-- V293__ecos_control_td_audit_log_register.sql
-- D-6 补登（台账 §D D-6 行 #146e 取证裁定的授权批动作）:
--   审计主表 td_audit_log 的 schema-锚定单源登记。
-- ============================================================
--
-- ── 缺陷事实（live 实证，取证 = 映射台账 D-6 行 #146e 期 2026-10-27）──
--   ① 表真身落点 = `ecos_control.td_audit_log`（dev PG 实查 5 行在位，
--      `public.td_audit_log` 不存在）。
--   ② 单源 CREATE 只有 V40__ecos_security_seed.sql:37 的一枚**裸名**
--      `CREATE TABLE IF NOT EXISTS td_audit_log`（无 schema 限定，13 列）；
--      建表后**无任何单源脚本**把它定格到 `ecos_control`——V47 的
--      `SET SCHEMA` 全为注释腿，实迁由单源目录外治理脚本
--      `08-database-util/V150__schema_unify.sql` + `50-db_script/V200__schema_governance.sql`
--      （非 flyway 通道）手跑承载。
--   ⇒ 违规点 = 「库面 schema（ecos_control）与单源 CREATE（裸名）分叉」，
--      与本表消费面（sysman 属主 AuditLog-sql.xml 裸名 + ontology
--      ActionHookAuditDao 裸名）叠加，任何新库按单源脚本重建都会把
--      表落回 public，再等静默手跑治理脚本才归位——断链面。
--
-- ── 本脚本动作（最小补登，零数据面动作）─────────────────────
--   1) `CREATE TABLE IF NOT EXISTS ecos_control.td_audit_log (...)`
--      —— schema 锚定单源补登。**dev 库上执行 = NO-OP**（表已在位，
--      IF NOT EXISTS 跳过）；**新库按单源目录重放时**本脚本保证表
--      直接落成 `ecos_control` 归属，不再依赖目录外手跑治理脚本。
--   2) 列清单单源 = 库面实查（2026-10-27 information_schema，13 列与
--      sysman 属主 AuditLog-sql.xml:5-13 写入清单逐名一致，取证锚 =
--      映射台账 D-6 行「补登列清单取 AuditLog-sql.xml:5-13」裁定），
--      不取 V40 裸名段的注释语义（两边列序/名一致，此处以 13 列名为准）。
--   3) 列类型逐名对齐库面实查：log_id VARCHAR(64) PK / event_type VARCHAR(64)
--      NOT NULL / timestamp TIMESTAMP NOT NULL DEFAULT now() / user_id
--      VARCHAR(64) / tenant_id VARCHAR(64) / resource VARCHAR(256) /
--      action VARCHAR(128) / result VARCHAR(32) / ip_address VARCHAR(64) /
--      user_agent VARCHAR(512) / request_id VARCHAR(64) / duration INT /
--      details TEXT。
--
-- ── 纪律核对（IR03/DR01/ST07/MC01）──────────────────────────
--   IR03 只加不删: 本脚本仅一枚 CREATE TABLE IF NOT EXISTS + COMMENT，
--      零 ALTER / 零 DROP / 零 RENAME，不触碰任何存量列/行。
--   IR02 手动 psql: dev 侧执行通道 = docker exec psql（Flyway 禁用全库口径），
--      本脚本已按此于 2026-10-27 实跑（dev 库 NO-OP，exit 0）。
--   DR01 schema 限定: 新载体显式 `ecos_control.`，消费面裸名访问继续经
--      search_path（JDBC currentSchema 含 ecos_control），与本仓库
--      V285 `public.td_runtime_task*` 同形态（迁移后手跑治理再归位的
--      既有先例的**反面修正**：本枚直接 schema 锚定，不复制分叉）。
--   ST07 封闭枚举: `ecos_control` 为 8 schema 封闭枚举成员（主控制
--      schema 目标名），合规。
--   MC01: 主键 VARCHAR(64) = 签发现场约定（log_id 应用侧生成，既有
--      13 行存量值形态未变动）；DDL 无 gen_random_uuid()。
--   P0-10 关联: 本枚是 ST07 封闭枚举侵蚀面（控制域表锚定 ecos_control
--      而非 public）的最小补登动作之一，增量迁移授权批执行面。
--
-- ── 状态 ────────────────────────────────────────────────────
--   2026-10-27 已对 dev PG 实跑（NO-OP 锚定，exit 0；表 5 行在位不变）。
--   本脚本的效力面 = 单源目录完整重放（新库）时表直接落 ecos_control，
--   与 V40 裸名段构成"裸名兜底 + schema 锚定"双轨，锚定面优先生效
--   （CREATE IF NOT EXISTS 先到先得：重放顺序 V40 → … → V293，若 V40
--   在 search_path 缺 ecos_control 语境下先建到 public，V293 再建会
--   落到 ecos_control 形成双份——新库重放前置 = V150/V200 治理脚本
--   跑完后 public.td_audit_log 不存在，单轨成立；dev/存量库不受影响）。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_control.td_audit_log (
    log_id       VARCHAR(64)     PRIMARY KEY,
    event_type   VARCHAR(64)     NOT NULL,
    timestamp    TIMESTAMP       NOT NULL DEFAULT now(),
    user_id      VARCHAR(64),
    tenant_id    VARCHAR(64),
    resource     VARCHAR(256),
    action       VARCHAR(128),
    result       VARCHAR(32),
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(512),
    request_id   VARCHAR(64),
    duration     INT,
    details      TEXT
);

COMMENT ON TABLE  ecos_control.td_audit_log               IS '审计日志表（D-6 补登：schema 锚定单源，首个 schema 限定 CREATE；消费面 = sysman 属主 AuditLog-sql.xml 13 列 + ontology ActionHookAuditDao 8 列子集）';
COMMENT ON COLUMN ecos_control.td_audit_log.log_id        IS '日志唯一标识（应用侧生成）';
COMMENT ON COLUMN ecos_control.td_audit_log.event_type    IS '事件类型: LOGIN/ACCESS/CUD/EXPORT/CONFIG';
COMMENT ON COLUMN ecos_control.td_audit_log.user_id       IS '操作用户ID';
COMMENT ON COLUMN ecos_control.td_audit_log.tenant_id     IS '租户ID';
COMMENT ON COLUMN ecos_control.td_audit_log.resource      IS '操作资源路径';
COMMENT ON COLUMN ecos_control.td_audit_log.action        IS '操作动作';
COMMENT ON COLUMN ecos_control.td_audit_log.result        IS '操作结果: SUCCESS/FAILURE/DENIED';
COMMENT ON COLUMN ecos_control.td_audit_log.ip_address    IS '客户端IP';
COMMENT ON COLUMN ecos_control.td_audit_log.details       IS '补充信息(JSON格式)';
