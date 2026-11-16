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
import java.util.stream.Collectors;
import com.chinacreator.gzcm.sysman.service.WorldModelGraphQueryService;

/**
 * World Model — 目标与因果链控制器。
 * <p>
 * D103：读写两面都只走 PostgreSQL {@code ecos_wm_goal} / {@code ecos_wm_causal_link}。
 * 旧实现读库、写内存 Map，返回成功但重启即丢；内存兜底已删除，DB 不可用时如实报错。
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

    /**
     * 真实写入通道（D103）：旧实现用两个 ConcurrentHashMap 承接写面，读面却查 PG，
     * 造成「返回成功但没落库」。内存 Map 已删除，写面一律走本 DAO。
     */
    private final com.chinacreator.gzcm.sysman.dao.WorldModelGraphWriteDao writeDao;

    public WorldModelGraphController(WorldModelGraphQueryService worldModelGraphQueryService,
                                     com.chinacreator.gzcm.sysman.dao.WorldModelGraphWriteDao writeDao) {
        this.worldModelGraphQueryService = worldModelGraphQueryService;
        this.writeDao = writeDao;
    }

    // ═══════════════════════════════════════════════════════
    //  目标 CRUD
    // ═══════════════════════════════════════════════════════

    /** 从 DB 读取目标列表；DB 不可用即抛（D103：不再回退内存假数据） */
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
        // D103：去掉内存回退——DB 不可用时应如实报错，不能拿进程内假数据冒充真实目标
        return queryGoalsAsVo();
    }

    @GetMapping("/goals/{id}")
    public ApiResponse<?> getGoal(@PathVariable String id) {
        try {
            Long gid = parseIdOrNull(id);
            if (gid == null) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            com.chinacreator.gzcm.sysman.dao.WorldModelGraphWriteDao.GoalRow row = writeDao.findGoal(gid);
            if (row == null) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            return ApiResponse.success(new GoalDetailVO(String.valueOf(row.id()), row.name(),
                    row.description(), row.parentId() == null ? null : String.valueOf(row.parentId()),
                    row.progress(), row.status(), row.goalType(), row.weight(), row.orgId(),
                    row.ownerUserId(), row.createdAt(), row.updatedAt()));
        } catch (Exception e) {
            log.error("查询目标失败, id={}", id, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping("/goals")
    public ApiResponse<?> createGoal(@RequestBody GoalEntity goal) {
        try {
            if (goal.getName() == null || goal.getName().isBlank()) {
                return ApiResponse.badRequest("name 不能为空");
            }
            Long parentId = parseIdOrNull(goal.getParentId());
            if (goal.getParentId() != null && !goal.getParentId().isBlank()) {
                if (parentId == null || !writeDao.goalExists(parentId)) {
                    return ApiResponse.badRequest("父目标不存在: " + goal.getParentId());
                }
            }
            long id = writeDao.insertGoal(
                    goal.getName(), goal.getDescription(), parentId,
                    goal.getProgress(),
                    goal.getStatus() == null || goal.getStatus().isBlank() ? "PLANNED" : goal.getStatus(),
                    null, null, null, null);
            GoalEntity created = new GoalEntity();
            created.setId(String.valueOf(id));
            created.setName(goal.getName());
            created.setDescription(goal.getDescription());
            created.setParentId(parentId == null ? null : String.valueOf(parentId));
            created.setProgress(goal.getProgress() == null ? 0 : goal.getProgress());
            created.setStatus(goal.getStatus() == null || goal.getStatus().isBlank() ? "PLANNED" : goal.getStatus());
            created.setCreatedAt(System.currentTimeMillis());
            created.setUpdatedAt(System.currentTimeMillis());
            log.info("目标创建成功, id={}, name={}", id, created.getName());
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建目标失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    /** 因果图行键带展示前缀（{@code edge-}/{@code goal-}），对外的 CausalLinkEntity 用裸数字 id。 */
    private static String stripPrefix(Object value, String prefix) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    /** 对外 id 是字符串，入库前严格转 long；非法即 null（交由调用方判 400）。 */
    private static Long parseIdOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PutMapping("/goals/{id}")
    public ApiResponse<?> updateGoal(@PathVariable String id, @RequestBody GoalEntity goal) {
        try {
            Long gid = parseIdOrNull(id);
            if (gid == null || !writeDao.goalExists(gid)) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            Long parentId = parseIdOrNull(goal.getParentId());
            if (parentId != null && parentId.equals(gid)) {
                return ApiResponse.badRequest("父目标不能是自己");
            }
            int n = writeDao.updateGoal(gid, goal.getName(), goal.getDescription(), parentId,
                    goal.getProgress(), goal.getStatus(), null, null);
            if (n == 0) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            log.info("目标更新成功, id={}, name={}", gid, goal.getName());
            return ApiResponse.success(goal);
        } catch (Exception e) {
            log.error("更新目标失败, id={}", id, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/goals/{id}")
    public ApiResponse<WorldModelDeleteGoalVO> deleteGoal(@PathVariable String id) {
        try {
            Long gid = parseIdOrNull(id);
            if (gid == null || !writeDao.goalExists(gid)) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            int links = writeDao.countLinksOfGoal(gid);
            int n = writeDao.deleteGoal(gid);
            if (n == 0) {
                return ApiResponse.notFound("目标不存在: " + id);
            }
            log.info("目标删除成功, id={}, 级联删除 {} 条因果链", gid, links);
            return ApiResponse.success(new WorldModelDeleteGoalVO(true, id, links));
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
            // D103：读面改为与因果图同一数据源（此前从内存 Map 读，恒为空）
            // 因果图边的行键是 source/target（带 "goal-" 前缀），与 CausalLinkEntity 的
            // sourceGoalId/targetGoalId 不同名，这里做一次显式映射，禁假定同名。
            List<CausalLinkEntity> all = worldModelGraphQueryService.listCausalGraphEdges().stream()
                    .map(row -> {
                        CausalLinkEntity e = new CausalLinkEntity();
                        e.setId(stripPrefix(row.get("id"), "edge-"));
                        e.setSourceGoalId(stripPrefix(row.get("source"), "goal-"));
                        e.setTargetGoalId(stripPrefix(row.get("target"), "goal-"));
                        Object label = row.get("label");
                        e.setLabel(label == null ? null : String.valueOf(label));
                        return e;
                    })
                    .collect(Collectors.toList());
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
            // D103：取消内存回退，DB 不可用即抛（由外层 catch 报 500「构建因果图失败」）
            nodes = worldModelGraphQueryService.listCausalGraphNodes().stream()
                    .map(WorldModelCausalNodeVO::ofRow)
                    .collect(Collectors.toList());
            edges = worldModelGraphQueryService.listCausalGraphEdges().stream()
                    .map(WorldModelCausalEdgeVO::ofRow)
                    .collect(Collectors.toList());
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
            Long lid = parseIdOrNull(id);
            if (lid == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            com.chinacreator.gzcm.sysman.dao.WorldModelGraphWriteDao.LinkRow row = writeDao.findLink(lid);
            if (row == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            return ApiResponse.success(row);
        } catch (Exception e) {
            log.error("查询因果链失败, id={}", id, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PutMapping("/goals/{id}/links")
    public ApiResponse<?> addCausalLink(@PathVariable String id, @RequestBody CausalLinkEntity link) {
        try {
            String srcRaw = link.getSourceGoalId() != null && !link.getSourceGoalId().isBlank()
                    ? link.getSourceGoalId() : id;
            String tgtRaw = link.getTargetGoalId();
            if (tgtRaw == null || tgtRaw.isBlank()) {
                return ApiResponse.badRequest("targetGoalId 不能为空");
            }
            Long srcId = parseIdOrNull(srcRaw);
            Long tgtId = parseIdOrNull(tgtRaw);
            if (srcId == null || !writeDao.goalExists(srcId)) {
                return ApiResponse.badRequest("源目标不存在: " + srcRaw);
            }
            if (tgtId == null || !writeDao.goalExists(tgtId)) {
                return ApiResponse.badRequest("目标不存在: " + tgtRaw);
            }
            if (link.getStrength() != null && (link.getStrength() < 0.0 || link.getStrength() > 1.0)) {
                return ApiResponse.badRequest("strength 必须在 0.0 ~ 1.0 之间");
            }
            long lid = writeDao.insertLink(srcId, tgtId,
                    link.getLabel() == null || link.getLabel().isBlank() ? "causal" : link.getLabel(),
                    null,
                    link.getStrength() == null ? null : java.math.BigDecimal.valueOf(link.getStrength()));
            CausalLinkEntity created = new CausalLinkEntity();
            created.setId(String.valueOf(lid));
            created.setSourceGoalId(String.valueOf(srcId));
            created.setTargetGoalId(String.valueOf(tgtId));
            created.setLabel(link.getLabel() == null || link.getLabel().isBlank() ? "causal" : link.getLabel());
            created.setStrength(link.getStrength() == null ? 1.0 : link.getStrength());
            created.setCreatedAt(System.currentTimeMillis());
            log.info("因果链创建成功, id={}, {} → {}", lid, srcId, tgtId);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建因果链失败, sourceId={}", id, e);
            return ApiResponse.internalError("创建失败");
        }
    }

    /**
     * 目标详情 wire 面（12 键，与 D103 引入该端点时的键面逐字一致）。
     *
     * <p>不复用 {@link WorldModelGoalVO}：那是列表行（22 键，含 startDate/targetValue 等
     * 该端点从未返回过的键）。详情端点只做载体替换，键面不扩。</p>
     */
    public record GoalDetailVO(String id, String name, String description, String parentId,
                               Integer progress, String status, String goalType, Object weight,
                               String orgId, String ownerUserId,
                               java.sql.Timestamp createdAt, java.sql.Timestamp updatedAt) {
    }

    @DeleteMapping("/links/{id}")
    public ApiResponse<WorldModelDeleteLinkVO> deleteLink(@PathVariable String id) {
        try {
            Long lid = parseIdOrNull(id);
            if (lid == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            com.chinacreator.gzcm.sysman.dao.WorldModelGraphWriteDao.LinkRow existing = writeDao.findLink(lid);
            if (existing == null) {
                return ApiResponse.notFound("因果链不存在: " + id);
            }
            writeDao.deleteLink(lid);
            log.info("因果链删除成功, id={}, {} → {}",
                    id, existing.sourceGoalId(), existing.targetGoalId());
            return ApiResponse.success(new WorldModelDeleteLinkVO(true, id));
        } catch (Exception e) {
            log.error("删除因果链失败, id={}", id, e);
            return ApiResponse.internalError("删除失败");
        }
    }
}
