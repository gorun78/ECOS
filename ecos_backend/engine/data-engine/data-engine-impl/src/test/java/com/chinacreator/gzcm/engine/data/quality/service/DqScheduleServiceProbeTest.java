package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.data.quality.DqAlertService;
import com.chinacreator.gzcm.engine.data.quality.DqScoreService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.dao.DqScheduleDao;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.probe.DqRuleProbeExecutor;
import com.chinacreator.gzcm.engine.data.quality.probe.RuleProbeResult;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * F02-09 DQ 评分上下文真实化 - probe-first 分支验收：
 * {@code DqRuleProbeExecutor} 是真值来源时，{@code buildContext} 必须直接吃
 * {@code probe.totalRows / probe.failedRows}（而不是回退 dq_rule_check 行）。
 *
 * <p>分支覆盖：</p>
 * <ol>
 *   <li>probe.executed=true 且 totalRows&gt;0 → ctx 直填真值 + contextIncomplete=false + lastCheck.source=probe；</li>
 *   <li>probe.executed=true 且 totalRows=0（空表）→ contextIncomplete 保持 true（防"空表视完整"假 1.0）；</li>
 *   <li>probe.executed=false（表不可达 / 类型不支持 / SQL 异常）→ 走 F02-09 回退。</li>
 * </ol>
 *
 * <p>本用例与 {@link ScoringContextHydrationTest}（回退路径）互补，共同覆盖
 * {@code DqScheduleServiceImpl#buildContext} 的三级上下文拼装分支。</p>
 *
 * @author ECOS Data Engine Team / F02-09
 */
@DisplayName("F02-09 DQ probe-first 上下文：buildContext 消费 RuleProbeResult 真值")
class DqScheduleServiceProbeTest {

    // ───────────────────── 共用装配 ─────────────────────

    private static DqScheduleServiceImpl svcWith(DqRuleProbeExecutor probe) {
        DqScheduleMapper scheduleMapper = mock(DqScheduleMapper.class);
        DqRuleMapper ruleMapper = mock(DqRuleMapper.class);
        DqScoreService scoreService = mock(DqScoreService.class);
        DqSecurityService security = mock(DqSecurityService.class);
        DqAlertService alertService = mock(DqAlertService.class);
        DqScheduleDao scheduleDao = mock(DqScheduleDao.class);
        when(security.maskParameters(any())).thenAnswer(inv -> inv.getArgument(0));
        // 回退路径不查 DB（此用例只关心 probe 值）；显式返回空避免 unexpected stub
        when(scheduleDao.findLastCheck(any())).thenReturn(List.of());
        return new DqScheduleServiceImpl(scheduleMapper, ruleMapper, scoreService,
                security, alertService, scheduleDao, probe);
    }

    private static DqRuleVO rule(String id, String ruleType, String targetTable,
                                 String targetField, String paramsJson) {
        DqRuleVO r = new DqRuleVO();
        r.setId(id);
        r.setRuleType(ruleType);
        r.setTargetKind("TABLE");
        r.setTargetTable(targetTable);
        r.setTargetField(targetField);
        r.setParametersJson(paramsJson);
        return r;
    }

    // ───────────────────── 用例 ─────────────────────

    @Test
    @DisplayName("probe 执行成功且总行数 > 0 → ctx 直填真值 + lastCheck.source=probe + contextIncomplete=false")
    void probe_executed_withFails_ctxHydratesFromProbe() {
        DqRuleProbeExecutor probe = rule -> RuleProbeResult.executed(
                1234L, 12L, "COUNT/SUM NOT_NULL ecos_dw.ecos_biz_cost_fact.evidence_ref");
        DqScheduleServiceImpl svc = svcWith(probe);

        DqRuleVO r = rule("rule-001", "NOT_NULL", "ecos_dw.ecos_biz_cost_fact",
                "evidence_ref", "{}");
        ScoringContext ctx = svc.buildContext(r);

        assertEquals(1234L, ctx.getTotalRows(), "ctx.totalRows 必须 = probe.totalRows=1234");
        assertEquals(12L, ctx.getFailedRows(), "ctx.failedRows 必须 = probe.failedRows=12");

        assertNotNull(ctx.getConnectionConfig());
        assertEquals(Boolean.FALSE, ctx.getConnectionConfig().get("contextIncomplete"),
                "probe 有真值 → contextIncomplete=false");
        assertNotNull(ctx.getConnectionConfig().get("probeSqlDigest"),
                "connectionConfig 必须凿到 probeSqlDigest 便于回溯");

        assertNotNull(ctx.getSampleFailures());
        assertEquals(Boolean.TRUE, ctx.getSampleFailures().get("probe"),
                "sampleFailures 应标 probe=true 表示来源为真实探测");

        assertNotNull(ctx.getLastCheck());
        assertEquals("probe", ctx.getLastCheck().get("source"),
                "lastCheck.source 必须 = \"probe\"（区别于 legacy check 行）");
        assertEquals(1234L, ((Number) ctx.getLastCheck().get("totalRows")).longValue());
        assertEquals(12L, ((Number) ctx.getLastCheck().get("failedRows")).longValue());
        // passRate 应 = 1 - 12/1234
        double pr = ((Number) ctx.getLastCheck().get("passRate")).doubleValue();
        assertTrue(Math.abs(pr - (1.0D - 12.0D / 1234.0D)) < 1e-9,
                "lastCheck.passRate 应 = 1 - failed/total = 1 - 12/1234");
    }

    @Test
    @DisplayName("probe 执行成功但总行数=0（空表）→ contextIncomplete 保持 true 防空表假 1.0")
    void probe_executed_emptyTable_kincompleteKeptTrue() {
        DqRuleProbeExecutor probe = rule -> RuleProbeResult.executed(
                0L, 0L, "COUNT/SUM NOT_NULL ecos_dw.empty_table.x");
        DqScheduleServiceImpl svc = svcWith(probe);

        DqRuleVO r = rule("rule-002", "NOT_NULL", "ecos_dw.empty_table", "x", "{}");
        ScoringContext ctx = svc.buildContext(r);

        assertEquals(0L, ctx.getTotalRows(), "ctx.totalRows 保持 0");
        assertEquals(Boolean.TRUE, ctx.getConnectionConfig().get("contextIncomplete"),
                "空表 probe → contextIncomplete 必须保持 true（评估器据此降级 UNKNOWN/EMPTY_SAMPLE，"
                        + "而非伪装 1.0 高分通过）");
        assertEquals(Boolean.TRUE, ctx.getSampleFailures().get("contextIncomplete"),
                "sampleFailures.contextIncomplete=true");
        assertEquals(Boolean.TRUE, ctx.getLastCheck().get("contextIncomplete"),
                "lastCheck.contextIncomplete=true");
        assertEquals("probe-executed-empty-table", ctx.getLastCheck().get("reason"),
                "reason 应标记 \"probe-executed-empty-table\"（区别于 legacy probe 未执行的 reasoning）");
    }

    @Test
    @DisplayName("probe 未执行（表不可达 / ruleType 不支持 / SQL 异常）→ contextIncomplete=true 且保留 probeReason")
    void probe_notExecuted_keepsContextIncompleteAndReason() {
        DqRuleProbeExecutor probe = rule -> RuleProbeResult.notExecuted(
                "UNIQUE sql failed: BadDataSource");
        DqScheduleServiceImpl svc = svcWith(probe);

        DqRuleVO r = rule("rule-003", "UNIQUE", "ecos_dw.some_table", "id", "{}");
        ScoringContext ctx = svc.buildContext(r);

        assertEquals(Boolean.TRUE, ctx.getConnectionConfig().get("contextIncomplete"),
                "未执行 probe → contextIncomplete=true（评估器未知统计源，降级 UNKNOWN）");
        assertEquals("UNIQUE sql failed: BadDataSource",
                ctx.getConnectionConfig().get("probeReason"),
                "connectionConfig 应保留 probe Reason 便于运维定位");
        assertEquals("UNIQUE sql failed: BadDataSource",
                ctx.getLastCheck().get("probeReason"),
                "lastCheck 也应保留 probe Reason");
        assertEquals(0L, ctx.getTotalRows(), "数值保持 0 起手，由评估器层面处理");
        assertEquals(0L, ctx.getFailedRows(), "failedRows 保持 0 起手");
    }
}
