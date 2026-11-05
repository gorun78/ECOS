package com.chinacreator.gzcm.services.datanet.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.services.datanet.service.DqDashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 看板面 wire 键面保真门（#147CK · DoD DI-2 第十五批）。
 *
 * <p><b>钉的是什么</b>：规则/问题/执行三张表的行键（snake_case，13/15/8 键）、列表容器
 * data/total、看板 6 键、因果偏差（{@code rootCause} 仅在有链时出现）、目标追踪 2 键；
 * 以及「{@code created_at} 文本 vs {@code updated_at} 原始 Timestamp」这条旧实现自带的
 * **不一致取值口径**（本批只改载体、不改语义）。</p>
 *
 * <p><b>变异自证</b>：把 {@code DqRuleRow#ruleType} 的 {@code @JsonProperty("rule_type")}
 * 去掉 ⇒ 行键集红；把 {@code CausalDeviationVO#rootCause} 的 NON_NULL 去掉 ⇒ 空链场景多键红。</p>
 */
class DqDashboardWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    /** 只答本门用到的查询腿（按 SQL 子串路由）；datanet 无 Mockito 依赖，手写假面。 */
    @SuppressWarnings("unchecked")
    private static final class FakeService extends DqDashboardService {
        private final Map<String, List<?>> lists = new LinkedHashMap<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private final List<String> seenSql = new ArrayList<>();
        private final List<RowMapper<?>> seenMappers = new ArrayList<>();

        FakeService() {
            super(null);
        }

        FakeService list(String sqlFragment, List<?> rows) {
            lists.put(sqlFragment, rows);
            return this;
        }

        FakeService count(String sqlFragment, int value) {
            counts.put(sqlFragment, value);
            return this;
        }

        private List<?> lookup(String sql) {
            // 因果链 SQL 同时含 ecos_wm_goal（JOIN），须先判更具体的片段
            for (String key : List.of("ecos_dq_execution_result", "FROM ecos_dq_issue",
                    "FROM ecos_dq_rule", "ecos_wm_causal_link", "ecos_goal_tracking",
                    "ecos_wm_goal", "GROUP BY severity", "GROUP BY issue_type")) {
                if (sql.contains(key) && lists.containsKey(key)) {
                    return lists.get(key);
                }
            }
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            seenSql.add(sql);
            seenMappers.add(rowMapper);
            return (List<T>) lookup(sql);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            seenSql.add(sql);
            seenMappers.add(rowMapper);
            return (List<T>) lookup(sql);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            int value = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (sql.contains(e.getKey())) {
                    value = e.getValue();
                }
            }
            return (T) Integer.valueOf(value);
        }

        RowMapper<?> mapperFor(String sqlFragment) {
            for (int i = 0; i < seenSql.size(); i++) {
                if (seenSql.get(i).contains(sqlFragment)) {
                    return seenMappers.get(i);
                }
            }
            throw new IllegalStateException("未捕获该腿的 RowMapper: " + sqlFragment);
        }
    }

    @Test
    @DisplayName("规则列表：容器 2 键；行 13 个 snake 键（键序）")
    void rulesKeys() {
        FakeService service = new FakeService().list("FROM ecos_dq_rule", List.of(
                new DqDashboardController.DqRuleRow("r-1", "C1", "名称", "d", "RANGE", "订单表",
                        "金额", "amount > 0", "HIGH", "ACTIVE", "{}", "2026-01-02T03:04:05",
                        Timestamp.valueOf("2026-01-02 03:04:06"))));
        DqDashboardController controller = new DqDashboardController(service);

        ApiResponse<DqDashboardController.DataPage<DqDashboardController.DqRuleRow>> resp =
                controller.listRules("", "");
        assertEquals(List.of("data", "total"), keysOf(resp.getData()));
        assertEquals(List.of("id", "code", "name", "description", "rule_type", "target_entity",
                "target_field", "rule_expression", "severity", "status", "params", "created_at",
                "updated_at"), keysOf(resp.getData().data().get(0)));
    }

    @Test
    @DisplayName("规则行映射器：created_at 由 Timestamp 转 LocalDateTime 文本，updated_at 保留原始 Timestamp")
    void rulesMapperPreservesMixedTimeShapes() throws Exception {
        FakeService service = new FakeService().list("FROM ecos_dq_rule", List.of());
        DqDashboardController controller = new DqDashboardController(service);
        controller.listRules("", "");

        @SuppressWarnings("unchecked")
        RowMapper<DqDashboardController.DqRuleRow> mapper =
                (RowMapper<DqDashboardController.DqRuleRow>) service.mapperFor("FROM ecos_dq_rule");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "r-1");
        row.put("created_at", Timestamp.valueOf("2026-01-02 03:04:05"));
        row.put("updated_at", Timestamp.valueOf("2026-01-02 03:04:06"));
        DqDashboardController.DqRuleRow mapped = mapper.mapRow(fakeResultSet(row), 0);

        assertEquals("2026-01-02T03:04:05", mapped.createdAt());
        assertEquals(Timestamp.valueOf("2026-01-02 03:04:06"), mapped.updatedAt(),
                "updated_at 旧实现即原始 Timestamp，本批不改语义");
    }

    @Test
    @DisplayName("看板：6 个 snake 键；计数与分组列表")
    void dashboardKeys() {
        FakeService service = new FakeService()
                .list("GROUP BY severity", List.of(new DqDashboardController.SeverityCount("HIGH", 4L)))
                .list("GROUP BY issue_type", List.of(new DqDashboardController.IssueTypeCount("NULL_RATE", 6L)))
                .count("ecos_dq_rule", 5)
                .count("status = 'ACTIVE'", 3)
                .count("ecos_dq_issue", 7)
                .count("status = 'OPEN'", 2);
        DqDashboardController controller = new DqDashboardController(service);

        DqDashboardController.DqDashboardVO vo = controller.dashboard().getData();
        assertEquals(List.of("total_rules", "active_rules", "total_issues", "open_issues",
                "by_severity", "by_type"), keysOf(vo));
        assertEquals(List.of("severity", "count"), keysOf(vo.bySeverity().get(0)));
        assertEquals(List.of("issue_type", "count"), keysOf(vo.byType().get(0)));
        assertEquals(2, vo.openIssues());
    }

    @Test
    @DisplayName("问题/执行列表：行 15/8 个 snake 键")
    void issuesAndExecutionsKeys() {
        FakeService service = new FakeService()
                .list("FROM ecos_dq_issue", List.of(new DqDashboardController.DqIssueRow("i-1", "r-1",
                        "订单", "e-1", "金额", "RANGE", "HIGH", "超阈", "150", "100", "OPEN", "alice",
                        "2026-01-02T03:04:05", null, null)))
                .list("ecos_dq_execution_result", List.of(new DqDashboardController.DqExecutionRow(
                        "x-1", "r-1", "规则一", true, 100, 0, null, "2026-01-02T03:04:05")));
        DqDashboardController controller = new DqDashboardController(service);

        assertEquals(List.of("id", "rule_id", "entity_type", "entity_id", "field_name", "issue_type",
                "severity", "description", "current_value", "expected_value", "status", "assigned_to",
                "detected_at", "resolved_at", "resolution_note"),
                keysOf(controller.listIssues("", "", 100).getData().data().get(0)));
        assertEquals(List.of("id", "rule_id", "rule_name", "passed", "total_rows", "failed_rows",
                "error_details", "executed_at"),
                keysOf(controller.listExecutions("", 10).getData().data().get(0)));
    }

    @Test
    @DisplayName("因果偏差：有链时 rootCause 在尾；空链不得出现 rootCause 键")
    void causalRootCauseIsConditional() {
        FakeService service = new FakeService()
                .list("ecos_wm_goal", List.of(new DqDashboardController.GoalDeviation(1L, "营收",
                        new BigDecimal("10"), new BigDecimal("6"), 40.0, "AT_RISK")))
                .list("ecos_wm_causal_link", List.of(new DqDashboardController.CausalLink(9L,
                        "营收完成率", new BigDecimal("10"), new BigDecimal("6"), "利润完成率",
                        new BigDecimal("8"), new BigDecimal("5"), "NEGATIVE", "d")));
        DqDashboardController controller = new DqDashboardController(service);

        Map<String, Object> withChain = asMap(controller.causalDeviation("", "", "").getData());
        assertEquals(List.of("goalDeviations", "causalChains", "rootCause"),
                new ArrayList<>(withChain.keySet()));
        assertEquals(List.of("chainId", "sourceNode", "targetNode", "propagatedImpact"),
                keysOf(((List<?>) withChain.get("causalChains")).get(0)));

        service.list("ecos_wm_causal_link", List.of());
        Map<String, Object> empty = asMap(controller.causalDeviation("", "", "").getData());
        assertEquals(Set.of("goalDeviations", "causalChains"), empty.keySet(),
                "无链时不得出现 rootCause:null 键");
    }

    @Test
    @DisplayName("目标追踪：2 键＋行 9 键；旧写面恒 405")
    void goalTrackingAndLegacyStubs() {
        FakeService service = new FakeService().list("ecos_goal_tracking",
                List.of(new DqDashboardController.GoalTrackingRow("g-1", 1L, "营收", 60,
                        new BigDecimal("6"), new BigDecimal("10"), "n", "2026-01-02", "AT_RISK")));
        DqDashboardController controller = new DqDashboardController(service);

        ApiResponse<DqDashboardController.GoalTrackingVO> resp = controller.getGoalTracking(null);
        assertEquals(List.of("tracking", "total"), keysOf(resp.getData()));
        assertEquals(List.of("id", "goalId", "goalName", "progress", "actualValue", "targetValue",
                "note", "recordedAt", "status"), keysOf(resp.getData().tracking().get(0)));

        assertEquals(405, controller.createRule().getCode());
        assertEquals(405, controller.updateIssue("i-1").getCode());
        assertEquals(405, controller.executeRule("r-1").getCode());
        assertEquals(405, controller.executeAll().getCode());
        assertNull(controller.createRule().getData());
        assertTrue("METHOD_NOT_ALLOWED".equals(controller.createRule().getErrorCode()));
    }

    /** 以 Map 驱动的 ResultSet 假面（只答本门用到的取值方法）。 */
    private static ResultSet fakeResultSet(Map<String, Object> values) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getString".equals(name) || "getTimestamp".equals(name) || "getBoolean".equals(name)
                    || "getInt".equals(name) || "getLong".equals(name)
                    || "getBigDecimal".equals(name) || "getDate".equals(name)) {
                return values.get((String) args[0]);
            }
            if ("wasNull".equals(name)) {
                return false;
            }
            return null;
        };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[] { ResultSet.class }, handler);
    }
}
