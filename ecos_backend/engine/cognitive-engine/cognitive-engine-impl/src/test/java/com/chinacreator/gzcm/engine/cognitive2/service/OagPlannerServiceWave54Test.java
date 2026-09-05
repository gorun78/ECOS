package com.chinacreator.gzcm.engine.cognitive2.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Wave-5.4 T-19 seed: OagPlannerService DAG 模板拆解. */
class OagPlannerServiceWave54Test {

    private final OagPlannerService planner = new OagPlannerService();

    @Test
    void handle_nullIntentAndSlots_stillProducesFiveLinearSubTasks() {
        Map<String, Object> result = planner.handle(null, null, null);

        assertNotNull(result.get("plan_id"));
        assertEquals("unknown", result.get("intent_id"));
        @SuppressWarnings("unchecked")
        var subTasks = (java.util.List<Map<String, Object>>) result.get("sub_tasks");
        assertEquals(5, subTasks.size());
        assertEquals("task-1", subTasks.get(0).get("sub_task_id"));
        assertTrue(((java.util.List<String>) subTasks.get(0).get("depends_on")).isEmpty());
        assertEquals("task-1", ((java.util.List<String>) subTasks.get(1).get("depends_on")).get(0));
    }

    @Test
    void handle_slotsForwardsDomainMetricDeviation() {
        Map<String, Object> slots = Map.of("domain", "sales", "metric", "revenue", "deviation", -0.22);
        Map<String, Object> result = planner.handle("intent-1", slots, null);

        assertEquals("intent-1", result.get("intent_id"));
        assertEquals("sales", result.get("domain"));
        assertEquals("revenue", result.get("metric"));
    }

    @Test
    void handle_missingSlotDefaultsApplied() {
        Map<String, Object> result = planner.handle("intent-2", Map.of("metric", "revenue"), null);

        assertEquals("default", result.get("domain"));
        assertEquals("revenue", result.get("metric"));
    }
}
