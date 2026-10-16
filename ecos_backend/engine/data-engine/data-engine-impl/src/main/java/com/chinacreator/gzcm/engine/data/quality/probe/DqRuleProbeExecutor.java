package com.chinacreator.gzcm.engine.data.quality.probe;

import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;

/**
 * F02-09 补齐：DQ 规则真值探测器。
 * <p>
 * 与旧实现的关系：{@link com.chinacreator.gzcm.engine.data.quality.service.DqScheduleServiceImpl#buildContext}
 * 在旧代码下把 {@code totalRows/failedRows} 硬编 0，再从上一次 {@code dq_rule_check} 行"回灌"。
 * 因为种子环境首次跑批即无历史 check 行，形成 0/0 自引用 —— 评估器看到 totalRows=0 直接判 1.0，
 * "100% 通过"从未经过真实数据。本接口在评估实际读库之前，
 * 对规则目标表做 <b>只读 COUNT/SUM 探测</b> 拿到权威值。
 *
 * <p>契约：</p>
 * <ul>
 *   <li>仅对 {@code targetKind=TABLE} 且目标表存在于可解析 PG 目录的规则执行探测；</li>
 *   <li>目标 schema.table / 列名（含 when 子句引用的字段）全部过
 *       {@code information_schema} 白名单后再进 SQL，禁拼字符串；值一律走占位符；</li>
 *   <li>支持规则类型：NOT_NULL / PRESENCE / RANGE / ENUM / FORMAT / VALIDITY /
 *       FRESHNESS / UNIQUE；CONSISTENCY 与未知类型执行侧返回 {@code executed=false}
 *       （防御式：不伪装通过，交下游评估器降级 UNKNOWN），保持既有
 *       {@code ScoringContextHydrationTest} 兜底语义</li>
 *   <li>SQL 异常不吞；DAO 层已限为只读，SQL 拼装由本类完成后交 DAO 单语句执行</li>
 *   <li>只读、不落库、不改配置</li>
 * </ul>
 *
 * @author F02-09 / ECOS Data Engine Team
 */
public interface DqRuleProbeExecutor {

    /** 对单条规则执行真值探测。永不抛业务异常；DAO 侧 SQL 异常由本接口内部捕获并降级为 notExecuted。 */
    RuleProbeResult probe(DqRuleVO rule);
}
