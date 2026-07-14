package com.chinacreator.gzcm.engine.cognitive.service;

import com.chinacreator.gzcm.engine.cognitive.AgentMeshService;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.AgentRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgentMeshServiceImpl implements AgentMeshService {

    private static final Logger log = LoggerFactory.getLogger(AgentMeshServiceImpl.class);

    private final AgentRegistryRepository agentRepo;

    public AgentMeshServiceImpl(AgentRegistryRepository agentRepo) {
        this.agentRepo = agentRepo;
    }

    @Override
    public Map<String, Object> routeIntent(Map<String, Object> req) {
        String query = String.valueOf(req.getOrDefault("query", ""));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);

        List<AgentRegistryEntity> agents = agentRepo.findAll();
        AgentRegistryEntity bestMatch = null;
        double bestScore = 0;

        String queryLower = query.toLowerCase();
        for (AgentRegistryEntity agent : agents) {
            double score = 0;
            String name = agent.getName() != null ? agent.getName().toLowerCase() : "";
            String desc = agent.getDescription() != null ? agent.getDescription().toLowerCase() : "";
            if (queryLower.contains(name) || name.contains(queryLower)) score += 0.5;
            for (String kw : queryLower.split("[\\s，,]+")) {
                if (desc.contains(kw)) score += 0.2;
            }
            if (score > bestScore) {
                bestScore = score;
                bestMatch = agent;
            }
        }

        if (bestMatch != null && bestScore > 0.1) {
            result.put("targetAgentId", bestMatch.getId());
            result.put("targetAgentName", bestMatch.getName());
            result.put("confidence", bestScore);
            result.put("routingStrategy", "keyword_match");
        } else {
            result.put("targetAgentId", "default");
            result.put("targetAgentName", "Default Agent");
            result.put("confidence", 0.3);
            result.put("routingStrategy", "fallback");
        }
        return result;
    }

    @Override
    public Map<String, Object> getMissionStatus(String missionId) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("missionId", missionId);
        status.put("status", "UNKNOWN");
        return status;
    }
}
