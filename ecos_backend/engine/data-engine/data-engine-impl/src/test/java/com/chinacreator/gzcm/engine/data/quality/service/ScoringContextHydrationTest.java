package com.chinacreator.gzcm.engine.data.quality.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.quality.DqAlertService;
import com.chinacreator.gzcm.engine.data.quality.DqScoreService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqScheduleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * F02-09 DQ 评分上下文真实化验收（W61）：
 * 单条 {@code dq_rule_check} 行（mock JdbcTemplate 返回）→
 * {@link DqScheduleServiceImpl#buildContext(DqRuleVO)} 生成的 ctx 各字段
 * {@code connectionConfig / sampleFailures / lastCheck / totalRows / failedRows}
 * 必须来自那一行（非空 Map / 数值回填），不得再是"空转"假象。
 *
 * <p>验收点（D.9 系列，设计文档 F02-09）：</p>
 * <ol>
 *   <li>lastCheck 非空且包含 totalRows=200 / failedRows=5 / passRate=0.975；</li>
 *   <li>ctx.totalRows / ctx.failedRows 直接来自 check 行数值；</li>
 *   <li>connectionConfig 保留 scope 上下文并标注 lastCheckId（= id）；</li>
 *   <li>样本失败 JSON 反序列化到 sampleFailures。</li>
 * </ol>
 *
 * <p>实现依据：本用例驱动 main 代码 {@code buildContext → hydrateFromLastCheck}
 * （新增方法），F02-09 补齐后_CTX 空白假值/0L 组合不再出现。</p>
 *
 * @author ECOS Data Engine Team / F02-09
 */
@DisplayName("F02-09 DQ 评分上下文真实化：buildContext 从 dq_rule_check 行取值")
class ScoringContextHydrationTest {

    @Test
    @DisplayName("单条 dq_rule_check 行 → ctx.lastCheck/totalRows/failedRows/sampleFailures/connectionConfig 全部回填")
    void buildContext_hydratesFromLastCheckRow() {
        DqScheduleMapper scheduleMapper = mock(DqScheduleMapper.class);
        DqRuleMapper ruleMapper = mock(DqRuleMapper.class);
        DqScoreService scoreService = mock(DqScoreService.class);
        DqSecurityService security = mock(DqSecurityService.class);
        DqAlertService alertService = mock(DqAlertService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> jprov = mock(ObjectProvider.class);
        when(jprov.getIfAvailable()).thenReturn(jdbc);

        DqScheduleServiceImpl svc = new DqScheduleServiceImpl(
                scheduleMapper, ruleMapper, scoreService, security, alertService, jprov);

        // 预置 1 条 dq_rule_check 行 — 模拟真实回填源
        Map<String, Object> checkRow = new LinkedHashMap<>();
        checkRow.put("id", "chk-uuid-001");
        checkRow.put("ruleId", "rule-001");
        checkRow.put("scheduleId", "sched-001");
        checkRow.put("triggerType", "SCHEDULE");
        checkRow.put("executedAt", Timestamp.valueOf(LocalDateTime.now().minusMinutes(3)));
        checkRow.put("passed", true);
        checkRow.put("totalRows", 200L);
        checkRow.put("failedRows", 5L);
        checkRow.put("passRate", 0.975D);
        checkRow.put("sampleSize", 50);
        // sample_failures 是 JSONB 文本（PG 侧 ::text 后 Java 收到 String）
        checkRow.put("sampleFailuresJson",
                "{\"col1\":[{\"row\":3,\"expected\":\"not-null\",\"got\":null}]}");
        checkRow.put("errorMessage", null);

        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(checkRow));
        // DqSecurityService.maskParameters 返回入参 map 的一个副拷贝（保参不变）
        when(security.maskParameters(any())).thenAnswer(inv -> inv.getArgument(0));

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-001");
        rule.setRuleName("not-null 唯一性");
        rule.setRuleType("NOT_NULL");
        rule.setSeverity("MEDIUM");
        rule.setTargetKind("TABLE");
        rule.setTargetTable("sysman.customer");
        rule.setTargetField("email");
        rule.setParametersJson("{\"min_length\":5}");

        ScoringContext ctx = svc.buildContext(rule);

        // ── F02-09 五项字段的非空/来源断言 ─────────────────
        assertNotNull(ctx.getLastCheck(), "ctx.lastCheck 必须非 null（来自最近 check 行）");
        assertFalse(ctx.getLastCheck().isEmpty(),
                "ctx.lastCheck 不得为空 Map（F02-09 反‘空转’假象）: " + ctx.getLastCheck());
        assertEquals("chk-uuid-001", ctx.getLastCheck().get("id"), "lastCheck.id = check 行 id");
        assertEquals(0.975D, (Double) ctx.getLastCheck().get("passRate"),
                "lastCheck.passRate = check 行 pass_rate 字段原值");

        // totalRows / failedRows 由 hydrateFromLastCheck 直接回填
        assertEquals(200L, ctx.getTotalRows(), "ctx.totalRows 必须 = check 行 total_rows=200");
        assertEquals(5L, ctx.getFailedRows(), "ctx.failedRows 必须 = check 行 failed_rows=5");

        // sampleFailures 从 sample_failures JSONB 文本解析回填
        assertNotNull(ctx.getSampleFailures(), "ctx.sampleFailures 非 null");
        assertTrue(ctx.getSampleFailures().containsKey("col1"),
                "sampleFailures 应包含 JSONB 解析后的 col1 key");
        // 与 lastCheck 联动的 marker（表示来自 check 行而非新样本拉取）
        assertFalse((Boolean) ctx.getSampleFailures().getOrDefault("contextIncomplete", Boolean.TRUE)
                        == null, "contextIncomplete marker 应明确");
        // F02-09 规格：目标表可统计后 connectionConfig 应转 incomplete=false 并附 lastCheckId
        assertNotNull(ctx.getConnectionConfig(), "ctx.connectionConfig 非 null");
        assertEquals(Boolean.FALSE, ctx.getConnectionConfig().get("contextIncomplete"),
                "有 check 行时 contextIncomplete 应翻 false（真实已有统计）");
        assertEquals("chk-uuid-001", ctx.getConnectionConfig().get("lastCheckId"),
                "connectionConfig.lastCheckId 应引用 check 行 id 便于回溯");
    }

    @Test
    @DisplayName("F02-09 二义：最近 1h 内无 check 行（DB 空/Critical schema 缺）→ contextIncomplete=true，评分需按 UNKNOWN 降级")
    void buildContext_noRecentCheckRow_marksContextIncomplete() {
        DqScheduleMapper scheduleMapper = mock(DqScheduleMapper.class);
        DqRuleMapper ruleMapper = mock(DqRuleMapper.class);
        DqScoreService scoreService = mock(DqScoreService.class);
        DqSecurityService security = mock(DqSecurityService.class);
        DqAlertService alertService = mock(DqAlertService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> jprov = mock(ObjectProvider.class);
        when(jprov.getIfAvailable()).thenReturn(jdbc);

        DqScheduleServiceImpl svc = new DqScheduleServiceImpl(
                scheduleMapper, ruleMapper, scoreService, security, alertService, jprov);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(), new java.util.ArrayList<>());
        when(security.maskParameters(any())).thenAnswer(inv -> inv.getArgument(0));

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-002");
        rule.setRuleType("NOT_NULL");
        rule.setTargetKind("TABLE");
        rule.setTargetTable("x");

        ScoringContext ctx = svc.buildContext(rule);

        // 无 1h 内 check 行 → contextIncomplete 三者均标 true
        assertEquals(Boolean.TRUE, ctx.getConnectionConfig().get("contextIncomplete"),
                "无 check 行 → connectionConfig.contextIncomplete=true（未知统计源，评估器需降级 UNKNOWN）");
        assertEquals(Boolean.TRUE, ctx.getSampleFailures().get("contextIncomplete"),
                "sampleFailures 也要标 contextIncomplete=true");
        assertEquals(Boolean.TRUE, ctx.getLastCheck().get("contextIncomplete"),
                "lastCheck 要标 contextIncomplete=true，评估器据此降级为 UNKNOWN 而非高分通过"
        );
        // 数值仍保持 0 起手（不应污染"空表视完整"的默认 1.0 判定 → 那是评估器层面处理）
        assertEquals(0L, ctx.getTotalRows());
        assertEquals(0L, ctx.getFailedRows());
    }
}
