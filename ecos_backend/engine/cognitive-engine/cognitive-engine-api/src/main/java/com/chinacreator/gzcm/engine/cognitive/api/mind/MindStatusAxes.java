package com.chinacreator.gzcm.engine.cognitive.api.mind;

import java.util.Set;

/**
 * R4 Mind 三维状态三轴词表单源（详设 §8.4.2 R4 轮；V292 加列）。
 *
 * <p><b>三轴独立、禁止合并回单 {@code status}</b>——V187 单列只停写不删（IR03），
 * 治理真值恒走本词表三轴。词表守护纪律同 {@link MindCapabilityMask}：
 * 应用层把守，DDL CHECK 延后（V187 头注 4 同纪律），
 * 静态面由 {@code MindAggregateSchemaGateTest} 钉 V292 默认值。</p>
 *
 * <ul>
 *   <li>{@link Validity#VALID} — 认知有效性（validity_status）</li>
 *   <li>{@link Review#DRAFT}   — 审查治理（review_status）</li>
 *   <li>{@link Adoption#NOT_ADOPTED} — 业务采纳（adoption_status）</li>
 * </ul>
 */
public final class MindStatusAxes {

    private MindStatusAxes() {}

    /** 认知有效性轴。DDL 默认 PENDING_REVIEW。 */
    public enum Validity {
        /** 有效（候选 ACCEPTED 后治理面认定）。 */
        VALID,
        /** 待复核（V292 DDL 默认；存量行读面恒显此态的诚实投影）。 */
        PENDING_REVIEW,
        /** 已作废（被 superseded 或治理判定失效；只经受控端点置位）。 */
        INVALIDATED;

        private static final Set<String> NAMES = Set.of("VALID", "PENDING_REVIEW", "INVALIDATED");

        public static Validity fromName(String raw) {
            return raw == null ? null : NAMES.contains(raw) ? valueOf(raw) : null;
        }
    }

    /** 审查治理轴。DDL 默认 DRAFT。 */
    public enum Review {
        /** 草稿（V292 DDL 默认）。 */
        DRAFT,
        /** 复核中（候选提交后进入）。 */
        PENDING,
        /** 已批准（候选 ACCEPTED 终点）。 */
        APPROVED;

        private static final Set<String> NAMES = Set.of("DRAFT", "PENDING", "APPROVED");

        public static Review fromName(String raw) {
            return raw == null ? null : NAMES.contains(raw) ? valueOf(raw) : null;
        }
    }

    /** 业务采纳轴。DDL 默认 NOT_ADOPTED。 */
    public enum Adoption {
        /** 未采纳（V292 DDL 默认）。 */
        NOT_ADOPTED,
        /** 已采纳。 */
        ADOPTED,
        /** 已撤回。 */
        WITHDRAWN;

        private static final Set<String> NAMES = Set.of("NOT_ADOPTED", "ADOPTED", "WITHDRAWN");

        public static Adoption fromName(String raw) {
            return raw == null ? null : NAMES.contains(raw) ? valueOf(raw) : null;
        }
    }
}
