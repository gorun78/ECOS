package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 非结构化文档登记与解析编排结果（B5-1 / A3 过渡态 SOP-2）。
 */
@Data
public class KnowledgeDocIngestResultVO {

    /** 文档 ID */
    private String docId;

    /** 上游数据源标识 */
    private String source;

    /** 近源层对象 key（raw/unstructured/{source}/{docId}/{fileName}） */
    private String objectKey;

    /** 解析状态终态：done / failed */
    private String parseStatus;

    /** 分块数（落 kb_doc_chunk） */
    private int chunkCount;

    /** 向量写入成功行数（llm-gateway 嵌入 + embedding_vec） */
    private int vectorWritten;

    /** 向量跳过行数（文本为空 / 嵌入为空 / 维度不符 / 网关不可用） */
    private int vectorSkipped;

    /** 解析文本登记为 CURATED 资源时的 source_path（失败登记时为空） */
    private String textSourcePath;

    /** 原文资源是否已登记（数据工作台 RAW/UNSTRUCTURED） */
    private boolean originalRegistered;

    /** 解析文本是否已登记为 CURATED 资源 */
    private boolean parsedRegistered;

    /** 失败原因（parseStatus=failed 时非空） */
    private String errorMessage;

    /** 耗时毫秒 */
    private long durationMs;
}
