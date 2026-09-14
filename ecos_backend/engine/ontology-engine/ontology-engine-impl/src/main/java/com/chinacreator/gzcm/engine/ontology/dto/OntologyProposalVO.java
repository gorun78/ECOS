package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * 本体变更提案 — T16-3 强类型返回。
 *
 * <p>字段对齐 {@code ecos_ontology_proposals} 表列（与 SQL
 * {@code SELECT * FROM ecos_ontology_proposals} 投影一致）：
 * id / domain_code / proposal_type / target_entity / payload / snapshot /
 * status / author / reviewer / reviewer_comment / version_id /
 * optimistic_lock_version / created_at / updated_at。
 *
 * <p>{@code payload} 与 {@code snapshot} 为 PG JSONB 列，PG JDBC
 * 返回 PGobject；POJO 字段用 {@code Object} 承载（与
 * {@code OntologyEntityVO.mapping} 同款处理，仍属动态嵌套数据豁免）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyProposalVO {

    /** 自增主键 */
    private Object id;

    /** 领域编码（domain_code） */
    private String domainCode;

    /** 提案类型（CREATE_ENTITY/ADD_PROPERTY/MODIFY_PROPERTY/...） */
    private String proposalType;

    /** 目标实体编码 */
    private String targetEntity;

    /** 变更内容 JSONB（PGobject → 反序列化后对象） */
    private Object payload;

    /** 变更前快照 JSONB（PGobject → 反序列化后对象） */
    private Object snapshot;

    /** 状态（DRAFT/PENDING/APPROVED/REJECTED/EXECUTED/verified） */
    private String status;

    /** 提案人（author） */
    private String author;

    /** 审批人（reviewer） */
    private String reviewer;

    /** 审批意见（reviewer_comment） */
    private String reviewerComment;

    /** 关联版本 id（approve-and-publish 后回填） */
    private Object versionId;

    /** 乐观锁版本号（PMO-29 §4.2） */
    private Object optimisticLockVersion;

    /** 创建时间（ISO 字符串） */
    private String createdAt;

    /** 更新时间（ISO 字符串） */
    private String updatedAt;
}
