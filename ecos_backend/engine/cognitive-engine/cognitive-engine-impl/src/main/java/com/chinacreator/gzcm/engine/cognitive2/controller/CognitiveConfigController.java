package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveConfigItemVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveConfigSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveConfigSaveResult;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认知引擎配置 REST API — 前缀 /api/v1/cognitive/config
 *
 * <p>PMO-51 T1：为知识工作台 CognitiveConfigTab 提供 GET / 与 PUT 双端点。
 *
 * <p>数据源：sys_config 表（config_group='cognitive'，与前端 CognitiveConfigTab
 * 默认 {@code cognitive.*} 前缀的 key 对齐）。
 * 读写全部下沉 {@link CognitiveConfigQueryService}（架构铁律 §3.6：Controller
 * 不得直接访问数据库），避免跨模块 import sysman 包
 * （架构铁律：引擎层不依赖服务层）。
 * 写入完成发 {@code ecos.audit} 审计日志（log 兜底；正式 Kafka 接 PMO-50 EventBus 后切换）。</p>
 *
 * <p>路径池：</p>
 * <ul>
 *   <li>GET  /api/v1/cognitive/config — 拉取本组全部配置（active）</li>
 *   <li>PUT  /api/v1/cognitive/config — 批量 upsert 配置值（强类型 DTO 数组）</li>
 * </ul>
 *
 * <p>与 ai-engine {@code CognitiveConfigController}（/api/v1/cognitive/config/defaults 端点）同前缀
 * 不冲突：本 Controller 只映射 GET/PUT 根路径；ai 端点路径 /defaults 单独映射。
 * Bean name 加 {@code ecos} 前缀（架构铁律 1.3：新 Bean 加 ecos 前缀避免 default name 冲突）。</p>
 */
@RestController("ecosCognitiveConfigController")
@RequestMapping("/api/v1/cognitive/config")
public class CognitiveConfigController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveConfigController.class);

    /** 与前端 CognitiveConfigTab 默认 cognitive.* 前缀 key 同组 */
    static final String GROUP = "cognitive";

    private final CognitiveConfigQueryService cognitiveConfigQueryService;

    public CognitiveConfigController(CognitiveConfigQueryService cognitiveConfigQueryService) {
        this.cognitiveConfigQueryService = cognitiveConfigQueryService;
    }

    /**
     * GET /api/v1/cognitive/config — 返回本组全部配置行（active）。
     */
    @GetMapping
    public ApiResponse<List<CognitiveConfigItemVO>> list() {
        try {
            List<Map<String, Object>> rows = cognitiveConfigQueryService.listActiveConfigRows(GROUP);
            List<CognitiveConfigItemVO> vos = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                vos.add(new CognitiveConfigItemVO(
                        str(row.get("config_key")),
                        str(row.get("config_value")),
                        str(row.get("description")),
                        str(row.get("config_type")),
                        str(row.get("config_label"))));
            }
            log.debug("CognitiveConfig list: rows={}", vos.size());
            return ApiResponse.success(vos);
        } catch (DataAccessException e) {
            log.error("Failed to fetch cognitive config rows: {}", e.getMessage(), e);
            return ApiResponse.internalError("认知引擎配置拉取失败: " + e.getMessage());
        }
    }

    /**
     * PUT /api/v1/cognitive/config — 批量 upsert 配置值。
     *
     * <p>Body 为强类型 {@link CognitiveConfigSaveDTO} 数组：
     * {@code [{configKey, configValue}, ...]}。任一 configKey 缺失抛 {@link BusinessException}。</p>
     */
    @PutMapping
    public ApiResponse<CognitiveConfigSaveResult> save(@RequestBody List<CognitiveConfigSaveDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("items 不能为空");
        }
        int updated = 0;
        int inserted = 0;
        for (CognitiveConfigSaveDTO item : items) {
            String key = item == null ? null : item.getConfigKey();
            String value = item == null ? null : item.getConfigValue();
            if (key == null || key.isBlank()) {
                throw new BusinessException("configKey 不能为空");
            }
            int rows = cognitiveConfigQueryService.upsertConfigValue(
                    key, value == null ? "" : value, GROUP);
            if (rows > 0) {
                updated++;
            } else {
                inserted++;
            }
        }
        log.info("CognitiveConfig save: updated={}, inserted={}, group={}", updated, inserted, GROUP);
        emitAudit("cognitiveConfig.save",
                "updated=" + updated + " inserted=" + inserted + " size=" + items.size());
        return ApiResponse.success(new CognitiveConfigSaveResult(updated, inserted, items.size()));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 审计事件：写操作落 log 兜底；正式 Kafka 发件走 PMO-50 EventBus 统一接入后切换。 */
    private void emitAudit(String action, String detail) {
        try {
            log.info("AUDIT topic=ecos.audit action={} detail={}", action, detail);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }
}
