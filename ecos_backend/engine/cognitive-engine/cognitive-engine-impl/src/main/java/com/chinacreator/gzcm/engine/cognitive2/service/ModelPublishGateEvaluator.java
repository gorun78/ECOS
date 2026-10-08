package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import com.chinacreator.gzcm.engine.cognitive.api.GateOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 四类发布门禁裁决器（REQ-COG-07 规格 4 / 附件第三册 §15.1 引第二册 §17.7；V261 四门禁列）。
 *
 * <p><b>真实裁决、禁 return true 桩</b>——每门禁只对版本行"已登记证据"做可复核判定，
 * 输出 PASS/FAIL/PENDING 三值（{@link GateOutcome} 单源），并在 reason 里给出判据：</p>
 * <ul>
 *   <li>{@code contract} = 契约自洽：{@code cognitive_model_type} 已映射且属 CM-01~07 冻结枚举、
 *       {@code model_id} 非空、{@code version >= 1}、features 若存在必须为合法 JSON。缺项 ⇒ FAIL；</li>
 *   <li>{@code backtest} = 已登记回测证据：{@code backtest_score} 非空且 ∈ [0,1]（V192 列注释
 *       0~1 值域）。缺项/越界 ⇒ FAIL。<em>开放项</em>：通过阈值（score ≥ X）在需求单源
 *       （PRD-05/V261）中未定义 ⇒ 本批只裁决"证据存在性与值域"，阈值上线须先入需求单源，
 *       禁代码私设；</li>
 *   <li>{@code regression} = 回归集：本仓不存在任何回归语料载体（无回归集表/服务注册，
 *       详设-05 F 系列亦未给出）⇒ <b>不可评测，一律 PENDING fail-closed</b>（发布必拒），
 *       禁 return true 假绿；回归语料设施落地后此项升级为真实比对；</li>
 *   <li>{@code calibration} = 校准证据：features JSON 内须携带 {@code calibration} 对象且含
 *       非空 {@code method} 与数值型 {@code error}/{@code score}（∈ [0,1]）。缺证据 ⇒ FAIL。</li>
 * </ul>
 *
 * <p>升格裁决 = 四列全 PASS；任一 FAIL/PENDING ⇒ 停留 DRAFT 且发布端点显式 4xx（规格 4"不是静默成功"）。</p>
 */
@Component
public class ModelPublishGateEvaluator {

    /** 单门裁决 = 三值 outcome + 判据 reason。 */
    public record GateResult(GateOutcome outcome, String reason) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, GateResult> evaluate(Map<String, Object> row) {
        Map<String, GateResult> results = new LinkedHashMap<>();
        results.put(GateOutcome.GATE_CONTRACT, contract(row));
        results.put(GateOutcome.GATE_REGRESSION, regression(row));
        results.put(GateOutcome.GATE_BACKTEST, backtest(row));
        results.put(GateOutcome.GATE_CALIBRATION, calibration(row));
        return results;
    }

    /** 全 PASS 判定（发布升格唯一放行条件）。 */
    public static boolean allPassed(Map<String, GateResult> results) {
        return results.values().stream().allMatch(r -> r.outcome() == GateOutcome.PASS);
    }

    /** 未过项清单（4xx 响应 data.missing 用，顺序固定 = 裁决顺序）。 */
    public static List<String> notPassed(Map<String, GateResult> results) {
        return results.entrySet().stream()
                .filter(e -> e.getValue().outcome() != GateOutcome.PASS)
                .map(e -> e.getKey() + ":" + e.getValue().outcome().name())
                .toList();
    }

    // ── contract：契约自洽 ──

