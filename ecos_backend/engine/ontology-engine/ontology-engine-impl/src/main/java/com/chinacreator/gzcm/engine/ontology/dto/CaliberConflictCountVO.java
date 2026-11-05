package com.chinacreator.gzcm.engine.ontology.dto;

/**
 * WAgent Readiness 供数：同指标多口径冲突计数（{@code GET /conflict-count}）。
 *
 * <p>键面：{@code count} + {@code evidence.criterion}（判据字面量
 * {@code multi-APPROVED-versions-per-code} 供消费方核对口径）。</p>
 */
public record CaliberConflictCountVO(int count, Evidence evidence) {

    public static CaliberConflictCountVO of(int count) {
        return new CaliberConflictCountVO(count, new Evidence("multi-APPROVED-versions-per-code"));
    }

    /** 判据证据：只暴露 criterion 一项，保持既有键面。 */
    public record Evidence(String criterion) {
    }
}
