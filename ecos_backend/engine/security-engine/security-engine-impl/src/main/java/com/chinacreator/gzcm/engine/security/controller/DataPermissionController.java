package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionPolicyService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/data-permission", "/api/v1/security/permission"})
public class DataPermissionController {
    private static final Logger log = LoggerFactory.getLogger(DataPermissionController.class);

    static final String ANONYMOUS_OPERATOR = "anonymous";
    private static final String AUDIT_SOURCE = "security-engine-datapermission";
    private static final String AUDIT_AGGREGATE_TYPE = "dataPermissionPolicy";

    @Autowired(required = false)
    private IDataPermissionPolicyService policyService;

    /** runtime-event 统一事件总线；缺失时审计降级为 WARN 而非静默。 */
    @Autowired(required = false)
    private EventBusService eventBus;

    @GetMapping("/policies")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String policyType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> r = new LinkedHashMap<>();
            if (policyService == null) {
                r.put("data", Collections.emptyList()); r.put("total", 0);
                r.put("page", page); r.put("pageSize", pageSize);
                return ApiResponse.success(r);
            }
            Map<String, Object> cond = new HashMap<>();
            if (keyword != null) cond.put("keyword", keyword);
            if (policyType != null) cond.put("policyType", policyType);
            List<DataPermissionPolicy> all = policyService.listPolicies(cond);
            int from = (page - 1) * pageSize, to = Math.min(from + pageSize, all.size());
            r.put("data", from < all.size() ? all.subList(from, to) : Collections.emptyList());
            r.put("total", all.size()); r.put("page", page); r.put("pageSize", pageSize);
            return ApiResponse.success(r);
        } catch (Throwable e) {
            log.error("查询数据权限策略失败", e);
            return ApiResponse.internalError("查询数据权限策略失败");
        }
    }

    @PostMapping("/policies")
    public ApiResponse<?> create(@RequestBody DataPermissionPolicy policy) {
        String operator = resolveOperator();
        try {
            if (policyService == null) return ApiResponse.internalError("服务未就绪");
            policy.setPolicyId(UUID.randomUUID().toString().replace("-", ""));
            DataPermissionPolicy created = policyService.createPolicy(policy, operator);
            publishAudit("data_permission_policy_create", policy.getPolicyId(), operator,
                    null, policy.getPolicyName());
            return ApiResponse.success(created);
        } catch (Throwable e) {
            log.error("创建数据权限策略失败: policyId={} operator={}", policy.getPolicyId(), operator, e);
            return ApiResponse.internalError("创建数据权限策略失败");
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody DataPermissionPolicy policy) {
        String operator = resolveOperator();
        try {
            if (policyService == null) return ApiResponse.internalError("服务未就绪");
            policy.setPolicyId(id);
            DataPermissionPolicy updated = policyService.updatePolicy(policy, operator);
            publishAudit("data_permission_policy_update", id, operator,
                    null, policy.getPolicyName());
            return ApiResponse.success(updated);
        } catch (Throwable e) {
            log.error("更新数据权限策略失败: policyId={} operator={}", id, operator, e);
            return ApiResponse.internalError("更新数据权限策略失败");
        }
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        String operator = resolveOperator();
        try {
            if (policyService == null) return ApiResponse.internalError("服务未就绪");
            policyService.deletePolicy(id);
            publishAudit("data_permission_policy_delete", id, operator, null, null);
            return ApiResponse.success(Map.of("success", true));
        } catch (Throwable e) {
            log.error("删除数据权限策略失败: policyId={} operator={}", id, operator, e);
            return ApiResponse.internalError("删除数据权限策略失败");
        }
    }

    /**
     * 审计主体取已认证 token 上下文，不接受客户端自报，未认证降级为 anonymous
     * 而非看似可信的 admin/system（后端开发规范 §十一 N17 同类）。
     */
    String resolveOperator() {
        String userId = UserContext.getCurrentUserId();
        if (userId != null && !userId.isBlank() && !ANONYMOUS_OPERATOR.equals(userId)) {
            return userId;
        }
        String username = UserContext.getCurrentUsername();
        if (username != null && !username.isBlank() && !ANONYMOUS_OPERATOR.equals(username)) {
            return username;
        }
        return ANONYMOUS_OPERATOR;
    }

    Map<String, Object> buildAuditPayload(String eventType, String policyId, String operator,
                                          Object oldValue, Object newValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("timestamp", Instant.now().toString());
        payload.put("source", AUDIT_SOURCE);
        payload.put("module", "security");
        payload.put("aggregateType", AUDIT_AGGREGATE_TYPE);
        payload.put("aggregateId", policyId);
        payload.put("operator", operator);
        payload.put("oldValue", oldValue != null ? String.valueOf(oldValue) : "");
        payload.put("newValue", newValue != null ? String.valueOf(newValue) : "");
        return payload;
    }

    /** 审计不阻塞主流程，但必须不静默：未送达只 WARN。 */
    boolean publishAudit(String eventType, String policyId, String operator,
                         Object oldValue, Object newValue) {
        Map<String, Object> payload = buildAuditPayload(eventType, policyId, operator, oldValue, newValue);
        if (eventBus == null) {
            log.warn("审计未送达（runtime-event EventBusService 缺失）: eventType={} policyId={} operator={}",
                    eventType, policyId, operator);
            return false;
        }
        try {
            eventBus.publish(KafkaTopics.AUDIT, payload);
            log.info("审计已送达: eventType={} topic={} policyId={} operator={}",
                    eventType, KafkaTopics.AUDIT, policyId, operator);
            return true;
        } catch (Exception e) {
            log.warn("审计未送达（事件总线抛出异常）: eventType={} policyId={} operator={} reason={}",
                    eventType, policyId, operator, e.getMessage());
            return false;
        }
    }
}
