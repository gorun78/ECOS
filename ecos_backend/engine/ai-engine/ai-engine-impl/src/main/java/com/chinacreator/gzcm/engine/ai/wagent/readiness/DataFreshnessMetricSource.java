package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code data.staleness_days} 的跨引擎取数实现（F10-08 D 层检查项②，#146b）。
 *
 * <p><b>属主读端点（本端点只读不组装判定）</b>：data-engine
 * {@code GET /api/v1/engine/data/freshness}（datanet 属主面，default 匿名不登记，
 * 透传入站凭证；响应 data 段 {@code {lastSucceededFinishedAt: ISO-8601|null,
 * source:"ecos_pipeline_run"}}——管道最近一次 SUCCEEDED 的 finished_at 原值）。
 * 路径走 {@link WAgentEngineCatalog} DATA 前缀族登记段拼定，本类无完整 URL 字面
 * （R-43）；判定在本类 = <b>距 as-of（取数时刻）的自然日数</b>（seed「最近批次入湖
 * 距 as-of 的天数」的直读口径，向下取整到整日，不做小时加权）。</p>
 *
 * <p><b>fail-closed 分态</b>（任一环节"没取到"一律 absent，禁估算禁 0 兜底）：</p>
 * <ul>
 *   <li>取数故障（基址不可解析/超时/非 2xx/空体/信封缺 data）⇒ absent 并带客户端原因字面；</li>
 *   <li>data 段 {@code lastSucceededFinishedAt} = null ⇒ absent（"从未有成功入湖"
 *       与"很久没入湖"是两个证据态：前者没有可距差的时间锚，禁把 null 当 epoch-0
 *       洗出天文数字天数——那样把'无数据'洗成'极老'，是反向假真理）；</li>
 *   <li>时间字面不可解析 ⇒ absent（属主契约漂移，禁按 0 天洗成"刚刚入湖"）。</li>
 * </ul>
 */
@Component
public class DataFreshnessMetricSource implements ReadinessMetricSource {

    private static final String FRESHNESS_PATH = "/api/v1/engine/data/freshness";

    private final ReadinessMetricFetchClient fetchClient;

    public DataFreshnessMetricSource(
            ObjectProvider<ReadinessMetricFetchClient> fetchClientProvider) {
        this.fetchClient = fetchClientProvider.getIfAvailable();
    }

    @Override
    public boolean supports(ReadinessCheckCode code) {
        return code == ReadinessCheckCode.DATA_STALENESS;
    }

    @Override
    public MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context) {
        if (code != ReadinessCheckCode.DATA_STALENESS) {
            return MetricValue.absent("unsupported-by-data-freshness-source");
        }
        if (!WAgentEngineCatalog.isLiveServicePrefix(FRESHNESS_PATH)) {
            // 目录漂移自证：该路径已不属 DATA 前缀族 ⇒ 连"取数目标"都不成立
            return MetricValue.absent("fetch-path-not-registered:" + FRESHNESS_PATH);
        }
        if (fetchClient == null) {
            return MetricValue.absent("fetch-client-unavailable");
        }
        ReadinessMetricFetchClient.FetchResult result =
                fetchClient.fetchEnvelopeData(FRESHNESS_PATH);
        if (!result.ok()) {
            return MetricValue.absent("fetch-failed:" + result.reason());
        }
        Object raw = result.data().get("lastSucceededFinishedAt");
        if (raw == null) {
            // 从未有成功 run：无时间锚可距差，禁 0 兜底也禁 epoch-0
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("reason", "no-succeeded-run-on-record");
            ev.put("endpoint", FRESHNESS_PATH);
            ev.put("ownerSource", result.data().get("source"));
            return MetricValue.absent(null, ev);
        }
        Instant lastSucceeded;
        try {
            lastSucceeded = Instant.parse(String.valueOf(raw).trim());
        } catch (RuntimeException unparsable) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("reason", "last-succeeded-not-instant:" + raw);
            ev.put("endpoint", FRESHNESS_PATH);
            return MetricValue.absent(null, ev);
        }
        long days = Duration.between(lastSucceeded, Instant.now()).toDays();
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("source", sourceId());
        ev.put("lastSucceededFinishedAt", lastSucceeded.toString());
        ev.put("asOf", Instant.now().toString());
        ev.put("calendarDays", days);
        ev.put("endpoint", FRESHNESS_PATH);
        ev.put("ownerSource", result.data().get("source"));
        return MetricValue.number(BigDecimal.valueOf(days), ev);
    }

    @Override
    public String sourceId() {
        return "data-freshness";
    }
}
