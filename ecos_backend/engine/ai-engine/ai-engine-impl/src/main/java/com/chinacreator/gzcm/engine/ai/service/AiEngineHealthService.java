package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.AiEngineHealthDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(AiEngineHealthService.class);

    private final AiEngineHealthDao dao;

    public AiEngineHealthService(AiEngineHealthDao dao) {
        this.dao = dao;
    }

    public boolean pingDb() {
        try {
            dao.pingDb();
            return true;
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return false;
        }
    }

    public long countAgents() {
        try {
            return dao.countAgents();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countMissions() {
        try {
            return dao.countMissions();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countGuardrailPolicies() {
        try {
            return dao.countGuardrailPolicies();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countExecutionPlans() {
        try {
            return dao.countExecutionPlans();
        } catch (Exception e) {
            return -1;
        }
    }
}
