package com.chinacreator.gzcm.engine.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Wave-5.4 T-19 seed: ToolCall POJO/full contract. */
class ToolCallWave54Test {

    @Test
    void defaultConstructorInitializesMutableInvalidParams() {
        ToolCall tc = new ToolCall();
        assertNotNull(tc.getInvalidParams());
        tc.getInvalidParams().add("x");
        tc.getInvalidParams().clear();
    }

    @Test
    void parameterizedConstructorMapsFields() {
        Map<String, Object> args = Map.of("query", "sales");
        ToolCall tc = new ToolCall("call_1", "sql_query", args);

        assertEquals("call_1", tc.getId());
        assertEquals("sql_query", tc.getName());
        assertEquals(args, tc.getArguments());
        assertTrue(tc.getInvalidParams().isEmpty());
    }

    @Test
    void settersAndToStringRoundtrip() {
        ToolCall tc = new ToolCall();
        tc.setId("c2");
        tc.setName("rest_call");
        tc.setArguments(Map.of("url", "/api"));
        tc.setInvalidParams(List.of("url"));

        String s = tc.toString();
        assertTrue(s.contains("c2"));
        assertTrue(s.contains("rest_call"));
        assertTrue(s.contains("url"));
    }
}
