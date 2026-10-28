package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 抽取水位线条目 — 详设-07 §7.3.2 第 13 行（{@code GET /api/v1/knowledge/extract/watermarks}）。
 *
 * <p>字段一一对应 {@code ecos_knowledge.ecos_kb_extract_watermark}（V179）在册列，
 * 只投影活行（{@code is_deleted=0}），不合成列、不做聚合。</p>
 */
@Data
public class KnowledgeExtractWatermarkVO {

    /** 本体业务 ID（外部键）。 */
    private String ontologyId;

    /** 本体实体 code。 */
    private String entityCode;

    /** DW 层数据资源 ID（外部键）。 */
    private String resourceId;

    /** 增量水位（字符串形态）。 */
    private String watermark;

    /** 写入该水位的链路 traceId。 */
    private String traceId;

    /** 数据域分组。 */
    private String domain;

    /** 记录版本号（乐观锁/留痕）。 */
    private String versionNo;

    /** 最近更新时间（ISO-8601）。 */
    private String updatedAt;
}