    GateResult contract(Map<String, Object> row) {
        String cm = str(row.get("cognitive_model_type"));
        if (cm == null || cm.isBlank()) {
            return new GateResult(GateOutcome.FAIL, "cognitive_model_type unmapped (NULL)");
        }
        if (CognitiveModelType.fromCode(cm).isEmpty()) {
            return new GateResult(GateOutcome.FAIL, "cognitive_model_type not in frozen CM-01~CM-07: " + cm);
        }
        String modelId = str(row.get("model_id"));
        if (modelId == null || modelId.isBlank()) {
            return new GateResult(GateOutcome.FAIL, "model_id blank");
        }
        Integer version = asInt(row.get("version"));
        if (version == null || version < 1) {
            return new GateResult(GateOutcome.FAIL, "version must be >= 1, got: " + row.get("version"));
        }
        JsonNode features = parseFeatures(row);
        if (features == BOOLEAN_FALSE_PLACEHOLDER) {
            return new GateResult(GateOutcome.FAIL, "features content is not valid JSON");
        }
        return new GateResult(GateOutcome.PASS, "cm_type/model_id/version/features contract ok");
    }

    // ── regression：本仓不可评测 ⇒ PENDING fail-closed ──

    GateResult regression(Map<String, Object> row) {
        // 真实评测需要回归语料（golden set）载体；全仓（schema 单源目录 + 引擎代码）零命中，
        // 按任务口径 fail-closed：PENDING ⇒ 发布必拒。此处禁 return PASS 桩。
        return new GateResult(GateOutcome.PENDING,
                "no regression corpus carrier exists in this repository - gate cannot be evaluated, fail-closed");
    }

    // ── backtest：已登记回测证据的存在性与值域 ──

    GateResult backtest(Map<String, Object> row) {
        Object raw = row.get("backtest_score");
        if (raw == null) {
            return new GateResult(GateOutcome.FAIL, "backtest_score not recorded");
        }
        BigDecimal score;
        try {
            score = raw instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return new GateResult(GateOutcome.FAIL, "backtest_score not numeric: " + raw);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            return new GateResult(GateOutcome.FAIL,
                    "backtest_score out of [0,1] (V192 值域): " + score.toPlainString());
        }
        // 通过阈值单源未定义（见类 Javadoc 开放项）——本批只裁决证据存在性与值域，禁私设阈值。
        return new GateResult(GateOutcome.PASS, "backtest_score recorded and in range: " + score.toPlainString());
    }

    // ── calibration：features.calibration {method, error|score∈[0,1]} ──

    GateResult calibration(Map<String, Object> row) {
        JsonNode features = parseFeatures(row);
        if (features == null || !features.isObject()) {
            return new GateResult(GateOutcome.FAIL, "no features object - calibration evidence missing");
        }
        JsonNode cal = features.get("calibration");
        if (cal == null || !cal.isObject()) {
            return new GateResult(GateOutcome.FAIL, "features.calibration not recorded");
        }
        JsonNode method = cal.get("method");
        if (method == null || method.asText().isBlank()) {
            return new GateResult(GateOutcome.FAIL, "features.calibration.method blank");
        }
        JsonNode error = cal.has("error") ? cal.get("error") : cal.get("score");
        if (error == null || !error.isNumber()) {
            return new GateResult(GateOutcome.FAIL, "features.calibration.error|score missing or non-numeric");
        }
        BigDecimal v = error.decimalValue();
        if (v.compareTo(BigDecimal.ZERO) < 0 || v.compareTo(BigDecimal.ONE) > 0) {
            return new GateResult(GateOutcome.FAIL, "features.calibration value out of [0,1]: " + v.toPlainString());
        }
        return new GateResult(GateOutcome.PASS,
                "calibration evidence ok (" + method.asText() + ", " + v.toPlainString() + ")");
    }

    // ── helpers ──

    /** features 解析哨兵：非法 JSON（区别于"无 features"的 null）。 */
    private static final JsonNode BOOLEAN_FALSE_PLACEHOLDER = MAPPER.getNodeFactory().booleanNode(false);

    private JsonNode parseFeatures(Map<String, Object> row) {
        Object raw = row.containsKey("features_json") ? row.get("features_json") : row.get("features");
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        try {
            return MAPPER.readTree(String.valueOf(raw));
        } catch (Exception invalid) {
            return BOOLEAN_FALSE_PLACEHOLDER;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
