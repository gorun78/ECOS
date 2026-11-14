package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.gateway.service.GatewaySysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 *   重置配置项。D23 起：sys_config 无 default_value 列 ⇒ 无基线时如实 404（见方法注释）。
 *   成功响应: { configKey, resetValue, previousValue, updated }
 *
 * <p>H10-T2 审计锚点（PMO-74 铁律 §2.4-5 + §4）：审计一律经 runtime-event
 * {@code EventBusService} 发 {@code KafkaTopics.AUDIT}（{@code ecos.audit}），
 * 主体取 {@link SecurityContextHolder} 真实认证用户（JWT subject = userId）。
 * 原实现向 {@code sys_config_audit} 写 {@code operator='system'}，而该表在库中
 * 任何 schema 都不存在（{@code to_regclass} 实测 NULL），异常被 {@code log.debug}
 * 吞掉 ⇒ 配置重置实际零审计。事件总线不可用时降级为 WARN 留痕（不静默、不阻塞主流程）。
 */
@RestController
@RequestMapping("/api/v1/sysconfig")
public class SysConfigController {

    private static final Logger log = LoggerFactory.getLogger(SysConfigController.class);

    /** runtime-event 的 EventBusService；类名反射查找，gateway 编译期不硬依赖该模块。 */
    private static final String EVENTBUS_FQN = "com.chinacreator.gzcm.runtime.eventbus.EventBusService";

    /** 无认证主体时如实记 anonymous，不得伪造成 system/admin 等可信主体。 */
    static final String ANONYMOUS_OPERATOR = "anonymous";

    private final GatewaySysConfigService gatewaySysConfigService;
    private final ApplicationContext applicationContext;

    public SysConfigController(GatewaySysConfigService gatewaySysConfigService,
                               ApplicationContext applicationContext) {
        this.gatewaySysConfigService = gatewaySysConfigService;
        this.applicationContext = applicationContext;
    }

    /**
     * PUT /api/v1/sysconfig/{key}/reset
     *
     * <p>D23（2026-11-13 验证）：原实现 {@code SELECT config_value, default_value FROM sys_config}
     * 引用了库中不存在的列 —— {@code sys_config} 在 {@code ecos_control} / {@code ecos_sysman}
     * 两个 schema 均无 {@code default_value}（实测 information_schema，V13 建表 + V162 补列清单里都没有），
     * 且 {@code public.sys_config} 根本不存在 ⇒ 每次调用必然抛 {@code BadSqlGrammarException}，
     * 接口恒返 -1 并把 JDBC 异常原文塞进 message 给前端（系统配置页「重置」按钮 100% 失败）。
     *
     * <p>现在改为：只查真实存在的列；库内无默认值基线 ⇒ 如实返回 404 + 明确原因，
     * 不再把 SQL 异常当业务错误外泄。默认值基线一旦落到 sys_config（新增列走迁移单源）即可恢复重置能力。
     */
    @RequirePermission(permission = "system:config:manage")
    @PutMapping("/{key}/reset")
    public ApiResponse<ResetResultVO> reset(@PathVariable String key) {
        try {
            List<Map<String, Object>> rows = gatewaySysConfigService.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key=?", (Object[]) new Object[]{key});
            if (rows.isEmpty()) {
                return ApiResponse.notFound("配置项不存在: " + key);
            }

            Object currentVal = rows.get(0).get("config_value");
            // sys_config 无 default_value 列（见方法注释）⇒ 无可回退基线，如实拒绝而非伪造成功
            return ApiResponse.notFound(
                    "配置项 " + key + " 未定义默认值基线，无法重置（当前值: " + currentVal + "）");

        } catch (Exception e) {
            log.error("配置重置失败: key={}", key, e);
            // 不外泄 JDBC 异常原文：只回可读结论，原文进服务端日志
            return ApiResponse.internalError("配置重置失败，请稍后重试或凭追踪号联系管理员");
        }
    }

    /** 当前认证主体（JWT subject = userId）；未认证如实返回 anonymous。 */
    static String resolveOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return ANONYMOUS_OPERATOR;
            String name = auth.getName();
            if (name == null || name.isBlank() || "anonymousUser".equals(name)) return ANONYMOUS_OPERATOR;
            return name;
        } catch (Exception e) {
            log.debug("读取 SecurityContext 失败，审计主体退化为 anonymous: {}", e.getMessage());
            return ANONYMOUS_OPERATOR;
        }
    }

    static Map<String, Object> buildAuditPayload(String operator, String configKey,
                                                 Object oldValue, String newValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "sys_config_reset");
        payload.put("source", "gateway-sysconfig");
        payload.put("module", "gateway");
        payload.put("aggregateType", "sysConfig");
        payload.put("aggregateId", configKey);
        payload.put("operator", operator);
        payload.put("oldValue", oldValue == null ? "" : String.valueOf(oldValue));
        payload.put("newValue", newValue);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    /** @return 事件是否已交给事件总线 */
    boolean publishAuditEvent(Map<String, Object> payload) {
        Object bus = resolveEventBus();
        if (bus == null) {
            log.warn("[audit] EventBusService 不可用，审计事件未送达 {}: key={} operator={}",
                    KafkaTopics.AUDIT, payload.get("aggregateId"), payload.get("operator"));
            return false;
        }
        try {
            bus.getClass().getMethod("publish", String.class, Object.class)
                    .invoke(bus, KafkaTopics.AUDIT, payload);
            return true;
        } catch (Exception e) {
            log.warn("[audit] 审计事件发布失败（不阻塞主流程）: key={} operator={} {}",
                    payload.get("aggregateId"), payload.get("operator"), e.getMessage());
            return false;
        }
    }

    Object resolveEventBus() {
        try {
            Class<?> type = Class.forName(EVENTBUS_FQN);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length > 0) return applicationContext.getBean(names[0]);
        } catch (ClassNotFoundException e) {
            log.debug("[audit] runtime-event 不在 classpath，审计事件无法外发");
        } catch (Exception e) {
            log.debug("[audit] EventBusService bean 查找失败: {}", e.getMessage());
        }
        return null;
    }

    /** 重置回执（DI-2 #147EQ）：configKey / resetValue / previousValue / updated 四键，previousValue 可为 null。 */
    public record ResetResultVO(String configKey, String resetValue, String previousValue,
                                boolean updated) {
    }
}
