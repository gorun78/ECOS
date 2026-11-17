-- ============================================================================
-- ecos_demo 二级索引补齐脚本（T3）
-- ----------------------------------------------------------------------------
-- 用途
--   上一批次把 MySQL 转储转换为 PG 并导入 sys_man.ecos_demo（197 表 / 146398 行 / 52 FK），
--   转换器为保证导入成功**丢弃了全部 233 个 MySQL 二级索引**，仅保留主键与唯一键。
--   本脚本从源 dump 逐表复现这些索引，并补齐 52 条外键的子表侧索引
--   （PostgreSQL 不会为外键自动建索引）。
--
-- 生成方式（可复现，勿手改）
--   源 dump  : D:\workspace\政务知识库\产投投资管理系统测试环境数据库\touzi_localhost20260821.sql（只读）
--   解析     : 逐表读取源 dump 中的二级索引定义行（KEY / INDEX / UNIQUE KEY / UNIQUE INDEX）
--   非唯一索引 → CREATE INDEX（本文件全部为 btree 非唯一整列索引）
--   唯一键     → 转换器已用 UNIQUE(...) 保留，本文件不重复建（见 conversion 批次）
--   前缀索引   → MySQL col(64) 前缀语义 PG 不支持 → 建整列非唯一索引（降级，唯一性不恢复）
--   命名       → 源名（源 dump 内全局唯一且 ≤63 字节且不与现存索引重名）优先；
--                否则 idx_<table>_<cols缩写>，超长追加 md5[:8] 后缀
--   索引数     : 229 条（源 dump 复现 229 + 外键补齐 0）；
--                另跳过左前缀冗余 5 条、不兼容类型 0 条
--
-- 幂等性
--   全部 CREATE INDEX IF NOT EXISTS，可重复执行；只作用于 ecos_demo schema。
--
-- 执行方式
--   docker cp ecos_demo_indexes.sql ecos-postgres:/tmp/ && \
--   docker exec ecos-postgres psql -U postgres -d sys_man -f /tmp/ecos_demo_indexes.sql
-- ============================================================================

