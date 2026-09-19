package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 向量写入请求项（B4/T3：文本 → 嵌入 → 写 embedding_vec）。
 *
 * <p>仅承载「已有文本 → 向量化」的入参，不涉及 chunk 切分（chunk 切分按方案 §4
 * 属数据工作台解析节点产物，知识工作台只消费）。
 */
@Data
public class KnowledgeVectorWriteItem {

    /** 行主键（可选）；为空时按 articleId#chunkIndex 生成确定性 UUID，保证重复写入幂等 */
    private String id;

    /** 文档/文章 ID（落 document_id） */
    private String articleId;

    /** 分片序号（落 chunk_index） */
    private Integer chunkIndex;

    /** 文本内容（落 content，并作为嵌入输入） */
    private String text;

    /** token 数（可选，默认 0） */
    private Integer tokenCount;
}
