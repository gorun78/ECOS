package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

import java.util.List;

/**
 * Ecos 知识图谱快照 VO（PMO-74 H11-T4）。
 *
 * <p>承接 {@code EcosKnowledgeGraphService#getGraphSnapshot()} 的 Map 结果
 * （接口在 kb-engine-api，签名不可改，故在 Controller 出口收口为强类型）。
 * JSON 键集合固定为 nodes / edges / stats，与原 Map 一致（字段只增不删）。</p>
 */
@Data
public class KgGraphSnapshotVO {

    /** 节点列表（graph_node 投影：code/name/domainId/domainName/type/id/properties） */
    private List<Node> nodes;

    /** 边列表（graph_edge 投影：id/source/target/relationshipType/label） */
    private List<Edge> edges;

    /** 统计（nodeCount/edgeCount/domains） */
    private Stats stats;

    /** 图谱节点视图（键名与 loadNodes() 逐一对齐）。 */
    @Data
    public static class Node {
        private String id;
        private String code;
        private String name;
        private String domainId;
        private String domainName;
        private String type;
        private String properties;
    }

    /** 图谱边视图（键名与 loadEdges() 逐一对齐）。 */
    @Data
    public static class Edge {
        private String id;
        private String source;
        private String target;
        private String relationshipType;
        private String label;
    }

    /** 图谱统计视图（键名与 loadStats() 逐一对齐）。 */
    @Data
    public static class Stats {
        private Integer nodeCount;
        private Integer edgeCount;
        private List<String> domains;
    }
}
