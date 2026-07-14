/**
 * PipelineExecutionMonitor — 执行监控组件
 * 节点实时状态轮询 (2秒间隔) + Toast 通知 + 步骤详情弹出框
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Play, Square, RefreshCw, Clock,
  CheckCircle, XCircle, AlertTriangle,
  Loader2, ChevronRight, X, BarChart3, AlertCircle,
} from 'lucide-react';
import { apiFetchData } from '../../../api';

// ─── Types ────────────────────────────────────────────

export type StepStatus = 'idle' | 'queued' | 'running' | 'succeeded' | 'failed' | 'cancelled';

export interface StepRunInfo {
  id: string;
  stepId: string;
  nodeId: string;
  nodeName: string;
  status: StepStatus;
  rowsInput: number;
  rowsOutput: number;
  startedAt?: string;
  finishedAt?: string;
  elapsedMs: number;
  errorMsg?: string;
}

export interface RunInfo {
  id: string;
  taskId: string;
  status: StepStatus;
  triggeredBy: string;
  totalSteps: number;
  completedSteps: number;
  startedAt?: string;
  finishedAt?: string;
  elapsedMs: number;
  errorMsg?: string;
  steps: StepRunInfo[];
}

// ─── Props ─────────────────────────────────────────────

interface PipelineExecutionMonitorProps {
  runId?: string | null;
  taskId?: string;
  onRunComplete?: (runInfo: RunInfo) => void;
  onClose?: () => void;
  className?: string;
}

// ─── Status color map ─────────────────────────────────

const STATUS_COLORS: Record<StepStatus, { bg: string; text: string; border: string; icon: React.FC<{ size?: number; className?: string }> }> = {
  idle: { bg: 'bg-slate-100', text: 'text-slate-500', border: 'border-slate-300', icon: ({ size, className }) => <div className={`w-3 h-3 rounded-full bg-slate-400 ${className || ''}`} /> },
  queued: { bg: 'bg-blue-100', text: 'text-blue-600', border: 'border-blue-300', icon: ({ size, className }) => <Clock size={size || 12} className={`text-blue-500 ${className || ''}`} /> },
  running: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-400', icon: ({ size, className }) => <Loader2 size={size || 12} className={`text-yellow-600 animate-spin ${className || ''}`} /> },
  succeeded: { bg: 'bg-emerald-100', text: 'text-emerald-600', border: 'border-emerald-300', icon: ({ size, className }) => <CheckCircle size={size || 12} className={`text-emerald-500 ${className || ''}`} /> },
  failed: { bg: 'bg-red-100', text: 'text-red-600', border: 'border-red-400', icon: ({ size, className }) => <XCircle size={size || 12} className={`text-red-500 ${className || ''}`} /> },
  cancelled: { bg: 'bg-slate-100', text: 'text-slate-500', border: 'border-slate-300', icon: ({ size, className }) => <AlertTriangle size={size || 12} className={`text-slate-400 ${className || ''}`} /> },
};

const STATUS_LABELS: Record<StepStatus, string> = {
  idle: '空闲',
  queued: '排队中',
  running: '运行中',
  succeeded: '已完成',
  failed: '失败',
  cancelled: '已取消',
};

// ─── Status Icon helper ───────────────────────────────

const StatusIcon: React.FC<{ status: StepStatus; size?: number }> = React.memo(({ status, size = 12 }) => {
  switch (status) {
    case 'idle': return <div className="w-3 h-3 rounded-full bg-slate-400" />;
    case 'queued': return <Clock size={size} className="text-blue-500" />;
    case 'running': return <Loader2 size={size} className="text-yellow-600 animate-spin" />;
    case 'succeeded': return <CheckCircle size={size} className="text-emerald-500" />;
    case 'failed': return <XCircle size={size} className="text-red-500" />;
    case 'cancelled': return <AlertTriangle size={size} className="text-slate-400" />;
  }
});

// ─── Component ────────────────────────────────────────

const PipelineExecutionMonitor: React.FC<PipelineExecutionMonitorProps> = ({
  runId: externalRunId,
  taskId,
  onRunComplete,
  onClose,
  className = '',
}) => {
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
      <div className={`flex flex-col bg-white border border-slate-200 rounded-lg shadow-sm overflow-hidden ${className}`}>
        {/* Header */}
        <div className="flex items-center justify-between px-3 py-2.5 border-b border-slate-100 bg-slate-50 shrink-0">
          <div className="flex items-center gap-2">
            <Play size={14} className="text-blue-600" />
            <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">
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
            <button onClick={onClose} className="p-0.5 rounded hover:bg-slate-200 text-slate-400 transition-colors">
              <X size={14} />
            </button>
          )}
        </div>

        {/* Progress */}
        {runInfo && (
          <div className="px-3 py-2 border-b border-slate-50">
            <div className="flex items-center justify-between text-[10px] text-slate-500 mb-1">
              <span>进度: {runInfo.completedSteps}/{runInfo.totalSteps} 步骤</span>
              <span>{runInfo.elapsedMs ? `${(runInfo.elapsedMs / 1000).toFixed(1)}s` : '—'}</span>
            </div>
            <div className="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden">
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
            const colors = STATUS_COLORS[step.status];
            const isRunning = step.status === 'running';
            return (
              <div
                key={step.id}
                className={`flex items-center gap-2 px-3 py-2 border-b border-slate-50 last:border-b-0 hover:bg-slate-50 transition-colors cursor-pointer ${
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
                  <div className="text-[11px] text-slate-700 truncate font-medium">
                    {step.nodeName || step.nodeId}
                  </div>
                  <div className="flex items-center gap-2 text-[9px] text-slate-400">
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

                <ChevronRight size={12} className="text-slate-400 shrink-0" />
              </div>
            );
          })}
          {(!runInfo?.steps || runInfo.steps.length === 0) && (
            <div className="flex items-center justify-center py-6 text-[11px] text-slate-400">
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
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="fixed inset-0 bg-black/30" onClick={() => setSelectedStep(null)} />
          <div className="relative z-10 bg-white rounded-xl shadow-2xl w-[480px] max-h-[500px] flex flex-col overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
              <div className="flex items-center gap-2">
                <BarChart3 size={15} className="text-blue-600" />
                <span className="text-sm font-bold text-slate-800">步骤详情</span>
              </div>
              <button
                onClick={() => setSelectedStep(null)}
                className="p-1 rounded hover:bg-slate-200 text-slate-400 transition-colors"
              >
                <X size={15} />
              </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {/* Status */}
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-slate-500 w-16">状态</span>
                <span className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full ${STATUS_COLORS[selectedStep.status].bg} ${STATUS_COLORS[selectedStep.status].text}`}>
                  <StatusIcon status={selectedStep.status} />
                  {STATUS_LABELS[selectedStep.status]}
                </span>
              </div>

              {/* Node */}
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-slate-500 w-16">节点</span>
                <span className="text-xs font-mono text-slate-700">{selectedStep.nodeId}</span>
              </div>

              {/* Rows */}
              <div className="grid grid-cols-2 gap-3">
                <div className="border border-slate-200 rounded-lg p-2.5">
                  <div className="text-[10px] text-slate-400 mb-0.5">输入行数</div>
                  <div className="text-sm font-bold text-slate-800 tabular-nums">
                    {selectedStep.rowsInput.toLocaleString()}
                  </div>
                </div>
                <div className="border border-slate-200 rounded-lg p-2.5">
                  <div className="text-[10px] text-slate-400 mb-0.5">输出行数</div>
                  <div className="text-sm font-bold text-slate-800 tabular-nums">
                    {selectedStep.rowsOutput.toLocaleString()}
                  </div>
                </div>
              </div>

              {/* Timing */}
              <div className="grid grid-cols-3 gap-2">
                <div className="border border-slate-200 rounded-lg p-2.5">
                  <div className="text-[10px] text-slate-400 mb-0.5">耗时</div>
                  <div className="text-xs font-bold text-slate-700 tabular-nums">
                    {selectedStep.elapsedMs > 0 ? `${(selectedStep.elapsedMs / 1000).toFixed(2)}s` : '—'}
                  </div>
                </div>
                <div className="border border-slate-200 rounded-lg p-2.5">
                  <div className="text-[10px] text-slate-400 mb-0.5">开始时间</div>
                  <div className="text-[10px] text-slate-600 truncate">
                    {selectedStep.startedAt ? new Date(selectedStep.startedAt).toLocaleTimeString() : '—'}
                  </div>
                </div>
                <div className="border border-slate-200 rounded-lg p-2.5">
                  <div className="text-[10px] text-slate-400 mb-0.5">结束时间</div>
                  <div className="text-[10px] text-slate-600 truncate">
                    {selectedStep.finishedAt ? new Date(selectedStep.finishedAt).toLocaleTimeString() : '—'}
                  </div>
                </div>
              </div>

              {/* Error log */}
              {selectedStep.errorMsg && (
                <div>
                  <div className="flex items-center gap-1.5 mb-1">
                    <AlertCircle size={12} className="text-red-500" />
                    <span className="text-[11px] font-semibold text-red-600">错误日志</span>
                  </div>
                  <div className="border border-red-200 bg-red-50 rounded-lg p-3">
                    <pre className="text-[10px] text-red-700 font-mono whitespace-pre-wrap break-all">
                      {selectedStep.errorMsg}
                    </pre>
                  </div>
                </div>
              )}
            </div>

            <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 shrink-0">
              <button
                onClick={() => setSelectedStep(null)}
                className="w-full px-3 py-1.5 text-xs border border-slate-300 text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
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
