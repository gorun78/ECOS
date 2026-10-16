package com.chinacreator.gzcm.engine.data.quality.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * DqScoreDao 游标位置契约测试。
 *
 * <p>线上缺陷（2026-10-16 实测）：{@code POST /api/v1/dq/scores/recompute} 对全部 CURATED 资产
 * 返回 500 ECOS-DATA-031，日志 SQLState 24000「查询结果指标位置不正确，您也许需要呼叫
 * ResultSet 的 next() 方法」，栈停在 {@code DqScoreServiceImpl.loadRecentChecks:215}。
 * 根因＝{@code jdbcTemplate.query(sql, rs -> {…return null;})} 命中 <b>ResultSetExtractor</b>
 * 重载（不是 RowCallbackHandler），入参 ResultSet 停在首行之前，代码却直接取列。</p>
 *
 * <p>假 ResultSet 复刻 PgJDBC 两条语义，因此本测试对旧代码必然变红：</p>
 * <ol>
 *   <li>未 next() 前取列 → 抛 SQLState 24000；</li>
 *   <li>列标签大小写不敏感（PG 把未加引号的 {@code AS totalRows} 折叠成 {@code totalrows}，
 *       驱动仍可按 camelCase 取到——原「别名折叠」假设由此排除）。</li>
 * </ol>
 */
class DqScoreDaoCursorTest {

    private JdbcTemplate jdbcTemplate;
    private DqScoreDao dao;
    private final List<Map<String, Object>> fakeRows = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        dao = new DqScoreDao();
        jdbcTemplate = mock(JdbcTemplate.class);
        Field field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(dao, jdbcTemplate);
        fakeRows.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubSingleResultQuery() throws SQLException {
        // loadRecentChecks 走 query(sql, extractor, args...) 重载
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any(Object[].class)))
                .thenAnswer(inv -> runExtractor(inv.getArgument(1)));
        // perDimensionScores 走 query(sql, extractor) 两参重载
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(inv -> runExtractor(inv.getArgument(1)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object runExtractor(ResultSetExtractor extractor) throws SQLException {
        return extractor.extractData(cursorAwareRs());
    }

    // ==================== 1. loadRecentChecks：逐行消费 ====================

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void loadRecentChecksConsumesEveryRow() throws SQLException {
        stubSingleResultQuery();
        fakeRows.add(checkRow("rule-1", 100L, 3L, 0.97D));
        fakeRows.add(checkRow("rule-2", 200L, 50L, 0.75D));

        Map<String, Map<String, Object>> byRule = new LinkedHashMap<>();
        dao.loadRecentChecks("?,?", "INTERVAL '1 hour'", byRule::put, new Object[] {"rule-1", "rule-2"});

        assertEquals(2, byRule.size(), "两行检查记录都应落到 rule_id 索引（旧代码在此抛 24000）");
        assertEquals(100L, byRule.get("rule-1").get("totalRows"));
        assertEquals(0.97D, byRule.get("rule-1").get("passRate"));
        assertEquals(50L, byRule.get("rule-2").get("failedRows"));
        assertNotNull(byRule.get("rule-2").get("executedAt"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void loadRecentChecksToleratesEmptyResult() throws SQLException {
        stubSingleResultQuery();
        Map<String, Map<String, Object>> byRule = new LinkedHashMap<>();
        dao.loadRecentChecks("?", "INTERVAL '1 hour'", byRule::put, new Object[] {"rule-x"});
        assertTrue(byRule.isEmpty(), "无近期检查记录时返回空索引，不得抛异常");
    }

    // ==================== 2. perDimensionScores：逐行消费 ====================

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void perDimensionScoresConsumesEveryRow() throws SQLException {
        stubSingleResultQuery();
        fakeRows.add(Map.of("dimension", "COMPLETENESS", "avg_score", 0.81D));
        fakeRows.add(Map.of("dimension", "FRESHNESS", "avg_score", 0.66D));

        Map<String, Double> perDim = new LinkedHashMap<>();
        dao.perDimensionScores(perDim::put);

        assertEquals(Map.of("COMPLETENESS", 0.81D, "FRESHNESS", 0.66D), perDim);
    }

    // ==================== 3. 测试载体自身可红（PgJDBC 语义断言） ====================

    @Test
    void readingBeforeNextFailsLikePostgres() throws SQLException {
        fakeRows.add(checkRow("rule-1", 10L, 1L, 0.9D));
        ResultSet rs = cursorAwareRs();
        SQLException ex = assertThrows(SQLException.class, () -> rs.getLong("totalRows"));
        assertEquals("24000", ex.getSQLState(), "首行之前取列必须复刻驱动的 24000");
        assertTrue(rs.next());
        assertEquals(10L, callLong(rs, "TOTALROWS"), "camelCase 别名在折叠小写标签上仍可取到");
    }

    // ==================== 载体 ====================

    private static Map<String, Object> checkRow(String ruleId, long total, long failed, double passRate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleid", ruleId);
        row.put("passed", Boolean.TRUE);
        row.put("totalrows", total);
        row.put("failedrows", failed);
        row.put("passrate", passRate);
        row.put("executedat", new Timestamp(System.currentTimeMillis()));
        return row;
    }

    private static long callLong(ResultSet rs, String label) {
        try {
            return rs.getLong(label);
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    /** PgJDBC 语义的最小复刻：游标位置校验 + 标签大小写不敏感。 */
    private ResultSet cursorAwareRs() {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("next".equals(name)) {
                cursor[0]++;
                return cursor[0] < fakeRows.size();
            }
            if ("close".equals(name) || "clearWarnings".equals(name)) {
                return null;
            }
            if ("wasNull".equals(name)) {
                return false;
            }
            if (name.startsWith("get") && args != null && args.length == 1
                    && args[0] instanceof String label) {
                return value(label, name);
            }
            Class<?> rt = method.getReturnType();
            if (rt == boolean.class) {
                return false;
            }
            if (rt == int.class) {
                return 0;
            }
            if (rt == long.class) {
                return 0L;
            }
            if (rt == double.class) {
                return 0.0D;
            }
            if (rt == float.class) {
                return 0.0F;
            }
            if (rt == short.class) {
                return (short) 0;
            }
            if (rt == byte.class) {
                return (byte) 0;
            }
            return null;
        };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class}, handler);
    }

    private final int[] cursor = {-1};

    private Object value(String label, String getter) throws SQLException {
        if (cursor[0] < 0 || cursor[0] >= fakeRows.size()) {
            throw new SQLException("The result set is not positioned on a row.", "24000");
        }
        String key = label.toLowerCase(Locale.ROOT);
        Map<String, Object> row = fakeRows.get(cursor[0]);
        if (!row.containsKey(key)) {
            throw new SQLException("Column \"" + label + "\" not found.", "07000");
        }
        Object raw = row.get(key);
        return switch (getter) {
            case "getString" -> raw == null ? null : String.valueOf(raw);
            case "getLong" -> ((Number) raw).longValue();
            case "getInt" -> ((Number) raw).intValue();
            case "getDouble" -> ((Number) raw).doubleValue();
            case "getBoolean" -> raw;
            default -> raw;
        };
    }
}
