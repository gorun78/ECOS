package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * SqlLineageRequest — /api/v1/lineage/parse 请求体（PMO 新契约）。
 *
 * <p>强类型 DTO（反对 Map）：
 * <ul>
 *   <li>{@code query}：预处理后的结构化 SQL（INSERT/SELECT）或 OpenLineage/Atlas 的
 *       inputs/outputs JSON（缺 query 时走 OpenLineage/Atlas 解析）。</li>
 *   <li>{@code format}：openlineage / atlas / sql，缺省 openlineage。</li>
 * </ul>
 *
 * @author ECOS Ontology
 */
@Data
public class SqlLineageRequest {

    /** 源数据：SQL 文本或 OpenLineage/Atlas 结构化 JSON 字符串（兼容前端 payload 透传）。 */
    private String query;

    /**
     * 数据格式：openlineage / atlas / sql。
     * 缺省 openlineage；当 query 为空时使用 data 字段兜底（兼容老契约）。
     */
    private String format;

    /** 兼容旧契约的 payload（Map 字段，{格式来自 format}）。优先读 query。 */
    private Object data;

    /** 兼容旧字段别称：payload（知识工作台已知键）。 */
    private Object payload;
}
