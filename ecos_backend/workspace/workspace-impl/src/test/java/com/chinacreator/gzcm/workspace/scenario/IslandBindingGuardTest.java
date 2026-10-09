package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-04 孤岛判定验收：判据是连边可达性（degree==0）而非节点类型（破 X-27 只数 type=="resource"）。
 * DRAFT 保存期打标；DRAFT→ACTIVE 存在孤岛一律 409。纯内存图算法。
 */
class IslandBindingGuardTest {

    private final IslandBindingGuard guard = new IslandBindingGuard();

    private BindingNode node(String id, BindingType type) {
        return new BindingNode(id, "name-" + id, type);
    }

    @Test
    @DisplayName("DRAFT 保存期：无连边的绑定被打 island=true + reason=NO_REACHABLE_EDGE")
    void draftSavePersistsIslandFlag() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE));
        List<LinkEdge> links = Collections.singletonList(new LinkEdge("ds-1", "ot-1", EdgeType.MAPPING));
        bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE),
                node("kb-1", BindingType.KNOWLEDGE_BASE));

        List<IslandBindingGuard.IslandMark> marks = guard.markIslands(bindings, links);
        IslandBindingGuard.IslandMark kb = markOf(marks, "kb-1");
        assertTrue(kb.isIsland(), "kb-1 无任何连边 → 孤岛");
        assertEquals(IslandBindingGuard.IslandReason.NO_REACHABLE_EDGE, kb.getReason());
        assertFalse(markOf(marks, "ds-1").isIsland(), "ds-1 有连边 → 非孤岛");
    }

    @Test
    @DisplayName("激活存在孤岛 → 抛 IslandBindingException(409, ISLAND_BINDING) 且 details.islands 非空")
    void activateWithIslandReturnsHttp409WithIslandList() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("kb-1", BindingType.KNOWLEDGE_BASE));
        List<LinkEdge> links = Collections.emptyList();

        IslandBindingException ex = assertThrows(IslandBindingException.class,
                () -> guard.assertNoIslands(bindings, links));

        assertEquals(409, ex.getHttpStatus());
        assertEquals("ISLAND_BINDING", ex.getCode());
        Object islands = ex.getDetails().get("islands");
        assertInstanceOf(List.class, islands);
        assertEquals(2, ((List<?>) islands).size(), "两条绑定均孤岛 → islands 长度 2");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) islands).get(0);
        assertTrue(first.containsKey("bindingId") && first.containsKey("reason"));
    }

    @Test
    @DisplayName("激活无孤岛（全连通）→ assertNoIslands 放行不抛")
    void activateWithoutIslandSucceeds() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ot-1", BindingType.OBJECT_TYPE),
                node("kb-1", BindingType.KNOWLEDGE_BASE));
        List<LinkEdge> links = Arrays.asList(
                new LinkEdge("ds-1", "ot-1", EdgeType.MAPPING),
                new LinkEdge("ot-1", "kb-1", EdgeType.EXTRACTION));

        guard.assertNoIslands(bindings, links);
    }

    @Test
    @DisplayName("孤岛判定看连边不看节点类型：同类型一连通一孤立、异类型连通者非岛")
    void islandDetectionUsesEdgeReachabilityNotNodeType() {
        List<BindingNode> bindings = Arrays.asList(
                node("ds-1", BindingType.DATASET),
                node("ds-2", BindingType.DATASET),
                node("kb-1", BindingType.KNOWLEDGE_BASE));
        // ds-1↔ds-2 相连；kb-1 类型不同但无连边 → kb-1 才是孤岛
        List<LinkEdge> links = Collections.singletonList(new LinkEdge("ds-1", "ds-2", EdgeType.MAPPING));

        Set<String> islandIds = new HashSet<>();
        for (CompletenessReport.IslandView v : guard.findIslands(bindings, links)) {
            islandIds.add(v.getBindingId());
        }

        assertTrue(islandIds.contains("kb-1"), "无连边的 kb-1 是孤岛（与类型无关）");
        assertFalse(islandIds.contains("ds-1"), "有连边的 DATASET 不是孤岛（破按类型判定）");
        assertFalse(islandIds.contains("ds-2"));
        assertEquals(1, islandIds.size());
    }

    private IslandBindingGuard.IslandMark markOf(List<IslandBindingGuard.IslandMark> marks, String id) {
        for (IslandBindingGuard.IslandMark m : marks) {
            if (m.getBindingId().equals(id)) {
                return m;
            }
        }
        throw new AssertionError("mark not found: " + id);
    }
}
