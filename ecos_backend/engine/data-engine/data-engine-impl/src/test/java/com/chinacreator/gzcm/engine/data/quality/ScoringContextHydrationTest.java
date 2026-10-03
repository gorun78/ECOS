package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqAccuracyEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqCompletenessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqConsistencyEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqFreshnessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqUniquenessEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.impl.DqValidityEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.service.DqScoreEngine;
import com.chinacreator.gzcm.engine.data.quality.service.DqScheduleServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;

/**
 * F02-09（详细设计-02，D-20/W61）DQ 评分上下文真实化 验收测试。
 *
 * <p>覆盖设计验收用例：</p>
 * <ul>
 *   <li>{@code ScoringContextHydrationTest} — 预置 1 条 check 行 → 断言 ctx 各字段来自该行，非空转</li>
 *   <li>{@code IncompleteContextUnknownScoreTest} — 无 check 历史（目标表不可达）→ 评分降级 UNKNOWN，非高分通过</li>
 * </ul>
 */
class ScoringContextHydrationTest {

    // ==================== IncompleteContextUnknownScoreTest ====================

    private final DqScoreEngine engine = new DqScoreEngine(Arrays.asList(
            new DqCompletenessEvaluator(), new DqAccuracyEvaluator(), new DqConsistencyEvaluator(),
            new DqUniquenessEvaluator(), new DqValidityEvaluator(), new DqFreshnessEvaluator()));

    @Test
    @DisplayName("IncompleteContextUnknownScoreTest — contextIncomplete=true → 6 维全部 UNKNOWN 哨兵（非 1.0 高分，非 0.0 未通过）")
    void incompleteContext_degradesToUnknown() {
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId("r-incomplete");
        ctx.setScopeType("TABLE");
        ctx.setScopeId("sysman.customer");
        ctx.setTotalRows(0L);
        ctx.setFailedRows(0L);
        ctx.setContextIncomplete(true);

        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctx);
        assertEquals(DqDimension.all().size(), scores.size(), "仍应输出全部维度");
        for (DqDimension d : DqDimension.all()) {
            DimensionScore s = scores.get(d);
            assertNotNull(s, "维度 " + d + " 不应缺失");
            assertEquals(DimensionScore.UNKNOWN_SENTINEL, s.getScoreValue(), 1e-9,
                    "维度 " + d + " 应降级为 UNKNOWN 哨兵，而非 0.0/1.0");
            assertEquals("UNKNOWN", s.getDetails().get("status"),
                    "维度 " + d + " detail.status 应为 UNKNOWN");
        }
    }

    @Test
    @DisplayName("IncompleteContextUnknownScoreTest（对照）— contextIncomplete=false 且 totalRows=0 → 仍按既有 1.0 兜底（不回归）")
    void completeContextEmptySample_keepsLegacyFallback() {
        ScoringContext ctx = new ScoringContext();
        ctx.setRuleId("r-empty");
        ctx.setScopeType("TABLE");
        ctx.setScopeId("sysman.customer");
        ctx.setTotalRows(0L);
        ctx.setFailedRows(0L);
        ctx.setContextIncomplete(false);

        Map<DqDimension, DimensionScore> scores = engine.evaluateAll(ctx);
        DimensionScore c = scores.get(DqDimension.COMPLETENESS);
        assertEquals(1.0D, c.getScoreValue(), 1e-9, "空样本 + 上下文完整应保持既有 1.0 兜底");
    }

    // ==================== ScoringContextHydrationTest ====================

    /** 反射调用私有 buildContext（保持生产代码私有，不改可见性）。 */
    private ScoringContext invokeBuildContext(DqScheduleServiceImpl svc, DqRuleVO rule) throws Exception {
        java.lang.reflect.Method m = DqScheduleServiceImpl.class
                .getDeclaredMethod("buildContext", DqRuleVO.class);
        m.setAccessible(true);
        return (ScoringContext) m.invoke(svc, rule);
    }

    private DqRuleVO rule() {
        DqRuleVO r = new DqRuleVO();
        r.setId("r-hydrate");
        r.setTargetKind("FIELD");
        r.setTargetTable("sysman.customer");
        r.setTargetField("phone");
        r.setParametersJson("{\"rule_type\":\"NOT_NULL\"}");
        return r;
    }

    private DqScheduleServiceImpl serviceWith(ObjectProvider<JdbcTemplate> provider) {
        return new DqScheduleServiceImpl(
                mock(DqScheduleMapper.class), mock(DqRuleMapper.class),
                mock(DqScoreService.class), mock(DqSecurityService.class),
                mock(com.chinacreator.gzcm.engine.data.quality.DqAlertService.class),
                provider);
    }

    @Test
    @DisplayName("ScoringContextHydrationTest — 预置 1 条 check 行 → ctx.totalRows/failedRows/lastCheck 来自该行（非空 Map 0 转）")
    void hydration_fromLastCheckRow() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDateTime ts = LocalDateTime.of(2026, 10, 3, 12, 0, 0);
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("total_rows", 1234);
        row.put("failed_rows", 7);
        row.put("pass_rate", 0.9943);
        row.put("sample_size", 100);
        row.put("sample_failures", "{\"phone\":[\"13800000000\"]}");
        row.put("executed_at", ts);
        row.put("error_message", null);
        when(jdbc.queryForList(Mockito.anyString(), (Object) Mockito.any()))
                .thenReturn(java.util.List.of(row));

        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        Mockito.doReturn(jdbc).when(provider).getIfAvailable();
        DqScheduleServiceImpl svc = serviceWith(provider);

        ScoringContext ctx = invokeBuildContext(svc, rule());
        assertFalse(ctx.isContextIncomplete(), "有 check 历史 → 上下文完整（非不完整）");
        assertEquals(1234L, ctx.getTotalRows(), "totalRows 应来自 check 行，而非默认 0");
        assertEquals(7L, ctx.getFailedRows(), "failedRows 应来自 check 行");
        assertEquals("sysman.customer:phone", ctx.getScopeId(), "FIELD scopeId = table:field");
        assertEquals(ts, ctx.getExecutedAt(), "executedAt 应来自 check 行");
        assertNotNull(ctx.getLastCheck());
        assertFalse(ctx.getLastCheck().isEmpty(), "lastCheck 应回填 check 行，非空 Map");
        assertNotNull(ctx.getConnectionConfig());
        assertEquals("FIELD", ctx.getConnectionConfig().get("scopeType"),
                "connectionConfig 应含非敏感 scope 定位（target_kind），非空 Map");
    }

    @Test
    @DisplayName("ScoringContextHydrationTest（无历史）— 无任何 check 行 → contextIncomplete=true（供引擎降级 UNKNOWN）")
    void hydration_noHistory_marksIncomplete() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(Mockito.anyString(), (Object) Mockito.any()))
                .thenReturn(java.util.List.of());

        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        Mockito.doReturn(jdbc).when(provider).getIfAvailable();
        DqScheduleServiceImpl svc = serviceWith(provider);

        ScoringContext ctx = invokeBuildContext(svc, rule());
        assertTrue(ctx.isContextIncomplete(), "无 check 历史（目标表不可达）→ contextIncomplete=true");
        assertTrue(ctx.getLastCheck().isEmpty(), "lastCheck 保持空 Map（无历史可填）");
    }
}
