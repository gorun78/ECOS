package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController("cognitiveScenarioController")
@RequestMapping("/api/v1/cognitive/scenario")
public class ScenarioController {
    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    @Autowired
    private ScenarioSimulatorService simulatorService;

    @PostMapping("/simulate")
    public ApiResponse<SimulationResult> simulate(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.getOrDefault("variables", new HashMap<>());
        String domain = (String) request.getOrDefault("domain", "default");

        log.info("情景推演: name={}, domain={}", name, domain);

        Scenario scenario = new Scenario();
        scenario.setId(UUID.randomUUID().toString());
        scenario.setName(name);
        scenario.setType(ScenarioType.CUSTOM);
        scenario.setAssumptions(new HashMap<>(variables));
        scenario.setDescription("情景推演: " + name + " in " + domain);

        SimulationResult result = simulatorService.runSimulation(scenario);
        return ApiResponse.success(result);
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> listTemplates() {
        // 预设场景模板
        List<Map<String, Object>> templates = new ArrayList<>();

        addTemplate(templates, "原材料涨价10%", "供应链",
            Map.of("raw_material_cost", "+10%"), "模拟原材料成本上涨对利润的影响");
        addTemplate(templates, "汇率波动5%", "财务",
            Map.of("exchange_rate", "+5%"), "模拟汇率变动对进出口业务的影响");
        addTemplate(templates, "客户流失20%", "销售",
            Map.of("customer_churn", "+20%"), "模拟大客户流失对营收的影响");
        addTemplate(templates, "IT预算削减15%", "IT",
            Map.of("it_budget", "-15%"), "模拟IT预算削减对系统运维的影响");
        addTemplate(templates, "新法规合规成本+30%", "合规",
            Map.of("compliance_cost", "+30%"), "模拟新法规实施对运营成本的影响");

        return ApiResponse.success(templates);
    }

    private void addTemplate(List<Map<String, Object>> list, String name,
            String domain, Map<String, String> vars, String desc) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("domain", domain);
        t.put("variables", vars);
        t.put("description", desc);
        list.add(t);
    }

    @PostMapping("/compare")
    public ApiResponse<List<Map<String, Object>>> compare(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) request.get("scenarios");

        if (scenarios == null || scenarios.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> s : scenarios) {
            String name = (String) s.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> vars = (Map<String, Object>) s.getOrDefault("variables", new HashMap<>());

            Scenario scenario = new Scenario();
            scenario.setId(UUID.randomUUID().toString());
            scenario.setName(name);
            scenario.setType(ScenarioType.CUSTOM);
            scenario.setAssumptions(new HashMap<>(vars));

            SimulationResult result = simulatorService.runSimulation(scenario);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("confidence", result.getConfidence());
            entry.put("summary", result.getSummary());
            entry.put("predictions", result.getPredictions());
            results.add(entry);
        }

        return ApiResponse.success(results);
    }
}
