/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 GraphBuilderTab — 图谱构建任务列表 + dry-run 预览 + 回滚 + 实时日志
 *
 * 替换原 GraphSyncTab：
 * - 迁移到 /api/v1/knowledge/sync/jobs + /graph/build 体系
 * - 任务列表 / 预览 / 回滚 / 实时 SSE 日志（后端不可用时显示占位 "waiting P5"）
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  Database, Play, RefreshCw, Loader2, Eye, Undo2, Terminal, FileOutput,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { GraphBuildJob, GraphBuildPreview } from '../typesAndConstants';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

const STATUS_STYLES: Record<GraphBuildJob['status'], string> = {
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  RUNNING: 'bg-blue-50 text-blue-700 border-blue-200',
  SUCCEEDED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  FAILED: 'bg-rose-50 text-rose-700 border-rose-200',
  ROLLED_BACK: 'bg-slate-100 text-slate-600 border-slate-300',
};

export default function GraphBuilderTab({ showToast }: TabProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const zh = locale === 'zh';
  const [jobs, setJobs] = useState<GraphBuildJob[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [previewJob, setPreviewJob] = useState<GraphJobPreview | null>(null);
  const [logsJob, setLogsJob] = useState<{ job: GraphBuildJob; logs: string[] } | null>(null);
  const [building, setBuilding] = useState(false);

  const toast = useCallback(
    (type: 'success' | 'info' | 'error', msg: string) => (showToast ? showToast(type, msg) : console.info(msg)),
    [showToast]
  );

  const loadJobs = useCallback(async () => {
    setIsLoading(true);
    try {
      const list = await knowledgeApi.fetchGraphJobs();
      setJobs(list);
    } catch (e: any) {
      setJobs([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { loadJobs(); }, [loadJobs]);

  const handleDryRun = async () => {
    setBuilding(true);
    try {
      const preview = await knowledgeApi.previewGraphBuild({ dryRun: true });
      setPreviewJob({ kind: 'build', preview });
    } catch (e: any) {
      toast('error', t('knowledge.graph_builder.dryrun_failed') + ' ' + (e?.message || ''));
    } finally {
      setBuilding(false);
    }
  };

  const handleBuild = async () => {
    setBuilding(true);
    try {
      await knowledgeApi.triggerGraphBuild({ type: 'FULL', source: 'integration' });
      toast('success', t('knowledge.graph_builder.triggered'));
    } catch (e: any) {
      toast('error', t('knowledge.graph_builder.trigger_failed') + ' ' + (e?.message || ''));
    } finally {
      setBuilding(false);
      loadJobs();
    }
  };

  const openJobLogs = async (job: GraphBuildJob) => {
    try {
      const logs = await knowledgeApi.fetchGraphJobLogs(job.jobId);
      setLogsJob({ job, logs });
    } catch {
      setLogsJob({ job, logs: [t('knowledge.graph_builder.logs_unavailable')] });
    }
  };

  const openJobPreview = async (job: GraphBuildJob) => {
    try {
      const preview = await knowledgeApi.previewGraphJob(job.jobId);
      setPreviewJob({ kind: 'job', jobId: job.jobId, preview: { create: preview.create, update: preview.update, skip: preview.skip, samples: preview.samples as any } });
    } catch {
      setPreviewJob({ kind: 'job', jobId: job.jobId, preview: { create: 0, update: 0, skip: 0 } });
    }
  };

  const handleRollback = async (job: GraphBuildJob) => {
    try {
      await knowledgeApi.rollbackGraphJob(job.jobId);
      toast('success', t('knowledge.graph_builder.rolled_back') + job.jobId);
      loadJobs();
    } catch (e: any) {
      toast('error', t('knowledge.graph_builder.rollback_failed') + ' ' + (e?.message || ''));
    }
  };

  return (
    <div className="space-y-6">
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-4 gap-4`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <Database size={16} className="text-blue-600" />
            {t('knowledge.graph_builder.title')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('knowledge.graph_builder.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={loadJobs}
            disabled={isLoading}
            className={`px-3 py-1.5 ${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50`}
          >
            {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
            {t('knowledge.graph_builder.refresh')}
          </button>
          <button
            onClick={handleDryRun}
            disabled={building}
            className="px-3 py-1.5 bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50 border border-blue-200"
          >
            <Eye size={12} /> {t('knowledge.graph_builder.dry_run')}
          </button>
          <button
            onClick={handleBuild}
            disabled={building}
            className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50`}
          >
            {building ? <Loader2 size={12} className="animate-spin" /> : <Play size={12} />}
            {t('knowledge.graph_builder.full_build')}
          </button>
        </div>
      </div>

      {/* Jobs list */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <span className={`text-xs font-bold ${styles.cardText} flex items-center gap-2`}>
            <FileOutput size={13} className={styles.cardTextMuted} /> {t('knowledge.graph_builder.jobs_list')} ({jobs.length})
          </span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-[11px] border-collapse">
            <thead>
              <tr className={`${styles.badgeBg} ${styles.muted} border-b ${styles.cardBorder}`}>
                <th className="p-3 font-extrabold uppercase tracking-wider">Job ID</th>
                <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.graph_builder.col.type')}</th>
                <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.graph_builder.col.status')}</th>
                <th className="p-3 font-extrabold uppercase tracking-wider">{t('knowledge.graph_builder.col.created')}</th>
                <th className="p-3 font-extrabold uppercase tracking-wider text-right">{t('knowledge.graph_builder.col.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={5} className={`p-8 text-center ${styles.muted}`}>{t('knowledge.graph_builder.loading')}</td></tr>
              ) : jobs.length === 0 ? (
                <tr><td colSpan={5} className={`p-12 text-center ${styles.muted} text-xs space-y-2`}>
                  <Database size={24} className={`mx-auto ${styles.muted}`} />
                  <p>{zh ? '暂无图谱构建任务。点击「全量构建」触发一次 →' : 'No graph build jobs yet. Click "Full Build" to start one →'}</p>
                  <p className={`text-[10px] ${styles.muted} font-mono`}>waiting PMO-56 backend `/api/v1/knowledge/sync/jobs`</p>
                </td></tr>
              ) : jobs.map(job => (
                <tr key={job.jobId} className={`border-b ${styles.appBorder} ${styles.sidebarHoverBg} transition`}>
                  <td className={`p-3 font-mono font-bold ${styles.cardText}`}>{job.jobId}</td>
                  <td className={`p-3 ${styles.cardTextMuted}`}>{job.type}</td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 border rounded-full text-[10px] font-bold ${STATUS_STYLES[job.status]}`}>{job.status}</span>
                    {job.error && <p className="text-[9px] text-rose-500 mt-1 ml-0.5 truncate max-w-[180px]">{job.error}</p>}
                  </td>
                  <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>{job.createdAt}</td>
                  <td className="p-3">
                    <div className="flex gap-1.5 justify-end">
                      <button onClick={() => openJobPreview(job)} title={t('knowledge.graph_builder.action.preview')}
                        className={`p-1 ${styles.muted} hover:text-blue-600 cursor-pointer rounded ${styles.sidebarHoverBg}`}>
                        <Eye size={12} />
                      </button>
                      <button onClick={() => openJobLogs(job)} title={t('knowledge.graph_builder.action.logs')}
                        className={`p-1 ${styles.muted} hover:text-indigo-600 cursor-pointer rounded ${styles.sidebarHoverBg}`}>
                        <Terminal size={12} />
                      </button>
                      {(job.status === 'SUCCEEDED' || job.status === 'FAILED') && (
                        <button onClick={() => handleRollback(job)} title={t('knowledge.graph_builder.action.rollback')}
                          className={`p-1 ${styles.muted} hover:text-rose-600 cursor-pointer rounded ${styles.sidebarHoverBg}`}>
                          <Undo2 size={12} />
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

      {/* Build-time preview modal */}
      {previewJob && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0" style={{ background: 'rgba(15,23,42,0.5)' }} onClick={() => setPreviewJob(null)} />
          <div className={`relative w-full max-w-2xl ${styles.cardBg} border ${styles.cardBorder} rounded-2xl shadow-2xl overflow-hidden`}>
            <div className={`flex items-center justify-between px-5 py-3 border-b ${styles.cardBorder}`}>
              <h3 className={`text-sm font-extrabold ${styles.cardText} flex items-center gap-2`}>
                <Eye size={14} className="text-blue-600" />
                {previewJob.kind === 'build' ? t('knowledge.graph_builder.preview_title_build') : t('knowledge.graph_builder.preview_title_job')}
              </h3>
              <button onClick={() => setPreviewJob(null)} className={`p-1 ${styles.muted} ${styles.appText} cursor-pointer text-lg`}>X</button>
            </div>
            <div className="p-5 space-y-4">
              <div className="grid grid-cols-3 gap-3">
                {[
                  { label: t('knowledge.graph_builder.preview.create'), value: previewJob.preview.create, cls: 'text-emerald-600 border-emerald-200' },
                  { label: t('knowledge.graph_builder.preview.update'), value: previewJob.preview.update, cls: 'text-blue-600 border-blue-200' },
                  { label: t('knowledge.graph_builder.preview.skip'), value: previewJob.preview.skip, cls: `${styles.cardBorder} ${styles.muted}` },
                ].map((c, i) => (
                  <div key={i} className={`p-4 rounded-xl border ${c.cls} ${styles.cardBg} text-center space-y-1`}>
                    <div className={`text-[10px] font-extrabold uppercase ${styles.muted}`}>{c.label}</div>
                    <div className={`text-2xl font-black font-mono ${styles.cardText}`}>{c.value}</div>
                  </div>
                ))}
              </div>
              {previewJob.preview.samples && previewJob.preview.samples.length > 0 && (
                <div>
                  <span className={`text-[10px] font-extrabold ${styles.muted} uppercase block mb-1.5`}>{t('knowledge.graph_builder.preview.samples')}</span>
                  <div className={`max-h-48 overflow-y-auto border ${styles.cardBorder} rounded-lg`}>
                    <table className="w-full text-[10px]">
                      <thead className={`${styles.badgeBg} sticky top-0 sticky:shadow-sm`}>
                        <tr>
                          <th className={`p-2 text-left font-bold ${styles.cardTextMuted}`}>Entity ID</th>
                          <th className={`p-2 text-left font-bold ${styles.cardTextMuted}`}>Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {previewJob.preview.samples?.map((rawSample, idx) => {
                          const s = rawSample as unknown as Record<string, unknown>;
                          return (
                            <tr key={idx} className={`border-t ${styles.appBorder}`}>
                              <td className={`p-2 font-mono ${styles.sidebarText}`}>{String(s.entityId ?? s.id ?? JSON.stringify(s))}</td>
                              <td className={`p-2 font-semibold ${styles.cardText}`}>{String(s.action ?? '-')}</td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
            <div className={`p-4 border-t ${styles.cardBorder} flex justify-end gap-2`}>
              <button onClick={() => setPreviewJob(null)} className={`px-4 py-2 ${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.cardText} font-bold rounded-lg text-xs cursor-pointer`}>
                {t('knowledge.graph_builder.close')}
              </button>
              {previewJob.kind === 'build' && (
                <button onClick={handleBuild} disabled={building}
                  className={`px-4 py-2 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg text-xs cursor-pointer disabled:opacity-50 flex items-center gap-1.5`}>
                  <Play size={11} /> {t('knowledge.graph_builder.confirm_build')}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Job logs modal */}
      {logsJob && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0" style={{ background: 'rgba(15,23,42,0.5)' }} onClick={() => setLogsJob(null)} />
          <div className={`relative w-full max-w-3xl ${styles.cardBg} border ${styles.cardBorder} rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[75vh]`}>
            <div className={`flex items-center justify-between px-5 py-3 border-b ${styles.cardBorder}`}>
              <h3 className={`text-sm font-extrabold ${styles.cardText} flex items-center gap-2`}>
                <Terminal size={14} className="text-indigo-600" />
                {t('knowledge.graph_builder.logs_title')} · <span className="font-mono text-[11px]">{logsJob.job.jobId}</span>
              </h3>
              <button onClick={() => setLogsJob(null)} className={`p-1 ${styles.muted} ${styles.appText} cursor-pointer text-lg`}>X</button>
            </div>
            <div className="p-4 flex-1 overflow-y-auto bg-[var(--card,#020617)] rounded-lg">
              <pre className="font-mono text-[10px] text-[var(--card,#CBD5E1)] leading-relaxed whitespace-pre-wrap">
                {logsJob.logs.length === 0
                  ? `[?] ${t('knowledge.graph_builder.logs_unavailable')}\n→ ${t('knowledge.graph_builder.logs_hint')}`
                  : logsJob.logs.join('\n')}
              </pre>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

type GraphJobPreview =
  | { kind: 'build'; preview: GraphBuildPreview }
  | { kind: 'job'; jobId: string; preview: { create: number; update: number; skip: number; samples?: Array<Record<string, unknown>> } };
