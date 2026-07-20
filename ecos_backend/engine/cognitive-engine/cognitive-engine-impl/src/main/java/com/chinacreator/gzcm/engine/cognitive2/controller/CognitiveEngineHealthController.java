package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.engine.cognitive2.WorldModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cognitive")
public class CognitiveEngineHealthController {
    private static final Logger log = LoggerFactory.getLogger(CognitiveEngineHealthController.class);
    @Autowired
    private WorldModelService worldModelService;
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("engine", "COGNITIVE");
        Map<String, String> reasoners = new HashMap<>();
        reasoners.put("causalReasoner", "UP");
        reasoners.put("scenarioSimulator", "UP");
        reasoners.put("paretoOptimizer", "UP");
        status.put("reasoners", reasoners);
        return status;
    }
}
