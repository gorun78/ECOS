package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveConfigItemVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveConfigSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveConfigSaveResult;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveAuditFallback;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * 写入完成经 runtime-event {@link EventBusService} 发 Kafka {@code ecos.audit} 真实审计事件
 * （F05-15/X-40：移除假审计 log；主通道 → 本地兜底 → 双失败拒绝动作的 fail-closed 三段式，
 * 与 cognitive2 假设链 {@code CognitiveHypothesisService#audit} 同款口径）。</p>
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
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final CognitiveAuditFallback auditFallback;

    public CognitiveConfigController(CognitiveConfigQueryService cognitiveConfigQueryService,
                                     ObjectProvider<EventBusService> eventBusProvider,
                                     CognitiveAuditFallback auditFallback) {
        this.cognitiveConfigQueryService = cognitiveConfigQueryService;
        this.eventBusProvider = eventBusProvider;
        this.auditFallback = auditFallback;
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
            // F05-07 #4：异常细节只入日志，响应体不泄漏 e.getMessage()
            return ApiResponse.internalError("认知引擎配置拉取失败，请稍后重试或查看服务端日志");
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
        emitAudit("cognitiveConfig.save", "sys_config",
                "updated=" + updated + " inserted=" + inserted + " size=" + items.size());
        return ApiResponse.success(new CognitiveConfigSaveResult(updated, inserted, items.size()));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /**
     * 真实审计事件（X-40 fail-closed 收口）：三段式——
     * 主通道经 runtime-event {@link EventBusService} 发布 {@code ecos.audit}（KafkaTopics 单源）→
     * 主通道不可用/发布失败 → {@link CognitiveAuditFallback} 本地兜底落盘（JSONL，供运维补投）→
     * 兜底也失败 → 抛 {@link BusinessException}（ECOS-COG-504）拒绝本次动作，
     * 禁止"log.warn + continue"式假审计（铁律 §2.4#5）。
     */
    private void emitAudit(String action, String resource, String detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("action", action);
        event.put("resource", resource);
        event.put("result", "success");
        event.put("detail", detail);
        event.put("userId", "system");
        event.put("timestamp", LocalDateTime.now().toString());
        String topic = KafkaTopics.AUDIT;
        if (tryPublish(topic, event)) {
            return;
        }
        try {
            auditFallback.write(event);
        } catch (Exception fe) {
            log.error("认知配置审计兜底同样失败，按 fail-closed 拒绝业务动作: action={} err={}",
                    action, fe.getMessage());
            throw new BusinessException(500, CognitiveErrorCode.E504_INTERNAL
                    + ": 审计主通道与本地兜底均不可用，操作已拒绝（fail-closed）: action=" + action);
        }
    }

    /**
     * 主通道投递（单点封装）：EventBusService 未装配或发布异常均返回 false，
     * 由调用方进入本地兜底段；本方法不做吞掉后的"视为已审计"处理。
     */
    private boolean tryPublish(String topic, Map<String, Object> event) {
        EventBusService bus = eventBusProvider.getIfAvailable();
        if (bus == null) {
            log.warn("认知配置审计主通道未装配（EventBusService 缺失），转本地兜底: topic={}", topic);
            return false;
        }
        try {
            bus.publish(topic, event);
            return true;
        } catch (Exception e) {
            log.warn("认知配置审计主通道发布失败，转本地兜底: topic={} err={}", topic, e.getMessage());
            return false;
        }
    }
}
