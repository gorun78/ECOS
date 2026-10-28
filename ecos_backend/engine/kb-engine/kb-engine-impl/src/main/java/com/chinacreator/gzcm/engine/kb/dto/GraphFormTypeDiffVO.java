package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 按 {@code node_type} 的单行对账（§7.2.3 对账行：按 node_type 比对 PG 与图库计数）。
 *
 * <p>{@code pg} / {@code neo4j} 两侧同口径：{@code node_type} 空值归 {@code UNKNOWN} 桶，
 * 禁把无类型节点静默排除在对账之外。</p>
 */
@Data
public class GraphFormTypeDiffVO {

    /** PG 权威侧该类型节点数。 */
    private Long pg;

    /** 图库投影侧该类型节点数。 */
    private Long neo4j;

    /** 两侧是否同数。 */
    private Boolean matched;
}
