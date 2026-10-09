package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整度/孤岛读侧装载器（卷07 F07-02/04）。
 * <p>从既有 {@link WorkspaceDataDao} 载入绑定 + 连边并转成计算视图；Mapper 收口（F07-19）后由
 * {@code ScenarioBindingMapper}/{@code BindingLinkMapper} 替换本装载器的内部实现，接口不变。</p>
 */
@Component
public class CompletenessDataLoader {

    private final WorkspaceDataDao dao;

    public CompletenessDataLoader(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    public List<BindingNode> bindingsOf(String scenarioId) {
        List<ScenarioBinding> rows = dao.listBindingsByScenario(scenarioId);
        List<BindingNode> nodes = new ArrayList<>(rows.size());
        for (ScenarioBinding b : rows) {
            nodes.add(new BindingNode(b.getId(), effectiveName(b), BindingType.fromNullable(b.getBindingType())));
        }
        return nodes;
    }

    public List<LinkEdge> linksOf(String scenarioId) {
        List<ScenarioBindingLinkService.ScenarioBindingLinkVO> rows = dao.listLinksOfScenario(scenarioId);
        List<LinkEdge> edges = new ArrayList<>(rows.size());
        for (ScenarioBindingLinkService.ScenarioBindingLinkVO l : rows) {
            EdgeType type = EdgeType.fromNullable(l.getLinkType());
            if (type == null) {
                continue;
            }
            edges.add(new LinkEdge(l.getSourceBindingId(), l.getTargetBindingId(), type));
        }
        return edges;
    }

    private String effectiveName(ScenarioBinding b) {
        if (b.getTargetId() != null && !b.getTargetId().isBlank()) {
            return b.getTargetId();
        }
        return b.getTargetRef();
    }
}
