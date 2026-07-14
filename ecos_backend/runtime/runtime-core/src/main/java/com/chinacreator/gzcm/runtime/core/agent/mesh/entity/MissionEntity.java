package com.chinacreator.gzcm.runtime.core.agent.mesh.entity;

import java.time.LocalDateTime;

/**
 * Mission实体 — 映射 ecos_mission (PG实际结构)
 */
public class MissionEntity {

    private String id;
    private String title;
    private String goal;          // 对应旧 description
    private String mode;
    private String status;
    private String plan;          // JSONB — 输入参数
    private String result;        // JSONB — 输出结果+扩展字段
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public MissionEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    // ── 兼容旧代码的别名 ──

    public String getDescription() { return goal; }
    public void setDescription(String d) { this.goal = d; }

    public String getInputParams() { return plan; }
    public void setInputParams(String p) { this.plan = p; }

    public String getOutputResult() {
        if (result != null && result.contains("outputResult")) {
            try {
                var m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(result);
                if (node.has("outputResult")) return node.get("outputResult").asText();
            } catch (Exception ignored) {}
        }
        return result;
    }
    public void setOutputResult(String r) {
        try {
            var m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = m.createObjectNode();
            node.put("outputResult", r);
            this.result = m.writeValueAsString(node);
        } catch (Exception e) {
            this.result = "{\"outputResult\":\"" + (r != null ? r.replace("\"", "\\\"") : "") + "\"}";
        }
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
        try {
            var m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = result != null ? (com.fasterxml.jackson.databind.node.ObjectNode) m.readTree(result) : m.createObjectNode();
            node.put("errorMessage", msg);
            this.result = m.writeValueAsString(node);
        } catch (Exception e) {
            this.result = "{\"errorMessage\":\"" + msg + "\"}";
        }
    }

    public LocalDateTime getStartedAt() {
        if (result != null && result.contains("startedAt")) {
            try {
                var m = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = m.readTree(result);
                if (node.has("startedAt")) return LocalDateTime.parse(node.get("startedAt").asText());
            } catch (Exception ignored) {}
        }
        return null;
    }
    public void setStartedAt(LocalDateTime t) {
        try {
            var m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = result != null ? (com.fasterxml.jackson.databind.node.ObjectNode) m.readTree(result) : m.createObjectNode();
            node.put("startedAt", t != null ? t.toString() : null);
            this.result = m.writeValueAsString(node);
        } catch (Exception ignored) {}
    }

    public LocalDateTime getCompletedAt() { return finishedAt; }
    public void setCompletedAt(LocalDateTime t) { this.finishedAt = t; }

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
        try {
            var m = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = result != null ? (com.fasterxml.jackson.databind.node.ObjectNode) m.readTree(result) : m.createObjectNode();
            node.put("durationMs", ms);
            this.result = m.writeValueAsString(node);
        } catch (Exception ignored) {}
    }

    public LocalDateTime getUpdatedAt() { return finishedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.finishedAt = t; }
}
