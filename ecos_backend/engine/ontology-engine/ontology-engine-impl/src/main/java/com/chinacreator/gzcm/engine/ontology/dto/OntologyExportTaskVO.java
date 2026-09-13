package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体导出任务 VO — T16-4 强类型返回容器。
 *
 * <p>对齐 {@code OntologyExportController} ConcurrentHashMap 存储的记录结构：
 * 列表接口返回摘要（不含 payload）；详情/创建接口返回完整记录（含 payload）。
 * {@code payload} 为导出载荷（JSON Map / CSV 字符串 / DDL 字符串，随 format 变化），
 * 保持 Object 类型豁免动态结构（T16-4: 导出 payload 动态结构豁免）。
 *
 * <p>{@code objectCount} 仅在 COMPLETED 时填充；{@code error} 仅在 FAILED 时填充。
 * NON_NULL 保证列表摘要与详情按需输出字段，与既有 Map 序列化行为一致。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyExportTaskVO {

    /** 导出任务 ID（exp_ 前缀） */
    private String id;

    /** 所属本体 ID */
    private String ontologyId;

    /** 导出格式（JSON / CSV / DDL） */
    private String format;

    /** 导出范围（FULL / ENTITIES / RELATIONSHIPS） */
    private String scope;

    /** 任务状态（COMPLETED / FAILED） */
    private String status;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 导出对象数（COMPLETED 时填充） */
    private Integer objectCount;

    /**
     * 导出载荷 — 动态结构（JSON Map / CSV / DDL 字符串），保持 Object 豁免。
     * 列表接口不返回（null → NON_NULL 省略）。
     */
    private Object payload;

    /** 错误消息（FAILED 时填充） */
    private String error;
}
