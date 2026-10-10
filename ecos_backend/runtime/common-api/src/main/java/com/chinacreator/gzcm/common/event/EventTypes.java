package com.chinacreator.gzcm.common.event;

public final class EventTypes {
    private EventTypes() {}

    public static final class Identity {
        public static final String USER_CREATED = "UserCreated";
        public static final String USER_UPDATED = "UserUpdated";
        public static final String USER_LOCKED = "UserLocked";
        public static final String USER_DISABLED = "UserDisabled";
        public static final String ROLE_ASSIGNED = "RoleAssigned";
        public static final String TENANT_CREATED = "TenantCreated";
        public static final String TENANT_ACTIVATED = "TenantActivated";
    }

    public static final class Catalog {
        public static final String DATASET_CREATED = "DatasetCreated";
        public static final String DATASET_UPDATED = "DatasetUpdated";
        public static final String DATASOURCE_REGISTERED = "DatasourceRegistered";
        public static final String PIPELINE_EXECUTED = "PipelineExecuted";
    }

    public static final class Ontology {
        public static final String ONTOLOGY_CREATED = "OntologyCreated";
        public static final String ENTITY_ADDED = "EntityAdded";
        public static final String RELATIONSHIP_ADDED = "RelationshipAdded";
        public static final String ONTOLOGY_PUBLISHED = "OntologyPublished";
        public static final String ONTOLOGY_VERSION_CREATED = "OntologyVersionCreated";
    }

    public static final class Object {
        public static final String OBJECT_CREATED = "ObjectCreated";
        public static final String OBJECT_UPDATED = "ObjectUpdated";
        public static final String OBJECT_DELETED = "ObjectDeleted";
        public static final String RELATIONSHIP_CREATED = "RelationshipCreated";
    }

    public static final class Workflow {
        public static final String WORKFLOW_STARTED = "WorkflowStarted";
        public static final String TASK_ASSIGNED = "TaskAssigned";
        public static final String TASK_COMPLETED = "TaskCompleted";
        public static final String WORKFLOW_COMPLETED = "WorkflowCompleted";
        public static final String APPROVAL_REQUESTED = "ApprovalRequested";
    }

    public static final class Agent {
        public static final String AGENT_STARTED = "AgentStarted";
        public static final String TOOL_EXECUTED = "ToolExecuted";
        public static final String PLAN_GENERATED = "PlanGenerated";
        public static final String AGENT_COMPLETED = "AgentCompleted";
        public static final String MISSION_CREATED = "MissionCreated";
        public static final String MISSION_COMPLETED = "MissionCompleted";

        // ── 详细设计-10 §9.1.2 W Agent 编排域 additive 注册（30 名，R-70② 批准口径：
        //    8 topic 承载 36 名中的 30 名；ai.model.*（3 名）与 cognition.*（3 名）无落点/基建
        //    topic 未建，**不注册**、不新建 topic 常量，随 R-70 单独批准。
        //    纪律：只加常量、禁改上方既有 6 名；名一律 PascalCase（ECOS 风格，不因附件点分名改名）。
        //    topic 归属沿用 KafkaTopics.topicForAggregate()（行尾括注目标 topic 与聚合名口径）；
        //    action 三名按 R-69② 使用另立聚合名 "wagent_action"（经默认路由落 ecos.audit，
        //    与本体域 "action"→ecos.ontology 的同名不同义冲突不混用，路由表收口随 R-69 裁）。
        //    WAgentEvent 信封承载 tenant_id/run_id/trace_id/actor/schema_version（R-68②③）。 ──

        /** 附注：文档口径表只枚举 26 个有落点名；其余 4 名（RunReplanned/RunPaused/RunResumed/RunInputReceived）
         *  依 §4.2 EVT=REPLAN、F10-12、F10-15 RunResumeListener、F10-21 Kill Switch、§5.2 #8 submitRunInput
         *  的文义补足，以兑 "30 名有落点" 之裁定。 */

