package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 待审核抽取文件条目 VO — {@code GET /api/v1/knowledge/extract/files} 出参。
 *
 * <p>字段名与前端 {@code fetchExtractCandidateFiles()}（knowledgeApi.ts）声明的结构对齐：
 * {@code fileId / fileName / status / candidateCount / checksum / createdAt / error}。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractFileVO {

    /** 文件 ID（= 抽取草稿主键 extraction_drafts.id） */
    private String fileId;

    /** 原文件名 */
    private String fileName;

    /** 抽取状态（UPLOADED / PARSING / EXTRACTING / PENDING_REVIEW / APPROVED / REJECTED） */
    private String status;

    /** 候选数（实体 + 关系计数） */
    private int candidateCount;

    /** 校验和（当前数据源无该列，恒为 null） */
    private String checksum;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 失败原因（仅失败时有值） */
    private String error;
}
