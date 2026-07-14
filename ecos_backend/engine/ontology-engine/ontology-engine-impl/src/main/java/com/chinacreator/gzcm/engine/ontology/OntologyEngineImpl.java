package com.chinacreator.gzcm.engine.ontology;

import com.chinacreator.gzcm.common.engine.EngineStatus;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.common.engine.IEngine;
import com.chinacreator.gzcm.engine.ontology.service.OntologyEngineHealthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OntologyEngineImpl implements IEngine {

    private static final Logger log = LoggerFactory.getLogger(OntologyEngineImpl.class);

    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private final OntologyEngineHealthService healthService;

    public OntologyEngineImpl(OntologyEngineHealthService healthService) {
        this.healthService = healthService;
    }

    @PostConstruct
    void autoStart() {
        start();
        log.info("Ontology engine auto-started on boot");
    }

    @Override
    public String getName() {
        return "ontology-engine";
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
                "module", "ontology",
                "controllers", 20,
                "services", 3,
                "status", status.get().name(),
                "ontologies", healthService.countOntologies(),
                "entities", healthService.countEntities(),
                "properties", healthService.countProperties(),
                "relationships", healthService.countRelationships()
        );
    }

    @Override
    public HealthCheck healthCheck() {
        boolean dbUp = healthService.pingDb();
        long ontCount = healthService.countOntologies();
        String overall = (status.get() == EngineStatus.RUNNING && dbUp) ? "UP" : "DOWN";
        return new HealthCheck(overall, Map.of(
                "db", dbUp ? "UP" : "DOWN",
                "engine", status.get().name(),
                "ontologies", ontCount >= 0 ? ontCount : "N/A"
        ));
    }

    @Override
    public void start() {
        EngineStatus prev = status.getAndSet(EngineStatus.RUNNING);
        if (prev != EngineStatus.RUNNING) {
            log.info("Ontology engine started (was {})", prev);
        }
    }

    @Override
    public void stop() {
        EngineStatus prev = status.getAndSet(EngineStatus.STOPPED);
        if (prev != EngineStatus.STOPPED) {
            log.info("Ontology engine stopped (was {})", prev);
        }
    }
}
