package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.controller.model.CausalLinkEntity;
import com.chinacreator.gzcm.sysman.controller.model.GoalEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * World Model — 目标与因果链控制器。
 * <p>
 * Phase 2: 优先从 PostgreSQL ecos_wm_goal / ecos_wm_causal_link 读取真实数据，
 * 数据库不可用时回退到内存 ConcurrentHashMap (MVP demo)。
 * </p>
 * <pre>
 * GET    /api/v1/ecos/world-model-graph/goals          — 目标列表
 * POST   /api/v1/ecos/world-model-graph/goals          — 创建目标
 * PUT    /api/v1/ecos/world-model-graph/goals/{id}     — 更新目标
 * DELETE /api/v1/ecos/world-model-graph/goals/{id}     — 删除目标
 *
 * GET    /api/v1/ecos/world-model-graph/links          — 因果链列表
 * PUT    /api/v1/ecos/world-model-graph/goals/{id}/links — 添加因果链
 * DELETE /api/v1/ecos/world-model-graph/links/{id}     — 删除因果链
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/world-model-graph")
public class WorldModelGraphController {

    private static final Logger log = LoggerFactory.getLogger(WorldModelGraphController.class);

    /** 内存存储 (fallback) */
    private final ConcurrentHashMap<String, GoalEntity> goalStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CausalLinkEntity> linkStore = new ConcurrentHashMap<>();

    /** JdbcTemplate — 构造函数注入 (optional, falls back to memory) */
    private final JdbcTemplate jdbc;

