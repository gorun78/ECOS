package com.chinacreator.gzcm.buszhi.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

/**
 * 5种核心流程节点数据模型 — 拖拽式设计器前后端契约。
 */
public final class WorkflowNodeModels {

    private WorkflowNodeModels() {}

    // ── 节点基类 ────────────────────────────────────────────

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = StartNode.class, name = "start"),
        @JsonSubTypes.Type(value = EndNode.class, name = "end"),
        @JsonSubTypes.Type(value = UserTaskNode.class, name = "userTask"),
        @JsonSubTypes.Type(value = AgentTaskNode.class, name = "agentTask"),
        @JsonSubTypes.Type(value = ExclusiveGatewayNode.class, name = "exclusiveGateway")
    })
    public abstract static class NodeConfig {
        private String id;
        private String label;
        private Position position;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
    }

    public static class Position {
        private int x;
        private int y;

        public Position() {}
        public Position(int x, int y) { this.x = x; this.y = y; }
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
    }

    // ── (1) StartNode ─────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StartNode extends NodeConfig {
        private StartConfig config;

        public StartConfig getConfig() { return config; }
        public void setConfig(StartConfig config) { this.config = config; }
    }

    public static class StartConfig {
        private String triggerType;      // MANUAL | OBJECT_ACTION | SCHEDULED | API
        private String objectEntity;
        private String objectAction;
        private Map<String, String> inputMapping;

        public String getTriggerType() { return triggerType; }
        public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
        public String getObjectEntity() { return objectEntity; }
        public void setObjectEntity(String objectEntity) { this.objectEntity = objectEntity; }
        public String getObjectAction() { return objectAction; }
        public void setObjectAction(String objectAction) { this.objectAction = objectAction; }
        public Map<String, String> getInputMapping() { return inputMapping; }
        public void setInputMapping(Map<String, String> inputMapping) { this.inputMapping = inputMapping; }
    }

    // ── (2) EndNode ───────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EndNode extends NodeConfig {
        private EndConfig config;

        public EndConfig getConfig() { return config; }
        public void setConfig(EndConfig config) { this.config = config; }
    }

    public static class EndConfig {
        private String endType;          // COMPLETED | APPROVED | REJECTED | CANCELLED
        private Map<String, Object> outputMapping;

        public String getEndType() { return endType; }
        public void setEndType(String endType) { this.endType = endType; }
        public Map<String, Object> getOutputMapping() { return outputMapping; }
        public void setOutputMapping(Map<String, Object> outputMapping) { this.outputMapping = outputMapping; }
    }

    // ── (3) UserTaskNode ──────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserTaskNode extends NodeConfig {
        private UserTaskConfig config;

        public UserTaskConfig getConfig() { return config; }
        public void setConfig(UserTaskConfig config) { this.config = config; }
    }

    public static class UserTaskConfig {
        private String taskType;         // APPROVAL | EXECUTION | INVESTIGATION
        private String assignee;         // 表达式或固定用户ID
        private List<String> candidateUsers;
        private List<String> candidateRoles;
        private String dueDate;          // 如 "48h"
        private FormSchema formSchema;
        private List<TaskAction> actions;
        private String approvalType;     // SINGLE | ALL | OR
        private Escalation escalation;

        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
        public List<String> getCandidateUsers() { return candidateUsers; }
        public void setCandidateUsers(List<String> candidateUsers) { this.candidateUsers = candidateUsers; }
        public List<String> getCandidateRoles() { return candidateRoles; }
        public void setCandidateRoles(List<String> candidateRoles) { this.candidateRoles = candidateRoles; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
        public FormSchema getFormSchema() { return formSchema; }
        public void setFormSchema(FormSchema formSchema) { this.formSchema = formSchema; }
        public List<TaskAction> getActions() { return actions; }
        public void setActions(List<TaskAction> actions) { this.actions = actions; }
        public String getApprovalType() { return approvalType; }
        public void setApprovalType(String approvalType) { this.approvalType = approvalType; }
        public Escalation getEscalation() { return escalation; }
        public void setEscalation(Escalation escalation) { this.escalation = escalation; }
    }

    public static class FormSchema {
        private List<FormField> fields;

        public List<FormField> getFields() { return fields; }
        public void setFields(List<FormField> fields) { this.fields = fields; }
    }

    public static class FormField {
        private String code;
        private String label;
        private String type;     // TEXT | TEXTAREA | RADIO | CHECKBOX | SELECT | DATE | NUMBER
        private boolean required;
        private List<String> options;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
    }

    public static class TaskAction {
        private String code;         // approve | reject | transfer
        private String label;
        private String targetStatus;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getTargetStatus() { return targetStatus; }
        public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }
    }

    public static class Escalation {
        private String timeout;
        private String escalateTo;

        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
        public String getEscalateTo() { return escalateTo; }
        public void setEscalateTo(String escalateTo) { this.escalateTo = escalateTo; }
    }

    // ── (4) AgentTaskNode ─────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentTaskNode extends NodeConfig {
        private AgentTaskConfig config;

        public AgentTaskConfig getConfig() { return config; }
        public void setConfig(AgentTaskConfig config) { this.config = config; }
    }

    public static class AgentTaskConfig {
        private String agentType;        // RISK_ASSESSMENT | GENERAL | ANALYSIS | CUSTOM
        private AgentConfig agentConfig;
        private Map<String, String> inputContext;
        private Map<String, String> outputSchema;
        private Map<String, String> outputMapping;
        private boolean humanReview;
        private String timeout;          // e.g. "5m"
        private int retryOnError;

        public String getAgentType() { return agentType; }
        public void setAgentType(String agentType) { this.agentType = agentType; }
        public AgentConfig getAgentConfig() { return agentConfig; }
        public void setAgentConfig(AgentConfig agentConfig) { this.agentConfig = agentConfig; }
        public Map<String, String> getInputContext() { return inputContext; }
        public void setInputContext(Map<String, String> inputContext) { this.inputContext = inputContext; }
        public Map<String, String> getOutputSchema() { return outputSchema; }
        public void setOutputSchema(Map<String, String> outputSchema) { this.outputSchema = outputSchema; }
        public Map<String, String> getOutputMapping() { return outputMapping; }
        public void setOutputMapping(Map<String, String> outputMapping) { this.outputMapping = outputMapping; }
        public boolean isHumanReview() { return humanReview; }
        public void setHumanReview(boolean humanReview) { this.humanReview = humanReview; }
        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
        public int getRetryOnError() { return retryOnError; }
        public void setRetryOnError(int retryOnError) { this.retryOnError = retryOnError; }
    }

    public static class AgentConfig {
        private String agentId;
        private String model;
        private String systemPrompt;
        private Double temperature;

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
    }

    // ── (5) ExclusiveGatewayNode ──────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExclusiveGatewayNode extends NodeConfig {
        private GatewayConfig config;

        public GatewayConfig getConfig() { return config; }
        public void setConfig(GatewayConfig config) { this.config = config; }
    }

    public static class GatewayConfig {
        private List<ConditionBranch> conditions;

        public List<ConditionBranch> getConditions() { return conditions; }
        public void setConditions(List<ConditionBranch> conditions) { this.conditions = conditions; }
    }

    public static class ConditionBranch {
        private String expression;       // SpEL 表达式 e.g. "${variables.riskLevel} == 'HIGH'"
        private String label;
        private String targetNode;       // 目标节点ID
        private boolean isDefault;       // 默认分支

        public String getExpression() { return expression; }
        public void setExpression(String expression) { this.expression = expression; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getTargetNode() { return targetNode; }
        public void setTargetNode(String targetNode) { this.targetNode = targetNode; }
        public boolean isDefault() { return isDefault; }
        public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    }

    // ── 工具方法 ─────────────────────────────────────────

    /**
     * 根据节点类型返回对应的 Java Class。
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends NodeConfig> classForType(String type) {
        return switch (type) {
            case "start" -> StartNode.class;
            case "end" -> EndNode.class;
            case "userTask" -> UserTaskNode.class;
            case "agentTask" -> AgentTaskNode.class;
            case "exclusiveGateway" -> ExclusiveGatewayNode.class;
            default -> throw new IllegalArgumentException("Unknown node type: " + type);
        };
    }

    /** 支持的所有节点类型 */
    public static final String[] ALL_TYPES = {
        "start", "end", "userTask", "agentTask", "exclusiveGateway"
    };
}
