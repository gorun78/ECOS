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
      toast('success', tl('已转换状态: ', 'Transition: ') + `${asset.id} → ${next}`);
    } catch (e: any) {
      // 后端未就绪时本地审计记录
      if (e?.message?.includes('404') || e?.message?.includes('405') || e?.message?.includes('401')) {
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
    toast('info', tl('物理删除标记为 → archived（软删）', 'Acted as archive (soft delete)'));
  };

  const handleRestore = (asset: LifecycleAsset) => {
    handleTransition(asset, 'active');
  };

  return (
    <div className="space-y-6">
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.divider} pb-4 gap-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <GitBranch size={16} className="text-indigo-600" />
            {tl('生命周期管理（状态机 + 审计）', 'Lifecycle Management (State Machine + Audit)')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>{tl('draft → active → deprecated → archived · 每行状态变更写 kb_lifecycle_audit', '')}</p>
        </div>
        <button onClick={loadAssets} disabled={isLoading}
          className={`px-3 py-1.5 ${styles.appBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50`}>
          {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          {tl('刷新资产', 'Refresh')}
        </button>
      </div>

      {/* 状态机图示 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
        <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider mb-3 flex items-center gap-1.5`}>
          <GitBranch size={12} /> {tl('状态机', 'STATE MACHINE')}
        </h3>
        <div className="flex items-center gap-3 flex-wrap">
          {LIFECYCLE_STATES.map((s, i) => (
            <React.Fragment key={s}>
              <div className={`flex items-center gap-2 px-4 py-2 rounded-xl border ${
                s === 'draft' ? 'bg-amber-50 border-amber-200' :
                s === 'active' ? 'bg-emerald-50 border-emerald-200' :
                s === 'deprecated' ? 'bg-rose-50 border-rose-200' :
                `${styles.appBg} border ${styles.cardBorder}`
              }`}>
                <span className={`w-2 h-2 rounded-full ${STATE_COLOR[s].dot}`} />
                <span className={`text-xs font-bold ${STATE_COLOR[s].nameColor}`}>{s}</span>
              </div>
              {i < LIFECYCLE_STATES.length - 1 && (
                <span className={`${styles.cardTextMuted} font-bold`}>→</span>
              )}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* 资产表 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <span className={`text-xs font-bold ${styles.cardText}`}>
            {tl('资产列表', 'Assets')} ({assets.length})
          </span>
          <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>{assets.length === 0 ? tl('/assets 待 PMO-56 后端提供', '/assets endpoint awaiting PMO-56') : tl('加载自 /api/v1/knowledge/assets', 'loaded from /api/v1/knowledge/assets')}</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-[11px] border-collapse">
            <thead>
              <tr className={`${styles.appBg} ${styles.cardTextMuted} border-b ${styles.cardBorder}`}>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">ID</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('名称', 'Name')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('类型', 'Type')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('当前状态', 'Current State')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('更新于', 'Updated')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('转换', 'Transition')}</th>
                <th className="p-3 text-right font-extrabold uppercase tracking-wider">{tl('操作', 'Actions')}</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={7} className={`p-8 text-center ${styles.cardTextMuted}`}>{tl('加载中...', 'Loading...')}</td></tr>
              ) : assets.length === 0 ? (
                <tr>
                  <td colSpan={7} className={`p-12 text-center ${styles.cardTextMuted} text-xs space-y-2`}>
                    <Archive size={24} className={`mx-auto ${styles.cardTextMuted}`} />
                    <p>{tl('暂无生命周期资产', 'No lifecycle assets')}</p>
                    <p className={`text-[10px] font-mono ${styles.cardTextMuted}`}>/api/v1/knowledge/assets · {tl('PMO-56 待补', 'PMO-56 pending')}</p>
                  </td>
                </tr>
              ) : assets.map(asset => (
                <tr key={asset.id} className={`border-b ${styles.divider} hover:bg-blue-50/20`}>
                  <td className={`p-3 font-mono font-bold ${styles.cardText}`}>{asset.id}</td>
                  <td className={`p-3 ${styles.cardText}`}>{asset.name}</td>
                  <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>{asset.type}</td>
                  <td className="p-3">
                    <span className="inline-flex items-center gap-1.5">
                      <span className={`w-2 h-2 rounded-full ${STATE_COLOR[asset.state].dot}`} />
                      <span className={`font-bold ${STATE_COLOR[asset.state].nameColor}`}>{asset.state}</span>
                    </span>
                  </td>
                  <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>{asset.updatedAt}</td>
                  <td className="p-3">
                    <select
                      value={asset.state}
                      onChange={e => handleTransition(asset, e.target.value as LifecycleState)}
                      className={`px-2 py-1 border ${styles.inputBorder} rounded text-[10px] font-bold ${styles.cardBg} cursor-pointer`}
                    >
                      {LIFECYCLE_STATES.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </td>
                  <td className="p-3">
                    <div className="flex gap-1.5 justify-end">
                      {asset.state === 'archived' && (
                        <button onClick={() => handleRestore(asset)}
                          className={`p-1.5 ${styles.cardTextMuted} opacity-80 hover:opacity-100 hover:text-emerald-600 cursor-pointer rounded hover:bg-blue-50/20`}
                          title={tl('恢复', 'Restore to active')}>
                          <ArchiveRestore size={13} />
                        </button>
                      )}
                      {asset.state !== 'archived' && (
                        <button onClick={() => handlePhysicalDelete(asset)}
                          className={`p-1.5 ${styles.cardTextMuted} opacity-80 hover:opacity-100 hover:text-rose-600 cursor-pointer rounded hover:bg-blue-50/20`}
                          title={tl('物理删除（实际为软删 → archived）', 'Physical delete (soft → archived)')}>
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
        <div className={`flex items-center justify-between border-b ${styles.divider} pb-2`}>
          <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
            <CheckCircle2 size={13} className="text-indigo-500" /> {tl('操作审计日志 (kb_lifecycle_audit)', 'Audit Trail (kb_lifecycle_audit)')} ({audit.length})
          </h3>
          <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>
            {audit.length === 0 ? tl('后端 /lifecycle/audit 待 PMO-56 后端提供，目前仅本地 localStorage 记录', 'Backend /lifecycle/audit awaiting PMO-56; recording locally via localStorage') : tl('加载自 /lifecycle/audit', 'from /lifecycle/audit')}
          </span>
        </div>
        <div className="space-y-1.5 max-h-64 overflow-y-auto">
          {isAuditing ? (
            <div className={`py-8 text-center ${styles.cardTextMuted} text-xs`}>{tl('加载中...', 'Loading...')}</div>
          ) : audit.length === 0 ? (
            <p className={`py-8 text-center ${styles.cardTextMuted} text-xs`}>{tl('暂无审计记录', 'No audit entries yet')}</p>
          ) : audit.map(a => (
            <div key={a.id} className={`p-2 ${styles.appBg} border ${styles.divider} rounded-lg flex items-center gap-2 text-[10px]`}>
              <span className={`font-mono ${styles.cardTextMuted} w-40 shrink-0`}>{new Date(a.at).toLocaleString()}</span>
              <span className={`font-bold ${styles.cardText} w-36 truncate`}>{a.assetId}</span>
              <span className={`px-1.5 rounded ${STATE_COLOR[a.from].nameColor} ${styles.cardBg} border ${styles.cardBorder} font-bold`}>{a.from}</span>
              <span className={styles.cardTextMuted}>→</span>
              <span className={`px-1.5 rounded ${STATE_COLOR[a.to].nameColor} ${styles.cardBg} border ${styles.cardBorder} font-bold`}>{a.to}</span>
              <span className={`font-mono ${styles.cardTextMuted} ml-auto`}>{a.operator}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
