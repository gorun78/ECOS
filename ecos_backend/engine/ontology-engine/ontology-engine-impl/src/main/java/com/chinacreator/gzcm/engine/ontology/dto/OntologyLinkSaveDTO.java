package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * 链接（Link / Relationship）新增/编辑 DTO — T16-2。
 *
 * <p>字段对应 {@code OntologyDomainApiController.createLink} 实际消费的业务语义：
 * {@code sourceEntityId} + {@code targetEntityId} 必填（兼容旧字段 {@code sourceObjectId}）；
 * {@code relationshipType} 兼容旧字段 {@code linkType}。
 *
 * <p>{@code extras} 兜底未知字段，避免反序列化失败（与既有 Map body 全字段兼容）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyLinkSaveDTO {

    /** 源实体 ID（与旧字段 {@code sourceObjectId} 等价） */
    private String sourceEntityId;

    /** 兼容旧字段 {@code sourceObjectId}（当 sourceEntityId 空时取用） */
    private String sourceObjectId;

    /** 目标实体 ID（必填） */
    private String targetEntityId;

    /** 链接名称（可选） */
    private String name;

    /** 链接类型（与旧字段 {@code linkType} 等价） */
    private String relationshipType;

    /** 兼容旧字段 {@code linkType}（当 relationshipType 空时取用） */
    private String linkType;

    /** 描述（可选） */
    private String description;

    /** 透传扩展字段（兼容旧 body 中其他 key） */
    private Map<String, Object> extras = new java.util.LinkedHashMap<>();
}
