package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 文档接入重放入答 — 详设-07 §7.3.2 第 11 行组 B
 * （{@code POST /api/v1/knowledge/ingest/documents/{id}/retry} → 202 {@code {taskId}}）。
 */
@Data
public class KnowledgeDocumentIngestRetryVO {

    /** 新派发的 runtime-task 任务 ID（幂等键 objectKey 不变，重放同一原文对象）。 */
    private String taskId;

    /** 文档业务 ID（路径主键回显）。 */
    private String docId;

    /** 近源层对象 key（幂等键；取自 {@code kb_doc.object_key}，无行即 404，不合成）。 */
    private String objectKey;

    /** 固定 {@code SUBMITTED}（202 已受理）。 */
    private String status;
}
