package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * cognitive 引擎公开健康检查 VO — PMO-55 第 4 波 E-C 产物。
 *
 * <p>承载 {@code GET /api/v1/engine/cognitive/health} 响应体。
 * 字段结构对齐项目 {@link com.chinacreator.gzcm.common.engine.HealthCheck}
 * 契约（status + components），上游 {@code KnowledgeHealthAggregator}
 * 已按 {@code data.status / data.components} 解析。</p>
 *
 * <p>原 PMO-55 E-C 任务书 j 中以 {@code checks} 描述子对象，落地为强类型
 * {@link Components} 嵌套，字段名按项目契约统一为 {@code components}，
 * 让 E-A 已交付的 {@code KnowledgeHealthAggregator} 解析逻辑零改。</p>
 *
 * <p>状态判定逻辑：</p>
 * <ul>
 *   <li>{@code UP} — DB 探活成功 + pipelineCount/pipelineActiveCount 两个 COUNT 均成功</li>
 *   <li>{@code DEGRADED} — DB 探活成功 + 任一个 COUNT 失败（如表未建、schema 变化）</li>
 *   <li>{@code DOWN} — DB 探活失败（reason 字段含短原因）</li>
 * </ul>
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-C (2026-09-13)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CognitiveEngineOpenHealthVO {

    /** 引擎总体状态。取值：UP / DEGRADED / DOWN */
    private String status;

    /** components 块 — 子检查（强类型，避免 Map），与 com.chinacreator.gzcm.common.engine.HealthCheck 契约对齐。 */
    private Components components;

    /** 服务器 uptime（毫秒，自 Gateway/Service JVM 启动算起）。 */
    private Long uptimeMs;

    /** 引擎版本标识（当前固定 "v2.0"，对应 cognitive2 包）。 */
    private String version;

    /** 仅 status=DOWN 时填充 — 短原因（截断 200 字）。 */
    private String reason;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Components getComponents() { return components; }
    public void setComponents(Components components) { this.components = components; }

    public Long getUptimeMs() { return uptimeMs; }
    public void setUptimeMs(Long uptimeMs) { this.uptimeMs = uptimeMs; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    /**
     * 嵌套 components — 把 pipeline 计数与 DB 探活结果集中到一个嵌套对象，
     * 字段名按 project convention 走 {@code components}（E-A 已交付聚合器解析契约），
     * 任务书中的 {@code checks} 是字段含义别名。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Components {

        /** {@code kb_cognitive_pipeline} 行数（仅未删除 is_deleted=0）。-1 = 表不存在 / 查询失败 */
        private Long pipelineCount;

        /** {@code kb_cognitive_pipeline} 中 status='ACTIVE' 行数。-1 = 表不存在 / 查询失败 */
        private Long pipelineActiveCount;

        /** PG 探活结果。取值：UP / DOWN */
        private String db;

        public Long getPipelineCount() { return pipelineCount; }
        public void setPipelineCount(Long pipelineCount) { this.pipelineCount = pipelineCount; }

        public Long getPipelineActiveCount() { return pipelineActiveCount; }
        public void setPipelineActiveCount(Long pipelineActiveCount) { this.pipelineActiveCount = pipelineActiveCount; }

        public String getDb() { return db; }
        public void setDb(String db) { this.db = db; }
    }
}
