package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.common.engine.EngineStatus;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.common.engine.IEngine;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;

@Component
public class SecurityEngineImpl implements IEngine {

    private static final Logger log = LoggerFactory.getLogger(SecurityEngineImpl.class);

    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private final SecurityConfigService configService;

    public SecurityEngineImpl(SecurityConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    void autoStart() {
        start();
        log.info("Security engine auto-started on boot");
    }

    @Override
    public String getName() {
        return "security-engine";
    }

    @Override
    public EngineStatus getStatus() {
        return status.get();
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of(
                "module", "security",
                "controllers", 7,
                "services", 4,
                "status", status.get().name()
        );
    }

    @Override
    public HealthCheck healthCheck() {
        boolean dbUp = configService.ping();
        String overall = (status.get() == EngineStatus.RUNNING && dbUp) ? "UP" : "DOWN";
        return new HealthCheck(overall, Map.of("db", dbUp ? "UP" : "DOWN", "engine", status.get().name()));
    }

    @Override
    public void start() {
        EngineStatus prev = status.getAndSet(EngineStatus.RUNNING);
        if (prev != EngineStatus.RUNNING) {
            log.info("Security engine started (was {})", prev);
        }
    }

    @Override
    public void stop() {
        EngineStatus prev = status.getAndSet(EngineStatus.STOPPED);
        if (prev != EngineStatus.STOPPED) {
            log.info("Security engine stopped (was {})", prev);
        }
    }
}
