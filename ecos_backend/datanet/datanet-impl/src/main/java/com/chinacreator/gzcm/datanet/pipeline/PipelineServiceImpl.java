package com.chinacreator.gzcm.datanet.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Pipeline 定义管理服务实现 — 基于 JdbcTemplate 持久化。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class PipelineServiceImpl implements PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PipelineRepository repository;

    public PipelineServiceImpl(PipelineRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PipelineDefinition createDefinition(Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空");
        }

        PipelineDefinition def = new PipelineDefinition();
        def.setId(UUID.randomUUID().toString().replace("-", ""));
        def.setName(name);
        def.setDescription((String) body.getOrDefault("description", ""));
        def.setStatus((String) body.getOrDefault("status", "DRAFT"));

        PipelineDefinition created = repository.insertDefinition(def);

        // 处理节点
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) body.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> nodeMap : nodes) {
                PipelineNode node = new PipelineNode();
                node.setId(UUID.randomUUID().toString().replace("-", ""));
                node.setDefinitionId(created.getId());
                node.setNodeId((String) nodeMap.getOrDefault("nodeId", (String) nodeMap.get("id")));
                node.setType((String) nodeMap.getOrDefault("type", "TRANSFORM_SQL"));
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) nodeMap.get("config");
                try {
                    node.setConfig(config != null ? MAPPER.writeValueAsString(config) : "{}");
                } catch (Exception e) {
                    node.setConfig("{}");
                }

                Object px = nodeMap.get("positionX");
                node.setPositionX(px instanceof Number n ? n.intValue() : 0);
                Object py = nodeMap.get("positionY");
                node.setPositionY(py instanceof Number n ? n.intValue() : 0);

                repository.insertNode(node);
            }
        }

        // 处理 edges → 计算每个节点的 depends_on
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) body.get("edges");
        if (edges != null && !edges.isEmpty()) {
            // 构建 nodeId → [依赖它的节点列表]
            Map<String, List<String>> deps = new LinkedHashMap<>();
            for (Map<String, Object> edge : edges) {
                String from = (String) edge.get("from");
                String to = (String) edge.get("to");
                deps.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
            // 更新节点的 depends_on
            for (Map<String, Object> nodeMap : nodes) {
                String nodeId = (String) nodeMap.getOrDefault("nodeId", (String) nodeMap.get("id"));
                List<String> dependsOn = deps.getOrDefault(nodeId, Collections.emptyList());
                try {
                    String json = MAPPER.writeValueAsString(dependsOn);
                    repository.updateNodeDependsOn(created.getId(), nodeId, json);
                } catch (Exception e) {
                    log.warn("Failed to set depends_on for node {}", nodeId, e);
                }
            }
        }

        log.info("Created pipeline definition: {} (id={})", name, created.getId());
        return created;
    }

    @Override
    @Transactional
    public PipelineDefinition updateDefinition(String id, Map<String, Object> body) {
        if (!repository.definitionExists(id)) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + id);
        }

        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String status = (String) body.get("status");

        PipelineDefinition updated = repository.updateDefinition(id, name, description, status);

        // 如果传入了 nodes，则替换节点列表
        if (body.containsKey("nodes")) {
            repository.deleteNodesByDefinitionId(id);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) body.get("nodes");
            if (nodes != null) {
                for (Map<String, Object> nodeMap : nodes) {
                    PipelineNode node = new PipelineNode();
                    node.setId(UUID.randomUUID().toString().replace("-", ""));
                    node.setDefinitionId(id);
                    node.setNodeId((String) nodeMap.getOrDefault("nodeId", (String) nodeMap.get("id")));
                    node.setType((String) nodeMap.getOrDefault("type", "TRANSFORM_SQL"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) nodeMap.get("config");
                    try {
                        node.setConfig(config != null ? MAPPER.writeValueAsString(config) : "{}");
                    } catch (Exception e) {
                        node.setConfig("{}");
                    }

                    Object px = nodeMap.get("positionX");
                    node.setPositionX(px instanceof Number n ? n.intValue() : 0);
                    Object py = nodeMap.get("positionY");
                    node.setPositionY(py instanceof Number n ? n.intValue() : 0);

                    repository.insertNode(node);
                }
            }
        }

        log.info("Updated pipeline definition: id={}", id);
        return updated;
    }

    @Override
    @Transactional
    public void deleteDefinition(String id) {
        if (!repository.definitionExists(id)) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + id);
        }
        repository.deleteNodesByDefinitionId(id);
        repository.deleteDefinition(id);
        log.info("Deleted pipeline definition: id={}", id);
    }

    @Override
    public PipelineDefinition getDefinition(String id) {
        PipelineDefinition def = repository.findDefinitionById(id);
        if (def == null) {
            throw new IllegalArgumentException("Pipeline 定义不存在: " + id);
        }
        return def;
    }

    @Override
    public List<PipelineDefinition> listDefinitions() {
        return repository.findAllDefinitions();
    }
}
