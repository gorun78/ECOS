package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 知识实体接入结果 VO — POST {@code /api/v1/knowledge/ingest} 响应体。
 *
 * <p>{@code nodeId} 命中/新建的 graph_node.id（= entityId）；
 * {@code idempotent} 命中已有节点时为 true（重新落库已存在节点）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeIngestResult {

    private final String nodeId;
    private final boolean idempotent;

    public KnowledgeIngestResult(String nodeId, boolean idempotent) {
        this.nodeId = nodeId;
        this.idempotent = idempotent;
    }

    public String getNodeId() { return nodeId; }
    public boolean isIdempotent() { return idempotent; }
}
