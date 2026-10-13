/**
 * SecurityPolicyPage — 详设-01 B 章 3.1 安全策略管理页（安全控制面）
 *
 * 挂载：安全中心域内 Tab —— SecurityCenterLayout 读 `#/security-center?tab=policies`
 * 承接（W209/C191 路由根基线冻结，不开新根）。
 *
 * 数据面（端点均为后端 Controller 实证存在）：
 *  - RLS：GET /api/v1/security/rls/policies（RlsController#listPolicies）
 *  - CLS：GET /api/v1/security/cls/policies（ClsController，经 fetchClsPolicies）
 * 写面（新建/编辑/停用）由 DetectTab 的 Rls/ClsPolicyManager 承载，本页为
 * 只读控制面总览：谓词参数化展示（默认 DENY 提示）+ 详情面板 + 六态。
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Shield, Lock, Columns3, Loader2, AlertTriangle, X, Search, Database,
  Ban, Eye,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme, ThemeStyles } from '../../components/ThemeContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import {
  fetchRlsPolicies, fetchClsPolicies, extractErrorTraceId,
  type RlsPolicy, type ClsPolicy,
} from '../../api';
import { NoAccessError, NetworkError, isErrorResponse } from '../../services/httpClient';

type PolicyTabId = 'rls' | 'cls';

type PageState = 'loading' | 'success' | 'empty' | 'error' | 'denied' | 'offline';

interface PolicyRow {
  key: string;
  name: string;
  tableName: string;
  summary: string;          // 谓词/列过滤摘要（参数化展示，禁裸 SQL 直出美化）
  roles: string[];
  status: string;
  priority: number;
  updatedAt?: string;
  raw: RlsPolicy | ClsPolicy;
}

const inputCls = (styles: ThemeStyles) =>
  `w-full px-3 py-2 rounded-lg text-sm ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} focus:outline-none`;

function btnPrimaryCls(styles: ThemeStyles) {
  return `px-4 py-2 rounded-lg text-sm font-medium ${styles.accentBg} ${styles.accentText} ${styles.accentHover} transition-colors cursor-pointer disabled:opacity-50`;
}

function btnSecondaryCls(styles: ThemeStyles) {
  return `px-4 py-2 rounded-lg text-sm font-medium ${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} ${styles.sidebarHoverBg} transition-colors cursor-pointer`;
}

export default function SecurityPolicyPage() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [activeTab, setActiveTab] = useState<PolicyTabId>('rls');
  const [rows, setRows] = useState<PolicyRow[]>([]);
  const [state, setState] = useState<PageState>('loading');
  const [errorMsg, setErrorMsg] = useState<string>('');
  // 降级态网关 traceId（详设-00 C.5.1；仅当错误响应体携带时展示，网络失败无响应则为空）
  const [errorTraceId, setErrorTraceId] = useState<string | undefined>(undefined);
  const [tableFilter, setTableFilter] = useState('');
  const [searchText, setSearchText] = useState('');
  const [selected, setSelected] = useState<PolicyRow | null>(null);

  const load = useCallback(async (tab: PolicyTabId, tableName: string) => {
    setState('loading');
    setErrorMsg('');
    setErrorTraceId(undefined);
    setSelected(null);
    try {
      let list: PolicyRow[] = [];
      if (tab === 'rls') {
        const res = await fetchRlsPolicies(tableName ? { tableName } : {});
        list = res.data.map(mapRlsRow);
      } else {
        const res = await fetchClsPolicies(tableName ? { tableName } : {});
        list = res.data.map(mapClsRow);
      }
      setRows(list);
      setState(list.length === 0 ? 'empty' : 'success');
    } catch (e) {
      setRows([]);
      setErrorTraceId(extractErrorTraceId(e));
      if (e instanceof NoAccessError || (isErrorResponse(e) && isErrorResponse(e).status === 403)) {
        setState('denied');
      } else if (e instanceof NetworkError) {
        setState('offline');
      } else {
        setState('error');
        setErrorMsg(isErrorResponse(e).message || String(e));
      }
    }
  }, []);

  useEffect(() => { load(activeTab, tableFilter); }, [load, activeTab, tableFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setTableFilter(searchText.trim());
  };

  const tabs = useMemo(() => ([
    { id: 'rls' as PolicyTabId, labelKey: 'platform.security.tab.rls', icon: Lock },
    { id: 'cls' as PolicyTabId, labelKey: 'platform.security.tab.cls', icon: Columns3 },
  ]), []);

  return (
    <div className="h-full overflow-auto p-4 md:p-6 space-y-4">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
        <div>
          <h3 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
            <Shield size={18} className={styles.accentText} />
            {t('platform.security.title')}
          </h3>
          <p className={`text-xs mt-1 ${styles.muted}`}>{t('platform.security.desc')}</p>
        </div>
        {/* Default-DENY reminder (design B 3.1 state matrix · empty emphasis) */}
        <div className={`flex items-center gap-2 text-xs px-3 py-2 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
          <Ban size={14} className={styles.warningText} />
          <span className={styles.muted}>{t('platform.security.hint.defaultDeny')}</span>
        </div>
      </div>

      {/* Tabs (role=tablist per design element 8) */}
      <div role="tablist" aria-label={t('platform.security.title')} className={`flex gap-1 border-b ${styles.appBorder}`}>
        {tabs.map(tab => {
          const Icon = tab.icon;
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={active}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-t-md border-b-2 cursor-pointer transition-colors
                ${active ? `${styles.accentText} border-b-current` : `${styles.muted} border-b-transparent ${styles.sidebarHoverBg}`}`}
            >
              <Icon size={16} />
              <span>{t(tab.labelKey)}</span>
            </button>
          );
        })}
      </div>

      {/* Search by target table */}
      {!isMobile && (
        <form onSubmit={handleSearch} className="flex gap-2 max-w-md">
          <div className="relative flex-1">
            <Search size={14} className={`absolute left-3 top-1/2 -translate-y-1/2 ${styles.muted}`} />
            <input
              type="text"
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
              placeholder={t('platform.security.search.placeholder')}
              aria-label={t('platform.security.search.placeholder')}
              className={`${inputCls(styles)} pl-9`}
            />
          </div>
          <button type="submit" className={btnSecondaryCls(styles)}>{t('common.search')}</button>
        </form>
      )}

      {/* State matrix */}
      {state === 'loading' && (
        <div className="flex flex-col items-center justify-center py-16 gap-3" aria-busy="true">
          <Loader2 size={22} className={`animate-spin ${styles.muted}`} />
          <span className={`text-xs ${styles.muted}`}>{t('platform.security.state.loading')}</span>
        </div>
      )}

      {state === 'denied' && (
        <StateCard
          icon={<Ban size={28} className={styles.dangerText} />}
          title={t('platform.security.state.denied')}
          desc={t('platform.security.state.denied.desc')}
          tone="danger"
          traceId={errorTraceId}
        />
      )}

      {state === 'offline' && (
        <StateCard
          icon={<AlertTriangle size={28} className={styles.warningText} />}
          title={t('platform.security.state.offline')}
          desc={t('platform.security.state.offline.desc')}
          tone="warning"
        />
      )}

      {state === 'error' && (
        <StateCard
          icon={<AlertTriangle size={28} className={styles.dangerText} />}
          title={t('platform.security.state.error')}
          desc={errorMsg || t('platform.security.state.error.desc')}
          tone="danger"
          traceId={errorTraceId}
          onRetry={() => load(activeTab, tableFilter)}
          retryLabel={t('platform.security.action.retry')}
        />
      )}

      {state === 'empty' && (
        <StateCard
          icon={<Database size={28} className={styles.muted} />}
          title={t('platform.security.state.empty')}
          desc={t('platform.security.hint.defaultDeny')}
          tone="muted"
        />
      )}

      {state === 'success' && rows.length > 0 && (
        <div className={`rounded-xl border ${styles.cardBorder} overflow-hidden`}>
          <table className="w-full text-sm">
            <thead className={`${styles.cardBg} border-b ${styles.cardBorder}`}>
              <tr>
                {(['name', 'table', 'predicate', 'roles', 'status', 'priority', 'updatedAt'] as const).map(col => (
                  <th key={col} className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>
                    {t(`platform.security.col.${col}`)}
                  </th>
                ))}
                <th className={`text-left px-4 py-3 font-medium text-xs ${styles.muted}`}>{t('platform.security.col.action')}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => (
                <tr key={row.key} className={`border-t ${styles.cardBorder} ${styles.sidebarHoverBg}`}>
                  <td className={`px-4 py-3 font-medium ${styles.cardText}`}>{row.name}</td>
                  <td className={`px-4 py-3 ${styles.cardText}`}>
                    <code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{row.tableName}</code>
                  </td>
                  <td className={`px-4 py-3 max-w-xs truncate ${styles.cardTextMuted}`}>
                    <code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`} title={row.summary}>{row.summary}</code>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {row.roles.map(r => (
                        <span key={r} className={`px-2 py-0.5 rounded-full text-xs font-medium ${styles.badgeBg} ${styles.badgeText}`}>{r}</span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${row.status === 'ACTIVE' ? `${styles.successBg} ${styles.successText}` : `${styles.badgeBg} ${styles.muted}`}`}>
                      {row.status === 'ACTIVE' ? t('platform.security.status.active') : t('platform.security.status.inactive')}
                    </span>
                  </td>
                  <td className={`px-4 py-3 ${styles.cardTextMuted}`}>{row.priority}</td>
                  <td className={`px-4 py-3 text-xs ${styles.muted}`}>{row.updatedAt ?? '—'}</td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => setSelected(row)}
                      className={`flex items-center gap-1.5 px-2 py-1 rounded-lg text-xs cursor-pointer ${styles.sidebarHoverBg} ${styles.accentText}`}
                    >
                      <Eye size={13} />{t('platform.security.action.detail')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Detail panel (design: ?tab=&policyId= — here row drill-down) */}
      {selected && (
        <div
          role="dialog" aria-modal="true" aria-label={selected.name}
          className="fixed inset-0 z-50 flex items-center justify-center"
        >
          <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={() => setSelected(null)} aria-hidden="true" />
          <div className={`relative w-full max-w-lg mx-4 rounded-xl ${styles.cardBg} border ${styles.cardBorder} shadow-xl p-5 space-y-4`} onClick={e => e.stopPropagation()}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
              <h4 className={`font-bold ${styles.cardText}`}>{selected.name}</h4>
              <button onClick={() => setSelected(null)} aria-label={t('platform.security.action.close')} className={`${styles.muted} cursor-pointer`}><X size={16} /></button>
            </div>
            <dl className="space-y-2 text-sm">
              <DetailField label={t('platform.security.col.table')}><code className={`text-xs px-1.5 py-0.5 rounded ${styles.inputBg}`}>{selected.tableName}</code></DetailField>
              <DetailField label={t('platform.security.field.predicate')}>
                <code className={`text-xs px-2 py-1 rounded block break-all ${styles.inputBg} ${styles.inputText}`}>{selected.summary}</code>
              </DetailField>
              <DetailField label={t('platform.security.field.bindings')}>
                <p className={`text-xs ${styles.muted}`}>{t('platform.security.field.bindings.hint')}</p>
              </DetailField>
              <DetailField label={t('platform.security.col.roles')}>
                <span className="text-sm">{selected.roles.join(', ') || '—'}</span>
              </DetailField>
              <DetailField label={t('platform.security.col.status')}>
                <span className={selected.status === 'ACTIVE' ? styles.successText : styles.muted}>{selected.status}</span>
              </DetailField>
              {selected.raw.description ? (
                <DetailField label={t('platform.security.field.description')}>
                  <span className={`text-sm ${styles.cardTextMuted}`}>{selected.raw.description}</span>
                </DetailField>
              ) : null}
            </dl>
          </div>
        </div>
      )}
    </div>
  );
}

