package com.chinacreator.gzcm.engine.ai.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ai.dao.AipPipelineDao;
import com.chinacreator.gzcm.engine.ai.dataobject.AipPipelineDO;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService;
import com.chinacreator.gzcm.engine.ai.template.DefinitionJsonCodec;

/**
 * 逻辑编排「按已存定义执行」服务（2026-11-23 场景智能逻辑承接批）。
 *
 * <h3>为何需要本类（现场缺陷）</h3>
 * <ul>
 *   <li>{@code POST /api/v1/aip/pipelines/{id}/execute} 原实现只把行标记 completed、刷
 *       {@code last_executed_at}，<b>一个节点都不跑</b>；</li>
 *   <li>{@code POST /api/v1/aip/studio/pipelines/{id}/execute} 真跑节点，但节点图取自
 *       <b>请求体</b>（前端画布现发），服务端存的 {@code definition_json} 从未被读。</li>
 * </ul>
 * <p>后果：任何服务端调用方（场景运行、定时、CLI）都无法按"已保存的编排"执行。
 * 本类补上这一段：<b>图取自 definition_json</b>，执行复用
 * {@link AgentStudioService} 的六类节点实现（节点语义单源，不在本类复制一份）。</p>
 *
 * <h3>definition_json 形态（V329 画布快照）</h3>
 * <pre>{@code
 * { "steps": { "blocks": [], "graph": { "nodes": [{id,type,position,data:{type,label,config}}],
 *                                        "edges": [{id,source,target,data}] } } }
 * }</pre>
 * <p>解析容忍 {@code type/config/label} 落在 {@code data} 内（与画布快照一致），也容忍顶层直给。</p>
 *
 * <h3>真值纪律</h3>
 * <ul>
 *   <li>无节点图 ⇒ 明确失败（{@code AIP-EXEC-01}），<b>不</b>落"空跑成功"；画布为空是用户负输入，
 *       不是成功执行；</li>
 *   <li>环检测失败 ⇒ 400（{@code AIP-EXEC-02}），不做猜测性排序；</li>
 *   <li>等终态超时 ⇒ 如实 {@code TIMEOUT}，禁折成 completed；</li>
 *   <li>返回体带节点级轨迹与失败原因原文，调用方（场景分析锚点）据此判 degraded。</li>
 * </ul>
 */
