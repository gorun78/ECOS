package com.chinacreator.gzcm.engine.kb.service.assumption;

import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REQ-KB-03 情景假设服务（F04-07，P0 新能力）。
 *
 * <p>状态机 {@code DRAFT → PENDING_APPROVAL → ACTIVE → [EXPIRED | SUPERSEDED]}（单向，Forbidden 跃迁 409）：
 * <ul>
 *   <li>{@link #create} 建 DRAFT（{@code reason} 必填）；</li>
 *   <li>{@link #submit} DRAFT→PENDING_APPROVAL；</li>
 *   <li>{@link #approve} PENDING_APPROVAL→ACTIVE（同 key 既有 ACTIVE 转 SUPERSEDED）；</li>
 *   <li>{@link #assertUpdatable} 尝试改 ACTIVE → 409 APPROVED 不可变（I-6）；</li>
 *   <li>{@link #resolve} 仅 ACTIVE 且未到期可被预测引用，DELTA 语义解析为相对基准值。</li>
 * </ul>
 *
 * <p>{@code evidence_ref} 缺失 → {@code isUnverified=1}（I-4 禁静默关闭，UI 必呈现）；
 * 纯计算 + 落库，不持有 {@code Executors}/{@code @Scheduled}（铁律 §2.5-3）。
 */
public class KbAssumptionService {

    /** DELTA 语义（相对基准增量）。 */
    public static final String SEMANTIC_ABSOLUTE = "ABSOLUTE";
    /** ABSOLUTE 语义（绝对值）。 */
    public static final String SEMANTIC_DELTA = "DELTA";

    private final KbAssumptionStore store;

    public KbAssumptionService(KbAssumptionStore store) {
        this.store = store;
    }

    /**
     * 建 DRAFT 假设。
     *
     * @throws IllegalStateException reason 缺失 → 400（PRD-04 §3.2-3 必填）
     */
    public KbAssumptionDO create(KbAssumptionDO in, String actor) {
        if (in.getReason() == null || in.getReason().isBlank()) {
            throw new IllegalArgumentException(
                    KbErrorCodes.STATUS_TRANSITION_INVALID.code()
                            + ": 假设 reason 必填（PRD-04 §3.2-3）");
        }
        long now = System.currentTimeMillis();
        in.setId(UUID.randomUUID().toString());
        if (in.getAssumptionKey() == null || in.getAssumptionKey().isBlank()) {
            in.setAssumptionKey(assumptionKey(in.getScenarioType(), in.getMetricCode(), in.getDimScopeJson()));
        }
        if (in.getValueSemantic() == null || in.getValueSemantic().isBlank()) {
            in.setValueSemantic(SEMANTIC_ABSOLUTE);
        }
        in.setIsUnverified((in.getEvidenceRef() == null || in.getEvidenceRef().isBlank()) ? 1 : 0);
        in.setAssumptionVersion(nextVersion(in.getAssumptionKey()));
        in.setStatus("DRAFT");
        if (in.getDomain() == null || in.getDomain().isBlank()) {
            in.setDomain("kb");
        }
        if (in.getCreateBy() == null) {
            in.setCreateBy(actor);
        }
        in.setUpdateBy(actor);
        in.setCreateTime(now);
        in.setUpdateTime(now);
        store.insert(in);
        return in;
    }

    /** DRAFT → PENDING_APPROVAL（其余状态提交 → 409 非法跃迁）。 */
    public KbAssumptionDO submit(String id, String actor) {
        KbAssumptionDO row = require(id);
        if (!"DRAFT".equals(row.getStatus())) {
            throw illegalTransition(row, "PENDING_APPROVAL");
        }
        long now = System.currentTimeMillis();
        row.setStatus("PENDING_APPROVAL");
        row.setUpdateBy(actor);
        row.setUpdateTime(now);
        store.insert(row);
        return row;
    }

    /** PENDING_APPROVAL → ACTIVE（同 key 既有 ACTIVE 转 SUPERSEDED；非 PENDING → 409）。 */
    public KbAssumptionDO approve(String id, String actor) {
        KbAssumptionDO row = require(id);
        if (!"PENDING_APPROVAL".equals(row.getStatus())) {
            throw illegalTransition(row, "ACTIVE");
        }
        long now = System.currentTimeMillis();
        row.setStatus("ACTIVE");
        row.setApprovedBy(actor);
        row.setApprovedTime(now);
        row.setUpdateBy(actor);
        row.setUpdateTime(now);
        store.insert(row);
        supersedeOtherActive(row.getAssumptionKey(), row.getId());
        return row;
    }

    /** 尝试修改 ACTIVE 假设 → 409 APPROVED 不可变（I-6）。 */
    public void assertUpdatable(String id) {
        KbAssumptionDO row = require(id);
        if ("ACTIVE".equals(row.getStatus())) {
            throw new KbErrorCodes.DomainException(
                    KbErrorCodes.APPROVED_ASSUMPTION_MODIFICATION.httpStatus(),
                    KbErrorCodes.APPROVED_ASSUMPTION_MODIFICATION.code(),
                    "已批准假设不可修改，请建新版本 id=" + id);
        }
    }

    /**
     * 解析情景假设供预测引用（仅 ACTIVE 且未到期）：返回 DELTA 已相对基准归一后的有效值。
     *
     * @return 命中行 + 解析后的有效值（未命中 = profile null）
     */
    public ResolveResult resolve(String scenarioType, String metricCode, String period, long now) {
        List<KbAssumptionDO> candidates = store.query(scenarioType, metricCode, "ACTIVE");
        KbAssumptionDO hit = null;
        for (KbAssumptionDO c : candidates) {
            if (isWithinWindow(c, period)) {
                if (hit == null) {
                    hit = c;
                }
            }
        }
        if (hit == null) {
            // 无 ACTIVE 命中（或全部过期）→ 不可引用；仍回候选首个用于上层判 410
            return new ResolveResult(candidates.isEmpty() ? null : candidates.get(0), null, false);
        }
        if (hit.getExpireAt() > 0 && hit.getExpireAt() < now) {
            // ACTIVE 但已到期 → EXPIRED 语义，不可引用（410）
            return new ResolveResult(hit, null, true);
        }
        BigDecimal effective = effectiveValue(hit);
        return new ResolveResult(hit, effective, false);
    }

    /** 解析结果：命中行、有效值（null = 不可引用）、expired 标记。 */
    public record ResolveResult(KbAssumptionDO assumption, BigDecimal effectiveValue, boolean expired) {
    }

    // ── 有效值解析（纯函数，可单测）─────────────────────────────

    /**
     * DELTA 语义：以当期命中值作为相对增量叠加到基准假设值；ABSOLUTE：直接返回自身值。
     * 基准行缺失且语义为 DELTA → fail-loud（禁静默落到 0）。
     */
    public BigDecimal effectiveValue(KbAssumptionDO row) {
        if (row == null || row.getValue() == null) {
            return null;
        }
        if (SEMANTIC_DELTA.equals(row.getValueSemantic()) && row.getBaseAssumptionId() != null
                && !row.getBaseAssumptionId().isBlank()) {
            KbAssumptionDO base = store.findById(row.getBaseAssumptionId());
            if (base == null || base.getValue() == null) {
                throw new KbErrorCodes.DomainException(
                        KbErrorCodes.STATUS_TRANSITION_INVALID.httpStatus(),
                        KbErrorCodes.STATUS_TRANSITION_INVALID.code(),
                        "DELTA 假设基准缺失 base=" + row.getBaseAssumptionId());
            }
            return base.getValue().add(row.getValue()).setScale(4, RoundingMode.HALF_UP);
        }
        return row.getValue().setScale(4, RoundingMode.HALF_UP);
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    private KbAssumptionDO require(String id) {
        KbAssumptionDO row = store.findById(id);
        if (row == null) {
            throw new KbErrorCodes.DomainException(
                    KbErrorCodes.STATUS_TRANSITION_INVALID.httpStatus(),
                    KbErrorCodes.STATUS_TRANSITION_INVALID.code(),
                    "假设不存在 id=" + id);
        }
        return row;
    }

    private KbErrorCodes.DomainException illegalTransition(KbAssumptionDO row, String target) {
        return new KbErrorCodes.DomainException(
                KbErrorCodes.STATUS_TRANSITION_INVALID.httpStatus(),
                KbErrorCodes.STATUS_TRANSITION_INVALID.code(),
                "假设状态机非法跃迁 status=" + row.getStatus() + " → " + target + " id=" + row.getId());
    }

    /** 期窗口命中：validFrom/validTo 空 = 全期；否则 period（YYYY-MM）落在区间。 */
    private static boolean isWithinWindow(KbAssumptionDO c, String period) {
        if (period == null || period.isBlank()) {
            return true;
        }
        if (c.getValidFrom() != null && !c.getValidFrom().isBlank()
                && period.compareTo(c.getValidFrom()) < 0) {
            return false;
        }
        if (c.getValidTo() != null && !c.getValidTo().isBlank()
                && period.compareTo(c.getValidTo()) > 0) {
            return false;
        }
        return true;
    }

    private void supersedeOtherActive(String key, String keepId) {
        for (KbAssumptionDO other : store.query(null, null, "ACTIVE")) {
            if (!keepId.equals(other.getId())
                    && java.util.Objects.equals(other.getAssumptionKey(), key)) {
                other.setStatus("SUPERSEDED");
                other.setUpdateTime(System.currentTimeMillis());
                store.insert(other);
            }
        }
    }

    private String nextVersion(String key) {
        KbAssumptionDO latest = store.findLatestByKey(key);
        int n = latest == null ? 1 : nextOrdinal(latest.getAssumptionVersion());
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

    private static String assumptionKey(String scenario, String metric, String dimScopeJson) {
        StringBuilder sb = new StringBuilder(scenario == null ? "" : scenario)
                .append('|').append(metric == null ? "" : metric).append('|');
        if (dimScopeJson != null && !dimScopeJson.isBlank() && !"{}".equals(dimScopeJson.trim())) {
            sb.append(dimScopeJson.trim());
        }
        return sb.toString();
    }

    /** 供测试/上层构造预置维度（不被 DB 依赖）。 */
    public static Map<String, String> dimScope(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    /** 供上层组装 dimScopeJson。 */
    public static String serializeDimScope(Map<String, String> dims) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : dims == null ? Map.<String, String>of().entrySet() : dims.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
            first = false;
        }
        return sb.append('}').toString();
    }

    /** 构造期列表占位（预留，供 resolve 多期扩展）。 */
    public static List<String> periods(String from, String to) {
        List<String> out = new ArrayList<>();
        out.add(from);
        out.add(to);
        return out;
    }
}
