package com.chinacreator.gzcm.workspace;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 查询历史记录 — 记录每次 ObjectQL/NLQ 查询。
 */
public class QueryRecord {

    private String id;
    private String question;      // 用户输入的自然语言问题（NLQ）或 ObjectQL 简称
    private String queryJson;     // 实际执行的查询 JSON/DSL
    private int resultCount;      // 返回行数
    private LocalDateTime timestamp;

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
}
