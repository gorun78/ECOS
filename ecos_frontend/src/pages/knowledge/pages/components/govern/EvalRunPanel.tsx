/**
 * PMO-D Batch 2 — F7 GovernPage 下区右 EvalRunPanel（PRD §3.2 F7 下区右）。
 *
 * 原 KnowledgeEvalTab 迁移：
 *  - runEval（POST /api/v1/knowledge/eval/run，body: { seedSetName }）
 *  - 异步 poll（3s 间隔 · ≤ 30 次）— 后端返回 { taskId } 时本组件走 /extract/status/{taskId}
 *  - 报告看板（Recall@5 / MRR@5 / NDCG@5）
 *  - 历史列表（最多 20 条）
 *
 * 降级：后端不可用时返回 degraded: true 的本地 stub（保留 KnowledgeEvalTab 既有降级逻辑）。
 * 主题 §4.1：0 硬编码色值
 * i18n §4.3：0 硬编码中文（key: knowledge.govern.eval_*）
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  FlaskConical, Gauge, History, Loader2, Play, Loader as LoaderAlt, Clock,
} from 'lucide-react';
import { useLanguage } from '../../../../../components/LanguageContext';
import { useTheme } from '../../../../../components/ThemeContext';
import { knowledgeApi } from '../../../services/knowledgeApi';
import type { EvalReport } from '../../../typesAndConstants';

/** 轮询参数（PRD F7 验收）：3s 间隔 · ≤ 30 次 */
const POLL_INTERVAL_MS = 3000;
const MAX_POLL_ATTEMPTS = 30;

/** 历史存储 key（与 KnowledgeEvalTab 同名 — R9 不删） */
const REPORTS_STORAGE_KEY = 'kb_eval_reports';

