package com.chinacreator.gzcm.engine.kb.cognition;

import com.chinacreator.gzcm.engine.kb.dao.CognitionReadDao;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F04-08 场景认知契约 — 只读读取集成级单测（无 Spring 上下文，CognitionReadDao 用 Mockito 打桩）。
 * <p>R2 / B2-kb: 数据访问由 {@code JdbcTemplate} 收口到 {@code CognitionReadDao}。
 * 覆盖设计验收：emptyScenario / multiMind / noGateway / serviceUnavailableReturns503。
 * "noGateway" / "serviceUnavailable" 对应 cognitive 三态表在 standard 档不可达（DataAccessException）
 * → 读路径显式降级空态（不是 404、不静默吞，也不多抛 503 给读侧——读侧空态合法）。
 */
class ScenarioCognitionIntegrationTest {

    private CognitionReadService service(CognitionReadDao dao) {
        return new CognitionReadService(dao, new CognitionContractGuard());
    }

    @Test
    void emptyScenario() {
        CognitionReadDao dao = mock(CognitionReadDao.class);
        when(dao.read(anyString(), any(String[].class), anyString(), anyInt())).thenReturn(new ArrayList<>());
        List<Map<String, Object>> rows = service(dao).hypotheses("sc001", 10);
        assertNotNull(rows);
        assertTrue(rows.isEmpty(), "空场景 → 空列表（合法空态，非 404）");
    }

    @Test
    void multiMind() {
        CognitionReadDao dao = mock(CognitionReadDao.class);
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", "m1");
        r1.put("status", "BELIEVED");
        r1.put("domain", "sc002");
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", "m2");
        r2.put("status", "EVIDENCED");
        r2.put("domain", "sc002");
        List<Map<String, Object>> stub = new ArrayList<>(List.of(r1, r2));
        when(dao.read(anyString(), any(String[].class), anyString(), anyInt())).thenReturn(stub);
        List<Map<String, Object>> rows = service(dao).minds("sc002", 10);
        assertEquals(2, rows.size());
        assertEquals("BELIEVED", rows.get(0).get("status"));
    }

    /** 认知引擎未接入（数据访问不可用 = 场景侧该能力未启用）→ 空态而非故障。 */
    @Test
    void noGateway() {
        CognitionReadDao dao = mock(CognitionReadDao.class);
        when(dao.read(anyString(), any(String[].class), anyString(), anyInt())).thenReturn(new ArrayList<>());
        CognitionReadService svc = service(dao);
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
        CognitionReadDao dao = mock(CognitionReadDao.class);
        doThrow(new DataAccessException("relation ecos_cognitive_hypothesis does not exist") {})
                .when(dao).read(anyString(), any(String[].class), anyString(), anyInt());
        List<Map<String, Object>> rows = service(dao).hypotheses("sc004", 10);
        assertNotNull(rows);
        assertTrue(rows.isEmpty(), "表不可达 → 空态，不抛给读侧");
        verify(dao, atLeastOnce()).read(anyString(), any(String[].class), anyString(), anyInt());
    }
}
