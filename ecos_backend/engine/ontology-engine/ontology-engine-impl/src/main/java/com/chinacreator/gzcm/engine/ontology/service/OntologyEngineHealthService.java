package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.dao.OntologyEngineHealthDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OntologyEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(OntologyEngineHealthService.class);

    private final OntologyEngineHealthDao healthDao;

    public OntologyEngineHealthService(OntologyEngineHealthDao healthDao) {
        this.healthDao = healthDao;
    }

    public boolean pingDb() {
        try {
            healthDao.pingDb();
            return true;
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return false;
        }
    }

    public long countOntologies() {
        try {
            return healthDao.countOntologies();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countEntities() {
        try {
            return healthDao.countEntities();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countProperties() {
        try {
            return healthDao.countProperties();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countRelationships() {
        try {
            return healthDao.countRelationships();
        } catch (Exception e) {
            return -1;
        }
    }
}
