/**
 * PipelineExecutionMonitor — 执行监控组件
 * 节点实时状态轮询 (2秒间隔) + Toast 通知 + 步骤详情弹出框
 * 拆分后：类型/常量在 PipelineExecutionMonitorTypes，步骤详情弹窗在 PipelineExecutionMonitorStepDetail
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Play, CheckCircle, XCircle, AlertTriangle,
  Loader2, ChevronRight, X, AlertCircle,
} from 'lucide-react';
// #145/B6-15：轮询腿的族根改取 `api/dataClient` 单源常量（经 `src/api.ts` 门面），
// 本文件不再持 URL 体 ⇒ 原 `API_BASE` 导入腿随之撤除。
import { apiFetchData, ENGINE_DATA_PIPELINE_RUNS_BASE } from '../../../api';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  getStatusColor, STATUS_LABEL_KEYS, StatusIcon,
  type StepStatus, type StepRunInfo, type RunInfo,
  type PipelineExecutionMonitorProps,
} from './PipelineExecutionMonitorTypes';
import PipelineExecutionMonitorStepDetail from './PipelineExecutionMonitorStepDetail';

// ─── Component ────────────────────────────────────────

const PipelineExecutionMonitor: React.FC<PipelineExecutionMonitorProps> = ({
  runId: externalRunId,
  taskId,
  onRunComplete,
  onClose,
  className = '',
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [runInfo, setRunInfo] = useState<RunInfo | null>(null);
  const [selectedStep, setSelectedStep] = useState<StepRunInfo | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const prevStatusRef = useRef<StepStatus | null>(null);
  // B-15：轮询连续失败可见化，不再静默冻结在旧数据上
  const pollErrCountRef = useRef(0);
  const [pollDegraded, setPollDegraded] = useState(false);

  /** 状态标签：枚举内走双语键，未知枚举原样呈现（无假翻译） */
  const labelOf = useCallback((s: string) => (
    s in STATUS_LABEL_KEYS ? t(STATUS_LABEL_KEYS[s as StepStatus]) : s
  ), [t]);

  // Toast helper
  const showToast = useCallback((type: 'success' | 'error' | 'info', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 4000);
  }, []);

  // Poll execution status
  const fetchRunStatus = useCallback(async (rid: string) => {
    try {
      const resp = await apiFetchData<RunInfo & { data?: RunInfo }>(
        `${ENGINE_DATA_PIPELINE_RUNS_BASE}/${encodeURIComponent(rid)}`
      );
      const data = resp?.data || resp;
      setRunInfo(data);
      pollErrCountRef.current = 0;
      setPollDegraded(false);

      // Check for completion
      if (data?.status === 'succeeded') {
        if (prevStatusRef.current !== 'succeeded') {
          showToast('success', t('dw.execMonitor.completedToast').replace('{elapsed}', data.elapsedMs ? t('dw.execMonitor.completedElapsed').replace('{sec}', (data.elapsedMs / 1000).toFixed(1)) : ''));
          onRunComplete?.(data);
        }
        // Stop polling on completion
        if (pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      } else if (data?.status === 'failed') {
        if (prevStatusRef.current !== 'failed') {
          showToast('error', t('dw.execMonitor.failedToast').replace('{msg}', data.errorMsg || t('dw.execMonitor.unknownError')));
          onRunComplete?.(data);
        }
        if (pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      } else if (data?.status === 'cancelled') {
        if (prevStatusRef.current !== 'cancelled') {
          showToast('info', t('dw.execMonitor.cancelledToast'));
          onRunComplete?.(data);
        }
        if (pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      }

      prevStatusRef.current = data?.status || null;
    } catch {
      // 连续 3 次轮询失败 → 头部挂「连接中断」降级标，不再无感冻结
      pollErrCountRef.current += 1;
      if (pollErrCountRef.current >= 3) setPollDegraded(true);
    }
  }, [onRunComplete, showToast, t]);

  // Start/stop polling
  useEffect(() => {
    if (externalRunId) {
      fetchRunStatus(externalRunId);
      pollingRef.current = setInterval(() => fetchRunStatus(externalRunId), 2000);
    }
    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [externalRunId, fetchRunStatus]);

  if (!externalRunId && !runInfo) {
    return null;
  }

  return (
    <>
      {/* Monitor Panel */}
      <div className={`flex flex-col ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-sm overflow-hidden ${className}`}>
        {/* Header */}
        <div className={`flex items-center justify-between px-3 py-2.5 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <div className="flex items-center gap-2">
            <Play size={14} className={`${styles.accentText}`} />
            <span className={`text-xs font-bold ${styles.cardText} uppercase tracking-wider`}>
              {t('dw.execMonitor.title')}
            </span>
            {runInfo && (() => { const sc = getStatusColor(runInfo.status); return (
              <span
                className={`inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-full ${sc.bg} ${sc.text}`}
              >
                {labelOf(runInfo.status)}
              </span>
            ); })()}
            {pollDegraded && (
              <span className={`inline-flex items-center gap-1 text-[10px] ${styles.dangerText}`}>
                <AlertTriangle size={11} /> {t('dw.execMonitor.pollDegraded')}
              </span>
            )}
          </div>
          {onClose && (
            <button type="button" onClick={onClose} className={`p-0.5 rounded hover:${styles.sidebarBg} ${styles.cardTextMuted} transition-colors`}>
              <X size={14} />
            </button>
          )}
        </div>

        {/* Progress */}
        {runInfo && (
          <div className={`px-3 py-2 border-b ${styles.cardBorder}`}>
            <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted} mb-1`}>
              <span>{t('dw.execMonitor.progress').replace('{done}', String(runInfo.completedSteps)).replace('{total}', String(runInfo.totalSteps))}</span>
              <span>{runInfo.elapsedMs ? `${(runInfo.elapsedMs / 1000).toFixed(1)}s` : '—'}</span>
            </div>
            <div className={`w-full h-1.5 ${styles.cardBg} rounded-full overflow-hidden`}>
              <div
                className={`h-full transition-all duration-500 rounded-full ${
                  runInfo.status === 'failed' ? styles.dangerBg :
                  runInfo.status === 'succeeded' ? styles.successBg :
                  styles.infoBg
                }`}
                style={{
                  width: `${runInfo.totalSteps > 0 ? (runInfo.completedSteps / runInfo.totalSteps) * 100 : 0}%`
                }}
              />
            </div>
          </div>
        )}

        {/* Step list */}
        <div className="flex-1 overflow-y-auto">
          {runInfo?.steps?.map((step) => {
            const isRunning = step.status === 'running';
            return (
              <div
                key={step.id}
                className={`flex items-center gap-2 px-3 py-2 border-b ${styles.cardBorder} last:border-b-0 hover:${styles.cardBg} transition-colors cursor-pointer ${
                  selectedStep?.id === step.id ? styles.infoBg : ''
                }`}
                onClick={() => setSelectedStep(step)}
              >
                {/* Status icon */}
                <span className={`shrink-0 ${isRunning ? 'animate-pulse' : ''}`}>
                  <StatusIcon status={step.status} size={14} />
                </span>

                {/* Node info */}
                <div className="flex-1 min-w-0">
                  <div className={`text-[11px] ${styles.cardText} truncate font-medium`}>
                    {step.nodeName || step.nodeId}
                  </div>
                  <div className={`flex items-center gap-2 text-[9px] ${styles.cardTextMuted}`}>
                    <span>{labelOf(step.status)}</span>
                    {step.rowsInput > 0 && (
                      <span>{t('dw.execMonitor.rowsIn').replace('{n}', step.rowsInput.toLocaleString())}</span>
                    )}
                    {step.rowsOutput > 0 && (
                      <span>{t('dw.execMonitor.rowsOut').replace('{n}', step.rowsOutput.toLocaleString())}</span>
                    )}
                    {step.elapsedMs > 0 && (
                      <span>{(step.elapsedMs / 1000).toFixed(1)}s</span>
                    )}
                  </div>
                </div>

                <ChevronRight size={12} className={`${styles.cardTextMuted} shrink-0`} />
              </div>
            );
          })}
          {(!runInfo?.steps || runInfo.steps.length === 0) && (
            <div className={`flex items-center justify-center py-6 text-[11px] ${styles.cardTextMuted}`}>
              {externalRunId ? (
                <span className="flex items-center gap-1.5">
                  <Loader2 size={12} className="animate-spin" />
                  {t('dw.execMonitor.loadingSteps')}
                </span>
              ) : (
                t('dw.execMonitor.noRuns')
              )}
            </div>
          )}
        </div>
      </div>

      {/* Step detail modal */}
      {selectedStep && (
        <PipelineExecutionMonitorStepDetail
          step={selectedStep}
          onClose={() => setSelectedStep(null)}
          styles={{
            cardBg: styles.cardBg,
            cardBorder: styles.cardBorder,
            cardText: styles.cardText,
            cardTextMuted: styles.cardTextMuted,
          }}
        />
      )}

      {/* Toast notifications */}
      {toast && (
        <div className="fixed bottom-4 right-4 z-50 animate-slide-up">
          <div
            className={`flex items-center gap-2 px-4 py-3 rounded-xl shadow-lg ${
              toast.type === 'success'
                ? `${styles.successBg} ${styles.cardText}`
                : toast.type === 'error'
                  ? `${styles.dangerBg} ${styles.cardText}`
                  : `${styles.accentBg} ${styles.cardText}`
            }`}
          >
            {toast.type === 'success' && <CheckCircle size={16} />}
            {toast.type === 'error' && <XCircle size={16} />}
            {toast.type === 'info' && <AlertCircle size={16} />}
            <span className="text-xs font-medium">{toast.msg}</span>
          </div>
        </div>
      )}
    </>
  );
};

export default PipelineExecutionMonitor;
