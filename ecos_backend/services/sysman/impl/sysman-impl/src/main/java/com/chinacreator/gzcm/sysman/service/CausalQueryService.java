package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.CausalQueryDao;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * CausalQueryService — Causal 查询服务（PMO-E2: 从 CausalController 下沉的 JdbcTemplate 访问层）。
 * <p>铁律 §2.5：数据访问下沉到 DAO（{@link CausalQueryDao}）。</p>
 *
 * <p><b>#147DE 行面强类型</b>：节点/边行改 record（键与 DAO 行映射逐键一致），
 * 供 {@code CausalController} 图/路径/对比三面直接消费。</p>
 */
@Service
public class CausalQueryService {

    /** 因果节点行（4 键）。 */
    public record CausalNodeRow(@JsonProperty("id") String id,
                                @JsonProperty("name") String name,
                                @JsonProperty("type") String type,
                                @JsonProperty("group") String group) {
    }

    /** 因果边行（5 键）。 */
    public record CausalEdgeRow(@JsonProperty("id") String id,
                                @JsonProperty("source") String source,
                                @JsonProperty("target") String target,
                                @JsonProperty("weight") double weight,
                                @JsonProperty("type") String type) {
    }

    private final CausalQueryDao dao;

    public CausalQueryService(CausalQueryDao dao) {
        this.dao = dao;
    }

    public Integer ping() {
        return dao.ping();
    }

    public List<CausalNodeRow> listNodes() {
        return dao.listNodes().stream().map(CausalQueryService::toNode).toList();
    }

    public List<CausalEdgeRow> listEdges() {
        return dao.listEdges().stream().map(CausalQueryService::toEdge).toList();
    }

    static CausalNodeRow toNode(Map<String, Object> m) {
        return new CausalNodeRow((String) m.get("id"), (String) m.get("name"),
                (String) m.get("type"), (String) m.get("group"));
    }

    static CausalEdgeRow toEdge(Map<String, Object> m) {
        Object weight = m.get("weight");
        return new CausalEdgeRow((String) m.get("id"), (String) m.get("source"),
                (String) m.get("target"), weight instanceof Number n ? n.doubleValue() : 0.0,
                (String) m.get("type"));
    }
}
