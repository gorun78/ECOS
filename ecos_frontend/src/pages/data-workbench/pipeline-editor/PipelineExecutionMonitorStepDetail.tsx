/**
 * PipelineExecutionMonitorStepDetail — 步骤详情弹窗子组件
 * 从 PipelineExecutionMonitor 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import { BarChart3, X, AlertCircle } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import {
  getStatusColor, STATUS_LABEL_KEYS, StatusIcon,
  type StepStatus, type StepRunInfo,
} from './PipelineExecutionMonitorTypes';


interface Props {
  step: StepRunInfo;
  onClose: () => void;
  styles: Record<string, string>;
}

const PipelineExecutionMonitorStepDetail: React.FC<Props> = ({ step, onClose, styles }) => {
  const { t } = useLanguage();
  const labelOf = (s: string) => (s in STATUS_LABEL_KEYS ? t(STATUS_LABEL_KEYS[s as StepStatus]) : s);
  const sc = getStatusColor(step.status);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/30" onClick={onClose} />
      <div className={`relative z-10 ${styles.cardBg} rounded-xl shadow-2xl w-[480px] max-h-[500px] flex flex-col overflow-hidden`}>
        {/* Header */}
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <div className="flex items-center gap-2">
            <BarChart3 size={15} className={`${styles.accentText}`} />
            <span className={`text-sm font-bold ${styles.cardText}`}>{t('dw.execMonitor.step.title')}</span>
          </div>
          <button type="button"
            onClick={onClose}
            className={`p-1 rounded hover:${styles.sidebarBg} ${styles.cardTextMuted} transition-colors`}
          >
            <X size={15} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {/* Status */}
          <div className="flex items-center gap-2">
            <span className={`text-[11px] ${styles.cardTextMuted} w-16`}>{t('dw.execMonitor.step.status')}</span>
            <span className={`inline-flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full ${sc.bg} ${sc.text}`}>
              <StatusIcon status={step.status} />
              {labelOf(step.status)}
            </span>
          </div>

          {/* Node */}
          <div className="flex items-center gap-2">
            <span className={`text-[11px] ${styles.cardTextMuted} w-16`}>{t('dw.execMonitor.step.node')}</span>
            <span className={`text-xs font-mono ${styles.cardText}`}>{step.nodeId}</span>
          </div>

          {/* Rows */}
          <div className="grid grid-cols-2 gap-3">
            <div className={`border ${styles.cardBorder} rounded-lg p-2.5`}>
              <div className={`text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('dw.execMonitor.step.rowsInput')}</div>
              <div className={`text-sm font-bold ${styles.cardText} tabular-nums`}>
                {step.rowsInput.toLocaleString()}
              </div>
            </div>
            <div className={`border ${styles.cardBorder} rounded-lg p-2.5`}>
              <div className={`text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('dw.execMonitor.step.rowsOutput')}</div>
              <div className={`text-sm font-bold ${styles.cardText} tabular-nums`}>
                {step.rowsOutput.toLocaleString()}
              </div>
            </div>
          </div>

          {/* Timing */}
          <div className="grid grid-cols-3 gap-2">
            <div className={`border ${styles.cardBorder} rounded-lg p-2.5`}>
              <div className={`text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('dw.execMonitor.step.elapsed')}</div>
              <div className={`text-xs font-bold ${styles.cardText} tabular-nums`}>
                {step.elapsedMs > 0 ? `${(step.elapsedMs / 1000).toFixed(2)}s` : '—'}
              </div>
            </div>
            <div className={`border ${styles.cardBorder} rounded-lg p-2.5`}>
              <div className={`text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('dw.execMonitor.step.startedAt')}</div>
              <div className={`text-[10px] ${styles.cardText} truncate`}>
                {step.startedAt ? new Date(step.startedAt).toLocaleTimeString() : '—'}
              </div>
            </div>
            <div className={`border ${styles.cardBorder} rounded-lg p-2.5`}>
              <div className={`text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('dw.execMonitor.step.finishedAt')}</div>
              <div className={`text-[10px] ${styles.cardText} truncate`}>
                {step.finishedAt ? new Date(step.finishedAt).toLocaleTimeString() : '—'}
              </div>
            </div>
          </div>

          {/* Error log */}
          {step.errorMsg && (
            <div>
              <div className="flex items-center gap-1.5 mb-1">
                <AlertCircle size={12} className={`${styles.dangerText}`} />
                <span className={`text-[11px] font-semibold ${styles.dangerText}`}>{t('dw.execMonitor.step.errorLog')}</span>
              </div>
              <div className={`border ${styles.dangerBorder} ${styles.dangerBg} rounded-lg p-3`}>
                <pre className={`text-[10px] ${styles.dangerText} font-mono whitespace-pre-wrap break-all`}>
                  {step.errorMsg}
                </pre>
              </div>
            </div>
          )}
        </div>

        <div className={`px-4 py-3 border-t ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <button type="button"
            onClick={onClose}
            className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} ${styles.cardText} hover:${styles.sidebarBg} rounded-lg transition-colors`}
          >
            {t('dw.execMonitor.step.close')}
          </button>
        </div>
      </div>
    </div>
  );
};

export default PipelineExecutionMonitorStepDetail;
