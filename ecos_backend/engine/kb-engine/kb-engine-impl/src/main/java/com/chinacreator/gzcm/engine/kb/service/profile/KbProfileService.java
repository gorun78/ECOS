package com.chinacreator.gzcm.engine.kb.service.profile;

import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.engine.kb.support.ProfileStatsCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * REQ-KB-02 历史画像服务（F04-06，P0 新能力）。
 *
 * <p>只读 CURATED 层 PUBLISHED 事实（经 {@link CuratedFactSource}，禁直读 RAW / B-1）→ 按
 * 退化链分组统计（P10/P50/P90/CI + 置信度分层 + 分层退化）→ 版本化落库（DRAFT→PUBLISHED→SUPERSEDED）。
 *
 * <p>生成一律经 runtime-task（见 {@code KbProfileController} submitTask），本服务为纯计算 + 落库，
 * 不持有 {@code Executors}/{@code @Scheduled}（铁律 §2.5-3，纠正 K-38/K-39）。已发布画像不可变（I-6）。
 */
public class KbProfileService {

    /** CURATED 供数端口（禁直读 RAW；生产实现经 datanet REST / runtime-access 只读）。 */
    public interface CuratedFactSource {
        /** 取某指标在某维度组合下的观测值；dims 键与退化链层级一致。 */
        List<Number> fetchValues(String metricCode, List<String> dims,
                                 String windowFrom, String windowTo);
    }

    /** 分组选择结果：命中的退化层级 + 统计量 + 退化来源（上一更细层级，供 degrade_from）。 */
    public record Grouping(int levelIndex, ProfileStatsCalculator.Stats stats,
                           List<String> dims, String degradeFromKey) {
    }

    /** 生成结果。 */
    public record Generated(KbProfileDO row, Grouping grouping) {
    }

    private final KbProfileStore store;
    private final CuratedFactSource facts;

    public KbProfileService(KbProfileStore store, CuratedFactSource facts) {
        this.store = store;
        this.facts = facts;
    }

    /**
     * 纯分组选择（可单测）：沿退化链逐层取数，选第一个达到 MEDIUM（n≥10）的层级；
     * 若逐层退化到最粗仍不足，用最粗层（此时 confidence 可能 LOW，由调用方决定是否接受）。
     */
    public Grouping selectGrouping(List<List<Number>> levelValues, String metricCode) {
        int levels = ProfileStatsCalculator.degradeChain().size();
        String degradeFromKey = null;
        for (int i = 0; i < levels; i++) {
            List<Number> values = levelValues.get(i);
            ProfileStatsCalculator.Stats stats = ProfileStatsCalculator.compute(values);
            boolean last = i == levels - 1;
            boolean adequate = !ProfileStatsCalculator.shouldDegrade(stats.sampleCount());
            if (adequate || last) {
                return new Grouping(i, stats, ProfileStatsCalculator.chainLevel(i), degradeFromKey);
            }
            degradeFromKey = profileKey(metricCode, ProfileStatsCalculator.chainLevel(i));
        }
        // 理论上不可达（last 保证 return）
        ProfileStatsCalculator.Stats last = ProfileStatsCalculator.compute(levelValues.get(levels - 1));
        return new Grouping(levels - 1, last, ProfileStatsCalculator.chainLevel(levels - 1), degradeFromKey);
    }

