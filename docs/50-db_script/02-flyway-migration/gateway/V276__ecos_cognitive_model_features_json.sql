-- ============================================================
-- V276__ecos_cognitive_model_features_json.sql
-- 详设-05 E.3 / V192 幂等重放（列面）：
--   `ecos_cognitive.ecos_cognitive_model` 实库形态 = V200 schema 治理时
--   由 public 旧表搬入（`features JSONB`），V192 的新表形状（`features_json TEXT`）
--   从未实跑（§14.4 执行边界=只落脚本）⇒ ModelRegistryDao.listModels 的
--   `features_json AS features` 点名列报 BadSqlGrammarException，
--   GET /api/v1/cognitive/models 返回 500 ECOS-COG-504（2026-10-11 live 实证，
--   traceId 86a5f663-3fc5-4272-b66f-0d76564aa536）。
--
-- 方案（IR03 只加不删）：补 `features_json TEXT` 列并从旧 `features` 幂等回填；
--   旧 JSONB 列只停写不删除（knownLegacy），读路径单源 = features_json（MC02 形态）。
--   V261/V268 已在实库追加其余列（registry_state/gate_*/lifecycle_state 等），本批只裁
--   V192 列面中实缺的 features_json，其余（domain/version_no）DAO 不读、不在本批范围。
-- ============================================================

ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS features_json TEXT;

UPDATE ecos_cognitive.ecos_cognitive_model
   SET features_json = features::text
 WHERE features_json IS NULL
   AND features IS NOT NULL;

COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.features_json IS '特征清单/超参数 TEXT 形态（V192/MC02；旧 features JSONB 列停写保留，本列读写单源）';

-- ============================================================
-- DDL Lint Self-audit
-- [DR01] schema 限定 ✓  [MC02] 新增列为 TEXT 非 JSON ✓
-- [IR03] 只加不删，旧 features 列不动 ✓  [幂等] ADD IF NOT EXISTS + WHERE features_json IS NULL ✓
-- ============================================================
