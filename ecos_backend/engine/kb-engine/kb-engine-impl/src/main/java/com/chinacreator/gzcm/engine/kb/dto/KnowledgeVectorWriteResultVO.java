package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 向量写入结果（B4/T3）。
 */
@Data
public class KnowledgeVectorWriteResultVO {

    /** 请求条数 */
    private int requested;

    /** 实际落库（insert + update）行数 */
    private int written;

    /** 跳过条数（文本为空 / 嵌入为空 / 维度不符 / 列表为空） */
    private int skipped;

    /** 目标向量维度（与 embedding_vec 列维度一致） */
    private int dimension;

    /** 实际使用的嵌入模型 */
    private String model;

    /** 跳过原因摘要（无跳过时为 null） */
    private String skippedReason;
}
