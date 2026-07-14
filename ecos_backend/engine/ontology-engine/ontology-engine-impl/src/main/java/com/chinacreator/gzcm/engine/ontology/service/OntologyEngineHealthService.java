package com.chinacreator.gzcm.engine.ontology.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OntologyEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(OntologyEngineHealthService.class);

    private final JdbcTemplate jdbc;

    public OntologyEngineHealthService(JdbcTemplate jdbc) {
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

    public long countOntologies() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_ontology", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countEntities() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_ontology_entity", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countProperties() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_ontology_property", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countRelationships() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_ontology_relationship", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }
}