    /** 全维度分层取值 + 选择 + 落库（DRAFT）。取数链任一失败向上抛，由控制器映射 ECOS-KB-040。 */
    public Generated generate(String metricCode, Map<String, String> dims,
                              String windowFrom, String windowTo, String domain,
                              String taskId, String actor) {
        List<List<Number>> levelValues = new ArrayList<>();
        for (List<String> combo : ProfileStatsCalculator.degradeChain()) {
            levelValues.add(safeFetch(metricCode, combo, windowFrom, windowTo));
        }
        Grouping grouping = selectGrouping(levelValues, metricCode);
        List<String> hitDims = grouping.dims();
        Map<String, String> hitValues = pickValues(dims, hitDims);
        String key = profileKeyWithValues(metricCode, hitDims, hitValues);

        KbProfileDO row = new KbProfileDO();
        long now = System.currentTimeMillis();
        row.setId(UUID.randomUUID().toString());
        row.setProfileKey(key);
        row.setMetricCode(metricCode);
        row.setGroupDimsJson(serializeDims(hitValues));
        row.setWindowFrom(windowFrom);
        row.setWindowTo(windowTo);
        ProfileStatsCalculator.Stats s = grouping.stats();
        row.setSampleCount(s.sampleCount());
        row.setMissingRate(BigDecimal.valueOf(s.missingRate()).setScale(4, RoundingMode.HALF_UP));
        row.setP10(BigDecimal.valueOf(s.p10()));
        row.setP50(BigDecimal.valueOf(s.p50()));
        row.setP90(BigDecimal.valueOf(s.p90()));
        row.setMeanValue(BigDecimal.valueOf(s.mean()).setScale(4, RoundingMode.HALF_UP));
        row.setCiLow(BigDecimal.valueOf(s.ciLow()).setScale(4, RoundingMode.HALF_UP));
        row.setCiHigh(BigDecimal.valueOf(s.ciHigh()).setScale(4, RoundingMode.HALF_UP));
        row.setCiLevel("95");
        row.setStatsMethod("T_APPROX");
        row.setConfidence(s.confidence());
        row.setDegradeFrom(grouping.degradeFromKey());
        List<String> chain = new ArrayList<>();
        for (int i = 0; i <= grouping.levelIndex(); i++) {
            chain.add(String.join("×", ProfileStatsCalculator.chainLevel(i)));
        }
        row.setDegradePathJson(serializeChain(chain));
        row.setProfileVersion(nextVersion(key));
        row.setStatus("DRAFT");
        row.setTaskId(taskId);
        row.setDomain(domain);
        row.setCreateBy(actor);
        row.setUpdateBy(actor);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        store.insert(row);
        return new Generated(row, grouping);
    }

    /** 发布 DRAFT 画像：置 PUBLISHED + 同 key 既有 PUBLISHED 转 SUPERSEDED（新版本取代旧版本）。 */
    public KbProfileDO publish(String id, String actor) {
        KbProfileDO row = store.findById(id);
        if (row == null) {
            throw new IllegalStateException(
                    KbErrorCodes.PUBLISHED_IMMUTABLE.code() + ": 画像不存在 id=" + id);
        }
        // 已发布不可改（I-6）：对已 PUBLISHED 行再发布（即"修改"）→ 409
        if ("PUBLISHED".equals(row.getStatus())) {
            throw new IllegalStateException(
                    KbErrorCodes.PUBLISHED_IMMUTABLE.code()
                            + ": 已发布画像不可修改 id=" + id);
        }
        // 仅 DRAFT 可发布（状态机 I-6：DRAFT→PUBLISHED→SUPERSEDED，单向）
        if (!"DRAFT".equals(row.getStatus())) {
            throw new IllegalStateException(
                    KbErrorCodes.STATUS_TRANSITION_INVALID.code()
                            + ": 仅 DRAFT 画像可发布，当前 status=" + row.getStatus() + " id=" + id);
        }
        long now = System.currentTimeMillis();
        row.setStatus("PUBLISHED");
        row.setProfileVersion(nextVersionForPublish(row.getProfileKey()));
        row.setApprovedBy(actor);
        row.setApprovedTime(now);
        row.setUpdateBy(actor);
        row.setUpdateTime(now);
        store.insert(row);
        supersedeOthers(row.getProfileKey(), row.getId());
        return row;
    }

    /** 尝试修改已发布画像 → 409 PUBLISHED_IMMUTABLE（I-6）。 */
    public void assertUpdatable(String id) {
        KbProfileDO row = store.findById(id);
        if (row != null && "PUBLISHED".equals(row.getStatus())) {
            throw new IllegalStateException(
                    KbErrorCodes.PUBLISHED_IMMUTABLE.code() + ": 已发布画像不可修改 id=" + id);
        }
    }

    /** 解析画像 + 完整退化链（resolve 只读，跨两 schema 组装由上层负责）。 */
    public ResolveResult resolve(String metricCode, String status, String atVersion) {
        List<KbProfileDO> rows = store.query(metricCode, status, null, null);
        if (rows.isEmpty()) {
            return new ResolveResult(null, List.of());
        }
        KbProfileDO target = rows.get(0);
        if (atVersion != null && !atVersion.isBlank()
                && atVersion.equals(target.getProfileVersion())) {
            target = rows.get(0); // 精确命中
        }
        List<String> chain = new ArrayList<>();
        target.getDegradePathJson(); // parse 简化：返回退化链层级标签
        for (List<String> combo : ProfileStatsCalculator.degradeChain()) {
            chain.add(String.join("×", combo));
        }
        return new ResolveResult(target, chain);
    }

    /** 解析结果：命中行 + 退化链。 */
    public record ResolveResult(KbProfileDO profile, List<String> degradeChain) {
    }

