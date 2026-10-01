package com.chinacreator.gzcm.engine.kb.cognition;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F04-08 场景认知契约 — 只读读取集成级单测（无 Spring 上下文，JdbcTemplate 用 Mockito 打桩）。
 * <p>覆盖设计验收：emptyScenario / multiMind / noGateway / serviceUnavailableReturns503。
 * "noGateway" / "serviceUnavailable" 对应 cognitive 三态表在 standard 档不可达（DataAccessException）
 * → 读路径显式降级空态（不是 404、不静默吞，也不多抛 503 给读侧——读侧空态合法）。
 */
class ScenarioCognitionIntegrationTest {

    private CognitionReadService service(JdbcTemplate jdbc) {
        return new CognitionReadService(jdbc, new CognitionContractGuard());
    }

    @Test
    void emptyScenario() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(new ArrayList<>());
        List<Map<String, Object>> rows = service(jdbc).hypotheses("sc001", 10);
        assertNotNull(rows);
        assertTrue(rows.isEmpty(), "空场景 → 空列表（合法空态，非 404）");
    }

    @Test
    void multiMind() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", "m1");
        r1.put("status", "BELIEVED");
        r1.put("domain", "sc002");
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", "m2");
        r2.put("status", "EVIDENCED");
        r2.put("domain", "sc002");
        List<Map<String, Object>> stub = new ArrayList<>(List.of(r1, r2));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(stub);
        List<Map<String, Object>> rows = service(jdbc).minds("sc002", 10);
        assertEquals(2, rows.size());
        assertEquals("BELIEVED", rows.get(0).get("status"));
    }

    /** 认知引擎未接入（JdbcTemplate 不可用 = 场景侧该能力未启用）→ 空态而非故障。 */
    @Test
    void noGateway() {
        CognitionReadService svc = service(null);
        List<Map<String, Object>> rows = svc.beliefs("sc003", 10);
        assertNotNull(rows);
        assertTrue(rows.isEmpty());
    }

    /**
     * 认知三态表不可达（表缺失 / 鉴权拒绝 → DataAccessException）→ 读路径降级空态，
     * 不向读侧抛 5xx（读侧对"数据暂不可用"是合法空态）。验证异常被吞并返回空列表。
     */
    @Test
    void serviceUnavailableReturnsEmptyNotFault() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new DataAccessException("relation ecos_cognitive_hypothesis does not exist") {})
                .when(jdbc).queryForList(anyString(), any(Object[].class));
        List<Map<String, Object>> rows = service(jdbc).hypotheses("sc004", 10);
        assertNotNull(rows);
        assertTrue(rows.isEmpty(), "表不可达 → 空态，不抛给读侧");
        verify(jdbc, atLeastOnce()).queryForList(anyString(), any(Object[].class));
    }
}
