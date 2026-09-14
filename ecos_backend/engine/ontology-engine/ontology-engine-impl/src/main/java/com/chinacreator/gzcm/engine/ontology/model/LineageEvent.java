package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * 血缘事件实体 — 对应 kb_lineage_event 表。
 *
 * <p>一行 = 一次 lineage parse 事件，nodes/edges 以 JSON 字符串中转，
 * 由 {@link com.chinacreator.gzcm.engine.ontology.repository.LineageEventRepository}
 * 负责 JDBC 映射。</p>
 */
public class LineageEvent {

    /** 自增主键 */
    private Long id;
    /** 事件业务 ID (UUID，不含连字符) */
    private String eventId;
    /** 解析时使用的查询/输入描述 */
    private String query;
    /** 血缘格式: openlineage/atlas */
    private String format;
    /** 血缘节点列表 JSON 字符串（[{id, label, type}, ...]） */
    private String nodesJson;
    /** 血缘边列表 JSON 字符串（[{source, target, type}, ...]） */
    private String edgesJson;
    /** 解析时间 */
    private LocalDateTime parseAt;
    /** 逻辑删除标记: 0=未删除, 1=已删除 */
    private Integer isDeleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getNodesJson() { return nodesJson; }
    public void setNodesJson(String nodesJson) { this.nodesJson = nodesJson; }
    public String getEdgesJson() { return edgesJson; }
    public void setEdgesJson(String edgesJson) { this.edgesJson = edgesJson; }
    public LocalDateTime getParseAt() { return parseAt; }
    public void setParseAt(LocalDateTime parseAt) { this.parseAt = parseAt; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
}
