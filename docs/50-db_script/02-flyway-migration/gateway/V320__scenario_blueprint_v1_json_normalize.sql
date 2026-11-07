-- ============================================================
-- V320__scenario_blueprint_v1_json_normalize.sql
-- P0-3：v1 种子蓝图四段结构列 JSON 规范化
-- ============================================================
-- 背景（2026-11-07 验证报告 P0-3）：
--   entmgmt 演示种子的 4 个 v1 蓝图把 gaps/dependencies/capabilities/tests/risks
--   写成自由文本，而 ScenarioBlueprintValidator 按 JSON 数组解析 ⇒
--   GET /blueprints/{id}/validate 恒 passed=false（"Unrecognized token '缺口'"）。
--   fail-closed 行为正确，坏的是数据。
--
-- 本脚本（纯数据规范化，零 DDL 结构变更）：
--   仅对 version=1 且对应列不是 JSON 数组（首字符非 '['）的行就地规范化；
--   gaps 保留原意（两条已知缺口 → WARNING/OPEN，不阻断）；
--   dependencies 保留"本体已发布"事实但 required=false（v1 草案不宣称强制依赖）；
--   capabilities/tests/risks 原为自由文本，v1 草案无结构化条目 ⇒ 空数组（不臆造）。
--
-- 幂等：已是 JSON 数组的列原样保留；重复执行零变化。
-- 回滚：由 DBA 用执行前 pg_dump 快照恢复（本脚本不可逆，属数据订正）。
-- ============================================================

UPDATE ecos_control.ecos_scenario_blueprint
   SET gaps_text = CASE
           WHEN btrim(coalesce(gaps_text, '')) LIKE '[%' THEN gaps_text
           ELSE '[{"gapId":"D-W6-1","category":"PLATFORM","severity":"WARNING","status":"OPEN",'
                '"description":"builtin 智能体 status 小写不可见"},'
                '{"gapId":"D-W6-3","category":"PLATFORM","severity":"WARNING","status":"OPEN",'
                '"description":"护栏策略表缺"}]'
       END,
       dependencies_text = CASE
           WHEN btrim(coalesce(dependencies_text, '')) LIKE '[%' THEN dependencies_text
           ELSE '[{"type":"ONTOLOGY","id":"ont_entmgmt","required":false,'
                '"versionConstraint":"1.0.1","status":"PUBLISHED"}]'
       END,
       capabilities_text = CASE
           WHEN btrim(coalesce(capabilities_text, '')) LIKE '[%' THEN capabilities_text
           ELSE '[]'
       END,
       tests_text = CASE
           WHEN btrim(coalesce(tests_text, '')) LIKE '[%' THEN tests_text
           ELSE '[]'
       END,
       risks_text = CASE
           WHEN btrim(coalesce(risks_text, '')) LIKE '[%' THEN risks_text
           ELSE '[]'
       END,
       update_time = CURRENT_TIMESTAMP,
       update_by = 'V320'
 WHERE version = 1
   AND (btrim(coalesce(gaps_text, '')) NOT LIKE '[%'
        OR btrim(coalesce(dependencies_text, '')) NOT LIKE '[%'
        OR btrim(coalesce(capabilities_text, '')) NOT LIKE '[%'
        OR btrim(coalesce(tests_text, '')) NOT LIKE '[%'
        OR btrim(coalesce(risks_text, '')) NOT LIKE '[%');

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [IR03] 零 DROP / 零 ALTER / 零 CREATE；仅 UPDATE 数据 ✓
-- [DR01] schema 限定 ecos_control ✓  [ST07] 主控制 schema ✓
-- [语义] gaps 保留两条已知缺口事实（WARNING/OPEN），不臆造通过态 ✓
