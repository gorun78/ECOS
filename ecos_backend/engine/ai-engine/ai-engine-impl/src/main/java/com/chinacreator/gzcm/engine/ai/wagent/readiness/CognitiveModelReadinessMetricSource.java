package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import com.chinacreator.gzcm.engine.cognitive.api.GateOutcome;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C 层两项度量（{@code cog.cm_ready} + {@code cog.backtest_ready}）的跨引擎取数实现（#146b）。
 *
 * <p><b>属主读端点（一次取数供两项，禁两路重复出向）</b>：cognitive-engine
 * {@code GET /api/v1/cognitive/models?registryState=PUBLISHED&cognitiveModelType=<CM>}
 *（aiming 属主面，权限鉴权，透传入站凭证；行含 {@code backtestGate} = gate_backtest
 * 原值透传键）。判定语义在 V265 seed × 阈值档位，本类只负责把「意图 → CM 类型」与
 * 「rows → 布尔」两折算对。</p>
 *
 * <h3>口径裁决 D-#146B-2（本批登记，逐条）</h3>
 * <ol>
 *   <li><b>意图 → CM 映射单源 = api 契约 {@link CognitiveModelType#fromLegacyModelType}</b>
 *       （FORECAST→CM-03 / CAUSAL→CM-04 / SIMULATION→CM-05 / DECISION→CM-01）：
 *       W 域 {@link GoalIntent} 与之词面对齐的四枚（FORECAST/SIMULATE/DECIDE…见
 *       {@link #cmForIntent}）复用同一映射，未映射意图（DIAGNOSE/RANK/MONITOR/BUILD/REVIEW）
 *       无契约 CM ⇒ absent（"该意图没有登记模型类型"是实测事实，禁就近猜一个 CM
 *       然后把"没册"洗成"在册"）。</li>
 *   <li><b>cm_ready = rows 非空</b>（端点已按 registryState=PUBLISHED 过滤，
 *       命中行即"类型在册 ∧ 发布态"）；空 rows = 合法实测 false（"无在册行"与
 *       "取不到"分态——前者出布尔，后者出 absent）。</li>
 *   <li><b>backtest_ready 无行/无值 ⇒ absent，不是 false</b>：PUBLISHED 存在但
 *       gate 列缺失时没有"回测留痕"的证据本身，UNKNOWN 才是诚实表达；
 *       gate 值解析一律走 api 契约 {@link GateOutcome#fromValue}，非三值字面
 *       ⇒ absent 留痕（禁 orElse(PENDING) 式宽容在度量面重放——PENDING 语义是
 *       "没跑"，把坏字面翻成"没跑"会把契约漂移洗成可判定值）。命中行取排序首行
 *       （DAO 按 model_id/version 排序），evidence 落全部行 id/gate 供复盘。</li>
 * </ol>
 */
@Component
public class CognitiveModelReadinessMetricSource implements ReadinessMetricSource {

    private static final String MODELS_PATH_PREFIX = "/api/v1/cognitive/models?registryState=PUBLISHED";

    private final ReadinessMetricFetchClient fetchClient;

    public CognitiveModelReadinessMetricSource(
            ObjectProvider<ReadinessMetricFetchClient> fetchClientProvider) {
        this.fetchClient = fetchClientProvider.getIfAvailable();
    }

    @Override
    public boolean supports(ReadinessCheckCode code) {
        return code == ReadinessCheckCode.COG_CM_READY
                || code == ReadinessCheckCode.COG_BACKTEST_READY;
    }

    @Override
    public MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context) {
        if (code != ReadinessCheckCode.COG_CM_READY
                && code != ReadinessCheckCode.COG_BACKTEST_READY) {
            return MetricValue.absent("unsupported-by-cognitive-model-readiness-source");
        }
        CognitiveModelType cm = cmForIntent(context == null ? null : context.intent());
        if (cm == null) {
            return MetricValue.absent("intent-not-mapped-to-cm:" + intentLiteral(context));
        }
        String path = MODELS_PATH_PREFIX + "&cognitiveModelType=" + cm.code();
        if (!WAgentEngineCatalog.isLiveServicePrefix(path.split("\\?")[0])) {
            return MetricValue.absent("fetch-path-not-registered:" + path);
        }
        if (fetchClient == null) {
            return MetricValue.absent("fetch-client-unavailable");
        }
        ReadinessMetricFetchClient.FetchResult result =
                fetchClient.fetchEnvelopeRows(path);
        if (!result.ok()) {
            return MetricValue.absent("fetch-failed:" + result.reason());
        }
        List<Map<String, Object>> rows = result.rows();
        Map<String, Object> ev = baseEvidence(code, path, cm);
        if (rows.isEmpty()) {
            // 合法实测：该 CM 无 PUBLISHED 行。cm_ready 出 false；backtest 无对象可判 ⇒ absent
            if (code == ReadinessCheckCode.COG_CM_READY) {
                ev.put("rows", 0);
                return MetricValue.bool(Boolean.FALSE, ev);
            }
            ev.put("reason", "no-published-model-for-cm");
            ev.put("rows", 0);
            return MetricValue.absent(null, ev);
        }
        return code == ReadinessCheckCode.COG_CM_READY
                ? MetricValue.bool(Boolean.TRUE, ev)
                : backtestOf(rows, ev);
    }

    @Override
    public String sourceId() {
        return "cognitive-model-readiness";
    }

    /** 意图 → 契约 CM 单源映射（未映射 ⇒ null ⇒ 上游折算 absent，禁静默默认）。 */
    static CognitiveModelType cmForIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return null;
        }
        GoalIntent goalIntent;
        try {
            goalIntent = GoalIntent.valueOf(intent.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unmapped) {
            return null;
        }
        return switch (goalIntent) {
            case FORECAST -> CognitiveModelType.fromLegacyModelType("FORECAST").orElse(null);
            case DECIDE -> CognitiveModelType.fromLegacyModelType("DECISION").orElse(null);
            case SIMULATE -> CognitiveModelType.fromLegacyModelType("SIMULATION").orElse(null);
            // DIAGNOSE/RANK/MONITOR/BUILD/REVIEW：契约无 CM 映射（禁就近猜型，宁可 absent）
            default -> null;
        };
    }

    private static MetricValue backtestOf(List<Map<String, Object>> rows, Map<String, Object> ev) {
        ev.put("rows", rows.size());
        ev.put("modelIds", rows.stream().map(r -> String.valueOf(r.get("modelId")))
                .collect(java.util.stream.Collectors.toList()));
        Map<String, Object> sorted = rows.get(0);
        Object rawGate = sorted.get("backtestGate");
        if (rawGate == null || String.valueOf(rawGate).isBlank()) {
            ev.put("reason", "backtest-gate-absent-on-hit-row");
            ev.put("hitRowGate", null);
            return MetricValue.absent(null, ev);
        }
        Optional<GateOutcome> gate = GateOutcome.fromValue(String.valueOf(rawGate));
        if (gate.isEmpty()) {
            ev.put("reason", "backtest-gate-literal-unparsable:" + rawGate);
            ev.put("hitRowGate", String.valueOf(rawGate));
            return MetricValue.absent(null, ev);
        }
        ev.put("hitRowGate", gate.get().name());
        return MetricValue.bool(gate.get() == GateOutcome.PASS, ev);
    }

    private static Map<String, Object> baseEvidence(ReadinessCheckCode code, String path,
                                                     CognitiveModelType cm) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("source", "cognitive-model-readiness");
        ev.put("checkCode", code.checkCode());
        ev.put("endpoint", path);
        ev.put("cognitiveModelType", cm.code());
        ev.put("registryStateFilter", "PUBLISHED");
        return ev;
    }

    private static String intentLiteral(ReadinessProbeContext context) {
        return context == null || context.intent() == null ? "null" : context.intent();
    }
}
