package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.common.engine.EngineStatus;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.common.engine.IEngine;
import com.chinacreator.gzcm.engine.kb.service.KbEngineHealthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KbEngineImpl implements IEngine {

    private static final Logger log = LoggerFactory.getLogger(KbEngineImpl.class);

    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private final KbEngineHealthService healthService;

    public KbEngineImpl(KbEngineHealthService healthService) {
        this.healthService = healthService;
    }

    @PostConstruct
    void autoStart() {
        start();
        log.info("Knowledge engine auto-started on boot");
    }

    @Override
    public String getName() {
        return "kb-engine";
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
                "module", "knowledge",
                "controllers", 9,
                "services", 6,
                "status", status.get().name(),
                "subEngines", List.of("knowledge-graph", "expert-rules", "knowledge-retrieval", "kg-sync", "knowledge-settings", "ecos-knowledge-graph"),
                "nodes", healthService.countNodes(),
                "edges", healthService.countEdges(),
                "articles", healthService.countArticles(),
                "rules", healthService.countRules()
        );
    }

    @Override
    public HealthCheck healthCheck() {
        boolean dbUp = healthService.pingDb();
        String overall = (status.get() == EngineStatus.RUNNING && dbUp) ? "UP" : "DOWN";
        return new HealthCheck(overall, Map.of(
                "db", dbUp ? "UP" : "DOWN",
                "engine", status.get().name(),
                "nodes", healthService.countNodes(),
                "edges", healthService.countEdges(),
                "articles", healthService.countArticles(),
                "rules", healthService.countRules()
        ));
    }

    @Override
    public void start() {
        EngineStatus prev = status.getAndSet(EngineStatus.RUNNING);
        if (prev != EngineStatus.RUNNING) {
            log.info("Knowledge engine started (was {})", prev);
        }
    }

    @Override
    public void stop() {
        EngineStatus prev = status.getAndSet(EngineStatus.STOPPED);
        if (prev != EngineStatus.STOPPED) {
            log.info("Knowledge engine stopped (was {})", prev);
        }
    }
}