-- ---- A. 源 dump 二级索引复现 ----
CREATE INDEX IF NOT EXISTS "ACT_FK_BYTEARR_DEPL" ON ecos_demo."act_ge_bytearray" USING btree ("DEPLOYMENT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ACT_INST_START" ON ecos_demo."act_hi_actinst" USING btree ("START_TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ACT_INST_END" ON ecos_demo."act_hi_actinst" USING btree ("END_TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ACT_INST_PROCINST" ON ecos_demo."act_hi_actinst" USING btree ("PROC_INST_ID_", "ACT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ACT_INST_EXEC" ON ecos_demo."act_hi_actinst" USING btree ("EXECUTION_ID_", "ACT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_DETAIL_PROC_INST" ON ecos_demo."act_hi_detail" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_DETAIL_ACT_INST" ON ecos_demo."act_hi_detail" USING btree ("ACT_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_DETAIL_TIME" ON ecos_demo."act_hi_detail" USING btree ("TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_DETAIL_NAME" ON ecos_demo."act_hi_detail" USING btree ("NAME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_DETAIL_TASK_ID" ON ecos_demo."act_hi_detail" USING btree ("TASK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ENT_LNK_SCOPE" ON ecos_demo."act_hi_entitylink" USING btree ("SCOPE_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ENT_LNK_REF_SCOPE" ON ecos_demo."act_hi_entitylink" USING btree ("REF_SCOPE_ID_", "REF_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ENT_LNK_ROOT_SCOPE" ON ecos_demo."act_hi_entitylink" USING btree ("ROOT_SCOPE_ID_", "ROOT_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_ENT_LNK_SCOPE_DEF" ON ecos_demo."act_hi_entitylink" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_IDENT_LNK_USER" ON ecos_demo."act_hi_identitylink" USING btree ("USER_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_IDENT_LNK_SCOPE" ON ecos_demo."act_hi_identitylink" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_IDENT_LNK_SUB_SCOPE" ON ecos_demo."act_hi_identitylink" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_IDENT_LNK_SCOPE_DEF" ON ecos_demo."act_hi_identitylink" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_IDENT_LNK_TASK" ON ecos_demo."act_hi_identitylink" USING btree ("TASK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_IDENT_LNK_PROCINST" ON ecos_demo."act_hi_identitylink" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PRO_INST_END" ON ecos_demo."act_hi_procinst" USING btree ("END_TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PRO_I_BUSKEY" ON ecos_demo."act_hi_procinst" USING btree ("BUSINESS_KEY_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PRO_SUPER_PROCINST" ON ecos_demo."act_hi_procinst" USING btree ("SUPER_PROCESS_INSTANCE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_TASK_SCOPE" ON ecos_demo."act_hi_taskinst" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_TASK_SUB_SCOPE" ON ecos_demo."act_hi_taskinst" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_TASK_SCOPE_DEF" ON ecos_demo."act_hi_taskinst" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_TASK_INST_PROCINST" ON ecos_demo."act_hi_taskinst" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PROCVAR_NAME_TYPE" ON ecos_demo."act_hi_varinst" USING btree ("NAME_", "VAR_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_VAR_SCOPE_ID_TYPE" ON ecos_demo."act_hi_varinst" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_VAR_SUB_ID_TYPE" ON ecos_demo."act_hi_varinst" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PROCVAR_PROC_INST" ON ecos_demo."act_hi_varinst" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PROCVAR_TASK_ID" ON ecos_demo."act_hi_varinst" USING btree ("TASK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_HI_PROCVAR_EXE" ON ecos_demo."act_hi_varinst" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_MEMB_GROUP" ON ecos_demo."act_id_membership" USING btree ("GROUP_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_PRIV_MAPPING" ON ecos_demo."act_id_priv_mapping" USING btree ("PRIV_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_PRIV_USER" ON ecos_demo."act_id_priv_mapping" USING btree ("USER_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_PRIV_GROUP" ON ecos_demo."act_id_priv_mapping" USING btree ("GROUP_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_INFO_JSON_BA" ON ecos_demo."act_procdef_info" USING btree ("INFO_JSON_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_MODEL_SOURCE" ON ecos_demo."act_re_model" USING btree ("EDITOR_SOURCE_VALUE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_MODEL_SOURCE_EXTRA" ON ecos_demo."act_re_model" USING btree ("EDITOR_SOURCE_EXTRA_VALUE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_MODEL_DEPLOYMENT" ON ecos_demo."act_re_model" USING btree ("DEPLOYMENT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_START" ON ecos_demo."act_ru_actinst" USING btree ("START_TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_END" ON ecos_demo."act_ru_actinst" USING btree ("END_TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_PROC" ON ecos_demo."act_ru_actinst" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_PROC_ACT" ON ecos_demo."act_ru_actinst" USING btree ("PROC_INST_ID_", "ACT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_EXEC" ON ecos_demo."act_ru_actinst" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_EXEC_ACT" ON ecos_demo."act_ru_actinst" USING btree ("EXECUTION_ID_", "ACT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_ACTI_TASK" ON ecos_demo."act_ru_actinst" USING btree ("TASK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_DEADLETTER_JOB_EXCEPTION_STACK_ID" ON ecos_demo."act_ru_deadletter_job" USING btree ("EXCEPTION_STACK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_DEADLETTER_JOB_CUSTOM_VALUES_ID" ON ecos_demo."act_ru_deadletter_job" USING btree ("CUSTOM_VALUES_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_DEADLETTER_JOB_CORRELATION_ID" ON ecos_demo."act_ru_deadletter_job" USING btree ("CORRELATION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_DJOB_SCOPE" ON ecos_demo."act_ru_deadletter_job" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_DJOB_SUB_SCOPE" ON ecos_demo."act_ru_deadletter_job" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_DJOB_SCOPE_DEF" ON ecos_demo."act_ru_deadletter_job" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_DEADLETTER_JOB_EXECUTION" ON ecos_demo."act_ru_deadletter_job" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_DEADLETTER_JOB_PROCESS_INSTANCE" ON ecos_demo."act_ru_deadletter_job" USING btree ("PROCESS_INSTANCE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_DEADLETTER_JOB_PROC_DEF" ON ecos_demo."act_ru_deadletter_job" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_ENT_LNK_SCOPE" ON ecos_demo."act_ru_entitylink" USING btree ("SCOPE_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_ENT_LNK_REF_SCOPE" ON ecos_demo."act_ru_entitylink" USING btree ("REF_SCOPE_ID_", "REF_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_ENT_LNK_ROOT_SCOPE" ON ecos_demo."act_ru_entitylink" USING btree ("ROOT_SCOPE_ID_", "ROOT_SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_ENT_LNK_SCOPE_DEF" ON ecos_demo."act_ru_entitylink" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_", "LINK_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EVENT_SUBSCR_CONFIG_" ON ecos_demo."act_ru_event_subscr" USING btree ("CONFIGURATION_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EVENT_SUBSCR_SCOPEREF_" ON ecos_demo."act_ru_event_subscr" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_EVENT_EXEC" ON ecos_demo."act_ru_event_subscr" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EXEC_BUSKEY" ON ecos_demo."act_ru_execution" USING btree ("BUSINESS_KEY_");
CREATE INDEX IF NOT EXISTS "ACT_IDC_EXEC_ROOT" ON ecos_demo."act_ru_execution" USING btree ("ROOT_PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EXEC_REF_ID_" ON ecos_demo."act_ru_execution" USING btree ("REFERENCE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_EXE_PROCINST" ON ecos_demo."act_ru_execution" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_EXE_PARENT" ON ecos_demo."act_ru_execution" USING btree ("PARENT_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_EXE_SUPER" ON ecos_demo."act_ru_execution" USING btree ("SUPER_EXEC_");
CREATE INDEX IF NOT EXISTS "ACT_FK_EXE_PROCDEF" ON ecos_demo."act_ru_execution" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EXTERNAL_JOB_EXCEPTION_STACK_ID" ON ecos_demo."act_ru_external_job" USING btree ("EXCEPTION_STACK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EXTERNAL_JOB_CUSTOM_VALUES_ID" ON ecos_demo."act_ru_external_job" USING btree ("CUSTOM_VALUES_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EXTERNAL_JOB_CORRELATION_ID" ON ecos_demo."act_ru_external_job" USING btree ("CORRELATION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EJOB_SCOPE" ON ecos_demo."act_ru_external_job" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EJOB_SUB_SCOPE" ON ecos_demo."act_ru_external_job" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_EJOB_SCOPE_DEF" ON ecos_demo."act_ru_external_job" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_IDENT_LNK_USER" ON ecos_demo."act_ru_identitylink" USING btree ("USER_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_IDENT_LNK_GROUP" ON ecos_demo."act_ru_identitylink" USING btree ("GROUP_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_IDENT_LNK_SCOPE" ON ecos_demo."act_ru_identitylink" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_IDENT_LNK_SUB_SCOPE" ON ecos_demo."act_ru_identitylink" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_IDENT_LNK_SCOPE_DEF" ON ecos_demo."act_ru_identitylink" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_ATHRZ_PROCEDEF" ON ecos_demo."act_ru_identitylink" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TSKASS_TASK" ON ecos_demo."act_ru_identitylink" USING btree ("TASK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_IDL_PROCINST" ON ecos_demo."act_ru_identitylink" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_JOB_EXCEPTION_STACK_ID" ON ecos_demo."act_ru_job" USING btree ("EXCEPTION_STACK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_JOB_CUSTOM_VALUES_ID" ON ecos_demo."act_ru_job" USING btree ("CUSTOM_VALUES_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_JOB_CORRELATION_ID" ON ecos_demo."act_ru_job" USING btree ("CORRELATION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_JOB_SCOPE" ON ecos_demo."act_ru_job" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_JOB_SUB_SCOPE" ON ecos_demo."act_ru_job" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_JOB_SCOPE_DEF" ON ecos_demo."act_ru_job" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_JOB_EXECUTION" ON ecos_demo."act_ru_job" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_JOB_PROCESS_INSTANCE" ON ecos_demo."act_ru_job" USING btree ("PROCESS_INSTANCE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_JOB_PROC_DEF" ON ecos_demo."act_ru_job" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_SUSPENDED_JOB_EXCEPTION_STACK_ID" ON ecos_demo."act_ru_suspended_job" USING btree ("EXCEPTION_STACK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_SUSPENDED_JOB_CUSTOM_VALUES_ID" ON ecos_demo."act_ru_suspended_job" USING btree ("CUSTOM_VALUES_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_SUSPENDED_JOB_CORRELATION_ID" ON ecos_demo."act_ru_suspended_job" USING btree ("CORRELATION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_SJOB_SCOPE" ON ecos_demo."act_ru_suspended_job" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_SJOB_SUB_SCOPE" ON ecos_demo."act_ru_suspended_job" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_SJOB_SCOPE_DEF" ON ecos_demo."act_ru_suspended_job" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_SUSPENDED_JOB_EXECUTION" ON ecos_demo."act_ru_suspended_job" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_SUSPENDED_JOB_PROCESS_INSTANCE" ON ecos_demo."act_ru_suspended_job" USING btree ("PROCESS_INSTANCE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_SUSPENDED_JOB_PROC_DEF" ON ecos_demo."act_ru_suspended_job" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TASK_CREATE" ON ecos_demo."act_ru_task" USING btree ("CREATE_TIME_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TASK_SCOPE" ON ecos_demo."act_ru_task" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TASK_SUB_SCOPE" ON ecos_demo."act_ru_task" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TASK_SCOPE_DEF" ON ecos_demo."act_ru_task" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TASK_EXE" ON ecos_demo."act_ru_task" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TASK_PROCINST" ON ecos_demo."act_ru_task" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TASK_PROCDEF" ON ecos_demo."act_ru_task" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TIMER_JOB_EXCEPTION_STACK_ID" ON ecos_demo."act_ru_timer_job" USING btree ("EXCEPTION_STACK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TIMER_JOB_CUSTOM_VALUES_ID" ON ecos_demo."act_ru_timer_job" USING btree ("CUSTOM_VALUES_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TIMER_JOB_CORRELATION_ID" ON ecos_demo."act_ru_timer_job" USING btree ("CORRELATION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TIMER_JOB_DUEDATE" ON ecos_demo."act_ru_timer_job" USING btree ("DUEDATE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TJOB_SCOPE" ON ecos_demo."act_ru_timer_job" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TJOB_SUB_SCOPE" ON ecos_demo."act_ru_timer_job" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_TJOB_SCOPE_DEF" ON ecos_demo."act_ru_timer_job" USING btree ("SCOPE_DEFINITION_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TIMER_JOB_EXECUTION" ON ecos_demo."act_ru_timer_job" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TIMER_JOB_PROCESS_INSTANCE" ON ecos_demo."act_ru_timer_job" USING btree ("PROCESS_INSTANCE_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_TIMER_JOB_PROC_DEF" ON ecos_demo."act_ru_timer_job" USING btree ("PROC_DEF_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_VAR_SCOPE_ID_TYPE" ON ecos_demo."act_ru_variable" USING btree ("SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_RU_VAR_SUB_ID_TYPE" ON ecos_demo."act_ru_variable" USING btree ("SUB_SCOPE_ID_", "SCOPE_TYPE_");
CREATE INDEX IF NOT EXISTS "ACT_FK_VAR_BYTEARRAY" ON ecos_demo."act_ru_variable" USING btree ("BYTEARRAY_ID_");
CREATE INDEX IF NOT EXISTS "ACT_IDX_VARIABLE_TASK_ID" ON ecos_demo."act_ru_variable" USING btree ("TASK_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_VAR_EXE" ON ecos_demo."act_ru_variable" USING btree ("EXECUTION_ID_");
CREATE INDEX IF NOT EXISTS "ACT_FK_VAR_PROCINST" ON ecos_demo."act_ru_variable" USING btree ("PROC_INST_ID_");
CREATE INDEX IF NOT EXISTS "FLW_IDX_BATCH_PART" ON ecos_demo."flw_ru_batch_part" USING btree ("BATCH_ID_");
CREATE INDEX IF NOT EXISTS "sched_name" ON ecos_demo."qrtz_triggers" USING btree ("sched_name", "job_name", "job_group");
CREATE INDEX IF NOT EXISTS "idx_sys_logininfor_s" ON ecos_demo."sys_logininfor" USING btree ("status");
CREATE INDEX IF NOT EXISTS "idx_sys_logininfor_lt" ON ecos_demo."sys_logininfor" USING btree ("access_time");
CREATE INDEX IF NOT EXISTS "idx_sys_oper_log_bt" ON ecos_demo."sys_oper_log" USING btree ("business_type");
CREATE INDEX IF NOT EXISTS "idx_sys_oper_log_s" ON ecos_demo."sys_oper_log" USING btree ("status");
CREATE INDEX IF NOT EXISTS "idx_sys_oper_log_ot" ON ecos_demo."sys_oper_log" USING btree ("oper_time");
CREATE INDEX IF NOT EXISTS "idx_td_alarm_rule_detail_rule_id" ON ecos_demo."td_alarm_rule_detail" USING btree ("rule_id");
CREATE INDEX IF NOT EXISTS "idx_td_alarm_rule_frequency_rule_id" ON ecos_demo."td_alarm_rule_frequency" USING btree ("rule_id");
CREATE INDEX IF NOT EXISTS "idx_td_alarm_rule_notice_rule_id" ON ecos_demo."td_alarm_rule_notice" USING btree ("rule_id");
CREATE INDEX IF NOT EXISTS "idx_proc_inst_id" ON ecos_demo."td_approval_application" USING btree ("proc_inst_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_application_flow_status" ON ecos_demo."td_approval_application" USING btree ("flow_status");
CREATE INDEX IF NOT EXISTS "idx_td_approval_application_project_no" ON ecos_demo."td_approval_application" USING btree ("project_no");
CREATE INDEX IF NOT EXISTS "idx_td_approval_application_attachment_application_id" ON ecos_demo."td_approval_application_attachment" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_fund_yearly_investment_project_id" ON ecos_demo."td_approval_fund_yearly_investment" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_investment_committee_project_id" ON ecos_demo."td_approval_investment_committee" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_park_operations_project_id" ON ecos_demo."td_approval_park_operations" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "invest_plan_id" ON ecos_demo."td_approval_predict_economy" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_agreement_framework_project_id" ON ecos_demo."td_approval_project_agreement_framework" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_financial_indicator_project_id" ON ecos_demo."td_approval_project_financial_indicator" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_fund_investment_project_id" ON ecos_demo."td_approval_project_fund_investment" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_ledger_decision_status" ON ecos_demo."td_approval_project_ledger" USING btree ("decision_status");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_ledger_decision_result" ON ecos_demo."td_approval_project_ledger" USING btree ("decision_result");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_id" ON ecos_demo."td_approval_project_location" USING btree ("td_approval_project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_project_risk_indicator_project_id" ON ecos_demo."td_approval_project_risk_indicator" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_yearly_plan_audit_project_id" ON ecos_demo."td_approval_yearly_plan_audit" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_approval_yearly_plan_audit_company_id" ON ecos_demo."td_approval_yearly_plan_audit" USING btree ("company_id");
CREATE INDEX IF NOT EXISTS "idx_audit_status" ON ecos_demo."td_approval_yearly_plan_audit" USING btree ("audit_status");
CREATE INDEX IF NOT EXISTS "idx_investment_year" ON ecos_demo."td_approval_yearly_plan_audit" USING btree ("investment_year");
CREATE INDEX IF NOT EXISTS "idx_biz_time" ON ecos_demo."td_biz_audit_log" USING btree ("business_type", "business_id", "operate_time");
CREATE INDEX IF NOT EXISTS "idx_operate_time" ON ecos_demo."td_biz_audit_log" USING btree ("operate_time");
CREATE INDEX IF NOT EXISTS "uk_tenant_field_code" ON ecos_demo."td_category_node" USING btree ("tenant_id", "field_key", "node_code");
CREATE INDEX IF NOT EXISTS "idx_tenant_field_parent_sort" ON ecos_demo."td_category_node" USING btree ("tenant_id", "field_key", "parent_id", "sort_order");
CREATE INDEX IF NOT EXISTS "idx_tenant_field_status" ON ecos_demo."td_category_node" USING btree ("tenant_id", "field_key", "status");
CREATE INDEX IF NOT EXISTS "idx_subject" ON ecos_demo."td_data_scope_custom" USING btree ("subject_type", "subject_id", "rule_code");
CREATE INDEX IF NOT EXISTS "idx_td_data_scope_custom_rule_code" ON ecos_demo."td_data_scope_custom" USING btree ("rule_code");
CREATE INDEX IF NOT EXISTS "idx_td_data_scope_rule_column_rule_code" ON ecos_demo."td_data_scope_rule_column" USING btree ("rule_code");
CREATE INDEX IF NOT EXISTS "idx_mapper_id" ON ecos_demo."td_data_scope_rule_white" USING btree ("mapper_id");
CREATE INDEX IF NOT EXISTS "idx_statement_id" ON ecos_demo."td_data_scope_sql_log" USING btree ("statement_id");
CREATE INDEX IF NOT EXISTS "idx_td_data_scope_sql_log_user_id" ON ecos_demo."td_data_scope_sql_log" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "idx_created_time" ON ecos_demo."td_data_scope_sql_log" USING btree ("created_time");
CREATE INDEX IF NOT EXISTS "idx_decision_id" ON ecos_demo."td_decision_attachment" USING btree ("decision_id");
CREATE INDEX IF NOT EXISTS "idx_file_id" ON ecos_demo."td_decision_attachment" USING btree ("file_id");
CREATE INDEX IF NOT EXISTS "idx_td_fund_liquidation_asset_detail_liquidation_id" ON ecos_demo."td_fund_liquidation_asset_detail" USING btree ("liquidation_id");
CREATE INDEX IF NOT EXISTS "idx_td_fund_liquidation_debt_detail_liquidation_id" ON ecos_demo."td_fund_liquidation_debt_detail" USING btree ("liquidation_id");
CREATE INDEX IF NOT EXISTS "idx_td_fund_liquidation_investor_detail_liquidation_id" ON ecos_demo."td_fund_liquidation_investor_detail" USING btree ("liquidation_id");
CREATE INDEX IF NOT EXISTS "idx_td_fund_liquidation_node_detail_liquidation_id" ON ecos_demo."td_fund_liquidation_node_detail" USING btree ("liquidation_id");
CREATE INDEX IF NOT EXISTS "idx_td_invest_plan_summary_plan_year" ON ecos_demo."td_invest_plan_summary" USING btree ("plan_year");
CREATE INDEX IF NOT EXISTS "idx_report_status" ON ecos_demo."td_invest_plan_summary" USING btree ("report_status");
CREATE INDEX IF NOT EXISTS "idx_td_investment_plan_config_plan_year" ON ecos_demo."td_investment_plan_config" USING btree ("plan_year");
CREATE INDEX IF NOT EXISTS "idx_td_investor_investor_name" ON ecos_demo."td_investor" USING btree ("investor_name");
CREATE INDEX IF NOT EXISTS "idx_td_investor_investor_type_status" ON ecos_demo."td_investor" USING btree ("investor_type", "status");
CREATE INDEX IF NOT EXISTS "idx_td_investor_test_datax_investor_name" ON ecos_demo."td_investor_test_datax" USING btree ("investor_name");
CREATE INDEX IF NOT EXISTS "idx_td_investor_test_datax_investor_type_status" ON ecos_demo."td_investor_test_datax" USING btree ("investor_type", "status");
CREATE INDEX IF NOT EXISTS "idx_td_message_inbox_user_id" ON ecos_demo."td_message_inbox" USING btree ("user_id");
CREATE INDEX IF NOT EXISTS "idx_nnct_oa_biz_flow_rel_oa_business_id" ON ecos_demo."td_nnct_oa_biz_flow_rel" USING btree ("oa_business_id");
CREATE INDEX IF NOT EXISTS "idx_nnct_oa_cb_business_time" ON ecos_demo."td_nnct_oa_callback_log" USING btree ("oa_business_id", "create_time");
CREATE INDEX IF NOT EXISTS "idx_nnct_oa_cb_target_time" ON ecos_demo."td_nnct_oa_callback_log" USING btree ("target_id", "create_time");
CREATE INDEX IF NOT EXISTS "idx_nnct_oa_cb_status_time" ON ecos_demo."td_nnct_oa_callback_log" USING btree ("callback_status", "create_time");
CREATE INDEX IF NOT EXISTS "idx_paper_id" ON ecos_demo."td_nnct_oa_dict_paper_bind" USING btree ("paper_id");
CREATE INDEX IF NOT EXISTS "idx_apply_type" ON ecos_demo."td_nnct_oa_document" USING btree ("apply_type");
CREATE INDEX IF NOT EXISTS "idx_org_id" ON ecos_demo."td_nnct_oa_document" USING btree ("org_id");
CREATE INDEX IF NOT EXISTS "idx_invoke_time" ON ecos_demo."td_nnct_oa_http_invoke_log" USING btree ("invoke_time");
CREATE INDEX IF NOT EXISTS "idx_ruoyi_user_id" ON ecos_demo."td_nnct_oa_http_invoke_log" USING btree ("ruoyi_user_id");
CREATE INDEX IF NOT EXISTS "idx_object_id" ON ecos_demo."td_nnct_oa_http_invoke_log" USING btree ("object_id");
CREATE INDEX IF NOT EXISTS "idx_biz_trace_id" ON ecos_demo."td_nnct_oa_http_invoke_log" USING btree ("biz_trace_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_key_matter_project_id" ON ecos_demo."td_postinvestment_key_matter" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_comment_project_id" ON ecos_demo."td_postinvestment_project_comment" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_comment_plan_year" ON ecos_demo."td_postinvestment_project_comment" USING btree ("plan_year");
CREATE INDEX IF NOT EXISTS "idx_actual_year" ON ecos_demo."td_postinvestment_project_comment" USING btree ("actual_year");
CREATE INDEX IF NOT EXISTS "idx_develop_mode" ON ecos_demo."td_postinvestment_project_comment" USING btree ("develop_mode");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_comment_problem_project_id" ON ecos_demo."td_postinvestment_project_comment_problem" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_rectification_status" ON ecos_demo."td_postinvestment_project_comment_problem" USING btree ("rectification_status");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_construction_operation_project_id" ON ecos_demo."td_postinvestment_project_construction_operation" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_department_suggestion_project_id" ON ecos_demo."td_postinvestment_project_department_suggestion" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_director_project_id" ON ecos_demo."td_postinvestment_project_director" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_enablement_project_id" ON ecos_demo."td_postinvestment_project_enablement" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_exit_application_project_id" ON ecos_demo."td_postinvestment_project_exit_application" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_application_code" ON ecos_demo."td_postinvestment_project_exit_application" USING btree ("application_code");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_exit_application_flow_status" ON ecos_demo."td_postinvestment_project_exit_application" USING btree ("flow_status");
CREATE INDEX IF NOT EXISTS "idx_business_status" ON ecos_demo."td_postinvestment_project_exit_application" USING btree ("business_status");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_exit_application_attachm_377d2459" ON ecos_demo."td_postinvestment_project_exit_application_attachment" USING btree ("application_id");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_operation_project_id" ON ecos_demo."td_postinvestment_project_operation" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_report_month" ON ecos_demo."td_postinvestment_project_operation" USING btree ("report_month");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_tracking_stand_project_id" ON ecos_demo."td_postinvestment_project_tracking_stand" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_project_no" ON ecos_demo."td_postinvestment_project_tracking_stand" USING btree ("project_no");
CREATE INDEX IF NOT EXISTS "idx_belong_company" ON ecos_demo."td_postinvestment_project_tracking_stand" USING btree ("belong_company");
CREATE INDEX IF NOT EXISTS "idx_td_postinvestment_project_tracking_stand_status" ON ecos_demo."td_postinvestment_project_tracking_stand" USING btree ("status");
CREATE INDEX IF NOT EXISTS "idx_td_preinvestment_key_matter_config_project_id" ON ecos_demo."td_preinvestment_key_matter_config" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_matter_type" ON ecos_demo."td_preinvestment_key_matter_config" USING btree ("matter_type");
CREATE INDEX IF NOT EXISTS "idx_td_preinvestment_key_matter_config_status" ON ecos_demo."td_preinvestment_key_matter_config" USING btree ("status");
CREATE INDEX IF NOT EXISTS "idx_config_status" ON ecos_demo."td_preinvestment_project_config" USING btree ("config_status");
CREATE INDEX IF NOT EXISTS "idx_td_project_favorite_project_id" ON ecos_demo."td_project_favorite" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_project_operation_log_project_id" ON ecos_demo."td_project_operation_log" USING btree ("project_id");
CREATE INDEX IF NOT EXISTS "idx_td_project_operation_log_operation_time" ON ecos_demo."td_project_operation_log" USING btree ("operation_time");
CREATE INDEX IF NOT EXISTS "idx_td_project_operation_log_operation_user" ON ecos_demo."td_project_operation_log" USING btree ("operation_user");
CREATE INDEX IF NOT EXISTS "idx_td_project_sort_config_sort_scope" ON ecos_demo."td_project_sort_config" USING btree ("sort_scope");
CREATE INDEX IF NOT EXISTS "idx_ledger_id" ON ecos_demo."td_risk_handle_infos" USING btree ("risk_ledger_id");
CREATE INDEX IF NOT EXISTS "idx_td_risk_handle_infos_risk_no" ON ecos_demo."td_risk_handle_infos" USING btree ("risk_no");
CREATE INDEX IF NOT EXISTS "idx_mapper_statement_id" ON ecos_demo."td_sort_dimension_config" USING btree ("mapper_statement_id");
CREATE INDEX IF NOT EXISTS "idx_tc_name" ON ecos_demo."td_test_contract" USING btree ("name");
CREATE INDEX IF NOT EXISTS "idx_tc_proc_inst" ON ecos_demo."td_test_contract" USING btree ("proc_inst_id");
CREATE INDEX IF NOT EXISTS "idx_tc_flow_status" ON ecos_demo."td_test_contract" USING btree ("flow_status");

-- ---- B. 外键子表侧索引补齐 ----
-- 52 条外键的子表侧引用列均已由现存索引或上方 A 段新建索引的最左前缀覆盖，
-- 无需额外补建（PostgreSQL 不会为外键自动建索引，此处已逐条核对）。

-- 完成。
