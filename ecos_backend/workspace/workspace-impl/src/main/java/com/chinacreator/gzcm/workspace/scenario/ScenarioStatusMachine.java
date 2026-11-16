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
        // D110（2026-11-15 重排）：三段式 —— ①纯结构非法边 ②权限 ③带载荷的前置条件。
        // 原顺序是「权限 → 结构」，导致未授权主体对**本就非法的边**也只能看到 403
        // （D57 缺权限键时 admin 对 DRAFT→SUSPENDED 拿到 403 scenario:write，
        // 于是会去申请一个根本不存在的权限，而真正原因是他想做一件规则上不允许的事）。
        // 但**不能**简单把整段合法性提到权限之前：`assertActivate` / SUSPENDED→ACTIVE
        // 会抛 IslandBindingException，其响应体携带孤岛明细（bindingId/name/reason）——
        // 未授权主体先跑完就能读到本该无权查看的绑定清单。
        // 因此只把**不携带任何数据**的结构判定（目标态 DRAFT、终态出边、DRAFT 的非法出边、
        // 以及各状态下不存在的目标态）提前；所有带载荷的前置判定留在权限之后。
        assertStructurallyLegal(from, to);
        String perm = permissionPoint(from, to);
        if (perm != null && !checker.isAllowed(perm, ctx.getSubject())) {
            throw new ForbiddenOperationException(perm);
        }
        assertPreconditionsSatisfied(from, to, ctx);
    }

    /**
     * ① 纯结构非法边：判定「这条边在状态机里存不存在」，不读任何业务数据、不产生载荷。
     *
     * <p>顺序在权限之前是安全的：它只回答「DRAFT 能不能直接到 SUSPENDED」这类
     * 与调用者无关的规则问题，泄露的仅是设计文档里公开的状态机形状。</p>
     */
    private void assertStructurallyLegal(ScenarioStatus from, ScenarioStatus to) {
        if (from == null || to == null) {
            throw IllegalScenarioTransitionException.illegal(str(from), str(to));
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
                break;
            case ACTIVE:
            case SUSPENDED:
                break;
            default:
                throw IllegalScenarioTransitionException.illegal(from.name(), to.name());
        }
    }

    /**
     * ③ 前置条件（可能携带业务载荷）—— 必须在权限校验通过之后才执行。
     *
     * <p>{@code assertActivate} 与 {@code SUSPENDED→ACTIVE} 都会抛
     * {@link IslandBindingException}，其响应体含孤岛明细；放在权限前会构成越权信息泄露。</p>
     */
    private void assertPreconditionsSatisfied(ScenarioStatus from, ScenarioStatus to,
                                              TransitionContext ctx) {
        switch (from) {
            case DRAFT:
                // 结构上 DRAFT 只剩 →ACTIVE 一条出边（其余已在 ① 拦掉）
                assertActivate(ctx);
                break;
            case ACTIVE:
                if (to == ScenarioStatus.COMPLETED && !ctx.isFormalRunExists()) {
                    throw IllegalScenarioTransitionException.noFormalRun();
                }
                break;
            case SUSPENDED:
                if (to == ScenarioStatus.ACTIVE && !ctx.isIslandsEmpty()) {
                    throw new IslandBindingException(toIslandPayload(ctx));
                }
                break;
            default:
                break;
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
