package com.chinacreator.gzcm.engine.data.quality.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.model.DqKnowledgeEntryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaRequest;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DQ 根因分析服务实现（PMO-48-D T16：stub → 真实 cognitive 调用）。
 *
 * <p><b>真接链路</b>（替换 PMO-48-C T13 stub）：</p>
 * <ol>
 *   <li>按 {@code workOrderId} 读 {@code ecos_dq.dq_work_order} 拿 ruleId / severity / description</li>
 *   <li>{@code DqKnowledgeSinkService.searchSimilar(errorMessage, 3)} 取 top3 相似知识（RAG 上下文）</li>
 *   <li>调 cognitive-engine {@code POST /api/v1/cognitive/diagnose}（T6 总线，gateway 聚合 :8080）
 *       — body 承载 {@link DqRcaRequest} 字段 + {@code similarKnowledge} 上下文，
 *       响应 {@code {rootCause, causalChain, suggestions, ...}} 映射到 {@link DqRcaResult}</li>
 *   <li>失败 / 超时（15s）fallback：{@code rootCause="cognitive 调用超时", confidence=0.0}（不阻塞工单主流程）</li>
 *   <li>confidence 启发式合成：cognitive 返回基础上按相似知识命中（maxScore≥0.6 → 0.4 起 +0.1 命中加成，cap 0.95）</li>
 * </ol>
 *
 * <p><b>调用方式</b>：自定义 {@link RestTemplate}（SimpleClientHttpRequestFactory 15s/15s 超时），
 * 与 {@link DqSecurityService} / {@code PipelineSecurityService} 同一模式；不 new 全局无超时
 * RestTemplate Bean，不 import cognitive-engine-impl（铁律 2.1 跨引擎走 REST，铁律 2 不 new RestTemplate 全局）。
 * 走已存在的 {@code /api/v1/cognitive/diagnose} 端点（{@code DiagnosisController}），
 * 不依赖不存在的 {@code /api/v1/cognitive/agent/invoke}。</p>
 *
 * <p>Bean 名 {@code ecosDqRcaService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-C T13（stub）+ PMO-48-D T16（真接）
 */
@Service("ecosDqRcaService")
public class DqRcaServiceImpl implements DqRcaService {

    private static final Logger log = LoggerFactory.getLogger(DqRcaServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** cognitive diagnose 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 15000;

    /** cognitive diagnose 读取超时（毫秒，PMO-48-D 风险表"超时兜底 15s"） */
    private static final int READ_TIMEOUT_MS = 15000;

    /** cognitive-engine 基地址（gateway 聚合后走本进程 8080；独立部署按环境覆盖） */
    private static final String COGNITIVE_BASE_URL = "http://localhost:8080";

    /** RAG 相似知识 top N */
    private static final int SIMILAR_TOP = 3;

    /** 相似知识命中阈值（score ≥ 0.6 视为有效命中） */
    private static final double SIM_HIT_THRESHOLD = 0.6D;

    /** confidence 启发式起点 / 命中加成 / 上限 */
    private static final double CONF_BASE = 0.4D;
    private static final double CONF_HIT_BONUS = 0.1D;
    private static final double CONF_CAP = 0.95D;

    /** 每次运行新建（非共享 Bean，铁律 2：不走全局无超时 RestTemplate；short-lived 运行结束 GC 回收） */
    /** 每次运行短生命周期（非共享 Bean，铁律 2：不走全局无超时 RestTemplate；short-lived GC 回收）。
     *  非 final 以便 test-visible 构造器注入 mock（PMO-48-D T17 E2E IT） */
    private RestTemplate restTemplate = buildRestTemplate();

    private final DqKnowledgeSinkService knowledgeSinkService;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    @org.springframework.beans.factory.annotation.Autowired
    public DqRcaServiceImpl(DqKnowledgeSinkService knowledgeSinkService,
                             ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.knowledgeSinkService = knowledgeSinkService;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /**
     * 测试专用构造器（test-visible，PMO-48-D T17 E2E IT）：允许 mock RestTemplate 注入
     * 以便 stub cognitive diagnose 调用，验证 RCA 真接链路。公共可见性仅测试结果友好；
     * Bean 装配仍走 {@link #DqRcaServiceImpl(DqKnowledgeSinkService, ObjectProvider)}。
     */
    public DqRcaServiceImpl(RestTemplate restTemplate,
                            DqKnowledgeSinkService knowledgeSinkService,
                            ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.restTemplate = restTemplate;
        this.knowledgeSinkService = knowledgeSinkService;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @Override
    public DqRcaResult runDiagnose(DqRcaRequest request) {
        String workOrderId = request != null ? request.getWorkOrderId() : null;

        // 1. 读工单上下文（ruleId / severity / description 作为 errorMessage）
        Map<String, Object> wo = cleanId(workOrderId) == null ? new LinkedHashMap<>() : readWorkOrder(workOrderId);
        String ruleId = strOf(wo.get("ruleId"));
        String assetId = strOf(wo.get("assetId"));
        String severity = strOf(wo.get("severity"));
        String errorMessage = strOf(wo.get("description"));
        // 失败 / 空 workOrderId 时降级用 request 字段
        if (ruleId == null) {
            ruleId = request != null ? request.getFailedRuleId() : null;
        }
        if (assetId == null) {
            assetId = request != null ? request.getAssetId() : null;
        }

        // 2. RAG 相似知识（top3）— 失败降级空
        List<SimilarHit> similar = searchSimilarSafe(errorMessage);

        // 3. 组装 cognitive diagnose 请求（T6 总线 POST /api/v1/cognitive/diagnose）
        Map<String, Object> body = buildCognitiveBody(ruleId, assetId, severity, workOrderId, errorMessage, similar);

        // 4. 调 cognitive，失败 fallback
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    COGNITIVE_BASE_URL + "/api/v1/cognitive/diagnose",
                    new HttpEntity<>(body, jsonHeaders()), String.class);
            Map<String, Object> data = parseData(resp);
            if (data == null || data.isEmpty()) {
                return fallbackResult("cognitive 返回空 data", similar);
            }
            // 5. 映射 cognitive 响应 → DqRcaResult + confidence 启发式
            return mapCognitiveResult(data, similar, workOrderId);
        } catch (ResourceAccessException e) {
            // 连接 / 读取超时 → fallback
            log.warn("DqRca cognitive 调用超时（fallback）: workOrderId={}, error={}",
                    workOrderId, e.getMessage());
            return fallbackResult("cognitive 调用超时", similar);
        } catch (RestClientException e) {
            // HTTP 4xx/5xx 等业务错误 → fallback
            // 注意：Spring 6.1 RestTemplate 中 RestClientException 只暴露 getStatusCode()（已移除），
            // HTTP 状态码通过 (HttpStatusCodeException) 强取；非 HTTP 错误码（连接非法等）降级为 -1
            Object statusCode = "-1";
            if (e instanceof HttpStatusCodeException hce) {
                statusCode = String.valueOf(hce.getStatusCode().value());
            }
            log.warn("DqRca cognitive 调用失败（fallback）: workOrderId={}, status={}, error={}",
                    workOrderId, statusCode, e.getMessage());
            return fallbackResult("cognitive 调用失败: " + statusCode, similar);
        } catch (RuntimeException e) {
            log.warn("DqRca cognitive 调用异常（fallback）: workOrderId={}, error={}", workOrderId, e.getMessage());
            return fallbackResult("cognitive 调用异常", similar);
        }
    }

    // ==================== 1. 工单上下文 ====================

    private Map<String, Object> readWorkOrder(String workOrderId) {
        JdbcTemplate jdbc = jdbc();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT rule_id AS ruleId, asset_id AS assetId, severity, description"
                    + " FROM ecos_dq.dq_work_order WHERE id = ? AND is_deleted = FALSE",
                    workOrderId);
            return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
        } catch (RuntimeException e) {
            log.warn("DqRca 读 dq_work_order 失败（降级空）: workOrderId={}, error={}",
                    workOrderId, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    // ==================== 2. RAG 相似知识（失败降级空列表） ====================

    private List<SimilarHit> searchSimilarSafe(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<DqKnowledgeEntryVO> top = knowledgeSinkService.searchSimilar(errorMessage, SIMILAR_TOP).getData();
            List<SimilarHit> out = new ArrayList<>();
            if (top != null) {
                for (DqKnowledgeEntryVO vo : top) {
                    out.add(new SimilarHit(vo.getTitle(), vo.getSummary(), vo.getScoreHint()));
                }
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("DqRca searchSimilar 失败（降级空）: error={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 3. cognitive 请求 / 响应映射 ====================

    /** 组装 cognitive diagnose 请求体（契约对齐 DiagnosisController + DqRcaRequest 字段 + similarKnowledge 上下文） */
    private static Map<String, Object> buildCognitiveBody(String ruleId, String assetId, String severity,
                                                          String workOrderId, String errorMessage,
                                                          List<SimilarHit> similar) {
        Map<String, Object> input = new LinkedHashMap<>();
        putIfPresent(input, "workOrderId", workOrderId);
        putIfPresent(input, "ruleId", ruleId);
        putIfPresent(input, "alertLevel", severity);
        putIfPresent(input, "assetId", assetId);
        putIfPresent(input, "errorMessage", errorMessage);
        input.put("similarKnowledge", similar);
        input.put("timeWindow", "1h");

        Map<String, Object> body = new LinkedHashMap<>();
        // metric 必填（DiagnosisController 取 request.get("metric")）— 用 errorMessage 摘要作诊断指标
        body.put("metric", metricOf(errorMessage, ruleId));
        body.put("deviation", 1.0);
        body.put("domain", "dq_rca");
        body.put("maxDepth", 5);
        body.put("input", input);   // PMO-48-D T16 约定的业务上下文字段（cognitive 侧可选消费）
        return body;
    }

    /** metric：诊断指标标识（errorMessage 截断；空时用 ruleId 兜底） */
    private static String metricOf(String errorMessage, String ruleId) {
        if (errorMessage != null && !errorMessage.isBlank()) {
            String m = errorMessage.trim();
            return m.length() > 64 ? m.substring(0, 64) : m;
        }
        return ruleId != null ? ruleId : "dq_rule_check";
    }

    /** 解析 ApiResponse 响应体，取 data 字段（失败返回 null） */
    private Map<String, Object> parseData(ResponseEntity<String> resp) {
        String b = resp.getBody();
        if (b == null || b.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> root = MAPPER.readValue(b, MAP_TYPE);
            Object data = root.get("data");
            if (data instanceof Map<?, ?> m) {
                return castMap(m);
            }
            return null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("DqRca cognitive 响应解析失败: error={}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("DqRca cognitive 响应解析异常（fallback）: error={}", e.getMessage());
            return null;
        }
    }

    /** cognitive 响应 → DqRcaResult（rootCause 兼容 String/Map；causalChain 兼容 List<String>/List<Map>） */
    private DqRcaResult mapCognitiveResult(Map<String, Object> data, List<SimilarHit> similar, String workOrderId) {
        String rootCause = extractRootCause(data.get("rootCause"));
        List<String> causalChain = extractChain(data.get("causalChain"));
        List<DqRcaResult.Candidate> candidates = extractCandidates(data.get("candidates"));
        double confidence = synthesizeConfidence(similar);

        if (rootCause == null || rootCause.isBlank()) {
            // cognitive 未给出明确根因（如 metric 未在 KG 发现）→ 降级标记
            rootCause = "cognitive 未识别明确根因（参考相似知识 " + similar.size() + " 条）";
            confidence = Math.min(confidence, 0.3D);
        }

        log.info("DqRca cognitive 返回: workOrderId={}, rootCause={}, chainLen={}, confidence={}",
                workOrderId, truncate(rootCause, 80), causalChain.size(), confidence);

        return DqRcaResult.builder()
                .rootCause(rootCause)
                .confidence(round4(confidence))
                .causalChain(causalChain)
                .candidates(candidates)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /** rootCause 兼容：String 直取；Map → 取 message / cause / description 字段 */
    private static String extractRootCause(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof Map<?, ?> m) {
            for (String k : new String[]{"message", "cause", "description", "rootCause"}) {
                Object v = m.get(k);
                if (v != null && !String.valueOf(v).isBlank()) {
                    return String.valueOf(v);
                }
            }
            return null;
        }
        return String.valueOf(o);
    }

    /** causalChain 兼容：List<String> 直取；List<Map> → 取 node / label / name 字段 */
    private static List<String> extractChain(Object o) {
        List<String> out = new ArrayList<>();
        if (!(o instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof String s) {
                if (!s.isBlank()) {
                    out.add(s);
                }
            } else if (item instanceof Map<?, ?> m) {
                for (String k : new String[]{"node", "label", "name", "metric"}) {
                    Object v = m.get(k);
                    if (v != null && !String.valueOf(v).isBlank()) {
                        out.add(String.valueOf(v));
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** candidates 兼容：List<Map>{cause,prob} → Candidate；非 List 返回空 */
    private static List<DqRcaResult.Candidate> extractCandidates(Object o) {
        List<DqRcaResult.Candidate> out = new ArrayList<>();
        if (!(o instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                String cause = m.get("cause") != null ? String.valueOf(m.get("cause"))
                        : (m.get("description") != null ? String.valueOf(m.get("description")) : null);
                Double prob = toDouble(m.get("prob") != null ? m.get("prob") : m.get("prob"));
                if (cause != null) {
                    out.add(DqRcaResult.Candidate.builder()
                            .cause(cause)
                            .prob(prob != null ? prob : 0.0D)
                            .build());
                }
            }
        }
        return out;
    }

    /** confidence 启发式：基数 0.4 + 命中相似知识(≥0.6)×0.1，cap 0.95 */
    private static double synthesizeConfidence(List<SimilarHit> similar) {
        double c = CONF_BASE;
        int hits = 0;
        for (SimilarHit h : similar) {
            if (h.score != null && h.score >= SIM_HIT_THRESHOLD) {
                hits++;
            }
        }
        c += hits * CONF_HIT_BONUS;
        return Math.min(c, CONF_CAP);
    }

    /** fallback 结果：cognitive 不可用时的降级（confidence=0.0，不阻塞主流程） */
    private static DqRcaResult fallbackResult(String reason, List<SimilarHit> similar) {
        List<DqRcaResult.Candidate> cands = new ArrayList<>();
        for (SimilarHit h : similar) {
            cands.add(DqRcaResult.Candidate.builder()
                    .cause(h.title != null ? "相似知识参考: " + h.title : "相似知识参考")
                    .prob(h.score != null ? round4(h.score) : 0.0D)
                    .build());
        }
        return DqRcaResult.builder()
                .rootCause(reason)
                .confidence(0.0D)
                .causalChain(new ArrayList<>())
                .candidates(cands)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ==================== 4. 工具 ====================

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate tpl = jdbcTemplateProvider.getIfAvailable();
        if (tpl == null) {
            throw new BusinessException("JdbcTemplate 不可用，无法执行 DQ RCA");
        }
        return tpl;
    }

    private static String cleanId(String id) {
        if (id == null) {
            return null;
        }
        String t = id.trim();
        return t.isEmpty() ? null : t;
    }

    private static String strOf(Object o) {
        return o == null ? null : (String.valueOf(o).isBlank() ? null : String.valueOf(o));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0D) / 10000.0D;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static void putIfPresent(Map<String, Object> m, String k, Object v) {
        if (v != null && !String.valueOf(v).isBlank()) {
            m.put(k, v);
        }
    }

    // ==================== 相似知识命中（内部 DTO） ====================

    /** 相似知识命中（title + summary + score）— 透传给 cognitive input.similarKnowledge */
    static class SimilarHit {
        String title;
        String summary;
        Double score;

        SimilarHit(String title, String summary, Double score) {
            this.title = title;
            this.summary = summary;
            this.score = score;
        }
    }
}
