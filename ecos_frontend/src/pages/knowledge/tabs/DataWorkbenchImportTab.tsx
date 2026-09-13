/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 DataWorkbenchImportTab — 数据导入
 *
 * 左侧：数据源清单（/api/integration/metadata/sources 真实数据）
 * 右侧：选中 → 预览 pipeline 产物（/api/v1/integration/metadata/drift 返回 sample 表格 + 血缘）
 * 操作：入摄入队列 → knowledgeApi.addImportToQueue({dsId, pipelineId, options})；队列 table 状态 + 批量重试
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  Download, RefreshCw, Loader2, Database, Plus, AlertCircle,
  CheckCircle2, ListOrdered,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import { apiFetchData } from '../../../api';
import type { ImportQueueItem } from '../typesAndConstants';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

const STATUS_STYLES: Record<ImportQueueItem['status'], { cls: string; labelKey: 'queued' | 'parsing' | 'vectorizing' | 'done' | 'failed' }> = {
  queued:      { cls: 'bg-blue-50 text-blue-700 border-blue-200', labelKey: 'queued' },
  parsing:     { cls: 'bg-indigo-50 text-indigo-700 border-indigo-200', labelKey: 'parsing' },
  vectorizing: { cls: 'bg-violet-50 text-violet-700 border-violet-200', labelKey: 'vectorizing' },
  done:        { cls: 'bg-emerald-50 text-emerald-700 border-emerald-200', labelKey: 'done' },
  failed:      { cls: 'bg-rose-50 text-rose-700 border-rose-200', labelKey: 'failed' },
};

