package com.chinacreator.gzcm.workspace.scenario;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 孤岛判定（卷07 F07-04）。判定依据是**连边可达性**而非节点类型（破 X-27 只数 type=="resource"）。
 * <p>一个绑定是孤岛，当且仅当它在 (绑定集, 连边集) 图上对主链不可达——即与任何其它绑定之间不存在任何边
 * （必需边或可选边均计入可达）。DRAFT 态允许保存但打标；DRAFT→ACTIVE 迁移存在孤岛一律 409。</p>
 */
@Component
public class IslandBindingGuard {

    /** 计算孤岛集合（供完整度响应 islands[] 与激活守卫复用，同一算法单点）。 */
    public List<CompletenessReport.IslandView> findIslands(List<BindingNode> bindings, List<LinkEdge> links) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        Set<String> incident = new HashSet<>();
        Map<String, Integer> degree = new HashMap<>();
        Set<String> boundIds = new HashSet<>();
        for (BindingNode b : bindings) {
            boundIds.add(b.getBindingId());
        }
        if (links != null) {
            for (LinkEdge e : links) {
                boolean sOk = boundIds.contains(e.getSourceBindingId());
                boolean tOk = boundIds.contains(e.getTargetBindingId());
                if (!sOk || !tOk || e.getSourceBindingId().equals(e.getTargetBindingId())) {
                    continue;
                }
                incident.add(e.getSourceBindingId());
                incident.add(e.getTargetBindingId());
                degree.merge(e.getSourceBindingId(), 1, Integer::sum);
                degree.merge(e.getTargetBindingId(), 1, Integer::sum);
            }
        }
        List<CompletenessReport.IslandView> islands = new ArrayList<>();
        for (BindingNode b : bindings) {
            if (!incident.contains(b.getBindingId())) {
                islands.add(new CompletenessReport.IslandView(
                        b.getBindingId(), b.getName(),
                        b.getType() == null ? null : b.getType().name(),
                        IslandReason.NO_REACHABLE_EDGE));
            }
        }
        return islands;
    }

    /** 岛标持久化输入（供 ScenarioBindingMapper.markIslands 写回 is_island/island_reason）。 */
    public static final class IslandMark {
        private final String bindingId;
        private final boolean island;
        private final String reason;

        public IslandMark(String bindingId, boolean island, String reason) {
            this.bindingId = bindingId;
            this.island = island;
            this.reason = reason;
        }

        public String getBindingId() {
            return bindingId;
        }

        public boolean isIsland() {
            return island;
        }

        public String getReason() {
            return reason;
        }
    }

    /** DRAFT 保存期的岛标计算（true=孤岛），主链以“存在任意连边”为基准。 */
    public List<IslandMark> markIslands(List<BindingNode> bindings, List<LinkEdge> links) {
        Set<String> islandIds = new HashSet<>();
        for (CompletenessReport.IslandView v : findIslands(bindings, links)) {
            islandIds.add(v.getBindingId());
        }
        List<IslandMark> marks = new ArrayList<>();
        for (BindingNode b : bindings) {
            boolean island = islandIds.contains(b.getBindingId());
            marks.add(new IslandMark(b.getBindingId(), island, island ? IslandReason.NO_REACHABLE_EDGE : null));
        }
        return marks;
    }

    /** 激活守卫：存在孤岛即抛 409 ISLAND_BINDING（真实 HTTP 状态码依赖 F07-13 advice）。 */
    public void assertNoIslands(List<BindingNode> bindings, List<LinkEdge> links) {
        List<CompletenessReport.IslandView> islands = findIslands(bindings, links);
        if (!islands.isEmpty()) {
            List<Map<String, Object>> payload = new ArrayList<>(islands.size());
            for (CompletenessReport.IslandView v : islands) {
                Map<String, Object> m = new HashMap<>();
                m.put("bindingId", v.getBindingId());
                m.put("name", v.getName());
                m.put("bindingType", v.getBindingType());
                m.put("reason", v.getReason());
                payload.add(m);
            }
            throw new IslandBindingException(payload);
        }
    }

    /** 孤岛原因常量（单点）。 */
    public static final class IslandReason {
        public static final String NO_REACHABLE_EDGE = "NO_REACHABLE_EDGE";

        private IslandReason() {
        }
    }
}
