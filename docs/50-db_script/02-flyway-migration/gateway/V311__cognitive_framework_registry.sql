-- ============================================================
-- V311__cognitive_framework_registry.sql — 认知框架注册表（详细设计-认知引擎 v1.0 §5/§9.1）
-- 追溯: c-design/详细设计-认知引擎.md §5.1 框架定义字段、§5.2 首批四框架、
--       §9.1 Framework Registry、§14 CE-03 版本不可变、CE-04 硬约束优先
-- 归属: C 层过程治理元数据（如何组织认知过程），不是业务事实、不是 K 类知识副本；
--       不新增 cognitive_context，不新增认知业务事实表（README §2 红线）。
-- 落点: ecos_cognitive（ST07 五引擎 schema 封闭枚举内；同 ecos_cognitive_model 先例）
-- 只写红线:
--   * 版本不可变由应用层守卫（PUBLISHED 行禁 UPDATE 内容），DDL 不建状态 CHECK（R-15）；
--   * 唯一键 (framework_id, version, is_deleted)；
--   * content_hash = 规范化 content_json 的 SHA-256，发布时冻结；
--   * 种子四框架 DRAFT 注册，发布须走应用层发布门（不在此处伪造 PUBLISHED）。
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_framework (
    id                          VARCHAR(36) PRIMARY KEY,
    framework_id                VARCHAR(64) NOT NULL,
    version                     VARCHAR(32) NOT NULL,
    name                        VARCHAR(128) NOT NULL,
    category                    VARCHAR(64) NOT NULL,
    purpose                     TEXT,
    status                      VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    applicability_json          JSONB NOT NULL DEFAULT '{}'::jsonb,
    preconditions_json          JSONB NOT NULL DEFAULT '[]'::jsonb,
    input_schema_ref            VARCHAR(200),
    output_schema_ref           VARCHAR(200),
    steps_json                  JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_capabilities_json  JSONB NOT NULL DEFAULT '[]'::jsonb,
    validation_profile_ref      VARCHAR(200),
    budget_policy_ref           VARCHAR(200),
    stop_conditions_json        JSONB NOT NULL DEFAULT '[]'::jsonb,
    governance_json             JSONB NOT NULL DEFAULT '{}'::jsonb,
    contract_resolvable         SMALLINT NOT NULL DEFAULT 0,
    content_json                JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash                VARCHAR(128) NOT NULL,
    tenant_scope                VARCHAR(64),
    publish_time                TIMESTAMP,
    create_time                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by                   VARCHAR(100),
    update_by                   VARCHAR(100),
    is_deleted                  SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_v311_cog_framework_ver
    ON ecos_cognitive.ecos_cognitive_framework (framework_id, version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_v311_cog_framework_status
    ON ecos_cognitive.ecos_cognitive_framework (status, category, is_deleted);

COMMENT ON TABLE ecos_cognitive.ecos_cognitive_framework IS
    '认知框架注册表（详细设计-认知引擎 §5/§9.1）：版本化过程治理元数据；PUBLISHED 版本不可原地修改';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.status IS
    'DRAFT / VALIDATING / PUBLISHED / DEPRECATED / DISABLED（单源 CognitiveFrameworkStatus）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.contract_resolvable IS
    '输入/输出 Schema 与能力引用是否可解析（0/1；策略选择硬约束输入）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_framework.content_hash IS
    '发布冻结的内容摘要（SHA-256），用于版本可追溯与篡改检测';

-- 首批四框架（详细设计-认知引擎 §5.2；DRAFT 注册，发布走应用层门禁）
INSERT INTO ecos_cognitive.ecos_cognitive_framework
    (id, framework_id, version, name, category, purpose, status,
     applicability_json, preconditions_json, input_schema_ref, output_schema_ref,
     steps_json, required_capabilities_json, validation_profile_ref, budget_policy_ref,
     stop_conditions_json, governance_json, contract_resolvable, content_json, content_hash,
     create_by, update_by)
VALUES
    ('cf-01-01-1.0.0', 'CF-01-01', '1.0.0', '问题定义与边界识别', 'PROBLEM_FRAMING',
     '明确目标、范围、成功标准与未知项', 'DRAFT',
     '{"problemTypes":["problem_framing","goal_clarification"],"domains":["enterprise_operations"]}',
     '["goal_statement_present"]',
     'ecos://schemas/cognitive/problem-framing-input/v1',
     'ecos://schemas/cognitive/problem-framing-output/v1',
     '[{"stepId":"s1","name":"目标与范围澄清"},{"stepId":"s2","name":"成功标准与未知项登记"}]',
     '[]', 'scenario-validation/1', 'budget/default',
     '["success_criteria_present"]',
     '{"immutableAfterPublish":true,"productionApprovalRequired":true,"allowDirectSideEffects":false}',
     1, '{"frameworkId":"CF-01-01","version":"1.0.0"}', 'seed-cf-01-01-1.0.0', 'system', 'system'),
    ('cf-04-01-1.0.0', 'CF-04-01', '1.0.0', '证据质量与来源评价', 'EVIDENCE_QUALITY',
     '评价来源、时效、完整性、相关性与冲突', 'DRAFT',
     '{"problemTypes":["evidence_review","source_conflict"],"domains":["enterprise_operations"]}',
     '["evidence_refs_present"]',
     'ecos://schemas/cognitive/evidence-eval-input/v1',
     'ecos://schemas/cognitive/evidence-eval-output/v1',
     '[{"stepId":"s1","name":"来源与时效核验"},{"stepId":"s2","name":"支持/反证与冲突登记"}]',
     '["KB_RETRIEVE"]', 'scenario-validation/1', 'budget/default',
     '["conflict_unresolved"]',
     '{"immutableAfterPublish":true,"productionApprovalRequired":true,"allowDirectSideEffects":false}',
     1, '{"frameworkId":"CF-04-01","version":"1.0.0"}', 'seed-cf-04-01-1.0.0', 'system', 'system'),
    ('cf-03-03-1.0.0', 'CF-03-03', '1.0.0', '溯因与竞争性假设分析', 'ABDUCTIVE_REASONING',
     '从异常事实与可核验证据提出竞争性原因假设并寻找反证', 'DRAFT',
     '{"problemTypes":["metric_deviation","process_anomaly"],"domains":["enterprise_operations"]}',
     '["anomaly_definition_present","comparison_baseline_present"]',
     'ecos://schemas/cognitive/abduction-input/v1',
     'ecos://schemas/cognitive/abduction-output/v1',
     '[{"stepId":"s1","name":"异常与基线确认"},{"stepId":"s2","name":"竞争性假设生成"},{"stepId":"s3","name":"反证检查"}]',
     '["COGNITIVE_DIAGNOSE"]', 'scenario-validation/1', 'budget/default',
     '["max_iterations","no_progress"]',
     '{"immutableAfterPublish":true,"productionApprovalRequired":true,"allowDirectSideEffects":false}',
     1, '{"frameworkId":"CF-03-03","version":"1.0.0"}', 'seed-cf-03-03-1.0.0', 'system', 'system'),
    ('cf-06-02-1.0.0', 'CF-06-02', '1.0.0', '情景规划与敏感性分析', 'SCENARIO_PLANNING',
     '对结构化假设进行情景对比与敏感性分析', 'DRAFT',
     '{"problemTypes":["scenario_planning","sensitivity_analysis"],"domains":["enterprise_operations"]}',
     '["baseline_forecast_present","structured_assumptions_present"]',
     'ecos://schemas/cognitive/scenario-input/v1',
     'ecos://schemas/cognitive/scenario-output/v1',
     '[{"stepId":"s1","name":"基准与情景参数确认"},{"stepId":"s2","name":"确定性预测调用"},{"stepId":"s3","name":"因素贡献与敏感性排序"}]',
     '["DATA_PROFILE","FORECAST_RUN"]', 'scenario-validation/1', 'budget/default',
     '["budget_exceeded"]',
     '{"immutableAfterPublish":true,"productionApprovalRequired":true,"allowDirectSideEffects":false}',
     1, '{"frameworkId":"CF-06-02","version":"1.0.0"}', 'seed-cf-06-02-1.0.0', 'system', 'system')
ON CONFLICT DO NOTHING;

-- ── 回滚说明 ──────────────────────────────────────────────
-- 仅新增表与种子；回滚 = 停用 /frameworks 端点族。删表只允许人工裁决（IR03 禁脚本内 DROP）。
-- 幂等性：CREATE TABLE/INDEX IF NOT EXISTS + ON CONFLICT DO NOTHING ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定 ecos_cognitive. ✓ [DR02/03] 新表单数 ✓ [DR04] JSONB 列语义为结构化元数据 ✓
-- [DR05] 布尔列 contract_resolvable 为 SMALLINT 0/1 ✓ [DR06~08] 七基线列齐 ✓
-- [MC01] VARCHAR(36) 应用侧 UUID ✓ [MC02] 无二进制浮点 ✓ [MC03] 条件并入索引键 ✓
-- [ST07] ecos_cognitive 属五引擎 schema ✓ [ST09] 无跨 schema FK ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
