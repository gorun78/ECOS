package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.SandboxReferenceForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 基线引用守卫单测（详细设计-07 F07-09 / C155）。
 *
 * <p>FORMAL 运行引用 SANDBOX 演练产物 → {@code SandboxReferenceForbiddenException}（400
 * SANDBOX_REF_FORBIDDEN）；基线运行不存在 → 400 引用悬空；无引用（null/空白）直接放行不查库。
 * 断言错误码串，证明拒绝经真实异常而非"200-swallow"。</p>
 */
class BaselineReferenceGuardTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BaselineReferenceGuard guard = new BaselineReferenceGuard(jdbc);

    @SuppressWarnings("unchecked")
    private void stubRunFound(String runMode) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any())).thenReturn(runMode);
    }

    @Test
    void referencingSandboxBaselineYieldsSandboxReferenceForbidden() {
        stubRunFound("SANDBOX");

        SandboxReferenceForbiddenException ex =
                assertThrows(SandboxReferenceForbiddenException.class,
                        () -> guard.assertFormalMayReference("run_sbx_1"));
        assertEquals("SANDBOX_REF_FORBIDDEN", ex.getErrorCodeString());
    }

    @Test
    void formalBaselineReferenceAccepted() {
        stubRunFound("FORMAL");

        assertDoesNotThrow(() -> guard.assertFormalMayReference("run_fml_1"));
    }

    @Test
    void missingBaselineRunYieldsBusinessException400() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertFormalMayReference("run_missing"));
        // 服务层业务错误码 400（引用悬空）
        assertEquals(400, ex.getErrorCode());
    }

    @Test
    void blankReferenceShortCircuitsWithoutDbAccess() {
        assertDoesNotThrow(() -> guard.assertFormalMayReference(null));
        assertDoesNotThrow(() -> guard.assertFormalMayReference("   "));
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any());
    }

    // ── assertForecastRunIsFormal（F07-10-2 动作提案锚点侧，语义与 assertFormalMayReference 相反）──

    @Test
    void forecastAnchorBlankIsRejectedBeforeDbAccess() {
        assertThrows(ActionValidationException.class, () -> guard.assertForecastRunIsFormal(null));
        assertThrows(ActionValidationException.class, () -> guard.assertForecastRunIsFormal("  "));
    }

    @Test
    void forecastAnchorMissingRunIsRejected() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        assertThrows(ActionValidationException.class, () -> guard.assertForecastRunIsFormal("run_none"));
    }

    @Test
    void forecastAnchorOnSandboxRunIsRejectedNotAccepted() {
        // 反向侧红线：动作提案只能锚定 FORMAL，锚定 SANDBOX 演练产物必须拒，非静默放行
        stubRunFound("SANDBOX");
        assertThrows(ActionValidationException.class, () -> guard.assertForecastRunIsFormal("run_sbx"));
    }

    @Test
    void forecastAnchorOnFormalRunAccepted() {
        stubRunFound("FORMAL");
        assertDoesNotThrow(() -> guard.assertForecastRunIsFormal("run_fml"));
    }
}
