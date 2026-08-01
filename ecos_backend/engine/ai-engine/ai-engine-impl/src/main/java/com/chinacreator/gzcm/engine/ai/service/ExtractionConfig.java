package com.chinacreator.gzcm.engine.ai.service;

import java.util.Map;

/**
 * KAG Extractor 抽取配置 — 控制一次抽取的范围和行为。
 *
 * <pre>
 *   domain:       业务域标识，用于限定实体/关系的作用域
 *   syncMode:     同步模式 — AUTO(自动写入Neo4j) / MANUAL(仅返回结果)
 *   schema:       可选的 schema 指引，帮助 LLM 对齐目标本体
 *   confidenceThreshold: 置信度阈值，低于此值的实体/关系/规则将被过滤
 *   maxEntities:  单次抽取实体数量上限
 *   extractRules: 是否同时抽取规则
 * </pre>
 */
public class ExtractionConfig {

    private String domain;
    private String syncMode = "AUTO";        // AUTO | MANUAL
    private Map<String, Object> schema;       // 可选的 schema 指引
    private double confidenceThreshold = 0.6;
    private int maxEntities = 50;
    private boolean extractRules = true;

    public ExtractionConfig() {}

    public ExtractionConfig(String domain) {
        this.domain = domain;
    }

    public ExtractionConfig(String domain, String syncMode, double confidenceThreshold) {
        this.domain = domain;
        this.syncMode = syncMode;
        this.confidenceThreshold = confidenceThreshold;
    }

    // ── Getters / Setters ──

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getSyncMode() { return syncMode; }
    public void setSyncMode(String syncMode) { this.syncMode = syncMode; }

    public Map<String, Object> getSchema() { return schema; }
    public void setSchema(Map<String, Object> schema) { this.schema = schema; }

    public double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public int getMaxEntities() { return maxEntities; }
    public void setMaxEntities(int maxEntities) { this.maxEntities = maxEntities; }

    public boolean isExtractRules() { return extractRules; }
    public void setExtractRules(boolean extractRules) { this.extractRules = extractRules; }
}
