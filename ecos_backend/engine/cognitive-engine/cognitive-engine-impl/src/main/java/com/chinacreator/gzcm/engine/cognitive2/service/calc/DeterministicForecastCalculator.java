package com.chinacreator.gzcm.engine.cognitive2.service.calc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 确定性计算产物装配器（PRD-05 §6.4 REQ-COG-09 规格 1/3，暂名 {@code DeterministicForecastCalculator} 的落地名）。
 *
 * <p><b>职责边界（三者互斥，不得互相回退）</b>：本组件只把<b>已有</b>确定性事实
 * （预测运行服务快照 {@code ecos_dw.ecos_application_forecast_run}、04 册已发布画像版本、
 * ontology 口径版本）装配成可重放一致的产物明细并做规范化序列化；
 * <b>不产生任何新数值</b>（金额唯一来源铁律 F05-10 / B-2 / R-8 ②），
 * 不做 What-if 探索（{@code ScenarioSimulatorServiceImpl} 职责），
 * 不做事后因果解释（{@code CausalReasonerService} 职责）。</p>
 *
 * <p><b>幂等六要素</b>（规格 3）：{@code forecastRunId + caliberId@caliberVersion + asOfTime +
 * 输入快照 + 公式版本 + 证据引用} 全部进入规范化串与幂等键 ⇒ 同六要素重跑必须字节一致
 * （验收 {@code DeterministicForecastArtifactTest#rerunWithSameSixFactorsIsByteIdentical}）。</p>
 *
 * <p>纯函数：禁依赖 Spring/DB（C.8 三层纪律的 Calculator 条），便于确定性复算单测。</p>
 */
public final class DeterministicForecastCalculator {

    private static final String FORMULA_DOMAIN = "cognitive-forecast-artifact-v1";

    private DeterministicForecastCalculator() {}

    /** 幂等六要素（规格 3 冻结口径）。 */
    public record SixFactors(String forecastRunId,
                             String caliberId,
                             String caliberVersion,
                             String asOfTime,
                             String inputSnapshotJson,
                             String formulaVersion,
                             List<String> evidenceRefs) {
        public SixFactors {
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    /** 产物明细行（维度键 + 由快照带来的既有数值，本组件不改写）。 */
    public record ArtifactRow(String projectId,
                              String departmentId,
                              String period,
                              String metric,
                              BigDecimal value,
                              String valueSource) {
    }

    /** 装配结果：规范化字节 + 幂等键 + 内容哈希 + 明细（落库走 data-engine 唯一写通道）。 */
    public record Artifact(String forecastRunId,
                           String idempotencyKey,
                           String contentHash,
                           byte[] canonicalBytes,
                           List<Map<String, Object>> rows) {
        public Artifact {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 装配产物。<b>输入完整性先判</b>：六要素任一缺失或 {@code valueSource} 缺失 ⇒ 抛
     * {@link IllegalArgumentException}（由调用方映射 {@code ECOS-COG-400P}），
     * 禁以默认值补齐后"重跑一致"（那会伪造幂等证据）。
     */
    public static Artifact compute(SixFactors factors, List<ArtifactRow> cells) {
        requireText(factors.forecastRunId(), "forecastRunId");
        requireText(factors.caliberId(), "caliberId");
        requireText(factors.caliberVersion(), "caliberVersion");
        requireText(factors.asOfTime(), "asOfTime");
        requireText(factors.inputSnapshotJson(), "inputSnapshotJson");
        requireText(factors.formulaVersion(), "formulaVersion");
        if (factors.evidenceRefs().isEmpty()) {
            throw new IllegalArgumentException("evidenceRefs 不可为空（无证据引用 ⇒ 产物不可追溯）");
        }

        List<ArtifactRow> sorted = cells == null ? List.of()
                : cells.stream()
                        .sorted(Comparator.comparing((ArtifactRow r) -> nz(r.projectId()))
                                .thenComparing(r -> nz(r.departmentId()))
                                .thenComparing(r -> nz(r.period()))
                                .thenComparing(r -> nz(r.metric())))
                        .toList();

        List<Map<String, Object>> rows = new ArrayList<>(sorted.size());
        for (ArtifactRow r : sorted) {
            requireText(r.valueSource(), "valueSource");
            if (r.value() == null) {
                throw new IllegalArgumentException("value 不可为 null（缺数即 FAIL，禁 0 替真值）");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", nz(r.projectId()));
            row.put("departmentId", nz(r.departmentId()));
            row.put("period", nz(r.period()));
            row.put("metric", nz(r.metric()));
            row.put("value", r.value().toPlainString());
            row.put("valueSource", r.valueSource());
            rows.add(row);
        }

        String canonical = canonical(factors, rows);
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        String idempotencyKey = FORMULA_DOMAIN + ":" + sha256Hex(sixFactorLine(factors));
        return new Artifact(factors.forecastRunId(), idempotencyKey, sha256Hex(canonical), bytes, rows);
    }

    /** 六要素定位串（进入幂等键）。 */
    private static String sixFactorLine(SixFactors f) {
        return String.join("|",
                "run=" + f.forecastRunId(),
                "caliber=" + f.caliberId() + "@" + f.caliberVersion(),
                "asOf=" + f.asOfTime(),
                "formula=" + f.formulaVersion(),
                "snapshot=" + sha256Hex(f.inputSnapshotJson()),
                "evidence=" + String.join(",", new java.util.TreeSet<>(f.evidenceRefs())));
    }

    /** 规范化序列化：键序稳定（TreeMap）、明细排序、无时间戳/无随机量 ⇒ 同输入同字节。 */
    private static String canonical(SixFactors f, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(sixFactorLine(f)).append('\n');
        for (Map<String, Object> row : rows) {
            new TreeMap<>(row).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用（JVM 必备算法）", e);
        }
    }

    private static void requireText(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(name + " 必填（六要素缺一即不可装配）");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
