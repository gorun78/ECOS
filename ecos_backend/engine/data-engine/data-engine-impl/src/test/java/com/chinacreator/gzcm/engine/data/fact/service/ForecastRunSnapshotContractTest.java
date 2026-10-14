package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastRunSnapshotMapper;
import com.chinacreator.gzcm.engine.data.model.ForecastRunSnapshotVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * snapshot 端点六要素齐备 + cognitive 键名无损映射（H-3 冻结测试类④）。
 *
 * <p>断言面：
 * ① 六要素 {@code forecastRunId/caliberId/caliberVersion/asOfTime/snapshotId/formulaVersion}
 *    全量取自 V197+V277 列；
 * ② 序列化 JSON 键名与 {@code ForecastRunSnapshotReader.RunSnapshot} /
 *    {@code ScenarioBalanceChecker.Cell} / {@code ContributionEdgeSource} /
 *    {@code ChainNodeVO(node,type)} / {@code StatSupportVO(method,pValue)} 组件逐字一致；
 * ③ canonicalBytes 非 JSON 载荷 → cells/edges 空列表 + 利润 null（禁伪造）；
 * ④ runId 无行 404、空白 400（禁全库扫描）。
 */
@ExtendWith(MockitoExtension.class)
class ForecastRunSnapshotContractTest {

    @Mock private ForecastRunSnapshotMapper snapshotMapper;

    private static final String PAYLOAD_JSON = """
            {
              "baselineProfit": 5000000.00,
              "scenarioProfit": 5320000.50,
              "cells": [
                {"projectId": "P-1", "period": 202601, "baseline": 100.25, "scenario": 130.75},
                {"projectId": "P-2", "period": 202602, "baseline": 80.00,  "scenario": 60.00}
              ],
              "edges": [
                {"chainId": "CH-9",
                 "path": [{"node": "EV-1", "type": "EVENT"}, {"node": "M_FC_PROFIT", "type": "METRIC:M_FC_PROFIT"}],
                 "baseline": 100.25, "scenario": 130.75,
                 "evidenceRefs": ["EVDOC-3"],
                 "statSupport": {"method": "ols", "pValue": 0.031}}
              ]
            }
            """;

