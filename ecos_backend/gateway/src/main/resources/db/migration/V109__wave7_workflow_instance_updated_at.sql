-- V109__wave7_workflow_instance_updated_at.sql
-- Wave-7 T-26 (R2) -- 给 ecos_workflow_instance 加 updated_at 列
--
-- 根因: WorkflowInstanceRepository.updateStatus SQL 写 "updated_at = NOW()"
--      但 V108 只加了 error_message/retry_count/current_node_id/context_json/
--      created_at/started_at/completed_at — 漏了 updated_at。
--      结果 resume/suspend/terminate 三条 transition 全部 BadSqlGrammarException → 500
--
-- 铁律: schema 只加不删，幂等 (ADD COLUMN IF NOT EXISTS)
ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
