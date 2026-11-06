package com.chinacreator.gzcm.engine.cognitive2.service.framework;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 认知执行状态机（详细设计-认知引擎 v1.0 §7.2；CE-09）。
 *
 * <p>语义与既有 Runtime 对齐：{@code CREATED → PLANNED → VALIDATING → RUNNING → VERIFYING
 * → SUCCEEDED}；终态 {@code SUCCEEDED/NEEDS_INPUT/NEEDS_HUMAN_REVIEW/REJECTED/INVALID_OUTPUT/
 * FAILED/BUDGET_EXCEEDED/CANCELLED}。{@code SUCCEEDED} 仅表示流程与声明的验证通过，
 * 不代表业务结论正确——业务结论状态由独立字段记录。</p>
 */
public final class CognitiveExecutionStateMachine {

    public enum Status {
        CREATED,
        PLANNED,
        VALIDATING,
        RUNNING,
        VERIFYING,
        SUCCEEDED,
        NEEDS_INPUT,
        NEEDS_HUMAN_REVIEW,
        REJECTED,
        INVALID_OUTPUT,
        FAILED,
        BUDGET_EXCEEDED,
        CANCELLED;

        public boolean terminal() {
            return switch (this) {
                case SUCCEEDED, NEEDS_INPUT, NEEDS_HUMAN_REVIEW, REJECTED, INVALID_OUTPUT,
                     FAILED, BUDGET_EXCEEDED, CANCELLED -> true;
                default -> false;
            };
        }
    }

    private static final Map<Status, Set<Status>> ALLOWED = new EnumMap<>(Status.class);

    static {
        ALLOWED.put(Status.CREATED, EnumSet.of(Status.PLANNED, Status.NEEDS_INPUT,
                Status.REJECTED, Status.CANCELLED, Status.FAILED));
        ALLOWED.put(Status.PLANNED, EnumSet.of(Status.VALIDATING, Status.NEEDS_INPUT,
                Status.NEEDS_HUMAN_REVIEW, Status.FAILED, Status.CANCELLED));
        ALLOWED.put(Status.VALIDATING, EnumSet.of(Status.RUNNING, Status.REJECTED,
                Status.NEEDS_INPUT, Status.FAILED, Status.CANCELLED));
        ALLOWED.put(Status.RUNNING, EnumSet.of(Status.VERIFYING, Status.NEEDS_HUMAN_REVIEW,
                Status.FAILED, Status.BUDGET_EXCEEDED, Status.CANCELLED));
        ALLOWED.put(Status.VERIFYING, EnumSet.of(Status.SUCCEEDED, Status.INVALID_OUTPUT,
                Status.NEEDS_HUMAN_REVIEW, Status.FAILED, Status.CANCELLED));
        for (Status terminal : Status.values()) {
            if (terminal.terminal()) {
                ALLOWED.put(terminal, EnumSet.noneOf(Status.class));
            }
        }
    }

    private CognitiveExecutionStateMachine() {
    }

    public static boolean isLegal(Status from, Status to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Status parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 进入 SUCCEEDED 的前置：来自 VERIFYING 且 Schema 与业务验证都通过。
     * 仅流程成功 ≠ 业务结论正确；业务结论另行记录。
     */
    public static boolean maySucceed(Status from, boolean schemaValid, boolean businessValid) {
        return from == Status.VERIFYING && schemaValid && businessValid && isLegal(from, Status.SUCCEEDED);
    }
}
