package com.chinacreator.gzcm.workspace;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 查询历史记录 — 记录每次 ObjectQL/NLQ 查询。
 *
 * <p>F07-21 / C167 落库后，本对象由 {@code public.ecos_scenario_query_history}（V212）承载：
 * 持久化列只保留<b>审计安全</b>形态（{@code sql_digest} 摘要 + 主体 + traceId + 行数），
 * <b>禁存查询原文/结果集/自然语言问题原文</b>（安全红线 E-5，与分册 06 F06-03 同规则）。
 * 读侧回读时 {@link #question}/{@link #queryJson} 为不可重建（返回 null 或摘要），
 * {@link #sqlDigest}/{@link #traceId}/{@link #subjectId} 为落库字段。
 */
public class QueryRecord {

    private String id;
    private String question;      // 用户输入的自然语言问题（NLQ）或 ObjectQL 简称 — 落库后不回读原文
    private String queryJson;     // 实际执行的查询 JSON/DSL — 落库后不回读原文，仅存摘要
    private int resultCount;      // 返回行数
    private LocalDateTime timestamp;
    // ── F07-21 落库新增（只增字段，兼容既有前端读取） ─────────────────────────
    private String sqlDigest;     // 查询规范化摘要（sha256 hex，禁原文，E-5）
    private String traceId;       // 全链 traceId（TraceContext/MDC 贯通，F07-21 要点 2）
    private String subjectId;     // 执行主体用户 ID（非 PII，来自 SecurityContext）

    public QueryRecord() {
    }

    public QueryRecord(String question, String queryJson, int resultCount) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.question = question;
        this.queryJson = queryJson;
        this.resultCount = resultCount;
        this.timestamp = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getQueryJson() { return queryJson; }
    public void setQueryJson(String queryJson) { this.queryJson = queryJson; }
    public int getResultCount() { return resultCount; }
    public void setResultCount(int resultCount) { this.resultCount = resultCount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getSqlDigest() { return sqlDigest; }
    public void setSqlDigest(String sqlDigest) { this.sqlDigest = sqlDigest; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
}
