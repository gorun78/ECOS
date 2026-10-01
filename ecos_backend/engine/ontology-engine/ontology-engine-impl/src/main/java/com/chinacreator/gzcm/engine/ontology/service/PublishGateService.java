package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService.CaliberMissingException;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService.CaliberNotApprovedException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
/**
 * F03-03 发布门禁编排器（详细设计-03 §A F03-03 / §D.3）。
 *
 * <p>三套嵌套守卫，一个编排器：</p>
 * <ul>
 *   <li><b>V1 口径</b>（内联）：逐指标校验 {@code caliber_id} 存在 + 版本 APPROVED（经 {@link CaliberService}）；</li>
 *   <li><b>V2 单位</b>（{@link MetricUnitDerivationGuard}）：四则代数推导结果单位 + 可加性一致性；</li>
 *   <li><b>V3 血缘</b>（{@link DatNetLineageGuard}）：经 data-engine 元数据 REST 校验事实表列真实存在。</li>
 * </ul>
 *
 * <p><b>三态语义（铁律 §2.4-8）</b>：
 * 通过（继续执行）/ 拒绝（提案停留 PENDING，逐条违反项）/
 * 依赖不可用（V3 调 data-engine 失败、V1 口径服务 fail-closed）
 * = 503 {@code ECOS-ONTO-050} / 500 {@code ECOS-ONTO-042}，<b>不得默认通过</b>。</p>
 *
 * <h3>接入说明（W75/M0：本任务不改动 Controller）</h3>
 * <p>Call from {@code OntologyProposalController#executeProposal}/{@code #approveAndPublish}
 * <b>after the status keyword check and before {@code executeAndPublish}</b> — see 详细设计-03 §F03-03.
 * If {@code dependencyErrors} is non-empty, return
 * {@code ApiResponse.error(503, "ECOS-ONTO-050", "...")} (do NOT proceed).
 * If {@code violations} non-empty, return
 * {@code ApiResponse.badRequest("ECOS-ONTO-publish-gate-failed: " + violations)}.</p>
 * <pre>
 *   // 状态校验之后、executeAndPublish 之前：
 *   ApiResponse&lt;?&gt; gate = PublishGateService.toApiResponse(publishGateway.validate(proposalRow));
 *   if (gate != null) return gate;       // 400（violations）或 503（dependencyErrors）
 *   // 门禁通过 → 继续 executeAndPublish
 * </pre>
 */
@Component
public class PublishGateService {

    private static final Logger log = LoggerFactory.getLogger(PublishGateService.class);

    private final CaliberService caliberService;
    private final DatNetLineageGuard lineageGuard;

    /** 控制域 schema（ST07 5+1）；本门禁只做 AST/REST 判定，暂不落表。 */
    @Value("${ecos.ontology.control-schema:ecos_ontology}")
    private String controlSchema;

    /**
     * Spring 装配：口径服务同 JVM 宿主 bean；血缘元数据经
     * {@link ServiceEndpointResolver} 两态寻址 + 受限 {@link RestTemplate}（3s 超时）。
     *
     * @param caliberService   口径裁定服务（W73 同包契约；缺失/未审批抛 {@code CaliberMissingException}/{@code CaliberNotApprovedException}）
     * @param endpointResolver 跨服务寻址唯一出口（boot/单测态可能缺席 → null，V3 相应 fail-closed）
     */
    @Autowired
    public PublishGateService(CaliberService caliberService,
                              @Autowired(required = false) ServiceEndpointResolver endpointResolver) {
        this.caliberService = caliberService;
        this.lineageGuard = new DatNetLineageGuard(
                new DatNetLineageGuard.RestLineageColumnFetcher(endpointResolver, buildRestTemplate()));
    }

    /**
     * 测试/特殊装配入口：血缘列供给由调用方 stub（{@link DatNetLineageGuard.LineageColumnFetcher}），
     * 单元测试无需真实 HTTP。公开以便跨包子包（如 {@code service.PublishGate}）测试注入，不参与 Spring 装配。
     */
    public PublishGateService(CaliberService caliberService, DatNetLineageGuard.LineageColumnFetcher fetcher) {
        this.caliberService = caliberService;
        this.lineageGuard = new DatNetLineageGuard(fetcher);
    }

    /**
     * 门禁总入口（内部 API，跨引擎可复用）。
     *
     * @param proposalRow 提案行（{@code payload.metrics} 或 {@code payload.metricRef} +
     *                    {@code payload.changeType=="CREATE_METRIC"}；含事实表引用与声明列）
     * @return {@link GateResult}：passed + violations + dependencyErrors（后者非空 → 整体 503 fail-closed）
     */
    public GateResult validate(Map<String, Object> proposalRow) {
        GateResult result = new GateResult();
        result.setEvaluatedAt(java.time.Instant.now());

        Map<String, Object> payload = extractPayload(proposalRow);
        List<Map<String, Object>> metrics = extractMetrics(payload);

        for (Map<String, Object> metric : metrics) {
            checkCaliber(metric, result);   // V1
            checkUnit(metric, result);      // V2
        }

        checkLineage(payload, metrics, result); // V3

        result.setPassed(result.getViolations().isEmpty() && result.getDependencyErrors().isEmpty());
        log.info("publish-gate proposalId={} violations={} dependencyErrors={} schema={}",
                proposalRow == null ? null : proposalRow.get("id"),
                result.getViolations().size(),
                result.getDependencyErrors().size(),
                controlSchema);
        return result;
    }

