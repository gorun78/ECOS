package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.DataSourceService;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.metadata.MetadataAsyncTrigger;
import com.chinacreator.gzcm.engine.data.metadata.MetadataStrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 元数据策略更新兼容层 — PMO-40 批次3 T2。
 *
 * <p>前端 {@code data-workbench/api.ts} 使用
 * {@code PUT /api/v1/datanet/metadata/strategy/{id}} 路径，而主路径在
 * {@link MetadataController#saveStrategy}
 * （{@code PUT /api/v1/datanet/metadata/strategy/{id}} + 裸路径
 * {@code /api/datanet/metadata/strategy/{id}}）。
 * 本 Controller 是薄路径兼容层，<b>1-to-1 委托</b>给已有的
 * {@link DataSourceService#updateMetadataConfig}（不重复实现，§2.5），
 * 同时兼容前端旧路径变量名 {@code {id}}（数据源 ID = datasourceId）。
 * 不新增业务逻辑，不改动主路径（§0.2 API 只增不改）。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>PUT /api/v1/datanet/metadata/strategy/{id} — 单字段 PATCH 元数据策略</li>
 *   <li>裸路径 /api/datanet/metadata/strategy/{id} 同样可达（双路径）</li>
 * </ul>
 *
 * <h3>语义（与 {@link MetadataController#saveStrategy} 一致）</h3>
 * <ul>
 *   <li>请求体可含 {@code strategy} / {@code countMethod} / {@code scheduleCron}；
 *       未出现的字段保持现有值不变（单字段 PATCH 语义）</li>
 *   <li>策略必须为 ON_SAVE / ON_SCHEDULE / MANUAL / ON_DEMAND 之一</li>
 *   <li>行数统计方式必须为 EXACT / ESTIMATE / OFF 之一</li>
 *   <li>切换为 ON_SAVE 后异步触发采集任务（复用 {@link MetadataAsyncTrigger#submitAsync}）</li>
 *   <li>数据源不存在 → 404</li>
 *   <li>策略 / 行数统计方式取值非法 → 400</li>
 * </ul>
 *
 * @author PMO-40 Batch3
 */
@RestController
@RequestMapping({"/api/v1/datanet/metadata", "/api/datanet/metadata"})
public class MetadataStrategyCompatController {

    private static final Logger log = LoggerFactory.getLogger(MetadataStrategyCompatController.class);

    private final DataSourceService dataSourceService;
    private final MetadataAsyncTrigger asyncTrigger;

    public MetadataStrategyCompatController(DataSourceService dataSourceService,
                                            MetadataAsyncTrigger asyncTrigger) {
        this.dataSourceService = dataSourceService;
        this.asyncTrigger = asyncTrigger;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUT /api/v1/datanet/metadata/strategy/{id} — 兼容路径
    // ═══════════════════════════════════════════════════════════════

    /**
     * 单字段 PATCH 元数据策略 — 与
     * {@code PUT /api/v1/datanet/metadata/strategy/{id}}
     * 响应字段完全一致（§T2 验收）。
     *
     * @param datasourceId 数据源 ID（前端旧名 {id}）
     * @param body         可选包含 strategy / countMethod / scheduleCron
     */
    @PutMapping("/strategy/{id}")
    public ApiResponse<Map<String, Object>> updateStrategy(
            @PathVariable("id") String datasourceId,
            @RequestBody Map<String, Object> body) {
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new com.chinacreator.gzcm.common.exception.NotFoundException(
                "数据源不存在: " + datasourceId);
        }

        MetadataStrategyConfig cfg = MetadataStrategyConfig.fromJson(ds.getMetadataConfig());

        if (body.containsKey("strategy")) {
            String s = String.valueOf(body.get("strategy"));
            if (!s.equals(MetadataStrategyConfig.STRATEGY_ON_SAVE)
                    && !s.equals(MetadataStrategyConfig.STRATEGY_ON_SCHEDULE)
                    && !s.equals(MetadataStrategyConfig.STRATEGY_MANUAL)
                    && !s.equals(MetadataStrategyConfig.STRATEGY_ON_DEMAND)) {
                return ApiResponse.badRequest("未知策略: " + s);
            }
            cfg.setStrategy(s);
        }
        // 前端 ConnectionsTab 兼容写法：body { trigger: "ON_SAVE" }（api.ts:610）
        if (body.containsKey("trigger")) {
            String t = String.valueOf(body.get("trigger"));
            if (!t.equals(MetadataStrategyConfig.STRATEGY_ON_SAVE)
                    && !t.equals(MetadataStrategyConfig.STRATEGY_ON_SCHEDULE)
                    && !t.equals(MetadataStrategyConfig.STRATEGY_MANUAL)
                    && !t.equals(MetadataStrategyConfig.STRATEGY_ON_DEMAND)) {
                return ApiResponse.badRequest("未知策略: " + t);
            }
            cfg.setStrategy(t);
        }
        if (body.containsKey("countMethod")) {
            String cm = String.valueOf(body.get("countMethod"));
            if (!cm.equals(MetadataStrategyConfig.COUNT_EXACT)
                    && !cm.equals(MetadataStrategyConfig.COUNT_ESTIMATE)
                    && !cm.equals(MetadataStrategyConfig.COUNT_OFF)) {
                return ApiResponse.badRequest("未知行数统计方式: " + cm);
            }
            cfg.setCountMethod(cm);
        }
        if (body.containsKey("scheduleCron")) {
            cfg.setScheduleCron(body.get("scheduleCron") == null ? null : String.valueOf(body.get("scheduleCron")));
        }

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cfg);
            dataSourceService.updateMetadataConfig(datasourceId, json);
        } catch (Exception e) {
            log.warn("Compat: strategy save failed for datasource={}: {}", datasourceId, e.getMessage());
            return ApiResponse.internalError("策略保存失败: " + e.getMessage());
        }

        // 切换到 ON_SAVE 时触发异步采集（复用 MetadataAsyncTrigger，不重复实现，§2.5）
        if (MetadataStrategyConfig.STRATEGY_ON_SAVE.equalsIgnoreCase(cfg.getStrategy())) {
            try {
                String taskId = asyncTrigger.submitAsync(datasourceId);
                log.info("Compat: strategy->ON_SAVE, async collect submitted taskId={}", taskId);
            } catch (Exception e) {
                log.warn("Compat: submitAsync failed for datasource={}: {}", datasourceId, e.getMessage());
                // 不阻断主流程，仅记录
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasourceId", datasourceId);
        data.put("strategy", cfg.getStrategy());
        data.put("countMethod", cfg.getCountMethod());
        data.put("scheduleCron", cfg.getScheduleCron());
        data.put("updatedAt", new java.sql.Timestamp(System.currentTimeMillis()));
        data.put("success", true);

        return ApiResponse.success("ok", data);
    }
}
