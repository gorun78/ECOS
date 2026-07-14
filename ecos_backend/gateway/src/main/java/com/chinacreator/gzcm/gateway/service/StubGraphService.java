package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.common.service.IGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Profile("!enterprise & !flagship")
public class StubGraphService implements IGraphService {
    private static final Logger log = LoggerFactory.getLogger(StubGraphService.class);

    @Override
    public List<Map<String, Object>> query(String cypherPattern, Map<String, Object> params) {
        log.debug("StubGraphService.query: {}", cypherPattern);
        return List.of();
    }

    @Override
    public void createNode(String label, Map<String, Object> props) {
        log.debug("StubGraphService.createNode: label={}", label);
    }

    @Override
    public void createRelationship(String fromId, String toId, String relType) {
        log.debug("StubGraphService.createRelationship: {} -> {}", fromId, toId);
    }

    @Override
    public Map<String, Object> getSubgraph(String entityId) {
        log.debug("StubGraphService.getSubgraph: entityId={}", entityId);
        return Map.of("nodes", List.of(), "edges", List.of());
    }
}
