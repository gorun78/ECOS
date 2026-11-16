-- ============================================================
-- V333__cognitive_belief_shadow_read_columns.sql — 认知 belief 镜像读列补齐（D74）
-- 追溯: 2026-11-14 B2 认知引擎逐功能验证（V-COG-054 资产提案/迁移）实测
--   GET /api/v1/cognitive/assets/bel-xxx/resolve → 500 ECOS-COG-504
--   根因: BeliefDao.findLifecycleStateById / MentalReviewDao.beliefTimelines 等仍用裸表名
--         ecos_cognitive_belief；本库 search_path 首位是 ecos_control，解析命中
--         ecos_control.ecos_cognitive_belief（V191 双写期遗留镜像），该镜像缺
--         lifecycle_state / probability / prob_kind / calibration_ref / model_ref /
--         scope_ref / as_of_time / distribution_json / mind_id / scenario_id /
--         subject_type / subject_id 共 12 列 ⇒ BadSqlGrammarException
--         「column "lifecycle_state" does not exist」。
--   权威读端: ecos_cognitive.ecos_cognitive_belief（V191 归位 + V262/V268/V298 补列，
--         与实现侧 SELECT 列清单逐列可解析）。
--   同族前例: V327 已按同一处置口径补齐 ecos_cognitive_hypothesis / ecos_ontology_* 镜像；
--         本脚本是其漏项（belief 表）的补齐，属同一条缺陷族。
-- 处置: 只加不删（IR03）。仅 ADD COLUMN IF NOT EXISTS 把 ecos_control 镜像补齐到读端口径，
--       禁 DROP / 禁数据搬迁（镜像存量 2 行属治理遗留，搬迁属另批授权项）。
--       列类型/长度/默认值逐列对齐 ecos_cognitive 真身 DDL，避免二次漂移。
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02(手动 psql 执行) / IR03(只加不删)
-- ============================================================

-- ── ecos_control.ecos_cognitive_belief：补齐 V262/V268/V298 读端列 ──
ALTER TABLE IF EXISTS ecos_control.ecos_cognitive_belief
    ADD COLUMN IF NOT EXISTS distribution_json TEXT           NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS probability       NUMERIC(6,5),
    ADD COLUMN IF NOT EXISTS prob_kind         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS calibration_ref   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS model_ref         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS scope_ref         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS as_of_time        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS lifecycle_state   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS mind_id           VARCHAR(36),
    ADD COLUMN IF NOT EXISTS scenario_id       VARCHAR(36),
    ADD COLUMN IF NOT EXISTS subject_type      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS subject_id        VARCHAR(36);

COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.lifecycle_state IS
    'V333 补齐（D74）：读取端 CognitiveAssetLifecycleService.resolve 依赖；权威实例 = ecos_cognitive.ecos_cognitive_belief';
COMMENT ON COLUMN ecos_control.ecos_cognitive_belief.distribution_json IS
    'V333 补齐（D74）：BeliefStore 读端列；与 V191 真身同名同型（TEXT，MC02 合规形态）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 仅 ADD COLUMN IF NOT EXISTS；回滚 = 应用侧忽略新列（IR03 禁脚本内 DROP）。
-- 幂等性：全列 ADD IF NOT EXISTS ⇒ 重复执行零报错。