        /** Goal 创建（agent.goal.created）→ ecos.agent */
        public static final String GOAL_CREATED = "GoalCreated";
        /** Goal 新版本（agent.goal.versioned，口径变更链 F10-07）→ ecos.agent */
        public static final String GOAL_VERSIONED = "GoalVersioned";
        /** Mission 阶段变更（agent.mission.stage_changed）→ ecos.agent */
        public static final String MISSION_STAGE_CHANGED = "MissionStageChanged";
        /** Readiness 评估完成（agent.readiness.assessed，F10-08）→ ecos.agent */
        public static final String READINESS_ASSESSED = "ReadinessAssessed";
        /** Run 创建（agent.run.created，F10-11）→ ecos.agent */
        public static final String RUN_CREATED = "RunCreated";
        /** Run 终态完成（含 completed_degraded 的降级标注，agent.run.completed）→ ecos.agent */
        public static final String RUN_COMPLETED = "RunCompleted";
        /** Run 失败（agent.run.failed）→ ecos.agent */
        public static final String RUN_FAILED = "RunFailed";
        /** Run 重规划（§4.2 EVT=REPLAN / F10-12 时间线插注事件；补足名，登记口径见上方附注）→ ecos.agent */
        public static final String RUN_REPLANNED = "RunReplanned";
        /** Run 挂起（F10-21 Kill Switch 开启即 paused；补足名）→ ecos.agent */
        public static final String RUN_PAUSED = "RunPaused";
        /** Run 事件唤醒恢复（F10-15 RunResumeListener，wait_task 完成；补足名）→ ecos.agent */
        public static final String RUN_RESUMED = "RunResumed";
        /** Run 收到 awaiting_input 应答（§5.2 #8 submitRunInput；补足名）→ ecos.agent */
        public static final String RUN_INPUT_RECEIVED = "RunInputReceived";
        /** Step 成功（agent.step.completed，F10-14；SSE 承流 P-4 消费）→ ecos.agent */
        public static final String STEP_COMPLETED = "StepCompleted";
        /** Step 失败（agent.step.failed）→ ecos.agent */
        public static final String STEP_FAILED = "StepFailed";
        /** 策略裁决拒绝（agent.policy.denied，F10-22）→ ecos.agent **双发 ecos.audit**（铁律 §2.4#5/ST06） */
        public static final String POLICY_DENIED = "PolicyDenied";
        /** 候选生成（agent.candidate.generated，F10-20）→ ecos.agent */
        public static final String CANDIDATE_GENERATED = "CandidateGenerated";
        /** 候选五段校验通过（agent.candidate.validated，F10-20）→ ecos.agent */
        public static final String CANDIDATE_VALIDATED = "CandidateValidated";
        /** 候选评审通过（agent.candidate.approved；四眼校验）→ ecos.agent */
        public static final String CANDIDATE_APPROVED = "CandidateApproved";
        /** 候选驳回（agent.candidate.rejected）→ ecos.agent */
        public static final String CANDIDATE_REJECTED = "CandidateRejected";
        /** 候选发布（agent.candidate.published，F10-23；发布态按目标 Engine 的 aggregate 复路由 ecos.ontology/ecos.knowledge） */
        public static final String CANDIDATE_PUBLISHED = "CandidatePublished";
        /** 认知域派生候选（agent.candidate.from_cognition）→ ecos.knowledge（评审中心消费；cognition 基建 topic 缺失部分随 R-70） */
        public static final String CANDIDATE_FROM_COGNITION = "CandidateFromCognition";
        /** 决策选项就绪（agent.decision.options_ready，属主 ai-engine R-32①）→ ecos.agent */
        public static final String DECISION_OPTIONS_READY = "DecisionOptionsReady";
        /** 决策选定（agent.decision.selected）→ ecos.agent */
        public static final String DECISION_SELECTED = "DecisionSelected";
        /** 决策审批（agent.decision.approved）→ ecos.workflow（聚合名 "approval"，§9.1.2 括注） */
        public static final String DECISION_APPROVED = "DecisionApproved";
        /** 行动草稿创建（action.created，附件 §9.2 draftAction）→ 聚合名 wagent_action（R-69②） */
        public static final String ACTION_CREATED = "ActionCreated";
        /** 行动提交（action.committed，commitAction）→ 聚合名 wagent_action（R-69②） */
        public static final String ACTION_COMMITTED = "ActionCommitted";
        /** 行动完成（action.completed）→ 聚合名 wagent_action（R-69②） */
        public static final String ACTION_COMPLETED = "ActionCompleted";
        /** 效果回灌记录（agent.effect.recorded，与卷 09 F09-13 同源禁两处定义）→ ecos.agent */
        public static final String EFFECT_RECORDED = "EffectRecorded";
        /** 效果偏差检出（agent.effect.deviation_detected）→ ecos.agent */
        public static final String EFFECT_DEVIATION_DETECTED = "EffectDeviationDetected";
        /** 复审触发（agent.review.triggered，R-66/R-68 并案）→ ecos.agent／ecos.workflow（进 runtime-task 待办） */
        public static final String REVIEW_TRIGGERED = "ReviewTriggered";
        /** 安全事件上抛（agent.security.event，F10-22）→ ecos.audit（安全事件不得只在 ecos.agent 留一份） */
        public static final String SECURITY_EVENT_RAISED = "SecurityEventRaised";
    }

