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
    }

    public static final class Knowledge {
        public static final String KNOWLEDGE_CREATED = "KnowledgeCreated";
        public static final String KNOWLEDGE_LINKED = "KnowledgeLinked";
        public static final String GLOSSARY_PUBLISHED = "GlossaryPublished";
    }

    /**
     * F06-03（详细设计-06 §294 + §297）— Agent 工具裁决 GUARDRAIL 事件族。
     * <p>咽喉 {@code AgentToolPolicyGate.adjudicate} 的每次裁决（ALLOW/DENY/FAIL_CLOSED）
     * 经 {@code EventBusService.publish(KafkaTopics.AUDIT, event)} 发出一条
     * {@code eventType=GUARDRAIL_EVAL} 事件；detail 内的 {@code decision} 字段
     * 区分三态（PRD-06 §1.4）。{@code GUARDRAIL_DENIED} / {@code GUARDRAIL_FAIL_CLOSED}
     * 作为错误分类常量（F06-14 错误分类层复用），此处一并登记避免 ai-engine
     * 侧自定义字符串（X-17 红线：事件类型必须在 common-api 单源）。</p>
     */
    public static final class Guardrail {
        public static final String GUARDRAIL_EVAL = "GUARDRAIL_EVAL";
        public static final String GUARDRAIL_DENIED = "GUARDRAIL_DENIED";
        public static final String GUARDRAIL_FAIL_CLOSED = "GUARDRAIL_FAIL_CLOSED";

        /** Detail 段 decision 取值（与 tool 类型/来源正交，仅表达裁决结论） */
        public static final class Decision {
            public static final String ALLOW = "ALLOW";
            public static final String DENY = "DENY";
            public static final String FAIL_CLOSED = "FAIL_CLOSED";
        }
    }
}
