/**
 * Wave 3 C2 — 抽取监控 sub-component（T10）
 *
 * 父组件在 trigger 后传 `active = { taskId, dryRun }`；组件内部 2s 轮询
 * `fetchExtractJobStatus(taskId)` 与定时抽取最新任务列表，
 * 终态（SUCCEEDED / FAILED）后停止 interval；cleanup 在 unmount 与 active 变更时执行。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Activity, Download, FileText, Info, RefreshCw } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import {
  fetchExtractJobStatus,
  fetchExtractLogs,
  exportExtractLog,
  fetchExtractJobs,
  type ExtractJobInfo,
  type ExtractLogEntry,
} from '../../services/knowledgeApi';

/** 轮询间隔（毫秒）— 同时拉 status + logs + 最近任务，单一 interval 共享 */
const POLL_INTERVAL_MS = 2000;
/** 任务终态判定（命中后停止 interval） */
const TERMINAL_STATES = new Set(['SUCCEEDED', 'FAILED']);

interface MonitorPanelProps {
  /** 父组件触发后注入；null = 未触发，仅显示空态与最近任务 */
  active?: { taskId: string; dryRun: boolean } | null;
  /** 触发来源标识（MANUAL/SCHEDULED） */
  trigger?: ExtractJobInfo['trigger'];
}

/** 把耗时 ms 渲染为 1s.0s / mm:ss 形式 */
function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '—';
  if (ms < 1000) return `${ms}ms`;
  const s = Math.floor(ms / 1000);
  const r = Math.round((ms % 1000) / 100);
  if (s < 60) return `${s}s${r > 0 ? `.${r}s` : ''}`;
  const m = Math.floor(s / 60);
  const s2 = s % 60;
  return `${m}m${s2 > 0 ? `${s2}s` : ''}`;
}

/** 状态徽章颜色（主题感知） */
function statusBadgeClass(
  status: string | undefined,
  styles: ReturnType<typeof useTheme>['styles'],
): string {
  switch (status) {
    case 'SUCCEEDED': return `${styles.successBg} ${styles.successText} ${styles.successBorder}`;
    case 'FAILED': return `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`;
    case 'RUNNING': return `${styles.badgeBg} ${styles.accentText} ${styles.accentBorder}`;
    case 'PENDING': return `${styles.badgeBg} ${styles.muted} ${styles.cardBorder}`;
    default: return `${styles.badgeBg} ${styles.muted} ${styles.cardBorder}`;
  }
}

