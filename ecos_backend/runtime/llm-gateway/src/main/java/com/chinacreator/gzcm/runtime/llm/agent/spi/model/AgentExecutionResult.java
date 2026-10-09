package com.chinacreator.gzcm.runtime.llm.agent.spi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SPI 契约：任务执行结果（接缝义务 1 · executor 能力出参）。
 *
 * <p>注意：本模型不含 token / 模型名字段。Token 计量的唯一发生点是
 * {@code runtime/llm-gateway}（铁律 §2.5 / 接缝义务 5），provider 侧不得经此回传计量口径。</p>
 *
 * @author ecos-factory
 * @since 详细设计-06 §八 M0
 */
public class AgentExecutionResult {

    private String id;
    private String taskId;
    private String output;
    private boolean success;
    private Map<String, Object> metrics = new HashMap<>();

    public AgentExecutionResult() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics == null ? new HashMap<>() : metrics;
    }
}
