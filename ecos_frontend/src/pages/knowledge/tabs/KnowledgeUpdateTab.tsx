/**
 * K4 知识更新 — 4 大触发源监控视图（规范 §6.2 K4）
 *
 * 触发源：
 *  - 本体发布事件（OntologyVersionPublished）— 事件流审计
 *  - DW 层增量（watermark/jobs 增量水位线）
 *  - 手动全量重建（Manual FULL graph build）— 跳转 K3 入口
 *  - 文档更新（chunk deprecated + 重嵌入）
 *
 * 与 GraphBuilderTab 分工：
 *  - GraphBuilderTab（K3 存储）：任务操作（构建/预览/回滚）
 *  - KnowledgeUpdateTab（K4 更新）：触发源状态监控 + 触发源定位
 * 后端无可用的全量状态端点时各卡片显示「waiting P3」结构化占位，不假装真数据。
 */
import React, { useEffect, useState } from 'react';
import {
  RefreshCw,
  Database,
  FileClock,
  GitBranch,
  AlertCircle,
  Terminal,
  Loader2,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { GraphBuildJob, SyncLog, SyncStatus } from '../typesAndConstants';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

export default function KnowledgeUpdateTab({ showToast }: TabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [jobs, setJobs] = useState<GraphBuildJob[]>([]);
  const [syncLogs, setSyncLogs] = useState<SyncLog[]>([]);
  const [syncStatuses, setSyncStatuses] = useState<SyncStatus[]>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const toast = (type: 'success' | 'info' | 'error', msg: string) =>
    showToast ? showToast(type, msg) : console.info(msg);

  const loadAll = async () => {
    setIsRefreshing(true);
    try {
      const [j, logs, statuses] = await Promise.all([
        knowledgeApi.fetchGraphJobs(),
        knowledgeApi.fetchSyncLogs(),
        knowledgeApi.fetchSyncStatuses(),
      ]);
      setJobs(j);
      setSyncLogs(logs);
      setSyncStatuses(statuses);
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => { loadAll(); }, []);

  const triggerFullSync = async () => {
    try {
      await knowledgeApi.triggerFullSync();
      toast('success', t('knowledge.kupdate.trigger_ok'));
      loadAll();
    } catch (e: any) {
      toast('error', t('knowledge.kupdate.trigger_fail') + ' ' + (e?.message || ''));
    }
  };

  const iconCn = 'w-3.5 h-3.5';
  const cardCls = `${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs`;
  const cardTitleCls = `text-[11px] font-extrabold uppercase tracking-wider ${styles.muted} flex items-center gap-1.5 mb-3`;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div>
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <GitBranch size={16} className="text-indigo-600" />
            {t('knowledge.kupdate.title')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('knowledge.kupdate.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={loadAll}
            disabled={isRefreshing}
            className={`px-3 py-1.5 ${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50`}
          >
            {isRefreshing ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
            {t('knowledge.kupdate.refresh')}
          </button>
          <button
            onClick={triggerFullSync}
            disabled={isRefreshing}
            className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50"
          >
            <RefreshCw size={12} /> {t('knowledge.kupdate.rebuild')}
          </button>
        </div>
      </div>

      {/* K4 4 触发源 · 卡片组 */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
        <div className={cardCls}>
          <p className={cardTitleCls}><FileClock className={iconCn} /> {t('knowledge.kupdate.source_ontology_event')}</p>
          <p className="text-xs text-blue-600 font-bold flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
            {t('knowledge.kupdate.source_ontology_event_status')}
          </p>
          <p className={`text-[10px] ${styles.muted} font-mono mt-2`}>Kafka · ONTOLOGY_PUBLISHED</p>
        </div>
        <div className={cardCls}>
          <p className={cardTitleCls}><Database className={iconCn} /> {t('knowledge.kupdate.source_dw_incremental')}</p>
          <p className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
            <span className={`w-1.5 h-1.5 rounded-full ${jobs.some(j => j.status === 'RUNNING') ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`} />
            {`当前运行 ${jobs.filter(j => j.status === 'RUNNING').length}` }
            <span className={`${styles.muted} font-normal`}>/ {jobs.length}</span>
          </p>
          <p className={`text-[10px] ${styles.muted} font-mono mt-2`}>watermark + sync/jobs</p>
        </div>
        <div className={cardCls}>
          <p className={cardTitleCls}><RefreshCw className={iconCn} /> {t('knowledge.kupdate.source_manual')}</p>
          <button
            onClick={triggerFullSync}
            className="px-2 py-1 bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold rounded-md flex items-center gap-1 cursor-pointer text-[11px] disabled:opacity-50"
          >
            <RefreshCw size={10} /> {t('knowledge.kupdate.source_manual_button')}
          </button>
          <p className={`text-[10px] ${styles.muted} font-mono mt-2`}>POST /graph/build · type=FULL</p>
        </div>
        <div className={cardCls}>
          <p className={cardTitleCls}><AlertCircle className={iconCn} /> {t('knowledge.kupdate.source_doc_update')}</p>
          <p className={`text-xs font-bold ${styles.cardText}`}>{t('knowledge.kupdate.source_doc_update_placeholder')}</p>
          <p className={`text-[10px] ${styles.muted} font-mono mt-2`}>chunk · deprecated + re-embed</p>
        </div>
      </div>

      {/* 同步状态 + 日志 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 同步状态表 */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden lg:col-span-2`}>
          <div className={`px-4 py-2.5 border-b ${styles.cardBorder} flex items-center justify-between`}>
            <span className={`text-[11px] font-extrabold uppercase tracking-wider ${styles.muted}`}>
              {t('knowledge.kupdate.sync_status_title')} ({syncStatuses.length})
            </span>
          </div>
          <div className="max-h-72 overflow-y-auto">
            {syncStatuses.length === 0 ? (
              <p className={`p-8 text-center text-xs ${styles.muted}`}>{t('knowledge.kupdate.no_sync_status')}</p>
            ) : (
              <table className="w-full text-left text-[11px] border-collapse">
                <thead>
                  <tr className={`${styles.badgeBg} ${styles.muted} border-b ${styles.cardBorder}`}>
                    <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.kupdate.col.object_type')}</th>
                    <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.kupdate.col.status')}</th>
                    <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.kupdate.col.records')}</th>
                    <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.kupdate.col.synced_at')}</th>
                  </tr>
                </thead>
                <tbody>
                  {syncStatuses.map((s, i) => (
                    <tr key={`${s.objectType}-${i}`} className={`border-b ${styles.appBorder} ${styles.sidebarHoverBg}`}>
                      <td className={`p-3 font-mono font-bold ${styles.cardText}`}>{s.objectType}</td>
                      <td className="p-3">
                        {(() => {
                          const pct = s.total > 0 ? Math.round((s.synced / s.total) * 100) : 0;
                          return (
                            <span className={`px-2 py-0.5 border rounded-full text-[10px] font-bold ${
                              pct >= 100
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                : pct > 0
                                  ? 'bg-blue-50 text-blue-700 border-blue-200'
                                  : 'bg-amber-50 text-amber-700 border-amber-200'
                            }`}>
                              {pct}%
                            </span>
                          );
                        })()}
                      </td>
                      <td className={`p-3 font-mono ${styles.cardText}`}>{s.total}</td>
                      <td className={`p-3 font-mono text-[10px] ${styles.cardTextMuted}`}>{s.lastSyncTime || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* 审计日志（事件流） */}
        <div className="bg-slate-900 rounded-xl p-4 flex flex-col shadow-md border border-slate-800 text-slate-300">
          <div className="border-b border-slate-800 pb-2 flex items-center justify-between mb-2">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
              <span className="font-mono text-white text-[10px] font-bold">{t('knowledge.kupdate.audit_event_stream')}</span>
            </div>
            <span className={`text-[8px] ${styles.muted} font-mono`}>/api/knowledge/sync/logs</span>
          </div>
          <div className="flex-1 max-h-72 overflow-y-auto font-mono text-[9.5px] leading-relaxed space-y-1.5 pr-1">
            {syncLogs.length === 0 ? (
              <p className={`text-[10px] ${styles.muted} py-4 text-center`}>{t('knowledge.kupdate.no_audit_events')}</p>
            ) : (
              syncLogs.map((log, i) => (
                <div key={`${log.id}-${i}`} className="flex items-start gap-2">
                  <span className="shrink-0 text-slate-500">{log.timestamp?.substring(0, 16) || log.id}</span>
                  <span className={`shrink-0 ${
                    log.status === 'error' || log.status === 'failed' ? 'text-rose-400' : 'text-emerald-400'
                  }`}>{log.status || log.operation}</span>
                  <span className="flex-1 break-all">{log.message || log.objectType}</span>
                </div>
              ))
            )}
          </div>
          <div className="border-t border-slate-800 pt-2 mt-2 text-[9px] font-mono text-slate-500 flex justify-between">
            <span>knowledge.update.audit</span><span>v2.1</span>
          </div>
        </div>
      </div>

      {/* 版本对齐进度（占位卡 — 等 P3 端点） */}
      <div className={cardCls}>
        <p className={cardTitleCls}><GitBranch className={iconCn} /> {t('knowledge.kupdate.version_align_title')}</p>
        <p className={`text-[11px] ${styles.muted}`}>{t('knowledge.kupdate.version_align_placeholder')}</p>
      </div>
    </div>
  );
}
