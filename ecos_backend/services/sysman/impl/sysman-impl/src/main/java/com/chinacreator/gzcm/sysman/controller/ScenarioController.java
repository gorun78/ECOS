package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.controller.model.ScenarioEntity;
import com.chinacreator.gzcm.sysman.dao.WorldModelScenarioDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * World Model — 场景控制器。
 *
 * <p><b>D104（2026-11-15 活体实证）</b>：旧实现把四个 CRUD 端点全挂在进程内
 * {@code ConcurrentHashMap<String, ScenarioEntity> store} 上，构造函数还塞一条「默认场景」假数据，
 * 而 {@code ecos_control.ecos_wm_scenario} 里实存 4 行真实业务场景从未被读到。内存存储与假数据
 * 已删除，读写一律走 {@link WorldModelScenarioDao}；DB 不可用时如实报错，不再拿编造数据兜底。</p>
 *
 * <pre>
 * GET    /api/v1/ecos/world-model-graph/scenarios          — 场景列表
 * POST   /api/v1/ecos/world-model-graph/scenarios          — 创建场景
 * GET    /api/v1/ecos/world-model-graph/scenarios/{id}     — 场景详情
 * PUT    /api/v1/ecos/world-model-graph/scenarios/{id}     — 更新场景
 * DELETE /api/v1/ecos/world-model-graph/scenarios/{id}     — 删除场景
 * </pre>
 */
@RestController("sysmanScenarioController")
@RequestMapping("/api/v1/ecos/world-model-graph/scenarios")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    private final WorldModelScenarioDao scenarioDao;

    public ScenarioController(WorldModelScenarioDao scenarioDao) {
        this.scenarioDao = scenarioDao;
    }

    /** 对外 id 是字符串，入库前严格转 long；非法即 null（交由调用方判 404）。 */
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

    /** DB 行 → 对外实体（时间戳转毫秒，其余同名直取）。 */
    private static ScenarioEntity toEntity(WorldModelScenarioDao.ScenarioRow row) {
        ScenarioEntity e = new ScenarioEntity();
        e.setId(row.id());
        e.setName(row.name());
        e.setDescription(row.description());
        e.setStatus(row.status());
        e.setCreatedAt(row.createdAt() == null ? null : row.createdAt().getTime());
        e.setUpdatedAt(row.updatedAt() == null ? null : row.updatedAt().getTime());
        e.setRelatedGoalIds(new ArrayList<>());
        return e;
    }

    /** 列表容器（{@code {data,total}} 两键）。 */
    public record ScenarioPage(List<ScenarioEntity> data, int total) {
    }

    /** 删除回执（{@code {success,id}} 两键）。 */
    public record DeleteResult(boolean success, String id) {
    }

    @GetMapping
    public ApiResponse<ScenarioPage> list() {
        try {
            List<ScenarioEntity> all = scenarioDao.list(null).stream()
                    .map(ScenarioController::toEntity)
                    .toList();
            return ApiResponse.success(new ScenarioPage(all, all.size()));
        } catch (Exception e) {
            log.error("查询场景列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            Long sid = parseIdOrNull(id);
            if (sid == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            WorldModelScenarioDao.ScenarioRow row = scenarioDao.find(sid);
            if (row == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            return ApiResponse.success(toEntity(row));
        } catch (Exception e) {
            log.error("查询场景失败, id={}", id, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody ScenarioEntity scenario) {
        try {
            String name = scenario.getName();
            if (name == null || name.isBlank()) {
                return ApiResponse.badRequest("name 不能为空");
            }
            String status = scenario.getStatus() == null || scenario.getStatus().isBlank()
                    ? "DRAFT" : scenario.getStatus();
            long id = scenarioDao.insert(name, scenario.getDescription(), null, status);
            ScenarioEntity created = new ScenarioEntity();
            created.setId(String.valueOf(id));
            created.setName(name);
            created.setDescription(scenario.getDescription());
            created.setStatus(status);
            created.setRelatedGoalIds(new ArrayList<>());
            created.setCreatedAt(System.currentTimeMillis());
            created.setUpdatedAt(System.currentTimeMillis());
            log.info("场景创建成功, id={}, name={}", id, name);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建场景失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody ScenarioEntity scenario) {
        try {
            Long sid = parseIdOrNull(id);
            if (sid == null || scenarioDao.find(sid) == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            int n = scenarioDao.update(sid, scenario.getName(), scenario.getDescription(),
                    null, scenario.getStatus());
            if (n == 0) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            WorldModelScenarioDao.ScenarioRow row = scenarioDao.find(sid);
            log.info("场景更新成功, id={}, name={}", sid, scenario.getName());
            return ApiResponse.success(row == null ? scenario : toEntity(row));
        } catch (Exception e) {
            log.error("更新场景失败, id={}", id, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        try {
            Long sid = parseIdOrNull(id);
            if (sid == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            WorldModelScenarioDao.ScenarioRow existing = scenarioDao.find(sid);
            if (existing == null) {
                return ApiResponse.notFound("场景不存在: " + id);
            }
            scenarioDao.delete(sid);
            log.info("场景删除成功, id={}, name={}", sid, existing.name());
            return ApiResponse.success(new DeleteResult(true, String.valueOf(sid)));
        } catch (Exception e) {
            log.error("删除场景失败, id={}", id, e);
            return ApiResponse.internalError("删除失败");
        }
    }
}
