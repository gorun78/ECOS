package com.chinacreator.gzcm.worldmodel;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * World Model 业务服务 — 将 Entity 转为 Map 返回给 Controller
 * <p>
 * 遵循 AgentConfigService / OntologyService 模式，提供目标/场景/因果链 CRUD 业务逻辑。
 * </p>
 */
@Service
public class WorldModelService {

    private static final Logger log = LoggerFactory.getLogger(WorldModelService.class);

    private final WorldModelRepository repository;
    private final ObjectMapper objectMapper;

    public WorldModelService(WorldModelRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════
    //  目标 (Goal)
    // ═══════════════════════════════════════════════════════

    public List<Map<String, Object>> listGoals() {
        return repository.findAllGoals().stream()
            .map(this::goalToMap)
            .toList();
    }

    public List<Map<String, Object>> goalTree() {
        List<Map<String, Object>> allGoals = listGoals();
        Map<Object, List<Map<String, Object>>> childrenMap = new LinkedHashMap<>();
        for (Map<String, Object> g : allGoals) {
            Object parentId = g.get("parentId");
            childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(new LinkedHashMap<>(g));
        }
        // Recursively attach children to build full hierarchy
        List<Map<String, Object>> roots = childrenMap.getOrDefault(null, List.of());
        for (Map<String, Object> root : roots) {
            attachChildrenRecursive(root, childrenMap);
        }
        return roots;
    }

    private void attachChildrenRecursive(Map<String, Object> node,
                                          Map<Object, List<Map<String, Object>>> childrenMap) {
        Object nodeId = node.get("id");
        List<Map<String, Object>> children = childrenMap.getOrDefault(nodeId, List.of());
        if (!children.isEmpty()) {
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (Map<String, Object> child : children) {
                Map<String, Object> childCopy = new LinkedHashMap<>(child);
                attachChildrenRecursive(childCopy, childrenMap);
                childNodes.add(childCopy);
            }
            node.put("children", childNodes);
        }
    }

    public Optional<Map<String, Object>> getGoal(Long id) {
        return repository.findGoalById(id).map(this::goalToMap);
    }

    public Map<String, Object> createGoal(Map<String, Object> body) {
        GoalEntity entity = new GoalEntity();
        entity.setName(String.valueOf(body.getOrDefault("name", "新目标")));
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        Object parentIdObj = body.get("parentId");
        if (parentIdObj != null) {
            if (parentIdObj instanceof Number n) {
                entity.setParentId(n.longValue());
            } else {
                try { entity.setParentId(Long.valueOf(String.valueOf(parentIdObj))); }
                catch (NumberFormatException ignored) {}
            }
        }
        entity.setProgress(body.containsKey("progress")
            ? Integer.parseInt(String.valueOf(body.get("progress"))) : 0);
        entity.setStatus(String.valueOf(body.getOrDefault("status", "PLANNED")));
        entity.setGoalType(String.valueOf(body.getOrDefault("goalType", "STRATEGIC")));
        Object weightObj = body.get("weight");
        if (weightObj instanceof Number n) {
            entity.setWeight(n.intValue());
        } else if (weightObj != null) {
            try { entity.setWeight(Integer.valueOf(String.valueOf(weightObj))); }
            catch (NumberFormatException ignored) {}
        }
        entity.setOrgId(body.containsKey("orgId") ? String.valueOf(body.get("orgId")) : null);
        entity.setOwnerUserId(body.containsKey("ownerUserId") ? String.valueOf(body.get("ownerUserId")) : null);
        Object startDateObj = body.get("startDate");
        if (startDateObj != null) {
            try { entity.setStartDate(java.time.LocalDate.parse(String.valueOf(startDateObj))); }
            catch (Exception ignored) {}
        }
        Object endDateObj = body.get("endDate");
        if (endDateObj != null) {
            try { entity.setEndDate(java.time.LocalDate.parse(String.valueOf(endDateObj))); }
            catch (Exception ignored) {}
        }
        Object targetObj = body.get("targetValue");
        if (targetObj instanceof Number n) {
            entity.setTargetValue(java.math.BigDecimal.valueOf(n.doubleValue()));
        } else if (targetObj != null) {
            try { entity.setTargetValue(new java.math.BigDecimal(String.valueOf(targetObj))); }
            catch (NumberFormatException ignored) {}
        }
        Object currentObj = body.get("currentValue");
        if (currentObj instanceof Number n) {
            entity.setCurrentValue(java.math.BigDecimal.valueOf(n.doubleValue()));
        } else if (currentObj != null) {
            try { entity.setCurrentValue(new java.math.BigDecimal(String.valueOf(currentObj))); }
            catch (NumberFormatException ignored) {}
        }
        entity.setUnit(body.containsKey("unit") ? String.valueOf(body.get("unit")) : null);
        entity.setLinkedWorkflowId(body.containsKey("linkedWorkflowId") ? String.valueOf(body.get("linkedWorkflowId")) : null);
        entity.setKpiFormula(body.containsKey("kpiFormula") ? String.valueOf(body.get("kpiFormula")) : "currentValue/targetValue*100");
        entity.setMeasureFrequency(body.containsKey("measureFrequency") ? String.valueOf(body.get("measureFrequency")) : "MONTHLY");
        Object warnObj = body.get("alertThresholdWarn");
        if (warnObj instanceof Number n) entity.setAlertThresholdWarn(java.math.BigDecimal.valueOf(n.doubleValue()));
        else entity.setAlertThresholdWarn(java.math.BigDecimal.valueOf(80));
        Object critObj = body.get("alertThresholdCritical");
        if (critObj instanceof Number n) entity.setAlertThresholdCritical(java.math.BigDecimal.valueOf(n.doubleValue()));
        else entity.setAlertThresholdCritical(java.math.BigDecimal.valueOf(50));
        repository.insertGoal(entity);
        log.info("Goal created: id={}, name={}", entity.getId(), entity.getName());
        return goalToMap(entity);
    }

    public Optional<Map<String, Object>> updateGoal(Long id, Map<String, Object> body) {
        Optional<GoalEntity> existing = repository.findGoalById(id);
        if (existing.isEmpty()) return Optional.empty();

        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
        Long parentId = null;
        if (body.containsKey("parentId")) {
            Object pid = body.get("parentId");
            if (pid instanceof Number n) {
                parentId = n.longValue();
            } else if (pid != null) {
                try { parentId = Long.valueOf(String.valueOf(pid)); }
                catch (NumberFormatException ignored) {}
            }
        }
        Integer progress = body.containsKey("progress")
            ? Integer.parseInt(String.valueOf(body.get("progress"))) : null;
        String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;
        String goalType = body.containsKey("goalType") ? String.valueOf(body.get("goalType")) : null;
        Integer weight = body.containsKey("weight")
            ? Integer.parseInt(String.valueOf(body.get("weight"))) : null;
        String orgId = body.containsKey("orgId") ? String.valueOf(body.get("orgId")) : null;
        String ownerUserId = body.containsKey("ownerUserId") ? String.valueOf(body.get("ownerUserId")) : null;
        java.time.LocalDate startDate = null;
        if (body.containsKey("startDate") && body.get("startDate") != null) {
            try { startDate = java.time.LocalDate.parse(String.valueOf(body.get("startDate"))); }
            catch (Exception ignored) {}
        }
        java.time.LocalDate endDate = null;
        if (body.containsKey("endDate") && body.get("endDate") != null) {
            try { endDate = java.time.LocalDate.parse(String.valueOf(body.get("endDate"))); }
            catch (Exception ignored) {}
        }
        java.math.BigDecimal targetValue = null;
        if (body.containsKey("targetValue") && body.get("targetValue") != null) {
            Object tv = body.get("targetValue");
            if (tv instanceof Number n) targetValue = java.math.BigDecimal.valueOf(n.doubleValue());
            else try { targetValue = new java.math.BigDecimal(String.valueOf(tv)); } catch (NumberFormatException ignored) {}
        }
        java.math.BigDecimal currentValue = null;
        if (body.containsKey("currentValue") && body.get("currentValue") != null) {
            Object cv = body.get("currentValue");
            if (cv instanceof Number n) currentValue = java.math.BigDecimal.valueOf(n.doubleValue());
            else try { currentValue = new java.math.BigDecimal(String.valueOf(cv)); } catch (NumberFormatException ignored) {}
        }
        String unit = body.containsKey("unit") ? String.valueOf(body.get("unit")) : null;
        String linkedWorkflowId = body.containsKey("linkedWorkflowId") ? String.valueOf(body.get("linkedWorkflowId")) : null;
        String kpiFormula = body.containsKey("kpiFormula") ? String.valueOf(body.get("kpiFormula")) : null;
        String measureFrequency = body.containsKey("measureFrequency") ? String.valueOf(body.get("measureFrequency")) : null;
        java.math.BigDecimal alertThresholdWarn = null;
        if (body.containsKey("alertThresholdWarn") && body.get("alertThresholdWarn") != null) {
            Object aw = body.get("alertThresholdWarn");
            if (aw instanceof Number n) alertThresholdWarn = java.math.BigDecimal.valueOf(n.doubleValue());
            else try { alertThresholdWarn = new java.math.BigDecimal(String.valueOf(aw)); } catch (NumberFormatException ignored) {}
        }
        java.math.BigDecimal alertThresholdCritical = null;
        if (body.containsKey("alertThresholdCritical") && body.get("alertThresholdCritical") != null) {
            Object ac = body.get("alertThresholdCritical");
            if (ac instanceof Number n) alertThresholdCritical = java.math.BigDecimal.valueOf(n.doubleValue());
            else try { alertThresholdCritical = new java.math.BigDecimal(String.valueOf(ac)); } catch (NumberFormatException ignored) {}
        }

        repository.updateGoal(id, name, description, parentId, progress, status,
            goalType, weight, orgId, ownerUserId,
            startDate, endDate, targetValue, currentValue, unit, linkedWorkflowId,
            kpiFormula, measureFrequency, alertThresholdWarn, alertThresholdCritical);
        return repository.findGoalById(id).map(this::goalToMap);
    }

    public boolean deleteGoal(Long id) {
        // Cascade: 业务指标/目标/项目 + 子目标脱钩 + 因果链(CASCADE自动)
        repository.deleteBizMetricsByGoalId(id);
        repository.deleteBizTargetsByGoalId(id);
        repository.deleteBizProjectsByGoalId(id);
        repository.orphanChildren(id);
        return repository.deleteGoalById(id) > 0;
    }

    public long countGoals() {
        return repository.countGoals();
    }

    /** 自动生成编码: G-{yyyy}{MM}{dd}-{序号} */
    public String nextGoalCode() {
        String prefix = java.time.LocalDate.now().toString().replace("-", "");
        long count = repository.countGoals();
        return "G-" + prefix + "-" + String.format("%03d", count + 1);
    }

    // ═══════════════════════════════════════════════════════
    //  场景 (Scenario)
    // ═══════════════════════════════════════════════════════

    public List<Map<String, Object>> listScenarios() {
        return repository.findAllScenarios().stream()
            .map(this::scenarioToMap)
            .toList();
    }

    public Optional<Map<String, Object>> getScenario(Long id) {
        return repository.findScenarioById(id).map(this::scenarioToMap);
    }

    public Map<String, Object> createScenario(Map<String, Object> body) {
        ScenarioEntity entity = new ScenarioEntity();
        entity.setName(String.valueOf(body.getOrDefault("name", "新场景")));
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        // Store extra fields (score, cost, risk, etc.) in config_json
        String configJson = serializeConfig(body);
        entity.setConfigJson(configJson);
        entity.setStatus(String.valueOf(body.getOrDefault("status", "DRAFT")));
        repository.insertScenario(entity);
        log.info("Scenario created: id={}, name={}", entity.getId(), entity.getName());
        return scenarioToMap(entity);
    }

    public Optional<Map<String, Object>> updateScenario(Long id, Map<String, Object> body) {
        Optional<ScenarioEntity> existing = repository.findScenarioById(id);
        if (existing.isEmpty()) return Optional.empty();

        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
        String configJson = null;
        if (body.containsKey("score") || body.containsKey("cost") || body.containsKey("risk")
            || body.containsKey("configJson")) {
            // Merge with existing config
            Map<String, Object> merged = parseConfig(existing.get().getConfigJson());
            if (body.containsKey("score")) merged.put("score", body.get("score"));
            if (body.containsKey("cost")) merged.put("cost", body.get("cost"));
            if (body.containsKey("risk")) merged.put("risk", String.valueOf(body.get("risk")));
            if (body.containsKey("configJson")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> incoming = (Map<String, Object>) body.get("configJson");
                merged.putAll(incoming);
            }
            configJson = toJsonString(merged);
        }
        String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;

        repository.updateScenario(id, name, description, configJson, status);
        return repository.findScenarioById(id).map(this::scenarioToMap);
    }

    public boolean deleteScenario(Long id) {
        return repository.deleteScenarioById(id) > 0;
    }

    public long countScenarios() {
        return repository.countScenarios();
    }

    /**
     * 场景对比 — 返回按 score 降序排列的场景列表
     */
    public List<Map<String, Object>> compareScenarios(List<Long> scenarioIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : scenarioIds) {
            repository.findScenarioById(id)
                .map(this::scenarioToMap)
                .ifPresent(result::add);
        }
        result.sort((a, b) -> {
            double scoreA = extractScore(a);
            double scoreB = extractScore(b);
            return Double.compare(scoreB, scoreA);
        });
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  因果链 (Causal Link)
    // ═══════════════════════════════════════════════════════

    public List<Map<String, Object>> listCausalLinks() {
        return repository.findAllCausalLinks().stream()
            .map(this::linkToMap)
            .toList();
    }

    public Optional<Map<String, Object>> getCausalLink(Long id) {
        return repository.findCausalLinkById(id).map(this::linkToMap);
    }

    public Map<String, Object> createCausalLink(Map<String, Object> body) {
        CausalLinkEntity entity = new CausalLinkEntity();
        Object srcObj = body.get("sourceGoalId");
        if (srcObj instanceof Number n) {
            entity.setSourceGoalId(n.longValue());
        } else {
            entity.setSourceGoalId(Long.valueOf(String.valueOf(srcObj)));
        }
        Object tgtObj = body.get("targetGoalId");
        if (tgtObj instanceof Number n) {
            entity.setTargetGoalId(n.longValue());
        } else {
            entity.setTargetGoalId(Long.valueOf(String.valueOf(tgtObj)));
        }
        entity.setRelationshipType(String.valueOf(body.getOrDefault("relationshipType", "POSITIVE")));
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        repository.insertCausalLink(entity);
        log.info("CausalLink created: id={}", entity.getId());
        return linkToMap(entity);
    }

    public boolean deleteCausalLink(Long id) {
        return repository.deleteCausalLinkById(id) > 0;
    }

    public Optional<Map<String, Object>> updateCausalLink(Long id, Map<String, Object> body) {
        Optional<CausalLinkEntity> existing = repository.findCausalLinkById(id);
        if (existing.isEmpty()) return Optional.empty();

        Long sourceGoalId = null;
        if (body.containsKey("sourceGoalId")) {
            Object s = body.get("sourceGoalId");
            sourceGoalId = s instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(s));
        }
        Long targetGoalId = null;
        if (body.containsKey("targetGoalId")) {
            Object t = body.get("targetGoalId");
            targetGoalId = t instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(t));
        }
        String relationshipType = body.containsKey("relationType")
            ? String.valueOf(body.get("relationType")) : null;
        String description = body.containsKey("description")
            ? String.valueOf(body.get("description")) : null;

