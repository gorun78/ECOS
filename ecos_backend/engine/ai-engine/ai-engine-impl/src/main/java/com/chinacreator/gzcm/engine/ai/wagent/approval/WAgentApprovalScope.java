package com.chinacreator.gzcm.engine.ai.wagent.approval;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批凭证作用域单源（详细设计-10 §5.2 #20/#24b 的 L3 面）。
 *
 * <p>凭证绑定 {@code scope_type + scope_id}，<b>不得跨对象复用</b>：给候选 A 批的凭证
 * 不能用于候选 B，也不能用于行动提交。</p>
 */
public enum WAgentApprovalScope {

    CANDIDATE_PUBLISH("候选发布（ontology / knowledge 共用，靠 candidateId 区分）"),
    ACTION_COMMIT("行动提交（经属主 Engine 写端点）"),
    RUN_APPROVAL("Run 人工闸清闸（awaiting_approval → executing，靠 runId 区分）");

    private final String description;

    WAgentApprovalScope(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public static final Set<String> ALL = Arrays.stream(values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());

    /** 严格解析：未登记字面量返回 null（调用方按 400 处理，禁静默默认）。 */
    public static WAgentApprovalScope of(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return ALL.contains(key) ? valueOf(key) : null;
    }
}
