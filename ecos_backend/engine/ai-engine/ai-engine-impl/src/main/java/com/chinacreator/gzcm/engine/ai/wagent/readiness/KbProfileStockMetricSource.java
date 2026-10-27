package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code know.sample_size} 的跨引擎取数实现（F10-08 K 层检查项①，#146b）。
 *
 * <p><b>属主读端点</b>：kb-engine
 * {@code GET /api/v1/knowledge/profiles/stock?status=PUBLISHED}（dccheng 属主面，
 * 权限鉴权，透传入站凭证；响应 data 段 {@code {profiles, sampleSum, statusFilter,
 * metricCode, domain, evidence}}，DAO 侧全参数化、无命中行 0 兜底）。判定交给
 * {@link ReadinessCheckCode#KNOW_SAMPLE_SIZE}（seed 两界 + floor 自纠正），
 * 本类只取数 + 形态校验。</p>
 *
 * <p><b>口径裁决 D-#146B-1（本批登记）</b>：seed 说「可用<b>画像/历史样本数</b>」，
 * 属主画像域只有两枚事实量（画像行数 + 各画像样本量之和），且评估上下文不带
 * metricCode（问题面不承诺指标域）⇒ 度量取 <b>全库 PUBLISHED 画像的
 * {@code SUM(sample_count)}</b>：这是"当前 scope 有史可查多少样本"的直读口径
 * （"无过滤画像存量"），不做命中画像匹配（命中匹配需要 metric×dims 语义，
 * kb 属主面无该读端点，另批）。若把行数当样本数＝虚报样本量，若把行数与
 * sampleSum 相加＝两口径串味，两者都是假真理方向，禁。</p>
 */
@Component
public class KbProfileStockMetricSource implements ReadinessMetricSource {

    private static final String STOCK_PATH = "/api/v1/knowledge/profiles/stock?status=PUBLISHED";

    private final ReadinessMetricFetchClient fetchClient;

    public KbProfileStockMetricSource(
            ObjectProvider<ReadinessMetricFetchClient> fetchClientProvider) {
        this.fetchClient = fetchClientProvider.getIfAvailable();
    }

    @Override
    public boolean supports(ReadinessCheckCode code) {
        return code == ReadinessCheckCode.KNOW_SAMPLE_SIZE;
    }

    @Override
    public MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context) {
        if (code != ReadinessCheckCode.KNOW_SAMPLE_SIZE) {
            return MetricValue.absent("unsupported-by-kb-profile-stock-source");
        }
        if (!WAgentEngineCatalog.isLiveServicePrefix(STOCK_PATH.split("\\?")[0])) {
            return MetricValue.absent("fetch-path-not-registered:" + STOCK_PATH);
        }
        if (fetchClient == null) {
            return MetricValue.absent("fetch-client-unavailable");
        }
        ReadinessMetricFetchClient.FetchResult result =
                fetchClient.fetchEnvelopeData(STOCK_PATH);
        if (!result.ok()) {
            return MetricValue.absent("fetch-failed:" + result.reason());
        }
        BigDecimal sampleSum = ReadinessMetricFetchClient.asDecimal(result.data(), "sampleSum");
        if (sampleSum == null) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("reason", "sampleSum-field-shape-invalid");
            ev.put("endpoint", STOCK_PATH);
            ev.put("raw", String.valueOf(result.data().get("sampleSum")));
            return MetricValue.absent(null, ev);
        }
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("source", sourceId());
        ev.put("sampleSum", sampleSum.toPlainString());
        ev.put("profiles", result.data().get("profiles"));
        ev.put("statusFilter", result.data().get("statusFilter"));
        ev.put("endpoint", STOCK_PATH);
        ev.put("evidence", result.data().get("evidence"));
        return MetricValue.number(sampleSum, ev);
    }

    @Override
    public String sourceId() {
        return "kb-profile-stock";
    }
}
