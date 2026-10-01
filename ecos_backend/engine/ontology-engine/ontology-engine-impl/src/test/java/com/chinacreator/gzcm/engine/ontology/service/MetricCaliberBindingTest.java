package com.chinacreator.gzcm.engine.ontology.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W76/M0 · rule 4 + rule 3 — Metric 口径绑定门禁（纯 Mockito 无 Spring 上下文）。
 *
 * <p>断言：
 * <ol>
 *   <li>rule 4（正）：PUBLISHED 指标 caliber_id=NULL → {@link CaliberService.CaliberMissingException};</li>
 *   <li>rule 4（正）：口径已绑定但版本 status=DRAFT（非 APPROVED） → {@link CaliberService.CaliberNotApprovedException};</li>
 *   <li>rule 3：caliber_snapshot 已非空再次 bindMetricCaliber → {@link CaliberService.SnapshotFrozenException};</li>
 *   <li>rule 3（正）：首次绑定（快照为空）成功写入（1 行受影响）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricCaliberBindingTest {

    private static final String SCHEMA = "ecos_ontology";
    private static final String M = "M_SALES_GMV";
    private static final String CID = "cal-uuid-1";
    private static final String V = "2";

    @Mock
    private JdbcTemplate jdbc;

    private CaliberService service;

    @BeforeEach
    void setUp() {
        service = new CaliberService(jdbc, SCHEMA);
    }

    @Test
    @DisplayName("rule 4: PUBLISHED 指标无 caliber_id → CaliberMissingException")
    void publishedMetricWithoutCaliberId_throwsMissing() {
        Map<String, Object> metric = new HashMap<>();
        metric.put("code", M);
        metric.put("caliber_id", null);
        metric.put("formula_version", null);
        metric.put("caliber_snapshot", null);
        metric.put("status", "PUBLISHED");
        when(jdbc.queryForMap(anyString(), eq(M))).thenReturn(metric);

        assertThrows(CaliberService.CaliberMissingException.class,
                () -> service.assertMetricCaliberApprovedForPublish(M));
    }

    @Test
    @DisplayName("rule 4: 口径版本 status=DRAFT（非 APPROVED）→ CaliberNotApprovedException")
    void publishedMetricWithDraftVersion_throwsNotApproved() {
        Map<String, Object> metric = new HashMap<>();
        metric.put("code", M);
        metric.put("caliber_id", CID);
        metric.put("formula_version", V);
        metric.put("caliber_snapshot", null);
        metric.put("status", "PUBLISHED");
        when(jdbc.queryForMap(anyString(), eq(M))).thenReturn(metric);

        // lookupVersionStatus: SELECT status FROM ... AND version_no=? → "DRAFT"（非 APPROVED）
        when(jdbc.queryForObject(
                argThat(s -> s != null && s.contains("SELECT status FROM")),
                eq(String.class), eq(CID), eq(V)))
                .thenReturn("DRAFT");

        assertThrows(CaliberService.CaliberNotApprovedException.class,
                () -> service.assertMetricCaliberApprovedForPublish(M));
    }

    @Test
    @DisplayName("rule 3: 快照已非空 → bindMetricCaliber 抛 SnapshotFrozenException")
    void bindMetricWithExistingSnapshot_throwsFrozen() {
        // throwIfSnapshotFrozen: SELECT caliber_snapshot FROM ecos_metric_definition WHERE code=?
        when(jdbc.queryForObject(
                argThat(s -> s != null && s.contains("caliber_snapshot") && s.contains("ecos_metric_definition")),
                eq(String.class), eq(M)))
                .thenReturn("{\"caliber_id\":\"cal-uuid-1\"}");

        assertThrows(CaliberService.SnapshotFrozenException.class,
                () -> service.bindMetricCaliber(M, CID, V));

        // 冻结后不得产生任何 UPDATE
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("rule 3（正）: 首次绑定（快照为空）→ 成功写入冻结快照")
    void firstTimeBinding_succeedsAndFreezes() {
        // snapshot 查询 → 空（允许）
        when(jdbc.queryForObject(
                argThat(s -> s != null && s.contains("caliber_snapshot") && s.contains("ecos_metric_definition")),
                eq(String.class), eq(M)))
                .thenReturn(null);

        // formula 查询 → 明文
        when(jdbc.queryForObject(
                argThat(s -> s != null && s.contains("SELECT formula FROM")),
                eq(String.class), eq(CID), eq(V)))
                .thenReturn("SUM(amount)");

        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = assertDoesNotThrow(
                () -> service.bindMetricCaliber(M, CID, V));

        assertNotNull(result.get("caliber_snapshot"));
        String snapshot = String.valueOf(result.get("caliber_snapshot"));
        assertTrue(snapshot.contains("\"caliber_id\":\"" + CID + "\""),
                "snapshot should embed caliber_id: " + snapshot);
        assertTrue(snapshot.contains("\"version_no\":\"2\""),
                "snapshot should embed version_no: " + snapshot);
        assertEquals(CID, result.get("caliber_id"));
        assertEquals(V, result.get("formula_version"));

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("rule 4: 指标行不存在（Draft 路径）→ 静默返回，不抛异常")
    void missingMetricRow_isSilentDraftPath() {
        when(jdbc.queryForMap(anyString(), eq(M)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertDoesNotThrow(() -> service.assertMetricCaliberApprovedForPublish(M));
    }
}
