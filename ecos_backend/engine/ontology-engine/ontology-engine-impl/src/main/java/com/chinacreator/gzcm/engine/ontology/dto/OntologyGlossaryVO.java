package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 术语库列表项 VO。
 *
 * <p>字段对齐 {@code GlossaryController.toMap} 输出（与
 * {@code GlossaryEntity} 字段一致，时间字段序列化为 ISO 字符串）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyGlossaryVO {

    /** 主键 */
    private Long id;

    /** 术语编码 */
    private String code;

    /** 术语名称 */
    private String name;

    /** 术语定义 */
    private String definition;

    /** 所属领域 */
    private String domain;

    /** 业务负责人 */
    private String owner;

    /** 状态（DRAFT/REVIEW/PUBLISHED/DEPRECATED） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
