package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * LineageParseResponse — /api/v1/lineage/parse 响应体（PMO 新契约）。
 *
 * <p>结构兼容前端 LineageTab：</p>
 * <ul>
 *   <li>{@code nodes}：[{id, type, label, table}] — 去重节点（type=table/field/physical_table/olap_table/etl_job）</li>
 *   <li>{@code edges}：[{source, target, type, transform}] — 有向边</li>
 *   <li>{@code nodesCount}/{@code edgesCount}（新契约）+ {@code totalNodes}/{@code totalEdges}（旧契约兼容性）</li>
 *   <li>{@code format} / {@code parsed} / {@code message} — 兼容旧 envelope</li>
 *   <li>{@code success} / {@code addedNodes} / {@code addedLinks} / {@code lineage:{nodes,links}}
 *       — 兼容 LineageCompatController 旧契约扁平字段</li>
 * </ul>
 *
 * @author ECOS Ontology
 */
@Data
public class LineageParseResponse {

    /** 去重节点列表：id, type, label, table */
    private List<Map<String, Object>> nodes;

    /** 去重边列表：source, target, type, transform */
    private List<Map<String, Object>> edges;

    /** 节点数（新契约字段） */
    private Integer nodesCount;

    /** 边数（新契约字段） */
    private Integer edgesCount;

    /** 节点数（旧契约字段，兼容 LineageCompatController 语义） */
    private Integer totalNodes;

    /** 边数（旧契约字段） */
    private Integer totalEdges;

    /** 源数据格式（openlineage / atlas / sql） */
    private String format;

    /** 解析是否成功 */
    private Boolean parsed;

    /** 业务提示 */
    private String message;

    // ── 旧 LineageCompatController 兼容性字段 ──────────
    private Boolean success;
    private Integer addedNodes;
    private Integer addedLinks;
    private Map<String, Object> lineage;
}
