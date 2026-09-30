/**
 * OagProgressBar — OAG 8 步流水线进度条子组件
 *
 * 从 `components/aiworkbench/chatbot/ChatPanel.tsx` 机械抽取（H6-T4 组件行数治理）：
 * JSX 结构、状态色映射、i18n key 与占位插值顺序逐字保留。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import type { OagStepInfo } from './chatbotTypes';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── OAG Progress Bar Sub-component ──────────────────────────────────

export default function OagProgressBar({
  steps,
  styles,
}: {
  steps: OagStepInfo[];
  styles: Record<string, string>;
}) {
  const { t } = useLanguage();
  const completed = steps.filter(s => s.status === 'completed').length;
  const hasError = steps.some(s => s.status === 'error');
  const total = steps.length;
  const pct = Math.round((completed / total) * 100);

  return (
    <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-xl space-y-2 shadow-2xs`}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <span className={`font-extrabold text-[10px] ${styles.accentText} flex items-center gap-1.5`}>
          <Icon name="Zap" size={11} className="animate-pulse" />
          <span>{t('aiworkbench.chatbot.oagPipelineTitle')}</span>
        </span>
        <span className={`font-mono text-[9px] font-bold ${hasError ? 'text-rose-500' : 'text-emerald-500'}`}>
          {hasError ? t('aiworkbench.chatbot.oagAbnormal') : t('aiworkbench.chatbot.oagStepFormat').replace('{completed}', String(completed)).replace('{total}', String(total)).replace('{pct}', String(pct))}
        </span>
      </div>

      {/* Progress bar track */}
      <div className={`w-full h-1.5 ${styles.inputBg} rounded-full overflow-hidden`}>
        <div
          className={`h-full rounded-full transition-all duration-500 ease-out ${
            hasError ? 'bg-rose-500' : 'bg-gradient-to-r from-blue-500 to-emerald-500'
          }`}
          style={{ width: `${pct}%` }}
        />
      </div>

      {/* Step dots row */}
      <div className="flex items-center gap-0.5">
        {steps.map((step, idx) => (
          <React.Fragment key={step.step}>
            {idx > 0 && (
              <div
                className={`flex-1 h-0.5 rounded transition-colors duration-300 ${
                  step.status === 'completed' || steps[idx - 1].status === 'completed'
                    ? 'bg-emerald-400'
                    : step.status === 'active'
                    ? 'bg-blue-400 animate-pulse'
                    : `${styles.cardBorder}`
                }`}
              />
            )}
            <div
              title={`${step.step}. ${step.label}${step.detail ? ` — ${step.detail}` : ''}`}
              className={`w-5 h-5 rounded-full flex items-center justify-center text-[9px] font-bold transition-all duration-300 shrink-0 ${
                step.status === 'completed'
                  ? 'bg-emerald-500 text-white'
                  : step.status === 'active'
                  ? 'bg-blue-500 text-white animate-pulse shadow-lg shadow-blue-500/40'
                  : step.status === 'error'
                  ? 'bg-rose-500 text-white'
                  : `${styles.inputBg} ${styles.cardTextMuted} border ${styles.cardBorder}`
              }`}
            >
              {step.status === 'completed' ? (
                <Icon name="Check" size={9} />
              ) : step.status === 'error' ? (
                <Icon name="X" size={9} />
              ) : step.status === 'active' ? (
                <Icon name={step.icon} size={9} />
              ) : (
                <span className="text-[7px]">{step.step}</span>
              )}
            </div>
          </React.Fragment>
        ))}
      </div>

      {/* Current step detail */}
      {(() => {
        const activeStep = steps.find(s => s.status === 'active');
        const errorStep = steps.find(s => s.status === 'error');
        const target = errorStep || activeStep;
        if (!target) return null;
        return (
          <div
            className={`flex items-center gap-1.5 font-mono text-[9px] ${
              errorStep ? 'text-rose-500' : styles.accentText
            }`}
          >
            <Icon
              name={target.icon}
              size={10}
              className={errorStep ? '' : 'animate-spin'}
            />
            <span>
              {t('aiworkbench.chatbot.oagStepDetail').replace('{step}', String(target.step)).replace('{total}', String(total)).replace('{label}', target.label)}
              {target.detail && (
                <span className={styles.cardTextMuted}> — {target.detail}</span>
              )}
            </span>
          </div>
        );
      })()}
    </div>
  );
}
