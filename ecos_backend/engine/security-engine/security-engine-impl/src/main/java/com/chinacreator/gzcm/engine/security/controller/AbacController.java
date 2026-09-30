package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/abac", "/api/v1/security/abac"})
public class AbacController {
    private static final Logger log = LoggerFactory.getLogger(AbacController.class);

    static final String ANONYMOUS_OPERATOR = "anonymous";
    private static final String AUDIT_SOURCE = "security-engine-abac";
    private static final String AUDIT_AGGREGATE_TYPE = "abacPolicy";

    @Autowired(required = false)
    private IAbacPolicyService policyService;

    /** runtime-event 统一事件总线；缺失时审计降级为 WARN 而非静默。 */
    @Autowired(required = false)
    private EventBusService eventBus;

    // P1-2: 策略变更时触发缓存清除
    @Autowired(required = false)
    private com.chinacreator.gzcm.engine.security.service.OpaPolicyService opaPolicyService;

    @GetMapping("/policies")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            if (policyService == null) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("data", Collections.emptyList());
                empty.put("total", 0);
                empty.put("page", page);
                empty.put("pageSize", pageSize);
                return ApiResponse.success(empty);
            }
            List<AbacPolicy> all = policyService.listPolicies();
            // filter
            if (keyword != null && !keyword.isEmpty()) {
                all = all.stream().filter(p ->
                    p.getPolicyName() != null && p.getPolicyName().contains(keyword)
                ).collect(java.util.stream.Collectors.toList());
            }
            // paginate
            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, all.size());
            List<AbacPolicy> pageList = from < all.size() ? all.subList(from, to) : Collections.emptyList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", pageList);
            result.put("total", all.size());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询ABAC策略列表失败", e);
            return ApiResponse.internalError("查询ABAC策略列表失败");
        }
    }

    @GetMapping("/policies/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            AbacPolicy p = policyService.getPolicy(id);
            if (p == null) return ApiResponse.notFound("策略不存在");
            return ApiResponse.success(p);
        } catch (Exception e) {
            log.error("查询ABAC策略失败: policyId={}", id, e);
            return ApiResponse.internalError("查询ABAC策略失败");
        }
    }

    @PostMapping("/policies")
    @RequirePermission(permission = "security:abac:manage")
    public ApiResponse<?> create(@RequestBody AbacPolicy policy) {
        String operator = resolveOperator();
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            policy.setPolicyId(UUID.randomUUID().toString().replace("-", ""));
            AbacPolicy created = policyService.createPolicy(policy);
            evictDecisionCache();  // P1-2: 策略变更后清除决策缓存
            publishAudit("abac_policy_create", policy.getPolicyId(), operator,
                    null, describePolicy(policy));
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建ABAC策略失败: policyId={} operator={}", policy.getPolicyId(), operator, e);
            return ApiResponse.internalError("创建ABAC策略失败");
        }
    }

    @PutMapping("/policies/{id}")
    @RequirePermission(permission = "security:abac:manage")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody AbacPolicy policy) {
        String operator = resolveOperator();
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            policy.setPolicyId(id);
            AbacPolicy updated = policyService.updatePolicy(policy);
            evictDecisionCache();  // P1-2: 策略变更后清除决策缓存
            publishAudit("abac_policy_update", id, operator,
                    null, describePolicy(policy));
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新ABAC策略失败: policyId={} operator={}", id, operator, e);
            return ApiResponse.internalError("更新ABAC策略失败");
        }
    }

    @DeleteMapping("/policies/{id}")
    @RequirePermission(permission = "security:abac:manage")
    public ApiResponse<?> delete(@PathVariable String id) {
        String operator = resolveOperator();
        try {
            if (policyService == null) return ApiResponse.internalError("ABAC策略服务未就绪");
            policyService.deletePolicy(id);
            evictDecisionCache();  // P1-2: 策略变更后清除决策缓存
            publishAudit("abac_policy_delete", id, operator, null, null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", true);
            return ApiResponse.success(m);
        } catch (Exception e) {
            log.error("删除ABAC策略失败: policyId={} operator={}", id, operator, e);
            return ApiResponse.internalError("删除ABAC策略失败");
        }
    }

    /** P1-2: 策略变更后触发 OPA 策略重新加载，清除缓存决策 */
    private void evictDecisionCache() {
        if (opaPolicyService != null) {
            try {
                opaPolicyService.invalidateCache();
                log.info("ABAC策略变更, OPA缓存已失效");
            } catch (Exception e) {
                log.warn("OPA缓存清除失败: {}", e.getMessage());
            }
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

    /** 审计只记策略元信息（名称/效果/优先级/作用域），不落条件表达式正文。 */
    String describePolicy(AbacPolicy policy) {
        return "name=" + policy.getPolicyName()
                + " effect=" + policy.getEffect()
                + " priority=" + policy.getPriority()
                + " scopeType=" + policy.getScopeType();
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
