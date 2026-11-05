package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 口径版本状态推进回执（approve / retire）。
 *
 * <p>键面：{@code code}、{@code versionNo}、{@code status} 恒在；{@code approvedBy} 仅审批腿出现
 * （退休腿照旧为 3 键，故 NON_NULL 省略）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaliberStateChangeVO(
        String code,
        String versionNo,
        String status,
        String approvedBy) {

    public static CaliberStateChangeVO approved(String code, String versionNo, String approver) {
        return new CaliberStateChangeVO(code, versionNo, "APPROVED", approver);
    }

    public static CaliberStateChangeVO retired(String code, String versionNo) {
        return new CaliberStateChangeVO(code, versionNo, "RETIRED", null);
    }
}
