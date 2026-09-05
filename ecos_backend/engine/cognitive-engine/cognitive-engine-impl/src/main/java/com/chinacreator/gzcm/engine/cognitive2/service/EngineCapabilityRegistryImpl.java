package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode;
import com.chinacreator.gzcm.engine.cognitive2.model.NodeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 引擎能力注册表实现 — KAG 推理链环节 → 可编排节点的跨引擎能力映射。
 *
 * <p>每个 NodeType 对应一个引擎能力调用（跨引擎走 REST）：</p>
 * <ul>
 *   <li>INGEST  → kb-engine 文档解析（POST /api/v1/knowledge/ingest）</li>
 *   <li>EXTRACT → kb-engine 知识抽取（POST /api/v1/knowledge/extract）</li>
 *   <li>KG      → kb-engine 建图（POST /api/v1/knowledge/graph/build）</li>
 *   <li>REASON  → cognitive-engine 混合推理（POST /api/v1/knowledge/reason）</li>
 *   <li>DECISION → PMO-32 决策落地（本地 Service 调用，不走 REST）</li>
 *   <li>OAG_INTAKE   — Wave-3.2 新增，本地 OagIntakeService（需求解读）</li>
 *   <li>OAG_PLAN     — Wave-3.2 新增，本地 OagPlannerService（任务拆解）</li>
 *   <li>OAG_STRATEGY — Wave-3.2 新增，本地 StrategyGeneratorService（策略生成）</li>
 * </ul>
 */
