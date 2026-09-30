package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/security")
public class SecurityConfigController {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigController.class);

    static final String ANONYMOUS_OPERATOR = "anonymous";
    private static final String AUDIT_SOURCE = "security-engine-securityconfig";
    private static final String AUDIT_AGGREGATE_TYPE = "securityProfile";

    private final SecurityConfigService service;

    /**
     * runtime-event 统一事件总线（审计出口）。可选协作者 ⇒ 字段注入而非改构造签名，
     * 既有 {@code new SecurityConfigController(service)} 调用方与契约不受影响。
     */
    @Autowired(required = false)
    private EventBusService eventBus;

    public SecurityConfigController(SecurityConfigService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        try {
            if (scopeType != null && !scopeType.isBlank()) {
                SecurityProfile profile = service.queryProfileByScope(scopeType.toUpperCase(), scopeId);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("source", "scope_" + scopeType.toLowerCase());
                result.putAll(service.populateProfileResult(profile));
                return ApiResponse.success(result);
            }

            String effectiveUserId = userId != null ? userId : UserContext.getCurrentUserId();
            recordSubjectMismatch("GET /profile", userId);

            SecurityProfile profile = null;
            String source = "global_default";

            if (effectiveUserId != null) {
                profile = service.queryUserProfile(effectiveUserId);
                if (profile != null) {
                    source = "user";
                }
            }

            if (profile == null && roleId != null) {
                profile = service.queryRoleProfile(roleId);
                if (profile != null) {
                    source = "role";
                }
            }
            if (profile == null && effectiveUserId != null) {
                profile = service.queryHighestRoleProfileForUser(effectiveUserId);
                if (profile != null) {
                    source = "role";
                }
            }

            if (profile == null) {
                profile = service.queryGlobalDefaultProfile();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", source);
            result.putAll(service.populateProfileResult(profile));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取安全配置失败", e);
            return ApiResponse.internalError("获取安全配置失败");
        }
    }

    @PutMapping("/profile")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<Map<String, Object>> updateProfile(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String roleId) {
        String operator = resolveOperator();
        try {
            String effectiveUserId = userId != null ? userId : UserContext.getCurrentUserId();
            recordSubjectMismatch("PUT /profile", userId);

            String targetTable;
            String idColumn;
            String idValue;
            boolean isGlobal = false;

            if (effectiveUserId != null) {
                targetTable = "td_user_security_profile";
                idColumn = "user_id";
                idValue = effectiveUserId;
            } else if (roleId != null) {
                targetTable = "td_role_security_profile";
                idColumn = "role_id";
                idValue = roleId;
            } else {
                targetTable = "td_user_security_profile";
                idColumn = "user_id";
                idValue = "_global_default_";
                isGlobal = true;
            }

            Integer clearanceLevel = body.containsKey("clearanceLevel")
                    ? ((Number) body.get("clearanceLevel")).intValue() : null;
            String linkedWorkstation = body.containsKey("linkedWorkstation")
                    ? (String) body.get("linkedWorkstation") : null;
            String auditMode = body.containsKey("auditMode")
                    ? (String) body.get("auditMode") : null;
            Boolean sandboxMandatory = body.containsKey("sandboxMandatory")
                    ? Boolean.TRUE.equals(body.get("sandboxMandatory")) : null;
            String scopeType = body.containsKey("scopeType")
                    ? (String) body.get("scopeType") : null;
            String bodyTenantId = body.containsKey("tenantId")
                    ? (String) body.get("tenantId") : null;
            String orgId = body.containsKey("orgId")
                    ? (String) body.get("orgId") : null;

            service.upsertProfile(targetTable, idColumn, idValue,
                    clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isGlobal,
                    scopeType, bodyTenantId, orgId);

            log.info("安全配置更新成功: table={}, id={}, scopeType={}", targetTable, idValue, scopeType);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("targetTable", targetTable);
            extra.put("targetUserId", idValue);
            extra.put("claimedUserId", userId);
            extra.put("claimedRoleId", roleId);
            publishAudit("security_profile_upsert", idValue, operator,
                    null, describeFields(clearanceLevel, auditMode, sandboxMandatory, scopeType), extra);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("table", targetTable);
            result.put(idColumn, idValue);
            if (clearanceLevel != null) result.put("clearanceLevel", clearanceLevel);
            if (linkedWorkstation != null) result.put("linkedWorkstation", linkedWorkstation);
            if (auditMode != null) result.put("auditMode", auditMode);
            if (sandboxMandatory != null) result.put("sandboxMandatory", sandboxMandatory);
            if (scopeType != null) result.put("scopeType", scopeType);
            if (bodyTenantId != null) result.put("tenantId", bodyTenantId);
            if (orgId != null) result.put("orgId", orgId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("更新安全配置失败", e);
            return ApiResponse.internalError("更新安全配置失败");
        }
    }

    @GetMapping("/profile/user/{userId}")
    public ApiResponse<?> getUserProfile(@PathVariable String userId) {
        try {
            SecurityProfile profile = service.queryUserProfile(userId);
            if (profile == null) {
                return ApiResponse.notFound("用户安全配置不存在: " + userId);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询用户安全配置失败: userId={}", userId, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PutMapping("/profile/user/{userId}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> updateUserProfile(@PathVariable String userId,
                                            @RequestBody Map<String, Object> body) {
        String operator = resolveOperator();
        try {
            Integer clearanceLevel = body.containsKey("clearanceLevel")
                    ? ((Number) body.get("clearanceLevel")).intValue() : null;
            String linkedWorkstation = body.containsKey("linkedWorkstation")
                    ? (String) body.get("linkedWorkstation") : null;
            String auditMode = body.containsKey("auditMode")
                    ? (String) body.get("auditMode") : null;
            Boolean sandboxMandatory = body.containsKey("sandboxMandatory")
                    ? (Boolean) body.get("sandboxMandatory") : null;
            String scopeType = body.containsKey("scopeType")
                    ? (String) body.get("scopeType") : null;
            String bodyTenantId = body.containsKey("tenantId")
                    ? (String) body.get("tenantId") : null;
            String orgId = body.containsKey("orgId")
                    ? (String) body.get("orgId") : null;

            service.upsertUserProfile(userId, clearanceLevel, linkedWorkstation,
                    auditMode, sandboxMandatory, false, scopeType, bodyTenantId, orgId);

            log.info("用户安全配置更新成功: userId={}, scopeType={}", userId, scopeType);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("targetUserId", userId);
            publishAudit("security_user_profile_update", userId, operator,
                    null, describeFields(clearanceLevel, auditMode, sandboxMandatory, scopeType), extra);

            SecurityProfile updated = service.queryUserProfile(userId);
            return ApiResponse.success(updated != null ? updated : Map.of("userId", userId, "success", true));
        } catch (Exception e) {
            log.error("更新用户安全配置失败: userId={}", userId, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @GetMapping("/profile/role/{roleId}")
    public ApiResponse<?> getRoleProfile(@PathVariable String roleId) {
        try {
            SecurityProfile profile = service.queryRoleProfile(roleId);
            if (profile == null) {
                return ApiResponse.notFound("角色安全配置不存在: " + roleId);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询角色安全配置失败: roleId={}", roleId, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PutMapping("/profile/role/{roleId}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> updateRoleProfile(@PathVariable String roleId,
                                            @RequestBody Map<String, Object> body) {
        String operator = resolveOperator();
        try {
            Integer clearanceLevel = body.containsKey("clearanceLevel")
                    ? ((Number) body.get("clearanceLevel")).intValue() : null;
            String linkedWorkstation = body.containsKey("linkedWorkstation")
                    ? (String) body.get("linkedWorkstation") : null;
            String auditMode = body.containsKey("auditMode")
                    ? (String) body.get("auditMode") : null;
            Boolean sandboxMandatory = body.containsKey("sandboxMandatory")
                    ? (Boolean) body.get("sandboxMandatory") : null;
            String scopeType = body.containsKey("scopeType")
                    ? (String) body.get("scopeType") : null;
            String bodyTenantId = body.containsKey("tenantId")
                    ? (String) body.get("tenantId") : null;
            String orgId = body.containsKey("orgId")
                    ? (String) body.get("orgId") : null;

            service.upsertRoleProfile(roleId, clearanceLevel, linkedWorkstation,
                    auditMode, sandboxMandatory, scopeType, bodyTenantId, orgId);

            log.info("角色安全配置更新成功: roleId={}, scopeType={}", roleId, scopeType);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("targetRoleId", roleId);
            publishAudit("security_role_profile_update", roleId, operator,
                    null, describeFields(clearanceLevel, auditMode, sandboxMandatory, scopeType), extra);

            SecurityProfile updated = service.queryRoleProfile(roleId);
            return ApiResponse.success(updated != null ? updated : Map.of("roleId", roleId, "success", true));
        } catch (Exception e) {
            log.error("更新角色安全配置失败: roleId={}", roleId, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @GetMapping("/profile/roles")
    public ApiResponse<?> listRoleProfiles() {
        try {
            return ApiResponse.success(service.queryAllRoleProfiles());
        } catch (Exception e) {
            log.error("查询角色安全配置列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/profile/users")
    public ApiResponse<?> listUserProfiles() {
        try {
            return ApiResponse.success(service.queryAllUserProfiles());
        } catch (Exception e) {
            log.error("查询用户安全配置列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/profiles")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String scopeId) {
        try {
            List<SecurityProfile> all;
            if (scopeType != null && !scopeType.isBlank()) {
                all = service.queryProfilesByScope(scopeType, scopeId);
            } else {
                all = new ArrayList<>();
                all.addAll(service.queryAllUserProfiles());
                all.addAll(service.queryAllRoleProfiles());
            }

            if (keyword != null && !keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                all = all.stream()
                        .filter(p -> (p.getUserId() != null && p.getUserId().toLowerCase().contains(kw))
                                || (p.getRoleId() != null && p.getRoleId().toLowerCase().contains(kw))
                                || (p.getAuditMode() != null && p.getAuditMode().toLowerCase().contains(kw)))
                        .collect(Collectors.toList());
            }

            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, all.size());
            List<SecurityProfile> pageList = from < all.size()
                    ? all.subList(from, to)
                    : Collections.emptyList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", pageList);
            result.put("total", all.size());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询安全配置列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/profiles/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            SecurityProfile profile = service.queryUserProfile(id);
            if (profile == null) {
                return ApiResponse.notFound("安全配置不存在: " + id);
            }
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("查询安全配置失败, id={}", id, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/profiles/active")
    public ApiResponse<?> getActive() {
        try {
            SecurityProfile active = service.queryGlobalDefaultProfile();
            if (active == null) {
                return ApiResponse.notFound("未找到激活的安全配置");
            }
            return ApiResponse.success(active);
        } catch (Exception e) {
            log.error("查询激活的安全配置失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping("/profiles")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> create(@RequestBody SecurityProfile profile) {
        String operator = resolveOperator();
        try {
            String id = profile.getUserId();
            boolean generatedId = id == null || id.isBlank();
            if (generatedId) {
                id = UUID.randomUUID().toString().replace("-", "");
            }

            boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());

            if (isDefault) {
                service.clearAllDefaults();
            }

            service.upsertUserProfile(id, profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault,
                    profile.getScopeType(), profile.getTenantId(), profile.getOrgId());

            log.info("安全配置模板创建成功, id={}", id);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("generatedId", generatedId);
            publishAudit("security_profile_create", id, operator, null,
                    describeProfile(profile), extra);

            SecurityProfile created = service.queryUserProfile(id);
            return ApiResponse.success(created != null ? created : profile);
        } catch (Exception e) {
            log.error("创建安全配置失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/profiles/{id}")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody SecurityProfile profile) {
        String operator = resolveOperator();
        try {
            SecurityProfile existing = service.queryUserProfile(id);
            if (existing == null) {
                existing = service.queryRoleProfile(id);
                if (existing == null) {
                    return ApiResponse.notFound("安全配置不存在: " + id);
                }
            }

            boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());
            if (isDefault) {
                service.clearAllDefaults();
            }

            String oldValue = describeProfile(existing);

            service.updateProfileFields(id, profile.getClearanceLevel(), profile.getLinkedWorkstation(),
                    profile.getAuditMode(), profile.getSandboxMandatory(), isDefault);

            log.info("安全配置模板更新成功, id={}", id);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("clearedOtherDefaults", isDefault);
            publishAudit("security_profile_update", id, operator, oldValue,
                    describeProfile(profile), extra);

            SecurityProfile updated = service.queryUserProfile(id);
            return ApiResponse.success(updated != null ? updated : profile);
        } catch (Exception e) {
            log.error("更新安全配置失败, id={}", id, e);
            return ApiResponse.internalError("更新失败");
        }
    }

    @DeleteMapping("/profiles/{id}")
    @RequirePermission(permission = "security:config:delete")
    public ApiResponse<?> delete(@PathVariable String id) {
        String operator = resolveOperator();
        try {
            if ("_global_default_".equals(id)) {
                return ApiResponse.badRequest("不允许删除全局默认安全配置");
            }

            SecurityProfile existing = service.queryUserProfile(id);
            if (existing == null) {
                existing = service.queryRoleProfile(id);
            }
            String oldValue = existing != null ? describeProfile(existing) : null;

            int rows = service.deleteUserProfile(id);
            if (rows == 0) {
                rows = service.deleteRoleProfile(id);
            }
            if (rows == 0) {
                return ApiResponse.notFound("安全配置不存在: " + id);
            }

            log.info("安全配置删除成功, id={}", id);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("deletedRows", rows);
            publishAudit("security_profile_delete", id, operator, oldValue, "deleted", extra);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除安全配置失败, id={}", id, e);
            return ApiResponse.internalError("删除失败");
        }
    }

    @PostMapping("/profiles/{id}/activate")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> activate(@PathVariable String id) {
        String operator = resolveOperator();
        try {
            SecurityProfile before = service.queryUserProfile(id);
            String oldValue = before != null ? describeProfile(before) : null;

            service.clearAllDefaults();
            service.activateProfile(id);

            log.info("安全配置已激活, id={}", id);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("clearedOtherDefaults", true);
            publishAudit("security_profile_activate", id, operator, oldValue, "isDefault=true", extra);

            SecurityProfile profile = service.queryUserProfile(id);
            return ApiResponse.success(profile);
        } catch (Exception e) {
            log.error("激活安全配置失败, id={}", id, e);
            return ApiResponse.internalError("激活失败");
        }
    }

    @PostMapping("/profiles/{id}/clone")
    @RequirePermission(permission = "security:config:update")
    public ApiResponse<?> clone(@PathVariable String id,
                                @RequestParam(required = false) String newName) {
        String operator = resolveOperator();
        try {
            String newId = UUID.randomUUID().toString().replace("-", "");
            service.cloneProfile(id, newId);

            log.info("安全配置克隆成功, sourceId={}, newId={}", id, newId);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("sourceId", id);
            extra.put("requestedName", newName);
            publishAudit("security_profile_clone", newId, operator, null, "clonedFrom=" + id, extra);

            SecurityProfile cloned = service.queryUserProfile(newId);
            return ApiResponse.success(cloned);
        } catch (IllegalArgumentException e) {
            log.warn("克隆安全配置失败（源配置不存在）: sourceId={}, reason={}", id, e.getMessage());
            return ApiResponse.notFound("源安全配置不存在");
        } catch (Exception e) {
            log.error("克隆安全配置失败, sourceId={}", id, e);
            return ApiResponse.internalError("克隆失败");
        }
    }

    /**
     * 审计主体取已认证 token 上下文，不接受客户端自报；未认证降级为 anonymous
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

    /**
     * 客户端传入的 userId 优先于 token 主体（Q16 现状，本任务不改动其行为），
     * 只留下取证线索：两者不一致时按 ASCII 锚点记 WARN，供运行期与审计消费侧比对。
     */
    void recordSubjectMismatch(String endpoint, String claimedUserId) {
        String tokenUserId = UserContext.getCurrentUserId();
        if (claimedUserId == null || claimedUserId.isBlank()) {
            return;
        }
        if (!claimedUserId.equals(tokenUserId)) {
            log.warn("subject mismatch: endpoint={} claimedUserId={} tokenUserId={}",
                    endpoint, claimedUserId, tokenUserId);
        }
    }

    /** 审计只记字段变更摘要，不落 linkedWorkstation 之外的宿主信息正文。 */
    String describeFields(Integer clearanceLevel, String auditMode,
                          Boolean sandboxMandatory, String scopeType) {
        return "clearanceLevel=" + clearanceLevel
                + " auditMode=" + auditMode
                + " sandboxMandatory=" + sandboxMandatory
                + " scopeType=" + scopeType;
    }

    String describeProfile(SecurityProfile profile) {
        if (profile == null) {
            return "";
        }
        return describeFields(profile.getClearanceLevel(), profile.getAuditMode(),
                profile.getSandboxMandatory(), profile.getScopeType())
                + " userId=" + profile.getUserId()
                + " roleId=" + profile.getRoleId()
                + " isDefault=" + profile.getIsDefault();
    }

    Map<String, Object> buildAuditPayload(String eventType, String aggregateId, String operator,
                                          Object oldValue, Object newValue, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", eventType);
        payload.put("timestamp", Instant.now().toString());
        payload.put("source", AUDIT_SOURCE);
        payload.put("module", "security");
        payload.put("aggregateType", AUDIT_AGGREGATE_TYPE);
        payload.put("aggregateId", aggregateId);
        payload.put("operator", operator);
        payload.put("oldValue", oldValue != null ? String.valueOf(oldValue) : "");
        payload.put("newValue", newValue != null ? String.valueOf(newValue) : "");
        if (extra != null) {
            payload.putAll(extra);
        }
        return payload;
    }

    /** 审计不阻塞主流程，但必须不静默：未送达只 WARN。 */
    boolean publishAudit(String eventType, String aggregateId, String operator,
                         Object oldValue, Object newValue, Map<String, Object> extra) {
        Map<String, Object> payload = buildAuditPayload(eventType, aggregateId, operator,
                oldValue, newValue, extra);
        if (eventBus == null) {
            log.warn("审计未送达（runtime-event EventBusService 缺失）: eventType={} aggregateId={} operator={}",
                    eventType, aggregateId, operator);
            return false;
        }
        try {
            eventBus.publish(KafkaTopics.AUDIT, payload);
            log.info("审计已送达: eventType={} topic={} aggregateId={} operator={}",
                    eventType, KafkaTopics.AUDIT, aggregateId, operator);
            return true;
        } catch (Exception e) {
            log.warn("审计未送达（事件总线抛出异常）: eventType={} aggregateId={} operator={} reason={}",
                    eventType, aggregateId, operator, e.getClass().getSimpleName());
            return false;
        }
    }
}
