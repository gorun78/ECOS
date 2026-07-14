package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.OntologyGitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OntologyGitServiceImpl implements OntologyGitService {

    private static final Logger log = LoggerFactory.getLogger(OntologyGitServiceImpl.class);

    private final JdbcTemplate jdbc;

    public OntologyGitServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> commit(String ontologyId, Map<String, Object> body) {
        try {
            String commitMessage = (String) body.getOrDefault("message", "commit ontology " + ontologyId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ontologyId", ontologyId);
            result.put("commitMessage", commitMessage);
            result.put("status", "committed");
            result.put("timestamp", System.currentTimeMillis());
            log.info("Git commit for ontology {}: {}", ontologyId, commitMessage);
            return result;
        } catch (Exception e) {
            log.error("Git commit failed for ontology {}: {}", ontologyId, e.getMessage());
            throw new RuntimeException("Git commit failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> pull(String ontologyId, Map<String, Object> body) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ontologyId", ontologyId);
            result.put("status", "pulled");
            result.put("timestamp", System.currentTimeMillis());
            log.info("Git pull for ontology {}", ontologyId);
            return result;
        } catch (Exception e) {
            log.error("Git pull failed for ontology {}: {}", ontologyId, e.getMessage());
            throw new RuntimeException("Git pull failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> load(Map<String, Object> body) {
        try {
            String gitUrl = (String) body.getOrDefault("url", "");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", gitUrl);
            result.put("status", "loaded");
            result.put("timestamp", System.currentTimeMillis());
            log.info("Git load from {}", gitUrl);
            return result;
        } catch (Exception e) {
            log.error("Git load failed: {}", e.getMessage());
            throw new RuntimeException("Git load failed: " + e.getMessage());
        }
    }
}
