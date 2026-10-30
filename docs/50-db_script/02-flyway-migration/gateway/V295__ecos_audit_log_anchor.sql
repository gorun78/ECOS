-- ============================================================
-- V295__ecos_audit_log_anchor.sql
-- 附录A 接口总表第 6 条（getAuditLogDetail 契约载荷面）的数据面单源锚定：
--   ecos_audit_log 表 + 哈希链三列（prev_hash/curr_hash/hash_algorithm），
--   schema 锚定 public（库面实查落点，与消费方 AuditDao 裸名 SQL 同轨）。
-- ============================================================
--
-- ── 缺陷事实（live 实证，2026-10-29 取证锚）────────────────────
--   ① 差距台账 §四(二) 附录A 缺失清单第 6 条原判「纯代码 P1 批不擅自
--      固化未入单源的表列」，本脚本即对该表列的首枚单源固化动作。
--   ② 表真身落点：单源目录此前零 CREATE ⇒ 2026-10-29 实跑本脚本前
--      先实查全体 8 schema（`existence` 探针 0 行），哈希链/幂等
--      消费者写面在 dev 库本就无处落地——本脚本 `CREATE` 真执行
--      （非 NO-OP），把表连同哈希链三列首次落库。脚本注释头段
--      2026-10-29 勘订（原稿误作「repo 已在位」，代入后已改）。
--   ③ 单源目录 0 枚 CREATE 载体（grep 'CREATE TABLE.*ecos_audit_log'
--      02-flyway-migration 0 命中）⇒ 新库按单源目录重建时该表缺席，
--      AuditHashChainService（@Service 常驻 bean，P1-3 哈希链）与
--      GET /api/v1/security/audit/{id}（getAuditLogDetail）的整条
--      哈希链面在 greenfield 直接死亡。
--   ④ 列清单单源 = 库面实查（2026-10-29 information_schema：
--      id BIGSERIAL PK、event_type、user_id、username、operation、
--      action、resource、entity_type、entity_id、detail、ip_address、
--      result、created_at、prev_hash、curr_hash、hash_algorithm），
--      其中 id/user_id/username/operation/entity_type/entity_id/
--      created_at 六列与 AuditDao.findAuditRowContent/findHashChainRows
--      逐名一致；user_id/ip_address/result/detail 四列与
--      AuditDao.insertAuditLogIdempotent 写清单逐名一致；
--      event_type/action/resource/prev_hash/curr_hash/hash_algorithm
--      六列与 AuditEventProducer/Kafka 消费者幂等面及
--      updateHashFields 写清单逐名一致。
--   ⑤ 哈希链三列（prev_hash VARCHAR(64) / curr_hash VARCHAR(64) /
--      hash_algorithm VARCHAR(16)，链算法 SHA-256 ⇒ 十六进制 64 位，
--      64 长度取单源承载而非编译期常量施加）＝附录A 第 6 条契约
--      载荷 prevHash/currentHash 的唯一数据面落点，本脚本使其
--      首次进入 DDL 单源目录。
--
-- ── 本脚本动作（最小锚定，零数据面动作）──────────────────────
--   1) `CREATE TABLE IF NOT EXISTS public.ecos_audit_log (...)`
--      —— schema 锚定单源补登 + dev 库真身落库（dev 首轮＝真 CREATE）。
--      重放语义：表已建库再跑 = NO-OP（IF NOT EXISTS 跳过）；
--      新库按单源目录重放时本脚本保证表与哈希链三列直接在位，
--      哈希链面不再依赖库外手建。
--   2) dev/存量库重放零副作用：IF NOT EXISTS 全跳过，
--      零 ALTER/零 DROP/零 RENAME，不触碰任何存量列/行/序列。
--
-- ── 纪律核对（IR01/DR01/ST03/ST07/MC01）──────────────────────
--   IR01 只加不删: 仅一枚 CREATE TABLE IF NOT EXISTS + COMMENT，
--      零 ALTER/DROP/RENAME；重放 NO-OP，存量库面列/行不变。
--   IR02 手动 psql: dev 侧执行通道 = docker exec psql（Flyway
--      禁用全库口径），本脚本 2026-10-29 实跑（真执行，exit 0）。
--   DR01 schema 限定: 显式 `public.`，与消费方 AuditDao 裸名 SQL
--      同轨（JDBC currentSchema 链 ecos_control,…,ecos_dw,public
--      实测含 public ⇒ 裸名解析先落 public 本体，与 V293 对
--      td_audit_log 的 ecos_control 锚定相反方向：本表真身即在
--      public，锚定 public = 锚定真身落点，不制造分叉）。
--   ST03 建表字段: 零 JSONB/零 BOOLEAN（id BIGSERIAL PK 为存量
--      库面形态，本脚本只做锚定登记、不新造列型）；
--      VARCHAR 主键不适用（存量 BIGSERIAL，MC01 主键口径的
--      存量表锚定面例外，不擅自改型——改型＝IR01 违例）。
--   ST07 封闭枚举: `public` 为 8 schema 封闭枚举成员，合规；
--      不新造 schema（本批零新 schema）。
--   DR03 禁止索引: 本脚本零 INDEX（存量表在位索引不归本脚本管）。
--   P0-10 关联: 本枚是附录A 第 6 条「数据面单源零 CREATE」断链的
--      最小锚定动作——先让表+链列进单源，端点侧（代码侧同批）
--      才有真值锚可读，而不是继续「纯代码批不敢固化」的死锁。
--
-- ── 与既有脚本的边界 ─────────────────────────────────────────
--   · V293 锚定的是 `ecos_control.td_audit_log`（sysman 属主
--      AuditLog-sql.xml 消费面，Cg 类审计行）——与本脚本的
--      `public.ecos_audit_log`（Kafka 消费者幂等落库 + 哈希链面）
--      是两张不同的表，命名不冲突，各自锚定各自真身落点。
--   · V294 的 fc_* 四表（ecos_dw 域）与本脚本零交集。
--
-- ── 状态 ─────────────────────────────────────────────────────
--   2026-10-29 已对 dev PG 实跑：本脚本真执行（CREATE TABLE 落地，
--      非 NO-OP——该表此前不在 dev），exit 0；16 列 + COMMENT 全落库。
--   本脚本效力面 = ① dev 库哈希链面从「无表可写」到「单源在位」；
--   ② 单源目录完整重放（新库）时该表直接落成 `public.ecos_audit_log`
--   带哈希链三列，与库面设备形态对齐。
--   限岸红线：本脚本零数据变更（零 INSERT/UPDATE/ALTER），仅一枚
--   空表 CREATE + COMMENT；存量行/序列（别的 schema）不变。

CREATE TABLE IF NOT EXISTS public.ecos_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(128),
    user_id         VARCHAR(64),
    username        VARCHAR(64),
    operation       VARCHAR(128),
    action          VARCHAR(128),
    resource        VARCHAR(256),
    entity_type     VARCHAR(64),
    entity_id       VARCHAR(64),
    detail          TEXT,
    ip_address      VARCHAR(64),
    result          VARCHAR(16),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    prev_hash       VARCHAR(64),
    curr_hash       VARCHAR(64),
    hash_algorithm  VARCHAR(16)
);

COMMENT ON TABLE public.ecos_audit_log IS '审计日志（Kafka 消费者幂等落库载体 + P1-3 哈希链）——附录A 第6条 getAuditLogDetail 契约载荷 prevHash/currentHash 的唯一数据面；V295 单源锚定补登（2026-10-29 dev 实跑真执行，列清单取库面实查）';
