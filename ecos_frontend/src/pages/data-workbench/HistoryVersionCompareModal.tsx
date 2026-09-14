/**
 * HistoryVersionCompareModal — 数据表目录历史版本比较对话框
 *
 * 两步式交互：
 *   Step1 版本选择 — 列出 Git 存档的历史版本（时间 + 表数量），单选；
 *   Step2 差异表格 — 结构化展示所选历史版本与当前版本的差异（7 列）。
 *
 * 差异对比为后端内存 diff（两份 JSON 快照哈希对比），大数据量依然快速；
 * 前端仅做变更类型筛选，避免重复计算。
 *
 * 主题感知 (useTheme) / i18n (useLanguage, dw.histCompare.*) / lucide-react 图标。
 */
import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { GitCompare, X, Loader2, Clock, Table2, AlertTriangle, CheckCircle2, ChevronLeft, ListFilter } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { fetchVersionHistory, fetchVersionDiff, type MetadataVersion, type VersionDiffResult, type VersionDiffRow } from './api';

interface Props {
  datasourceId: string;
  datasourceName?: string;
  onClose: () => void;
}

/** 变更类型筛选选项 */
type ChangeFilter = 'ALL' | 'ADDED' | 'DELETED' | 'MODIFIED';

/** 变更类型徽章渲染（语义色，主题感知） */
function ChangeTypeBadge({ type }: { type: VersionDiffRow['changeType'] }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const cfg = type === 'ADDED'
    ? { cls: `${styles.successBg} ${styles.successText}`, key: 'dw.histCompare.added' }
    : type === 'DELETED'
      ? { cls: `${styles.dangerBg} ${styles.dangerText}`, key: 'dw.histCompare.deleted' }
      : { cls: `${styles.warningBg} ${styles.warningText}`, key: 'dw.histCompare.modified' };
  return (
    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold whitespace-nowrap ${cfg.cls}`}>
      {t(cfg.key)}
    </span>
  );
}

/** 时间格式化（快照 collectedAt 为 ISO 字符串） */
function fmtTime(iso?: string): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return isNaN(d.getTime()) ? iso : d.toLocaleString();
}

export default function HistoryVersionCompareModal({ datasourceId, datasourceName, onClose }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // Step1: 版本列表
  const [versions, setVersions] = useState<MetadataVersion[]>([]);
  const [current, setCurrent] = useState<{ exists: boolean; collectedAt?: string; tableCount?: number }>({ exists: false });
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [selected, setSelected] = useState<string | null>(null);

  // Step2: 差异结果
  const [diff, setDiff] = useState<VersionDiffResult | null>(null);
  const [loadingDiff, setLoadingDiff] = useState(false);
  const [diffError, setDiffError] = useState<string | null>(null);
  const [filter, setFilter] = useState<ChangeFilter>('ALL');

  // ESC 关闭
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);

  // 打开即拉取版本列表
  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoadingHistory(true);
      setHistoryError(null);
      const r = await fetchVersionHistory(datasourceId);
      if (cancelled) return;
      if (r === null) {
        setHistoryError(t('dw.histCompare.loadFailed'));
        setVersions([]);
      } else {
        setVersions(r.versions);
        setCurrent(r.current);
        // 默认选中最新历史版本
        if (r.versions.length > 0) setSelected(r.versions[0].versionId);
      }
      setLoadingHistory(false);
    })();
    return () => { cancelled = true; };
  }, [datasourceId, t]);

  /** 触发比较 */
  const handleCompare = useCallback(async () => {
    if (!selected) return;
    setLoadingDiff(true);
    setDiffError(null);
    setDiff(null);
    const r = await fetchVersionDiff(datasourceId, selected);
    setLoadingDiff(false);
    if (r === null) {
      setDiffError(t('dw.histCompare.loadFailed'));
    } else if ('error' in r) {
      setDiffError(r.error);
    } else {
      setDiff(r);
      setFilter('ALL');
    }
  }, [selected, datasourceId, t]);

  /** 返回版本选择 */
  const handleBack = useCallback(() => {
    setDiff(null);
    setDiffError(null);
  }, []);

  /** 类型筛选后的差异行（大数据量下前端仅做浅层过滤，不重算 diff） */
  const filteredRows = useMemo(() => {
    if (!diff) return [];
    if (filter === 'ALL') return diff.rows;
    return diff.rows.filter(r => r.changeType === filter);
  }, [diff, filter]);

  /** 筛选按钮计数 */
  const countBy = useMemo(() => {
    const c = { ALL: diff?.rows.length ?? 0, ADDED: 0, DELETED: 0, MODIFIED: 0 };
    for (const r of diff?.rows ?? []) { c[r.changeType]++; }
    return c;
  }, [diff]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div
        className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-2xl w-[92vw] max-w-5xl max-h-[85vh] flex flex-col`}
        onClick={e => e.stopPropagation()}
      >
        {/* 头部 */}
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder}`}>
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            <GitCompare className={`w-4 h-4 ${styles.accentText}`} />
            {t('dw.histCompare.title')}
            {datasourceName && <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full ${styles.appBg} ${styles.cardTextMuted}`}>{datasourceName}</span>}
          </h3>
          <button onClick={onClose} className="opacity-50 hover:opacity-100 transition cursor-pointer">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Step1: 版本选择 */}
        {!diff && !diffError && (
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {/* 当前版本信息 */}
            <div className={`flex items-center gap-2 text-xs ${styles.cardText} px-3 py-2 rounded-lg border ${styles.cardBorder} ${styles.appBg}`}>
              <CheckCircle2 className={`w-3.5 h-3.5 shrink-0 ${styles.successText}`} />
              <span className="font-semibold">{t('dw.histCompare.current')}</span>
              {current.exists ? (
                <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                  {fmtTime(current.collectedAt)} · {current.tableCount ?? '?'} {t('dw.histCompare.tablesUnit')}
                </span>
              ) : (
                <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('dw.histCompare.noCurrent')}</span>
              )}
            </div>

            {/* 版本列表 */}
            {loadingHistory ? (
              <div className="py-12 text-center">
                <Loader2 className="w-5 h-5 mx-auto animate-spin opacity-40" />
                <p className={`text-xs mt-2 ${styles.muted}`}>{t('dw.loading')}</p>
              </div>
            ) : historyError ? (
              <div className={`py-8 text-center border border-dashed rounded-lg ${styles.cardBorder}`}>
                <AlertTriangle className="w-5 h-5 mx-auto mb-1 opacity-50" />
                <p className={`text-xs ${styles.muted}`}>{historyError}</p>
              </div>
            ) : versions.length === 0 ? (
              <div className="py-8 text-center border border-dashed rounded-lg ${styles.cardBorder}">
                <Clock className="w-5 h-5 mx-auto mb-1 opacity-30" />
                <p className={`text-xs ${styles.muted}`}>{t('dw.histCompare.noVersions')}</p>
                <p className={`text-[10px] mt-1 ${styles.muted}`}>{t('dw.histCompare.noVersionsHint')}</p>
              </div>
            ) : (
              <>
                <p className={`text-[10px] font-semibold uppercase tracking-wider ${styles.cardTextMuted}`}>{t('dw.histCompare.selectVersion')}</p>
                <div className="grid grid-cols-2 gap-2">
                  {versions.map(v => (
                    <button
                      key={v.versionId}
                      onClick={() => setSelected(v.versionId)}
                      className={`text-left px-3 py-2 rounded-lg border transition-all cursor-pointer ${
                        selected === v.versionId
                          ? `${styles.accentBorder} ${styles.badgeBg}`
                          : `${styles.cardBorder} hover:${styles.appBg}`
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <Clock className="w-3.5 h-3.5 shrink-0 opacity-50" />
                        <span className={`text-xs font-mono font-semibold ${styles.cardText}`}>{fmtTime(v.collectedAt)}</span>
                      </div>
                      <div className="flex items-center gap-1.5 mt-1 pl-5">
                        <Table2 className="w-3 h-3 opacity-40" />
                        <span className={`text-[10px] ${styles.cardTextMuted}`}>{v.tableCount ?? '?'} {t('dw.histCompare.tablesUnit')}</span>
                        <span className={`text-[9px] font-mono ml-auto ${styles.cardTextMuted}`}>{v.versionId}</span>
                      </div>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* Step1 → 比较按钮 */}
        {!diff && !diffError && (
          <div className={`flex justify-end gap-2 px-4 py-3 border-t ${styles.cardBorder}`}>
            <button onClick={onClose} className={`px-4 py-1.5 text-xs rounded border ${styles.cardBorder} ${styles.cardTextMuted} cursor-pointer hover:${styles.appBg}`}>
              {t('dw.conn.cancel')}
            </button>
            <button
              onClick={handleCompare}
              disabled={!selected || loadingDiff}
              className={`px-4 py-1.5 text-xs rounded font-semibold transition-colors flex items-center gap-1.5 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed ${styles.accentBg} ${styles.accentHover} ${styles.cardText}`}
            >
              {loadingDiff ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <GitCompare className="w-3.5 h-3.5" />}
              {t('dw.histCompare.compare')}
            </button>
          </div>
        )}

        {/* Step2 加载中 / 错误 */}
        {loadingDiff && (
          <div className="flex-1 py-16 text-center">
            <Loader2 className="w-6 h-6 mx-auto animate-spin opacity-40" />
            <p className={`text-xs mt-2 ${styles.muted}`}>{t('dw.histCompare.comparing')}</p>
          </div>
        )}
        {!loadingDiff && diffError && (
          <div className="flex-1 flex flex-col items-center justify-center py-12 px-4 gap-3">
            <AlertTriangle className="w-6 h-6 opacity-50" />
            <p className={`text-xs ${styles.dangerText}`}>{diffError}</p>
            <button onClick={handleBack} className={`px-4 py-1.5 text-xs rounded border ${styles.cardBorder} ${styles.cardText} cursor-pointer hover:${styles.appBg} flex items-center gap-1.5`}>
              <ChevronLeft className="w-3.5 h-3.5" />
              {t('dw.histCompare.backToSelect')}
            </button>
          </div>
        )}

        {/* Step2: 差异表格 */}
        {!loadingDiff && diff && !diffError && (
          <>
            <div className={`px-4 py-2 border-b ${styles.cardBorder} flex items-center justify-between gap-3 flex-wrap`}>
              <div className="flex items-center gap-2 text-[10px] font-mono">
                <span className={`${styles.cardTextMuted}`}>{t('dw.histCompare.versionLabel')}:</span>
                <span className={`font-semibold ${styles.cardText}`}>{fmtTime(diff.versionCollectedAt)}</span>
                <span className={styles.accentText}>→</span>
                <span className={`${styles.cardTextMuted}`}>{t('dw.histCompare.current')}</span>
                <span className={`font-semibold ${styles.cardText}`}>{fmtTime(diff.currentCollectedAt)}</span>
              </div>
              {/* 类型筛选 */}
              <div className="flex items-center gap-1">
                <ListFilter className={`w-3 h-3 ${styles.cardTextMuted} mr-1`} />
                {(['ALL', 'ADDED', 'DELETED', 'MODIFIED'] as ChangeFilter[]).map(f => (
                  <button
                    key={f}
                    onClick={() => setFilter(f)}
                    className={`px-2 py-0.5 rounded-full text-[10px] font-semibold transition-colors cursor-pointer ${
                      filter === f
                        ? `${styles.accentBg} ${styles.cardText}`
                        : `${styles.appBg} ${styles.cardTextMuted} border ${styles.cardBorder}`
                    }`}
                  >
                    {f === 'ALL' ? `${t('dw.histCompare.all')} (${countBy.ALL})` : f === 'ADDED' ? `${t('dw.histCompare.added')} (${countBy.ADDED})` : f === 'DELETED' ? `${t('dw.histCompare.deleted')} (${countBy.DELETED})` : `${t('dw.histCompare.modified')} (${countBy.MODIFIED})`}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex-1 overflow-auto p-2">
              {filteredRows.length === 0 ? (
                <div className="py-12 text-center">
                  <CheckCircle2 className="w-6 h-6 mx-auto mb-2 opacity-30" />
                  <p className={`text-xs ${styles.muted}`}>
                    {diff.rows.length === 0 ? t('dw.histCompare.noDiff') : t('dw.histCompare.noDiffInFilter')}
                  </p>
                </div>
              ) : (
                <table className="w-full text-xs border-collapse">
                  <thead className="sticky top-0 z-10">
                    <tr className={`${styles.appBg} border-b ${styles.cardBorder}`}>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colChangeType')}</th>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colTable')}</th>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colField')}</th>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colCurrent')}</th>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colPrevious')}</th>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colTime')}</th>
                      <th className={`text-left py-2 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{t('dw.histCompare.colCommit')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredRows.map((row, i) => (
                      <tr key={`${row.tableName}-${row.field}-${i}`} className="border-b border-current/5 hover:bg-black/5 dark:hover:${styles.cardBg}/5">
                        <td className="py-1.5 px-2"><ChangeTypeBadge type={row.changeType} /></td>
                        <td className={`py-1.5 px-2 font-mono font-semibold whitespace-nowrap ${styles.cardText}`}>{row.tableName}</td>
                        <td className={`py-1.5 px-2 font-mono whitespace-nowrap ${styles.cardTextMuted}`}>{row.field}</td>
                        <td className={`py-1.5 px-2 whitespace-nowrap max-w-[220px] truncate ${styles.successText}`} title={row.currentValue}>{row.currentValue || '-'}</td>
                        <td className={`py-1.5 px-2 whitespace-nowrap max-w-[220px] truncate ${styles.dangerText}`} title={row.previousValue}>{row.previousValue || '-'}</td>
                        <td className={`py-1.5 px-2 whitespace-nowrap font-mono text-[10px] ${styles.cardTextMuted}`}>{fmtTime(row.changeTime)}</td>
                        <td className={`py-1.5 px-2 font-mono text-[10px] whitespace-nowrap max-w-[260px] truncate ${styles.cardTextMuted}`} title={row.commitMessage}>{row.commitMessage}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div className={`flex items-center justify-between px-4 py-2.5 border-t ${styles.cardBorder}`}>
              <span className={`text-[10px] ${styles.cardTextMuted}`}>{diff.summary}</span>
              <div className="flex gap-2">
                <button onClick={handleBack} className={`px-3 py-1.5 text-xs rounded border ${styles.cardBorder} ${styles.cardText} cursor-pointer hover:${styles.appBg} flex items-center gap-1.5`}>
                  <ChevronLeft className="w-3.5 h-3.5" />
                  {t('dw.histCompare.backToSelect')}
                </button>
                <button onClick={onClose} className={`px-4 py-1.5 text-xs rounded ${styles.accentBg} ${styles.accentHover} ${styles.cardText} font-semibold cursor-pointer`}>
                  {t('dw.histCompare.close')}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
