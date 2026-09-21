package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 词条 ↔ 本体实体 绑定 DTO（T1 本体消费词条）。
 *
 * <p>用于 {@code PUT /api/v1/ontology/glossary/terms/{id}/binding}：
 * <ul>
 *   <li>{@code objectTypeId} 为实体主键（ecos_ontology_entity.id，如 {@code ent001}）→ 绑定；</li>
 *   <li>{@code objectTypeId} 为 null / 空串 → 解绑（清空归属并撤下主术语标记）；</li>
 *   <li>{@code primary} 为 true 且已绑定 → 同时置为该实体的主术语（同实体原主术语自动撤下）。</li>
 * </ul>
 *
 * <p>独立于 {@link OntologyGlossarySaveDTO}：通用编辑端点以 null 表示「不改」，
 * 无法表达「清空归属」，故绑定/解绑走本 DTO 的显式语义端点。
 */
@Data
public class GlossaryBindingDTO {

    /** 目标本体实体主键；null / 空串表示解绑 */
    private String objectTypeId;

    /** 是否设为主术语（objectTypeId 为空时忽略） */
    private Boolean primary;
}
