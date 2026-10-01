package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dao.KbEngineHealthDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KbEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(KbEngineHealthService.class);

    private final KbEngineHealthDao kbEngineHealthDao;

    public KbEngineHealthService(KbEngineHealthDao kbEngineHealthDao) {
        this.kbEngineHealthDao = kbEngineHealthDao;
    }

    public boolean pingDb() {
        try {
            kbEngineHealthDao.pingDb();
            return true;
        } catch (Exception e) {
            log.warn("DB health check failed: {}", e.getMessage());
            return false;
        }
    }

    public long countNodes() {
        try {
            return kbEngineHealthDao.countNodes();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countEdges() {
        try {
            return kbEngineHealthDao.countEdges();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countArticles() {
        try {
            return kbEngineHealthDao.countArticles();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countRules() {
        try {
            return kbEngineHealthDao.countRules();
        } catch (Exception e) {
            return -1;
        }
    }

    public long countEmbeddings() {
        try {
            return kbEngineHealthDao.countEmbeddings();
        } catch (Exception e) {
            return -1;
        }
    }
}
