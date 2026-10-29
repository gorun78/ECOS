package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 文档接入提交应答 — 详设-07 §7.3.2 第 11 行组 A
 * （{@code POST /api/v1/knowledge/ingest/documents} → 202 {@code {taskId,objectKey}}）。
 *
 * <p>{@code docId} 为 additive 字段：与 {@code GET /ingest/documents} 列表行及
 * {@code POST /ingest/documents/{id}/retry} 的路径主键对齐，供受理方回读与重放。</p>
 */
@Data
public class KnowledgeDocumentIngestSubmitVO {

    /** runtime-task 任务 ID（异步执行腿 {@code KB_DOC_INGEST} 的承流主键）。 */
    private String taskId;

    /** 近源层对象 key（契约幂等键；原文已写 MinIO RAW 后方返回）。 */
    private String objectKey;

    /** 文档业务 ID（{@code ecos_knowledge.kb_doc.doc_id}）。 */
    private String docId;

    /** 固定 {@code SUBMITTED}（202 已受理；解析进度经列表端点回读 kb_doc 状态机）。 */
    private String status;
}
