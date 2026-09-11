package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 知识条目 VO — 对应 {@code ecos_dq.dq_knowledge_entry}（PMO-48-D T16）。
 *
 * <p>字段映射 V114 表（驼峰）；{@link #embeddingJson} 对应 {@code embedding} TEXT 列
 * （JSON 数组字符串，DIM=1024，pgvector 未启用降级）。</p>
 *
 * @author PMO-48-D T16
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqKnowledgeEntryVO {

    /** 知识条目 ID (VARCHAR(36) PK) */
    private String id;

    /** 来源类型: RULE / ALERT / WORK_ORDER */
    private String sourceType;

    /** 来源记录 ID（对应 dq_rule.id / dq_alert_record.id / dq_work_order.id） */
    private String sourceId;

    /** 分类: rule-def / alert / fix-pattern / rule-hit */
    private String category;

    /** 知识条目标题 (<= 191) */
    private String title;

    /** 摘要（供列表展示） */
    private String summary;

    /** 知识全文 Markdown */
    private String contentMd;

    /** 结构化实体 JSONB 文本（ruleName / assetId / ruleType / alertLevel 等） */
    private String entityJson;

    /** 向量列（JSON 数组字符串 DIM=1024，pgvector 未启用降级为 TEXT 存） */
    private String embeddingJson;

    /** 预计算相似分参考 (0.0-1.0) */
    private Double scoreHint;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
