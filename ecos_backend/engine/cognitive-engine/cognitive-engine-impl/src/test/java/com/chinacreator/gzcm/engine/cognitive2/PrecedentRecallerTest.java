package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.dao.DecisionDao;
import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef;
import com.chinacreator.gzcm.engine.cognitive2.service.DecisionServiceImpl;
import com.chinacreator.gzcm.engine.cognitive2.service.PrecedentRecaller;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 T2 — PrecedentRecaller 行为测试（不依赖 mockito，用"DB down" DAO 兜底降级）。
 *
 * <p>说明：DecisionService.findSimilarDecisions 走 DB SQL，DB 不可用时抛异常，
 * PrecedentRecaller 内部 try-catch 降级为 empty 列表，符合 T2 验收。
 * B2-b 收口后 DAO 注入改为 {@link DecisionDao}，无法达 DataSource 的 DAO 以
 * 匿名子类覆写查询方法抛异常等价模拟 "DB down"。</p>
 */
class PrecedentRecallerTest {

    /** DB down 语义的 DecisionDao：任何数据查询都抛异常（等价于无 DataSource）。 */
    private DecisionDao unavailableDao() {
        return new DecisionDao() {
            @Override
            public List<Map<String, Object>> findSimilarDecisions(String category, String pattern, int maxResults) {
                throw new IllegalStateException("DB unavailable (no DataSource)");
            }
        };
    }

    @Test
    void failedDecisionServiceDegradesToEmpty() {
        // decisionService 内部走 DAO DB 查询（DB down → DAO 抛异常）
        PrecedentRecaller rec = new PrecedentRecaller(new DecisionServiceImpl(unavailableDao()));
        // 不抛异常，返回空
        List<PrecedentRef> refs = rec.recall("某场景", "finance", 3);
        assertNotNull(refs);
        assertTrue(refs.isEmpty(), "DB down 时 PrecedentRecaller 应返回空列表（不抛异常）");
    }

    @Test
    void noScenarioReturnsEmpty() {
        PrecedentRecaller rec = new PrecedentRecaller(new DecisionServiceImpl(null));
        assertTrue(rec.recall("", "finance", 3).isEmpty());
        assertTrue(rec.recall(null, "finance", 3).isEmpty());
    }

    @Test
    void toIndexMapsByPrecedentId() {
        PrecedentRecaller rec = new PrecedentRecaller(new DecisionServiceImpl(null));
        PrecedentRef r1 = new PrecedentRef("p-1", "d-1", "x", "approved", 0.8);
        PrecedentRef r2 = new PrecedentRef("p-2", "d-2", "y", "approved", 0.8);
        // 用 ArrayList 而不是 List.of（List.of 不接 null 元素）
        java.util.List<PrecedentRef> list = new java.util.ArrayList<>();
        list.add(r1);
        list.add(r2);
        Map<String, PrecedentRef> idx = rec.toIndex(list);
        assertEquals(2, idx.size());
        assertTrue(idx.containsKey("p-1"));
        assertTrue(idx.containsKey("p-2"));
        assertEquals("d-1", idx.get("p-1").getDecisionId());
    }

    @Test
    void decisionModelHasDecisionIdAndOutcome() {
        // G2: PrecedentRef 必须可挂 Decision 信息（T2 precondition）
        Decision d = new Decision();
        d.setId("d-1");
        d.setCategory("finance");
        d.setScenario("应收账款波动");
        d.setReasoning("客户回款周期延长");
        d.setOutcome("approved");
        d.setConfidence(0.78);
        PrecedentRef p = PrecedentRef.fromDecision(d, 0.81);
        assertEquals("d-1", p.getDecisionId());
        assertEquals("approved", p.getOutcome());
        assertTrue(p.getSummary().contains("应收账款"));
        assertEquals(0.81, p.getSimilarity());
    }
}
