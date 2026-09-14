package com.chinacreator.gzcm.engine.data.quality.scoring;

/**
 * 6 维评估器 SPI 接口（PMO-48-B T8 评分引擎核心抽象）。
 *
 * <p>实现约定（铁律 1.3 / 0.2）：</p>
 * <ul>
 *   <li>Bean 必须由 Spring 容器管理（6 个 @Component 自动注入到 {@link DqScoreEngine#evaluateAll}）</li>
 *   <li>实现类只算 + 返回，禁止写库（落库并入 {@code DqScoreService#recomputeForAsset}）</li>
 *   <li>禁止 {@code throws Exception}（公共契约不抛受检异常）</li>
 *   <li>样本 0 行 / NULL → 评估器返回 1.0（空表视完整/无重复/无错误）</li>
 *   <li>命中敏感字段时 detail.values 用 *** 占位（铁律 2.4 #3 安全卡）</li>
 * </ul>
 *
 * <p>边界场景（实现侧强制）：</p>
 * <table>
 *   <caption>样本 / 触发场景 → 行为约定</caption>
 *   <tr><td>sample=0 且 ctx.totalRows=0 且 rule_type=OWN</td><td>score = 1.0</td></tr>
 *   <tr><td>样本拉取超时 60s（Phase 3 才接）</td><td>score = 0.0 + details.status=TIMEOUT</td></tr>
 *   <tr><td>命中敏感字段（phone/mobile/amount/身份证/银行卡/手机/金额）</td><td>detail.values 替换 ***，score 仍可算</td></tr>
 *   <tr><td>样本存在失败</td><td>score = 1 - failed / total</td></tr>
 * </table>
 *
 * <p>默认 6 维实现（Phase 2 内化）：</p>
 * <ul>
 *   <li>{@code DqCompletenessEvaluator} — 字段非空率 / 必填覆盖度</li>
 *   <li>{@code DqUniquenessEvaluator}   — 主键/业务键无重复</li>
 *   <li>{@code DqAccuracyEvaluator}     — 格式/范围/正则通过率</li>
 *   <li>{@code DqConsistencyEvaluator}  — 跨表/跨源一致性</li>
 *   <li>{@code DqValidityEvaluator}     — 枚举/合规命中通过率</li>
 *   <li>{@code DqFreshnessEvaluator}    — 数据新鲜度（executed_at 距 now）</li>
 * </ul>
 *
 * <p>新增维度实现仅需：1) 加 @Component；2) data-engine-impl 的 spi 子包内放置；
 * 3) 不动 DqScoreEngine 主流程（{@code List&lt;DimensionEvaluator&gt;} 自动收集）。</p>
 *
 * @author PMO-48-B T8
 */
public interface DimensionEvaluator {

    /**
     * 评估器所属维度（用于 {@link DqScoreEngine#evaluateAll} 维度分发）。
     *
     * <p>同一维度仅允许一个 Bean 实现；多 Bean 冲突验收不通过（铁律 1.3）。</p>
     *
     * @return 维度枚举
     */
    DqDimension dimension();

    /**
     * 评估上下文 → 单维度分数。
     *
     * <p>返回 {@link DimensionScore}（0.0-1.0 + 样本规模 + 命中规则数 + detail）。
     * 不得返回 null — 无数据时 score=1.0 + detail 解释；
     * 超时（Phase 3）时返回 score=0.0 + detail.status=TIMEOUT。</p>
     *
     * <p>实现必须：</p>
     * <ol>
     *   <li>先判 NULL/0-样本边界（返回 1.0，detail 写 "EMPTY_SAMPLE"）</li>
     *   <li>再按维度规则计算（如 NOT_NULL 用 1 - failed/total）</li>
     *   <li>填充 details（threshold / sampleStats / values-masked / relatedAlertId 等）</li>
     *   <li>不得写库；不得抛受检异常；失败应返回 0.0 + detail</li>
     * </ol>
     *
     * @param ctx 评估上下文（永不 null，Service 层兜底 new）
     * @return 维度分数（永不 null）
     */
    DimensionScore evaluate(ScoringContext ctx);
}
