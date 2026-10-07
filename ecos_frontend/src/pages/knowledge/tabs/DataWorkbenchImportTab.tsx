/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO K1 DataWorkbenchImportTab — 知识结构化抽取（原数据导入队列已下线）
 *
 * K1 结构化（映射驱动）实例抽取主力：
 * 左侧：本体版本选择（/api/v1/ecos/versions）+ 抽取模式（FULL/INCREMENTAL）
 * 右侧：Dry-run 预览报告 + 触发真实抽取 + 抽取作业列表（行展开看 detail）
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  Download, RefreshCw, Loader2, ChevronDown, ChevronRight,
  Eye, PlayCircle, ListOrdered,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import { apiFetchData } from '../../../api';
import type { StructuredExtractReport, StructuredExtractJob } from '../typesAndConstants';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

/** 本体版本下拉项（OntologyVersionVO 子集） */
interface OntologyVersionOption {
  id: string;
  ontologyId: string;
  versionNo: string;
  status: string;
}

/** 抽取模式 */
type ExtractMode = 'FULL' | 'INCREMENTAL';

/** 作业状态 → 主题色徽章样式 */
const JOB_STATUS_STYLES: Record<string, string> = {
  SUCCESS: 'text-emerald-600',
  PENDING: 'text-blue-600',
  RUNNING: 'text-blue-600',
  NOT_FOUND: 'text-slate-500',
  FAILED: 'text-rose-600',
};

