package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.services.agent.runtime.evolution.EvolutionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/evolution")
public class EvolutionController {

    private static final Logger log = LoggerFactory.getLogger(EvolutionController.class);

    private final EvolutionOrchestrator orchestrator;

    public EvolutionController(EvolutionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/trigger")
    public ApiResponse<Map<String, Object>> trigger(@RequestBody Map<String, Object> request) {
        try {
            String trigger = (String) request.getOrDefault("trigger", "MANUAL");
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) request.getOrDefault("context", Map.of());
            String missionId = orchestrator.triggerEvolution(trigger, context);
            log.info("Evolution triggered: missionId={}", missionId);
            return ApiResponse.success(Map.of("missionId", missionId, "status", "STARTED"));
        } catch (Exception e) {
            log.error("Evolution trigger failed", e);
            return ApiResponse.internalError("Evolution trigger failed: " + e.getMessage());
        }
    }

    @GetMapping("/log/{missionId}")
    public ApiResponse<List<Map<String, Object>>> getLog(@PathVariable String missionId) {
        try {
            List<Map<String, Object>> logEntries = orchestrator.getEvolutionLog(missionId);
            return ApiResponse.success(logEntries);
        } catch (Exception e) {
            log.error("Get evolution log failed: missionId={}", missionId, e);
            return ApiResponse.internalError("Get evolution log failed: " + e.getMessage());
        }
    }
}
