-- ============================================================================
-- V370 — 本体规则表缺列补齐（2026-11-26 场景规则消费活体修复）
-- ============================================================================
-- 问题（活体取证）：buszhi 独立服务 `GET /api/v1/ecos/rules` 500：
--   `SELECT * FROM ecos_ontology_rule ...` 后映射 `rule_carrier` 列缺失
--   （PSQLException: ResultSet 中找不到栏位名称 rule_carrier）。表为 V1 时代存量，
--   OntologyRuleVO 已声明 ruleCarrier/ruleLayer/scope/gitRef 但列未迁。
-- 处置：只加列（可空），不动既有列与数据；补后 buszhi 行映射可解析，规则中心恢复可读。
--
-- 纪律：IR03 只加不删；DR06/07/08；ST07（本体域既有表就地补列）；不建 FK（ST09）。
-- ============================================================================

ALTER TABLE ecos_ontology.ecos_ontology_rule
    ADD COLUMN IF NOT EXISTS rule_carrier VARCHAR(32),
    ADD COLUMN IF NOT EXISTS rule_layer   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS scope        VARCHAR(128),
    ADD COLUMN IF NOT EXISTS git_ref      VARCHAR(128);

COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.rule_carrier IS
    '规则载体（V370；OntologyRuleVO.ruleCarrier，存量表缺列补齐）';
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.rule_layer IS
    '规则层级（V370；OntologyRuleVO.ruleLayer）';
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.scope IS
    '适用范围（V370；OntologyRuleVO.scope）';
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.git_ref IS
    'Git 溯源引用（V370；OntologyRuleVO.gitRef）';