@Service
public class EngineCapabilityRegistryImpl implements EngineCapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(EngineCapabilityRegistryImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Gateway 内部调用基址 */
    private static final String INTERNAL_BASE = "http://localhost:8080";

    private final DecisionService decisionService;
    private final OagIntakeService oagIntakeService;
    private final OagPlannerService oagPlannerService;
    private final StrategyGeneratorService strategyGeneratorService;
    private final RestTemplate restTemplate;

    @Autowired
    public EngineCapabilityRegistryImpl(DecisionService decisionService,
                                         OagIntakeService oagIntakeService,
                                         OagPlannerService oagPlannerService,
                                         StrategyGeneratorService strategyGeneratorService) {
        this.decisionService = decisionService;
        this.oagIntakeService = oagIntakeService;
        this.oagPlannerService = oagPlannerService;
        this.strategyGeneratorService = strategyGeneratorService;
        this.restTemplate = new RestTemplate();
        // 防 XML 响应解析
        this.restTemplate.getMessageConverters().removeIf(c ->
            c instanceof org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter);
    }

    @Override
    public Map<String, Object> executeNode(CognitivePipelineNode node, Map<String, Object> context) throws Exception {
        NodeType type = node.getNodeType();
        log.info("Executing node: {} type={}", node.getNodeId(), type);

        Map<String, Object> config = parseConfig(node.getConfig());

        return switch (type) {
            case DECISION     -> executeDecision(node, config, context);
            case REASON       -> executeReason(node, config, context);
            case EXTRACT      -> executeExtract(node, config, context);
            case KG           -> executeKg(node, config, context);
            case INGEST       -> executeIngest(node, config, context);
            case OAG_INTAKE   -> executeOagIntake(node, config, context);
            case OAG_PLAN     -> executeOagPlan(node, config, context);
            case OAG_STRATEGY -> executeOagStrategy(node, config, context);
        };
    }

    // ════════════════════════════════════════════════════
    //  DECISION — 本地调用 PMO-32 DecisionService
    // ════════════════════════════════════════════════════

    private Map<String, Object> executeDecision(CognitivePipelineNode node, Map<String, Object> config,
                                                Map<String, Object> context) {
        String category = (String) config.getOrDefault("category", "pipeline");
        String scenario = (String) config.getOrDefault("scenario", context.toString());
        String reasoning = (String) config.getOrDefault("reasoning", "");
        String outcome = (String) config.getOrDefault("outcome", "pending");
        double confidence = config.get("confidence") != null
            ? ((Number) config.get("confidence")).doubleValue() : 0.7;
        String decisionMaker = (String) config.getOrDefault("decisionMaker", "cognitive-pipeline");

        String decisionId = decisionService.recordDecision(
            category, scenario, reasoning, outcome, confidence, decisionMaker
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decisionId", decisionId);
        result.put("nodeId", node.getNodeId());
        result.put("status", "recorded");
        return result;
    }

    // ════════════════════════════════════════════════════
    //  REASON — 调 cognitive-engine 混合推理（本地 REST）
    // ════════════════════════════════════════════════════

    private Map<String, Object> executeReason(CognitivePipelineNode node, Map<String, Object> config,
                                              Map<String, Object> context) {
        String query = (String) config.getOrDefault("query", config.getOrDefault("q", ""));
        String url = INTERNAL_BASE + "/api/v1/knowledge/reason";

        HttpHeaders headers = jsonHeaders();
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("context", context);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodeId", node.getNodeId());
            result.put("response", resp.getBody());
            result.put("status", "reasoned");
            return result;
        } catch (Exception e) {
            log.warn("REASON node fallback (engine may be unavailable): {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodeId", node.getNodeId());
            result.put("status", "fallback");
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ════════════════════════════════════════════════════
    //  EXTRACT — 调 kb-engine 知识抽取（跨引擎 REST）
    // ════════════════════════════════════════════════════

    private Map<String, Object> executeExtract(CognitivePipelineNode node, Map<String, Object> config,
                                               Map<String, Object> context) {
        return callKbEngine(node, "/api/v1/knowledge/extract", config, context);
    }

    // ════════════════════════════════════════════════════
    //  KG — 调 kb-engine 建图（跨引擎 REST）
    // ════════════════════════════════════════════════════

    private Map<String, Object> executeKg(CognitivePipelineNode node, Map<String, Object> config,
                                          Map<String, Object> context) {
        return callKbEngine(node, "/api/v1/knowledge/graph/build", config, context);
    }

    // ════════════════════════════════════════════════════
    //  INGEST — 调 kb-engine 文档接入（PMO-34 补齐，当前降级）
    // ════════════════════════════════════════════════════

    private Map<String, Object> executeIngest(CognitivePipelineNode node, Map<String, Object> config,
                                              Map<String, Object> context) {
        return callKbEngine(node, "/api/v1/knowledge/ingest", config, context);
    }

    // ════════════════════════════════════════════════════
    //  通用跨引擎 REST 调用（带降级）
    // ════════════════════════════════════════════════════

    private Map<String, Object> callKbEngine(CognitivePipelineNode node, String path,
                                             Map<String, Object> config, Map<String, Object> context) {
        String url = INTERNAL_BASE + path;
        HttpHeaders headers = jsonHeaders();
        Map<String, Object> body = new HashMap<>();
        body.putAll(config);
        body.put("pipelineContext", context);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodeId", node.getNodeId());
            result.put("response", resp.getBody());
            result.put("status", "completed");
            return result;
        } catch (Exception e) {
            log.warn("{} node fallback (endpoint may be unavailable): {}", node.getNodeType(), e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodeId", node.getNodeId());
            result.put("status", "fallback");
            result.put("error", e.getMessage());
            result.put("endpoint", path);
            return result;
        }
    }

    // ════════════════════════════════════════════════════
    //  OAG_INTAKE — Wave-3.2 本地节点：需求解读
    // ════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeOagIntake(CognitivePipelineNode node,
                                                 Map<String, Object> config,
                                                 Map<String, Object> context) {
        String rawRequest = (String) config.get("raw_request");
        String domain = (String) config.getOrDefault("domain", "default");
        if (rawRequest == null && context != null && !context.isEmpty()) {
            // 上游可能携带 raw_request
            for (Object upObj : context.values()) {
                if (!(upObj instanceof Map)) continue;
                Map<String, Object> up = (Map<String, Object>) upObj;
                if (up.get("raw_request") != null) {
                    rawRequest = String.valueOf(up.get("raw_request"));
                    break;
                }
            }
        }
        Map<String, Object> out = oagIntakeService.handle(rawRequest, domain, config);
        out.put("nodeId", node.getNodeId());
        out.put("status", "intake_completed");
        return out;
    }

    // ════════════════════════════════════════════════════
    //  OAG_PLAN — Wave-3.2 本地节点：任务拆解
    // ════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeOagPlan(CognitivePipelineNode node,
                                               Map<String, Object> config,
                                               Map<String, Object> context) {
        String intentId = null;
        Map<String, Object> slots = new LinkedHashMap<>();
        if (context != null) {
            for (Object upObj : context.values()) {
                if (!(upObj instanceof Map)) continue;
                Map<String, Object> up = (Map<String, Object>) upObj;
                if (up.get("intent_id") != null) intentId = String.valueOf(up.get("intent_id"));
                Object sm = up.get("slot_map");
                if (sm instanceof Map) slots.putAll((Map<String, Object>) sm);
            }
        }
        Map<String, Object> out = oagPlannerService.handle(intentId, slots, config);
        out.put("nodeId", node.getNodeId());
        out.put("status", "plan_completed");
        return out;
    }

    // ════════════════════════════════════════════════════
    //  OAG_STRATEGY — Wave-3.2 本地节点：策略生成
    // ════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeOagStrategy(CognitivePipelineNode node,
                                                   Map<String, Object> config,
                                                   Map<String, Object> context) {
        Map<String, Object> reasonerResult = null;
        List<String> precedentIds = new ArrayList<>();
        if (context != null) {
            for (Object upObj : context.values()) {
                if (!(upObj instanceof Map)) continue;
                Map<String, Object> up = (Map<String, Object>) upObj;
                Object resp = up.get("response");
                if (resp instanceof Map) {
                    Map<String, Object> r = (Map<String, Object>) resp;
                    if (reasonerResult == null) {
                        reasonerResult = r;
                    }
                    Object pids = r.get("precedent_ids");
                    if (pids instanceof List) {
                        for (Object o : (List<Object>) pids) {
                            if (o != null) precedentIds.add(String.valueOf(o));
                        }
                    }
                } else if (up.get("reasoner_result") instanceof Map) {
                    reasonerResult = (Map<String, Object>) up.get("reasoner_result");
                }
            }
        }

        int maxActions = config.get("max_actions") instanceof Number
                ? ((Number) config.get("max_actions")).intValue() : 5;

        Map<String, Object> out = strategyGeneratorService.handle(
                reasonerResult, precedentIds, null /* precedents 由 Reviewer/QA 注入 */, maxActions);
        out.put("nodeId", node.getNodeId());
        out.put("status", "strategy_completed");
        return out;
    }

    // ════════════════════════════════════════════════════
    //  helpers
    // ════════════════════════════════════════════════════

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) return new HashMap<>();
        try {
            return MAPPER.readValue(configJson, Map.class);
        } catch (Exception e) {
            // config 可能是简单的非 JSON 字符串
            Map<String, Object> simple = new HashMap<>();
            simple.put("raw", configJson);
            return simple;
        }
    }
}
