package com.chinacreator.gzcm.engine.data.quality.probe;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 单条 DQ 规则的真值探测结果（F02-09 补齐）。
 * <p>
 * 由 {@code DqRuleProbeExecutor} 产出，供 {@code DqScheduleServiceImpl#buildContext}
 * 覆写 ScoringContext 的 {@code totalRows / failedRows}，消除旧实现"0/0 空转 → 规则自证 100% 通过"
 * 的假真值。契约不单源在 impl（api 层可引用），但当前仅数据域使用，暂落 impl/probe 包内，
 * 后续跨引擎复用再外迁。
 *
 * @author F02-09
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleProbeResult {

    /** 是否真执行了 SELECT（true = totalRows/failedRows 是权威值，下游评估器直接消费）。 */
    private boolean executed;

    /** 未执行时的 reason（表不在白名单 / rule_type 不支持 / SQL 异常）。执行成功时 null。 */
    private String reason;

    /** 目标表总行数（executed=true 时非 null，恒 >= 0）。 */
    private Long totalRows;

    /** 命中"失败谓词"的行数（executed=true 时非 null，0 ≤ failed ≤ total）。 */
    private Long failedRows;

    /** 生成 SQL 摘要（回写 log/lastCheck 用；不含具体值）。 */
    private String sqlDigest;

    public static RuleProbeResult notExecuted(String reason) {
        RuleProbeResult r = new RuleProbeResult();
        r.setExecuted(false);
        r.setReason(reason);
        return r;
    }

    public static RuleProbeResult executed(long total, long failed, String sqlDigest) {
        RuleProbeResult r = new RuleProbeResult();
        r.setExecuted(true);
        r.setTotalRows(total);
        r.setFailedRows(failed);
        r.setSqlDigest(sqlDigest);
        return r;
    }
}
