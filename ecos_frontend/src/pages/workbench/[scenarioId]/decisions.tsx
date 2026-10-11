/**
 * 工作台 L3 — 经营管控闭环（H-8 第一批：P08 动作回链 + P09 四联视图）
 *
 * <p>路由 `/workbench/:scenarioId/decisions`（F07-06 三层 IA 第三层，与 forecast.tsx 同层，
 * 不新开顶层导航）。`?runId=` 为 run 锚点单源：由 P08 看板回链或外部深链带入，
 * 本页 P09 四联视图据此消费 `GET /api/v1/cognitive/outcomes/by-run/{runId}`。</p>
 *
 * <p>DEMO 徽标（PRD-09 §八 演练⑥ / 详设-09 R-63 ②）：页头恒挂 `<DemoBadge />`，
 * 判定条件单源于 `services/forecastScenarioMode.ts`（真实四源未串通 ⇒ 恒显示）。</p>
 *
 * <p>禁假真理：本页不产任何数字；所有面由后端端点驱动，未上线/降级/空态复用既有
 * `CognitiveSkeleton` / `CognitiveEmptyState` / `CognitiveErrorState` / `DegradationBanner`。</p>
 */

import React, { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import LucideIcon from '../../../components/LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import DemoBadge from '../../../components/common/status/DemoBadge';
import ActionFormPanel from './decisions/ActionFormPanel';
import DecisionBoardPanel from './decisions/DecisionBoardPanel';
import OutcomeQuadView from './decisions/OutcomeQuadView';

export default function WorkbenchDecisions() {
  const { scenarioId = '' } = useParams<{ scenarioId: string }>();
  const [searchParams] = useSearchParams();
  const runId = searchParams.get('runId') ?? '';
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');
  /** 创建成功计数（驱动看板重取；不自造乐观行）。 */
  const [createdTick, setCreatedTick] = useState(0);

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* Header + back（与 forecast.tsx 同构，L3 一致性） */}
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
        <Link
          to={`/workbench/${scenarioId}`}
          className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer mb-2 ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <LucideIcon name="ArrowLeft" size={12} />
          {t('workspace.l3.back')}
        </Link>
        <div className="flex items-center gap-2 flex-wrap">
          <h1 className={`text-lg font-bold tracking-tight ${styles.appText} flex items-center gap-2`}>
            <LucideIcon name="Gavel" size={16} className={styles.infoText} />
            {t('scenario.decisions.title')}
          </h1>
          <DemoBadge />
        </div>
        <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('scenario.decisions.subtitle')}</p>
        {runId && (
          <p className={`mt-2 text-[10px] font-mono ${styles.cardTextMuted}`} data-testid="decisions-run-anchor">
            {t('scenario.decisions.run_anchor', { runId })}
          </p>
        )}
      </div>

      <div className={`p-4 space-y-4 ${isMobile ? '' : 'max-w-5xl'}`}>
        {/* P08：动作创建（五必填 + forecast_run_id 回链） */}
        <ActionFormPanel
          scenarioId={scenarioId}
          forecastRunId={runId}
          onCreated={() => setCreatedTick((n) => n + 1)}
        />

        {/* P08：动作看板 + 回链可跳转 */}
        <DecisionBoardPanel scenarioId={scenarioId} activeRunId={runId} refreshKey={createdTick} />

        {/* P09：四联视图（当时预测 / 实际 / 误差 / 待复审假设） */}
        <OutcomeQuadView runId={runId} scenarioId={scenarioId} />
      </div>
    </div>
  );
}
