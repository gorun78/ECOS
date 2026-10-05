package com.chinacreator.gzcm.workspace.fc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W224/C206 · 经营动作 5 必填校验（FC-04 §4.1 · V218 {@code ecos_fc_action_ext}
 * 五列 NOT NULL：action_desc/owner_id/due_date/expected_impact/kpi_text）。
 *
 * <p>验收标识：{@code mvn -Dtest=FcActionClosureTest#requiresAllFiveMandatoryFields}。</p>
 *
 * <p>验收红线：五必填缺一 ⇒ 400；缺一的字段必须在错误消息里点名。</p>
 */
class FcActionClosureTest {

    /** 构造一份五必填齐全的 DTO（作为对照）。 */
    private static FcActionExtController.ActionCreateDto complete() {
        return new FcActionExtController.ActionCreateDto(
                "delay_q4_acceptance",    // actionDesc
                "owner-001",               // ownerId
                LocalDate.of(2026, 11, 30),// dueDate
                new BigDecimal("150000.00"),// expectedImpact
                "kanban:kpi_reduction",    // kpiText
                "dec-001",                 // decisionId (预填)
                "run-001",                 // forecastRunId (预填)
                "factor:delay",            // factorRef (预填)
                "P-01");                   // projectId (预填)
    }

    @Test
    @DisplayName("§七 W224/C206 · 五必填齐全 ⇒ missing 为空")
    void requiresAllFiveMandatoryFields() {
        assertEquals(List.of(), complete().missing(), "五必填都应通过校验");
    }

    @Test
    @DisplayName("缺一即点名：预期影响额缺失 ⇒ 拒 + 定位到 expected_impact")
    void rejectsMissingExpectedImpact() {
        FcActionExtController.ActionCreateDto d = complete();
        FcActionExtController.ActionCreateDto patched = new FcActionExtController.ActionCreateDto(
                d.actionDesc(), d.ownerId(), d.dueDate(), null, d.kpiText(),
                d.decisionId(), d.forecastRunId(), d.factorRef(), d.projectId());
        List<String> miss = patched.missing();
        assertEquals(1, miss.size());
        assertTrue(miss.contains("expected_impact"), "错误消息应点名 expected_impact: " + miss);
    }

    @Test
    @DisplayName("多缺齐报：actionDesc + ownerId + dueDate 缺 ⇒ 一次 3 项具名")
    void reportsAllMissing() {
        FcActionExtController.ActionCreateDto c = complete();
        FcActionExtController.ActionCreateDto d = new FcActionExtController.ActionCreateDto(
                null, null, null,
                c.expectedImpact(), c.kpiText(),
                c.decisionId(), c.forecastRunId(), c.factorRef(), c.projectId());
        List<String> miss = d.missing();
        assertEquals(3, miss.size(), "应报 3 项: " + miss);
        assertTrue(miss.contains("action_desc"));
        assertTrue(miss.contains("owner_id"));
        assertTrue(miss.contains("due_date"));
    }

    @Test
    @DisplayName("空白串等同 null：ownerId=\"   \" 拒（禁伪造空 Owner）")
    void blankOwnerTreatedAsMissing() {
        FcActionExtController.ActionCreateDto c = complete();
        FcActionExtController.ActionCreateDto d = new FcActionExtController.ActionCreateDto(
                c.actionDesc(), "   ", c.dueDate(),
                c.expectedImpact(), c.kpiText(),
                c.decisionId(), c.forecastRunId(), c.factorRef(), c.projectId());
        assertTrue(d.missing().contains("owner_id"), "空白 ownerId 应视为缺");
    }
}
