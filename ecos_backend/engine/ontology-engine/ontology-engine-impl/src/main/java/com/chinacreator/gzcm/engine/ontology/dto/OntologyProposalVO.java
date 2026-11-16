package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * 本体变更提案 — T16-3 强类型返回。
 *
 * <p>字段对齐 V174 合规表 {@code ecos_ontology.ecos_ontology_proposal} 的显式列投影
 * （D137 口径；DTO 键面保持不变以守「API 只增不改」）：
 * id / domain_code / proposal_type / target_entity / payload_json / snapshot_json /
 * status / author / reviewer / reviewer_comment / version_id /
 * optimistic_lock_version / create_time / update_time。
 *
 * <p>库列名与 DTO 键面不同名是刻意的：SQL 侧用列别名投影回 {@code payload} /
 * {@code snapshot} / {@code created_at} / {@code updated_at}，前端契约零变更。
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