function DetailField({ label, children }: { label: string; children: React.ReactNode }) {
  const { styles } = useTheme();
  return (
    <div className="grid grid-cols-3 gap-2">
      <dt className={`text-xs ${styles.muted} pt-0.5`}>{label}</dt>
      <dd className="col-span-2">{children}</dd>
    </div>
  );
}

function StateCard(props: {
  icon: React.ReactNode;
  title: string;
  desc: string;
  tone: 'danger' | 'warning' | 'muted';
  /** 网关 traceId（仅错误响应体携带时展示；样板=GatewayDiagnosticsPage 页脚 traceId） */
  traceId?: string;
  onRetry?: () => void;
  retryLabel?: string;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const toneCls = props.tone === 'danger'
    ? `${styles.dangerBorder} ${styles.dangerText}`
    : props.tone === 'warning'
      ? `${styles.warningBorder} ${styles.warningText}`
      : `${styles.cardBorder} ${styles.muted}`;
  return (
    <div role="status" className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} p-8 flex flex-col items-center gap-3 text-center`}>
      <div className={`p-3 rounded-full border ${toneCls}`}>{props.icon}</div>
      <h4 className={`font-bold ${styles.cardText}`}>{props.title}</h4>
      <p className={`text-xs max-w-md ${styles.muted}`}>{props.desc}</p>
      {props.traceId && (
        <p className={`text-[10px] font-mono ${styles.muted}`}>{t('platform.security.traceId')}: {props.traceId}</p>
      )}
      {props.onRetry && (
        <button onClick={props.onRetry} className={btnSecondaryCls(styles)}>{props.retryLabel}</button>
      )}
    </div>
  );
}

function mapRlsRow(p: RlsPolicy): PolicyRow {
  return {
    key: `rls-${p.id ?? p.policyName}`,
    name: p.policyName,
    tableName: p.tableName,
    summary: p.filterExpression,
    roles: p.roles || [],
    status: p.status,
    priority: p.priority ?? 0,
    updatedAt: p.updatedAt,
    raw: p,
  };
}

function mapClsRow(p: ClsPolicy): PolicyRow {
  const visible = p.visibleColumns?.length ? `allow: ${p.visibleColumns.join(', ')}` : '';
  const blocked = p.blockedColumns?.length ? `deny: ${p.blockedColumns.join(', ')}` : '';
  return {
    key: `cls-${p.id ?? p.policyName}`,
    name: p.policyName,
    tableName: p.tableName,
    summary: [visible, blocked].filter(Boolean).join(' | ') || '—',
    roles: p.roles || [],
    status: p.status,
    priority: p.priority ?? 0,
    updatedAt: p.updatedAt,
    raw: p,
  };
}
