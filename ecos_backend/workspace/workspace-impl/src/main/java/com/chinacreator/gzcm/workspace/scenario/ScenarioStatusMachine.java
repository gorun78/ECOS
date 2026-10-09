package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景状态机与迁移守卫（卷07 F07-07，C-2 迁移表唯一实现）。
 * <p>非法迁移 409；激活需 islands 空 ∧ verdict≠EMPTY_SCENARIO ∧ 认知可达；COMPLETED→* 终态；
 * 权限经 {@link MigrationPermissionChecker}（默认 DENY）。目标态 DRAFT 一律非法（回退走副本，属 09 册）。</p>
 */
@Service
public class ScenarioStatusMachine {

    public static final String PERM_ACTIVATE = "scenario:activate";
    public static final String PERM_WRITE = "scenario:write";

    private final MigrationPermissionChecker permissionChecker;

    public ScenarioStatusMachine(MigrationPermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    /** 权限点：终态/回退无权限点（直接非法）。仅初次激活（DRAFT→ACTIVE）走 activate；
     *  暂停/恢复/完成等生命周期翻转一律 write（恢复≠初次激活）。 */
    public String permissionPoint(ScenarioStatus from, ScenarioStatus to) {
        if (from == null || to == null || to == ScenarioStatus.DRAFT || from == ScenarioStatus.COMPLETED) {
            return null;
        }
        if (from == ScenarioStatus.DRAFT && to == ScenarioStatus.ACTIVE) {
            return PERM_ACTIVATE;
        }
        // SUSPENDED / COMPLETED 属写侧；SUSPENDED→ACTIVE（恢复）亦写侧
        return PERM_WRITE;
    }

    /** 断言迁移合法（含权限 + 前置条件）；失败抛领域异常。 */
    public void assertTransition(ScenarioStatus from, ScenarioStatus to, TransitionContext ctx) {
        assertTransition(from, to, ctx, permissionChecker);
    }

    private void assertTransition(ScenarioStatus from, ScenarioStatus to, TransitionContext ctx,
                                  MigrationPermissionChecker checker) {
        if (from == null || to == null) {
            throw IllegalScenarioTransitionException.illegal(str(from), str(to));
        }
        String perm = permissionPoint(from, to);
        if (perm != null && !checker.isAllowed(perm, ctx.getSubject())) {
            throw new ForbiddenOperationException(perm);
        }
        if (to == ScenarioStatus.DRAFT) {
            throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
        }
        if (from == ScenarioStatus.COMPLETED) {
            throw IllegalScenarioTransitionException.terminal(from.name());
        }
        switch (from) {
            case DRAFT:
                if (to == ScenarioStatus.SUSPENDED) {
                    throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
                }
                if (to == ScenarioStatus.COMPLETED) {
                    throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
                }
                assertActivate(ctx);
                break;
            case ACTIVE:
                if (to == ScenarioStatus.SUSPENDED) {
                    break;
                }
                if (to == ScenarioStatus.COMPLETED) {
                    if (!ctx.isFormalRunExists()) {
                        throw IllegalScenarioTransitionException.noFormalRun();
                    }
                    break;
                }
                if (to == ScenarioStatus.ACTIVE) {
                    break;
                }
                throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
            case SUSPENDED:
                if (to == ScenarioStatus.ACTIVE) {
                    if (!ctx.isIslandsEmpty()) {
                        throw new IslandBindingException(toIslandPayload(ctx));
                    }
                    break;
                }
                if (to == ScenarioStatus.SUSPENDED) {
                    break;
                }
                throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
            default:
                throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
        }
    }

    /** 结构合法的目标态（前端按钮态唯一来源；权限拒绝在执行态兜底 403）。 */
    public List<ScenarioStatus> allowedTransitions(ScenarioStatus from, TransitionContext ctx) {
        List<ScenarioStatus> allowed = new ArrayList<>();
        if (from == null) {
            return allowed;
        }
        for (ScenarioStatus to : ScenarioStatus.values()) {
            if (to == from) {
                continue;
            }
            try {
                assertTransitionWithoutPermission(from, to, ctx);
                allowed.add(to);
            } catch (ScenarioDomainException ignored) {
                // 非法/前置未满足的态不出现在按钮集
            }
        }
        return allowed;
    }

    private void assertTransitionWithoutPermission(ScenarioStatus from, ScenarioStatus to, TransitionContext ctx) {
        // 复用主逻辑但跳过权限（allowedTransitions 表达“结构可达态”，与调用者权限解耦）
        assertTransition(from, to, ctx, withAllowAll());
    }

    private MigrationPermissionChecker withAllowAll() {
        return (perm, subject) -> true;
    }

    private void assertActivate(TransitionContext ctx) {
        if (ctx.getVerdict() == CompletenessReport.Verdict.EMPTY_SCENARIO) {
            throw IllegalScenarioTransitionException.incomplete();
        }
        if (!ctx.isIslandsEmpty()) {
            throw new IslandBindingException(toIslandPayload(ctx));
        }
        if (!ctx.isCognitiveReachable()) {
            throw IllegalScenarioTransitionException.incomplete();
        }
    }

    private List<java.util.Map<String, Object>> toIslandPayload(TransitionContext ctx) {
        List<java.util.Map<String, Object>> payload = new ArrayList<>();
        for (CompletenessReport.IslandView v : ctx.getIslands()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("bindingId", v.getBindingId());
            m.put("name", v.getName());
            m.put("bindingType", v.getBindingType());
            m.put("reason", v.getReason());
            payload.add(m);
        }
        return payload;
    }

    private String str(ScenarioStatus s) {
        return s == null ? "null" : s.name();
    }
}
