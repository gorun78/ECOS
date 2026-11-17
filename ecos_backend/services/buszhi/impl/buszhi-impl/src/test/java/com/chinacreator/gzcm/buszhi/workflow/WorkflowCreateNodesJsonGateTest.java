package com.chinacreator.gzcm.buszhi.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * D153 回归门：{@code createWorkflow} 落库的 {@code nodes}/{@code edges} 必须是**合法 JSON**。
 *
 * <p>缺陷（2026-11-16 活体发现）：原实现用 {@code String.valueOf(body.get("nodes"))} 落库，
 * 把 {@code List<Map>} 转成 Java {@code Map.toString()}（{@code [{id=n1, type=start}]}）——
 * 不是 JSON。{@code WorkflowEngine.parseNodes} 用 Jackson 解析必失败 ⇒ 节点列表为空
 * ⇒ 实例瞬间 {@code completed}、**零任务产生**（「任务分派/审批留痕」永远不发生）。</p>
 *
 * <p>{@code updateWorkflow} 早前已用 {@code toJsonString}（正确），本门禁住新建路径与之一致。</p>
 */
class WorkflowCreateNodesJsonGateTest {

    private final WorkflowRepository dao = mock(WorkflowRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("createWorkflow 落库 nodes/edges 能被 Jackson 反解析（不是 Map.toString）")
    void createWorkflowPersistsParseableJson() throws Exception {
        when(dao.findById(any())).thenReturn(java.util.Optional.empty());
        WorkflowService service = new WorkflowService(dao, objectMapper, null, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "D153-probe");
        body.put("mode", "sequential");
        body.put("nodes", List.of(
                Map.of("id", "n1", "type", "start", "label", "开始"),
                Map.of("id", "n2", "type", "userTask", "label", "审批",
                        "config", Map.of("taskType", "APPROVAL", "assignee", "admin")),
                Map.of("id", "n3", "type", "end", "label", "结束")));
        body.put("edges", List.of(
                Map.of("source", "n1", "target", "n2"),
                Map.of("source", "n2", "target", "n3")));

        service.createWorkflow(body);

        ArgumentCaptor<WorkflowEntity> captor = ArgumentCaptor.forClass(WorkflowEntity.class);
        verify(dao).insert(captor.capture());
        WorkflowEntity saved = captor.getValue();

        // 核心断言：必须是合法 JSON（修复前这里是 `[{id=n1, type=start}]`，Jackson 必抛）
        List<Map<String, Object>> nodes = objectMapper.readValue(
                saved.getNodes(), new TypeReference<List<Map<String, Object>>>() { });
        assertEquals(3, nodes.size(), "三个节点必须完整落库");
        assertEquals("n1", nodes.get(0).get("id"));
        assertEquals("userTask", nodes.get(1).get("type"));
        assertTrue(saved.getNodes().startsWith("["), "必须是 JSON 数组形态");
        assertTrue(saved.getNodes().contains("\"id\":\"n1\""), "必须是 JSON 键值对形态（带引号）");

        List<Map<String, Object>> edges = objectMapper.readValue(
                saved.getEdges(), new TypeReference<List<Map<String, Object>>>() { });
        assertEquals(2, edges.size());
        assertEquals("n1", edges.get(0).get("source"));
    }

    @Test
    @DisplayName("缺省 nodes/edges ⇒ 落库 \"[]\" 仍是合法空数组")
    void createWorkflowDefaultEmptyIsValidJson() throws Exception {
        when(dao.findById(any())).thenReturn(java.util.Optional.empty());
        WorkflowService service = new WorkflowService(dao, objectMapper, null, null);

        service.createWorkflow(Map.of("name", "D153-default"));

        ArgumentCaptor<WorkflowEntity> captor = ArgumentCaptor.forClass(WorkflowEntity.class);
        verify(dao).insert(captor.capture());
        WorkflowEntity saved = captor.getValue();

        assertTrue(objectMapper.readValue(saved.getNodes(), List.class).isEmpty());
        assertTrue(objectMapper.readValue(saved.getEdges(), List.class).isEmpty());
    }

    @Test
    @DisplayName("D154：库中已存在 wf201/wf202 时跳过，不复用已占用 id")
    void createWorkflowSkipsExistingIds() {
        WorkflowRepository repo = mock(WorkflowRepository.class);
        WorkflowEntity occupied = new WorkflowEntity();
        occupied.setId("wf201");
        // 模拟库中 wf201..wf205 已占用（进程重启后 ID_SEQ 从 201 重来）
        when(repo.findById(org.mockito.ArgumentMatchers.startsWith("wf")))
            .thenAnswer(inv -> {
                String id = inv.getArgument(0);
                int n = Integer.parseInt(id.substring(2));
                return n <= 205 ? java.util.Optional.of(occupied) : java.util.Optional.empty();
            });
        WorkflowService service = new WorkflowService(repo, objectMapper, null, null);

        service.createWorkflow(java.util.Map.of("name", "D154-probe"));

        ArgumentCaptor<WorkflowEntity> captor = ArgumentCaptor.forClass(WorkflowEntity.class);
        verify(repo).insert(captor.capture());
        String assigned = captor.getValue().getId();
        assertTrue(Integer.parseInt(assigned.substring(2)) > 205,
            "必须跳过已占用的 wf201~wf205，实得 " + assigned);
    }
}
