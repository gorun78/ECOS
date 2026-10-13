/**
 * AuditQueryPage — 详设-01 B 章 3.2 审计查询页（安全控制面）
 *
 * 挂载：安全中心域内 Tab（W209/C191 路由根基线冻结，设计
 * `#/platform/security/audit` 由 `#/security-center?tab=audit-query` 承接）。
 *
 * 数据面（后端 Controller 实证）：
 *  - 列表：GET /api/v1/audit/logs?userId&action&page&pageSize&startTime&endTime
 *          （security-engine AuditController#list，双路径含 /api/v1/security/audit/logs）
 *  - 链校验：POST /api/v1/security/audit/verify-chain {from,to}（本批次新增端点，
 *          区间为 ecos_audit_log 主键 id 闭区间；契约响应 {valid, brokenAt[]}）
 * 六态矩阵（§3.3）：loading / empty / error / denied / offline / success。
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  FileSearch, Loader2, AlertTriangle, Ban, X, Link2, ChevronRight, CalendarDays,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme, ThemeStyles } from '../../components/ThemeContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import type { AuditEvent } from '../../types';
import {
  fetchAuditLogs, verifyAuditHashChainRange, extractErrorTraceId,
  type VerifyChainResult,
} from '../../api';
import { NoAccessError, NetworkError, isErrorResponse } from '../../services/httpClient';

type PageState = 'loading' | 'success' | 'empty' | 'error' | 'denied' | 'offline';
type ChainState = 'idle' | 'checking' | 'done';

const PAGE_SIZE = 20;
const MAX_WINDOW_DAYS = 90;

const inputCls = (styles: ThemeStyles) =>
  `px-3 py-2 rounded-lg text-sm ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} focus:outline-none`;

const btnPrimaryCls = (styles: ThemeStyles) =>
  `flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium ${styles.accentBg} ${styles.accentText} ${styles.accentHover} transition-colors cursor-pointer disabled:opacity-50`;

const btnSecondaryCls = (styles: ThemeStyles) =>
  `px-3.5 py-2 rounded-lg text-sm font-medium ${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} ${styles.sidebarHoverBg} transition-colors cursor-pointer`;

export default function AuditQueryPage() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const isMobile = useMediaQuery('(max-width: 767px)');

  // ── filters ──
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [userId, setUserId] = useState('');
  const [action, setAction] = useState('');
  const [windowTooWide, setWindowTooWide] = useState(false);

  // ── list ──
  const [logs, setLogs] = useState<AuditEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [state, setState] = useState<PageState>('loading');
  const [errorMsg, setErrorMsg] = useState('');
  // 降级态网关 traceId（详设-00 C.5.1；错误响应体携带才展示，禁伪造）
  const [errorTraceId, setErrorTraceId] = useState<string | undefined>(undefined);
  const [selected, setSelected] = useState<AuditEvent | null>(null);

  // ── chain verify panel ──
  const [chainFrom, setChainFrom] = useState('');
  const [chainTo, setChainTo] = useState('');
  const [chainState, setChainState] = useState<ChainState>('idle');
  const [chainResult, setChainResult] = useState<VerifyChainResult | null>(null);
  const [chainError, setChainError] = useState('');

  const checkWindow = useCallback((): boolean => {
    if (fromDate && toDate) {
      const days = (new Date(toDate).getTime() - new Date(fromDate).getTime()) / 86_400_000;
      if (days > MAX_WINDOW_DAYS) {
        setWindowTooWide(true);
        return false;
      }
    }
    setWindowTooWide(false);
    return true;
  }, [fromDate, toDate]);

  const load = useCallback(async (p: number) => {
    setState('loading');
    setErrorMsg('');
    setErrorTraceId(undefined);
    setSelected(null);
    try {
      const res = await fetchAuditLogs(
        userId || undefined, action || undefined, undefined, p, PAGE_SIZE,
        fromDate || undefined, toDate || undefined,
      );
      setLogs(res.data || []);
      setTotal(res.total ?? (res.data || []).length);
      setState((res.data || []).length === 0 ? 'empty' : 'success');
      setPage(p);
    } catch (e) {
      setLogs([]);
      setErrorTraceId(extractErrorTraceId(e));
      const errInfo = isErrorResponse(e);
      if (e instanceof NoAccessError || errInfo.status === 403) {
        setState('denied');
      } else if (e instanceof NetworkError) {
        setState('offline');
      } else {
        setState('error');
        setErrorMsg(errInfo.message || String(e));
      }
    }
  }, [userId, action, fromDate, toDate]);

  useEffect(() => { void load(1); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const handleQuery = (e: React.FormEvent) => {
    e.preventDefault();
    if (!checkWindow()) return;
    void load(1);
  };

  const handleReset = () => {
    setFromDate(''); setToDate(''); setUserId(''); setAction('');
    setWindowTooWide(false);
    void loadAfterReset();
  };
  const loadAfterReset = async () => {
    setState('loading');
    setErrorTraceId(undefined);
    try {
      const res = await fetchAuditLogs(undefined, undefined, undefined, 1, PAGE_SIZE);
      setLogs(res.data || []);
      setTotal(res.total ?? (res.data || []).length);
      setState((res.data || []).length === 0 ? 'empty' : 'success');
      setPage(1);
    } catch (e) {
      setErrorTraceId(extractErrorTraceId(e));
      setState('error');
      setErrorMsg(isErrorResponse(e).message || String(e));
    }
  };

  const runChainVerify = useCallback(async () => {
    const from = Number(chainFrom);
    const to = Number(chainTo);
    if (!Number.isInteger(from) || !Number.isInteger(to) || from > to) {
      setChainError(t('platform.audit.chain.invalidRange'));
      setChainState('done');
      setChainResult(null);
      return;
    }
    setChainError('');
    setChainState('checking');
    try {
      const res = await verifyAuditHashChainRange(from, to);
      setChainResult(res);
      setChainState('done');
    } catch (e) {
      setChainResult(null);
      setChainError(isErrorResponse(e).message || String(e));
      setChainState('done');
    }
  }, [chainFrom, chainTo, t]);

  const totalPages = useMemo(() => Math.max(1, Math.ceil(total / PAGE_SIZE)), [total]);

  return (
    <div className="h-full overflow-auto p-4 md:p-6 space-y-4">
      {/* Header */}
      <div>
        <h3 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
          <FileSearch size={18} className={styles.accentText} />
          {t('platform.audit.title')}
        </h3>
        <p className={`text-xs mt-1 ${styles.muted}`}>{t('platform.audit.desc')}</p>
      </div>

      {/* Filter bar (design: 时间窗默认 7d 最大 90d) */}
      <form onSubmit={handleQuery} className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} p-3 space-y-2`}>
        <div className={`grid gap-2 ${isMobile ? 'grid-cols-1' : 'grid-cols-2 md:grid-cols-5'}`}>
          <label className="flex flex-col gap-1 text-xs">
            <span className={styles.muted}>{t('platform.audit.filter.from')}</span>
            <input type="date" value={fromDate} onChange={e => setFromDate(e.target.value)} className={inputCls(styles)} aria-label={t('platform.audit.filter.from')} />
          </label>
          <label className="flex flex-col gap-1 text-xs">
            <span className={styles.muted}>{t('platform.audit.filter.to')}</span>
            <input type="date" value={toDate} onChange={e => setToDate(e.target.value)} className={inputCls(styles)} aria-label={t('platform.audit.filter.to')} />
          </label>
          <label className="flex flex-col gap-1 text-xs">
            <span className={styles.muted}>{t('platform.audit.filter.user')}</span>
            <input type="text" value={userId} onChange={e => setUserId(e.target.value)} className={inputCls(styles)} aria-label={t('platform.audit.filter.user')} />
          </label>
          <label className="flex flex-col gap-1 text-xs">
            <span className={styles.muted}>{t('platform.audit.filter.action')}</span>
            <input type="text" value={action} onChange={e => setAction(e.target.value)} className={inputCls(styles)} aria-label={t('platform.audit.filter.action')} placeholder={t('platform.audit.filter.actionPh')} />
          </label>
          <div className="flex items-end gap-2">
            <button type="submit" className={btnPrimaryCls(styles)} disabled={windowTooWide}>{t('common.search')}</button>
            <button type="button" onClick={handleReset} className={btnSecondaryCls(styles)}>{t('platform.audit.filter.reset')}</button>
          </div>
        </div>
        {windowTooWide && (
          <p role="alert" className={`flex items-center gap-1.5 text-xs ${styles.warningText}`}>
            <AlertTriangle size={13} />{t('platform.audit.state.windowTooWide')}
          </p>
        )}
      </form>

      {/* Chain verification panel (design B 3.2 时序⑥⑦) */}
      <section aria-label={t('platform.audit.chain.title')} className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} p-3 space-y-2`}>
        <div className={`flex items-center gap-2 text-sm font-medium ${styles.cardText}`}>
          <Link2 size={15} className={styles.accentText} />
          {t('platform.audit.chain.title')}
        </div>
        <div className={`flex flex-wrap items-end gap-2`}>
          <label className="flex flex-col gap-1 text-xs">
            <span className={styles.muted}>{t('platform.audit.chain.from')}</span>
            <input type="number" min={1} value={chainFrom} onChange={e => setChainFrom(e.target.value)} className={`${inputCls(styles)} w-28`} aria-label={t('platform.audit.chain.from')} />
          </label>
          <label className="flex flex-col gap-1 text-xs">
            <span className={styles.muted}>{t('platform.audit.chain.to')}</span>
            <input type="number" min={1} value={chainTo} onChange={e => setChainTo(e.target.value)} className={`${inputCls(styles)} w-28`} aria-label={t('platform.audit.chain.to')} />
          </label>
          <button onClick={() => void runChainVerify()} className={btnPrimaryCls(styles)} disabled={chainState === 'checking'}>
            {chainState === 'checking' ? <Loader2 size={14} className="animate-spin" /> : <Link2 size={14} />}
            {t('platform.audit.chain.verify')}
          </button>
        </div>
        <div aria-live="polite" className="text-xs space-y-1">
          {chainError && <p className={styles.dangerText}>{chainError}</p>}
          {chainResult && (
            chainResult.valid ? (
              <p className={`flex items-center gap-1.5 ${styles.successText}`}>
                <span>✓</span>
                {t('platform.audit.chain.result.valid')} · {t('platform.audit.chain.result.checked')}: {chainResult.totalChecked}
              </p>
            ) : (
              <p className={`flex items-center gap-1.5 ${styles.dangerText}`}>
                <Ban size={13} />
                {chainResult.error
                  ? chainResult.error
                  : `${t('platform.audit.chain.result.broken')} — ${t('platform.audit.chain.result.brokenIds')}: ${chainResult.brokenAt.join(', ') || '—'}`}
              </p>
            )
          )}
          <p className={styles.muted}>{t('platform.audit.chain.hint')}</p>
        </div>
      </section>

      {/* State matrix */}
      {state === 'loading' && (
        <div aria-busy="true" className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className={`h-10 rounded-lg ${styles.badgeBg} animate-pulse`} />
          ))}
        </div>
      )}

      {state === 'empty' && (
        <StatePanel icon={<CalendarDays size={26} className={styles.muted} />} title={t('platform.audit.state.empty')} desc={t('platform.audit.state.empty.desc')} />
      )}

      {state === 'denied' && (
        <StatePanel
          icon={<Ban size={26} className={styles.dangerText} />}
          title={t('platform.audit.state.denied')}
          desc={t('platform.audit.state.denied.desc')}
          traceId={errorTraceId}
        />
      )}

      {state === 'offline' && (
        <StatePanel icon={<AlertTriangle size={26} className={styles.warningText} />} title={t('platform.audit.state.offline')} desc={t('platform.audit.state.offline.desc')} />
      )}

      {state === 'error' && (
        <StatePanel
          icon={<AlertTriangle size={26} className={styles.dangerText} />}
          title={t('platform.audit.state.error')}
          desc={errorMsg || t('platform.audit.state.error.desc')}
          traceId={errorTraceId}
          onRetry={() => void load(page)}
          retryLabel={t('platform.audit.action.retry')}
        />
      )}

      {state === 'success' && (
        <div className={`rounded-xl border ${styles.cardBorder} overflow-hidden`}>
          <table className="w-full text-sm">
            <thead className={`${styles.cardBg} border-b ${styles.cardBorder}`}>
              <tr>
                <th className={`text-left px-4 py-2.5 text-xs font-medium ${styles.muted}`}>{t('platform.audit.col.time')}</th>
                <th className={`text-left px-4 py-2.5 text-xs font-medium ${styles.muted}`}>{t('platform.audit.col.user')}</th>
                <th className={`text-left px-4 py-2.5 text-xs font-medium ${styles.muted}`}>{t('platform.audit.col.action')}</th>
                {!isMobile && <th className={`text-left px-4 py-2.5 text-xs font-medium ${styles.muted}`}>{t('platform.audit.col.resource')}</th>}
                {!isMobile && <th className={`text-left px-4 py-2.5 text-xs font-medium ${styles.muted}`}>{t('platform.audit.col.ip')}</th>}
                <th className={`text-left px-4 py-2.5 text-xs font-medium ${styles.muted}`}>{t('platform.audit.col.result')}</th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody>
              {logs.map((row, idx) => (
                <AuditRow key={row.eventId || `${page}-${idx}`} audit={row} onOpen={() => setSelected(row)} isMobile={isMobile} styles={styles} t={t} />
              ))}
            </tbody>
          </table>
          {/* Pagination */}
          <div className={`flex items-center justify-between px-4 py-2.5 border-t ${styles.cardBorder} text-xs ${styles.muted}`}>
            <span>{t('platform.audit.pagination.total')}: {total}</span>
            <div className="flex items-center gap-2">
              <button className={btnSecondaryCls(styles) + ' px-2.5 py-1'} disabled={page <= 1} onClick={() => void load(page - 1)}>{t('common.prev')}</button>
              <span>{page} / {totalPages}</span>
              <button className={btnSecondaryCls(styles) + ' px-2.5 py-1'} disabled={page >= totalPages} onClick={() => void load(page + 1)}>{t('common.next')}</button>
            </div>
          </div>
        </div>
      )}

      {/* Detail drawer (design: role=dialog + Esc) */}
      {selected && (
        <AuditDetailDrawer audit={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  );
}

// ── Row (design 组件契约 AuditRow{audit,onOpen}，域内单文件承载) ──
function AuditRow(props: {
  audit: AuditEvent;
  onOpen: () => void;
  isMobile: boolean;
  styles: ThemeStyles;
  t: (k: string) => string;
}) {
  const { audit: row, styles, t } = props;
  const denied = row.result === 'FAILURE' || row.result === 'DENIED';
  return (
    <tr className={`border-t ${styles.cardBorder} ${styles.sidebarHoverBg}`}>
      <td className={`px-4 py-2.5 whitespace-nowrap ${styles.cardTextMuted} text-xs`}>{row.timestamp}</td>
      <td className={`px-4 py-2.5 ${styles.cardText}`}>{row.userId ?? '—'}</td>
      <td className={`px-4 py-2.5 ${styles.cardText}`}>{row.action}</td>
      {!props.isMobile && <td className={`px-4 py-2.5 max-w-[16rem] truncate ${styles.cardTextMuted}`} title={row.resource}>{row.resource}</td>}
      {!props.isMobile && <td className={`px-4 py-2.5 text-xs ${styles.cardTextMuted}`}>{row.ipAddress ?? '—'}</td>}
      <td className="px-4 py-2.5">
        {/* 结果 denied 用图标 + 文本（不单靠色，design 要素 7） */}
        <span className={`inline-flex items-center gap-1 text-xs ${denied ? styles.dangerText : styles.successText}`}>
          {denied ? <Ban size={12} /> : <span aria-hidden="true">✓</span>}
          {row.result}
        </span>
      </td>
      <td className="px-4 py-2.5 text-right">
        <button onClick={props.onOpen} className={`inline-flex items-center gap-1 text-xs cursor-pointer ${styles.accentText}`} aria-label={t('platform.audit.action.open')}>
          {t('platform.audit.action.detail')}<ChevronRight size={13} />
        </button>
      </td>
    </tr>
  );
}

// ── Detail drawer (design 组件契约 AuditDetailDrawer) ──
function AuditDetailDrawer({ audit, onClose }: { audit: AuditEvent; onClose: () => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [showDetailJson, setShowDetailJson] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const detailJson = audit.details ? JSON.stringify(audit.details, null, 2) : null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={onClose} aria-hidden="true" />
      <aside
        role="dialog" aria-modal="true" aria-label={t('platform.audit.drawer.title')}
        className={`relative h-full w-full max-w-md ${styles.cardBg} border-l ${styles.cardBorder} shadow-xl overflow-auto p-5 space-y-4`}
      >
        <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
          <h4 className={`font-bold ${styles.cardText}`}>{t('platform.audit.drawer.title')}</h4>
          <button onClick={onClose} aria-label={t('common.close')} className={`${styles.muted} cursor-pointer`}><X size={16} /></button>
        </div>
        <dl className="space-y-2 text-sm">
          <DrawerField label={t('platform.audit.drawer.eventId')} value={audit.eventId} mono />
          <DrawerField label={t('platform.audit.col.time')} value={audit.timestamp} />
          <DrawerField label={t('platform.audit.col.user')} value={audit.userId ?? '—'} />
          <DrawerField label={t('platform.audit.col.action')} value={audit.action} />
          <DrawerField label={t('platform.audit.drawer.eventType')} value={audit.eventType} />
          <DrawerField label={t('platform.audit.col.resource')} value={audit.resource} mono />
          <DrawerField label={t('platform.audit.col.result')} value={audit.result} />
          <DrawerField label={t('platform.audit.col.ip')} value={audit.ipAddress ?? '—'} />
          <DrawerField label={t('platform.audit.drawer.agent')} value={audit.userAgent ?? '—'} />
        </dl>
        {detailJson && (
          <div className="space-y-1.5">
            <button
              onClick={() => setShowDetailJson(v => !v)}
              className={`flex items-center gap-1.5 text-xs cursor-pointer ${styles.accentText}`}
              aria-expanded={showDetailJson}
            >
              <ChevronRight size={13} className={showDetailJson ? 'rotate-90 transition-transform' : 'transition-transform'} />
              {t('platform.audit.drawer.detailJson')}
            </button>
            {showDetailJson && (
              <pre className={`text-xs p-3 rounded-lg overflow-auto max-h-64 ${styles.inputBg} ${styles.inputText}`}>{detailJson}</pre>
            )}
          </div>
        )}
        <p className={`text-xs ${styles.muted}`}>{t('platform.audit.drawer.chainNote')}</p>
      </aside>
    </div>
  );
}

function DrawerField({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  const { styles } = useTheme();
  return (
    <div className="grid grid-cols-3 gap-2">
      <dt className={`text-xs ${styles.muted} pt-0.5`}>{label}</dt>
      <dd className={`col-span-2 break-all ${styles.cardText} ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd>
    </div>
  );
}

function StatePanel(props: {
  icon: React.ReactNode;
  title: string;
  desc: string;
  /** 网关 traceId（仅错误响应体携带时展示；样板=GatewayDiagnosticsPage 页脚 traceId） */
  traceId?: string;
  onRetry?: () => void;
  retryLabel?: string;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div role="status" className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} p-10 flex flex-col items-center gap-3 text-center`}>
      <div className={`p-3 rounded-full border ${styles.cardBorder}`}>{props.icon}</div>
      <h4 className={`font-bold ${styles.cardText}`}>{props.title}</h4>
      <p className={`text-xs max-w-md ${styles.muted}`}>{props.desc}</p>
      {props.traceId && (
        <p className={`text-[10px] font-mono ${styles.muted}`}>{t('platform.audit.traceId')}: {props.traceId}</p>
      )}
      {props.onRetry && <button onClick={props.onRetry} className={btnSecondaryCls(styles)}>{props.retryLabel}</button>}
    </div>
  );
}
