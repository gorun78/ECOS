package com.chinacreator.gzcm.engine.kb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KbEngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(KbEngineHealthService.class);

    private final JdbcTemplate jdbc;

    public KbEngineHealthService(JdbcTemplate jdbc) {
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

    public long countNodes() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.graph_node", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countEdges() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.graph_edge", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countArticles() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.knowledge_article", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countRules() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.expert_rule", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }

    public long countEmbeddings() {
        try {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_knowledge.knowledge_embedding", Long.class);
        } catch (Exception e) {
            return -1;
        }
    }
}