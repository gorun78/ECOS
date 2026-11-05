package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.controller.model.CausalLinkEntity;
import com.chinacreator.gzcm.sysman.controller.model.GoalEntity;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelCausalEdgeVO;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelCausalGraphVO;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelCausalNodeVO;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelDeleteGoalVO;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelDeleteLinkVO;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelGoalVO;
import com.chinacreator.gzcm.sysman.controller.model.WorldModelListVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.chinacreator.gzcm.sysman.service.WorldModelGraphQueryService;

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

        private final WorldModelGraphQueryService worldModelGraphQueryService;
private static final Logger log = LoggerFactory.getLogger(WorldModelGraphController.class);

    /** 内存存储 (fallback) */
    private final ConcurrentHashMap<String, GoalEntity> goalStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CausalLinkEntity> linkStore = new ConcurrentHashMap<>();

    /** WorldModelGraphQueryService — 构造函数注入 (optional, falls back to memory) */

    public WorldModelGraphController(WorldModelGraphQueryService worldModelGraphQueryService) {
        this.worldModelGraphQueryService = worldModelGraphQueryService;
        initFallbackData();
    }

    /** 数据库是否可用 */
    private boolean dbAvailable() {
        try {
            worldModelGraphQueryService.ping();
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
    public ApiResponse<WorldModelListVO<WorldModelGoalVO>> listGoals() {
        try {
            List<WorldModelGoalVO> data = loadGoals();
            return ApiResponse.success(new WorldModelListVO<>(data, data.size()));
        } catch (Exception e) {
            log.error("查询目标列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/goals/tree")
    public ApiResponse<List<WorldModelGoalVO>> goalTree() {
        try {
            List<WorldModelGoalVO> all = loadGoals();
            // 构建树形结构（父键与 id 均为两源原始值，Long / String 各自相等即挂接）
            Map<Object, List<WorldModelGoalVO>> childrenMap = new LinkedHashMap<>();
            for (WorldModelGoalVO g : all) {
                childrenMap.computeIfAbsent(g.parentId(), k -> new ArrayList<>()).add(g);
            }
            List<WorldModelGoalVO> roots = new ArrayList<>();
            for (WorldModelGoalVO root : childrenMap.getOrDefault(null, List.of())) {
                roots.add(attachChildren(root, childrenMap));
            }
            return ApiResponse.success(roots);
        } catch (Exception e) {
            log.error("查询目标树失败", e);
            return ApiResponse.internalError("查询目标树失败");
        }
    }

    private WorldModelGoalVO attachChildren(WorldModelGoalVO node,
                                            Map<Object, List<WorldModelGoalVO>> childrenMap) {
        List<WorldModelGoalVO> children = childrenMap.get(node.id());
        if (children == null || children.isEmpty()) {
            return node;
        }
        List<WorldModelGoalVO> attached = new ArrayList<>(children.size());
        for (WorldModelGoalVO child : children) {
            attached.add(attachChildren(child, childrenMap));
        }
        return node.withChildren(attached);
    }

    /** DB 行 → 强类型目标行（行值为两源原始类型，见 {@link WorldModelGoalVO} 头注）。 */
    private List<WorldModelGoalVO> queryGoalsAsVo() {
        return worldModelGraphQueryService.listGoals().stream()
                .map(WorldModelGoalVO::ofRow)
                .collect(Collectors.toList());
    }

    private List<WorldModelGoalVO> loadGoals() {
        if (dbAvailable()) {
            return queryGoalsAsVo();
        }
        return goalStore.values().stream()
                .map(WorldModelGoalVO::ofEntity)
                .collect(Collectors.toList());
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
            return ApiResponse.internalError("查询失败");
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
            return ApiResponse.internalError("创建失败");
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
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/goals/{id}")
    public ApiResponse<WorldModelDeleteGoalVO> deleteGoal(@PathVariable String id) {
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
            return ApiResponse.success(new WorldModelDeleteGoalVO(true, id, linksToRemove.size()));
        } catch (Exception e) {
            log.error("删除目标失败, id={}", id, e);
            return ApiResponse.internalError("删除失败");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  因果链管理
    // ═══════════════════════════════════════════════════════

    @GetMapping("/links")
    public ApiResponse<WorldModelListVO<CausalLinkEntity>> listLinks() {
        try {
            List<CausalLinkEntity> all = new ArrayList<>(linkStore.values());
            return ApiResponse.success(new WorldModelListVO<>(all, all.size()));
        } catch (Exception e) {
            log.error("查询因果链列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    /**
     * 因果图 — 从 PostgreSQL 构建 nodes + edges
     */
    @GetMapping("/causal-graph")
    public ApiResponse<WorldModelCausalGraphVO> causalGraph() {
        try {
            List<WorldModelCausalNodeVO> nodes;
            List<WorldModelCausalEdgeVO> edges;
            if (dbAvailable()) {
                nodes = worldModelGraphQueryService.listCausalGraphNodes().stream()
                        .map(WorldModelCausalNodeVO::ofRow)
                        .collect(Collectors.toList());
                edges = worldModelGraphQueryService.listCausalGraphEdges().stream()
                        .map(WorldModelCausalEdgeVO::ofRow)
                        .collect(Collectors.toList());
            } else {
                nodes = goalStore.values().stream()
                        .map(g -> new WorldModelCausalNodeVO(
                                g.getId(), g.getName(), g.getDescription(), g.getStatus(), null))
                        .collect(Collectors.toList());
                edges = linkStore.values().stream()
                        .map(l -> new WorldModelCausalEdgeVO(
                                l.getId(), l.getSourceGoalId(), l.getTargetGoalId(), l.getLabel(), null))
                        .collect(Collectors.toList());
            }
            return ApiResponse.success(new WorldModelCausalGraphVO(
                    nodes, edges, nodes.size(), edges.size()));
        } catch (Exception e) {
            log.error("构建因果图失败", e);
            return ApiResponse.internalError("构建因果图失败");
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
            return ApiResponse.internalError("查询失败");
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
            return ApiResponse.internalError("创建失败");
        }
    }

    @DeleteMapping("/links/{id}")
    public ApiResponse<WorldModelDeleteLinkVO> deleteLink(@PathVariable String id) {
        try {
            CausalLinkEntity removed = linkStore.remove(id);
            if (removed == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            log.info("因果链删除成功, id={}, {} → {}",
                    id, removed.getSourceGoalId(), removed.getTargetGoalId());
            return ApiResponse.success(new WorldModelDeleteLinkVO(true, id));
        } catch (Exception e) {
            log.error("删除因果链失败, id={}", id, e);
            return ApiResponse.internalError("删除失败");
        }
    }
}