export default function DataWorkbenchImportTab({ showToast }: TabProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;
  const toast = useCallback(
    (type: 'success' | 'info' | 'error', msg: string) => (showToast ? showToast(type, msg) : console.info(msg)),
    [showToast]
  );

  const [sources, setSources] = useState<any[]>([]);
  const [selectedSource, setSelectedSource] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [driftPreview, setDriftPreview] = useState<{ fields: any[]; rows: any[]; lineage: any } | null>(null);
  const [queue, setQueue] = useState<ImportQueueItem[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);

  const loadSources = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await apiFetchData<any>('/api/integration/metadata');
      const list = Array.isArray(data) ? data : (data?.data as any[]) || data?.sources || [];
      const mapped = (list as any[]).map((s: any, i: number) => ({
        id: String(s.id ?? s.dsId ?? `ds-${i}`),
        name: String(s.name ?? s.tableName ?? s.id ?? `source-${i}`),
        type: String(s.sourceType ?? s.type ?? 'integration'),
        status: (s.status ?? (s.syncStatus === 'synced' ? 'connected' : 'disconnected')),
        records: s.records ?? s.recordsOrFields ?? '—',
      }));
      setSources(mapped.filter(s => s.id));
    } catch {
      setSources([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const loadDrift = useCallback(async (dsId: string) => {
    setDriftPreview(null);
    try {
      const data = await apiFetchData<any>('/api/integration/metadata/drift?sample=true&dsId=' + encodeURIComponent(dsId));
      const raw = Array.isArray(data) ? { fields: data } : data;
      setDriftPreview({
        fields: raw?.fields ?? raw?.schemaDelta ?? raw?.columns ?? [],
        rows: raw?.rows ?? raw?.samples ?? [],
        lineage: raw?.lineage ?? null,
      });
    } catch {
      setDriftPreview({ fields: [], rows: [], lineage: null });
    }
  }, []);

  const loadQueue = useCallback(async () => {
    setQueueLoading(true);
    try {
      const list = knowledgeApi.fetchImportQueue();
      setQueue(list);
    } finally {
      setQueueLoading(false);
    }
  }, []);

  useEffect(() => { loadSources(); loadQueue(); }, [loadSources, loadQueue]);

  const handleEnqueue = () => {
    if (!selectedSource) return;
    const src = sources.find(s => s.id === selectedSource);
    if (!src) return;
    try {
      const entry = knowledgeApi.addImportToQueue({
        dsId: src.id,
        label: src.name,
        pipelineId: src.id,
      });
      toast('success', tl('已入摄入队列: ', 'Enqueued: ') + src.name);
      loadQueue();
    } catch (e: any) {
      toast('error', tl('入队失败: ', 'Enqueue failed: ') + (e?.message || ''));
    }
  };

  const handleRetry = (id: string) => {
    try {
      knowledgeApi.retryImport(id);
      toast('success', tl('已重置为 queued: ' + id, 'Reset to queued: ' + id));
      loadQueue();
    } catch (e: any) {
      toast('error', tl('重试失败: ', 'Retry failed: ') + (e?.message || ''));
    }
  };

  const failedCount = queue.filter(q => q.status === 'failed').length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b border-slate-200 ${styles.cardBorder} pb-4 gap-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <Download size={16} className="text-blue-600" />
            {tl('数据导入（集成工作台 → 知识摄入队列）', 'Data Import (Integration → KB Queue)')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>{tl('左侧选数据源，右侧预览 schema/样本/血缘，加入摄入队列', 'Left source, right preview, enqueue to vectorize')}</p>
        </div>
        <div className="flex gap-2">
          <button onClick={loadSources} disabled={isLoading}
            className={`px-3 py-1.5 ${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50`}>
            {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
            {tl('刷新数据源', 'Refresh sources')}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 左侧：数据源清单 */}
        <div className={`${styles.cardBg} border border-slate-200 ${styles.cardBorder} rounded-xl overflow-hidden`}>
          <div className={`px-4 py-3 border-b border-slate-100 ${styles.appBorder} flex items-center justify-between`}>
            <span className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
              <Database size={13} className="text-indigo-500" /> {tl('数据源', 'Data Sources')} ({sources.length})
            </span>
            <span className={`text-[9px] font-mono ${styles.muted}`}>/api/integration/metadata</span>
          </div>
          <div className={`divide-y divide-slate-100 ${styles.sidebarBorder} max-h-[460px] overflow-y-auto`}>
            {isLoading ? (
              <div className={`p-10 text-center ${styles.muted} text-xs`}>{tl('加载中...', 'Loading...')}</div>
            ) : sources.length === 0 ? (
              <div className={`p-10 text-center ${styles.muted} text-xs space-y-1`}>
                <p>{tl('暂无数据源返回', 'No sources returned')}</p>
                <p className={`text-[9px] font-mono ${styles.muted}`}>{tl('PMO-56 后端待补 /integration/metadata/sources', 'PMO-56: /integration/metadata/sources')}</p>
              </div>
            ) : sources.map(s => (
              <button
                key={s.id}
                onClick={() => { setSelectedSource(s.id); loadDrift(s.id); }}
                className={`w-full text-left px-4 py-3 transition ${
                  selectedSource === s.id ? 'bg-indigo-50 border-l-2 border-indigo-500' : `${styles.sidebarHoverBg} border-l-2 border-transparent`
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className={`font-bold text-xs ${styles.cardText} truncate flex-1`}>{s.name}</span>
                  <span className={`px-1.5 py-0.5 rounded-full text-[8px] font-bold ${
                    s.status === 'connected' ? `${styles.successBg} ${styles.successText}` : `${styles.badgeBg} ${styles.cardTextMuted}`
                  }`}>{s.status}</span>
                </div>
                <div className={`text-[10px] ${styles.cardTextMuted} font-mono`}>{s.id}</div>
                <div className={`text-[9px] ${styles.muted}`}>{s.type} · {s.records}</div>
              </button>
            ))}
          </div>
          <div className={`p-3 border-t border-slate-100 ${styles.appBorder}`}>
            <button
              onClick={handleEnqueue}
              disabled={!selectedSource}
              className="w-full py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-200 disabled:opacity-50 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center justify-center gap-1.5 disabled:cursor-not-allowed"
            >
              <Plus size={12} /> {tl('并入摄入队列', 'Enqueue to Import Queue')}
            </button>
          </div>
        </div>

        {/* 右侧：预览 */}
        <div className={`lg:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-4 overflow-y-auto max-h-[560px]`}>
          <div className={`flex items-center justify-between border-b ${styles.appBorder} pb-2`}>
            <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
              <Database size={13} className="text-blue-500" />
              {tl('Pipeline 产物预览', 'Pipeline Output Preview')}
            </h3>
            <span className={`text-[9px] font-mono ${styles.muted}`}>/integration/metadata/drift · sample</span>
          </div>
          {!selectedSource ? (
            <div className={`py-16 text-center ${styles.muted} space-y-2`}>
              <Database size={24} className={`mx-auto ${styles.muted}`} />
              <p className="text-xs">{tl('从左侧选中一个数据源查看 schema / 行样本 / 血缘', 'Select a data source to preview schema / sample rows / lineage')}</p>
            </div>
          ) : !driftPreview ? (
            <div className={`py-12 text-center ${styles.muted} text-xs`}>{tl('加载预览...', 'Loading preview...')}</div>
          ) : (
            <>
              {/* Schema */}
              {driftPreview.fields.length > 0 && (
                <div>
                  <span className={`text-[10px] font-extrabold ${styles.muted} uppercase block mb-1.5`}>{tl('字段 Schema', 'Schema')} ({driftPreview.fields.length})</span>
                  <div className={`overflow-x-auto border ${styles.cardBorder} rounded-lg`}>
                    <table className="w-full text-[10px]">
                      <thead>
                        <tr className={`${styles.badgeBg} border-b ${styles.cardBorder}`}>
                          <th className={`p-2 text-left font-bold ${styles.cardTextMuted}`}>column</th>
                          <th className={`p-2 text-left font-bold ${styles.cardTextMuted}`}>type</th>
                          <th className={`p-2 text-left font-bold ${styles.cardTextMuted}`}>nullable</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(driftPreview.fields as any[]).map((f: any, i: number) => (
                          <tr key={i} className={`border-t ${styles.appBorder}`}>
                            <td className={`p-2 font-mono ${styles.cardText}`}>{String(f.name ?? f.column ?? f.field ?? '')}</td>
                            <td className={`p-2 ${styles.sidebarText}`}>{String(f.type ?? f.dataType ?? '—')}</td>
                            <td className={`p-2 ${styles.cardTextMuted}`}>{String(f.nullable ?? '—')}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
              {/* Sample rows */}
              {driftPreview.rows && driftPreview.rows.length > 0 && (
                <div>
                  <span className={`text-[10px] font-extrabold ${styles.muted} uppercase block mb-1.5`}>{tl('行样本 (limit 5)', 'Sample Rows (limit 5)')}</span>
                  <div className={`overflow-x-auto border ${styles.cardBorder} rounded-lg`}>
                    <table className="w-full text-[10px]">
                      <tbody>
                        {(driftPreview.rows as any[]).slice(0, 5).map((r: any, i: number) => (
                          <tr key={i} className={`border-t ${styles.appBorder} first:border-0`}>
                            {Object.entries(r).slice(0, 6).map(([k, v], j) => (
                              <td key={j} className={`p-2 font-mono ${styles.sidebarText}`}>
                                <span className={`${styles.muted} mr-1`}>{k}:</span>
                                {String(v).substring(0, 40)}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
              {/* Lineage stub */}
              <div>
                <span className={`text-[10px] font-extrabold ${styles.muted} uppercase block mb-1.5`}>{tl('血缘 Lineage', 'Lineage')}</span>
                <div className={`p-3 ${styles.badgeBg} border ${styles.cardBorder} rounded-lg text-[10px] ${styles.sidebarText} space-y-1`}>
                  {driftPreview.lineage ? (
                    <>
                      <span className="font-mono">{JSON.stringify(driftPreview.lineage, null, 2).substring(0, 400)}</span>
                    </>
                  ) : (
                    <p>{tl('后端 /integration/metadata/drift 未返回血缘字段（PMO-56 待补）', 'drift endpoint did not return lineage payload (PMO-56)')}</p>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {/* 底部：摄入队列 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <span className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
            <ListOrdered size={13} className="text-indigo-500" /> {tl('摄入队列', 'Import Queue')} ({queue.length})
          </span>
          {failedCount > 0 && (
            <span className="text-[10px] font-bold text-rose-600">{failedCount} failed · {tl('批量重试', 'retry-all')}</span>
          )}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-[11px] border-collapse">
            <thead>
              <tr className={`${styles.badgeBg} ${styles.muted} border-b ${styles.cardBorder}`}>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">Label</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">dsId</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">pipelineId</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('状态', 'Status')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('入队时间', 'Enrolled')}</th>
                <th className="p-3 text-right font-extrabold uppercase tracking-wider">{tl('操作', 'Action')}</th>
              </tr>
            </thead>
            <tbody>
              {queueLoading ? (
                <tr><td colSpan={6} className={`p-8 text-center ${styles.muted}`}>{tl('加载中...', 'Loading...')}</td></tr>
              ) : queue.length === 0 ? (
                <tr><td colSpan={6} className={`p-10 text-center ${styles.muted} text-xs`}>
                  {tl('队列空。在上方选中数据源后点击「并入摄入队列」', 'Queue empty. Select a source and click "Enqueue" above.')}
                </td></tr>
              ) : queue.map(item => (
                <tr key={item.id} className={`border-b ${styles.appBorder} ${styles.sidebarHoverBg}`}>
                  <td className={`p-3 font-bold ${styles.cardText}`}>{item.label}</td>
                  <td className={`p-3 font-mono ${styles.cardTextMuted}`}>{item.dsId}</td>
                  <td className={`p-3 font-mono ${styles.cardTextMuted}`}>{item.pipelineId || '—'}</td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${STATUS_STYLES[item.status].cls}`}>
                      {tl(STATUS_STYLES[item.status].labelKey.toUpperCase(), STATUS_STYLES[item.status].labelKey.toUpperCase())}
                    </span>
                    {item.errorMsg && <p className="text-[9px] text-rose-600 mt-1 truncate max-w-[180px]">{item.errorMsg}</p>}
                  </td>
                  <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>{item.enrollmentAt}</td>
                  <td className="p-3 text-right">
                    {item.status === 'failed' || item.status === 'queued' ? (
                      <button onClick={() => handleRetry(item.id)}
                        className={`px-2 py-1 ${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-md text-[10px] flex items-center gap-1 ml-auto`}>
                        <RefreshCw size={10} /> {tl('重试', 'Retry')}
                      </button>
                    ) : (
                      <span className={`${styles.muted} text-[10px] font-bold ml-auto`}>{tl('运行中...', 'running...')}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
