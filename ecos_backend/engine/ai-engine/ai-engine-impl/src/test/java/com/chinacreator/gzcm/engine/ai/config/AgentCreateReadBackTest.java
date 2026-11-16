package com.chinacreator.gzcm.engine.ai.config;

import com.chinacreator.gzcm.engine.ai.dao.AgentDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * D117（2026-11-15）：{@code POST /api/v1/agents} 的创建响应必须与随后的 {@code GET} 回读一致 ——
 * DAO 的「写后回读」不得被跳过。
 *
 * <p>缺陷现场：{@code createAgent} 插入后直接 {@code toRow(entity)}，而 entity 上没有
 * {@code created_at}/{@code updated_at}（这两列由 DB 默认值填充），导致创建响应
 * {@code createdAt=null / updatedAt=null}，而同一条记录的 GET 回读却有值。
 * 同类的 update/bindTools/publish 都走 {@code findById(...).map(this::toRow)}，只有创建例外。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentCreateReadBackTest {

    @Mock
    private AgentDao dao;

    private AgentConfigService service;

    @BeforeEach
    void setUp() {
        service = new AgentConfigService(dao, new ObjectMapper());
    }

    private static AgentEntity persistedEntity() {
        AgentEntity e = new AgentEntity();
        e.setId("agent-999");
        e.setName("probe");
        e.setModelProvider("deepseek");
        e.setModelName("deepseek-chat");
        e.setSystemPrompt("p");
        e.setTools("[]");
        e.setKnowledge("[]");
        e.setStatus("draft");
        LocalDateTime t = LocalDateTime.of(2026, 11, 15, 22, 1, 4);
        e.setCreatedAt(t);
        e.setUpdatedAt(t);
        return e;
    }

    @Test
    @DisplayName("D117: createAgent 必须写后回读，响应 createdAt/updatedAt 不得为 null")
    void createAgentReturnsPersistedTimestamps() {
        lenient().when(dao.findById(anyString())).thenReturn(Optional.of(persistedEntity()));

        AgentConfigService.AgentRow row = service.createAgent(Map.of("name", "probe"));

        assertNotNull(row.createdAt(), "createdAt 不得为 null（必须写后回读，不能直接 toRow(entity)）");
        assertNotNull(row.updatedAt(), "updatedAt 不得为 null（必须写后回读）");
        assertEquals("2026-11-15T22:01:04", row.createdAt());
        verify(dao).insert(any(AgentEntity.class));
        verify(dao).findById(anyString());
    }

    @Test
    @DisplayName("D118: 新建 id 必须以库内最大序号为基线下推（重启后不得撞已有主键）")
    void createAgentIdFollowsDbMaxNotInProcessCounter() {
        // 模拟「重启后进程计数归零，但库里已有 agent-101」
        lenient().when(dao.maxNumericAgentSuffix()).thenReturn(101);
        lenient().when(dao.findById(anyString())).thenReturn(Optional.of(persistedEntity()));

        org.mockito.ArgumentCaptor<AgentEntity> cap = org.mockito.ArgumentCaptor.forClass(AgentEntity.class);
        service.createAgent(Map.of("name", "probe2"));
        verify(dao).insert(cap.capture());

        String id = cap.getValue().getId();
        int suffix = Integer.parseInt(id.substring("agent-".length()));
        assertEquals(102, suffix, "库内最大为 101 时新 id 必须是 agent-102（实际 " + id + "）");
    }
}
