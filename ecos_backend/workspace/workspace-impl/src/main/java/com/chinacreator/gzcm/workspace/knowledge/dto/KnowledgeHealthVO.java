package com.chinacreator.gzcm.workspace.knowledge.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识工作台健康检查 VO — E-A#1 产物。
 *
 * <p>聚合 6 个引擎 + PG 数据库/向量库状态，供前端知识工作台 15 Tab 的
 * 状态条消费。单引擎 DOWN 不影响整体 HTTP 200（降级为 DEGRADED + reason）。
 *
 * @author ecos-factory
 * @since PMO-55 批次 E-A
 */
public class KnowledgeHealthVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 单引擎健康快照（内部结构， kaum 用途）。 */
    public static class EngineHealthVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 引擎状态："UP" / "DEGRADED" / "DOWN" */
        private String status;

        /** 引擎级 reason（source=local|remote 说明探活方式） */
        private String reason;

        /** 各子系统组件状态（来自引擎 HealthCheck.components） */
        private Map<String, Object> components = new LinkedHashMap<>();

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Map<String, Object> getComponents() {
            return components;
        }

        public void setComponents(Map<String, Object> components) {
            this.components = components == null ? new LinkedHashMap<>() : components;
        }
    }

    /** 整体状态：UP=全绿 / DEGRADED=部分 DOWN / DOWN=核心 DOWN */
    private String overall;

    /** 各引擎健康状态（kb/cognitive/ai/security/ontology/data，LinkedHashMap 保序） */
    private Map<String, EngineHealthVO> engines = new LinkedHashMap<>();

    /** PostgreSQL 主库状态（pgdb，直连 fast-path） */
    private String pgdb;

    /** PG 向量库（pgvector 扩展可用性，shared feature = pgdb + pgvectorEXT） */
    private String pgVector;

    /** 本次探活总耗时（毫秒），含远端 cognitive 回退尝试 */
    private Long latencyMs;

    public String getOverall() {
        return overall;
    }

    public void setOverall(String overall) {
        this.overall = overall;
    }

    public Map<String, EngineHealthVO> getEngines() {
        return engines;
    }

    public void setEngines(Map<String, EngineHealthVO> engines) {
        this.engines = engines == null ? new LinkedHashMap<>() : engines;
    }

    public String getPgdb() {
        return pgdb;
    }

    public void setPgdb(String pgdb) {
        this.pgdb = pgdb;
    }

    public String getPgVector() {
        return pgVector;
    }

    public void setPgVector(String pgVector) {
        this.pgVector = pgVector;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
