package com.chinacreator.gzcm.engine.kb.doc.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 非结构化文档过渡实体（A3 过渡态，表 {@code ecos_knowledge.kb_doc}）。
 *
 * <p>承载文档级解析状态机（F2）：{@code queued → parsing → extracting → done/failed}。
 * A1 目标态落地（SOURCE_MINIO + 解析节点，doc 落 DW 层）后本类随之迁出。
 */
@Data
public class KbDoc {

    /** 文档 ID（业务主键，同近源层对象 key 的 {docId} 段） */
    private String docId;

    /** 上游数据源标识（对象 key 的 {source} 段） */
    private String source;

    /** 原始文件名 */
    private String originalFileName;

    /** 近源层对象 key：raw/unstructured/{source}/{docId}/{originalFileName} */
    private String objectKey;

    /** 内容类型（如 application/pdf） */
    private String contentType;

    /** 文件字节数 */
    private Long sizeBytes;

    /** 解析状态：queued/parsing/extracting/done/failed */
    private String parseStatus;

    /** 实际使用的分块大小 */
    private Integer chunkSize;

    /** 实际使用的分块重叠 */
    private Integer chunkOverlap;

    /** 分块总数 */
    private Integer chunkCount;

    /** 解析文本登记为 CURATED 资源时的 source_path */
    private String textSourcePath;

    /** 失败原因（parseStatus=failed 时非空） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
