package com.chinacreator.gzcm.engine.cognitive.api.asset;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 认知资产统一生命周期状态（《认知域与智能域补充详细设计 v1.0》§8.4 状态机词表单源）。
 *
 * <p><b>载体</b>：V268 三表唯一权威列 {@code lifecycle_state}
 * （{@code ecos_cognitive.ecos_cognitive_{hypothesis,belief,model}}）；存量 legacy
 * {@code status} 列停写不改语义，读侧 NULL 回落 legacy 展示（禁伪造新状态，V268 头注 ②）。
 * 本枚举 = 该列<b>唯一合法词表</b>（R-15 同纪律：DDL 不建 CHECK，词表收口契约层）。</p>
 *
 * <p><b>迁移矩阵（§8.4 冻结）</b>：主链
 * {@code DRAFT → CANDIDATE → EVIDENCE_CHECKED → VALIDATED → APPROVED → ACTIVE → SUPERSEDED}；
 * 异常态 {@code CONTRADICTED / EXPIRED / REJECTED / DEPRECATED / SUSPENDED}。
 * 额外冻结语义（每条均有出处）：</p>
 * <ul>
 *   <li>任意未终态 → {@code SUSPENDED}（§8.4 异常态「门禁触发暂时停用」= 可逆，恢复回原态）；</li>
 *   <li>任意未终态 → {@code EXPIRED}（§8.4「证据/数据快照/适用范围过期」）；</li>
 *   <li>{@code ACTIVE → CONTRADICTED}（§8.4「出现足以反驳当前判断的新证据」）；</li>
 *   <li>{@code ACTIVE → DEPRECATED}（「不再推荐，保留历史」）；</li>
 *   <li>{@code SUPERSEDED/CONTRADICTED → CANDIDATE}（§8.4「单次错误只触发<b>复审候选</b>」，
 *       经候选流程可再复活；禁止其余终态跳转）；</li>
 *   <li>SUPERSEDED 语义 = 生成新版本 + {@code supersedes} 指旧版（§8.4「不可原地覆盖」），
 *       实现细节归 impl，本枚举只管词表与合法性。</li>
 * </ul>
 *
 * <p>非法迁移处理单源 = {@code CognitiveErrorCode.E409_STATE_ILLEGAL}（409，原行不变）。</p>
 */
public enum CognitiveAssetStatus {

    DRAFT,
    CANDIDATE,
    EVIDENCE_CHECKED,
    VALIDATED,
    APPROVED,
    ACTIVE,
    SUPERSEDED,
    CONTRADICTED,
    EXPIRED,
    REJECTED,
    DEPRECATED,
    SUSPENDED;

    /** 迁移矩阵（不可变；新增迁移必须先改补充设计 §8.4 再入码）。 */
    private static final Map<CognitiveAssetStatus, Set<CognitiveAssetStatus>> TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT,            EnumSet.of(CANDIDATE, REJECTED, EXPIRED, SUSPENDED)),
            Map.entry(CANDIDATE,        EnumSet.of(EVIDENCE_CHECKED, REJECTED, EXPIRED, SUSPENDED)),
            Map.entry(EVIDENCE_CHECKED, EnumSet.of(VALIDATED, REJECTED, EXPIRED, SUSPENDED)),
            Map.entry(VALIDATED,        EnumSet.of(APPROVED, REJECTED, EXPIRED, SUSPENDED)),
            Map.entry(APPROVED,         EnumSet.of(ACTIVE, REJECTED, EXPIRED, SUSPENDED)),
            Map.entry(ACTIVE,           EnumSet.of(SUPERSEDED, CONTRADICTED, DEPRECATED, EXPIRED, SUSPENDED)),
            Map.entry(SUSPENDED,        EnumSet.of(ACTIVE, DRAFT, EXPIRED)),
            Map.entry(SUPERSEDED,       EnumSet.of(CANDIDATE)),
            Map.entry(CONTRADICTED,     EnumSet.of(CANDIDATE)),
            Map.entry(EXPIRED,          EnumSet.noneOf(CognitiveAssetStatus.class)),
            Map.entry(REJECTED,         EnumSet.noneOf(CognitiveAssetStatus.class)),
            Map.entry(DEPRECATED,       EnumSet.of(CANDIDATE))
    );

    /**
     * 合法迁移判定（§8.4 状态机唯一裁决点，{@code from.canTransitTo(to)}）。
     *
     * @param to 请求迁入状态（null 非法；自迁移非法）
     * @return true = 允许
     */
    public boolean canTransitTo(CognitiveAssetStatus to) {
        if (to == null || to == this) {
            return false;
        }
        return TRANSITIONS.get(this).contains(to);
    }

    /** 合法后续状态集合（文档/前端状态矩阵共用，稳定可 diff）。 */
    public Set<CognitiveAssetStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** 是否终态（无出站迁移）。 */
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }

    /** 是否终局（版本链意义上的生成/替代终态）。 */
    public boolean isSupersededTerminal() {
        return this == SUPERSEDED || this == REJECTED || this == DEPRECATED;
    }

    /** DB 值解析（大小写不敏感；未登记录面 ⇒ empty 由调用方 fail-loud，禁静默默认）。 */
    public static Optional<CognitiveAssetStatus> fromDbValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 词表字面量集合（目录/文档面单源）。 */
    public static Set<String> vocabulary() {
        return java.util.Arrays.stream(values()).map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