        repository.updateCausalLink(id, sourceGoalId, targetGoalId, relationshipType, description);
        return repository.findCausalLinkById(id).map(this::linkToMap);
    }

    /**
     * 因果图 — 返回 {nodes: [...], edges: [...]}
     */
    public Map<String, Object> causalGraph() {
        List<CausalLinkEntity> links = repository.findAllCausalLinks();
        // Gather goal names for node labels
        Map<Long, String> goalNames = repository.findAllGoals().stream()
            .collect(Collectors.toMap(GoalEntity::getId, GoalEntity::getName));

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CausalLinkEntity link : links) {
            Long src = link.getSourceGoalId();
            Long tgt = link.getTargetGoalId();
            if (seen.add(src)) {
                nodes.add(Map.of("id", src, "label", goalNames.getOrDefault(src, "Goal " + src), "type", "cause"));
            }
            if (seen.add(tgt)) {
                nodes.add(Map.of("id", tgt, "label", goalNames.getOrDefault(tgt, "Goal " + tgt), "type", "effect"));
            }
        }

        List<Map<String, Object>> edges = links.stream()
            .map(this::linkToMap)
            .toList();

        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return graph;
    }

    // ═══════════════════════════════════════════════════════
    //  内部映射方法
    // ═══════════════════════════════════════════════════════

    private Map<String, Object> goalToMap(GoalEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("parentId", entity.getParentId());
        map.put("progress", entity.getProgress());
        map.put("status", entity.getStatus());
        map.put("goalType", entity.getGoalType());
        map.put("weight", entity.getWeight());
        map.put("orgId", entity.getOrgId());
        map.put("ownerUserId", entity.getOwnerUserId());
        map.put("startDate", entity.getStartDate() != null ? entity.getStartDate().toString() : null);
        map.put("endDate", entity.getEndDate() != null ? entity.getEndDate().toString() : null);
        map.put("targetValue", entity.getTargetValue());
        map.put("currentValue", entity.getCurrentValue());
        map.put("unit", entity.getUnit());
        map.put("linkedWorkflowId", entity.getLinkedWorkflowId());
        map.put("kpiFormula", entity.getKpiFormula());
        map.put("measureFrequency", entity.getMeasureFrequency());
        map.put("alertThresholdWarn", entity.getAlertThresholdWarn());
        map.put("alertThresholdCritical", entity.getAlertThresholdCritical());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> scenarioToMap(ScenarioEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("status", entity.getStatus());
        // Merge config_json fields into the map for backward API compatibility
        if (entity.getConfigJson() != null && !entity.getConfigJson().isBlank()) {
            try {
                Map<String, Object> config = objectMapper.readValue(entity.getConfigJson(), Map.class);
                map.putAll(config);
            } catch (Exception e) {
                log.warn("Failed to parse config_json for scenario {}: {}", entity.getId(), e.getMessage());
            }
        }
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> linkToMap(CausalLinkEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("sourceGoalId", entity.getSourceGoalId());
        map.put("targetGoalId", entity.getTargetGoalId());
        map.put("relationshipType", entity.getRelationshipType());
        map.put("description", entity.getDescription());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse config JSON: {}", json, e);
            return new LinkedHashMap<>();
        }
    }

    private String serializeConfig(Map<String, Object> body) {
        Map<String, Object> config = new LinkedHashMap<>();
        for (String key : List.of("score", "cost", "risk")) {
            if (body.containsKey(key)) {
                config.put(key, body.get(key));
            }
        }
        return config.isEmpty() ? "{}" : toJsonString(config);
    }

    private double extractScore(Map<String, Object> scenarioMap) {
        Object score = scenarioMap.get("score");
        if (score instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", obj, e);
            return "{}";
        }
    }
}