    public WorldModelGraphController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initFallbackData();
    }

    /** 数据库是否可用 */
    private boolean dbAvailable() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 初始化内存兜底数据 */
    private void initFallbackData() {
        GoalEntity rootGoal = new GoalEntity();
        rootGoal.setId("root-goal");
        rootGoal.setName("系统总体目标");
        rootGoal.setDescription("ECOS 平台总体业务目标");
        rootGoal.setProgress(0);
        rootGoal.setStatus("PLANNED");
        rootGoal.setCreatedAt(System.currentTimeMillis());
        rootGoal.setUpdatedAt(System.currentTimeMillis());
        goalStore.put("root-goal", rootGoal);

        GoalEntity childGoal = new GoalEntity();
        childGoal.setId("child-goal");
        childGoal.setName("提升数据质量");
        childGoal.setDescription("通过数据治理提升整体数据质量水平");
        childGoal.setParentId("root-goal");
        childGoal.setProgress(0);
        childGoal.setStatus("PLANNED");
        childGoal.setCreatedAt(System.currentTimeMillis());
        childGoal.setUpdatedAt(System.currentTimeMillis());
        goalStore.put("child-goal", childGoal);

        log.info("WorldModelGraphController 初始化完成，已加载 {} 个默认目标 (fallback)", goalStore.size());
    }

    // ═══════════════════════════════════════════════════════
    //  目标 CRUD
    // ═══════════════════════════════════════════════════════

    /** 从 DB 读取目标列表，DB 不可用时回退内存 */
    @GetMapping("/goals")
    public ApiResponse<Map<String, Object>> listGoals() {
        try {
            List<Map<String, Object>> data;
            if (dbAvailable()) {
                data = queryGoalsFromDB();
            } else {
                data = goalStore.values().stream()
                    .map(this::goalToMap)
                    .collect(Collectors.toList());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            result.put("total", data.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询目标列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/goals/tree")
    public ApiResponse<List<Map<String, Object>>> goalTree() {
        try {
            List<Map<String, Object>> all;
            if (dbAvailable()) {
                all = queryGoalsFromDB();
            } else {
                all = goalStore.values().stream()
                    .map(this::goalToMap)
                    .collect(Collectors.toList());
            }
            // 构建树形结构
            Map<Object, List<Map<String, Object>>> childrenMap = new LinkedHashMap<>();
            for (Map<String, Object> g : all) {
                Object parentId = g.get("parentId");
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(g);
            }
            List<Map<String, Object>> roots = childrenMap.getOrDefault(null, List.of());
            for (Map<String, Object> root : roots) {
                attachChildren(root, childrenMap);
            }
            return ApiResponse.success(roots);
        } catch (Exception e) {
            log.error("查询目标树失败", e);
            return ApiResponse.internalError("查询目标树失败: " + e.getMessage());
        }
    }

    private void attachChildren(Map<String, Object> node, Map<Object, List<Map<String, Object>>> childrenMap) {
        Object nodeId = node.get("id");
        List<Map<String, Object>> children = childrenMap.get(nodeId);
        if (children != null) {
            node.put("children", new ArrayList<>(children));
            for (Map<String, Object> child : children) {
                attachChildren(child, childrenMap);
            }
        }
    }

    private List<Map<String, Object>> queryGoalsFromDB() {
        String sql = """
            SELECT id, name, description, parent_id, progress, status,
                   goal_type, weight, org_id, owner_user_id,
                   start_date, end_date, target_value, current_value, unit,
                   linked_workflow_id, kpi_formula, measure_frequency,
                   alert_threshold_warn, alert_threshold_critical,
                   created_at, updated_at
            FROM ecos_wm_goal
            ORDER BY id
            """;
        return jdbc.query(sql, (rs, rowNum) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            long pid = rs.getLong("parent_id");
            m.put("parentId", rs.wasNull() ? null : pid);
            m.put("progress", rs.getInt("progress"));
            m.put("status", rs.getString("status"));
            m.put("goalType", rs.getString("goal_type"));
            m.put("weight", rs.getObject("weight"));
            m.put("orgId", rs.getString("org_id"));
            m.put("ownerUserId", rs.getString("owner_user_id"));
            m.put("startDate", rs.getDate("start_date"));
            m.put("endDate", rs.getDate("end_date"));
            m.put("targetValue", rs.getBigDecimal("target_value"));
            m.put("currentValue", rs.getBigDecimal("current_value"));
            m.put("unit", rs.getString("unit"));
            m.put("linkedWorkflowId", rs.getString("linked_workflow_id"));
            m.put("kpiFormula", rs.getString("kpi_formula"));
            m.put("measureFrequency", rs.getString("measure_frequency"));
            m.put("alertThresholdWarn", rs.getBigDecimal("alert_threshold_warn"));
            m.put("alertThresholdCritical", rs.getBigDecimal("alert_threshold_critical"));
            Timestamp ca = rs.getTimestamp("created_at");
            m.put("createdAt", ca != null ? ca.toLocalDateTime().toString() : null);
            Timestamp ua = rs.getTimestamp("updated_at");
            m.put("updatedAt", ua != null ? ua.toLocalDateTime().toString() : null);
            return m;
        });
    }

    private Map<String, Object> goalToMap(GoalEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("name", g.getName());
        m.put("description", g.getDescription());
        m.put("parentId", g.getParentId());
        m.put("progress", g.getProgress());
        m.put("status", g.getStatus());
        m.put("goalType", g.getGoalType());
        m.put("weight", g.getWeight());
        m.put("orgId", g.getOrgId());
        m.put("ownerUserId", g.getOwnerUserId());
        m.put("startDate", g.getStartDate());
        m.put("endDate", g.getEndDate());
        m.put("targetValue", g.getTargetValue());
        m.put("currentValue", g.getCurrentValue());
        m.put("unit", g.getUnit());
        m.put("linkedWorkflowId", g.getLinkedWorkflowId());
        m.put("kpiFormula", g.getKpiFormula());
        m.put("measureFrequency", g.getMeasureFrequency());
        m.put("alertThresholdWarn", g.getAlertThresholdWarn());
        m.put("alertThresholdCritical", g.getAlertThresholdCritical());
        m.put("createdAt", g.getCreatedAt());
        m.put("updatedAt", g.getUpdatedAt());
        return m;
    }

    @GetMapping("/goals/{id}")
    public ApiResponse<?> getGoal(@PathVariable String id) {
        try {
            GoalEntity goal = goalStore.get(id);
            if (goal == null) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            return ApiResponse.success(goal);
        } catch (Exception e) {
            log.error("查询目标失败, id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/goals")
    public ApiResponse<?> createGoal(@RequestBody GoalEntity goal) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            goal.setId(id);
            goal.setCreatedAt(System.currentTimeMillis());
            goal.setUpdatedAt(System.currentTimeMillis());
            if (goal.getName() == null || goal.getName().isBlank()) {
                goal.setName("目标 " + id.substring(0, 8));
            }
            if (goal.getStatus() == null || goal.getStatus().isBlank()) {
                goal.setStatus("PLANNED");
            }
            if (goal.getProgress() == null) {
                goal.setProgress(0);
            }
            if (goal.getParentId() != null && !goal.getParentId().isBlank()) {
                if (!goalStore.containsKey(goal.getParentId())) {
                    return ApiResponse.badRequest("父目标不存在: " + goal.getParentId());
                }
            }
            goalStore.put(id, goal);
            log.info("目标创建成功, id={}, name={}", id, goal.getName());
            return ApiResponse.success(goal);
        } catch (Exception e) {
            log.error("创建目标失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/goals/{id}")
    public ApiResponse<?> updateGoal(@PathVariable String id, @RequestBody GoalEntity goal) {
        try {
            GoalEntity existing = goalStore.get(id);
            if (existing == null) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            goal.setId(id);
            goal.setCreatedAt(existing.getCreatedAt());
            goal.setUpdatedAt(System.currentTimeMillis());
            if (goal.getName() == null) goal.setName(existing.getName());
            if (goal.getDescription() == null) goal.setDescription(existing.getDescription());
            if (goal.getParentId() == null) goal.setParentId(existing.getParentId());
            if (goal.getProgress() == null) goal.setProgress(existing.getProgress());
            if (goal.getStatus() == null) goal.setStatus(existing.getStatus());
            goalStore.put(id, goal);
            log.info("目标更新成功, id={}, name={}", id, goal.getName());
            return ApiResponse.success(goal);
        } catch (Exception e) {
            log.error("更新目标失败, id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/goals/{id}")
    public ApiResponse<?> deleteGoal(@PathVariable String id) {
        try {
            GoalEntity removed = goalStore.remove(id);
            if (removed == null) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            List<String> linksToRemove = linkStore.values().stream()
                    .filter(l -> id.equals(l.getSourceGoalId()) || id.equals(l.getTargetGoalId()))
                    .map(CausalLinkEntity::getId)
                    .collect(Collectors.toList());
            linksToRemove.forEach(linkStore::remove);
            log.info("目标删除成功, id={}, name={}, 级联删除 {} 条因果链",
                    id, removed.getName(), linksToRemove.size());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            result.put("cascadedLinksRemoved", linksToRemove.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除目标失败, id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  因果链管理
    // ═══════════════════════════════════════════════════════

    @GetMapping("/links")
    public ApiResponse<Map<String, Object>> listLinks() {
        try {
            List<CausalLinkEntity> all = new ArrayList<>(linkStore.values());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", all);
            result.put("total", all.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询因果链列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 因果图 — 从 PostgreSQL 构建 nodes + edges
     */
    @GetMapping("/causal-graph")
    public ApiResponse<Map<String, Object>> causalGraph() {
        try {
            List<Map<String, Object>> nodes;
            List<Map<String, Object>> edges;
            if (dbAvailable()) {
                nodes = jdbc.query(
                    "SELECT id, name, description, status, goal_type FROM ecos_wm_goal ORDER BY id",
                    (rs, rowNum) -> {
                        Map<String, Object> n = new LinkedHashMap<>();
                        n.put("id", "goal-" + rs.getLong("id"));
                        n.put("label", rs.getString("name"));
                        n.put("description", rs.getString("description"));
                        n.put("status", rs.getString("status"));
                        n.put("type", rs.getString("goal_type"));
                        return n;
                    });
                edges = jdbc.query(
                    "SELECT id, source_goal_id, target_goal_id, relationship_type, description FROM ecos_wm_causal_link",
                    (rs, rowNum) -> {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("id", "edge-" + rs.getLong("id"));
                        e.put("source", "goal-" + rs.getLong("source_goal_id"));
                        e.put("target", "goal-" + rs.getLong("target_goal_id"));
                        e.put("label", rs.getString("relationship_type"));
                        e.put("description", rs.getString("description"));
                        return e;
                    });
            } else {
                nodes = goalStore.values().stream().map(g -> {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("id", g.getId());
                    n.put("label", g.getName());
                    n.put("description", g.getDescription());
                    n.put("status", g.getStatus());
                    return n;
                }).collect(Collectors.toList());
                edges = linkStore.values().stream().map(l -> {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("id", l.getId());
                    e.put("source", l.getSourceGoalId());
                    e.put("target", l.getTargetGoalId());
                    e.put("label", l.getLabel());
                    return e;
                }).collect(Collectors.toList());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodes", nodes);
            result.put("edges", edges);
            result.put("nodeCount", nodes.size());
            result.put("edgeCount", edges.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("构建因果图失败", e);
            return ApiResponse.internalError("构建因果图失败: " + e.getMessage());
        }
    }

    @GetMapping("/links/{id}")
    public ApiResponse<?> getLink(@PathVariable String id) {
        try {
            CausalLinkEntity link = linkStore.get(id);
            if (link == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            return ApiResponse.success(link);
        } catch (Exception e) {
            log.error("查询因果链失败, id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/goals/{id}/links")
    public ApiResponse<?> addCausalLink(@PathVariable String id, @RequestBody CausalLinkEntity link) {
        try {
            String srcId = link.getSourceGoalId() != null && !link.getSourceGoalId().isBlank()
                    ? link.getSourceGoalId() : id;
            String tgtId = link.getTargetGoalId();
            if (tgtId == null || tgtId.isBlank()) {
                return ApiResponse.badRequest("targetGoalId 不能为空");
            }
            if (!goalStore.containsKey(srcId)) {
                return ApiResponse.badRequest("源目标不存在: " + srcId);
            }
            if (!goalStore.containsKey(tgtId)) {
                return ApiResponse.badRequest("目标目标不存在: " + tgtId);
            }
            String linkId = UUID.randomUUID().toString().replace("-", "");
            link.setId(linkId);
            link.setSourceGoalId(srcId);
            link.setCreatedAt(System.currentTimeMillis());
            if (link.getStrength() == null) {
                link.setStrength(1.0);
            }
            if (link.getStrength() < 0.0 || link.getStrength() > 1.0) {
                return ApiResponse.badRequest("strength 必须在 0.0 ~ 1.0 之间");
            }
            if (link.getLabel() == null || link.getLabel().isBlank()) {
                link.setLabel("causal");
            }
            linkStore.put(linkId, link);
            log.info("因果链创建成功, id={}, {} → {}, label={}",
                    linkId, srcId, tgtId, link.getLabel());
            return ApiResponse.success(link);
        } catch (Exception e) {
            log.error("创建因果链失败, sourceId={}", id, e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/links/{id}")
    public ApiResponse<?> deleteLink(@PathVariable String id) {
        try {
            CausalLinkEntity removed = linkStore.remove(id);
            if (removed == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            log.info("因果链删除成功, id={}, {} → {}",
                    id, removed.getSourceGoalId(), removed.getTargetGoalId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除因果链失败, id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }
}
