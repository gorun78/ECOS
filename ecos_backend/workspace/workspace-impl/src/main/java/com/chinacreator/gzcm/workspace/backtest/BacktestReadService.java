package com.chinacreator.gzcm.workspace.backtest;

import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回测五指标读面承流服务（#146aa 附录A 第 33 条 {@code listForecastBacktests}，
 * 契约行 = 详设 line 8267：{@code GET /api/v1/workspace/backtests?period=&granularity=}，
 * 400/403，粒度 ∈ 项目/部门/环节）。
 *
 * <h3>做实腿（本类持有）</h3>
 * <ol>
 *   <li><b>粒度词表 400</b>：{@code granularity} 显式给出但不在
 *       {@link BacktestGranularity}（DDL 单源 PROJECT/DEPARTMENT/STAGE）⇒ 逐字段
 *       400 {@code ACTION_FIELDS_MISSING}（契约「400 逐字段」腿）。缺省 = 不约束，
 *       不在此处判 400（契约参面可空）；</li>
 *   <li><b>行级裁决 403/503</b>：读端点出口前过 {@link ScenarioReadGuard#assertReadable}
 *       F07-18 咽喉（security 明确拒绝/漂移 ⇒ 403，不可用 ⇒ 503，默认 DENY）。</li>
 * </ol>
 *
 * <h3>承流结论：501 显式未上线，判定不翻转</h3>
 * 五指标（MAE/MAPE/区间覆盖率/数据覆盖率/偏差方向）+ LOW_SAMPLE 标记 + 阈值来源三条线
 * <b>全部无读载体</b>，故只做到词表与安全腿，读数面按 X-43/X-44 回真实 501 +
 * {@code data.stub=true} + 五枚可复验缺载体事实，禁 200 空列表、禁合成固定指标值。
 * 台账对第 33 条的判定不因本批翻转（端点可路由 ≠ 已实现，同 #146w 第 17 条判定口径）。
 */
@Service
public class BacktestReadService {

    private static final Logger log = LoggerFactory.getLogger(BacktestReadService.class);

    /** backtest:read 对应 security-engine RLS 实体编码。 */
    private static final String ENTITY_CODE = "fc_backtest";

    /**
     * 缺载体取证（每条可由 grep/SQL 复验，禁推断式关联；文案禁写跨引擎 schema/表
     * 限定名 —— 铁律 §3.1 与 WorkspaceLayeringArchTest 冻结判据同源）：
     * <ol>
     *   <li>回测表建表脚本 V217__fc_backtest.sql 已落单源目录但未获授权实跑 ⇒
     *       dev PG 实测 relation does not exist（2026-10-29 psql 取证），无行可读；</li>
     *   <li>写通道（data-engine 关账回测计算）无落盘接线：仓内 data-engine/datanet 控制器
     *       对 fc_backtest 零命中（grep 实证），表既未建也无供数腿；</li>
     *   <li>五指标+LOW_SAMPLE+阈值来源的属主读端点不存在：cognitive outcomes 面
     *       （{@code OutcomeEvaluateController}）全部端点带键（scenarioId/runId/evaluationId），
     *       无 period×granularity 无键枚举端点，无属主可承流；</li>
     *   <li>复审阈值配置 {@code ecos.fc.backtest.review_threshold.*} 的播种脚本 V280 同批
     *       「仅落文件未实跑」⇒ 阈值来源（契约出参列）无库内真值；</li>
     *   <li>{@code previewBacktestMetrics} 按详设 line 8270 是服务层 dry-run 能力、
     *       明确「不暴露为端点」⇒ 不能借道它冒充本端点供数。</li>
     * </ol>
     */
    static final List<String> MISSING_FACTS = List.of(
            "建表脚本 V217__fc_backtest.sql 未获授权实跑：dev PG 实测回测表 relation does not exist，无行可读",
            "写通道无落盘接线：data-engine/datanet 控制器对 fc_backtest 零命中（grep 实证），无供数腿",
            "属主读端点缺失：cognitive outcomes 面全端点带键（scenarioId/runId/evaluationId），无 period×granularity 枚举端点",
            "V280 复审阈值种子未实跑：ecos.fc.backtest.review_threshold.* 无库内真值，「阈值来源」出参列无载体",
            "previewBacktestMetrics 按详设为服务层 dry-run（不暴露为端点），禁借道冒充本端点供数");

    private final ScenarioReadGuard readGuard;

    public BacktestReadService(ScenarioReadGuard readGuard) {
        this.readGuard = readGuard;
    }

    /**
     * @param period      期间过滤（可空 = 不约束；形态合法性交属主面处置，本面不预校验以免第二套口径）
     * @param granularity 粒度过滤：显式给出但枚举外 ⇒ 400 逐字段；缺省不约束
     * @throws ActionValidationException      granularity 词表不合法（400）
     * @throws com.chinacreator.gzcm.workspace.exception.ForbiddenOperationException  RLS 拒绝（403）
     * @throws com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException 安全引擎不可用（503）
     * @throws BacktestNotLiveException       缺载体恒成立（501，带 stub=true 与可复验取证）
     */
    public void list(String period, String granularity) {
        if (granularity != null && !granularity.isBlank()
                && BacktestGranularity.parse(granularity) == null) {
            throw new ActionValidationException(List.of("granularity"));
        }
        readGuard.assertReadable(ENTITY_CODE);
        log.info("backtests 读面承流 period={} granularity={}（缺载体 501，登记见 missing）",
                period, granularity);
        throw new BacktestNotLiveException("workspace/backtests", MISSING_FACTS, null);
    }
}
