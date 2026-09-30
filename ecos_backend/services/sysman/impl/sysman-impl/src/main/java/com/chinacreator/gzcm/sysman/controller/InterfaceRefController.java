package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dto.InterfaceRefUpsertDTO;
import com.chinacreator.gzcm.sysman.service.InterfaceRefQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PMO-60 v2.0 T4b — 场景接口引用 CRUD 控制器。
 * <p>
 * 对应真表 {@code ecos_interface_ref}（V149），场景绑定的外部接口引用
 * （HTTP/KAFKA/REST/MQ/AMQP）独立成表。
 * <p>
 * 路径前缀 {@code /api/v1/interfaces}，未被 {@code VersionPrefixRewriteFilter} 重写（KEEP）。
 * 鉴权：{@code SecurityConfig} 中该前缀不在 permitAll（PMO-60 v2.0 P4 P0-2 已移除），
 * 走 {@code .anyRequest().authenticated()}（需 Bearer Token）。
 * 准入：{@code ClearanceInterceptor} 默认路径规则按 {@code /api/v1/} 前缀推断 L1（内部级）。
 * <p>
 * 数据访问：PMO-74 H9-T4 起 SQL 全部下沉 {@link InterfaceRefQueryService}
 * （Controller 不持有数据访问模板 — 硬规则）；本类只做入参校验、{@code ApiResponse}
 * 包装与审计事件发布。
 * <p>
 * 写操作审计：通过 {@code EventBusService}（可选注入）发送事件到
 * {@code KafkaTopics.AUDIT}（{@code ecos.audit}）topic。不可用时仅 WARN 日志，不阻塞主流程。
 */
@RestController
@RequestMapping("/api/v1/interfaces")
public class InterfaceRefController {

    private static final Logger log = LoggerFactory.getLogger(InterfaceRefController.class);

    private final InterfaceRefQueryService interfaceRefService;
    private final ObjectProvider<Object> eventBusProvider;

    /**
     * 构造器注入。
     *
     * @param interfaceRefService 接口引用数据访问服务（SQL 已下沉，PMO-74 H9-T4）
     * @param eventBusProvider    事件总线可选注入，不可用时为 empty
     */
    public InterfaceRefController(InterfaceRefQueryService interfaceRefService,
                                  ObjectProvider<Object> eventBusProvider) {
        this.interfaceRefService = interfaceRefService;
        this.eventBusProvider = eventBusProvider;
    }

    // ── GET / — 列表 ─────────────────────────────────────────────

