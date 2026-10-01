package com.chinacreator.gzcm.engine.ontology.service.PublishGate;

import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.engine.ontology.service.DatNetLineageGuard;
import com.chinacreator.gzcm.engine.ontology.service.PublishGateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

/**
 * F03-03 V1 口径门禁（W75/M0）。
 *
 * <p>Mock {@link CaliberService} 对测试指标抛 {@code CaliberMissingException} →
 * {@code validate(proposal)} 产出 {@code V1_CALIBER / ECOS-ONTO-040}。</p>
 */
@ExtendWith(MockitoExtension.class)
class PublishGateV1Test {

    @Mock
    private CaliberService caliberService;

    private static final String METRIC = "M_FC_PROFIT";

    private PublishGateService service() {
        // 无血缘引用（无 factTable）→ V3 跳过；无 expression/additive → V2 跳过
        return new PublishGateService(caliberService,
                (DatNetLineageGuard.LineageColumnFetcher) tableRef -> List.of());
    }

    @Test
    void caliberMissingProducesV1Violation() {
        doThrow(new CaliberService.CaliberMissingException("no caliber bound"))
                .when(caliberService).assertMetricCaliberApprovedForPublish(METRIC);

        Map<String, Object> metric = Map.of(
                "code", METRIC,
                "caliber_id", "cb-1");

        GateResult r = service().validate(proposalRow(metric));

        assertFalse(r.isPassed(), "口径缺失应使门禁不通过");
        assertTrue(hasViolation(r, "V1_CALIBER", "ECOS-ONTO-040"),
                "应产出 V1_CALIBER/ECOS-ONTO-040，实际 " + r.getViolations());
        assertTrue(r.getViolations().stream()
                        .anyMatch(v -> v.getMessage().contains("caliber")),
                "违反项消息应提及 caliber");
    }

    @Test
    void approvedCaliberPassesV1() {
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(METRIC);
        GateResult r = service().validate(proposalRow(Map.of("code", METRIC, "caliber_id", "cb-1")));
        assertFalse(r.getViolations().stream().anyMatch(v -> "V1_CALIBER".equals(v.getGate())),
                "已审批口径不应产出 V1 违反项");
    }

    private static Map<String, Object> proposalRow(Map<String, Object> metric) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("metrics", List.of(metric));
        payload.put("changeType", "CREATE_METRIC");
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", "proposal-1");
        row.put("payload", payload);
        return row;
    }

    private static boolean hasViolation(GateResult r, String gate, String code) {
        return r.getViolations().stream()
                .anyMatch(v -> gate.equals(v.getGate()) && code.equals(v.getCode()));
    }
}
