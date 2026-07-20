package com.chinacreator.gzcm.engine.ai;

import com.chinacreator.gzcm.common.engine.EngineStatus;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.common.engine.IEngine;
import com.chinacreator.gzcm.engine.ai.service.AiEngineHealthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AiEngineImpl implements IEngine {

    private static final Logger log = LoggerFactory.getLogger(AiEngineImpl.class);

    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private final AiEngineHealthService healthService;

    public AiEngineImpl(AiEngineHealthService healthService) {
        this.healthService = healthService;
    }

    @PostConstruct
    void autoStart() {
        start();
        log.info("Cognitive engine auto-started on boot");
    }

    @Override
    public String getName() {
        return "cognitive-engine";
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
                "module", "cognitive",
                "controllers", 20,
                "services", 5,
                "status", status.get().name(),
                "subEngines", List.of("prompt-compiler", "agent-mesh", "guardrails", "action-bridge"),
                "agents", healthService.countAgents(),
                "missions", healthService.countMissions()
        );
    }

    @Override
    public HealthCheck healthCheck() {
        boolean dbUp = healthService.pingDb();
        String overall = (status.get() == EngineStatus.RUNNING && dbUp) ? "UP" : "DOWN";
        return new HealthCheck(overall, Map.of(
                "db", dbUp ? "UP" : "DOWN",
                "engine", status.get().name(),
                "agents", healthService.countAgents()
        ));
    }

    @Override
    public void start() {
        EngineStatus prev = status.getAndSet(EngineStatus.RUNNING);
        if (prev != EngineStatus.RUNNING) {
            log.info("Cognitive engine started (was {})", prev);
        }
    }

    @Override
    public void stop() {
        EngineStatus prev = status.getAndSet(EngineStatus.STOPPED);
        if (prev != EngineStatus.STOPPED) {
            log.info("Cognitive engine stopped (was {})", prev);
        }
    }
}
