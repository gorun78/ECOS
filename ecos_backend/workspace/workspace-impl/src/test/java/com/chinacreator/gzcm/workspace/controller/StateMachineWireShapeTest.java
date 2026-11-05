package com.chinacreator.gzcm.workspace.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.StateMachineEngine;
import com.chinacreator.gzcm.workspace.dto.StateMachineVO;
import com.chinacreator.gzcm.workspace.dto.StateTransitionVO;
import com.chinacreator.gzcm.workspace.service.ObjectStateMachineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 状态机定义端点 wire 保真（DI-2 #147ED）。
 *
 * <p>状态机视图 4 键（entityCode/states/transitions/totalTransitions）；转换规则 8 键；
 * states 去重保序；sortOrder 保持 DB 原值。</p>
 */
class StateMachineWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 只覆盖 listDefinitions，引擎仓储依赖不参与。 */
    private static final class FakeEngine extends StateMachineEngine {
        private FakeEngine() {
            super(null, null);
        }

        @Override
        public List<Map<String, Object>> listDefinitions(String entityCode) {
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("from_status", "Draft");
            a.put("to_status", "Submitted");
            a.put("transition_code", "submit");
            a.put("transition_name", "提交");
            a.put("require_role", "role.A");
            a.put("guard_rule", null);
            a.put("side_effect", null);
            a.put("sort_order", 1);
            rows.add(a);
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("from_status", "Submitted");
            b.put("to_status", "Approved");
            b.put("transition_code", "approve");
            b.put("transition_name", "审批");
            b.put("require_role", null);
            b.put("guard_rule", "amount>0");
            b.put("side_effect", "notify");
            b.put("sort_order", 2);
            rows.add(b);
            return rows;
        }
    }

    @Test
    @DisplayName("状态机：4 键；states 去重保序；转换规则 8 键且 sortOrder 为数值")
    void stateMachineKeepsShape() throws Exception {
        ObjectStateMachineController controller =
                new ObjectStateMachineController(null, new FakeEngine());

        JsonNode data = MAPPER.valueToTree(controller.getStateMachine("Customer")).get("data");

        assertEquals(List.of("entityCode", "states", "transitions", "totalTransitions"),
                fieldNames(data));
        assertEquals("Customer", data.get("entityCode").asText());
        assertEquals("[\"Draft\",\"Submitted\",\"Approved\"]", data.get("states").toString());
        assertEquals(2, data.get("totalTransitions").asInt());
        JsonNode t = data.get("transitions").get(0);
        assertEquals(List.of("from", "to", "transitionCode", "transitionName", "requireRole",
                "guardRule", "sideEffect", "sortOrder"), fieldNames(t));
        assertEquals(1, t.get("sortOrder").asInt());
    }

    @Test
    @DisplayName("转换规则 DTO：键面与空值保留")
    void transitionDtoKeepsNulls() throws Exception {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("from_status", "A");
        def.put("to_status", "B");
        def.put("transition_code", "go");
        def.put("transition_name", null);
        def.put("require_role", null);
        def.put("guard_rule", null);
        def.put("side_effect", null);
        def.put("sort_order", null);

        JsonNode row = MAPPER.valueToTree(StateTransitionVO.of(def));
        assertEquals(8, row.size());
        assertEquals("A", row.get("from").asText());
        assertEquals(4, MAPPER.valueToTree(new StateMachineVO("E", List.of(), List.of(), 0))
                .size());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
