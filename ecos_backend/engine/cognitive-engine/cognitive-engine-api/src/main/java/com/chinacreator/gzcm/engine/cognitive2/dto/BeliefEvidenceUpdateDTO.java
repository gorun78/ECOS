package com.chinacreator.gzcm.engine.cognitive2.dto;

import lombok.Data;

/**
 * 不确定性判断"新证据加权更新"请求 DTO（PMO-59 P2b，api-contract 预登记
 * {@code POST /api/v1/cognitive/beliefs/{variable}/update-by-evidence} 入参）。
 *
 * <p>{@code variable}（路径）= variable_name；{@code domain} 必填强制（跨域重名隔离，
 * Phase 1 验收记录残留风险 4 落点）；{@code evidenceId} 引用已登记证据
 * （ecos_cognitive_evidence.id，证据溯源与 last_evidence_id 一致）。</p>
 */
@Data
public class BeliefEvidenceUpdateDTO {

    /** 业务域（必填，强制查询域隔离） */
    private String domain;

    /** 触发更新的证据 id（必填，引用已登记证据） */
    private String evidenceId;
}
