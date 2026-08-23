package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * KG因果链遍历器 — 沿知识图谱逐层遍历 CAUSES/AFFECTS/CORRELATES 关系，构建因果链节点。
 *
 * <p>从 CausalReasonerServiceImpl 拆出，职责单一：KG路径遍历 + 节点描述获取。
 */
@Component
public class CausalDetector {

    private static final Logger log = LoggerFactory.getLogger(CausalDetector.class);

    /** KG路径遍历的关系类型 — 因果相关 */
    static final List<String> CAUSAL_RELATION_TYPES = List.of("CAUSES", "AFFECTS", "CORRELATES");

    /** KG路径置信度基准 */
    static final double KG_CONFIDENCE_BASE = 0.80;
    /** 每层深度衰减系数 */
    static final double DEPTH_DECAY = 0.05;

    private final KnowledgeGraphService knowledgeGraphService;

    public CausalDetector(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /**
     * 沿知识图谱逐层遍历因果链。
     *
     * @param result       累积结果（因果链节点追加到 result.causalChain）
     * @param metric       指标名，用于在KG中搜索起始节点
     * @param domain       业务域
     * @param maxDepth     最大深度
     * @param currentDepth 当前深度（起始为1，即指标自身）
     * @param visited      已访问节点ID集合（防环）
     * @return KG遍历覆盖的最大深度（≥currentDepth）
     */
    int traverseKgChain(CausalChainResult result, String metric, String domain,
                        int maxDepth, int currentDepth, Set<String> visited) {
        if (currentDepth >= maxDepth) {
            return currentDepth;
        }

        List<KnowledgeNode> startNodes = knowledgeGraphService.search(metric);
        if (startNodes.isEmpty()) {
            log.debug("KG中未搜索到匹配 '{}' 的节点", metric);
            return currentDepth;
        }

        Deque<String[]> queue = new ArrayDeque<>();
        for (KnowledgeNode node : startNodes) {
            if (visited.add(node.getId())) {
                queue.offer(new String[]{node.getId(), String.valueOf(currentDepth + 1),
                        node.getLabel() != null ? node.getLabel() : metric});
            }
        }

        while (!queue.isEmpty() && currentDepth < maxDepth) {
            String[] entry = queue.poll();
            String nodeId = entry[0];
            int depth = Integer.parseInt(entry[1]);
            String parentDesc = entry[2];

            if (depth > maxDepth) continue;

            Map<String, Object> neighborResult = knowledgeGraphService.getNeighbors(nodeId, 1);
            @SuppressWarnings("unchecked")
            List<KnowledgeEdge> neighbors = (List<KnowledgeEdge>) neighborResult.get("neighbors");

            if (neighbors == null || neighbors.isEmpty()) continue;

            for (KnowledgeEdge edge : neighbors) {
                if (!CAUSAL_RELATION_TYPES.contains(edge.getRelationship().toUpperCase())) {
                    continue;
                }

                String targetId = edge.getTargetNodeId();
                if (!visited.add(targetId)) continue;

                String nodeDesc = getNodeDescription(targetId);
                double confidence = Math.max(0.35, KG_CONFIDENCE_BASE - (depth - 1) * DEPTH_DECAY);

                CausalChainNode chainNode = new CausalChainNode(depth, nodeDesc, confidence, "KG", domain);
                result.getCausalChain().add(chainNode);

                if (depth < maxDepth) {
                    queue.offer(new String[]{targetId, String.valueOf(depth + 1), nodeDesc});
                }
            }

            currentDepth = Math.max(currentDepth, depth);
        }

        return currentDepth;
    }

    /**
     * 获取KG节点描述文本。
     */
    private String getNodeDescription(String nodeId) {
        try {
            Map<String, Object> detail = knowledgeGraphService.getNodeDetail(nodeId);
            if (detail != null) {
                KnowledgeNode node = (KnowledgeNode) detail.get("node");
                if (node != null) {
                    String desc = node.getDescription();
                    if (desc != null && !desc.isEmpty()) return desc;
                    String label = node.getLabel();
                    if (label != null && !label.isEmpty()) return label;
                }
            }
        } catch (Exception e) {
            log.debug("获取节点 {} 详情失败: {}", nodeId, e.getMessage());
        }
        return "节点-" + nodeId.substring(0, Math.min(8, nodeId.length()));
    }
}