    /**
     * 便捷出口：把 {@link GateResult} 映射为 Controller 可直接返回的 {@link ApiResponse}（见类注释「接入说明」）。
     * <p>{@code dependencyErrors} 优先（503 fail-closed，且<B>不得继续执行</B>）→ 否则 violations 400 → 全通过返回 null。</p>
     */
    public static <T> ApiResponse<T> toApiResponse(GateResult result) {
        if (result == null) {
            return null;
        }
        if (result.hasDependencyErrors()) {
            String code = result.getDependencyErrors().get(0).getCode();
            return ApiResponse.error(503, code, "ECOS-ONTO-050: publish-gate dependency unavailable");
        }
        if (!result.isPassed()) {
            // 4xx 拒绝：errorCode = 第一个违反项的 code（如 ECOS-ONTO-040/021/030）
            String code = result.getViolations().isEmpty()
                    ? GateResult.CODE_CALIBER_METRIC_MISSING : result.getViolations().get(0).getCode();
            return ApiResponse.error(400, code,
                    "ECOS-ONTO-publish-gate-failed: " + result.getViolations());
        }
        return null; // OK → proceed
    }

    // ── V1 口径（内联） ────────────────────────────────────────────

    private void checkCaliber(Map<String, Object> metric, GateResult result) {
        String code = asText(metric.get("code"));
        Object caliberId = firstNonNull(metric.get("caliber_id"), metric.get("caliberId"));
        if (caliberId == null || asText(caliberId).isBlank()) {
            result.getViolations().add(new GateResult.GateViolation(
                    GateResult.GATE_V1_CALIBER, GateResult.CODE_CALIBER_METRIC_MISSING,
                    "metric '" + code + "' has no caliber_id"));
            return;
        }
        try {
            caliberService.assertMetricCaliberApprovedForPublish(code);
        } catch (CaliberMissingException | CaliberNotApprovedException e) {
            result.getViolations().add(new GateResult.GateViolation(
                    GateResult.GATE_V1_CALIBER, GateResult.CODE_CALIBER_METRIC_MISSING,
                    "metric '" + code + "' has no approved caliber: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.error("caliber service unavailable (fail-closed) for metric={}", code, e);
            result.getDependencyErrors().add(
                    new GateResult.DependencyError("caliber", GateResult.CODE_CALIBER_SERVICE_UNAVAILABLE));
            result.setPassed(false);
        }
    }

    // ── V2 单位/可加性（MetricUnitDerivationGuard） ─────────────────

    private void checkUnit(Map<String, Object> metric, GateResult result) {
        String code = asText(metric.get("code"));
        Object expression = firstNonNull(metric.get("expression"), metric.get("formula"));
        result.getViolations().addAll(
                MetricUnitDerivationGuard.check(code, asText(expression), declaredAdditive(metric.get("additive"))));
    }

    // ── V3 血缘列存在（DatNetLineageGuard） ────────────────────────

    private void checkLineage(Map<String, Object> payload, List<Map<String, Object>> metrics, GateResult result) {
        String tableRef = resolveTableRef(payload, metrics);
        if (tableRef == null || tableRef.isBlank()) {
            return; // 无血缘引用 → PASS（无事实表可校验）
        }
        lineageGuard.check(tableRef, collectDeclaredColumns(payload, metrics), result);
    }

    // ── payload 解析辅助 ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractPayload(Map<String, Object> proposalRow) {
        if (proposalRow == null) return Map.of();
        Object p = proposalRow.get("payload");
        return p instanceof Map ? (Map<String, Object>) p : proposalRow;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractMetrics(Map<String, Object> payload) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (payload == null) return out;
        Object list = payload.get("metrics");
        if (list instanceof List) {
            for (Object o : (List<Object>) list) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
        if (!out.isEmpty()) return out;
        // 单指标提案：metricRef / changeType==CREATE_METRIC
        Object ref = payload.get("metricRef");
        if (ref instanceof Map) {
            out.add((Map<String, Object>) ref);
            return out;
        }
        if ("CREATE_METRIC".equals(asText(payload.get("changeType"))) && ref != null) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            if (ref instanceof Map) m.putAll((Map<String, Object>) ref);
            out.add(m);
        }
        return out;
    }

    private static String resolveTableRef(Map<String, Object> payload, List<Map<String, Object>> metrics) {
        for (String key : new String[]{"sourceTable", "factTable", "tableRef"}) {
            String v = asText(payload.get(key));
            if (v != null && !v.isBlank()) return v;
        }
        for (Map<String, Object> m : metrics) {
            for (String key : new String[]{"sourceTable", "factTable", "tableRef"}) {
                String v = asText(m.get(key));
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> collectDeclaredColumns(Map<String, Object> payload, List<Map<String, Object>> metrics) {
        List<String> cols = new ArrayList<>();
        Object declared = payload.get("declaredColumns");
        if (declared instanceof List) {
            for (Object o : (List<Object>) declared) {
                String s = asText(o);
                if (s != null && !s.isBlank()) cols.add(s);
            }
        }
        for (Map<String, Object> m : metrics) {
            Object c = m.get("column");
            String s = asText(c);
            if (s != null && !s.isBlank()) cols.add(s);
        }
        return cols;
    }

    // ── 通用工具 ──────────────────────────────────────────────────

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    /** 解析「声明 additive=1」：true=元数据声明可加；null=计量非可加（比率/月本身非可加、无 additive 声明）。 */
    private static Boolean declaredAdditive(Object additive) {
        if (additive instanceof Boolean b) return b;
        String s = asText(additive);
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    private static String asText(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
