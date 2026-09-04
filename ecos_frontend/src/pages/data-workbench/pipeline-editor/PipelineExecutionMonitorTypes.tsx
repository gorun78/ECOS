/**
 * PipelineExecutionMonitorTypes — 类型定义与状态常量
 * 从 PipelineExecutionMonitor 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import {
  Clock, CheckCircle, XCircle, AlertTriangle, Loader2,
} from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';

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

const PIPELINE_STATUS_STYLE: Record<string, string> = {
  sidebarBg: 'bg-slate-100', muted: 'text-slate-500', inputBorder: 'border-[#E2E8F0]',
  infoBg: 'bg-blue-50', accentText: 'text-indigo-600', infoBorder: 'border-blue-200',
  infoText: 'text-blue-700', warningBg: 'bg-amber-50', warningText: 'text-amber-700',
  warningBorder: 'border-amber-200', successBg: 'bg-emerald-50', successText: 'text-emerald-700',
  successBorder: 'border-emerald-200', dangerBg: 'bg-red-50', dangerText: 'text-red-700',
  dangerBorder: 'border-red-200', cardTextMuted: 'text-slate-500',
};

export const STATUS_COLORS: Record<StepStatus, { bg: string; text: string; border: string; icon: React.FC<{ size?: number; className?: string }> }> = {
  idle: { bg: `${PIPELINE_STATUS_STYLE.sidebarBg}`, text: `${PIPELINE_STATUS_STYLE.muted}`, border: `${PIPELINE_STATUS_STYLE.inputBorder}`, icon: ({ size, className }) => <div className={`w-3 h-3 rounded-full ${PIPELINE_STATUS_STYLE.sidebarBg} ${className || ''}`} /> },
  queued: { bg: `${PIPELINE_STATUS_STYLE.infoBg}`, text: `${PIPELINE_STATUS_STYLE.accentText}`, border: `${PIPELINE_STATUS_STYLE.infoBorder}`, icon: ({ size, className }) => <Clock size={size || 12} className={`${PIPELINE_STATUS_STYLE.infoText} ${className || ''}`} /> },
  running: { bg: `${PIPELINE_STATUS_STYLE.warningBg}`, text: `${PIPELINE_STATUS_STYLE.warningText}`, border: `${PIPELINE_STATUS_STYLE.warningBorder}`, icon: ({ size, className }) => <Loader2 size={size || 12} className={`${PIPELINE_STATUS_STYLE.warningText} animate-spin ${className || ''}`} /> },
  succeeded: { bg: `${PIPELINE_STATUS_STYLE.successBg}`, text: `${PIPELINE_STATUS_STYLE.successText}`, border: `${PIPELINE_STATUS_STYLE.successBorder}`, icon: ({ size, className }) => <CheckCircle size={size || 12} className={`${PIPELINE_STATUS_STYLE.successText} ${className || ''}`} /> },
  failed: { bg: `${PIPELINE_STATUS_STYLE.dangerBg}`, text: `${PIPELINE_STATUS_STYLE.dangerText}`, border: `${PIPELINE_STATUS_STYLE.dangerBorder}`, icon: ({ size, className }) => <XCircle size={size || 12} className={`${PIPELINE_STATUS_STYLE.dangerText} ${className || ''}`} /> },
  cancelled: { bg: `${PIPELINE_STATUS_STYLE.sidebarBg}`, text: `${PIPELINE_STATUS_STYLE.muted}`, border: `${PIPELINE_STATUS_STYLE.inputBorder}`, icon: ({ size, className }) => <AlertTriangle size={size || 12} className={`${PIPELINE_STATUS_STYLE.cardTextMuted} ${className || ''}`} /> },
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
    case 'idle': return <div className={`w-3 h-3 rounded-full ${PIPELINE_STATUS_STYLE.sidebarBg}`} />;
    case 'queued': return <Clock size={size} className={`${PIPELINE_STATUS_STYLE.infoText}`} />;
    case 'running': return <Loader2 size={size} className={`${PIPELINE_STATUS_STYLE.warningText} animate-spin`} />;
    case 'succeeded': return <CheckCircle size={size} className={`${PIPELINE_STATUS_STYLE.successText}`} />;
    case 'failed': return <XCircle size={size} className={`${PIPELINE_STATUS_STYLE.dangerText}`} />;
    case 'cancelled': return <AlertTriangle size={size} className={`${PIPELINE_STATUS_STYLE.cardTextMuted}`} />;
  }
});

// TODO: useTheme insertion needed