    /**
     * 查询所有未删除的接口引用。
     *
     * @return {@code ApiResponse} 包装的接口引用列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        try {
            return ApiResponse.success(interfaceRefService.listAll());
        } catch (Exception e) {
            log.error("查询接口引用列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ── GET /{id} — 详情 ─────────────────────────────────────────────

    /**
     * 按 ID 查询单条接口引用。
     *
     * @param id 接口引用主键（ifc_xxxxxxxx）
     * @return {@code ApiResponse} 包装的详情；不存在返回 notFound
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable String id) {
        try {
            Map<String, Object> row = interfaceRefService.findById(id);
            if (row == null) {
                return ApiResponse.notFound("接口引用不存在: " + id);
            }
            return ApiResponse.success(row);
        } catch (Exception e) {
            log.error("查询接口引用详情失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ── POST / — 创建 ─────────────────────────────────────────────

    /**
     * 创建接口引用。自动生成 ID（ifc_ + 8位UUID片段）。
     * 创建成功后发送审计事件到 ecos.audit topic。
     *
     * @param dto 请求体 DTO：name（必填）、interfaceType（必填）、endpoint（必填）、method、timeoutMs
     * @return {@code ApiResponse} 包装的新建接口引用
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody InterfaceRefUpsertDTO dto) {
        String name = dto.getName();
        String interfaceType = dto.getInterfaceType();
        String endpoint = dto.getEndpoint();
        if (name == null || name.isBlank()) {
            return ApiResponse.badRequest("name 不能为空");
        }
        if (interfaceType == null || interfaceType.isBlank()) {
            return ApiResponse.badRequest("interfaceType 不能为空");
        }
        if (endpoint == null || endpoint.isBlank()) {
            return ApiResponse.badRequest("endpoint 不能为空");
        }
        String method = dto.getMethod();
        int timeoutMs = dto.getTimeoutMs() != null ? dto.getTimeoutMs() : 3000;

        try {
            String id = interfaceRefService.create(name, interfaceType, endpoint, method, timeoutMs);
            log.info("接口引用创建成功: id={}, name={}, type={}", id, name, interfaceType);

            publishAuditEvent("interface_ref_create", id, name);

            return ApiResponse.success(Map.of("id", id, "name", name));
        } catch (Exception e) {
            log.error("创建接口引用失败: name={}", name, e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    // ── PUT /{id} — 更新 ─────────────────────────────────────────────

    /**
     * 更新接口引用（name/interface_type/endpoint/method/timeout_ms）。不允许跨改 id。
     * 更新成功后发送审计事件。
     *
     * @param id  接口引用主键
     * @param dto 请求体 DTO：name、interfaceType、endpoint、method、timeoutMs
     * @return {@code ApiResponse} 包装的更新结果
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                   @RequestBody InterfaceRefUpsertDTO dto) {
        try {
            Integer count = interfaceRefService.countById(id);
            if (count == null || count == 0) {
                return ApiResponse.notFound("接口引用不存在: " + id);
            }
        } catch (Exception e) {
            log.error("校验接口引用存在性失败: id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }

        String name = dto.getName();
        String interfaceType = dto.getInterfaceType();
        String endpoint = dto.getEndpoint();
        String method = dto.getMethod();
        Integer timeoutMs = dto.getTimeoutMs();

        if (name != null && name.isBlank()) {
            return ApiResponse.badRequest("name 不能为空字符串");
        }
        if (endpoint != null && endpoint.isBlank()) {
            return ApiResponse.badRequest("endpoint 不能为空字符串");
        }

        try {
            int rows = interfaceRefService.update(id, name, interfaceType, endpoint, method, timeoutMs);
            if (rows == 0) {
                return ApiResponse.notFound("接口引用不存在或已删除: " + id);
            }
            log.info("接口引用更新成功: id={}", id);

            publishAuditEvent("interface_ref_update", id, name != null ? name : "updated");
            return ApiResponse.success(Map.of("id", id, "updated", true));
        } catch (Exception e) {
            log.error("更新接口引用失败: id={}", id, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    // ── DELETE /{id} — 逻辑删除 ─────────────────────────────────────────────

    /**
     * 逻辑删除接口引用（is_deleted → 1）。删除成功后发送审计事件。
     *
     * @param id 接口引用主键
     * @return {@code ApiResponse} 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        try {
            int rows = interfaceRefService.logicalDelete(id);
            if (rows == 0) {
                return ApiResponse.notFound("接口引用不存在或已删除: " + id);
            }
            log.info("接口引用已逻辑删除: id={}", id);

            publishAuditEvent("interface_ref_delete", id, "deleted");
            return ApiResponse.success(Map.of("id", id, "deleted", true));
        } catch (Exception e) {
            log.error("删除接口引用失败: id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ── 审计事件辅助 ─────────────────────────────────────────────

    /**
     * 发布审计事件到 ecos.audit topic。
     * <p>
     * 通过 ObjectProvider 可选注入 EventBusService，发送失败或不可用时仅 WARN 日志，
     * 不阻塞主流程（铁律 §2.4-5 审计不阻塞主流程）。
     *
     * @param eventType 事件类型（interface_ref_create / update / delete）
     * @param id        接口引用 ID
     * @param name      接口引用名称
     */
    private void publishAuditEvent(String eventType, String id, String name) {
        try {
            Object eventBus = eventBusProvider.getIfAvailable();
            if (eventBus == null) {
                log.debug("[audit] EventBusService 不可用，跳过审计事件: {}", eventType);
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("source", "sysman-interface-ref");
            payload.put("aggregateType", "interfaceRef");
            payload.put("aggregateId", id);
            payload.put("name", name);
            payload.put("timestamp", System.currentTimeMillis());

            eventBus.getClass().getMethod("publish", String.class, Object.class)
                    .invoke(eventBus, "ecos.audit", payload);
            log.debug("[audit] 审计事件已发布: {} id={}", eventType, id);
        } catch (Exception e) {
            log.warn("[audit] 审计事件发布失败（不阻塞主流程）: eventType={}, id={}, {}",
                    eventType, id, e.getMessage());
        }
    }
}
