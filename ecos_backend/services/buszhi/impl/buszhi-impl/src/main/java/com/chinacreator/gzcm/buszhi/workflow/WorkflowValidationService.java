package com.chinacreator.gzcm.buszhi.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工作流定义 JSON Schema 验证服务。
 * <p>验证节点、边、必须字段、孤立节点、循环依赖等。</p>
 */
@Component
public class WorkflowValidationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowValidationService.class);
    private final ObjectMapper objectMapper;

    public WorkflowValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 验证结果 */
    public static class ValidationResult {
        private boolean valid;
        private List<Map<String, String>> errors = new ArrayList<>();
        private List<Map<String, String>> warnings = new ArrayList<>();
        private List<Map<String, String>> suggestions = new ArrayList<>();

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<Map<String, String>> getErrors() { return errors; }
        public List<Map<String, String>> getWarnings() { return warnings; }
        public List<Map<String, String>> getSuggestions() { return suggestions; }

        public void addError(String code, String nodeId, String message) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("code", code);
            e.put("nodeId", nodeId);
            e.put("message", message);
            errors.add(e);
            valid = false;
        }

        public void addWarning(String message) {
            Map<String, String> w = new LinkedHashMap<>();
            w.put("message", message);
            warnings.add(w);
        }

        public void addSuggestion(String message) {
            Map<String, String> s = new LinkedHashMap<>();
            s.put("message", message);
            suggestions.add(s);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("valid", valid);
            map.put("errors", errors);
            map.put("warnings", warnings);
            map.put("suggestions", suggestions);
            return map;
        }
    }

    /**
     * 验证完整 WorkflowDefinition JSON。
     */
    @SuppressWarnings("unchecked")
    public ValidationResult validate(Map<String, Object> definition) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.getOrDefault("nodes", Collections.emptyList());
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.getOrDefault("edges", Collections.emptyList());

        // ── WF-010: 必须包含开始和结束节点 ──
        boolean hasStart = nodes.stream().anyMatch(n -> "start".equals(n.get("type")));
        boolean hasEnd = nodes.stream().anyMatch(n -> "end".equals(n.get("type")));
        if (!hasStart) result.addError("WF-010", null, "缺少开始节点 (type=start)");
        if (!hasEnd) result.addError("WF-010", null, "缺少结束节点 (type=end)");

        // ── 收集节点ID ──
        Set<String> nodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            if (nodeId == null || nodeId.isBlank()) {
                result.addError("WF-003", null, "节点缺少 ID");
                continue;
            }
            nodeIds.add(nodeId);

            // ── 验证每种节点类型 ──
            String type = (String) node.get("type");
            if (type == null || !Set.of("start", "end", "userTask", "agentTask", "exclusiveGateway").contains(type)) {
                result.addError("WF-003", nodeId, "未知节点类型: " + type);
                continue;
            }

            Map<String, Object> config = (Map<String, Object>) node.get("config");
            validateNodeConfig(type, nodeId, config, result);
        }

        // ── WF-011: 孤立节点检查 ──
        Set<String> connectedNodes = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            String target = (String) edge.get("target");
            if (source != null) connectedNodes.add(source);
            if (target != null) connectedNodes.add(target);
        }
        for (String nodeId : nodeIds) {
            if (!connectedNodes.contains(nodeId)) {
                result.addError("WF-011", nodeId, "存在孤立节点（无任何连线）");
            }
        }

        // ── WF-012: 循环依赖检查 ──
        if (hasCycle(nodes, edges)) {
            result.addError("WF-012", null, "工作流定义存在循环依赖");
        }

        // ── 建议 ──
        for (Map<String, Object> node : nodes) {
            String type = (String) node.get("type");
            String nodeId = (String) node.get("id");
            Map<String, Object> config = (Map<String, Object>) node.get("config");
            if ("agentTask".equals(type) && (config == null || !Boolean.TRUE.equals(config.get("humanReview")))) {
                result.addWarning("Agent 节点 '" + nodeId + "' 未启用人工复核");
            }
            if ("exclusiveGateway".equals(type) && config != null) {
                List<Map<String, Object>> conditions = (List<Map<String, Object>>) config.get("conditions");
                if (conditions != null && conditions.stream().noneMatch(c -> Boolean.TRUE.equals(c.get("isDefault")))) {
                    result.addWarning("条件网关 '" + nodeId + "' 缺少默认分支");
                }
            }
        }

        return result;
    }

    private void validateNodeConfig(String type, String nodeId,
                                     Map<String, Object> config, ValidationResult result) {
        if (config == null) {
            result.addWarning("节点 '" + nodeId + "' 未配置参数");
            return;
        }
        switch (type) {
            case "userTask" -> {
                if (config.get("assignee") == null && config.get("candidateUsers") == null
                    && config.get("candidateRoles") == null) {
                    result.addError("WF-003", nodeId, "任务节点未指定处理人 (assignee/candidateUsers/candidateRoles)");
                }
            }
            case "agentTask" -> {
                Object agentConfigObj = config.get("agentConfig");
                if (agentConfigObj instanceof Map agentConfig) {
                    if (agentConfig.get("agentId") == null) {
                        result.addWarning("Agent 节点 '" + nodeId + "' 未指定 Agent ID");
                    }
                }
            }
            case "exclusiveGateway" -> {
                Object condObj = config.get("conditions");
                if (condObj instanceof List conds && conds.isEmpty()) {
                    result.addError("WF-003", nodeId, "条件网关未定义分支条件");
                }
            }
        }
    }

    /**
     * 简单的 DFS 循环检测。
     */
    private boolean hasCycle(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        Set<String> allNodes = new HashSet<>();
        for (Map<String, Object> n : nodes) allNodes.add((String) n.get("id"));
        for (Map<String, Object> e : edges) {
            String src = (String) e.get("source");
            String tgt = (String) e.get("target");
            if (src != null && tgt != null) {
                adj.computeIfAbsent(src, k -> new ArrayList<>()).add(tgt);
            }
        }
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        for (String node : allNodes) {
            if (detectCycle(node, adj, visited, recStack)) return true;
        }
        return false;
    }

    private boolean detectCycle(String node, Map<String, List<String>> adj,
                                 Set<String> visited, Set<String> recStack) {
        if (recStack.contains(node)) return true;
        if (visited.contains(node)) return false;
        visited.add(node);
        recStack.add(node);
        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            if (detectCycle(neighbor, adj, visited, recStack)) return true;
        }
        recStack.remove(node);
        return false;
    }
}
