package com.chinacreator.gzcm.engine.ai.approval;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * L3 审批凭证作用域单源（原 {@code wagent.approval.WAgentApprovalScope}，2026-11-23 去 wagent 化迁入本包）。
 *
 * <p>凭证绑定 {@code scope_type + scope_id}，<b>不得跨对象复用</b>：给对象 A 批的凭证
 * 不能用于对象 B，也不能用于别类动作。</p>
 *
 * <p><b>取值只增不改</b>：三个既有 wire 值逐字保留（历史签发凭证仍须可校验），
 * 新增场景逻辑的 {@link #PIPELINE_PUBLISH} 为 additive。</p>
 */
public enum L3ApprovalScope {

    CANDIDATE_PUBLISH("候选发布（ontology / knowledge 共用，靠 candidateId 区分）"),
    ACTION_COMMIT("行动提交（经属主 Engine 写端点）"),
    RUN_APPROVAL("Run 人工闸清闸（awaiting_approval → executing，靠 runId 区分）"),
    /** 场景逻辑编排发布/启用（2026-11-23 承接批新增；scopeId = 编排 id）。 */
    PIPELINE_PUBLISH("逻辑编排发布（scopeId = ecos_aip_pipeline.id）");

    private final String description;

    L3ApprovalScope(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public static final Set<String> ALL = Arrays.stream(values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());

    /** 严格解析：未登记字面量返回 null（调用方按 400 处理，禁静默默认）。 */
    public static L3ApprovalScope of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return ALL.contains(key) ? valueOf(key) : null;
    }
}