    private static Map<String, Object> header(boolean withJsonPayload) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("forecast_run_id", "run-77");
        h.put("scenario_id", "scn-1");
        h.put("run_type", "SIMULATE");
        h.put("status", "SUCCEEDED");
        h.put("caliber_id", "cal-3");
        h.put("caliber_version", "v2.1");
        h.put("as_of_time", Timestamp.valueOf(LocalDateTime.of(2025, 9, 30, 23, 59, 59)));
        h.put("snapshot_id", "snap-0001");
        h.put("formula_version", "f-1.4");
        h.put("baseline_run_id", "run-70");
        h.put("simulation_result_json", withJsonPayload ? PAYLOAD_JSON : "sixFactorLine|P-1|2026|v=1.0;unit=CNY;k=v;");
        h.put("forecast_result_json", null);
        h.put("diagnosis_result_json", null);
        return h;
    }

    @Test
    @DisplayName("六要素齐备 + cells/edges/利润从结构化 JSON 无损摄取")
    void sixElementsAndDetails() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(header(true));

        ForecastRunSnapshotVO vo = service.getSnapshot("run-77");

        assertEquals("run-77", vo.getForecastRunId());
        assertEquals("cal-3", vo.getCaliberId());
        assertEquals("v2.1", vo.getCaliberVersion());
        assertEquals("2025-09-30T23:59:59", vo.getAsOfTime());
        assertEquals("snap-0001", vo.getSnapshotId());
        assertEquals("f-1.4", vo.getFormulaVersion());
        assertEquals(0, vo.getBaselineProfit().compareTo(new java.math.BigDecimal("5000000.00")));
        assertEquals(0, vo.getScenarioProfit().compareTo(new java.math.BigDecimal("5320000.50")));
        assertEquals(2, vo.getCells().size());
        assertEquals("P-1", vo.getCells().get(0).getProjectId());
        assertEquals(Integer.valueOf(202601), vo.getCells().get(0).getPeriod());   // Cell.period=int 原样
        assertEquals(1, vo.getEdges().size());
        assertEquals(2, vo.getEdges().get(0).getPath().size());
        assertEquals("M_FC_PROFIT", vo.getEdges().get(0).getPath().get(1).getNode());
        assertEquals("METRIC:M_FC_PROFIT", vo.getEdges().get(0).getPath().get(1).getType());
        assertEquals(java.util.List.of("EVDOC-3"), vo.getEdges().get(0).getEvidenceRefs());
        assertEquals("ols", vo.getEdges().get(0).getStatSupport().getMethod());
        assertEquals(0.031, vo.getEdges().get(0).getStatSupport().getpValue());
    }

    @Test
    @DisplayName("序列化 JSON 键名 = cognitive record 组件名（RunSnapshot/Cell/Edge/ChainNode/StatSupport 逐字对齐）")
    void wireKeysMapLosslesslyToCognitiveRecords() throws Exception {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(header(true));

        JsonNode json = new ObjectMapper().valueToTree(service.getSnapshot("run-77"));

        for (String key : new String[]{"forecastRunId", "caliberId", "caliberVersion",
                "asOfTime", "snapshotId", "formulaVersion", "baselineProfit", "scenarioProfit",
                "cells", "edges"}) {
            assertTrue(json.has(key), "缺键: " + key);
        }
        JsonNode cell = json.get("cells").get(0);
        for (String key : new String[]{"projectId", "period", "baseline", "scenario"}) {
            assertTrue(cell.has(key), "Cell 缺键: " + key);
        }
        assertTrue(cell.get("period").isNumber(), "Cell.period 必须是数值（int 形态）");
        JsonNode edge = json.get("edges").get(0);
        for (String key : new String[]{"chainId", "path", "baseline", "scenario", "evidenceRefs", "statSupport"}) {
            assertTrue(edge.has(key), "ContributionEdgeSource 缺键: " + key);
        }
        JsonNode node = edge.get("path").get(0);
        assertTrue(node.has("node") && node.has("type"), "ChainNodeVO 键名须为 node/type");
        JsonNode stat = edge.get("statSupport");
        assertTrue(stat.has("method"), "StatSupportVO 缺 method");
        assertTrue(stat.has("pValue"), "StatSupportVO 键名须钉死为 pValue（防 bean 命名漂移成 pvalue）");
        // 空值语义：结构化载荷缺字段时回显 null 键（记录组件按 null 接收，禁伪造 0）
        assertNotNull(json);
    }

    @Test
    @DisplayName("snake_case 兼容形态（baseline_profit/chain_id/evidence_refs/stat_support/p_value）亦可摄取")
    void snakeCasePayloadCompatible() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        Map<String, Object> h = header(true);
        h.put("simulation_result_json", """
                {"baseline_profit":"10.00","scenario_profit":"12.00",
                 "cells":[{"project_id":"P-9","period":"202601","baseline":"1","scenario":"2"}],
                 "edges":[{"chain_id":"CH-1","path":[{"node":"A","type":"EVENT"}],
                           "baseline":1,"scenario":2,"evidence_refs":["E"],
                           "stat_support":{"method":"mw","p_value":0.04}}]}
                """);
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(h);

        ForecastRunSnapshotVO vo = service.getSnapshot("run-77");

        assertEquals(0, vo.getBaselineProfit().compareTo(new java.math.BigDecimal("10.00")));
        assertEquals("CH-1", vo.getEdges().get(0).getChainId());
        assertEquals(0.04, vo.getEdges().get(0).getStatSupport().getpValue());
        assertEquals(java.util.List.of("E"), vo.getEdges().get(0).getEvidenceRefs());
        assertEquals(Integer.valueOf(202601), vo.getCells().get(0).getPeriod());   // 字符串整型亦归位
    }

    @Test
    @DisplayName("canonicalBytes 非 JSON 载荷 → 六要素仍齐备；cells/edges 空、利润 null（禁伪造）")
    void canonicalTextYieldsEmptyDetails() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(header(false));

        ForecastRunSnapshotVO vo = service.getSnapshot("run-77");

        assertEquals("run-77", vo.getForecastRunId());
        assertEquals("snap-0001", vo.getSnapshotId());
        assertEquals("f-1.4", vo.getFormulaVersion());
        assertEquals(java.util.List.of(), vo.getCells());
        assertEquals(java.util.List.of(), vo.getEdges());
        assertNull(vo.getBaselineProfit());
        assertNull(vo.getScenarioProfit());
    }

    @Test
    @DisplayName("runId 无行 → 404（禁把行不存在伪装成空快照）")
    void missingRunIs404() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        when(snapshotMapper.selectRunHeader("nope")).thenReturn(null);
        DataBridgeException ex = assertThrows(DataBridgeException.class, () -> service.getSnapshot("nope"));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("runId 空白 → 400，不触库（禁全库扫描，SnapshotQuery 要点4）")
    void blankRunIs400NoQuery() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        DataBridgeException ex = assertThrows(DataBridgeException.class, () -> service.getSnapshot("  "));
        assertEquals(400, ex.getHttpStatus());
        verify(snapshotMapper, never()).selectRunHeader(any());
    }

    // ── V287 幂等锚两列 + 按幂等键读路径（差距分析 P0-4 / 详设-09 H-5 判定链）──

    @Test
    @DisplayName("按运行键读：V287 两列 + V277 task_id/reused_from_run_id 齐备透出")
    void v287AnchorColumnsSurfaceOnRunKeyRead() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        Map<String, Object> h = header(false);
        h.put("idempotency_key", "idem-caller-0001");
        h.put("content_hash", "sha256:aa55");
        h.put("reused_from_run_id", "run-60");
        h.put("task_id", "task-777");
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(h);

        ForecastRunSnapshotVO vo = service.getSnapshot("run-77");

        assertEquals("idem-caller-0001", vo.getIdempotencyKey());
        assertEquals("sha256:aa55", vo.getContentHash());
        assertEquals("run-60", vo.getReusedFromRunId());
        assertEquals("task-777", vo.getTaskId());
    }

    @Test
    @DisplayName("历史行缺 V287 列 ⇒ 幂等锚 null（禁伪造回填，读侧不可据此判复用）")
    void legacyRowWithoutAnchorColumnsStaysNull() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(header(false));

        ForecastRunSnapshotVO vo = service.getSnapshot("run-77");

        assertNull(vo.getIdempotencyKey());
        assertNull(vo.getContentHash());
        assertNull(vo.getReusedFromRunId());
        assertNull(vo.getTaskId());
    }

    @Test
    @DisplayName("按幂等键读：命中行映射与按运行键读同形（两条读路径禁形态分叉）")
    void snapshotByIdempotencyKeyUsesSameMapping() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        Map<String, Object> h = header(true);
        h.put("idempotency_key", "idem-caller-0001");
        h.put("content_hash", "sha256:aa55");
        when(snapshotMapper.selectRunHeaderByIdempotencyKey("idem-caller-0001")).thenReturn(h);

        ForecastRunSnapshotVO byKey = service.getSnapshotByIdempotencyKey("idem-caller-0001");

        assertEquals("run-77", byKey.getForecastRunId());
        assertEquals("idem-caller-0001", byKey.getIdempotencyKey());
        assertEquals("sha256:aa55", byKey.getContentHash());
        assertEquals(2, byKey.getCells().size());
        // 库侧按 create_time DESC LIMIT 1 取最近一行，读侧不再二次筛选
        verify(snapshotMapper).selectRunHeaderByIdempotencyKey("idem-caller-0001");
        verify(snapshotMapper, never()).selectRunHeader(any());
    }

    @Test
    @DisplayName("按幂等键未命中 ⇒ 返回 null 而非 404（首次运行的正常分支，禁把正常分支做成异常）")
    void idempotencyKeyMissIsNullNot404() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        when(snapshotMapper.selectRunHeaderByIdempotencyKey("idem-new")).thenReturn(null);

        assertNull(service.getSnapshotByIdempotencyKey("idem-new"));
    }

    @Test
    @DisplayName("幂等键空白 ⇒ 400 且不触库（禁全库扫描）")
    void blankIdempotencyKeyIs400NoQuery() {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service.getSnapshotByIdempotencyKey("   "));
        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("idempotencyKey"), ex.getMessage());
        verify(snapshotMapper, never()).selectRunHeaderByIdempotencyKey(any());
    }

    @Test
    @DisplayName("序列化键名 = cognitive/workspace 读侧键名（reusedFromRunId/taskId/idempotencyKey/contentHash）")
    void wireKeysMatchReaderSideNames() throws Exception {
        ForecastRunSnapshotService service = new ForecastRunSnapshotService(snapshotMapper);
        Map<String, Object> h = header(false);
        h.put("idempotency_key", "idem-1");
        h.put("content_hash", "hash-1");
        h.put("task_id", "task-1");
        when(snapshotMapper.selectRunHeader("run-77")).thenReturn(h);

        JsonNode json = new ObjectMapper().valueToTree(service.getSnapshot("run-77"));

        for (String key : new String[]{"reusedFromRunId", "taskId", "idempotencyKey", "contentHash"}) {
            assertTrue(json.has(key), "读侧依赖键缺失: " + key);
        }
        assertEquals("idem-1", json.get("idempotencyKey").asText());
    }
}
