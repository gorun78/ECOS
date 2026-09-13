package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 本体变更提案新增/编辑 SaveDTO。
 *
 * <p>字段对齐 {@code POST /api/v1/ontology/proposals} /
 * {@code PUT /api/v1/ontology/proposals/{id}} 实际消费的业务语义。
 *
 * <p>兼容旧字段：
 * <ul>
 *   <li>{@code type} / {@code source} → {@code proposalType}（兼容驼峰）</li>
 *   <li>{@code domain_code} → {@code domainCode}</li>
 *   <li>{@code targetId} / {@code target_entity} → {@code targetEntity}</li>
 *   <li>{@code proposedBy} → {@code author}</li>
 * </ul>
 * 兼容字段通过 {@code @JsonProperty} 映射，不影响主字段语义。
 */
@Data
public class OntologyProposalSaveDTO {

    /** 领域编码（新增必填，空则默认 "default"） */
    private String domainCode;

    /** 提案类型（CREATE_ENTITY/ADD_PROPERTY/...，新增必填） */
    private String proposalType;

    /** 目标实体编码（可选） */
    private String targetEntity;

    /** 提案人（可选，默认 "system"） */
    private String author;

    /** 兼容旧字段：title（存入 payload） */
    private String title;

    /** 兼容旧字段：description（存入 payload） */
    private String description;

    /** 兼容旧字段：changeType（存入 payload） */
    private String changeType;

    /** 变更内容 Map（可选，与 title/description 平铺字段合并后序列化入库） */
    private Map<String, Object> payload;

    /** 变更前快照（可选，对象或字符串均可，序列化入库） */
    private Object snapshot;

    /** 兼容旧字段：ptype（body 平铺时作为 proposalType 备选） */
    @JsonProperty("ptype")
    private String ptypeAlt;

    /** 兼容旧字段：source（body 平铺时作为 proposalType 备选） */
    @JsonProperty("source")
    private String sourceAlt;

    /** 兼容旧字段：targetId（body 平铺时作为 targetEntity 备选） */
    @JsonProperty("targetId")
    private String targetIdAlt;

    /** 兼容旧字段：domain_code（body 平铺时作为 domainCode 备选） */
    @JsonProperty("domain_code")
    private String domainCodeAlt;

    /** 兼容旧字段：target_entity（body 平铺时作为 targetEntity 备选） */
    @JsonProperty("target_entity")
    private String targetEntityAlt;

    /** 兼容旧字段：proposedBy（body 平铺时作为 author 备选） */
    @JsonProperty("proposedBy")
    private String proposedByAlt;

    /** 兼容旧字段：proposal_type（body 平铺时作为 proposalType 备选） */
    @JsonProperty("proposal_type")
    private String proposalTypeAlt;

    /** 兼容旧字段：targetType（body 平铺时作为 proposalType 备选） */
    @JsonProperty("targetType")
    private String targetTypeAlt;

    /** 审批人（approve / reject / approve-and-publish 端点 body 字段） */
    private String reviewer;

    /** 审批意见（approve / reject / approve-and-publish 端点 body 字段） */
    @JsonProperty("reviewComment")
    private String reviewComment;
}