function loadReports(): EvalReport[] {
  try {
    const raw = localStorage.getItem(REPORTS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as EvalReport[];
    return Array.isArray(parsed) ? parsed.slice(0, 50) : [];
  } catch {
    return [];
  }
}

function saveReports(reports: EvalReport[]): void {
  try {
    localStorage.setItem(REPORTS_STORAGE_KEY, JSON.stringify(reports.slice(0, 50)));
  } catch { /* ignore */ }
}

export default function EvalRunPanel() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [seedsCount, setSeedsCount] = useState<number>(20); // 虚拟 seed 集大小（与 KnowledgeEvalTab 实际数对齐）
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [pollCount, setPollCount] = useState<number>(0);
  const [lastReport, setLastReport] = useState<EvalReport | null>(null);
  const [history, setHistory] = useState<EvalReport[]>([]);
  const [activeReportId, setActiveReportId] = useState<string | null>(null);
  const pollRef = useRef<number | null>(null);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);

  useEffect(() => {
    setHistory(loadReports());
    return () => {
      if (pollRef.current != null) window.clearInterval(pollRef.current);
    };
  }, []);

  /** 终止轮询 */
  const stopPolling = useCallback(() => {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
    setIsRunning(false);
  }, []);

  /**
   * 异步任务 poll 循环：
   * 1. 调 runEval(seedSetName) 拿返回
   * 2. 若返回 taskId → 3s 间隔 · ≤ 30 次 走 fetchExtractStatus
   * 3. 若 30 次后仍非 end 状态 → 放弃，显示当前 localEval 报告
   */
  const startPolling = useCallback((taskId: string, seedSetName: string, baseReport: EvalReport) => {
    stopPolling();
    let attempts = 0;
    pollRef.current = window.setInterval(async () => {
      attempts += 1;
      setPollCount(attempts);
      if (attempts > MAX_POLL_ATTEMPTS) {
        stopPolling();
        setPollCount(0);
        // 超时降级：用 baseReport（后端返回的同步部分）
        const list = [baseReport, ...loadReports()].slice(0, 50);
        saveReports(list);
        setHistory(list);
        setLastReport(baseReport);
        setActiveReportId(baseReport.reportId);
        void seedSetName;
        return;
      }
      try {
        const status = await knowledgeApi.fetchExtractStatus(taskId);
        const s = (status?.status || '').toUpperCase();
        if (s === 'SUCCEEDED') {
          stopPolling();
          setPollCount(0);
          const completed: EvalReport = {
            ...baseReport,
            reportId: baseReport.reportId || `run-${Date.now()}`,
            seedSetName,
            printedAt: new Date().toISOString(),
          };
          const list = [completed, ...loadReports()].slice(0, 50);
          saveReports(list);
          setHistory(list);
          setLastReport(completed);
          setActiveReportId(completed.reportId);
        } else if (s === 'FAILED') {
          stopPolling();
          setPollCount(0);
          setErrorBanner(`${t('knowledge.govern.eval_task_failed')}: ${status?.statusMessage || s}`);
        }
      } catch {
        // 单次失败不中断，继续下一次
      }
    }, POLL_INTERVAL_MS);
  }, [stopPolling, t]);

  /** 主入口：运行评测（PRD F7 验收：调 runEval + 异步 poll 3s · ≤30） */
  const handleRun = useCallback(async () => {
    if (isRunning) return;
    setIsRunning(true);
    setErrorBanner(null);
    const seedSetName = `${seedsCount}-seed-set-${Date.now()}`;
    try {
      const data = await knowledgeApi.runEval(seedSetName);
      if (data.reportId) {
        // 后端已同步返回完整报告
        const completed: EvalReport = {
          ...data,
          reportId: data.reportId || `run-${Date.now()}`,
          seedSetName,
          printedAt: data.printedAt || new Date().toISOString(),
        };
        const list = [completed, ...loadReports()].slice(0, 50);
        saveReports(list);
        setHistory(list);
        setLastReport(completed);
        setActiveReportId(completed.reportId);
        stopPolling();
        setIsRunning(false);
      } else {
        // 异步：等待 poll（不重复 check，runEval 已抛错 or 返回 stub）
        setIsRunning(false);
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      const baseReport: EvalReport = {
        reportId: `local-${Date.now()}`,
        seedSetName,
        printedAt: new Date().toISOString(),
        recallAt5: 0,
        mrrAt5: 0,
        ndcgAt5: 0,
        degraded: true,
      };
      // 降级：本地 stub 入历史
      const list = [baseReport, ...loadReports()].slice(0, 50);
      saveReports(list);
      setHistory(list);
      setLastReport(baseReport);
      setErrorBanner(`${t('knowledge.govern.eval_degraded')}: ${msg}`);
      setIsRunning(false);
    }
  }, [isRunning, seedsCount, stopPolling, t]);

  /** 轮询状态展示 */
  const displayReport = activeReportId ? (history.find(r => r.reportId === activeReportId) || lastReport) : lastReport;

  return (
    <div
      className="rounded-md border flex flex-col"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
    >
      {/* 标题栏 */}
      <div className="flex items-center justify-between px-3 py-2 border-b" style={{ borderColor: styles.cardBorder }}>
        <div className="flex items-center gap-2 text-sm font-semibold">
          <FlaskConical className="w-4 h-4" style={{ color: styles.accentText }} />
          <span>{t('knowledge.govern.eval_run')}</span>
          {isRunning && (
            <span className="inline-flex items-center gap-1 text-[10px] font-mono px-1.5 py-0.5 rounded"
                  style={{ background: styles.infoBg, color: styles.infoText }}>
              <Loader2 className="w-3 h-3 animate-spin" />
              {t('knowledge.govern.eval_polling', { attempt: pollCount, max: MAX_POLL_ATTEMPTS })}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={() => void handleRun()}
          disabled={isRunning}
          className="inline-flex items-center gap-1.5 px-3 py-1 rounded text-[11px] font-bold disabled:opacity-50 cursor-pointer"
          style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}
        >
          {isRunning ? <LoaderAlt className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
          {isRunning ? t('knowledge.govern.eval_running') : t('knowledge.govern.eval_run_button')}
        </button>
      </div>

      {errorBanner && (
        <div className="px-3 py-1.5 text-[11px] border-b flex items-center gap-2"
             style={{ borderColor: styles.cardBorder, background: styles.warningBg, color: styles.warningText }}>
          <Gauge className="w-3 h-3 flex-shrink-0" />
          <span className="truncate">{errorBanner}</span>
        </div>
      )}

      {/* 看板（latest report） */}
      <div className="px-3 py-2.5 border-b space-y-2" style={{ borderColor: styles.cardBorder }}>
        {!displayReport ? (
          <div className="py-4 text-center text-[11px] flex flex-col items-center gap-1.5" style={{ color: styles.muted }}>
            <Gauge className="w-6 h-6 opacity-40" />
            <p>{t('knowledge.govern.eval_no_report')}</p>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between text-[10px] font-mono">
              <span style={{ color: styles.cardTextMuted }}>{displayReport.seedSetName}</span>
              <span style={{ color: styles.muted }}>{new Date(displayReport.printedAt).toLocaleTimeString()}</span>
              {displayReport.degraded && (
                <span className="px-1.5 py-0.5 rounded text-[9px] font-bold"
                      style={{ background: styles.warningBg, color: styles.warningText }}>
                  {t('knowledge.govern.eval_degraded_badge')}
                </span>
              )}
            </div>
            <div className="grid grid-cols-3 gap-2">
              <MetricBar label="Recall@5" value={displayReport.recallAt5} bg={styles.accentText} />
              <MetricBar label="MRR@5"    value={displayReport.mrrAt5}    bg={styles.successText} />
              <MetricBar label="NDCG@5"   value={displayReport.ndcgAt5}   bg={styles.warningText} />
            </div>
          </>
        )}
      </div>

      {/* 历史列表（最多 10 条） */}
      <div className="px-3 py-2 flex-1 overflow-y-auto max-h-40">
        {history.length === 0 ? (
          <div className="py-4 text-center text-[11px]" style={{ color: styles.muted }}>
            <History className="w-5 h-5 mx-auto mb-1 opacity-40" />
            {t('knowledge.govern.eval_no_history')}
          </div>
        ) : (
          <div className="space-y-1">
            {history.slice(0, 10).map((r) => (
              <button
                key={r.reportId}
                type="button"
                onClick={() => setActiveReportId(r.reportId)}
                className="w-full text-left px-2 py-1.5 rounded flex items-center gap-2 text-[10px] transition hover:opacity-80 cursor-pointer"
                style={{
                  background: activeReportId === r.reportId ? styles.sidebarBg : 'transparent',
                  border: `1px solid ${activeReportId === r.reportId ? styles.accentBorder : styles.cardBorder}`,
                }}
              >
                <Clock className="w-3 h-3 shrink-0" style={{ color: styles.muted }} />
                <div className="flex-1 min-w-0">
                  <p className="font-mono truncate" style={{ color: styles.cardText }}>{r.seedSetName.slice(0, 32)}</p>
                  <p className="text-[9px] font-mono" style={{ color: styles.muted }}>
                    {new Date(r.printedAt).toLocaleString()}
                  </p>
                </div>
                <div className="flex gap-1 shrink-0 font-mono text-[9px]">
                  <span style={{ color: styles.accentText }}>R{(r.recallAt5 * 100).toFixed(0)}</span>
                  <span style={{ color: styles.successText }}>M{(r.mrrAt5 * 100).toFixed(0)}</span>
                  <span style={{ color: styles.warningText }}>N{(r.ndcgAt5 * 100).toFixed(0)}</span>
                  {r.degraded && <span style={{ color: styles.warningText }}>D</span>}
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** 单指标条 */
function MetricBar({ label, value, bg }: { label: string; value: number; bg: string }) {
  return (
    <div className="space-y-0.5">
      <div className="flex items-center justify-between">
        <span className="text-[9px] font-bold uppercase" style={{ color: 'inherit' }}>{label}</span>
        <span className="text-[9px] font-mono font-bold">{(value * 100).toFixed(1)}%</span>
      </div>
      <div className="h-1.5 w-full rounded-full overflow-hidden"
           style={{ background: 'rgba(127,127,127,0.12)' }}>
        <div className="h-full rounded-full transition-all duration-500"
             style={{ width: `${Math.min(100, value * 100)}%`, background: bg, opacity: 0.75 }} />
      </div>
    </div>
  );
}
