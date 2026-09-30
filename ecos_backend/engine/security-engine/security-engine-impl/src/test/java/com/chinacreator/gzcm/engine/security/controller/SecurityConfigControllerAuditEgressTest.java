package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SecurityConfigControllerAuditEgressTest — H10-T2 / T2-1e：安全配置写面的审计出口与主体归因。
 *
 * <p>锁定的行为契约：
 * <ul>
 *   <li>operator 只取已认证 token 上下文，未认证降级 anonymous，绝不落 admin/system；</li>
 *   <li>operator 与被操作对象（targetUserId/claimedUserId）在事件里分离，
 *       使 Q16 现状（客户端 userId 参数优先）留下可查证的越权线索；</li>
 *   <li>事件投递目标为 {@code KafkaTopics.AUDIT}，正文只带字段变更摘要，
 *       不带 linkedWorkstation 宿主信息，也不带异常细节（N-25 本文件清零）；</li>
 *   <li>审计失败或未接线只 WARN，绝不阻塞业务写。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SecurityConfigControllerAuditEgressTest {

    private static final String AUDIT_SOURCE = "security-engine-securityconfig";

    @Mock
    private SecurityConfigService service;

    @Mock
    private EventBusService eventBus;

    private SecurityConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new SecurityConfigController(service);
        ReflectionTestUtils.setField(controller, "eventBus", eventBus);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static void authenticate(String userId, String username) {
        UserContext context = new UserContext();
        context.setUserId(userId);
        context.setUsername(username);
        UserContext.setCurrent(context);
    }

    /** clearanceLevel/auditMode/sandboxMandatory 三字段的最小写请求体，附带宿主信息用于证伪其外泄。 */
    private static Map<String, Object> profileBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clearanceLevel", 4);
        body.put("linkedWorkstation", "WS-SEC-01");
        body.put("auditMode", "full");
        body.put("sandboxMandatory", true);
        return body;
    }

    private Map<String, Object> captureSingleAuditEvent() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), payload.capture());
        return cast(payload.getValue());
    }

    @Test
    @DisplayName("主体取 token 上下文 userId，不硬编码 admin")
    void operatorComesFromAuthenticatedContext() {
        authenticate("u-42", "alice");
        assertEquals("u-42", controller.resolveOperator());
    }

    @Test
    @DisplayName("无上下文降级 anonymous，绝不落 admin/system")
    void missingContextYieldsAnonymousNotAdmin() {
        String operator = controller.resolveOperator();
        assertEquals(SecurityConfigController.ANONYMOUS_OPERATOR, operator);
        assertNotEquals("admin", operator);
        assertNotEquals("system", operator);
    }

    @Test
    @DisplayName("userId 空白时退回 username，仍取自已认证上下文")
    void fallsBackToUsernameWhenUserIdBlank() {
        authenticate("  ", "bob");
        assertEquals("bob", controller.resolveOperator());
    }

    @Test
    @DisplayName("PUT /profile 带他人 userId：落 user 表且事件把操作者与目标分离（Q16 现状可取证）")
    void profileUpdateSeparatesTokenOperatorFromClaimedTarget() {
        authenticate("u-42", "alice");

        ApiResponse<Map<String, Object>> resp = controller.updateProfile(profileBody(), "victim-9", null);

        assertTrue(resp.isSuccess());
        verify(service).upsertProfile(eq("td_user_security_profile"), eq("user_id"), eq("victim-9"),
                eq(4), eq("WS-SEC-01"), eq("full"), eq(Boolean.TRUE), eq(false),
                isNull(), isNull(), isNull());

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("security_profile_upsert", event.get("eventType"));
        assertEquals("u-42", event.get("operator"));
        assertEquals("victim-9", event.get("aggregateId"));
        assertEquals("victim-9", event.get("targetUserId"));
        assertEquals("victim-9", event.get("claimedUserId"));
        assertNotEquals(event.get("operator"), event.get("aggregateId"));
        assertEquals("security", event.get("module"));
        assertEquals("securityProfile", event.get("aggregateType"));
        assertEquals(AUDIT_SOURCE, event.get("source"));
    }

    @Test
    @DisplayName("审计正文只记字段摘要，不落 linkedWorkstation 宿主信息")
    void auditValueSummarizesFieldsWithoutHostIdentity() {
        authenticate("u-42", "alice");

        controller.updateProfile(profileBody(), "victim-9", null);

        String newValue = String.valueOf(captureSingleAuditEvent().get("newValue"));
        assertTrue(newValue.contains("clearanceLevel=4"));
        assertTrue(newValue.contains("auditMode=full"));
        assertTrue(newValue.contains("sandboxMandatory=true"));
        assertFalse(newValue.contains("WS-SEC-01"));
    }

    @Test
    @DisplayName("roleId 参数路由到角色表，操作者仍为 token 主体")
    void roleParameterRoutesToRoleTable() {
        ApiResponse<Map<String, Object>> resp = controller.updateProfile(profileBody(), null, "role-7");

        assertTrue(resp.isSuccess());
        verify(service).upsertProfile(eq("td_role_security_profile"), eq("role_id"), eq("role-7"),
                eq(4), eq("WS-SEC-01"), eq("full"), eq(Boolean.TRUE), eq(false),
                isNull(), isNull(), isNull());

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("role-7", event.get("aggregateId"));
        assertEquals("role-7", event.get("claimedRoleId"));
        assertEquals(SecurityConfigController.ANONYMOUS_OPERATOR, event.get("operator"));
    }

    @Test
    @DisplayName("无 userId/roleId 时写全局默认行并以 isDefault=true 下沉")
    void neitherParameterFallsBackToGlobalDefaultRow() {
        ApiResponse<Map<String, Object>> resp = controller.updateProfile(profileBody(), null, null);

        assertTrue(resp.isSuccess());
        verify(service).upsertProfile(eq("td_user_security_profile"), eq("user_id"), eq("_global_default_"),
                eq(4), eq("WS-SEC-01"), eq("full"), eq(Boolean.TRUE), eq(true),
                isNull(), isNull(), isNull());
        assertEquals("_global_default_", captureSingleAuditEvent().get("aggregateId"));
    }

    @Test
    @DisplayName("PUT /profile/user/{userId} 以路径 ID 为目标，操作者取自 token")
    void userScopedEndpointUsesPathIdAsTarget() {
        authenticate("u-42", "alice");

        ApiResponse<?> resp = controller.updateUserProfile("u-77", profileBody());

        assertTrue(resp.isSuccess());
        verify(service).upsertUserProfile(eq("u-77"), eq(4), eq("WS-SEC-01"), eq("full"),
                eq(Boolean.TRUE), eq(false), isNull(), isNull(), isNull());

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("security_user_profile_update", event.get("eventType"));
        assertEquals("u-77", event.get("aggregateId"));
        assertEquals("u-77", event.get("targetUserId"));
        assertEquals("u-42", event.get("operator"));
    }

    @Test
    @DisplayName("PUT /profile/role/{roleId} 走角色专属事件类型")
    void roleScopedEndpointUsesPathIdAsTarget() {
        authenticate("u-42", "alice");

        ApiResponse<?> resp = controller.updateRoleProfile("role-3", profileBody());

        assertTrue(resp.isSuccess());
        verify(service).upsertRoleProfile(eq("role-3"), eq(4), eq("WS-SEC-01"), eq("full"),
                eq(Boolean.TRUE), isNull(), isNull(), isNull());

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("security_role_profile_update", event.get("eventType"));
        assertEquals("role-3", event.get("aggregateId"));
        assertEquals("role-3", event.get("targetRoleId"));
        assertEquals("u-42", event.get("operator"));
    }

    @Test
    @DisplayName("POST /profiles 缺 id 时生成并以 generatedId 标注")
    void createGeneratesIdAndPinsFlag() {
        authenticate("u-42", "alice");
        SecurityProfile profile = new SecurityProfile();
        profile.setClearanceLevel(3);
        profile.setAuditMode("basic");

        ApiResponse<?> resp = controller.create(profile);

        assertTrue(resp.isSuccess());
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(service).upsertUserProfile(idCaptor.capture(), eq(3), isNull(), eq("basic"),
                isNull(), eq(false), isNull(), isNull(), isNull());

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("security_profile_create", event.get("eventType"));
        assertEquals(Boolean.TRUE, event.get("generatedId"));
        assertEquals(idCaptor.getValue(), event.get("aggregateId"));
        assertNotNull(idCaptor.getValue());
        assertFalse(idCaptor.getValue().isBlank());
        assertEquals("u-42", event.get("operator"));
    }

    @Test
    @DisplayName("PUT /profiles/{id} 事件带旧值与新值摘要")
    void updateRecordsOldValueFromExistingRow() {
        authenticate("u-42", "alice");
        SecurityProfile existing = new SecurityProfile();
        existing.setUserId("p-2");
        existing.setClearanceLevel(3);
        existing.setAuditMode("basic");
        when(service.queryUserProfile("p-2")).thenReturn(existing);

        SecurityProfile changed = new SecurityProfile();
        changed.setClearanceLevel(5);

        ApiResponse<?> resp = controller.update("p-2", changed);

        assertTrue(resp.isSuccess());
        verify(service).updateProfileFields(eq("p-2"), eq(5), isNull(), isNull(), isNull(), eq(false));

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("security_profile_update", event.get("eventType"));
        assertTrue(String.valueOf(event.get("oldValue")).contains("clearanceLevel=3"));
        assertTrue(String.valueOf(event.get("newValue")).contains("clearanceLevel=5"));
        assertEquals(Boolean.FALSE, event.get("clearedOtherDefaults"));
    }

    @Test
    @DisplayName("DELETE 记录实际影响行数与旧值摘要")
    void deleteReportsAffectedRows() {
        authenticate("u-42", "alice");
        when(service.deleteUserProfile("p-9")).thenReturn(2);

        ApiResponse<?> resp = controller.delete("p-9");

        assertTrue(resp.isSuccess());
        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("security_profile_delete", event.get("eventType"));
        assertEquals("deleted", event.get("newValue"));
        assertEquals(2, event.get("deletedRows"));
        assertEquals("", event.get("oldValue"));
    }

    @Test
    @DisplayName("拒绝删除全局默认行：返回 badRequest 且不产生审计事件")
    void globalDefaultDeleteIsRejectedWithoutAudit() {
        ApiResponse<?> resp = controller.delete("_global_default_");

        assertFalse(resp.isSuccess());
        verify(eventBus, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("activate/clone 各有专属事件类型与聚合标识")
    void activateAndClonePublishTheirOwnEventTypes() {
        authenticate("u-42", "alice");

        ApiResponse<?> activated = controller.activate("a-1");
        assertTrue(activated.isSuccess());
        verify(service).clearAllDefaults();
        verify(service).activateProfile("a-1");
        Map<String, Object> activateEvent = captureSingleAuditEvent();
        assertEquals("security_profile_activate", activateEvent.get("eventType"));
        assertEquals("a-1", activateEvent.get("aggregateId"));
        assertEquals("isDefault=true", activateEvent.get("newValue"));
        assertEquals(Boolean.TRUE, activateEvent.get("clearedOtherDefaults"));

        ApiResponse<?> cloned = controller.clone("src-1", "副本");
        assertTrue(cloned.isSuccess());
        ArgumentCaptor<String> newId = ArgumentCaptor.forClass(String.class);
        verify(service).cloneProfile(eq("src-1"), newId.capture());

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(2)).publish(eq(KafkaTopics.AUDIT), payloads.capture());
        Map<String, Object> cloneEvent = cast(payloads.getAllValues().get(1));
        assertEquals("security_profile_clone", cloneEvent.get("eventType"));
        assertEquals(newId.getValue(), cloneEvent.get("aggregateId"));
        assertNotEquals("src-1", cloneEvent.get("aggregateId"));
        assertEquals("src-1", cloneEvent.get("sourceId"));
        assertEquals("clonedFrom=src-1", cloneEvent.get("newValue"));
        assertEquals("副本", cloneEvent.get("requestedName"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object payload) {
        return (Map<String, Object>) payload;
    }

    @Test
    @DisplayName("事件总线抛异常：审计判 false，业务写照常成功")
    void auditFailureNeverBlocksTheWrite() {
        authenticate("u-42", "alice");
        doThrow(new IllegalStateException("kafka down"))
                .when(eventBus).publish(anyString(), any());

        assertFalse(controller.publishAudit("security_profile_delete", "p-1", "u-42", null, "x", null));

        ApiResponse<Map<String, Object>> resp = controller.updateProfile(profileBody(), "victim-9", null);
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("EventBusService 未接线（null）：降级 WARN 不外抛，写操作不受影响")
    void missingEventBusDegradesWithoutThrowing() {
        ReflectionTestUtils.setField(controller, "eventBus", null);
        assertFalse(controller.publishAudit("security_profile_activate", "a-2", "u-42", null, null, null));
    }

    @Test
    @DisplayName("服务层异常细节不回显客户端，也不产生审计事件（N-25 本文件清零）")
    void serviceExceptionDetailNeverReachesClient() {
        authenticate("u-42", "alice");
        doThrow(new IllegalStateException("jdbc:postgresql://10.0.0.7:5432 password=Pa55"))
                .when(service).upsertProfile(anyString(), anyString(), anyString(), any(), any(),
                        any(), any(), anyBoolean(), any(), any(), any());

        ApiResponse<Map<String, Object>> resp = controller.updateProfile(profileBody(), "victim-9", null);

        assertFalse(resp.isSuccess());
        assertNotNull(resp.getMessage());
        assertFalse(resp.getMessage().contains("Pa55"));
        assertFalse(resp.getMessage().contains("jdbc"));
        verify(eventBus, never()).publish(anyString(), any());
    }

    @Test
    @DisplayName("oldValue/newValue 为 null 时序列化为空串，事件必带 eventId/timestamp")
    void nullValuesSerializeAsEmptyStrings() {
        Map<String, Object> payload = controller.buildAuditPayload(
                "security_profile_delete", "p-4", "u-42", null, null, null);
        assertEquals("", payload.get("oldValue"));
        assertEquals("", payload.get("newValue"));
        assertNotNull(payload.get("eventId"));
        assertNotNull(payload.get("timestamp"));
        assertEquals("p-4", payload.get("aggregateId"));
        assertEquals("u-42", payload.get("operator"));
    }
}
