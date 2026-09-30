package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AbacControllerAuditEgressTest — ABAC 策略写操作的审计出口与主体归因。
 *
 * <p>对应 H10-T2 / T2-1b：与 T2-1a 的差异是 IAbacPolicyService 四个方法均无
 * operator 形参（跨模块接口不可改签名），故主体只经审计事件承载；
 * 同时覆盖 N-25（异常细节回显客户端）在本文件的清零。
 */
@ExtendWith(MockitoExtension.class)
class AbacControllerAuditEgressTest {

    @Mock
    private IAbacPolicyService policyService;

    @Mock
    private EventBusService eventBus;

    private AbacController controller;

    @BeforeEach
    void setUp() {
        controller = new AbacController();
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

    private static AbacPolicy policy() {
        AbacPolicy policy = new AbacPolicy();
        policy.setPolicyName("deny-l4-export");
        policy.setEffect("DENY");
        policy.setPriority(10);
        policy.setScopeType("TENANT");
        policy.setSubjectCondition("subject.role == 'intern' /*SENSITIVE-EXPR*/");
        return policy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureSingleAuditEvent() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), payload.capture());
        return (Map<String, Object>) payload.getValue();
    }

    @Test
    @DisplayName("主体取 token 上下文 userId，不硬编码 admin")
    void operatorComesFromAuthenticatedContext() {
        authenticate("u-55", "alice");
        assertEquals("u-55", controller.resolveOperator());
    }

    @Test
    @DisplayName("无上下文降级 anonymous，绝不落 admin/system")
    void missingContextYieldsAnonymousNotAdmin() {
        String operator = controller.resolveOperator();
        assertEquals(AbacController.ANONYMOUS_OPERATOR, operator);
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
    @DisplayName("create 写操作投递到 ecos.audit，事件带齐真实主体与聚合标识")
    void createPublishesAuditToEcosystemAuditTopic() throws Exception {
        authenticate("u-55", "alice");
        AbacPolicy policy = policy();

        ApiResponse<?> resp = controller.create(policy);

        assertTrue(resp.isSuccess());
        assertNotNull(policy.getPolicyId());

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("abac_policy_create", event.get("eventType"));
        assertEquals("u-55", event.get("operator"));
        assertEquals("security", event.get("module"));
        assertEquals("abacPolicy", event.get("aggregateType"));
        assertEquals(policy.getPolicyId(), event.get("aggregateId"));
    }

    @Test
    @DisplayName("delete 无 operator 形参，主体仍经审计事件可归因")
    void deleteCarriesActorThroughAuditEvent() throws Exception {
        authenticate("u-88", "carol");

        ApiResponse<?> resp = controller.delete("pol-3");

        assertTrue(resp.isSuccess());
        verify(policyService).deletePolicy("pol-3");

        Map<String, Object> event = captureSingleAuditEvent();
        assertEquals("abac_policy_delete", event.get("eventType"));
        assertEquals("u-88", event.get("operator"));
        assertEquals("pol-3", event.get("aggregateId"));
    }

    @Test
    @DisplayName("update 事件记策略元信息，不落条件表达式正文")
    void updateEventRecordsMetaNotConditionBody() throws Exception {
        authenticate("u-55", "alice");
        AbacPolicy policy = policy();

        ApiResponse<?> resp = controller.update("pol-9", policy);

        assertTrue(resp.isSuccess());
        Map<String, Object> event = captureSingleAuditEvent();
        String newValue = String.valueOf(event.get("newValue"));
        assertTrue(newValue.contains("name=deny-l4-export"));
        assertTrue(newValue.contains("effect=DENY"));
        assertFalse(newValue.contains("SENSITIVE-EXPR"));
    }

    @Test
    @DisplayName("审计总线抛异常：写操作照常成功，publishAudit 返回 false 不外溢")
    void auditFailureNeverBlocksTheWrite() throws Exception {
        authenticate("u-55", "alice");
        doThrow(new IllegalStateException("kafka down"))
                .when(eventBus).publish(anyString(), any());

        assertFalse(controller.publishAudit("abac_policy_update", "pol-1", "u-55", null, "x"));

        ApiResponse<?> resp = controller.update("pol-7", policy());
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("eventBus 缺失（null）：降级为 WARN 不抛，判 false")
    void missingEventBusDegradesWithoutThrowing() {
        ReflectionTestUtils.setField(controller, "eventBus", null);
        assertFalse(controller.publishAudit("abac_policy_delete", "pol-2", "u-55", null, null));
    }

    @Test
    @DisplayName("oldValue/newValue 为 null 时序列化为空串，不留 null 键")
    void nullValuesSerializeAsEmptyString() {
        Map<String, Object> payload = controller.buildAuditPayload(
                "abac_policy_delete", "pol-4", "u-55", null, null);
        assertEquals("", payload.get("oldValue"));
        assertEquals("", payload.get("newValue"));
        assertNotNull(payload.get("eventId"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    @DisplayName("服务异常细节不回显客户端（N-25 本文件清零）")
    void clientFacingErrorDoesNotEchoExceptionDetail() throws Exception {
        when(policyService.getPolicy("p-1"))
                .thenThrow(new IAbacPolicyService.AbacException("jdbc:postgresql://10.0.0.7:5432 password=Pa55"));

        ApiResponse<?> resp = controller.get("p-1");

        assertFalse(resp.isSuccess());
        assertNotNull(resp.getMessage());
        assertFalse(resp.getMessage().contains("Pa55"));
        assertFalse(resp.getMessage().contains("jdbc"));
    }
}
