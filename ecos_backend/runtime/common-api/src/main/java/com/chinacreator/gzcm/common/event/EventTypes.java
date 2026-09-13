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
}
