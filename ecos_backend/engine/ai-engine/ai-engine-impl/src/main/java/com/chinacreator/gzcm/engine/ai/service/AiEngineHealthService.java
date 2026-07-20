package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(AiEngineHealthService.class);

    private final JdbcTemplate jdbc;

    public AiEngineHealthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean pingDb() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return false;
        }
    }

    public long countAgents() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_agent_registry", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countMissions() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_mission", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countGuardrailPolicies() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_guardrail_policy", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countExecutionPlans() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_execution_plan", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }
}