@Service
public class AipPipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AipPipelineExecutionService.class);

    /** 等终态默认预算（与认知域"推理不能 >30s"同档；编排可能串多节点，取两倍）。 */
    public static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private static final long POLL_INTERVAL_MS = 100L;

    private final AipPipelineDao pipelineDao;
    private final AgentStudioService studioService;

    public AipPipelineExecutionService(AipPipelineDao pipelineDao, AgentStudioService studioService) {
        this.pipelineDao = pipelineDao;
        this.studioService = studioService;
    }

    /** 执行结果（供 Controller 出参与场景分析锚点消费）。 */
    public record Result(String pipelineId,
                         String executionId,
                         String status,
                         boolean degraded,
                         int executedNodes,
                         List<String> nodeOrder,
                         Map<String, Object> nodeTraces,
                         Map<String, Object> outputs,
                         String message) {
    }

    /**
     * 按已存定义执行一次编排（同步等到终态或超时）。
     *
     * @param tenantId      租户（编排归属）
     * @param pipelineId    编排 id
     * @param inputs        运行入参（节点可用 prompt/业务键；非节点图字段）
     * @param timeoutMillis 等终态预算（≤0 取 {@link #DEFAULT_TIMEOUT_MS}）
     */
    public Result execute(String tenantId, String pipelineId, Map<String, Object> inputs,
                          long timeoutMillis) {
        AipPipelineDO row = pipelineDao.findById(tenantId, pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("AIP-EXEC-404: 编排不存在: " + pipelineId));
        List<Map<String, Object>> nodes = nodeGraphOf(row.getDefinitionJson());
        if (nodes.isEmpty()) {
            // IllegalArgumentException → AiErrorAdvice 归 INVALID_ARGUMENT（400）：
            // 画布为空是调用方负输入，不是服务端故障，禁落 502。
            throw new IllegalArgumentException("AIP-EXEC-01: 编排 " + pipelineId
                    + " 无节点图（definition_json.steps.graph.nodes 为空），拒绝空跑成功");
        }
        List<String> orderedIds = topoOrder(nodes, edgesOf(row.getDefinitionJson()));
        // studio 执行器按列表顺序串行跑，故先按拓扑序重排 nodes（否则画布保存顺序即执行顺序）
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> n : nodes) {
            byId.put(String.valueOf(n.get("id")), n);
        }
        List<Map<String, Object>> orderedNodes = new ArrayList<>();
        for (String id : orderedIds) {
            orderedNodes.add(byId.get(id));
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (inputs != null) {
            params.putAll(inputs);
        }
        params.put("nodes", orderedNodes);

        long budget = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TIMEOUT_MS;
        AgentStudioService.PipelineExecution started = studioService.startPipeline(pipelineId, params);
        AgentStudioService.PipelineExecution finalExec = awaitTerminal(started.getExecutionId(), budget);

        String status = finalExec == null ? "TIMEOUT" : finalExec.getStatus();
        boolean degraded = !"completed".equals(status);
        Map<String, Object> traces = new LinkedHashMap<>();
        Map<String, Object> outputs = new LinkedHashMap<>();
        int executed = 0;
        if (finalExec != null) {
            finalExec.getNodeTraces().forEach((nodeId, trace) -> {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("status", trace.getStatus());
                t.put("latencyMs", trace.getLatencyMs());
                if (trace.getErrorMessage() != null) t.put("error", trace.getErrorMessage());
                if (trace.getOutputJson() != null) t.put("output", trace.getOutputJson());
                traces.put(nodeId, t);
            });
            int i = 0;
            for (String nodeId : orderedIds) {
                var trace = finalExec.getNodeTraces().get(nodeId);
                if (trace != null && "success".equals(trace.getStatus())) {
                    outputs.put(nodeId, trace.getOutputJson());
                    i++;
                }
            }
            executed = i;
        }
        String message = finalExec == null
                ? "编排执行超时（" + budget + "ms）未到终态，状态未知，不视为成功"
                : ("completed".equals(status)
                    ? "编排执行完成（" + executed + " 节点成功）"
                    : "编排执行失败，见 nodeTraces 失败节点原因");
        log.info("AIP 编排执行 pipeline={} exec={} status={} nodes={}/{}",
                pipelineId, started.getExecutionId(), status, executed, orderedIds.size());
        return new Result(pipelineId, started.getExecutionId(), status, degraded, executed,
                orderedIds, traces, outputs, message);
    }

    // ── definition_json → 画布 payload ────────────────────────────────

    /** 取 definition_json.steps.graph.nodes，并归一成 AgentStudioService 认识的顶层 {id,type,label,config}。 */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> nodeGraphOf(String definitionJson) {
        Object graph = graphOf(definitionJson);
        Object raw = graph instanceof Map<?, ?> gm ? gm.get("nodes") : null;
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> node = new LinkedHashMap<>();
            Object id = m.get("id");
            if (id == null) continue;
            node.put("id", String.valueOf(id));
            Object data = m.get("data");
            Map<String, Object> dm = data instanceof Map<?, ?> d ? (Map<String, Object>) d : Map.of();
            Object type = dm.get("type") != null ? dm.get("type") : m.get("type");
            node.put("type", type == null ? "trigger" : String.valueOf(type));
            Object label = dm.get("label") != null ? dm.get("label") : m.get("label");
            node.put("label", label == null ? String.valueOf(id) : String.valueOf(label));
            Object config = dm.get("config");
            node.put("config", config instanceof Map<?, ?> c ? (Map<String, Object>) c : Map.of());
            out.add(node);
        }
        return out;
    }

    /** 取 edges 的 (source,target) 对；缺省空列表（单节点图无需边）。 */
    @SuppressWarnings("unchecked")
    static List<String[]> edgesOf(String definitionJson) {
        Object graph = graphOf(definitionJson);
        Object raw = graph instanceof Map<?, ?> gm ? gm.get("edges") : null;
        List<String[]> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Object s = m.get("source");
            Object t = m.get("target");
            if (s != null && t != null) {
                out.add(new String[]{String.valueOf(s), String.valueOf(t)});
            }
        }
        return out;
    }

    private static Object graphOf(String definitionJson) {
        Map<String, Object> def = DefinitionJsonCodec.read(definitionJson);
        Object steps = def.get("steps");
        if (!(steps instanceof Map<?, ?> sm)) return null;
        // 画布快照形态：steps.graph.{nodes,edges}
        return sm.get("graph");
    }

    /**
     * Kahn 拓扑排序（保持原始顺序作同层 tie-break）。
     *
     * @throws IllegalStateException {@code AIP-EXEC-02} 存在环或边指向未知节点
     */
    static List<String> topoOrder(List<Map<String, Object>> nodes, List<String[]> edges) {
        List<String> ids = new ArrayList<>();
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Map<String, Object> n : nodes) {
            String id = String.valueOf(n.get("id"));
            if (index.containsKey(id)) {
                throw new IllegalArgumentException("AIP-EXEC-02: 节点 id 重复: " + id);
            }
            index.put(id, ids.size());
            ids.add(id);
        }
        Map<String, List<String>> indeg = new LinkedHashMap<>();
        Map<String, List<String>> adj = new LinkedHashMap<>();
        for (String id : ids) {
            indeg.put(id, new ArrayList<>());
            adj.put(id, new ArrayList<>());
        }
        for (String[] e : edges) {
            if (!index.containsKey(e[0]) || !index.containsKey(e[1])) {
                throw new IllegalArgumentException("AIP-EXEC-02: 边指向未知节点: " + e[0] + " → " + e[1]);
            }
            adj.get(e[0]).add(e[1]);
            indeg.get(e[1]).add(e[0]);
        }
        List<String> order = new ArrayList<>();
        boolean[] done = new boolean[ids.size()];
        while (order.size() < ids.size()) {
            String next = null;
            for (String id : ids) { // 原始顺序扫，保证同层确定性
                if (!done[index.get(id)] && indeg.get(id).stream().allMatch(p -> done[index.get(p)])) {
                    next = id;
                    break;
                }
            }
            if (next == null) {
                throw new IllegalArgumentException("AIP-EXEC-02: 节点图存在环，无法确定执行顺序");
            }
            done[index.get(next)] = true;
            order.add(next);
        }
        return order;
    }

    private AgentStudioService.PipelineExecution awaitTerminal(String executionId, long budgetMillis) {
        long deadline = System.currentTimeMillis() + budgetMillis;
        while (System.currentTimeMillis() < deadline) {
            AgentStudioService.PipelineExecution exec = studioService.getPipelineStatus(executionId);
            if (exec != null && ("completed".equals(exec.getStatus()) || "failed".equals(exec.getStatus()))) {
                return exec;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
