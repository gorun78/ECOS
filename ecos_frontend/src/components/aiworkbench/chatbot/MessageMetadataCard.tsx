/**
 * MessageMetadataCard — 单条回复的 token / 时延 / 置信度 / 护栏元数据卡
 *
 * 从 `components/aiworkbench/chatbot/ChatPanel.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 空 metadata 早退、字段条件渲染顺序与 i18n key 逐字保留。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import type { MessageMetadata } from './chatbotTypes';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Message Metadata Card Sub-component ─────────────────────────────

export default function MessageMetadataCard({
  metadata,
  styles,
}: {
  metadata: MessageMetadata;
  styles: Record<string, string>;
}) {
  const { t } = useLanguage();
  if (!metadata || Object.keys(metadata).length === 0) return null;

  return (
    <div
      className={`p-2.5 ${styles.cardBg}/80 border ${styles.cardBorder} rounded-lg space-y-1.5 font-mono text-[9px] transition-all`}
    >
      <div className={`flex items-center gap-1.5 ${styles.cardTextMuted} font-extrabold text-[9px] border-b ${styles.cardBorder} pb-1`}>
        <Icon name="BarChart3" size={10} />
        <span>{t('aiworkbench.chatbot.metadataTitle')}</span>
      </div>
      <div className="grid grid-cols-3 gap-x-2 gap-y-1 text-[9px]">
        {metadata.inputTokens !== undefined && (
          <div className="flex items-center gap-1">
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.metadataInput')}</span>
            <span className={`${styles.cardText} font-bold`}>{metadata.inputTokens.toLocaleString()} tok</span>
          </div>
        )}
        {metadata.outputTokens !== undefined && (
          <div className="flex items-center gap-1">
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.metadataOutput')}</span>
            <span className={`${styles.cardText} font-bold`}>{metadata.outputTokens.toLocaleString()} tok</span>
          </div>
        )}
        {metadata.latencyMs !== undefined && (
          <div className="flex items-center gap-1">
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.metadataLatency')}</span>
            <span className={`${styles.cardText} font-bold`}>{metadata.latencyMs}ms</span>
          </div>
        )}
        {metadata.confidence !== undefined && (
          <div className="flex items-center gap-1 col-span-1">
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.metadataConfidence')}</span>
            <span
              className={`font-bold ${
                metadata.confidence >= 0.9
                  ? 'text-emerald-500'
                  : metadata.confidence >= 0.7
                  ? 'text-amber-500'
                  : 'text-rose-500'
              }`}
            >
              {(metadata.confidence * 100).toFixed(1)}%
            </span>
          </div>
        )}
        {metadata.modelName && (
          <div className="flex items-center gap-1 col-span-1">
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.metadataModel')}</span>
            <span className={`${styles.cardText} font-bold`}>{metadata.modelName}</span>
          </div>
        )}
        {metadata.guardrailStatus && (
          <div className="flex items-center gap-1 col-span-1">
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.metadataGuardrail')}</span>
            <span
              className={`font-bold ${
                metadata.guardrailStatus === 'passed'
                  ? 'text-emerald-500'
                  : metadata.guardrailStatus === 'warning'
                  ? 'text-amber-500'
                  : 'text-rose-500'
              }`}
            >
              {metadata.guardrailStatus === 'passed'
                ? t('aiworkbench.chatbot.metadataPassed')
                : metadata.guardrailStatus === 'warning'
                ? t('aiworkbench.chatbot.metadataWarning')
                : t('aiworkbench.chatbot.metadataBlocked')}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
