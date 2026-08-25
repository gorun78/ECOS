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

export const STATUS_COLORS: Record<StepStatus, { bg: string; text: string; border: string; icon: React.FC<{ size?: number; className?: string }> }> = {
  idle: { bg: `${styles.sidebarBg}`, text: `${styles.muted}`, border: `${styles.inputBorder}`, icon: ({ size, className }) => <div className={`w-3 h-3 rounded-full ${styles.sidebarBg} ${className || ''}`} /> },
  queued: { bg: `${styles.infoBg}`, text: `${styles.accentText}`, border: `${styles.infoBorder}`, icon: ({ size, className }) => <Clock size={size || 12} className={`${styles.infoText} ${className || ''}`} /> },
  running: { bg: `${styles.warningBg}`, text: `${styles.warningText}`, border: `${styles.warningBorder}`, icon: ({ size, className }) => <Loader2 size={size || 12} className={`${styles.warningText} animate-spin ${className || ''}`} /> },
  succeeded: { bg: `${styles.successBg}`, text: `${styles.successText}`, border: `${styles.successBorder}`, icon: ({ size, className }) => <CheckCircle size={size || 12} className={`${styles.successText} ${className || ''}`} /> },
  failed: { bg: `${styles.dangerBg}`, text: `${styles.dangerText}`, border: `${styles.dangerBorder}`, icon: ({ size, className }) => <XCircle size={size || 12} className={`${styles.dangerText} ${className || ''}`} /> },
  cancelled: { bg: `${styles.sidebarBg}`, text: `${styles.muted}`, border: `${styles.inputBorder}`, icon: ({ size, className }) => <AlertTriangle size={size || 12} className={`${styles.cardTextMuted} ${className || ''}`} /> },
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
    case 'idle': return <div className={`w-3 h-3 rounded-full ${styles.sidebarBg}`} />;
    case 'queued': return <Clock size={size} className={`${styles.infoText}`} />;
    case 'running': return <Loader2 size={size} className={`${styles.warningText} animate-spin`} />;
    case 'succeeded': return <CheckCircle size={size} className={`${styles.successText}`} />;
    case 'failed': return <XCircle size={size} className={`${styles.dangerText}`} />;
    case 'cancelled': return <AlertTriangle size={size} className={`${styles.cardTextMuted}`} />;
  }
});

// TODO: useTheme insertion needed
