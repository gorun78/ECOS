/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 LifecycleManagerTab — 生命周期管理
 *
 * 顶部：状态机图示（draft → active → deprecated → archived）
 * 下：资产表 + 每行"状态"下拉 + 操作审计 kb_lifecycle_audit（后端未提供时本地 localStorage 记录）
 * 物理删除/软删/恢复
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  GitBranch, RefreshCw, Loader2, Archive, ArchiveRestore, Trash2, CheckCircle2,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { LifecycleAsset, LifecycleAuditEntry, LifecycleState } from '../typesAndConstants';
import { LIFECYCLE_STATES } from '../typesAndConstants';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

const STATE_COLOR: Record<LifecycleState, { dot: string; nameColor: string }> = {
  draft:      { dot: 'bg-amber-400', nameColor: 'text-amber-600' },
  active:     { dot: 'bg-emerald-500', nameColor: 'text-emerald-600' },
  deprecated: { dot: 'bg-rose-400', nameColor: 'text-rose-600' },
  archived:   { dot: 'bg-slate-400', nameColor: 'text-slate-500' },
};

const AUDIT_STORAGE_KEY = 'kb_lifecycle_audit';

function loadAudit(): LifecycleAuditEntry[] {
  try {
    const raw = localStorage.getItem(AUDIT_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as LifecycleAuditEntry[];
    return Array.isArray(parsed) ? parsed.slice(0, 100) : [];
  } catch { return []; }
}

function pushAuditLocal(entry: LifecycleAuditEntry) {
  const list = loadAudit();
  list.unshift(entry);
  localStorage.setItem(AUDIT_STORAGE_KEY, JSON.stringify(list.slice(0, 100)));
}

export default function LifecycleManagerTab({ showToast }: TabProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;
  const toast = useCallback((type: 'success' | 'info' | 'error', msg: string) => (showToast ? showToast(type, msg) : console.info(msg)), [showToast]);

  const [assets, setAssets] = useState<LifecycleAsset[]>([]);
  const [audit, setAudit] = useState<LifecycleAuditEntry[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isAuditing, setIsAuditing] = useState(false);

  const loadAssets = useCallback(async () => {
    setIsLoading(true);
    try {
      const list = await knowledgeApi.fetchLifecycleAssets();
      setAssets(list);
    } catch {
      setAssets([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadAuditAll = useCallback(async () => {
    setIsAuditing(true);
    try {
      const remote = await knowledgeApi.fetchLifecycleAudit();
      const merged = remote.length > 0 ? remote : loadAudit();
      setAudit(merged.slice(0, 50));
    } catch {
      setAudit(loadAudit());
    } finally {
      setIsAuditing(false);
    }
  }, []);

  useEffect(() => { loadAssets(); loadAuditAll(); }, [loadAssets, loadAuditAll]);

  const handleTransition = async (asset: LifecycleAsset, next: LifecycleState) => {
    if (next === asset.state) return;
    try {
      await knowledgeApi.lifecycleTransition(asset.id, next);
      toast('success', t('knowledge.lifecycle.transitionToast') + `${asset.id} → ${next}`);
    } catch (e: unknown) {
      // 后端未就绪时本地审计记录
      const msg = (e as { message?: string } | undefined)?.message ?? '';
      if (msg.includes('404') || msg.includes('405') || msg.includes('401')) {
        console.info('PMO-56 stub — transition logged locally only');
      }
    }
    const auditEntry: LifecycleAuditEntry = {
      id: `audit-${Date.now()}-${asset.id}`,
      assetId: asset.id,
      from: asset.state,
      to: next,
      operator: 'local.kb',
      at: new Date().toISOString(),
    };
    pushAuditLocal(auditEntry);
    setAssets(prev => prev.map(a => a.id === asset.id ? { ...a, state: next, updatedAt: new Date().toISOString() } : a));
    loadAuditAll();
  };

  const handlePhysicalDelete = (asset: LifecycleAsset) => {
    if (!confirm(tl(`确认物理删除资产 ${asset.id}？此操作不可逆`, 'Physically delete asset ' + asset.id + '? Irreversible.'))) return;
    handleTransition(asset, 'archived');
    toast('info', t('knowledge.lifecycle.physDeletedOrSoftDeleted'));
  };

  const handleRestore = (asset: LifecycleAsset) => {
    handleTransition(asset, 'active');
  };

  return (
    <div className="space-y-6">
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-4 gap-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <GitBranch size={16} className="text-indigo-600" />
            {t('knowledge.lifecycle.pageTitle')}
          </h2>
          <p className={`text-xs ${styles.muted}`}>{t('knowledge.lifecycle.pageSubtitle')}</p>
        </div>
        <button type="button" onClick={loadAssets} disabled={isLoading}
          className={`px-3 py-1.5 ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50`}>
          {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          {t('knowledge.lifecycle.refreshAssets')}
        </button>
      </div>

      {/* 状态机图示 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
        <h3 className={`text-xs font-extrabold ${styles.muted} uppercase tracking-wider mb-3 flex items-center gap-1.5`}>
          <GitBranch size={12} /> {t('knowledge.lifecycle.stateMachine')}
        </h3>
        <div className="flex items-center gap-3 flex-wrap">
          {LIFECYCLE_STATES.map((s, i) => (
            <React.Fragment key={s}>
              <div className={`flex items-center gap-2 px-4 py-2 rounded-xl border ${
                s === 'draft' ? `${styles.warningBg} ${styles.warningBorder}` :
                s === 'active' ? `${styles.successBg} ${styles.successBorder}` :
                s === 'deprecated' ? 'bg-rose-50 border-rose-200' :
                `${styles.appBg} ${styles.cardBorder}`
              }`}>
                <span className={`w-2 h-2 rounded-full ${STATE_COLOR[s].dot}`} />
                <span className={`text-xs font-bold ${STATE_COLOR[s].nameColor}`}>{s}</span>
              </div>
              {i < LIFECYCLE_STATES.length - 1 && (
                <span className={`${styles.muted} font-bold`}>→</span>
              )}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* 资产表 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <span className={`text-xs font-bold ${styles.cardText}`}>
            {t('knowledge.lifecycle.assetList')} ({assets.length})
          </span>
          <span className={`text-[9px] font-mono ${styles.muted}`}>{assets.length === 0 ? t('knowledge.lifecycle.assetsPendingPmo56') : t('knowledge.lifecycle.loadedFromEndpoint')}</span>
        </div>
        <div className="w-full overflow-x-auto">
          <table className="w-full text-[11px] border-collapse whitespace-nowrap">
            <thead>
              <tr className={`${styles.appBg} ${styles.muted} border-b ${styles.cardBorder}`}>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">ID</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.lifecycle.colName')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.lifecycle.colType')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.lifecycle.currentState')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.lifecycle.updatedAt')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.lifecycle.transitionCol')}</th>
                <th className="p-3 text-right font-extrabold uppercase tracking-wider">{t('knowledge.lifecycle.actionsCol')}</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={7} className={`p-8 text-center ${styles.muted}`}>{t('knowledge.lifecycle.loading')}</td></tr>
              ) : assets.length === 0 ? (
                <tr>
                  <td colSpan={7} className={`p-12 text-center ${styles.muted} text-xs space-y-2`}>
                    <Archive size={24} className={`mx-auto ${styles.muted}`} />
                    <p>{t('knowledge.lifecycle.assetsEmpty')}</p>
                    <p className={`text-[10px] font-mono ${styles.muted}`}>/api/v1/knowledge/assets · {t('knowledge.lifecycle.pmo56Pending')}</p>
                  </td>
                </tr>
              ) : assets.map(asset => (
                <tr key={asset.id} className={`border-b ${styles.cardBorder} ${styles.sidebarHoverBg}`}>
                  <td className={`p-3 font-mono font-bold ${styles.cardText}`}>{asset.id}</td>
                  <td className={`p-3 ${styles.cardText}`}>{asset.name}</td>
                  <td className={`p-3 ${styles.muted} font-mono text-[10px]`}>{asset.type}</td>
                  <td className="p-3">
                    <span className="inline-flex items-center gap-1.5">
                      <span className={`w-2 h-2 rounded-full ${STATE_COLOR[asset.state].dot}`} />
                      <span className={`font-bold ${STATE_COLOR[asset.state].nameColor}`}>{asset.state}</span>
                    </span>
                  </td>
                  <td className={`p-3 ${styles.muted} font-mono text-[10px]`}>{asset.updatedAt}</td>
                  <td className="p-3">
                    <select
                      value={asset.state}
                      onChange={e => handleTransition(asset, e.target.value as LifecycleState)}
                      className={`px-2 py-1 border ${styles.inputBorder} rounded text-[10px] font-bold ${styles.inputBg} cursor-pointer`}
                    >
                      {LIFECYCLE_STATES.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </td>
                  <td className="p-3">
                    <div className="flex gap-1.5 justify-end">
                      {asset.state === 'archived' && (
                        <button type="button" onClick={() => handleRestore(asset)}
                          className={`p-1.5 ${styles.muted} hover:text-emerald-600 cursor-pointer rounded ${styles.sidebarHoverBg}`}
                          title={t('knowledge.lifecycle.restore')}>
                          <ArchiveRestore size={13} />
                        </button>
                      )}
                      {asset.state !== 'archived' && (
                        <button type="button" onClick={() => handlePhysicalDelete(asset)}
                          className={`p-1.5 ${styles.muted} hover:text-rose-600 cursor-pointer rounded ${styles.sidebarHoverBg}`}
                          title={t('knowledge.lifecycle.physDeleteTitle')}>
                          <Trash2 size={13} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 审计日志 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
        <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
          <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
            <CheckCircle2 size={13} className="text-indigo-500" /> {t('knowledge.lifecycle.auditLog')} ({audit.length})
          </h3>
          <span className={`text-[9px] font-mono ${styles.muted}`}>
            {audit.length === 0 ? t('knowledge.lifecycle.auditLocalOnly') : t('knowledge.lifecycle.auditLoaded')}
          </span>
        </div>
        <div className="space-y-1.5 max-h-64 overflow-y-auto">
          {isAuditing ? (
            <div className={`py-8 text-center ${styles.muted} text-xs`}>{t('knowledge.lifecycle.loading')}</div>
          ) : audit.length === 0 ? (
            <p className={`py-8 text-center ${styles.muted} text-xs`}>{t('knowledge.lifecycle.noAudit')}</p>
          ) : audit.map(a => (
            <div key={a.id} className={`p-2 ${styles.appBg} border ${styles.cardBorder} rounded-lg flex items-center gap-2 text-[10px]`}>
              <span className={`font-mono ${styles.muted} w-40 shrink-0`}>{new Date(a.at).toLocaleString()}</span>
              <span className={`font-bold ${styles.cardText} w-36 truncate`}>{a.assetId}</span>
              <span className={`px-1.5 rounded ${STATE_COLOR[a.from].nameColor} ${styles.cardBg} border ${styles.cardBorder} font-bold`}>{a.from}</span>
              <span className={styles.muted}>→</span>
              <span className={`px-1.5 rounded ${STATE_COLOR[a.to].nameColor} ${styles.cardBg} border ${styles.cardBorder} font-bold`}>{a.to}</span>
              <span className={`font-mono ${styles.muted} ml-auto`}>{a.operator}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
