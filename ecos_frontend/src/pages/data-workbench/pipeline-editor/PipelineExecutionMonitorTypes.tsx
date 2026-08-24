/**
 * PipelineExecutionMonitorTypes — 类型定义与状态常量
 * 从 PipelineExecutionMonitor 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import {
  Clock, CheckCircle, XCircle, AlertTriangle, Loader2,
} from 'lucide-react';

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

export interface PipelineExecutionMonitorProps {
  runId?: string | null;
  taskId?: string;
  onRunComplete?: (runInfo: RunInfo) => void;
  onClose?: () => void;
  className?: string;
}

// ─── Status color map ─────────────────────────────────

export const STATUS_COLORS: Record<StepStatus, { bg: string; text: string; border: string; icon: React.FC<{ size?: number; className?: string }> }> = {
  idle: { bg: 'bg-slate-100', text: 'text-slate-500', border: 'border-slate-300', icon: ({ size, className }) => <div className={`w-3 h-3 rounded-full bg-slate-400 ${className || ''}`} /> },
  queued: { bg: 'bg-blue-100', text: 'text-blue-600', border: 'border-blue-300', icon: ({ size, className }) => <Clock size={size || 12} className={`text-blue-500 ${className || ''}`} /> },
  running: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-400', icon: ({ size, className }) => <Loader2 size={size || 12} className={`text-yellow-600 animate-spin ${className || ''}`} /> },
  succeeded: { bg: 'bg-emerald-100', text: 'text-emerald-600', border: 'border-emerald-300', icon: ({ size, className }) => <CheckCircle size={size || 12} className={`text-emerald-500 ${className || ''}`} /> },
  failed: { bg: 'bg-red-100', text: 'text-red-600', border: 'border-red-400', icon: ({ size, className }) => <XCircle size={size || 12} className={`text-red-500 ${className || ''}`} /> },
  cancelled: { bg: 'bg-slate-100', text: 'text-slate-500', border: 'border-slate-300', icon: ({ size, className }) => <AlertTriangle size={size || 12} className={`text-slate-400 ${className || ''}`} /> },
};

export const STATUS_LABELS: Record<StepStatus, string> = {
  idle: '空闲',
  queued: '排队中',
  running: '运行中',
  succeeded: '已完成',
  failed: '失败',
  cancelled: '已取消',
};

// ─── Status Icon helper ───────────────────────────────

export const StatusIcon: React.FC<{ status: StepStatus; size?: number }> = React.memo(({ status, size = 12 }) => {
  switch (status) {
    case 'idle': return <div className="w-3 h-3 rounded-full bg-slate-400" />;
    case 'queued': return <Clock size={size} className="text-blue-500" />;
    case 'running': return <Loader2 size={size} className="text-yellow-600 animate-spin" />;
    case 'succeeded': return <CheckCircle size={size} className="text-emerald-500" />;
    case 'failed': return <XCircle size={size} className="text-red-500" />;
    case 'cancelled': return <AlertTriangle size={size} className="text-slate-400" />;
  }
});
