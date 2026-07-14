package com.chinacreator.gzcm.engine.data;

import com.chinacreator.gzcm.common.engine.EngineStatus;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.common.engine.IEngine;
import com.chinacreator.gzcm.engine.data.service.DataEngineHealthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DataEngineImpl implements IEngine {

    private static final Logger log = LoggerFactory.getLogger(DataEngineImpl.class);

    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private final DataEngineHealthService healthService;

    public DataEngineImpl(DataEngineHealthService healthService) {
        this.healthService = healthService;
    }

    @PostConstruct
    void autoStart() {
        start();
        log.info("Data engine auto-started on boot");
    }

    @Override
    public String getName() {
        return "data-engine";
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
                "module", "data",
                "controllers", 15,
                "services", 14,
                "status", status.get().name(),
                "datasources", healthService.countDatasources(),
                "resources", healthService.countResources(),
                "fields", healthService.countFields()
        );
    }

    @Override
    public HealthCheck healthCheck() {
        boolean dbUp = healthService.pingDb();
        long dsCount = healthService.countDatasources();
        String overall = (status.get() == EngineStatus.RUNNING && dbUp) ? "UP" : "DOWN";
        return new HealthCheck(overall, Map.of(
                "db", dbUp ? "UP" : "DOWN",
                "engine", status.get().name(),
                "datasources", dsCount >= 0 ? dsCount : "N/A"
        ));
    }

    @Override
    public void start() {
        EngineStatus prev = status.getAndSet(EngineStatus.RUNNING);
        if (prev != EngineStatus.RUNNING) {
            log.info("Data engine started (was {})", prev);
        }
    }

    @Override
    public void stop() {
        EngineStatus prev = status.getAndSet(EngineStatus.STOPPED);
        if (prev != EngineStatus.STOPPED) {
            log.info("Data engine stopped (was {})", prev);
        }
    }
}
