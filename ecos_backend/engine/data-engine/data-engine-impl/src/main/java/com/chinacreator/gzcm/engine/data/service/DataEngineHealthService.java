package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.dao.DataEngineHealthDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DataEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(DataEngineHealthService.class);

    private final DataEngineHealthDao healthDao;

    public DataEngineHealthService(DataEngineHealthDao healthDao) {
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

    public long countDatasources() {
        try {
            return healthDao.countDatasources();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countResources() {
        try {
            return healthDao.countResources();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countCatalogItems() {
        try {
            return healthDao.countCatalogItems();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countFields() {
        try {
            return healthDao.countFields();
        } catch (Exception e) {
            return -1;
        }
    }
}
