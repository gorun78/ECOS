package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 详细设计-07 F07-09 正式运行禁引用演练基线验收（C-7，唯一实现点）。
 * run_mode 解析经桩 {@link BaselineReferenceGuard.BaselineRunModeResolver} 注入，纯内存无 DB。
 */
class FormalBaselineReferenceTest {

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerReturning(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private BaselineReferenceGuard guardResolving(Map<String, RunMode> modes) {
        BaselineReferenceGuard.BaselineRunModeResolver resolver = modes::get;
        return new BaselineReferenceGuard(providerReturning(resolver));
    }

    @Test
    @DisplayName("FORMAL 运行引用 SANDBOX 基线 → 400 SANDBOX_REF_FORBIDDEN")
    void referencingSandboxBaselineYieldsHttp400WithCode() {
        BaselineReferenceGuard guard = guardResolving(singleton("r-base", RunMode.SANDBOX));
        ScenarioRunRequestValidator validator = new ScenarioRunRequestValidator(guard);

        Map<String, Object> body = new HashMap<>();
        body.put("runMode", "FORMAL");
        body.put("baselineRunId", "r-base");

        SandboxReferenceForbiddenException ex = assertThrows(SandboxReferenceForbiddenException.class,
                () -> validator.validate("s-1", body));
        assertEquals(400, ex.getHttpStatus());
        assertEquals("SANDBOX_REF_FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("FORMAL 引用 FORMAL 基线放行；SANDBOX 运行忽略基线校验")
    void formalBaselineReferenceAccepted() {
        BaselineReferenceGuard guard = guardResolving(singleton("r-base", RunMode.FORMAL));

        assertDoesNotThrow(() -> guard.assertAllowed(RunMode.FORMAL, RunMode.FORMAL, "r-base"));

        ScenarioRunRequestValidator validator = new ScenarioRunRequestValidator(guard);
        Map<String, Object> body = new HashMap<>();
        body.put("runMode", "FORMAL");
        body.put("baselineRunId", "r-base");
        ScenarioRunRequestValidator.RunCommand cmd = validator.validate("s-1", body);
        assertEquals(RunMode.FORMAL, cmd.getRunMode());
        assertEquals("r-base", cmd.getBaselineRunId());

        assertDoesNotThrow(() -> guard.assertAllowed(RunMode.SANDBOX, RunMode.SANDBOX, "r-base"),
                "创建态本身为 SANDBOX 时基线校验短路（演练上下文可引用任意产物）");
    }

    @Test
    @DisplayName("守卫实现唯一：run 校验与动作闭环共用同一 BaselineReferenceGuard 实例，拒绝结果一致")
    void guardImplementedOnceNotTwice() {
        Map<String, RunMode> modes = new HashMap<>();
        modes.put("r-sbx", RunMode.SANDBOX);
        BaselineReferenceGuard sharedGuard = guardResolving(modes);

        ScenarioRunRequestValidator runValidator = new ScenarioRunRequestValidator(sharedGuard);
        ScenarioActionService actionService = new ScenarioActionService(
                new ActionProposalValidator(), sharedGuard, providerReturning((DecisionGateway) null));

        Map<String, Object> body = new HashMap<>();
        body.put("runMode", "FORMAL");
        body.put("baselineRunId", "r-sbx");
        SandboxReferenceForbiddenException fromRun = assertThrows(SandboxReferenceForbiddenException.class,
                () -> runValidator.validate("s-1", body));

        SandboxReferenceForbiddenException fromAction = assertThrows(SandboxReferenceForbiddenException.class,
                () -> actionService.assertForecastRunFormal("r-sbx"));

        assertEquals(fromRun.getCode(), fromAction.getCode());
        assertEquals("SANDBOX_REF_FORBIDDEN", fromAction.getCode());
    }

    private static Map<String, RunMode> singleton(String k, RunMode v) {
        Map<String, RunMode> m = new HashMap<>();
        m.put(k, v);
        return m;
    }
}
