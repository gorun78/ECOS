package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.audit.AiSecurityEngineAuditInterceptor;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.AgentRow;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V-AI-095 — 创建类端点（服务端生成主键、路径里没有 id）的审计 {@code entity_id} 落库门。
 *
 * <p><b>缺陷背景</b>：{@code POST /api/v1/agents} 的响应有 id，但请求 URI 不含 id，
 * 拦截器 {@code resolveEntityId} 只能拿到 null ⇒ 审计行 {@code entity_id} 恒空，
 * 回答不了"创建的是哪个 Agent"。</p>
 *
 * <p><b>钉两件事</b>：① 控制器创建成功后把主键写入请求属性；
 * ② 拦截器据此把 {@code operation}/{@code entityType}/{@code entityId} 三键带进审计载荷。</p>
 */
class AgentConfigAuditEntityIdTest {

    private AgentConfigService service;
    private AgentConfigController controller;
    private com.chinacreator.gzcm.runtime.eventbus.EventBusService bus;
    private AiSecurityEngineAuditInterceptor interceptor;

    @BeforeEach
    void setUp() {
        service = mock(AgentConfigService.class);
        controller = new AgentConfigController(service);

        bus = mock(com.chinacreator.gzcm.runtime.eventbus.EventBusService.class);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeanNamesForType(com.chinacreator.gzcm.runtime.eventbus.EventBusService.class))
                .thenReturn(new String[]{"kafkaEventBusServiceImpl"});
        when(context.getBean(anyString())).thenReturn(bus);
        interceptor = new AiSecurityEngineAuditInterceptor(context);
    }

    private static AgentRow row() {
        return new AgentRow("agent-101", "经营诊断", "deepseek", "deepseek-v4-flash", "sys",
                List.of("query_db"), List.of("kb1"), "draft",
                "2026-11-05T09:00", "2026-11-05T09:10");
    }

    @Test
    @DisplayName("POST 创建成功后把服务端主键写入请求属性（路径无 id 时的唯一锚点）")
    void createStampsEntityIdAttribute() {
        when(service.createAgent(any())).thenReturn(row());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/agents");

        controller.createAgent(new LinkedHashMap<>(Map.of("name", "经营诊断")), req);

        assertEquals("agent-101", req.getAttribute(AiSecurityEngineAuditInterceptor.ATTR_ENTITY_ID));
    }

    @Test
    @DisplayName("拦截器从请求属性取 entityId，载荷带 operation/entityType/entityId")
    void interceptorCarriesSubjectKeysFromAttribute() throws Exception {
        when(service.createAgent(any())).thenReturn(row());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/agents");
        controller.createAgent(new LinkedHashMap<>(Map.of("name", "经营诊断")), req);

        HandlerMethod hm = new HandlerMethod(controller,
                AgentConfigController.class.getMethod("createAgent", Map.class, HttpServletRequest.class));
        interceptor.afterCompletion(req, new MockHttpServletResponse(), hm, null);

        Map<String, Object> event = capturePublish();
        assertEquals("CREATE", event.get("operation"));
        assertEquals("AgentConfig", event.get("entityType"));
        assertEquals("agent-101", event.get("entityId"),
                "创建类端点必须在审计里带上对象主键，否则 entity_id 恒空");
    }

    @Test
    @DisplayName("无该属性且路径无 id — entityId 留空（不编造）")
    void noAttributeMeansNullEntityId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/api/v1/agents");

        HandlerMethod hm = new HandlerMethod(controller,
                AgentConfigController.class.getMethod("createAgent", Map.class, HttpServletRequest.class));
        interceptor.afterCompletion(req, new MockHttpServletResponse(), hm, null);

        assertNull(capturePublish().get("entityId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePublish() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(bus).publish(eq(KafkaTopics.AUDIT), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }
}
