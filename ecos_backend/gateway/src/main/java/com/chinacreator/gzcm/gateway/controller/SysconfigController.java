package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.service.GatewaySysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统配置重置端点 — 独立 Controller。
 *
 * <p>注意: 与 SysConfigController（/api/v1/system/config）不同，
 * 本 Controller 的基路径为 /api/v1/sysconfig（无 "system" 层级）。
 * 前端 resetSysConfig 调用 PUT /api/v1/sysconfig/{key}/reset。
 * 两者共存，路由均由 VersionPrefixRewriteFilter 管理。
 *
 * <p>PUT /api/v1/sysconfig/{key}/reset
 *   将指定 config_key 的 config_value 重置为 sys_config.default_value。
 *   响应: { configKey, resetValue, previousValue, updated }
 */
@RestController
@RequestMapping("/api/v1/sysconfig")
public class SysconfigController {

    private static final Logger log = LoggerFactory.getLogger(SysconfigController.class);

    private final GatewaySysConfigService gatewaySysConfigService;

    public SysconfigController(GatewaySysConfigService gatewaySysConfigService) {
        this.gatewaySysConfigService = gatewaySysConfigService;
    }

    /**
     * PUT /api/v1/sysconfig/{key}/reset
     * 重置指定配置项为 default_value。
     */
    @PutMapping("/{key}/reset")
    public ApiResponse<Map<String, Object>> reset(@PathVariable String key) {
        try {
            // 1. 查当前值 + default_value
            List<Map<String, Object>> rows = gatewaySysConfigService.queryForList(
                    "SELECT config_value, default_value FROM sys_config WHERE config_key=?", (Object[]) new Object[]{key});
            if (rows.isEmpty()) {
                return ApiResponse.notFound("配置项不存在: " + key);
            }

            Map<String, Object> row = rows.get(0);
            Object currentVal = row.get("config_value");
            Object defaultVal = row.get("default_value");
            if (defaultVal == null) defaultVal = "";

            // 2. 执行重置
            int affected = gatewaySysConfigService.update(
                    "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                    String.valueOf(defaultVal), key);

            if (affected == 0) {
                return ApiResponse.internalError("重置失败，配置项可能已被删除");
            }

            // 3. 审计日志（fire-and-forget）
            try {
                gatewaySysConfigService.update(
                        "INSERT INTO sys_config_audit (config_key, old_value, new_value, action, operator, created_at) " +
                        "VALUES (?, ?, ?, 'reset', 'system', NOW())",
                        key,
                        currentVal != null ? String.valueOf(currentVal) : "",
                        String.valueOf(defaultVal));
            } catch (Exception auditEx) {
                log.debug("配置重置审计日志写入失败（忽略）: {}", auditEx.getMessage());
            }

            log.info("配置重置: key={} oldValue={} newValue={}", key, currentVal, defaultVal);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("configKey", key);
            result.put("resetValue", String.valueOf(defaultVal));
            result.put("previousValue", currentVal != null ? String.valueOf(currentVal) : null);
            result.put("updated", true);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("配置重置失败: key={}", key, e);
            return ApiResponse.internalError("配置重置失败: " + e.getMessage());
        }
    }
}
