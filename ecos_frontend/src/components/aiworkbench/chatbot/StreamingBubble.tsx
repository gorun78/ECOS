/**
 * StreamingBubble — SSE 流式回复中的气泡（含光标占位）
 *
 * 从 `components/aiworkbench/chatbot/ChatPanel.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 头像、占位文案 i18n key 与闪烁光标 className 逐字保留。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Streaming Message Bubble ────────────────────────────────────────

export default function StreamingBubble({
  content,
  agentName,
  agentAvatar,
  styles,
}: {
  content: string;
  agentName: string;
  agentAvatar: string;
  styles: Record<string, string>;
}) {
  const { t } = useLanguage();
  return (
    <div className="flex gap-2.5">
      <span
        className={`p-1.5 rounded-lg shrink-0 h-7 w-7 flex items-center justify-center font-bold text-white shadow-3xs ${styles.sidebarActiveBg}`}
      >
        <Icon name={agentAvatar} size={12} />
      </span>
      <div className="space-y-1.5 max-w-[85%]">
        <div
          className={`p-3 rounded-2xl ${styles.cardBg} ${styles.cardText} ${styles.cardBorder} border rounded-tl-none font-sans text-[11px] whitespace-pre-line shadow-3xs leading-normal`}
        >
          {content || (
            <span className={`${styles.cardTextMuted} italic`}>
              {t('aiworkbench.chatbot.streamingPlaceholder')}
            </span>
          )}
          {content && (
            <span className="inline-block w-2 h-3.5 bg-blue-500 ml-0.5 animate-pulse rounded-sm align-middle" />
          )}
        </div>
      </div>
    </div>
  );
}
