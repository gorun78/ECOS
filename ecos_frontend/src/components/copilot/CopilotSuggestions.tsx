/**
 * Copilot 动态建议区（抽屉底部「一键智能代理协同」）。
 *
 * 内容按当前用户数据动态生成：当前智能体的能力建议 + 本会话最近提问，
 * 默认收起（open=false），点击标题展开；点击建议直接发起该智能体对话。
 * 纯展示组件，文案经 `copilot.*` i18n。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { Icon } from '../LucideIcon';
import { useTheme } from '../ThemeContext';

interface CopilotSuggestionsProps {
  open: boolean;
  title: string;
  hint?: string;
  items: string[];
  emptyText: string;
  disabled?: boolean;
  onToggle: () => void;
  onAsk: (text: string) => void;
}

export default function CopilotSuggestions({
  open,
  title,
  hint,
  items,
  emptyText,
  disabled,
  onToggle,
  onAsk,
}: CopilotSuggestionsProps) {
  const { styles } = useTheme();
  return (
    <div className={`border-t ${styles.cardBorder} ${styles.appBg} shrink-0 select-none`}>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className={`w-full px-3 py-2 flex items-center justify-between gap-2 cursor-pointer hover:opacity-90 ${styles.cardText}`}
      >
        <span className={`text-[10px] font-black uppercase tracking-wider font-mono flex items-center gap-1.5 ${styles.cardTextMuted}`}>
          <Icon name="Zap" size={11} className={`${styles.accentText}`} />
          {title}
          <span className={`text-[9px] px-1 rounded ${styles.badgeBg} ${styles.muted}`}>{items.length}</span>
        </span>
        <Icon name={open ? 'ChevronUp' : 'ChevronDown'} size={13} className={styles.cardTextMuted} />
      </button>
      {open && (
        <div className="px-3 pb-3 space-y-1.5">
          {hint && (
            <p className={`text-[9px] ${styles.cardTextMuted} font-mono`}>{hint}</p>
          )}
          {items.length === 0 ? (
            <p className={`text-[10px] ${styles.cardTextMuted}`}>{emptyText}</p>
          ) : (
            <div className="grid grid-cols-1 gap-1.5">
              {items.map((item) => (
                <button
                  type="button"
                  key={item}
                  disabled={disabled}
                  onClick={() => { if (!disabled) onAsk(item); }}
                  className={`w-full text-left px-2.5 py-1.5 rounded-lg border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} text-[11px] transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5`}
                >
                  <Icon name="Sparkles" size={11} className={`${styles.accentText} shrink-0`} />
                  <span className="truncate">{item}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
