package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.model.DqAssetScoreVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreSystemVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreTrendVO;
import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;

/**
 * DQ 评分服务接口（PMO-48-B T8）。
 *
 * <p>5 端点能力（重算 + 4 项查询）：</p>
 * <pre>
 *   POST /api/v1/dq/scores/recompute?assetType=TABLE&amp;assetId=xxx   → recomputeForAsset
 *   GET  /api/v1/dq/scores?assetType=TABLE&amp;assetId=xxx            → getAssetScore
 *   GET  /api/v1/dq/scores/trend?assetType=TABLE&amp;assetId=xxx&amp;days=30 → trend
 *   GET  /api/v1/dq/scores/grade?grade=F                            → byGrade
 *   GET  /api/v1/dq/scores/system                                   → systemScore
 * </pre>
 *
 * <p>实现强制安全卡（铁律 2.4）：</p>
 * <ol>
 *   <li>脱敏（#3）：含敏感字段的 target_field 在 detail/rolled_up_scores 用 *** 占位</li>
 *   <li>审计（#5）：每次 recomputeForAsset 异步 {@code POST /api/security/audit/log}，
 *       读端点 auditRead；security-engine 不可用时默认 DENY</li>
 * </ol>
 *
 * <p>Bean 名 {@code ecosDqScoreService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-B T8
 */
public interface DqScoreService {

    /**
     * 重算资产评分并落库。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>查 {@code dq_rule WHERE target_kind='TABLE' AND target_id=assetId
     *       AND status='ACTIVE' AND is_deleted=FALSE}（限最近 1h 避免跨窗口统计）</li>
     *   <li>查配对的 {@code dq_rule_check WHERE rule_id IN (...) AND executed_at >= NOW() - interval '1 hour'}</li>
     *   <li>按 dq_rule.rule_type dispatch 到 6 评估器，调 {@code DqScoreEngine#evaluateAll}</li>
     *   <li>写 {@code ecos_dq.dq_score_snapshot}（每维度 1 行，scope_type='TABLE'，scope_id=assetId）</li>
     *   <li>加权汇总 → 写 {@code ecos_dq.dq_score_asset ON CONFLICT DO UPDATE}</li>
     *   <li>异步 audit（DQ_SCORE_RECOMPUTE）</li>
     * </ol>
     *
     * @param assetType TABLE / DATASOURCE（V1 仅 TABLE 已实现，DATASOURCE 未来扩展）
     * @param assetId   资产 ID
     * @return 重算后的资产评分（含 6 维 + overall + grade），资产不可评时返回 null
     */
    DqAssetScoreVO recomputeForAsset(String assetType, String assetId);

    /**
     * 查询资产当前评分（dq_score_asset 最新一条）。
     *
     * @return null 表示尚无评分，前端应触发「未评分 + 手动重算」
     */
    DqAssetScoreVO getAssetScore(String assetType, String assetId);

    /**
     * 趋势查询（按天分组）。
     *
     * <p>SQL:</p>
     * <pre>
     *   SELECT to_date(evaluated_at, 'YYYY-MM-DD') AS day,
     *          dimension, AVG(score_value) AS score, COUNT(*) AS hits
     *   FROM ecos_dq.dq_score_snapshot
     *   WHERE scope_type = ? AND scope_id = ?
     *     AND evaluated_at &gt;= NOW() - (? || ' days')::interval
     *   GROUP BY 1, 2
     *   ORDER BY 1 DESC, 2
     * </pre>
     *
     * @param assetType TABLE / DATASOURCE
     * @param assetId   资产 ID
     * @param days      回看天数（&gt;0 才有效；上限 365，clamp）
     * @return 趋势行列表（可能为空，前端可降级 dash 占位）
     */
    List<DqScoreTrendVO> trend(String assetType, String assetId, int days);

    /**
     * 按等级查资产（Java 侧过滤 — 本任务不加列，DB 没 grade 字段，按 DqDimension#gradeOf
     * 阈值计算后再过滤）。
     *
     * @param grade A/B/C/D/F（大小写不敏感）
     * @return 指定等级资产列表（按 rolledScore ASC，最低分最高优先）
     */
    List<DqAssetScoreVO> byGrade(String grade);

    /**
     * 系统级健康度 — AVG(rolled_score) + 近 1 天 6 维度均值。
     *
     * <p>SQL:</p>
     * <pre>
     *   SELECT AVG(overall_score) FROM ecos_dq.dq_score_asset WHERE is_deleted = FALSE
     *   SELECT dimension, AVG(score_value) FROM ecos_dq.dq_score_snapshot
     *     WHERE evaluated_at &gt;= NOW() - interval '1 day'
     *     GROUP BY dimension
     * </pre>
     *
     * @return 系统级 VO（资产数 0 时 overallScore=0 + grade=F）
     */
    DqScoreSystemVO systemScore();

    /**
     * 兼容旧入口：仅查 6 维分数（无资产/不落库）。
     *
     * <p>调用 {@code DqScoreEngine#evaluateAll} 但不写任何 DB。当前无 controller 暴露，
     * 主要供内部测试/未来前端"即时评分"用量。</p>
     *
     * @param ctx 评估上下文（永不 null，调用方兜底 new ScoringContext()）
     * @return 6 维分数
     */
    Map<DqDimension, com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore> evaluateAll(
            com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext ctx);
}
