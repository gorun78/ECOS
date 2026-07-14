package com.chinacreator.gzcm.cognitive.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.cognitive.model.CausalNode;
import com.chinacreator.gzcm.cognitive.model.CausalPath;
import com.chinacreator.gzcm.cognitive.model.RootCause;

/**
 * 因果推理器 — 基于因果图的根因分析引擎。
 *
 * <p>核心能力：
 * <ul>
 *   <li>因果图维护：事件 → 原因链的有向图</li>
 *   <li>路径追踪：BFS/DFS 遍历因果图，从事件反向追溯根因</li>
 *   <li>根因定位：叶子节点（无进一步原因的节点）标记为 rootCause</li>
 *   <li>阈值剪枝：置信度低于阈值的边自动忽略</li>
 *   <li>多路径发现：支持从同一事件发散的多条因果链</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 *   Map<String, List<CausalEdge>> graph = ...;
 *   CausalPath path = causalReasoner.traceCause("service_timeout", graph, 5, 0.6);
 *   List<RootCause> rootCauses = causalReasoner.findRootCauses("service_timeout", graph, 0.6);
 * }</pre>
 *
 * <p>零外部依赖，仅依赖 cognitive-api 中的 model 类和 SLF4J 日志。
 */
@Service("causalReasoner")
public class CausalReasoner {

    private static final Logger log = LoggerFactory.getLogger(CausalReasoner.class);

    /** 默认最大遍历深度 */
    public static final int DEFAULT_MAX_DEPTH = 5;

    /** 默认置信度阈值（0 表示不剪枝） */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.0;

    // ═══════════════════════════════════════════════════
    // 公共 API
    // ═══════════════════════════════════════════════════

