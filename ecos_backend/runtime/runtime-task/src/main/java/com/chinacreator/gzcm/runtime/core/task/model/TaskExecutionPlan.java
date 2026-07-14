package com.chinacreator.gzcm.runtime.core.task.model;

import java.util.List;
import java.util.Map;

/**
 * 任务执行计划
 * 由任务解析器分析TaskDescription后生成，包含具体的执行步骤和配置
 * 
 * @author CDRC Runtime Team
 */
public class TaskExecutionPlan {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 执行步骤列表
     */
    private List<ExecutionStep> steps;

    /**
     * 执行上下文（包含执行过程中需要的各种信息）
     */
    private Map<String, Object> context;

    /**
     * 执行计划元数据
     */
    private Map<String, Object> metadata;

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<ExecutionStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ExecutionStep> steps) {
        this.steps = steps;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 执行步骤
     */
    public static class ExecutionStep {
        /**
         * 步骤ID
         */
        private String stepId;

        /**
         * 步骤名称
         */
        private String stepName;

        /**
         * 步骤类型（如：READ、TRANSFORM、WRITE、VALIDATE等）
         */
        private String stepType;

        /**
         * 步骤执行器类名或标识
         */
        private String executor;

        /**
         * 步骤配置
         */
        private Map<String, Object> config;

        /**
         * 步骤顺序
         */
        private Integer order;

        /**
         * 是否必须（如果失败是否继续执行后续步骤）
         */
        private Boolean required;

        /**
         * 依赖的步骤ID列表
         */
        private List<String> dependencies;

        // Getters and Setters
        public String getStepId() {
            return stepId;
        }

        public void setStepId(String stepId) {
            this.stepId = stepId;
        }

        public String getStepName() {
            return stepName;
        }

        public void setStepName(String stepName) {
            this.stepName = stepName;
        }

        public String getStepType() {
            return stepType;
        }

        public void setStepType(String stepType) {
            this.stepType = stepType;
        }

        public String getExecutor() {
            return executor;
        }

        public void setExecutor(String executor) {
            this.executor = executor;
        }

        public Map<String, Object> getConfig() {
            return config;
        }

        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }

        public Integer getOrder() {
            return order != null ? order : 0;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }

        public Boolean getRequired() {
            return required != null ? required : true;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }
    }
}