    public static final class Knowledge {
        public static final String KNOWLEDGE_CREATED = "KnowledgeCreated";
        public static final String KNOWLEDGE_LINKED = "KnowledgeLinked";
        public static final String GLOSSARY_PUBLISHED = "GlossaryPublished";
    }

    /**
     * 详细设计-06 F06-03（卷 06 REQ-AI-01）—— 护栏/工具裁决事件类型。
     *
     * <p>常量登记在 runtime/common-api 供全仓共用（禁 ai-engine 自定义字符串）。
     * 载荷只带摘要与 ID，禁带工具入参原文 / SQL 原文 / 结果原文。</p>
     */
    public static final class Guardrail {
        /** 一次工具裁决（ALLOW / DENY / FAIL_CLOSED 三态同型，每次裁决恰一条） */
        public static final String GUARDRAIL_EVAL = "GUARDRAIL_EVAL";
        /** 输入/输出护栏一次过滤（F06-04） */
        public static final String GUARDRAIL_FILTER = "GUARDRAIL_FILTER";
    }

    /**
     * 详细设计-06 F06-07（REQ-AI-03 §3.2）—— LLM 计量事件类型。
     *
     * <p>每次模型调用恰一条；计量点唯一在 runtime/llm-gateway（禁各引擎自记）。</p>
     */
    public static final class Llm {
        public static final String LLM_USAGE = "LLM_USAGE";
    }

    /**
     * 详细设计-06 F06-09（卷 06 REQ-AI-02）—— 会话压缩治理事件类型。
     *
     * <p>两者都落 {@code KafkaTopics.AUDIT}（不新增 topic，容器/主题基线不变）。
     * 载荷只带批次 ID、模式、token 前后可读数与引用 ID 串，
     * <b>禁</b>带消息原文 / 摘要原文（回放与语义抽检经批次表取，不在事件面铺原文）。</p>
     */
    public static final class Session {
        /** 摘要失败降级为截断归档（{@code compression_mode=TRUNCATE_FALLBACK}，§2.2-3 抽检验据） */
        public static final String COMPRESSION_FALLBACK = "SESSION_COMPRESSION_FALLBACK";
        /** 压缩开关关闭 ⇒ 显式上报 {@code compressSkipped=true}（F06-09 要点 1，禁静默关闭） */
        public static final String COMPRESSION_SKIPPED = "SESSION_COMPRESSION_SKIPPED";
    }
}
