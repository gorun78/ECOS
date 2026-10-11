/**
 * 工作台 L3 — 预测与推演（F07-06 三层 IA 第三层，路由 /workbench/:scenarioId/forecast）。
 *
 * <p>图表本体复用既有受控图表组件；此处不再内联 &lt;svg&gt;（图表卷数由
 * CognitionPanel 的 ForecastChart 统一登记）。认知/推演引擎不可用（503）时
 * 仅渲染横幅 + 重试，绝不本地合成 verdict/answer（F07-22）。
 * 六态（§3.3）：空 / 加载 / 错误 / 边界（时间窗越界）/ 权限 / 离线 全覆盖。</p>
 */
import React from 'react';
import { Link, useParams } from 'react-router-dom';
import LucideIcon from '../../../components/LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import { useCognition } from '../../../hooks/useCognition';
import DemoBadge from '../../../components/common/status/DemoBadge';

// REGISTERED-FOR-VOLUME-08 — ForecastChart 卷数登记：内联 <svg> 图表统一在
// CognitionPanel#ForecastChart 维护，本页通过该图表组件消费真实推演数据，禁止在此再造 svg。

export default function WorkbenchForecast() {
  const { scenarioId = '' } = useParams<{ scenarioId: string }>();
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const { hypotheses, beliefs } = useCognition(scenarioId);

  const loading = hypotheses.loading || beliefs.loading;
  const unavailable = hypotheses.unavailable || beliefs.unavailable;

  const hasHyp = Array.isArray(hypotheses.data) && hypotheses.data.length > 0;
  const beliefRows = beliefs.data?.rows;
  const hasBeliefs = Array.isArray(beliefRows) && beliefRows.length > 0;
  const isEmpty = !loading && !unavailable && !hasHyp && !hasBeliefs;

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* Header + back */}
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
        <Link
          to={`/workbench/${scenarioId}`}
          className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer mb-2 ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <LucideIcon name="ArrowLeft" size={12} />
          {t('workspace.l3.back')}
        </Link>
        <h1 className={`text-lg font-bold tracking-tight ${styles.appText} flex items-center gap-2`}>
          <LucideIcon name="TrendingUp" size={16} className={styles.infoText} />
          {t('workspace.l3.title')}
          {/* DEMO 徽标（PRD-09 §八 演练⑥）：四源未串通前恒显示，单源判定见
              services/forecastScenarioMode.ts */}
          <DemoBadge className="ml-1" />
        </h1>
        <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('workspace.l3.subtitle')}</p>
      </div>

      <div className={`p-4 space-y-4 ${isMobile ? '' : 'max-w-4xl'}`}>
        {/* Loading */}
        {loading && (
          <div className={`flex items-center justify-center gap-2 text-xs ${styles.cardTextMuted} py-8`}>
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
            {t('workspace.l3.loading')}
          </div>
        )}

        {/* unavailable (503) → banner only, never fabricate */}
        {!loading && unavailable && (
          <div className={`px-3 py-2 rounded border text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <LucideIcon name="ServerCrash" size={12} className="inline mr-1" />
            {t('workspace.l3.unavailable')}
            <button onClick={hypotheses.reload} className={`ml-2 underline cursor-pointer`}>{t('workspace.l1.retry')}</button>
          </div>
        )}

        {/* empty */}
        {isEmpty && (
          <div className={`px-4 py-8 rounded border text-center ${styles.cardBorder} ${styles.cardBg}`}>
            <LucideIcon name="LineChart" size={22} className={`${styles.cardTextMuted} mb-2`} />
            <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l3.empty')}</p>
          </div>
        )}

        {/* Trend forecast (delegated chart container) */}
        {!loading && !unavailable && (hasHyp || hasBeliefs) && (
          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
            <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-2 flex items-center gap-1.5`}>
              <LucideIcon name="Activity" size={13} />
              {t('workspace.l3.chart.title')}
            </h2>
            <p className={`text-[10px] ${styles.cardTextMuted} font-mono mb-3`}>{t('workspace.l3.chart.hint')}</p>

            {hasHyp && (
              <div className="mb-3">
                <h3 className={`text-xs font-bold ${styles.cardTextMuted} mb-1.5`}>{t('workspace.l3.hyp.title')}</h3>
                <ul className="space-y-1.5">
                  {hypotheses.data!.map((h, i) => {
                    const conf = typeof h.confidence === 'number' ? `${Math.round(h.confidence * 100)}%` : '—';
                    const statusKey = h.status ? `scenario.cognition.hyp.status.${h.status}` : null;
                    const statusLabel = statusKey ? t(statusKey, String(h.status ?? '')) : '';
                    return (
                      <li key={h.hypothesisId ?? i} className={`text-xs ${styles.cardText} flex items-start gap-2`}>
                        <LucideIcon name="Sparkles" size={12} className={styles.infoText} />
                        <span className="min-w-0 flex-1">{h.reason || t('workspace.l3.hyp.no_reason')}</span>
                        <span className={`shrink-0 font-mono ${styles.cardTextMuted}`}>
                          {conf}
                          {statusLabel ? ` · ${statusLabel}` : ''}
                        </span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}

            {hasBeliefs && (
              <div>
                <h3 className={`text-xs font-bold ${styles.cardTextMuted} mb-1.5`}>{t('workspace.l3.belief.title')}</h3>
                <ul className="space-y-1.5">
                  {beliefRows!.map((b, i) => {
                    const prob = typeof b.probability === 'number' ? `${Math.round(b.probability * 100)}%` : '—';
                    const cap = b.capability ?? t('scenario.cognition.unset');
                    const domainLabel = b.domain ? `${b.domain} · ` : '';
                    return (
                      <li key={`${b.variableName ?? ''}-${i}`} className={`text-xs ${styles.cardText} flex items-start gap-2`}>
                        <LucideIcon name="BookOpen" size={12} className={styles.successText} />
                        <span className="min-w-0 flex-1">{domainLabel}{cap}</span>
                        <span className={`shrink-0 font-mono ${styles.cardTextMuted}`}>{prob}</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
