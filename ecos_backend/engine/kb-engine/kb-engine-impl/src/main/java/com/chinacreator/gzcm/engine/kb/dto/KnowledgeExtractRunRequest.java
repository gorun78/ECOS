package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.Map;

/**
 * 知识抽取运行提交请求 — 详设-07 §7.3.2 第 12 行
 * （{@code POST /api/v1/knowledge/extract/runs} 与 {@code POST /extract/dry-run} 共用入参）。
 *
 * <p><b>入参承载现状（本批如实声明，禁静默忽略）</b>：
 * <ul>
 *   <li>{@code ontologySnapshotId} — 唯一承载项：用作抽取范围（kb 快照表
 *       {@code kb_ontology_snapshot} 的外部业务键），空 / {@code ALL} = 全量本体；</li>
 *   <li>{@code dryRun} — 承载：{@code true} 时 runtime-task 侧不落库、不推进水位线；</li>
 *   <li>{@code sourceType} — 词表仅 {@code ONTOLOGY_MAPPING}（缺省同值）：唯一在位的抽取来源是
 *       本体映射台账；其他取值为 400 显式拒绝，不静默降级为「按全量跑」；</li>
 *   <li>{@code sourceRef} — <b>无载体</b>（抽取源由映射台账决定，表无 source_ref 列），非空即 400；</li>
 *   <li>{@code fieldMappings} — <b>无载体</b>（字段映射契约由本体映射台账承载，kb 不落映射表），
 *       非空即 400（C.1 映射驱动：禁请求体内联映射绕过台账校验）。</li>
 * </ul>
 */
@Data
public class KnowledgeExtractRunRequest {

    /** 抽取来源类型：当前仅承载 {@code ONTOLOGY_MAPPING}（缺省取该值）。 */
    private String sourceType;

    /** 来源引用（当前无载体，非空即 400）。 */
    private String sourceRef;

    /** 本体快照 ID（= 本体业务 ID；空 / ALL 表示全量本体快照）。 */
    private String ontologySnapshotId;

    /** 字段映射（当前无载体，非空即 400）。 */
    private Map<String, String> fieldMappings;

    /** 是否干跑预览（true = 只统计不落库、水位线不推进）。 */
    private Boolean dryRun;
}
