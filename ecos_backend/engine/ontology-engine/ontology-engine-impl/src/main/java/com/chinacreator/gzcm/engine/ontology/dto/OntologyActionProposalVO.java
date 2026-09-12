package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 动作提案（占位）VO — {@code GET /ontology/actions/{id}/proposals} 返回单元。
 *
 * <p>当前为占位实现（service 返回空列表），字段对齐"提案 = 一条对象 id 字符串列表"语义。
 * 后续如有提案持久化，可在该 VO 中扩展 {@code proposalType / status / payload} 字段，无需
 * 前端切换 URL/JSON 外层结构。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyActionProposalVO {

    /** 提案主键（占位实现下为 null） */
    private String id;

    /** 关联动作主键（占位实现下为 null） */
    private String actionId;

    /** 提案类型（占位实现下为 null；预期值如 CREATE_ENTITY / ADD_PROPERTY） */
    private String proposalType;

    /** 提案状态（占位实现下为 null；预期值如 PENDING / APPROVED / REJECTED） */
    private String status;

    /** 提案载荷（占位实现下为 null） */
    private java.util.Map<String, Object> payload;
}
