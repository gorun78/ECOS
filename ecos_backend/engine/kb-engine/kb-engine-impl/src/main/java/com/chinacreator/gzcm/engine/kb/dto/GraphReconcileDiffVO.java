package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.Map;

/**
 * PG↔图库对账明细（附录 A 第 15 条 {@code reconcileDiff} / {@code diff}）。
 *
 * <p>三态语义：{@code matched=true} 一致；{@code false} 已确认漂移（带逐类型差异）；
 * {@code null} = 投影端读数不可得（<b>未知</b>，禁写成 {@code false} 冒充已知不一致）。</p>
 */
@Data
public class GraphReconcileDiffVO {

    /** PG 权威侧节点数。 */
    private Long nodesPg;

    /** 图库投影侧节点数（读数不可得为 {@code null}）。 */
    private Long nodesNeo4j;

    /** 节点总数是否一致（读数不可得为 {@code null}）。 */
    private Boolean nodesMatched;

    /** PG 权威侧边数。 */
    private Long edgesPg;

    /** 图库投影侧边数（读数不可得为 {@code null}）。 */
    private Long edgesNeo4j;

    /** 边总数是否一致（读数不可得为 {@code null}）。 */
    private Boolean edgesMatched;

    /** 因端点缺失未能投影的边数（{@code edgesPg - edgesNeo4j} 的正值部分）。 */
    private Long edgeSkipped;

    /** 按 {@code node_type} 的逐类型对账（键 = 类型码，含 {@code UNKNOWN} 桶）。 */
    private Map<String, GraphFormTypeDiffVO> byType;

    /** 总账是否一致：总数与逐类型全部相同才为 {@code true}。 */
    private Boolean matched;
}
