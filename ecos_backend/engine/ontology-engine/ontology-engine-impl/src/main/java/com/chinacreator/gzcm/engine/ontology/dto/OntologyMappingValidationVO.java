package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 映射一致性校验报告 — {@code POST /api/v1/ontology/mappings/validate} 响应。
 *
 * <p>PMO-B2 T2（方案 §2.3 C4）。校验失败时端点仍返回 {@code ApiResponse.success}（HTTP 200），
 * 由 {@link #valid} + {@link #issues} 表达结果；{@link #rejectCode} 为 {@code INVALID_MAPPING}
 * 时，kb 侧应据此拒绝该实体的实例抽取（不阻断其他实体）。
 */
@Data
public class OntologyMappingValidationVO {

    /** 校验是否全部通过 */
    private boolean valid;

    /** 本次校验的映射条数 */
    private int checkedCount;

    /** 失败拒绝码：{@link #valid}=false 时为 {@code INVALID_MAPPING}，通过时为 null */
    private String rejectCode;

    /** 问题明细（通过时为空列表） */
    private List<OntologyMappingIssueVO> issues = new ArrayList<>();
}
