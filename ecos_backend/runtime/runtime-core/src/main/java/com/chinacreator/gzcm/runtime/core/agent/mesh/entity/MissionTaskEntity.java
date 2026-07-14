package com.chinacreator.gzcm.runtime.core.agent.mesh.entity;

import java.time.LocalDateTime;

/**
 * MissionTask实体 — 映射 ecos_mission_task (PG实际结构)
 */
public class MissionTaskEntity {

    private String id;
    private String missionId;
    private String agentId;
    private String agentName;     // 运行时快照，存在 result JSONB 中
    private String instruction;
    private String status;
    private String result;        // JSONB — 结果+扩展字段
    private String dependsOn;     // 依赖的上游任务
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public MissionTaskEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMissionId() { return missionId; }
    public void setMissionId(String missionId) { this.missionId = missionId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getDependsOn() { return dependsOn; }
    public void setDependsOn(String dependsOn) { this.dependsOn = dependsOn; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    // ── 兼容旧代码 ──

    public Integer getSeq() {
        if (result != null && result.contains("seq")) {
            try {
                var m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(result);
                if (node.has("seq")) return node.get("seq").asInt();
            } catch (Exception ignored) {}
        }
        return 0;
    }
    public void setSeq(Integer seq) {
        mergeResult("seq", seq);
    }

    public String getResultSummary() {
        if (result != null && result.contains("summary")) {
            try {
                var m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(result);
                if (node.has("summary")) return node.get("summary").asText();
            } catch (Exception ignored) {}
        }
        return result;
    }
    public void setResultSummary(String s) {
        mergeResult("summary", s);
    }

    public String getResultDetail() {
        return result;
    }
    public void setResultDetail(String d) {
        mergeResult("detail", d);
    }

    public String getErrorMessage() {
        if (result != null && result.contains("errorMessage")) {
            try {
                var m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(result);
                if (node.has("errorMessage")) return node.get("errorMessage").asText();
            } catch (Exception ignored) {}
        }
        return null;
    }
    public void setErrorMessage(String msg) {
        mergeResult("errorMessage", msg);
    }

    public Long getDurationMs() {
        if (result != null && result.contains("durationMs")) {
            try {
                var m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(result);
                if (node.has("durationMs")) return node.get("durationMs").asLong();
            } catch (Exception ignored) {}
        }
        return null;
    }
    public void setDurationMs(Long ms) {
        mergeResult("durationMs", ms);
    }

    public LocalDateTime getCreatedAt() { return startedAt; }
    public void setCreatedAt(LocalDateTime t) { this.startedAt = t; }

    public LocalDateTime getUpdatedAt() { return finishedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.finishedAt = t; }

    private void mergeResult(String key, Object value) {
        try {
            var m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = result != null ? (com.fasterxml.jackson.databind.node.ObjectNode) m.readTree(result) : m.createObjectNode();
            if (value == null) node.putNull(key);
            else if (value instanceof String) node.put(key, (String) value);
            else if (value instanceof Number) node.put(key, ((Number) value).longValue());
            else node.put(key, value.toString());
            this.result = m.writeValueAsString(node);
        } catch (Exception e) {
            this.result = "{\"" + key + "\":\"" + value + "\"}";
        }
    }
}
