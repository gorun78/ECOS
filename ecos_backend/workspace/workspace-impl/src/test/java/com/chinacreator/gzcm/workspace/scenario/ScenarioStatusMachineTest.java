package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 详细设计-07 F07-07/C-2 状态机验收：非法迁移 409；激活需 islands 空 ∧ verdict≠EMPTY ∧ 认知可达；
 * 生命周期（暂停/恢复）走 scenario:write、初次激活走 scenario:activate，权限默认 DENY；
 * 迁移主体来自上下文（JWT）而非请求体。纯内存，权限经构造注入的 {@link MigrationPermissionChecker} 桩控制。
 */
class ScenarioStatusMachineTest {

    private static final MigrationPermissionChecker ALLOW_ALL = (perm, subject) -> true;
    private static final MigrationPermissionChecker DENY_ALL = (perm, subject) -> false;

    @Test
    @DisplayName("非法迁移（DRAFT→SUSPENDED、终态出边）一律 409")
    void illegalTransitionRejectedWith409() {
        ScenarioStatusMachine machine = new ScenarioStatusMachine(ALLOW_ALL);
        TransitionContext ctx = TransitionContext.builder().build();

        IllegalScenarioTransitionException draftToSuspend = assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition(ScenarioStatus.DRAFT, ScenarioStatus.SUSPENDED, ctx));
        assertEquals(409, draftToSuspend.getHttpStatus());
        assertEquals("ILLEGAL_TRANSITION", draftToSuspend.getCode());

        IllegalScenarioTransitionException terminal = assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition(ScenarioStatus.COMPLETED, ScenarioStatus.ACTIVE, ctx));
        assertEquals(409, terminal.getHttpStatus());
        assertEquals("TERMINAL_STATE", terminal.getCode());
    }

    @Test
    @DisplayName("激活需 islands 空 ∧ verdict≠EMPTY_SCENARIO ∧ 认知可达")
    void activateRequiresEmptyIslandsAndNonEmptyVerdict() {
        ScenarioStatusMachine machine = new ScenarioStatusMachine(ALLOW_ALL);

        TransitionContext empty = TransitionContext.builder()
                .verdict(CompletenessReport.Verdict.EMPTY_SCENARIO).build();
        assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition(ScenarioStatus.DRAFT, ScenarioStatus.ACTIVE, empty));

        TransitionContext islanded = TransitionContext.builder()
                .islands(Collections.singletonList(
                        new CompletenessReport.IslandView("kb-1", "孤儿", "KNOWLEDGE_BASE", "NO_REACHABLE_EDGE")))
                .build();
        IslandBindingException ex = assertThrows(IslandBindingException.class,
                () -> machine.assertTransition(ScenarioStatus.DRAFT, ScenarioStatus.ACTIVE, islanded));
        assertEquals(409, ex.getHttpStatus());

        TransitionContext noCognitive = TransitionContext.builder().cognitiveReachable(false).build();
        assertThrows(IllegalScenarioTransitionException.class,
                () -> machine.assertTransition(ScenarioStatus.DRAFT, ScenarioStatus.ACTIVE, noCognitive));

        machine.assertTransition(ScenarioStatus.DRAFT, ScenarioStatus.ACTIVE,
                TransitionContext.builder().build());
    }

    @Test
    @DisplayName("暂停/恢复需 scenario:write；缺权限默认 DENY → 403")
    void suspendAndResumeRequireScenarioWriteOtherwiseDeny() {
        ScenarioStatusMachine machine = new ScenarioStatusMachine(ALLOW_ALL);
        assertEquals("scenario:activate", machine.permissionPoint(ScenarioStatus.DRAFT, ScenarioStatus.ACTIVE));
        assertEquals("scenario:write", machine.permissionPoint(ScenarioStatus.ACTIVE, ScenarioStatus.SUSPENDED));
        assertEquals("scenario:write", machine.permissionPoint(ScenarioStatus.SUSPENDED, ScenarioStatus.ACTIVE));

        ScenarioStatusMachine denied = new ScenarioStatusMachine(DENY_ALL);
        TransitionContext ctx = TransitionContext.builder().build();

        ForbiddenOperationException suspend = assertThrows(ForbiddenOperationException.class,
                () -> denied.assertTransition(ScenarioStatus.ACTIVE, ScenarioStatus.SUSPENDED, ctx));
        assertEquals(403, suspend.getHttpStatus());
        assertEquals("scenario:write", suspend.getDetails().get("permission"));

        ForbiddenOperationException resume = assertThrows(ForbiddenOperationException.class,
                () -> denied.assertTransition(ScenarioStatus.SUSPENDED, ScenarioStatus.ACTIVE, ctx));
        assertEquals(403, resume.getHttpStatus());
        assertEquals("scenario:write", resume.getDetails().get("permission"));
    }

    @Test
    @DisplayName("迁移主体取自上下文（JWT），非请求体自填 userId")
    void transitionSubjectComesFromTokenNotRequestBody() {
        AtomicReference<String> seen = new AtomicReference<>();
        MigrationPermissionChecker capturing = (perm, subject) -> {
            seen.set(subject);
            return true;
        };
        ScenarioStatusMachine machine = new ScenarioStatusMachine(capturing);
        TransitionContext ctx = TransitionContext.builder().subject("jwt-user-42").build();

        machine.assertTransition(ScenarioStatus.DRAFT, ScenarioStatus.ACTIVE, ctx);

        assertEquals("jwt-user-42", seen.get(), "守卫只消费 JWT 主体，请求体 userId 无影响");
    }
}
