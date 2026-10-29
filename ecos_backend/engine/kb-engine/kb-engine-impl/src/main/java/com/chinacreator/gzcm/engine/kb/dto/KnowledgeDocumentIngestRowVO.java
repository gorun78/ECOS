package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 文档接入记录行 — 详设-07 §7.3.2 第 11 行组 B（{@code GET /api/v1/knowledge/ingest/documents}）。
 *
 * <p>字段一一对应 {@code ecos_knowledge.kb_doc} 在册列（A3 过渡表），只投影真实行，
 * 不合成计数、不把「读不到」渲染成「0」。</p>
 */
@Data
public class KnowledgeDocumentIngestRowVO {

    /** 文档业务 ID（{@code doc_id}）。 */
    private String docId;

    /** 上游数据源标识（{@code source}）。 */
    private String source;

    /** 原始文件名（{@code original_file_name}）。 */
    private String originalFileName;

    /** 近源层对象 key（{@code object_key}，幂等键）。 */
    private String objectKey;

    /** 内容类型（{@code content_type}）。 */
    private String contentType;

    /** 文件字节数（{@code size_bytes}）。 */
    private Long sizeBytes;

    /** 解析状态（{@code parse_status}：queued/parsing/extracting/done/failed）。 */
    private String parseStatus;

    /** 实际分块大小（{@code chunk_size}）。 */
    private Integer chunkSize;

    /** 实际分块重叠（{@code chunk_overlap}）。 */
    private Integer chunkOverlap;

    /** 分块总数（{@code chunk_count}）。 */
    private Integer chunkCount;

    /** 解析文本 CURATED 资源定位（{@code text_source_path}，未达该阶段为 null）。 */
    private String textSourcePath;

    /** 失败原因（{@code error_message}，仅 failed 态非空）。 */
    private String errorMessage;

    /** 创建时间（ISO-8601）。 */
    private String createdAt;

    /** 最近更新时间（ISO-8601，排序键）。 */
    private String updatedAt;
}
