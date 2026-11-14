package com.chinacreator.gzcm.sysman.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Causal 行类型载体（原查询服务，2026-11-13 D48 裁决后只剩行类型）。
 *
 * <p><b>为什么只剩 record</b>：本类原含 {@code ping/listNodes/listEdges} 三个方法，
 * 经 {@code CausalQueryDao} 读 {@code ecos_causal_node}/{@code ecos_causal_edge}。
 * 这两张表全仓无 DDL、库中零命中，于是 {@code GET /api/v1/causal/graph} 恒 500（D48）。
 * 同族的 {@code /paths} 与 {@code /compare} 本就走内存种子，前端生产代码也不消费
 * 本前缀（真图在 {@code /api/v1/ecos/world-model-graph/causal-graph}）。
 * 裁决：删掉那条读不到的库腿与 DAO，整族统一读内存，行类型留在此处供三个面共用。</p>
 *
 * <p><b>保留 {@code @Service} 无 Bean 冲突风险</b>：本类已无字段与方法，
 * 不注入任何依赖；若将来给 {@code /causal/*} 接真实数据源，应在此处新增
 * Service 方法（而不是在 Controller 里直连 DAO，铁律 §2.5）。</p>
 */
public final class CausalQueryService {

    private CausalQueryService() {
    }

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
}
