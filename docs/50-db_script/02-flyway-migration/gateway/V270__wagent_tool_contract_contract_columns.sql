-- V270 (补充设计-认知域与智能域 v1.0 §11): Tool Contract 机校字段补列（ecos_ai.ecos_wagent_tool_contract，V236 表）
-- 追溯: §11.1 工具契约字段表 15 项；§7.5 候选安全与幂等（operationClass READ/SUGGEST/WRITE、
--       automationLevel、requiresApproval、dataClassificationMax、evidenceRequired）；REQ-AI-09
-- 原则: **只加不删、不改既有列**；与 V236 既有列的映射（§11 首行「如已有同义字段则映射，不重复造字段」）：
--       * min_level（V236，档位=AutomationLevel）   ←→ automation_level（本批补列 = 显式契约位，读以新列为准，NULL 回落 min_level）
--       * is_evidence_output（V236）                ←→ evidence_required（新列；两者并存，语义 = 是否要求来源引用）
--       * side_effect / rollback_plan_text（V236）  ←→ side_effect_scope / rollback_*（新列细化，旧列停写不改）
--       * 幂等键：V236 无 → 本批 idempotent / idempotency_key_required
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)

-- ── 1. 补列（幂等；DDL 不建 CHECK，词表单源 Java 契约层，R-15 同纪律）──────────────────
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS capability_id              VARCHAR(50);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS operation_class            VARCHAR(10);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS input_schema_ref           VARCHAR(200);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS output_schema_ref          VARCHAR(200);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS evidence_required          SMALLINT;
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS deterministic_output       SMALLINT;
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS automation_level           VARCHAR(10);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS data_classification_max    VARCHAR(10);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS requires_approval          VARCHAR(10);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS idempotent                 SMALLINT;
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS idempotency_key_required   SMALLINT;
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS timeout_ms                 BIGINT;
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS retry_policy_ref           VARCHAR(100);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS side_effect_scope          VARCHAR(200);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS rollback_capability        VARCHAR(10);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS rollback_ref               VARCHAR(200);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS audit_event_type           VARCHAR(50);
ALTER TABLE ecos_ai.ecos_wagent_tool_contract ADD COLUMN IF NOT EXISTS owner_engine               VARCHAR(32);

-- ── 2. 索引：能力目录反查（capability_id → 契约）──────────
CREATE INDEX IF NOT EXISTS idx_v270_tc_capability ON ecos_ai.ecos_wagent_tool_contract (capability_id, is_deleted);

-- ── 3. 注释登记 ──────────────────────────────────────────────
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.operation_class IS
    '<补充设计 v1.0 §11>操作类别 READ/SUGGEST/WRITE（词表单源 WAgentEnums）；SUGGEST 生成候选不改权威资产；WRITE 必走 ToolExecutor 咽喉+security 裁决+审批/幂等/审计/回滚（§11.2）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.automation_level IS
    '<补充设计 v1.0 §11>自动化等级档位（显式契约位；NULL 回落 V236 min_level 语义，读侧单源在 Java 契约层合并）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.evidence_required IS
    '<补充设计 v1.0 §11/§7.5>是否要求来源引用（与 V236 is_evidence_output 并存；新契约读以本列为准）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.data_classification_max IS
    '<补充设计 v1.0 §11>允许处理的最高密级档位（security-engine 权威裁决，本列只声明上限，禁 Router/引擎内复制策略）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.requires_approval IS
    '<补充设计 v1.0 §11>审批要求档位（NONE/HUMAN/TWO_EYE 语义；高风险 WRITE 默认人工审批，§7.2 DATA_CHANGE_APPLY 同口径）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.idempotency_key_required IS
    '<补充设计 v1.0 §7.5>幂等键强制位（同一 idempotencyKey 重试不得重复创建/重复应用）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.rollback_capability IS
    '<补充设计 v1.0 §11>回滚能力档位（NONE/NATURAL/MANUAL 语义；WRITE 高影响面禁止 NONE）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.audit_event_type IS
    '<补充设计 v1.0 §11>统一审计事件类型（值必须命中 EventTypes 单源清单，禁自造字符串，SupplEventCatalogTest 守）';
COMMENT ON COLUMN ecos_ai.ecos_wagent_tool_contract.owner_engine IS
    '<补充设计 v1.0 §11>正式语义与写入权归属引擎（值 = WAgentEngineCatalog.contractEngineCode 六名：data/ontology/kb/cognitive/ai/security；Agent 不写对方表，候选写回经属主 Engine REST，F10-23 同通道）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：纯增量列。回滚 = 应用侧不再读写新列（契约读回落 V236 既有列语义）；
--           如需清列，只允许人工裁决（IR03 禁脚本内 DROP COLUMN）。
-- 幂等性：ADD COLUMN IF NOT EXISTS + CREATE INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_ai.）✓ [DR02] 不改表名 ✓ [DR04] 无 JSON 列（引用键=VARCHAR）✓
-- [DR05] 布尔一律 SMALLINT 0/1 ✓ [DR06~08] 通用列 V236 已有 ✓
-- [MC01] 无新主键 ✓ [MC02] timeout_ms BIGINT 无精度语义（时长非度量值）✓
-- [MC03] 无条件索引 / 无 RLS ✓
-- [ST07] 控制域 AI schema ✓ [IR02] 仅文件 ✓ [IR03] 只加不改 ✓
