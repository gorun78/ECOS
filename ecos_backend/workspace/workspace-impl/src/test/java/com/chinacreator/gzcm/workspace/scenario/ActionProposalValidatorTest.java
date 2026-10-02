package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F07-10-2 写侧校验骨架单测：五必填服务端强校验 + forecastRunId FORMAL 判定。
 * 对应验收 {@code #eachOfFiveMandatoryFieldsMissingYieldsHttp400}（参数化 5 用例）与
 * {@code #forecastRunIdMustExistAndBeFormal} 的服务层可测部分（端点/持久化面归切断线 F07-11/19）。
 */
class ActionProposalValidatorTest {

    private final ActionProposalValidator validator = new ActionProposalValidator();

    private static ActionProposalDTO full() {
        ActionProposalDTO d = new ActionProposalDTO();
        d.setAssignee("ops-lead");
        d.setDeadline("2026-12-31");
        d.setApprover("sec-officer");
        d.setExpectedImpact("revenue +2%");
        d.setControlMetric("on-time-delivery");
        d.setForecastRunId("run-formal-1");
        return d;
    }

    // ═══════════════ 五必填：缺任一 → 400 ═══════════════

    @Test
    void allFiveMandatoryPresentPasses() {
        assertDoesNotThrow(() -> validator.validate(full()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignee", "deadline", "approver", "expectedImpact", "controlMetric"})
    void eachMandatoryFieldMissingYields400(String field) {
        ActionProposalDTO d = full();
        clear(field, d);
        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> validator.validate(d));
        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains(field), "应报出缺失字段 " + field);
    }

    @Test
    void multipleMissingFieldsReportedTogether() {
        ActionProposalDTO d = full();
        d.setAssignee(null);
        d.setApprover("  ");
        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> validator.validate(d));
        assertTrue(ex.getMessage().contains("assignee") && ex.getMessage().contains("approver"),
                "应一次性报出全部缺失字段（assignee + approver）");
    }

    @Test
    void nullDtoReportsAllFiveMandatory() {
        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> validator.validate(null));
        for (String f : ActionProposalValidator.MANDATORY_FIELDS) {
            assertTrue(ex.getMessage().contains(f), "null DTO 应报全五必填，覆盖 " + f);
        }
    }

    // ═══════════════ forecastRunId 锚点：存在且 FORMAL ═══════════════

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BaselineReferenceGuard guard = new BaselineReferenceGuard(jdbc);

    @SuppressWarnings("unchecked")
    private void stubRun(int count, String runMode) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(count);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), any())).thenReturn(runMode);
    }

    @Test
    void forecastRunFormalAccepted() {
        stubRun(1, "FORMAL");
        assertDoesNotThrow(() -> guard.assertForecastRunIsFormal("run-formal-1"));
    }

    @Test
    void forecastRunBlankYields400WithoutDbAccess() {
        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> guard.assertForecastRunIsFormal("  "));
        assertEquals(400, ex.getHttpStatus());
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any());
    }

    @Test
    void forecastRunNotExistYields400() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> guard.assertForecastRunIsFormal("ghost-run"));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void forecastRunSandboxYields400() {
        stubRun(1, "SANDBOX");
        assertThrows(ActionValidationException.class,
                () -> guard.assertForecastRunIsFormal("run-sandbox-1"));
    }

    private static void clear(String field, ActionProposalDTO d) {
        switch (field) {
            case "assignee" -> { d.setAssignee(null); break; }
            case "deadline" -> { d.setDeadline(""); break; }
            case "approver" -> { d.setApprover(" "); break; }
            case "expectedImpact" -> { d.setExpectedImpact(null); break; }
            case "controlMetric" -> { d.setControlMetric(""); break; }
            default -> throw new IllegalArgumentException(field);
        }
    }
}
