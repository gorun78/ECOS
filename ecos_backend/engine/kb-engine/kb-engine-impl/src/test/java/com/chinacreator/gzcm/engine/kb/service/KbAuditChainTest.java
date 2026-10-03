package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.shared.KbAuditPublisher;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode.KbErrorCodeException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F04-15 验收（mvn -Dtest=KbAuditChainTest）— 审计与事件收口 runtime-event（K-30/K-44/K-45 纠正）。
 *
 * <p>覆盖两条降级＋一条 fail-closed：
 * <ul>
 *   <li>{@code extractWriteEmitsAuditEvent}：知识抽取写操作（approve）同步经
 *       {@link KbAuditPublisher} 发 {@code ecos.audit} 事件（EventBus 首选通路）；</li>
 *   <li>{@code eventBusUnavailableFailsClosed}：EventBus 兜底 JDBC 亦失败时
 *       → 抛 KB_022（HTTP 500）"默认 DENY"，纠正"warn 后继续 = 静默丢审计"。</li>
 * </ul>
 *
 * <p>纯 Mockito，无 @SpringBootTest / 无 H2。
 *
 * @author ECOS KB Team
 */
class KbAuditChainTest {

    private JdbcTemplate jdbc;
    private EventBusService eventBus;
    private KbAuditPublisher publisher;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        eventBus = mock(EventBusService.class);
        // 模拟 Spring 注入：ObjectProvider 拿到非空 EventBus bean
        @SuppressWarnings("unchecked")
        ObjectProvider<EventBusService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(eventBus);
        publisher = new KbAuditPublisher(jdbc, provider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String principal) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(
                principal, "n/a", java.util.List.of(new SimpleGrantedAuthority("knowledge-admin")));
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    @DisplayName("F04-15: 知识抽取写操作 → 同步发 ecos.audit 事件（含 actor/entity/action，真实主体可归因）")
    void extractWriteEmitsAuditEvent() {
        login("u-1001");

        publisher.emit("APPROVED", "extraction", "ext-1",
                Map.of("rulesWritten", 3, "entitiesWritten", 7, "linksWritten", 2));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), payload.capture());
        verify(jdbc, never()).update(anyString(), any(), any(), any());

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) payload.getValue();
        assertEquals("extraction", event.get("entityType"));
        assertEquals("ext-1", event.get("entityId"));
        assertEquals("APPROVED", event.get("action"));
        assertEquals("u-1001", event.get("actor"), "actor 必须是真实 JWT 主体，禁 'current-user'（K-42）");
        assertEquals("knowledge", event.get("kt"));
        assertNotNull(event.get("eventId"), "UUID eventId 存在");
        assertEquals(3, event.get("rulesWritten"));
    }

    @Test
    @DisplayName("F04-15: EventBus publish 抛错 → 转 JDBC 本地兜底必写 kb_extract_audit")
    void eventBusThrowsFallsBackToJdbc() {
        doThrow(new RuntimeException("kafka down")).when(eventBus).publish(anyString(), any());

        publisher.emit("APPROVED", "extraction", "ext-2", null);

        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), any());
        verify(jdbc, times(1)).update(
                eq("INSERT INTO ecos_knowledge.kb_extract_audit (job_id, tier, mode, status, error_message) VALUES (?, 'kb', ?, 'AUDIT', ?)"),
                eq("ext-2"), eq("APPROVED"), eq("bus_publish_failed"));
    }

    @Test
    @DisplayName("F04-15: EventBus + JDBC 双通路全失败 → 抛 KB_022/500（默认 DENY，拒绝操作）")
    void eventBusUnavailableFailsClosed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<EventBusService> noProvider = mock(ObjectProvider.class);
        when(noProvider.getIfAvailable()).thenReturn(null);
        KbAuditPublisher closed = new KbAuditPublisher(jdbc, noProvider);

        when(jdbc.update(anyString(), any(), any(), any())).thenThrow(new RuntimeException("pg down"));

        KbErrorCodeException ex = assertThrows(KbErrorCodeException.class,
                () -> closed.emit("APPROVED", "extraction", "ext-3", null));
        assertEquals(KbErrorCode.KB_022, ex.getCodeStr(), "双通路失败 → KB_022");
        assertEquals(KbErrorCode.HTTP_022, ex.getHttpStatus(), "KB_022 → HTTP 500");
    }
}