    /**
     * 从指定事件出发，追溯置信度最高的因果路径。
     *
     * @param event  起始事件/症状节点 ID
     * @param graph  因果图：key=节点ID，value=从该节点出发的因果边列表
     * @return 最高置信度的因果路径；若图为空或事件不存在则返回空路径
     */
    public CausalPath traceCause(String event, Map<String, List<CausalEdge>> graph) {
        return traceCause(event, graph, DEFAULT_MAX_DEPTH, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * 从指定事件出发，追溯置信度最高的因果路径。
     *
     * @param event    起始事件/症状节点 ID
     * @param graph    因果图
     * @param maxDepth 最大遍历深度（0 表示仅当前节点，&lt;1 自动修正为默认值）
     * @return 最高置信度的因果路径
     */
    public CausalPath traceCause(String event, Map<String, List<CausalEdge>> graph, int maxDepth) {
        return traceCause(event, graph, maxDepth, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * 从指定事件出发，追溯置信度最高的因果路径（带阈值剪枝）。
     *
     * @param event              起始事件/症状节点 ID
     * @param graph              因果图
     * @param maxDepth           最大遍历深度
     * @param confidenceThreshold 置信度阈值：低于此值的边将被忽略（0~1）
     * @return 最高置信度的因果路径；无可达路径时返回仅含起始节点的路径
     */
    public CausalPath traceCause(String event, Map<String, List<CausalEdge>> graph,
                                  int maxDepth, double confidenceThreshold) {
        List<CausalPath> allPaths = traceAllPaths(event, graph, maxDepth, confidenceThreshold);

        if (allPaths.isEmpty()) {
            log.debug("No causal paths found from event '{}' (graph={}, threshold={})",
                    event, graph != null ? graph.size() : 0, confidenceThreshold);
            return buildEmptyPath(event);
        }

        // 按总置信度降序，返回最优路径
        allPaths.sort(Comparator.comparing(CausalPath::getTotalConfidence,
                Comparator.nullsLast(Comparator.reverseOrder())));

        CausalPath best = allPaths.get(0);
        log.debug("Best causal path for '{}': {} nodes, confidence={}",
                event, best.getPathLength(), best.getTotalConfidence());
        return best;
    }

    /**
     * 发现从事件出发的所有因果路径（BFS 变体，支持分支）。
     *
     * @param event              起始事件/症状节点 ID
     * @param graph              因果图
     * @param maxDepth           最大遍历深度
     * @param confidenceThreshold 置信度阈值
     * @return 所有因果路径列表（按发现顺序）
     */
    public List<CausalPath> traceAllPaths(String event, Map<String, List<CausalEdge>> graph,
                                           int maxDepth, double confidenceThreshold) {
        if (graph == null || graph.isEmpty()) {
            log.debug("Causal graph is null or empty, returning empty path list");
            return Collections.emptyList();
        }

        int depth = (maxDepth < 1) ? DEFAULT_MAX_DEPTH : maxDepth;
        double threshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));

        List<CausalPath> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        LinkedList<CausalNode> currentPath = new LinkedList<>();

        // DFS 从事件节点出发，沿因果边反向追溯
        dfsTrace(event, graph, depth, 0, threshold, 1.0,
                visited, currentPath, results);

        log.debug("traceAllPaths: event='{}', depth={}, threshold={}, pathsFound={}",
                event, depth, threshold, results.size());
        return results;
    }

    /**
     * 发现所有根因节点。
     *
     * <p>根因定义为：在因果图中作为叶子节点出现的节点（无出边或出边均被阈值剪枝）。
     *
     * @param event              起始事件
     * @param graph              因果图
     * @param confidenceThreshold 置信度阈值
     * @return 根因列表；无根因时返回空列表
     */
    public List<RootCause> findRootCauses(String event, Map<String, List<CausalEdge>> graph,
                                           double confidenceThreshold) {
        if (graph == null || graph.isEmpty() || event == null) {
            return Collections.emptyList();
        }

        double threshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
        List<RootCause> rootCauses = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        collectRootCauses(event, graph, DEFAULT_MAX_DEPTH, 0, threshold, 1.0, rootCauses, seen);

        log.debug("findRootCauses: event='{}', threshold={}, rootsFound={}",
                event, threshold, rootCauses.size());
        return rootCauses;
    }

    /**
     * 便捷方法：使用默认阈值发现根因。
     */
    public List<RootCause> findRootCauses(String event, Map<String, List<CausalEdge>> graph) {
        return findRootCauses(event, graph, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * 计算从事件出发可达的因果图节点总数（含事件本身）。
     * 用于填充 {@code ReasonResponse.kgNodesVisited}。
     *
     * @param event              起始事件
     * @param graph              因果图
     * @param confidenceThreshold 置信度阈值
     * @return 访问的知识图谱节点数
     */
    public int countReachableNodes(String event, Map<String, List<CausalEdge>> graph,
                                    double confidenceThreshold) {
        if (graph == null || graph.isEmpty() || event == null) {
            return 0;
        }
        Set<String> visited = new HashSet<>();
        double threshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
        countReachable(event, graph, DEFAULT_MAX_DEPTH, 0, threshold, visited);
        return visited.size();
    }

    // ═══════════════════════════════════════════════════
    // 核心算法: DFS 路径追踪
    // ═══════════════════════════════════════════════════

    /**
     * 深度优先遍历因果图，收集所有因果路径。
     *
     * @param nodeId       当前节点 ID
     * @param graph        因果图
     * @param maxDepth     最大深度
     * @param currentDepth 当前深度（从 0 开始）
     * @param threshold    置信度阈值
     * @param pathConfidence 沿路径累积的置信度（连乘）
     * @param visited      本路径已访问节点（防环）
     * @param pathNodes    当前路径节点栈（从事件→原因的方向）
     * @param results      输出：收集到的因果路径
     */
    private void dfsTrace(String nodeId,
                           Map<String, List<CausalEdge>> graph,
                           int maxDepth,
                           int currentDepth,
                           double threshold,
                           double pathConfidence,
                           Set<String> visited,
                           LinkedList<CausalNode> pathNodes,
                           List<CausalPath> results) {

        // 防环 & 防重复
        if (visited.contains(nodeId)) {
            return;
        }
        visited.add(nodeId);

        // 创建当前节点
        CausalNode node = new CausalNode();
        node.setNodeId(nodeId);
        node.setLabel(nodeId); // 默认 label=nodeId，可被边上的 label 覆盖
        pathNodes.addLast(node);

        // 获取从当前节点出发的因果边（过滤掉低于阈值的）
        List<CausalEdge> edges = filterEdges(graph.get(nodeId), threshold);

        if (edges.isEmpty() || currentDepth >= maxDepth) {
            // 到达叶子节点或最大深度 → 生成路径
            CausalPath path = buildPathFromStack(pathNodes, pathConfidence);
            results.add(path);
        } else {
            // 沿每条边继续深搜
            for (CausalEdge edge : edges) {
                double childConfidence = pathConfidence * edge.confidence;
                // 更新子节点 label（取自边的 label）
                dfsTraceWithEdge(edge, graph, maxDepth, currentDepth + 1,
                        threshold, childConfidence, visited, pathNodes, results);
            }
        }

        // 回溯
        pathNodes.removeLast();
        visited.remove(nodeId);
    }

    /**
     * 沿一条因果边继续 DFS，处理边上的 label 信息。
     */
    private void dfsTraceWithEdge(CausalEdge edge,
                                   Map<String, List<CausalEdge>> graph,
                                   int maxDepth,
                                   int currentDepth,
                                   double threshold,
                                   double pathConfidence,
                                   Set<String> visited,
                                   LinkedList<CausalNode> pathNodes,
                                   List<CausalPath> results) {
        if (visited.contains(edge.to)) {
            return;
        }
        visited.add(edge.to);

        CausalNode node = new CausalNode();
        node.setNodeId(edge.to);
        node.setLabel(edge.label != null && !edge.label.isBlank() ? edge.label : edge.to);
        pathNodes.addLast(node);

        List<CausalEdge> childEdges = filterEdges(graph.get(edge.to), threshold);

        if (childEdges.isEmpty() || currentDepth >= maxDepth) {
            CausalPath path = buildPathFromStack(pathNodes, pathConfidence);
            results.add(path);
        } else {
            for (CausalEdge childEdge : childEdges) {
                double childConfidence = pathConfidence * childEdge.confidence;
                dfsTraceWithEdge(childEdge, graph, maxDepth, currentDepth + 1,
                        threshold, childConfidence, visited, pathNodes, results);
            }
        }

        pathNodes.removeLast();
        visited.remove(edge.to);
    }

    // ═══════════════════════════════════════════════════
    // 根因收集
    // ═══════════════════════════════════════════════════

    /**
     * DFS 遍历收集根因节点。
     */
    private void collectRootCauses(String nodeId,
                                    Map<String, List<CausalEdge>> graph,
                                    int maxDepth,
                                    int currentDepth,
                                    double threshold,
                                    double pathConfidence,
                                    List<RootCause> rootCauses,
                                    Set<String> seen) {
        if (seen.contains(nodeId) || currentDepth > maxDepth) {
            return;
        }
        seen.add(nodeId);

        List<CausalEdge> edges = filterEdges(graph.get(nodeId), threshold);

        if (edges.isEmpty()) {
            // 叶子节点 → 根因
            RootCause rc = new RootCause();
            rc.setNodeId(nodeId);
            rc.setNodeLabel(nodeId);
            rc.setConfidence(pathConfidence);
            rc.setImpactScore(computeImpactScore(pathConfidence, currentDepth));
            rootCauses.add(rc);
        } else {
            for (CausalEdge edge : edges) {
                collectRootCauses(edge.to, graph, maxDepth, currentDepth + 1,
                        threshold, pathConfidence * edge.confidence, rootCauses, seen);
            }
        }
    }

    /**
     * 统计可达节点数。
     */
    private void countReachable(String nodeId,
                                 Map<String, List<CausalEdge>> graph,
                                 int maxDepth,
                                 int currentDepth,
                                 double threshold,
                                 Set<String> visited) {
        if (visited.contains(nodeId) || currentDepth > maxDepth) {
            return;
        }
        visited.add(nodeId);
        List<CausalEdge> edges = filterEdges(graph.get(nodeId), threshold);
        for (CausalEdge edge : edges) {
            countReachable(edge.to, graph, maxDepth, currentDepth + 1, threshold, visited);
        }
    }

    // ═══════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════

    /**
     * 过滤因果边：剔除置信度低于阈值的边。
     */
    private List<CausalEdge> filterEdges(List<CausalEdge> edges, double threshold) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }
        if (threshold <= 0.0) {
            return new ArrayList<>(edges);
        }
        List<CausalEdge> filtered = new ArrayList<>();
        for (CausalEdge edge : edges) {
            if (edge.confidence != null && edge.confidence >= threshold) {
                filtered.add(edge);
            }
        }
        return filtered;
    }

    /**
     * 从路径节点栈构建 CausalPath。
     *
     * <p>pathNodes 的顺序是「事件 → 原因 → … → 根因」（DFS 入栈顺序）。
     * CausalPath.nodes 要求「从因到果」，因此需要逆序。
     */
    private CausalPath buildPathFromStack(LinkedList<CausalNode> pathNodes, double totalConfidence) {
        CausalPath path = new CausalPath();
        path.setPathId("cp-" + UUID.randomUUID().toString().substring(0, 8));

        // 逆序：根因 → … → 事件（从因到果）
        List<CausalNode> reversed = new ArrayList<>(pathNodes);
        Collections.reverse(reversed);
        path.setNodes(reversed);
        path.setTotalConfidence(roundConfidence(totalConfidence));
        path.setPathLength(reversed.size());
        return path;
    }

    /**
     * 构建仅含起始节点的空路径（无因果边可达时）。
     */
    private CausalPath buildEmptyPath(String event) {
        CausalPath path = new CausalPath();
        path.setPathId("cp-empty-" + UUID.randomUUID().toString().substring(0, 8));

        CausalNode node = new CausalNode();
        node.setNodeId(event);
        node.setLabel(event);
        path.setNodes(Collections.singletonList(node));
        path.setTotalConfidence(1.0);
        path.setPathLength(1);
        return path;
    }

    /**
     * 计算影响力评分：基于置信度和深度。
     *
     * <p>越靠近根因（深度越大）、路径整体置信度越高，影响力越大。
     */
    private Double computeImpactScore(double confidence, int depth) {
        if (depth == 0) return confidence;
        // 深度惩罚因子：深度越深离原始事件越远，但根因性越强
        double depthFactor = Math.min(1.0, depth / (double) DEFAULT_MAX_DEPTH);
        return roundConfidence(confidence * (0.5 + 0.5 * depthFactor));
    }

    /**
     * 置信度四舍五入到小数点后 4 位，避免浮点累积误差。
     */
    private double roundConfidence(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    // ═══════════════════════════════════════════════════
    // 内部类: CausalEdge — 因果边
    // ═══════════════════════════════════════════════════

    /**
     * 因果图中的有向边，表示「from → to」的因果关系。
     *
     * <p>示例：{@code new CausalEdge("service_timeout", "db_slow_query", "数据库慢查询", 0.85)}
     * 表示「数据库慢查询」导致「服务超时」，置信度 85%。
     */
    public static class CausalEdge {

        /** 起因节点 ID */
        private String from;

        /** 结果节点 ID（更深层的原因） */
        private String to;

        /** 边的描述标签 */
        private String label;

        /** 置信度 0~1 */
        private Double confidence;

        public CausalEdge() {
        }

        public CausalEdge(String from, String to, String label, Double confidence) {
            this.from = from;
            this.to = to;
            this.label = label;
            this.confidence = confidence;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return "CausalEdge{" +
                    "from='" + from + '\'' +
                    ", to='" + to + '\'' +
                    ", label='" + label + '\'' +
                    ", confidence=" + confidence +
                    '}';
        }
    }
}
