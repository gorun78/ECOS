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
import { apiFetchData } from '../../../api';
import { useTheme } from '../../../components/ThemeContext';
import {
  STATUS_COLORS, STATUS_LABELS, StatusIcon,
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
  const [runInfo, setRunInfo] = useState<RunInfo | null>(null);
  const [selectedStep, setSelectedStep] = useState<StepRunInfo | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error' | 'info'; msg: string } | null>(null);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const prevStatusRef = useRef<StepStatus | null>(null);

  // Toast helper
  const showToast = useCallback((type: 'success' | 'error' | 'info', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 4000);
  }, []);

  // Poll execution status
  const fetchRunStatus = useCallback(async (rid: string) => {
    try {
      const resp = await apiFetchData<{ data: RunInfo }>(
        `/api/v1/engine/data/pipeline/runs/${encodeURIComponent(rid)}`
      );
      const data = (resp as any)?.data || resp;
      setRunInfo(data);

      // Check for completion
      if (data?.status === 'succeeded') {
        if (prevStatusRef.current !== 'succeeded') {
          showToast('success', `Pipeline 执行完成! ${data.elapsedMs ? `耗时 ${(data.elapsedMs / 1000).toFixed(1)}s` : ''}`);
          onRunComplete?.(data);
        }
        // Stop polling on completion
        if (pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      } else if (data?.status === 'failed') {
        if (prevStatusRef.current !== 'failed') {
          showToast('error', `Pipeline 执行失败: ${data.errorMsg || '未知错误'}`);
          onRunComplete?.(data);
        }
        if (pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      } else if (data?.status === 'cancelled') {
        if (prevStatusRef.current !== 'cancelled') {
          showToast('info', 'Pipeline 已取消');
          onRunComplete?.(data);
        }
        if (pollingRef.current) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
        }
      }

      prevStatusRef.current = data?.status || null;
    } catch {
      // Silently ignore fetch errors during polling
    }
  }, [onRunComplete, showToast]);

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
            <Play size={14} className="text-blue-600" />
            <span className={`text-xs font-bold ${styles.cardText} uppercase tracking-wider`}>
              执行监控
            </span>
            {runInfo && (
              <span
                className={`inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-full ${STATUS_COLORS[runInfo.status].bg} ${STATUS_COLORS[runInfo.status].text}`}
              >
                {STATUS_LABELS[runInfo.status]}
              </span>
            )}
          </div>
          {onClose && (
            <button onClick={onClose} className={`p-0.5 rounded hover:bg-slate-200 ${styles.cardTextMuted} transition-colors`}>
              <X size={14} />
            </button>
          )}
        </div>

        {/* Progress */}
        {runInfo && (
          <div className={`px-3 py-2 border-b ${styles.cardBorder}`}>
            <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted} mb-1`}>
              <span>进度: {runInfo.completedSteps}/{runInfo.totalSteps} 步骤</span>
              <span>{runInfo.elapsedMs ? `${(runInfo.elapsedMs / 1000).toFixed(1)}s` : '—'}</span>
            </div>
            <div className={`w-full h-1.5 ${styles.cardBg} rounded-full overflow-hidden`}>
              <div
                className={`h-full transition-all duration-500 rounded-full ${
                  runInfo.status === 'failed' ? 'bg-red-500' :
                  runInfo.status === 'succeeded' ? 'bg-emerald-500' :
                  'bg-blue-500'
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
                className={`flex items-center gap-2 px-3 py-2 border-b ${styles.cardBorder} last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer ${
                  selectedStep?.id === step.id ? 'bg-blue-50' : ''
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
                    <span>{STATUS_LABELS[step.status]}</span>
                    {step.rowsInput > 0 && (
                      <span>入: {step.rowsInput.toLocaleString()} 行</span>
                    )}
                    {step.rowsOutput > 0 && (
                      <span>出: {step.rowsOutput.toLocaleString()} 行</span>
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
                  正在加载步骤...
                </span>
              ) : (
                '暂无执行记录'
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
                ? 'bg-emerald-600 text-white'
                : toast.type === 'error'
                  ? 'bg-red-600 text-white'
                  : 'bg-blue-600 text-white'
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