export default function DataWorkbenchImportTab({ showToast }: TabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [versions, setVersions] = useState<OntologyVersionOption[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<string>('');
  const [mode, setMode] = useState<ExtractMode>('INCREMENTAL');

  const [preview, setPreview] = useState<StructuredExtractReport | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [triggering, setTriggering] = useState(false);

  const [jobs, setJobs] = useState<StructuredExtractJob[]>([]);
  const [jobsLoading, setJobsLoading] = useState(false);
  const [expandedJob, setExpandedJob] = useState<{ rowKey: string; detail: string; status: string } | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // ── 数据加载 ─────────────────────────────────────────────────────────────

  const loadVersions = useCallback(async () => {
    setVersionsLoading(true);
    try {
      const data = await apiFetchData<OntologyVersionOption[]>('/api/v1/ecos/versions');
      const list = Array.isArray(data) ? data : [];
      setVersions(list.map(v => ({
        id: String(v.id ?? ''),
        ontologyId: String(v.ontologyId ?? ''),
        versionNo: String(v.versionNo ?? ''),
        status: String(v.status ?? ''),
      })).filter(v => v.id));
    } catch {
      setVersions([]);
    } finally {
      setVersionsLoading(false);
    }
  }, []);

  const loadJobs = useCallback(async () => {
    setJobsLoading(true);
    try {
      setJobs(await knowledgeApi.fetchStructuredJobs(1, 20));
    } catch {
      setJobs([]);
    } finally {
      setJobsLoading(false);
    }
  }, []);

  useEffect(() => { loadVersions(); loadJobs(); }, [loadVersions, loadJobs]);

  // ── Dry-run 预览 / 触发抽取 ───────────────────────────────────────────────

  const handleDryRun = useCallback(async () => {
    setPreviewLoading(true);
    setPreview(null);
    try {
      const report = await knowledgeApi.fetchTriggerStructuredExtract({
        ontologyId: selectedVersion || undefined,
        mode,
        dryRun: true,
      });
      setPreview(report);
    } catch (e) {
      showToast?.('error', t('knowledge.import.dryRunErr') + (e as Error).message);
    } finally {
      setPreviewLoading(false);
    }
  }, [selectedVersion, mode, t, showToast]);

  const handleTrigger = useCallback(async () => {
    setTriggering(true);
    try {
      await knowledgeApi.fetchTriggerStructuredExtract({
        ontologyId: selectedVersion || undefined,
        mode,
        dryRun: false,
      });
      showToast?.('success', t('knowledge.import.jobSubmitted'));
      loadJobs();
    } catch (e) {
      showToast?.('error', t('knowledge.import.triggerErr') + (e as Error).message);
    } finally {
      setTriggering(false);
    }
  }, [selectedVersion, mode, t, showToast, loadJobs]);

  // ── 作业详情展开 ─────────────────────────────────────────────────────────

  const handleRowClick = useCallback(async (job: StructuredExtractJob, rowKey: string) => {
    if (!job.jobId) return;
    if (expandedJob?.rowKey === rowKey && !detailLoading) {
      setExpandedJob(null);
      return;
    }
    setDetailLoading(true);
    try {
      const detail = await knowledgeApi.fetchStructuredJobDetail(job.jobId);
      setExpandedJob({ rowKey, detail: detail.detail ?? '', status: detail.status ?? '' });
    } catch (e) {
      setExpandedJob({ rowKey, detail: (e as Error).message, status: 'ERROR' });
    } finally {
      setDetailLoading(false);
    }
  }, [expandedJob, detailLoading]);

  const versionLabel = (v: OntologyVersionOption) =>
    `${v.ontologyId} @ ${v.versionNo} (${v.status || '—'})`;

  return (
    <div className="space-y-4">
      {/* 头部 */}
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <Download size={16} className="text-indigo-600" />
            {t('knowledge.import.title')}
          </h2>
          <p className={`text-[10px] ${styles.cardTextMuted}`}>
            {t('knowledge.import.subtitle')}
          </p>
        </div>
        <button
          onClick={() => { loadVersions(); loadJobs(); }}
          disabled={versionsLoading || jobsLoading}
          className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1.5 border cursor-pointer disabled:opacity-50 ${styles.cardBorder} ${styles.inputBg}`}
        >
          {(versionsLoading || jobsLoading) ? <Loader2 size={11} className="animate-spin" /> : <RefreshCw size={11} />}
          {t('knowledge.import.refresh')}
        </button>
      </div>

      {/* 本体版本 + 模式 + 操作 */}
      <div className={`border ${styles.cardBorder} rounded-xl p-4 space-y-4 ${styles.cardBg}`}>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* 本体版本 */}
          <div className="space-y-1">
            <label className={`text-[10px] font-extrabold ${styles.muted} uppercase`}>
              {t('knowledge.import.ontologyVersion')}
            </label>
            <select
              value={selectedVersion}
              onChange={e => setSelectedVersion(e.target.value)}
              disabled={versionsLoading}
              className={`w-full px-2.5 py-2 text-xs rounded-md border focus:outline-none ${styles.inputBg} ${styles.inputBorder}`}
            >
              <option value="">{t('knowledge.import.allOntologies')}</option>
              {versions.map(v => (
                <option key={v.id} value={v.ontologyId}>{versionLabel(v)}</option>
              ))}
            </select>
            {versionsLoading && (
              <p className="text-[9px] font-mono opacity-50">{t('knowledge.import.loadingVersions')}</p>
            )}
          </div>

          {/* 抽取模式 */}
          <div className="space-y-1">
            <label className={`text-[10px] font-extrabold ${styles.muted} uppercase`}>
              {t('knowledge.import.extractMode')}
            </label>
            <div className="flex rounded-md border overflow-hidden">
              {(['INCREMENTAL', 'FULL'] as ExtractMode[]).map(m => (
                <button
                  key={m}
                  onClick={() => setMode(m)}
                  className={`flex-1 px-3 py-2 text-[11px] font-bold transition ${
                    mode === m
                      ? `${styles.accentBg} text-white`
                      : `${styles.inputBg} ${styles.cardText} hover:opacity-80`
                  }`}
                >
                  {m === 'FULL' ? t('knowledge.import.modeFull') : t('knowledge.import.modeIncr')}
                </button>
              ))}
            </div>
          </div>

          {/* 操作按钮 */}
          <div className="flex flex-col justify-end gap-2">
            <button
              onClick={() => handleDryRun()}
              disabled={previewLoading || triggering}
              className={`w-full px-3 py-2 rounded-md text-xs font-bold border flex items-center justify-center gap-1.5 transition disabled:opacity-50 ${styles.cardBorder} ${styles.inputBg}`}
            >
              {previewLoading ? <Loader2 size={13} className="animate-spin" /> : <Eye size={13} />}
              {t('knowledge.import.dryRunPreview')}
            </button>
            <button
              onClick={() => handleTrigger()}
              disabled={previewLoading || triggering}
              className={`w-full px-3 py-2 rounded-md text-xs font-bold text-white flex items-center justify-center gap-1.5 transition disabled:opacity-50 ${styles.accentBg}`}
            >
              {triggering ? <Loader2 size={13} className="animate-spin" /> : <PlayCircle size={13} />}
              {t('knowledge.import.triggerExtract')}
            </button>
          </div>
        </div>

        {/* Dry-run 报告 */}
        {preview && (
          <div className={`border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <div className={`text-[10px] font-extrabold ${styles.muted} uppercase`}>
              {t('knowledge.import.dryRunReport')} (mode: {preview.mode}, {preview.durationMs}ms)
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {[
                { label: t('knowledge.import.ontologyCount'), value: preview.ontologyCount },
                { label: t('knowledge.import.entityCount'), value: preview.entityCount },
                { label: t('knowledge.import.nodesCreated'), value: preview.nodeCreated },
                { label: t('knowledge.import.nodesUpdated'), value: preview.nodeUpdated },
                { label: t('knowledge.import.edgesCreated'), value: preview.edgeCreated },
                { label: t('knowledge.import.skipped'), value: preview.nodeSkipped },
                { label: t('knowledge.import.invalidMappings'), value: preview.invalidMappings },
                { label: t('knowledge.import.watermark'), value: preview.nextWatermark ?? '—' },
              ].map((item, i) => (
                <div key={i} className={`p-2 rounded border ${styles.appBorder}`}>
                  <div className={`text-[9px] ${styles.muted}`}>{item.label}</div>
                  <div className={`text-sm font-bold ${styles.cardText} truncate`}>
                    {typeof item.value === 'number' ? item.value.toFixed(0) : String(item.value)}
                  </div>
                </div>
              ))}
            </div>
            {preview.issues.length > 0 && (
              <div>
                <span className={`text-[9px] font-bold ${styles.muted} uppercase`}>
                  {t('knowledge.import.issues')} ({preview.issues.length})
                </span>
                <ul className="space-y-0.5 max-h-24 overflow-y-auto">
                  {preview.issues.map((iss, i) => (
                    <li key={i} className="text-[10px] font-mono text-rose-500">
                      [{iss.code}] {iss.message}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 抽取作业列表 */}
      <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.cardBg}`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <span className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
            <ListOrdered size={13} className="text-indigo-500" />
            {t('knowledge.import.jobs')} ({jobs.length})
          </span>
          <span className={`text-[9px] font-mono ${styles.muted}`}>/extract/structured/jobs</span>
        </div>
        <div className="w-full overflow-x-auto">
          <table className="w-full text-[11px] border-collapse whitespace-nowrap">
            <thead>
              <tr className={`${styles.appBorder} border-b ${styles.cardBorder}`}>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider w-8"></th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">jobId</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.import.colMode')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.import.colStatus')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{t('knowledge.import.startedAt')}</th>
                <th className="p-3 text-right font-extrabold uppercase tracking-wider">{t('knowledge.import.duration')}</th>
              </tr>
            </thead>
            <tbody>
              {jobsLoading ? (
                <tr><td colSpan={6} className="p-8 text-center opacity-50">{t('knowledge.import.loading')}</td></tr>
              ) : jobs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="p-10 text-center opacity-50 text-xs">
                    {t('knowledge.import.emptyJobs')}
                  </td>
                </tr>
              ) : jobs.map((job, idx) => {
                const rowKey = job.jobId || `job-${idx}`;
                const expanded = expandedJob?.rowKey === rowKey;
                const statusCls = JOB_STATUS_STYLES[job.status] ?? 'text-slate-500';
                return (
                  <React.Fragment key={rowKey}>
                    <tr
                      onClick={() => job.jobId && handleRowClick(job, rowKey)}
                      className={`border-b ${styles.appBorder} ${job.jobId ? 'cursor-pointer hover:opacity-60' : ''}`}
                    >
                      <td className="p-3">
                        {job.jobId ? (
                          expanded
                            ? <ChevronDown size={12} className={styles.muted} />
                            : <ChevronRight size={12} className={styles.muted} />
                        ) : null}
                      </td>
                      <td className={`p-3 font-mono ${styles.cardTextMuted}`}>{job.jobId ?? '—'}</td>
                      <td className={`p-3 ${styles.cardText}`}>{job.mode}</td>
                      <td className="p-3">
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${styles.cardBorder} ${statusCls}`}>
                          {job.status}
                        </span>
                      </td>
                      <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>{job.startedAt ?? '—'}</td>
                      <td className={`p-3 text-right font-mono ${styles.cardTextMuted} text-[10px]`}>{job.durationMs ?? '—'}</td>
                    </tr>
                    {expanded && (
                      <tr className={`border-b ${styles.appBorder}`}>
                        <td colSpan={6} className={`p-3 ${styles.appBorder} text-[10px] font-mono`}>
                          <div className={`text-[9px] font-bold ${styles.muted} uppercase mb-1`}>
                            {t('knowledge.import.jobDetail')} ({expandedJob?.status})
                          </div>
                          {detailLoading && expandedJob?.rowKey === rowKey
                            ? <span className="opacity-50">{t('knowledge.import.loadingDetail')}</span>
                            : String(expandedJob?.detail ?? '')}
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
