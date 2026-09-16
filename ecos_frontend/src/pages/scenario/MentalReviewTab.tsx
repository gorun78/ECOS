/**
 * 假设失效告警 + 复盘视图（PMO-59 P4b T4）。
 *
 * <p>人机干预入口之四（审计侧）：查 `GET /api/v1/cognitive/mental-reviews?tag=&since=`
 * （复盘口径 = 三表版本链 + V130 run 作废留痕重建），渲染：
 * 失效假设告警留痕（warnAlerts，源自 runtime-monitor `ecos_warn_log` 只读 join，
 * 含 faultContext/reviewTag）、假设时间线（失效高亮）、心智版本时间线、作废 run 留痕、汇总计数。</p>
 *
 * <p>tag 走后端白名单校验，非法 tag 由 400 拒绝 → 友好提示（不做前端白名单硬编码同步）。</p>
 */
import React, { useState } from 'react';
import LucideIcon from '../../components/LucideIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import { showToastGlobal } from '../../components/common/Toast';
import { formatNum, formatTime, type MentalReviewResult } from './mentalTypes';

/** 后端白名单 tag 的建议值（仅作输入建议，最终以后端校验为准） */
const SUGGESTED_TAG = 'P2b-mental-layer-review';

export default function MentalReviewTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [tag, setTag] = useState(SUGGESTED_TAG);
  const [since, setSince] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<MentalReviewResult | null>(null);

  /** 查询复盘聚合（since 为 datetime-local 值，补秒为 ISO LocalDateTime） */
  const handleQuery = async () => {
    if (loading) {
      return;
    }
    const tagValue = tag.trim();
    if (!tagValue) {
      showToastGlobal('error', t('scenario.iv.rv.needTag'));
      return;
    }
    const params = new URLSearchParams({ tag: tagValue });
    if (since.trim() !== '') {
      params.set('since', since.trim().length === 16 ? `${since.trim()}:00` : since.trim());
    }
    setLoading(true);
    try {
      const data = await apiFetchData<MentalReviewResult>(
        `/api/v1/cognitive/mental-reviews?${params.toString()}`
      );
      setResult(data);
      showToastGlobal('success', t('scenario.iv.rv.queryOk'));
    } catch (e) {
      setResult(null);
      showToastGlobal('error', `${t('scenario.iv.rv.queryFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  };

  const warnAlerts = result?.warnAlerts ?? [];
  const hypotheses = result?.hypotheses ?? [];
  const timelines = result?.beliefTimelines ?? [];
  const runImpacts = result?.runImpacts ?? [];
  const summary = result?.summary ?? {};

  return (
    <div className="space-y-4">
      {/* ── 查询配置 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
        <div className="flex items-center gap-2">
          <LucideIcon name="BellRing" size={14} />
          <span className="text-sm font-semibold">{t('scenario.iv.rv.title')}</span>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} leading-normal`}>{t('scenario.iv.rv.desc')}</p>
        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rv.tag')}
            </span>
            <input
              value={tag}
              onChange={(e) => setTag(e.target.value)}
              placeholder={SUGGESTED_TAG}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rv.since')}
            </span>
            <input
              type="datetime-local"
              value={since}
              onChange={(e) => setSince(e.target.value)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
        </div>
        <div className="flex justify-end">
          <button
            onClick={handleQuery}
            disabled={loading}
            className="px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-[11px] font-bold transition-colors cursor-pointer flex items-center gap-1.5"
          >
            <LucideIcon name="Search" size={12} className={loading ? 'animate-pulse' : ''} />
            {loading ? t('scenario.iv.rv.querying') : t('scenario.iv.rv.query')}
          </button>
        </div>
      </div>

      {!result && (
        <p className={`text-xs ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <LucideIcon name="Info" size={12} />
          {t('scenario.iv.rv.idle')}
        </p>
      )}

      {result && (
        <div className="space-y-4">
          {/* ── 汇总 ── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400">
              {t('scenario.iv.rv.summary')}
            </span>
            <div className="flex flex-wrap gap-4 text-[11px]">
              {Object.entries(summary).map(([key, value]) => (
                <span key={key} className={styles.cardTextMuted}>
                  {key}: <span className={`font-mono ${styles.cardText}`}>{value}</span>
                </span>
              ))}
            </div>
            {result.reconstructionMode && (
              <p className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{result.reconstructionMode}</p>
            )}
          </div>

          {/* ── 失效假设告警留痕（ecos_warn_log） ── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <div className="flex items-center gap-2">
              <LucideIcon name="BellRing" size={12} />
              <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
                {t('scenario.iv.rv.warnAlerts')}
              </span>
            </div>
            {warnAlerts.length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.rv.noWarn')}</p>
            ) : (
              <ul className="space-y-2">
                {warnAlerts.map((w) => (
                  <li
                    key={w.logId ?? w.id}
                    className={`rounded border ${styles.inputBorder} ${styles.inputBg} p-2 space-y-1`}
                  >
                    <div className="flex flex-wrap items-center gap-2 text-[11px]">
                      <span
                        className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
                      >
                        {w.warnLevel ?? 'WARN'}
                      </span>
                      <span className={`font-mono ${styles.cardText}`}>{w.warnType ?? '-'}</span>
                      {w.warnObjName && <span className={styles.cardTextMuted}>{w.warnObjName}</span>}
                      <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                        {formatTime(w.warnTime, '-')}
                      </span>
                    </div>
                    {w.warnMessage && <p className={`text-[11px] ${styles.cardText} leading-normal`}>{w.warnMessage}</p>}
                    <p className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                      {t('scenario.iv.rv.reviewTag')}: {w.reviewTag ?? '-'} · {t('scenario.iv.rv.faultContext')}:{' '}
                      {w.faultContext ? JSON.stringify(w.faultContext) : '-'}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* ── 假设时间线（失效高亮） ── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rv.hypotheses')}
            </span>
            {hypotheses.length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.rv.emptySection')}</p>
            ) : (
              <ul className="space-y-1.5">
                {hypotheses.map((h) => (
                  <li key={h.id} className="flex flex-wrap items-center gap-2 text-[11px]">
                    <span className={`font-mono ${styles.cardText}`}>{h.hypothesisCode ?? h.id}</span>
                    <span className={styles.cardTextMuted}>{h.domain ?? '-'}</span>
                    <span
                      className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${
                        h.isValid
                          ? `${styles.successBg} ${styles.successText} ${styles.successBorder}`
                          : `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
                      }`}
                    >
                      {h.status ?? '-'}
                    </span>
                    {!h.isValid && h.invalidAt && (
                      <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                        {formatTime(h.invalidAt, '-')}
                      </span>
                    )}
                    {h.invalidReason && <span className={styles.cardTextMuted}>{h.invalidReason}</span>}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* ── 心智版本时间线（不确定性判断） ── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rv.beliefTimelines')}
            </span>
            {timelines.length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.rv.emptySection')}</p>
            ) : (
              <ul className="space-y-2">
                {timelines.map((g) => (
                  <li key={`${g.variableName}-${g.domain}`} className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2 text-[11px]">
                      <span className={`font-mono ${styles.cardText}`}>{g.variableName}</span>
                      <span className={styles.cardTextMuted}>{g.domain ?? '-'}</span>
                      <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                        {t('scenario.iv.rv.versions')}: {(g.versions ?? []).length}
                      </span>
                    </div>
                    <ul className="pl-3 space-y-0.5">
                      {(g.versions ?? []).map((v) => (
                        <li key={v.version} className="flex flex-wrap items-center gap-2 text-[10px]">
                          <span className={`font-mono ${styles.cardTextMuted}`}>v{v.version}</span>
                          <span className={`font-mono ${styles.cardText}`}>
                            {(v.distribution ?? []).map((p) => `${p.outcome}:${formatNum(p.prob)}`).join(' / ') || '-'}
                          </span>
                          {v.manualOverride && (
                            <span
                              className={`font-bold px-1.5 py-0.5 rounded border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
                            >
                              {t('scenario.iv.ov.manualOverrideTag')}
                            </span>
                          )}
                          <span className={`font-mono ${styles.cardTextMuted}`}>{formatTime(v.updatedAt, '-')}</span>
                        </li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* ── 作废 run 留痕（V130） ── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rv.runImpacts')}
            </span>
            {runImpacts.length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.rv.emptySection')}</p>
            ) : (
              <ul className="space-y-1">
                {runImpacts.map((r) => (
                  <li key={r.eventId ?? r.runId} className="flex flex-wrap items-center gap-2 text-[11px]">
                    <span className={`font-mono ${styles.cardText}`}>{r.runId ?? '-'}</span>
                    <span className={`font-mono ${styles.cardTextMuted}`}>{r.hypothesisId ?? '-'}</span>
                    <span
                      className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${
                        r.autoDetected
                          ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
                          : `${styles.inputBg} ${styles.cardTextMuted} ${styles.inputBorder}`
                      }`}
                    >
                      {r.autoDetected ? t('scenario.iv.rv.autoDetected') : t('scenario.iv.rv.manualDetected')}
                    </span>
                    <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                      {formatTime(r.supersededAt, '-')}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
