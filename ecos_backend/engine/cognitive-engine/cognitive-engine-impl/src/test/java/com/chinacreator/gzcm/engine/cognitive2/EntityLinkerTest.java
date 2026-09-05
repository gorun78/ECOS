package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.service.EntityLinker;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-3.2 T6 — EntityLinker 行为测试（降级策略：endpoint 不可用时 status=fallback，不抛异常）。
 */
class EntityLinkerTest {

    private final EntityLinker linker = new EntityLinker();

    @Test
    void invalidInputReturnsInvalidStatus() {
        Map<String, Object> r = linker.linkEntity("", "");
        assertEquals("invalid_input", r.get("status"));

        Map<String, Object> r2 = linker.linkEntity(null, "");
        assertEquals("invalid_input", r2.get("status"));
    }

    @Test
    void noEndpointDegradesToFallback() {
        // 当前测试环境没有起 kb-engine 8080，期望：status=fallback, ontologyPath=未匹配
        Map<String, Object> r = linker.linkEntity("应收账款", "财务科目");
        if ("fallback".equals(r.get("status"))) {
            assertEquals("未匹配", r.get("ontologyPath"));
            assertEquals(0.0, ((Number) r.get("confidence")).doubleValue());
        } else if ("linked".equals(r.get("status"))) {
            assertNotNull(r.get("ontologyPath"));
        } else {
            fail("unexpected status: " + r.get("status"));
        }
    }

    @Test
    void batchLinkReturnsResultPerEntity() {
        List<Map<String, String>> ents = List.of(
                Map.of("name", "A", "type", "t1"),
                Map.of("name", "B", "type", "t2"),
                Map.of("name", "C", "type", "t3")
        );
        List<Map<String, Object>> out = linker.linkEntities(ents);
        assertEquals(3, out.size());
        for (Map<String, Object> o : out) {
            assertNotNull(o.get("status"));
            assertNotNull(o.get("entityName"));
        }
    }
}
