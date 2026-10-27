package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code onto.conflict_count} 的跨引擎取数实现（F10-08 I 层检查项②，#146b）。
 *
 * <p><b>属主读端点</b>：ontology-engine
 * {@code GET /api/v1/ontology/calibers/conflict-count}（buszhi 属主面，权限鉴权，
 * 透传入站凭证；响应 data 段 {@code {count:int, evidence:{criterion}}}——
 * 同一 caliber.code 下 ≥2 条 APPROVED 未删除版本行的 code 计数）。判定完全交给
 * {@link ReadinessCheckCode#ONTO_CONFLICT_COUNT}（V265 seed 两 clause 语义），
 * 本类只做"取数 + 形态校验"，不对 count 做任何折叠（负数/非数 ⇒ absent 留痕，
 * 由 judge 域的 out-of-domain 分支兜底 UNKNOWN，禁"看不懂就当 0 冲突"）。</p>
 */
@Component
public class CaliberConflictCountMetricSource implements ReadinessMetricSource {

    private static final String CONFLICT_COUNT_PATH = "/api/v1/ontology/calibers/conflict-count";

    private final ReadinessMetricFetchClient fetchClient;

    public CaliberConflictCountMetricSource(
            ObjectProvider<ReadinessMetricFetchClient> fetchClientProvider) {
        this.fetchClient = fetchClientProvider.getIfAvailable();
    }

    @Override
    public boolean supports(ReadinessCheckCode code) {
        return code == ReadinessCheckCode.ONTO_CONFLICT_COUNT;
    }

    @Override
    public MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context) {
        if (code != ReadinessCheckCode.ONTO_CONFLICT_COUNT) {
            return MetricValue.absent("unsupported-by-caliber-conflict-source");
        }
        if (!WAgentEngineCatalog.isLiveServicePrefix(CONFLICT_COUNT_PATH)) {
            return MetricValue.absent("fetch-path-not-registered:" + CONFLICT_COUNT_PATH);
        }
        if (fetchClient == null) {
            return MetricValue.absent("fetch-client-unavailable");
        }
        ReadinessMetricFetchClient.FetchResult result =
                fetchClient.fetchEnvelopeData(CONFLICT_COUNT_PATH);
        if (!result.ok()) {
            return MetricValue.absent("fetch-failed:" + result.reason());
        }
        BigDecimal count = ReadinessMetricFetchClient.asDecimal(result.data(), "count");
        if (count == null) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("reason", "count-field-shape-invalid");
            ev.put("endpoint", CONFLICT_COUNT_PATH);
            ev.put("raw", String.valueOf(result.data().get("count")));
            return MetricValue.absent(null, ev);
        }
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("source", sourceId());
        ev.put("count", count.toPlainString());
        ev.put("endpoint", CONFLICT_COUNT_PATH);
        ev.put("criterion", result.data().get("evidence"));
        return MetricValue.number(count, ev);
    }

    @Override
    public String sourceId() {
        return "caliber-conflict-count";
    }
}