function StatusBadge({ status, styles }: { status: string; styles: ReturnType<typeof useTheme>['styles'] }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-mono font-bold border ${statusBadgeClass(status, styles)}`}>
      {status}
    </span>
  );
}

export default function MonitorPanel({ active, trigger = 'MANUAL' }: MonitorPanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [job, setJob] = useState<ExtractJobInfo | null>(null);
  const [logs, setLogs] = useState<ExtractLogEntry[]>([]);
  const [recentJobs, setRecentJobs] = useState<ExtractJobInfo[]>([]);
  const [cancelRef, setCancelRef] = useState<number | null>(null);
  const intervalIdRef = useRef<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportFailed, setExportFailed] = useState(false);

  /** 终止轮询（手动/失败/终态调用） */
  const stopPolling = useCallback(() => {
    if (intervalIdRef.current !== null) {
      clearInterval(intervalIdRef.current);
      intervalIdRef.current = null;
    }
    setCancelRef(null);
  }, []);

  /** 单轮拉取：status + logs（仅在 active 时）+ 最近任务（始终） */
  const tick = useCallback(async () => {
    // 最近任务始终刷新（即使 active=null 也展示）
    try {
      const jobs = await fetchExtractJobs(10);
      setRecentJobs(jobs);
    } catch {
      // 静默
    }
    if (active === null || active === undefined) return;
    // 主任务状态
    try {
      const st = await fetchExtractJobStatus(active.taskId);
      if (st) setJob(st);
    } catch {
      // 静默
    }
    // 日志（task 启动后才会有，容错空）：触发即用 active.taskId 的 jobId（后端 jobId is Number 字符串），拿到 Number 后再拉
    try {
      const n = Number(active.taskId);
      if (Number.isFinite(n) && n > 0) {
        const entries = await fetchExtractLogs(n);
        if (Array.isArray(entries) && entries.length > 0) setLogs(entries);
      }
    } catch {
      // 静默
    }
  }, [active]);

  // active 变化 / 挂载 → 启动 polling；active 移除或终态 → 清理
  useEffect(() => {
    if (active === null || active === undefined) {
      stopPolling();
      // active 清除时：保留 lastJob 供「最近任务」展示，但主区域回空态
      setJob(null);
      setLogs([]);
      return undefined;
    }
    // 若已有终态 job（例如快速切换）直接停
    if (job && TERMINAL_STATES.has(job.status)) {
      stopPolling();
      return undefined;
    }
    stopPolling();
    intervalIdRef.current = window.setInterval(() => { void tick(); }, POLL_INTERVAL_MS);
    setCancelRef(intervalIdRef.current);
    void tick(); // 立即拉一次避免 2s 冷启动
    return () => {
      // cleanup：interval + active 变化时
      stopPolling();
    };
    // 依赖 active.taskId；status 终态由内部停 polling 并通过 stopPolling set null
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active?.taskId, active === null, active === undefined]);

  // job 终态 → 停止 interval（effect 内不可重置闭包，直接调外部 ref）
  useEffect(() => {
    if (job && TERMINAL_STATES.has(job.status) && intervalIdRef.current !== null) {
      stopPolling();
      // 终态后还要拉一次日志收尾
      void (async () => {
        const jid = (job.jobId as string) || '';
        const n = Number(jid);
        if (!n || Number.isNaN(n) || n <= 0) return;
        try { const entries = await fetchExtractLogs(n); setLogs(entries); } catch { /* 静默 */ }
      })();
    }
  }, [job, stopPolling]);

  /** 手动触发"最近任务"列表立即刷新 */
  const refreshRecent = useCallback(() => {
    void fetchExtractJobs(10).then(setRecentJobs).catch(() => { /* 静默 */ });
  }, []);

  /** 导出 Blob → a.download 触发，revoke 在 setTimeout 30s 后（避免立刻 revoke） */
  const exportLog = useCallback(async () => {
    if (!job) return;
    const jid = Number(job.jobId);
    if (!jid || Number.isNaN(jid)) return;
    setExporting(true);
    setExportFailed(false);
    try {
      const blob = await exportExtractLog(String(jid));
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = t('knowledge.datasync.export.log_suffix', { id: String(jid) });
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
    } catch (e) {
      console.warn('export extract log failed', e);
      setExportFailed(true);
    } finally {
      setExporting(false);
    }
  }, [job, t]);

  /** 4 KPI 卡（progress / rowsRead / rowsWritten / mismatches） */
  const kpiValues = [
    { label: t('knowledge.datasync.kpi.rows_read'), value: job?.rowsRead != null ? String(job.rowsRead) : '—' },
    { label: t('knowledge.datasync.kpi.rows_written'), value: job?.rowsWritten != null ? String(job.rowsWritten) : '—' },
    { label: t('knowledge.datasync.kpi.mismatches'), value: job?.mismatches != null ? String(job.mismatches) : '—' },
    { label: t('knowledge.datasync.kpi.progress'), value: `${job?.progress ?? 0}%` },
  ];

  const isDryRun = active?.dryRun === true;
  const progressPct = Math.max(0, Math.min(100, job?.progress ?? 0));

  return (
    <div className={`border rounded-xl p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}>
      <div className="flex items-center justify-between flex-wrap gap-2 border-b pb-2" style={{ borderColor: styles.cardBorder }}>
        <span className={`text-[10px] font-extrabold uppercase tracking-wider ${styles.muted} font-mono flex items-center gap-1`}>
          <Activity size={12} />
          {t('knowledge.datasync.section.monitor')}
        </span>
        <div className="flex items-center gap-1.5">
          {job && (
            <button
              type="button"
              onClick={refreshRecent}
              className={`p-1 rounded-md cursor-pointer ${styles.sidebarHoverBg} ${styles.muted}`}
              title={t('knowledge.common.refresh')}
            >
              <RefreshCw size={11} />
            </button>
          )}
          <button
            type="button"
            disabled={!job || exporting}
            onClick={() => void exportLog()}
            className={`inline-flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-mono font-bold border cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition ${exporting ? 'opacity-60' : 'hover:opacity-80'}`}
            style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.cardText }}
          >
            {exporting ? (
              <RefreshCw size={10} className="animate-spin" />
            ) : (
              <Download size={10} />
            )}
            {exporting ? t('knowledge.datasync.monitor.exporting') : t('knowledge.job_monitor.export')}
          </button>
        </div>
      </div>

      {/* 主区：未触发时空态（含灰态 KPI + 0% 进度条）；触发后 KPI + 进度条 + 状态 badge */}
      {active === null || active === undefined || !job ? (
        <div className="space-y-3">
          <div className={`p-3 rounded-lg text-xs text-center ${styles.muted} ${styles.badgeBg}`}>
            {t('knowledge.datasync.monitor.no_task')}
          </div>
          {/* 灰态 KPI（值=0，提示面板结构） */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
            {kpiValues.map(k => (
              <div key={k.label} className={`rounded-lg px-3 py-2 border ${styles.badgeBg} opacity-50`} style={{ borderColor: styles.cardBorder }}>
                <p className="text-[10px] font-mono uppercase tracking-wider" style={{ color: styles.muted }}>{k.label}</p>
                <p className="text-sm font-bold font-mono" style={{ color: styles.muted }}>{k.value}</p>
              </div>
            ))}
          </div>
          {/* 0% 进度条（灰态） */}
          <div className={`h-2 rounded-md overflow-hidden ${styles.inputBg} border opacity-50`} style={{ borderColor: styles.cardBorder }}>
            <div className={`h-full ${styles.accentBg}`} style={{ width: '0%' }} />
          </div>
          {/* 空日志占位 */}
          <div className={`rounded-lg p-3 ${styles.badgeBg}`} style={{ borderColor: styles.cardBorder }}>
            <p className="text-[10px] font-mono uppercase tracking-wider flex items-center gap-1.5" style={{ color: styles.muted }}>
              <FileText size={10} />
              {t('knowledge.datasync.monitor.select_text')}
            </p>
            <pre className="text-[10px] font-mono mt-1.5" style={{ color: styles.muted }}>{t('knowledge.datasync.monitor.no_logs')}</pre>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {/* 状态 badge + mode + dryRun 标记 */}
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-2">
              <StatusBadge status={job.status} styles={styles} />
              <span className={`text-[10px] font-mono ${styles.muted}`} style={{ color: styles.cardTextMuted }}>
                {job.trigger === 'SCHEDULED' ? t('knowledge.tick_mode.scheduled') : t('knowledge.tick_mode.now')}
                {job.mode === 'FULL' ? t('knowledge.sync_mode.full') : t('knowledge.sync_mode.incremental')}
                {isDryRun ? ` · ${t('knowledge.datasync.monitor.dry_run_badge')}` : ''}
              </span>
            </div>
            <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
              {t('knowledge.job_monitor.duration')}: {formatDuration(job.durationMs)}
            </span>
          </div>

          {/* 进度条 */}
          <div className={`h-2 rounded-md overflow-hidden ${styles.inputBg} border`} style={{ borderColor: styles.cardBorder }}>
            <div
              className={`h-full transition-all duration-300 ${job.status === 'FAILED' ? styles.dangerBg : styles.accentBg}`}
              style={{ width: `${progressPct}%` }}
            />
          </div>

          {/* 4 KPI 卡（grid） */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
            {kpiValues.map(k => (
              <div key={k.label} className={`rounded-lg px-3 py-2 border ${styles.badgeBg}`} style={{ borderColor: styles.cardBorder }}>
                <p className="text-[10px] font-mono uppercase tracking-wider" style={{ color: styles.muted }}>{k.label}</p>
                <p className="text-sm font-bold font-mono" style={{ color: styles.cardText }}>{k.value}</p>
              </div>
            ))}
          </div>

          {/* 失败提示：error + advice */}
          {job.status === 'FAILED' && (job.error || job.advice) && (
            <div className={`rounded-lg p-3 border ${styles.dangerBg}`} style={{ borderColor: styles.dangerBorder }}>
              {job.error && (
                <p className="text-xs" style={{ color: styles.dangerText }}>
                  <span className="font-bold">{t('knowledge.job_monitor.error_reason')}:</span> {job.error}
                </p>
              )}
              {job.advice && (
                <p className="text-[11px] mt-1" style={{ color: styles.muted }}>
                  <span className="font-bold">{t('knowledge.job_monitor.error_advice')}:</span> {job.advice}
                </p>
              )}
            </div>
          )}

          {/* statusMessage hint */}
          {job.statusMessage && (
            <div className={`flex items-start gap-1.5 p-2 rounded-md border ${styles.badgeBg}`} style={{ borderColor: styles.cardBorder }}>
              <Info size={11} className="mt-0.5 shrink-0" style={{ color: styles.accentText }} />
              <p className="text-[11px]" style={{ color: styles.cardTextMuted }}>{job.statusMessage}</p>
            </div>
          )}
        </div>
      )}

      {/* 日志区 */}
      {active !== null && active !== undefined && (
        <div className={`border rounded-lg p-3 space-y-2 ${styles.badgeBg}`} style={{ borderColor: styles.cardBorder }}>
          <p className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted} flex items-center gap-1.5`}>
            <FileText size={10} />
            {t('knowledge.datasync.monitor.select_text')}
            {exportFailed && <span style={{ color: styles.dangerText }}> · {t('knowledge.datasync.monitor.export_failed')}</span>}
          </p>
          <pre
            className="text-[10px] font-mono leading-relaxed whitespace-pre-wrap break-words max-h-56 overflow-y-auto p-2 rounded"
            style={{ background: styles.inputBg, color: styles.cardText }}
          >
            {logs.length === 0
              ? (job?.status === 'RUNNING' ? '…' : t('knowledge.datasync.monitor.no_logs'))
              : [...logs].sort((a, b) => (a.ts < b.ts ? -1 : 1)).map(l => `${l.ts}  [${l.level}]  ${l.message}`).join('\n')}
          </pre>
        </div>
      )}

      {/* 最近任务（最近 10 条） */}
      <div>
        <p className={`text-[10px] font-extrabold uppercase tracking-wider ${styles.muted} font-mono mb-2`}>
          {t('knowledge.datasync.recent_jobs_title')}
        </p>
        {recentJobs.length === 0 ? (
          <p className="text-xs py-3 text-center" style={{ color: styles.muted }}>{t('knowledge.datasync.monitor.empty')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[11px]">
              <thead>
                <tr className="text-left font-mono text-[9px] uppercase tracking-wider" style={{ color: styles.muted }}>
                  <th className="py-1 pr-2">jobId</th>
                  <th className="py-1 pr-2">status</th>
                  <th className="py-1 pr-2">mode</th>
                  <th className="py-1 pr-2">trigger</th>
                  <th className="py-1 pr-2">progress</th>
                  <th className="py-1 pr-2">{t('knowledge.datasync.kpi.rows_written')}</th>
                  <th className="py-1 pr-2">{t('knowledge.datasync.kpi.mismatches')}</th>
                  <th className="py-1">{t('knowledge.job_monitor.duration')}</th>
                </tr>
              </thead>
              <tbody>
                {recentJobs.slice(0, 10).map(j => (
                  <tr key={j.jobId} className="border-t" style={{ borderColor: styles.cardBorder }}>
                    <td className="py-1 pr-2 font-mono" style={{ color: styles.cardText }}>{j.jobId}</td>
                    <td className="py-1 pr-2"><StatusBadge status={j.status} styles={styles} /></td>
                    <td className="py-1 pr-2 font-mono" style={{ color: styles.cardText }}>{j.mode}</td>
                    <td className="py-1 pr-2 font-mono" style={{ color: styles.cardText }}>{j.trigger}</td>
                    <td className="py-1 pr-2 font-mono" style={{ color: styles.cardText }}>{`${j.progress ?? 0}%`}</td>
                    <td className="py-1 pr-2 font-mono" style={{ color: styles.cardText }}>{j.rowsWritten ?? '—'}</td>
                    <td className="py-1 pr-2 font-mono" style={{ color: styles.cardText }}>{j.mismatches ?? '—'}</td>
                    <td className="py-1 font-mono" style={{ color: styles.cardText }}>{formatDuration(j.durationMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