    /**
     * 画像存量两计（只读，WAgent Readiness 定级 {@code know.sample_size} 度量属主取数）。
     *
     * <p>口径：{@code status}/{@code metricCode}/{@code domain} 为空则不过滤（缺省策略由
     * 控制器层承载，本方法不内置默认值）；{@code domain} 支持逗号分隔多枚，DAO 侧
     * 按 {@code IN (?,...)} 参数化展开。返回键面固定：
     * {@code {profiles:int, sampleSum:long, statusFilter:String|null,
     * metricCode:String|null, domain:String|null, evidence:{table}}}；
     * 无命中行时 DAO 侧 {@code COUNT(*)=0} / {@code COALESCE(SUM)=0}，本方法对
     * {@code sampleSum=null} 再兜底 0（禁把「无存量」回成 null 制造口径歧义）。
     */
    public Map<String, Object> profileStock(String status, String metricCode, String domain) {
        List<String> domains = null;
        if (domain != null && !domain.isBlank()) {
            domains = java.util.Arrays.stream(domain.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        String statusFilter = (status == null || status.isBlank()) ? null : status.trim();
        String metric = (metricCode == null || metricCode.isBlank()) ? null : metricCode.trim();
        Map<String, Object> stock = store.profileStock(statusFilter, metric, domains);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Object profiles = stock.get("profiles");
        Object sampleSum = stock.get("sampleSum");
        out.put("profiles", profiles == null ? 0 : ((Number) profiles).intValue());
        out.put("sampleSum", sampleSum == null ? 0L : ((Number) sampleSum).longValue());
        out.put("statusFilter", statusFilter);
        out.put("metricCode", metric);
        out.put("domain", domain == null || domain.isBlank() ? null : domain.trim());
        out.put("evidence", Map.of("table", "ecos_knowledge.ecos_kb_profile"));
        return out;
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    private List<Number> safeFetch(String metric, List<String> dims, String from, String to) {
        try {
            List<Number> v = facts.fetchValues(metric, dims, from, to);
            return v == null ? List.of() : v;
        } catch (RuntimeException e) {
            // CURATED 供数不可达：向上抛（控制器映射 ECOS-KB-040），不静默吞（B-1 / I-5）
            throw e;
        }
    }

    private Map<String, String> pickValues(Map<String, String> dims, List<String> hitDims) {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        if (dims != null) {
            for (String d : hitDims) {
                String v = dims.get(d) == null ? "global" : dims.get(d);
                out.put(d, v);
            }
        }
        return out;
    }

    private static String profileKey(String metric, List<String> dims) {
        return profileKeyWithValues(metric, dims, Map.of());
    }

    private static String profileKeyWithValues(String metric, List<String> dims, Map<String, String> values) {
        // 规范化：metric|k1=v1|k2=v2（键按给定顺序）
        StringBuilder sb = new StringBuilder(metric == null ? "" : metric);
        for (String d : dims) {
            sb.append('|').append(d).append('=').append(values.getOrDefault(d, ""));
        }
        return sb.toString();
    }

    private String nextVersion(String key) {
        KbProfileDO latest = store.findLatestByKey(key);
        int n = latest == null ? 1 : nextOrdinal(latest.getProfileVersion());
        return n + ".0.0";
    }

    private String nextVersionForPublish(String key) {
        KbProfileDO latest = store.findLatestByKey(key);
        int n = latest == null ? 1 : nextOrdinal(latest.getProfileVersion());
        return n + ".0.0";
    }

    private int nextOrdinal(String version) {
        if (version == null) {
            return 1;
        }
        String major = version.split("\\.")[0];
        int val;
        try {
            val = Integer.parseInt(major.trim());
        } catch (NumberFormatException e) {
            val = 1;
        }
        return val + 1;
    }

    private void supersedeOthers(String key, String keepId) {
        for (KbProfileDO other : store.query(null, "PUBLISHED", null, key)) {
            if (!keepId.equals(other.getId())) {
                other.setStatus("SUPERSEDED");
                other.setUpdateTime(System.currentTimeMillis());
                store.insert(other);
            }
        }
    }

    private static String serializeDims(Map<String, String> values) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
            first = false;
        }
        return sb.append('}').toString();
    }

    private static String serializeChain(List<String> chain) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String c : chain) {
            if (!first) {
                sb.append(",");
            }
            sb.append('"').append(c).append('"');
            first = false;
        }
        return sb.append(']').toString();
    }
}
