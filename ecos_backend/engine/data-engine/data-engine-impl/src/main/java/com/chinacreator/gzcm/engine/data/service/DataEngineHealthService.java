package com.chinacreator.gzcm.engine.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataEngineHealthService.class);

    private final JdbcTemplate jdbc;

    public DataEngineHealthService(JdbcTemplate jdbc) {
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

    public long countDatasources() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM td_datasource", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countResources() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM td_data_resource", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countCatalogItems() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM td_catalog_item", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countFields() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM td_data_field", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }
}
