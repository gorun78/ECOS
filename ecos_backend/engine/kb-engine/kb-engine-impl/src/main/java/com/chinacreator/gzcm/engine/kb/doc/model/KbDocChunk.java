package com.chinacreator.gzcm.engine.kb.doc.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 非结构化文档分块实体（A3 过渡态，表 {@code ecos_knowledge.kb_doc_chunk}）。
 *
 * <p>注意：这是 kb 自有过渡表，**不是** DW 层 {@code doc_chunk}（A1 目标态产物）。
 * 分块切分在 A3 由 kb 代做，A1 落地后须迁至数据工作台解析节点（技术债退出条件见
 * knowledge-workbench-replan-v1 §3.3）。
 */
@Data
public class KbDocChunk {

    /** 分块主键（≤64） */
    private String id;

    /** 所属文档 ID */
    private String docId;

    /** 上游数据源标识 */
    private String source;

    /** 分块序号（同文档内从 0 递增，与 doc_id 组成唯一键） */
    private Integer chunkIndex;

    /** 分块文本内容 */
    private String content;

    /** 在原文中的起始字符偏移 */
    private Integer charStart;

    /** 在原文中的结束字符偏移（不含） */
    private Integer charEnd;

    /** 分块元数据（JSON 字符串，写库时 CAST 为 jsonb） */
    private String metadata;

    /** 分块状态：active/deprecated */
    private String status;

    /** 关联向量行 ID（ecos_knowledge.knowledge_embedding.id），可空 */
    private String embeddingId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
