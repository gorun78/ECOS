package com.chinacreator.gzcm.engine.ontology.service.PublishGate;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.engine.ontology.service.PublishGateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * F03-03 V3 血缘门禁（W75/M0）—— 经 data-engine 元数据列集校验事实表列存在性。
 *
 * <p>{@code DatNetLineageGuard.LineageColumnFetcher} 返回 <b>null</b> 代表 data-engine
 * 元数据 REST 不可用（resolver 空 / 404 / 5xx / 响应非法，生产装配内部同走该路径）→
 * {@code dependencyErrors} 含 {@code ECOS-ONTO-050}（503 fail-closed，禁默认通过）。
 * 声明列缺失 → {@code V3_LINEAGE / ECOS-ONTO-030}。</p>
 */
@ExtendWith(MockitoExtension.class)
class PublishGateV3Test {

    @Mock
    private CaliberService caliberService;

    @Test
    void upstreamUnresolvableMarksDependencyUnavailable() {
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(anyString());

        // fetcher 返回 null = data-engine 元数据不可达（fail-closed 路径）
        PublishGateService service = new PublishGateService(caliberService, tableRef -> null);

        GateResult r = service.validate(proposalRow("ecos_biz_stage_fact", "amount"));

        assertTrue(r.hasDependencyErrors(), "元数据不可解析应产生 dependencyErrors： " + r.getDependencyErrors());
        assertTrue(r.getDependencyErrors().stream()
                        .anyMatch(d -> "ECOS-ONTO-050".equals(d.getCode())
                                && "datanet".equals(d.getDependency())),
                "dependencyErrors 应含 datanet/ECOS-ONTO-050，实际 " + r.getDependencyErrors());
        assertFalse(r.isPassed(), "依赖不可用时门禁必须不通过（fail-closed）");
    }

    @Test
    void declaredColumnPresentPassesV3() {
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(anyString());

        // 列集可解析且包含声明列 → V3 通过
        PublishGateService service = new PublishGateService(caliberService,
                tableRef -> List.of("amount", "period"));

        GateResult r = service.validate(proposalRow("ecos_biz_stage_fact", "amount"));

        assertFalse(r.hasDependencyErrors(), "元数据可达不应产生 dependencyErrors： " + r.getDependencyErrors());
        assertTrue(r.getViolations().stream()
                        .noneMatch(v -> "V3_LINEAGE".equals(v.getGate())),
                "声明列存在时不应产生 V3 违反项，实际 " + r.getViolations());
    }

    @Test
    void declaredColumnMissingProducesV3Violation() {
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(anyString());

        // 声明列 missing_amount 不在事实表列集 → V3 违反项 ECOS-ONTO-030
        PublishGateService service = new PublishGateService(caliberService,
                tableRef -> List.of("amount", "period"));

        GateResult r = service.validate(proposalRow("ecos_biz_stage_fact", "missing_amount"));

        assertFalse(r.hasDependencyErrors());
        assertTrue(r.getViolations().stream()
                        .anyMatch(v -> "V3_LINEAGE".equals(v.getGate())
                                && "ECOS-ONTO-030".equals(v.getCode())),
                "缺失列应产生 V3_LINEAGE/ECOS-ONTO-030，实际 " + r.getViolations());
    }

    @Test
    void toApiResponseMapsDependencyTo503() {
        GateResult r = new GateResult();
        r.setPassed(false);
        r.getDependencyErrors().add(new GateResult.DependencyError("datanet", "ECOS-ONTO-050"));
        ApiResponse<?> resp = PublishGateService.toApiResponse(r);
        assertNotNull(resp);
        assertTrue(resp.getCode() == 503, "toApiResponse 应映射为 503");
        assertTrue(resp.getMessage().contains("ECOS-ONTO-050"));
    }

    private static Map<String, Object> proposalRow(String tableRef, String declaredColumn) {
        Map<String, Object> metric = new java.util.HashMap<>();
        metric.put("code", "M_FC_PROFIT");
        metric.put("caliber_id", "cb-1");
        metric.put("column", declaredColumn);

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("metrics", List.of(metric));
        payload.put("factTable", tableRef);
        payload.put("changeType", "CREATE_METRIC");
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", "proposal-v3");
        row.put("payload", payload);
        return row;
    }
}
