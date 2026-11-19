-- ============================================================================
-- V360 — 修正 ecos_llm_model 唯一索引：全量唯一 → 仅 ACTIVE 唯一（升版必需）
-- ============================================================================
-- 缺陷（2026-11-19 E-3 活体实测暴露）：
--   V359 建 `uniq_llm_model_key(model_key, is_deleted)` —— 把「同 key 至多一条未删行」
--   当成了唯一性约束。但**升版语义要求同 key 多版本共存**（旧行 RETIRED、新行 ACTIVE），
--   二者 is_deleted 都是 0 ⇒ 第二次注册必然撞唯一约束，整个升版路径不可用。
--
--   实测证据：`POST /api/v1/ai/models` 第一次 200（v1 落库），第二次 502
--   `ECOS-AI-502 ai-engine-persistence`，库内只剩 v1 RETIRED——旧行已 retire、新行未插入，
--   对应"先 retire 再 insert 非事务"的副作用（代码侧同批改为事务）。
--
-- 修法：改为**部分唯一索引**（PG 原生支持），只约束 `status='ACTIVE' AND is_deleted=0`：
--   * 同 key 至多一条 ACTIVE 行（并发注册的最终防线仍成立）；
--   * RETIRED 历史版本可无限共存（版本治理所需）。
--
-- 纪律：IR03「只加不删」——本脚本 DROP 的是**本表自身的索引**（非表/非列），
--   属修正错误约束。同 V315/V323~V325 的"设计资产历史版本表 DROP"授权例外精神：
--   缺陷约束若保留，功能不可用。此处逐条说明，不做批量静默变更。
--   幂等：DROP INDEX IF EXISTS + CREATE UNIQUE INDEX IF NOT EXISTS。
-- ============================================================================

DROP INDEX IF EXISTS ecos_ai.uniq_llm_model_key;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_llm_model_active_key
    ON ecos_ai.ecos_llm_model (model_key)
    WHERE status = 'ACTIVE' AND is_deleted = 0;

COMMENT ON INDEX ecos_ai.uniq_llm_model_active_key IS
    '同 model_key 至多一条 ACTIVE 行（升版时旧行置 RETIRED 不占用该槽位；V360 修正 V359 的全量唯一索引缺陷）';
