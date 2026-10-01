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
import static org.mockito.ArgumentMatchers.anyString;

/**
 * F03-03 V3 依赖不可用（resolvable-empty 路径）—— W75/M0.
 *
 * <p>与 {@link PublishGateV3Test} 的 404/5xx 路径互补：本测试锁定
 * <b>"resolver 成功寻址 + HTTP 200 但响应不可解析"</b>（fetcher 返回 null）语义——
 * 仍产出 {@code dependencyErrors += ECOS-ONTO-050}（503 fail-closed），绝不默认通过。
 * 这是设计 §F03-03 "三态语义"的<b>不可用</b>边界的独立断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class PublishGateDependencyDownTest {

    @Mock
    private CaliberService caliberService;

    @Test
    void resolvableEmptyFetcherMarksDependencyUnavailable() {
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(anyString());

        // fetcher 返回 null = 元数据解析不可用（resolver 成功但 columns 集为空 / 非法）
        DatNetLineageGuard.LineageColumnFetcher unavailable = tableRef -> null;

        GateResult r = new PublishGateService(caliberService, unavailable)
                .validate(proposalRow("ecos_biz_stage_fact", "amount"));

        assertTrue(r.hasDependencyErrors(),
                "resolvable-empty（fetcher 返回 null）应产生 dependencyErrors： " + r.getDependencyErrors());
        assertTrue(r.getDependencyErrors().stream()
                        .anyMatch(d -> "ECOS-ONTO-050".equals(d.getCode())
                                && "datanet".equals(d.getDependency())),
                "dependencyErrors 应含 datanet/ECOS-ONTO-050，实际 " + r.getDependencyErrors());
        assertFalse(r.isPassed(), "依赖不可用 → 门禁必须 fail-closed（不执行）");
    }

    @Test
    void lineageColumnMissingProducesV3Violation() {
        // 正反向判定的<b>合格</b>路径：HTTP 200 返回列集 → 缺声明列 → V3_LINEAGE / ECOS-ONTO-030
        doNothing().when(caliberService).assertMetricCaliberApprovedForPublish(anyString());

        DatNetLineageGuard.LineageColumnFetcher colsOk = tableRef -> List.of("amount", "stage_code");

        GateResult r = new PublishGateService(caliberService, colsOk)
                .validate(proposalRow("ecos_biz_stage_fact", "ghost_col"));

        assertFalse(r.isPassed(), "列缺失应使门禁不通过");
        assertTrue(r.getViolations().stream()
                        .anyMatch(v -> "V3_LINEAGE".equals(v.getGate())
                                && "ECOS-ONTO-030".equals(v.getCode())),
                "应产出 V3_LINEAGE/ECOS-ONTO-030，实际 " + r.getViolations());
        assertTrue(r.getViolations().stream().anyMatch(v -> v.getCode().equals("ECOS-ONTO-030"))
                && r.getViolations().get(0).getMessage().contains("missing on table"),
                "违反项消息应含 'missing on table' 前缀");
    }

    private static Map<String, Object> proposalRow(String tableRef, String declaredColumn) {
        Map<String, Object> metric = new java.util.HashMap<>();
        metric.put("code", "M_FC_PROFIT");
        metric.put("caliber_id", "cb-1");
        metric.put("column", declaredColumn);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("metrics", List.of(metric));
        payload.put("factTable", tableRef);
        payload.put("changeType", "CREATE_METRIC");
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", "proposal-depdown");
        row.put("payload", payload);
        return row;
    }
}
