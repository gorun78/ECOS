-- =============================================================================
-- V287__fc_run_add_idempotency_columns.sql
-- 预测运行表幂等键/内容指纹列补建（详细设计-09 v1.2 §十一 H-5 · F10-08 同批纪律）
--
-- [缘起] workspace 编排面 ForecastRunOrchestrationService 的幂等裁决链要求
--        findByIdempotencyKey(key) → 既有 run 视图 → ForecastRunStateMachine
--        .resolveIdempotentReuse(existing, command.contentHash) 判「同 key 同 hash 复用 /
--        同 key 异 hash 冲突」。该判定需读回两个存值真值：idempotency_key、content_hash。
--        V197 建表未含此二列，V277 只补了六要素增项（snapshot_id/formula_version/
--        reused_from_run_id/task_id），故幂等复用在数据层无处落锚 —— 唯一写通道台账
--        （V260 td_dw_write_channel_idempotency）的 write_id 是通道自生成的记录 UUID，
--        既非运行业务键也不含内容指纹，不能替代本表列（跨 schema 反查亦违 MC06 单表定位）。
--
-- [裁决] B2-② 批次自主判断：补两枚可空列，不改任何既有列语义、不动既有索引。
--        可空 = 历史行（V197 搬运自 public.ecos_scenario_run 的存量）无键可回填，
--        原样 NULL（禁伪造回填，与 V277 四列同纪律）。
--
-- [红线自查]
--   IR01/IR02 无 DROP/DELETE；只加列 + 加索引。
--   MC01 无 gen_random_uuid() 默认值（本批列均为业务键，非主键）。
--   MC02 索引键为 VARCHAR 标量列，无 JSON/TEXT 参与 WHERE 或索引。
--   MC03 普通索引（非条件索引）；唯一性刻意不设 —— 见下条理由。
--   MC06 表名 schema 限定 ecos_dw.（业务域 APPLICATION 层，R-17 ①）。
--   ST03-A 无个人数据列；ST07 不新建 schema。
--   金额无关（本批零金额列，不触 NUMERIC(p,s) 白名单条款）。
--   R-15 枚举 CHECK 本批不建（与 V197 §「status/run_type CHECK 本批不建」同纪律，不反转）。
--
-- [为什么是普通索引而非唯一索引] 幂等冲突语义 = 同 idempotency_key 允许并存多行
--   （异 content_hash 时状态机要判 409 冲突并点名既有行）；唯一索引会在写入侧直接
--   抹掉「冲突可判定」这一事实，把 409 变成数据库报错。并发去重仍由唯一写通道台账
--   （uniq_dw_wc_idempotency_key，V260）兜底，本层索引只服务查询定位。
-- =============================================================================

-- ── 1. 增列（幂等性：ADD COLUMN IF NOT EXISTS，重复执行零报错、零重复）──────
ALTER TABLE ecos_dw.ecos_application_forecast_run
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS content_hash    VARCHAR(64);

-- ── 2. 列注释登记 ────────────────────────────────────────────────────────
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.idempotency_key IS
    '幂等键（详设-09 H-5：与唯一写通道 V260 台账 idempotency_key 同值，本表侧供 findByIdempotencyKey 直接定位既有 run；历史搬运行为 NULL，禁伪造回填）';
COMMENT ON COLUMN ecos_dw.ecos_application_forecast_run.content_hash IS
    '内容指纹（六要素+范围规范化后的确定性摘要；同键异指纹 ⇒ 409 冲突判定依据，ForecastRunStateMachine.resolveIdempotentReuse 读回比对，禁事后改写）';

-- ── 3. 索引（普通索引 MC03；is_deleted 并入键代偿条件索引缺位，与 V197 §2 同法）──
CREATE INDEX IF NOT EXISTS idx_afcr_idem
    ON ecos_dw.ecos_application_forecast_run (idempotency_key, is_deleted);

-- ── 4. 登记 ─────────────────────────────────────────────────────────────
-- [下一枚] V288
-- [实跑状态] 本文件仅落单源目录，未在 dev PG 实跑（dev PG 现无 ecos_dw.ecos_application_forecast_run
--            表，V197/V277 同批未应用 ⇒ 单独应用本脚本必失败）。实跑授权与批次见
--            docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md。
-- [幂等性] IF NOT EXISTS 三重守卫，重复执行零报错、零重复列/索引。
-- [回滚面] 只加不删（IR01）；如需回退走「备份 + 应用侧读写先切走 + 人工审批」三步，本批不提供 DROP。
