package com.chinacreator.gzcm.engine.cognitive.api.mind;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * R4 Mind 候选更新闸门契约（详设 v2.1-R4 §8.4.2 {@code ecos_cognitive_mind_update} /
 * 差距分析 P0-6 / B3 认知治理批）。
 *
 * <p>状态机单源：提交恒 {@link #STATUS_PROPOSED}（W Agent 通道等价字面），
 * 终态 {@link #STATUS_ACCEPTED} / {@link #STATUS_REJECTED} 只经受控端点
 * {@code PUT …/proposals/{updateId}/status} 置位并留痕（reviewed_by/at/reason）。
 * 既有错误码冻结：{@code ECOS-COG-404U} 行不存在／{@code ECOS-COG-409U} 已终态。</p>
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class MindUpdateVO {

    public static final String STATUS_PROPOSED  = "PROPOSED";
    public static final String STATUS_ACCEPTED  = "ACCEPTED";
    public static final String STATUS_REJECTED  = "REJECTED";

    /** 候选作用对象词表单源（target_type；DDL CHECK 延后纪律同 V187 头注 4）。 */
    public static final java.util.Set<String> TARGET_TYPES =
            java.util.Set.of("BELIEF", "HYPOTHESIS", "CONTEXT", "EXPLANATION");

    public static boolean isTargetType(String raw) {
        return raw != null && TARGET_TYPES.contains(raw);
    }

    public static boolean isTerminal(String status) {
        return STATUS_ACCEPTED.equals(status) || STATUS_REJECTED.equals(status);
    }

    private String  id;
    private String  mindId;
    private String  agentRunId;
    private String  targetType;
    private String  targetId;
    /** 候选变更全文（TEXT JSON，原样回显；禁服务端解析后空值冒充）。 */
    private String  changeJson;
    private String  evidenceRefsJson;
    private String  status;
    private String  reviewedBy;
    /** 落库返回 {@code java.sql.Timestamp}，序列化由全局 Jackson 裁定（同既有 Map 回读面）。 */
    private Object  reviewedAt;
    private String  reviewReason;
    private String  traceId;
    private String  createTime;
}
