/**
 * 工作台 L3 — 预测与推演（F07-06 三层 IA 第三层，路由 /workbench/:scenarioId/forecast）。
 *
 * <p>本页只读渲染 E3 hypotheses / E4 beliefs（useCognition 单源通道）。趋势图表
 * ForecastChart 仍单源登记于 CognitionPanel（REGISTERED-FOR-VOLUME-08），但其数据源 =
 * 认知 run 的 forecast.points，本页 E3/E4 通道无时序点 ⇒ 本批次不挂载图表、原
 * 「趋势预测」空壳区块已摘除（行260 订正，禁有无数据的图表标题）。
 * 认知/推演引擎不可用（503）时仅渲染横幅 + 重试，绝不本地合成 verdict/answer（F07-22）。
 * 六态（§3.3）实装口径：加载 / 503 降级横幅 / 错误（其余失败，后端 message 直排，
 * 权限 403 亦入该分支，未单拆）/ 空 / 离线（navigator.onLine=false 横幅）；
 * 「边界（时间窗越界）」本页无时间窗入参 ⇒ 不承诺该态。</p>
 */
import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import LucideIcon from '../../../components/LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import { useCognition } from '../../../hooks/useCognition';
import DemoBadge from '../../../components/common/status/DemoBadge';
import RunProvenancePanel from './forecast/RunProvenancePanel';
import WhatIfSimulator from './forecast/WhatIfSimulator';

// REGISTERED-FOR-VOLUME-08 — ForecastChart 卷数登记：内联 <svg> 图表统一在
// CognitionPanel#ForecastChart 维护；本页无 forecast.points 数据源，不复制图表。

export default function WorkbenchForecast() {
  const { scenarioId = '' } = useParams<{ scenarioId: string }>();
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const { hypotheses, beliefs } = useCognition(scenarioId);

  const [offline, setOffline] = useState(() => typeof navigator !== 'undefined' && !navigator.onLine);
  useEffect(() => {
    const on = () => setOffline(false);
    const off = () => setOffline(true);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => {
      window.removeEventListener('online', on);
      window.removeEventListener('offline', off);
    };
  }, []);

  const loading = hypotheses.loading || beliefs.loading;
  const unavailable = hypotheses.unavailable || beliefs.unavailable;
  const loadError = !loading && !unavailable ? hypotheses.error || beliefs.error : null;

  const hasHyp = Array.isArray(hypotheses.data) && hypotheses.data.length > 0;
  const beliefRows = beliefs.data?.rows;
  const hasBeliefs = Array.isArray(beliefRows) && beliefRows.length > 0;
  const isEmpty = !loading && !unavailable && !loadError && !hasHyp && !hasBeliefs;

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
        {/* 离线态 */}
        {offline && (
          <div className={`px-3 py-2 rounded border text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <LucideIcon name="WifiOff" size={12} className="inline mr-1" />
            {t('workspace.l3.offline')}
          </div>
        )}

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

        {/* 普通错误（含 403/网络中断，后端 message 原样直排，禁掩成空态） */}
        {!loading && !unavailable && loadError && (
          <div className={`px-3 py-2 rounded border text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <LucideIcon name="AlertTriangle" size={12} className="inline mr-1" />
            {t('workspace.l3.error', { msg: String(loadError) })}
            <button onClick={() => { hypotheses.reload(); beliefs.reload(); }} className={`ml-2 underline cursor-pointer`}>{t('workspace.l1.retry')}</button>
          </div>
        )}

        {/* empty */}
        {isEmpty && (
          <div className={`px-4 py-8 rounded border text-center ${styles.cardBorder} ${styles.cardBg}`}>
            <LucideIcon name="Inbox" size={22} className={`${styles.cardTextMuted} mb-2`} />
            <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l3.empty')}</p>
          </div>
        )}

        {/* 推演假设（E3 只读；行260 订正：原「趋势预测」假图表区块已摘除，
            本页无 forecast.points 数据源，列表如实呈现 hypotheses/beliefs 本体） */}
        {!loading && !unavailable && !loadError && hasHyp && (
          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
            <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-2 flex items-center gap-1.5`}>
              <LucideIcon name="Sparkles" size={13} />
              {t('workspace.l3.hyp.title')}
            </h2>
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
          </section>
        )}

        {/* 认知信念（E4 只读） */}
        {!loading && !unavailable && !loadError && hasBeliefs && (
          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
            <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-2 flex items-center gap-1.5`}>
              <LucideIcon name="BookOpen" size={13} />
              {t('workspace.l3.belief.title')}
            </h2>
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
          </section>
        )}

        {/* L3 受控运行闭环（复盘 S2）：What-if 推演（exploratory 徽标判定源=响应回显字段）
            + 运行溯源（蓝图版本/contextDigest/traceId 能实拿则实拿，缺字段恒「—」+ 来源说明） */}
        <WhatIfSimulator scenarioId={scenarioId} />
        <RunProvenancePanel scenarioId={scenarioId} />
      </div>
    </div>
  );
}
