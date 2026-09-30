package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionPolicyService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DataPermissionAuditEgressTest — 数据权限策略写操作的审计出口与主体归因。
 *
 * <p>对应 H10-T2 / T2-1a：整改前 create/update 把 operator 硬编码为 "admin"，
 * deletePolicy 甚至没有 operator 形参 ⇒ 三类写操作全部不可归因。
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionAuditEgressTest {

    @Mock
    private IDataPermissionPolicyService policyService;

    @Mock
    private EventBusService eventBus;

    private DataPermissionController controller;

    @BeforeEach
    void setUp() {
        controller = new DataPermissionController();
        ReflectionTestUtils.setField(controller, "policyService", policyService);
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

    @Test
    @DisplayName("主体取 token 上下文 userId，不用硬编码 admin")
    void operatorComesFromAuthenticatedContext() {
        authenticate("u-77", "alice");
        assertEquals("u-77", controller.resolveOperator());
    }

    @Test
    @DisplayName("无上下文降级 anonymous，绝不落 admin/system")
    void missingContextYieldsAnonymousNotAdmin() {
        String operator = controller.resolveOperator();
        assertEquals(DataPermissionController.ANONYMOUS_OPERATOR, operator);
        assertNotEquals("admin", operator);
        assertNotEquals("system", operator);
    }

    @Test
    @DisplayName("userId 缺失时退回 username，仍非硬编码")
    void fallsBackToUsernameWhenUserIdBlank() {
        authenticate("  ", "bob");
        assertEquals("bob", controller.resolveOperator());
    }

    @Test
    @DisplayName("create 走真实主体且事件投递到 ecos.audit")
    void createPublishesAuditToEcosystemAuditTopic() throws Exception {
        authenticate("u-77", "alice");
        DataPermissionPolicy policy = new DataPermissionPolicy();
        policy.setPolicyName("new-policy");

        ApiResponse<?> resp = controller.create(policy);

        assertTrue(resp.isSuccess());
        verify(policyService).createPolicy(policy, "u-77");

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(1)).publish(topic.capture(), payload.capture());

        assertEquals(KafkaTopics.AUDIT, topic.getValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) payload.getValue();
        assertEquals("data_permission_policy_create", event.get("eventType"));
        assertEquals("u-77", event.get("operator"));
        assertEquals("security", event.get("module"));
        assertEquals("dataPermissionPolicy", event.get("aggregateType"));
        assertEquals(policy.getPolicyId(), event.get("aggregateId"));
    }

    @Test
    @DisplayName("delete 无 operator 形参，仍经审计事件带上主体")
    void deleteCarriesActorThroughAuditEvent() throws Exception {
        authenticate("u-88", "carol");

        ApiResponse<?> resp = controller.delete("pol-9");

        assertTrue(resp.isSuccess());
        verify(policyService).deletePolicy("pol-9");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(anyString(), payload.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) payload.getValue();
        assertEquals("data_permission_policy_delete", event.get("eventType"));
        assertEquals("u-88", event.get("operator"));
        assertEquals("pol-9", event.get("aggregateId"));
    }

    @Test
    @DisplayName("审计总线抛异常：写操作照常成功，publishAudit 返回 false 不外溢")
    void auditFailureNeverBlocksTheWrite() throws Exception {
        authenticate("u-77", "alice");
        doThrow(new IllegalStateException("kafka down"))
                .when(eventBus).publish(anyString(), any());

        assertFalse(controller.publishAudit("data_permission_policy_update", "pol-1", "u-77", null, "x"));

        DataPermissionPolicy policy = new DataPermissionPolicy();
        policy.setPolicyName("updated");
        ApiResponse<?> resp = controller.update("pol-7", policy);
        assertTrue(resp.isSuccess());
        assertEquals("pol-7", policy.getPolicyId());
    }

    @Test
    @DisplayName("eventBus 缺失（null）：降级不抛，写操作仍成功")
    void missingEventBusDegradesWithoutThrowing() {
        ReflectionTestUtils.setField(controller, "eventBus", null);
        authenticate("u-77", "alice");
        assertFalse(controller.publishAudit("data_permission_policy_delete", "pol-2", "u-77", null, null));
    }

    @Test
    @DisplayName("payload 带齐主体/新旧值/时间戳，且原样投递未包成副本")
    void payloadCarriesChangeSetAndIsDeliveredAsIs() {
        Map<String, Object> payload = controller.buildAuditPayload(
                "data_permission_policy_update", "pol-3", "u-77", "old", "new");

        assertEquals("old", payload.get("oldValue"));
        assertEquals("new", payload.get("newValue"));
        assertNotNull(payload.get("eventId"));
        assertNotNull(payload.get("timestamp"));

        assertTrue(controller.publishAudit("data_permission_policy_update", "pol-3", "u-77", "old", "new"));

        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(eventBus).publish(org.mockito.ArgumentMatchers.eq(KafkaTopics.AUDIT), captured.capture());
        assertSameSentPayloadShape(captured.getValue());
    }

    @SuppressWarnings("unchecked")
    private static void assertSameSentPayloadShape(Object sent) {
        Map<String, Object> event = (Map<String, Object>) sent;
        assertEquals("data_permission_policy_update", event.get("eventType"));
        assertEquals("pol-3", event.get("aggregateId"));
        assertEquals("u-77", event.get("operator"));
        assertEquals("old", event.get("oldValue"));
        assertEquals("new", event.get("newValue"));
    }

    @Test
    @DisplayName("oldValue/newValue 为 null 时序列化为空串，不留 null 键")
    void nullValuesSerializeAsEmptyString() {
        Map<String, Object> payload = controller.buildAuditPayload(
                "data_permission_policy_delete", "pol-4", "u-77", null, null);
        assertEquals("", payload.get("oldValue"));
        assertEquals("", payload.get("newValue"));
    }
}
