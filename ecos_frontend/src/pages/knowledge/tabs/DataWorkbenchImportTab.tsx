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
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;

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
      showToast?.('error', tl('Dry-run 预览失败: ', 'Dry-run failed: ') + (e as Error).message);
    } finally {
      setPreviewLoading(false);
    }
  }, [selectedVersion, mode, tl, showToast]);

  const handleTrigger = useCallback(async () => {
    setTriggering(true);
    try {
      await knowledgeApi.fetchTriggerStructuredExtract({
        ontologyId: selectedVersion || undefined,
        mode,
        dryRun: false,
      });
      showToast?.('success', tl('抽取任务已提交', 'Extract job submitted'));
      loadJobs();
    } catch (e) {
      showToast?.('error', tl('触发抽取失败: ', 'Trigger failed: ') + (e as Error).message);
    } finally {
      setTriggering(false);
    }
  }, [selectedVersion, mode, tl, showToast, loadJobs]);

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
            {tl('体数据来源与抽取任务', 'Ontology Source & Extract Jobs')}
          </h2>
          <p className={`text-[10px] ${styles.cardTextMuted}`}>
            {tl('选择本体版本 → Dry-run 预览 → 触发结构化抽取', 'Pick ontology version → dry-run preview → trigger structured extract')}
          </p>
        </div>
        <button
          onClick={() => { loadVersions(); loadJobs(); }}
          disabled={versionsLoading || jobsLoading}
          className={`px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all flex items-center gap-1.5 border cursor-pointer disabled:opacity-50 ${styles.cardBorder} ${styles.inputBg}`}
        >
          {(versionsLoading || jobsLoading) ? <Loader2 size={11} className="animate-spin" /> : <RefreshCw size={11} />}
          {tl('刷新', 'Refresh')}
        </button>
      </div>

      {/* 本体版本 + 模式 + 操作 */}
      <div className={`border ${styles.cardBorder} rounded-xl p-4 space-y-4 ${styles.cardBg}`}>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* 本体版本 */}
          <div className="space-y-1">
            <label className={`text-[10px] font-extrabold ${styles.muted} uppercase`}>
              {tl('本体版本', 'Ontology Version')}
            </label>
            <select
              value={selectedVersion}
              onChange={e => setSelectedVersion(e.target.value)}
              disabled={versionsLoading}
              className={`w-full px-2.5 py-2 text-xs rounded-md border focus:outline-none ${styles.inputBg} ${styles.inputBorder}`}
            >
              <option value="">{tl('全部本体（默认）', 'All ontologies (default)')}</option>
              {versions.map(v => (
                <option key={v.id} value={v.ontologyId}>{versionLabel(v)}</option>
              ))}
            </select>
            {versionsLoading && (
              <p className="text-[9px] font-mono opacity-50">{tl('加载版本中...', 'Loading versions...')}</p>
            )}
          </div>

          {/* 抽取模式 */}
          <div className="space-y-1">
            <label className={`text-[10px] font-extrabold ${styles.muted} uppercase`}>
              {tl('抽取模式', 'Extract Mode')}
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
                  {m === 'FULL' ? tl('全量 (FULL)', 'Full') : tl('增量 (INCREMENTAL)', 'Incremental')}
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
              {tl('Dry-run 预览', 'Dry-run Preview')}
            </button>
            <button
              onClick={() => handleTrigger()}
              disabled={previewLoading || triggering}
              className={`w-full px-3 py-2 rounded-md text-xs font-bold text-white flex items-center justify-center gap-1.5 transition disabled:opacity-50 ${styles.accentBg}`}
            >
              {triggering ? <Loader2 size={13} className="animate-spin" /> : <PlayCircle size={13} />}
              {tl('触发抽取', 'Trigger Extract')}
            </button>
          </div>
        </div>

        {/* Dry-run 报告 */}
        {preview && (
          <div className={`border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <div className={`text-[10px] font-extrabold ${styles.muted} uppercase`}>
              {tl('Dry-run 预览报告', 'Dry-run Report')} (mode: {preview.mode}, {preview.durationMs}ms)
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {[
                { label: tl('本体数', 'Ontologies'), value: preview.ontologyCount },
                { label: tl('实体数', 'Entities'), value: preview.entityCount },
                { label: tl('新建节点', 'Nodes Created'), value: preview.nodeCreated },
                { label: tl('更新节点', 'Nodes Updated'), value: preview.nodeUpdated },
                { label: tl('新建边', 'Edges Created'), value: preview.edgeCreated },
                { label: tl('跳过', 'Skipped'), value: preview.nodeSkipped },
                { label: tl('无效映射', 'Invalid Mappings'), value: preview.invalidMappings },
                { label: tl('水位线', 'Watermark'), value: preview.nextWatermark ?? '—' },
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
                  {tl('问题明细', 'Issues')} ({preview.issues.length})
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
            {tl('抽取作业', 'Extract Jobs')} ({jobs.length})
          </span>
          <span className={`text-[9px] font-mono ${styles.muted}`}>/extract/structured/jobs</span>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-[11px] border-collapse">
            <thead>
              <tr className={`${styles.appBorder} border-b ${styles.cardBorder}`}>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider w-8"></th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">jobId</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('模式', 'Mode')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('状态', 'Status')}</th>
                <th className="p-3 text-left font-extrabold uppercase tracking-wider">{tl('开始时间', 'Started At')}</th>
                <th className="p-3 text-right font-extrabold uppercase tracking-wider">{tl('耗时 (ms)', 'Duration')}</th>
              </tr>
            </thead>
            <tbody>
              {jobsLoading ? (
                <tr><td colSpan={6} className="p-8 text-center opacity-50">{tl('加载中...', 'Loading...')}</td></tr>
              ) : jobs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="p-10 text-center opacity-50 text-xs">
                    {tl('暂无抽取作业。点击「Dry-run 预览」或「触发抽取」发起。', 'No extract jobs yet. Click Dry-run or Trigger to start.')}
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
                            {tl('作业详情', 'Job Detail')} ({expandedJob?.status})
                          </div>
                          {detailLoading && expandedJob?.rowKey === rowKey
                            ? <span className="opacity-50">{tl('加载详情中...', 'Loading detail...')}</span>
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
