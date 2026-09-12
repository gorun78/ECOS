/**
 * AIP Copilot — natural-language input bar (bottom of drawer)
 * Pure presentation component, no business logic.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import LucideIcon from '../LucideIcon';
import { useLanguage } from '../LanguageContext';
import { useTheme } from '../ThemeContext';

interface CopilotInputBarProps {
  value: string;
  onChange: (v: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  disabled: boolean;
}

export default function CopilotInputBar({ value, onChange, onSubmit, disabled }: CopilotInputBarProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <form
      onSubmit={onSubmit}
      className={`border-t ${styles.appBorder} p-3 ${styles.cardBg} shrink-0 flex items-center gap-2`}
    >
      <input
        type="text"
        placeholder={t('copilot.chat.placeholder')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        className={`flex-1 h-9 px-3 ${styles.inputBg} hover:${styles.appBg} focus:${styles.cardBg} border ${styles.inputBorder} rounded-lg text-xs placeholder:${styles.cardTextMuted} ${styles.inputText} focus:outline-hidden focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/30 transition-all disabled:opacity-50 disabled:cursor-not-allowed`}
      />
      <button
        type="submit"
        disabled={!value.trim() || disabled}
        aria-label={t('copilot.chat.runAgent')}
        className="h-9 w-9 bg-indigo-600 text-white rounded-lg flex items-center justify-center hover:bg-indigo-700 active:bg-indigo-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <LucideIcon name="Send" size={13} />
      </button>
    </form>
  );
}
