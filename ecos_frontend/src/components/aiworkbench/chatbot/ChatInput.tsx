/**
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

interface ChatInputProps {
  chatInput: string;
  setChatInput: (val: string) => void;
  isReplying: boolean;
  onSend: (textToSend?: string) => void;
  placeholder?: string;
  styles: Record<string, string>;
}

export default function ChatInput({
  chatInput,
  setChatInput,
  isReplying,
  onSend,
  placeholder,
  styles,
}: ChatInputProps) {
  const { t } = useLanguage();
  const p = placeholder || t('aiworkbench.chatbot.chatInputPlaceholder');
  return (
    <div className={`p-3 ${styles.cardBg} border-t ${styles.cardBorder} shrink-0`}>
      <form
        onSubmit={(e) => { e.preventDefault(); onSend(); }}
        className="flex gap-2"
      >
        <input
          type="text"
          value={chatInput}
          onChange={(e) => setChatInput(e.target.value)}
          disabled={isReplying}
          className={`flex-1 p-2 ${styles.inputBg} border ${styles.cardBorder} rounded-xl outline-none text-[11px] focus:${styles.cardBg} focus:ring-1 focus:ring-blue-500 focus:border-blue-500 transition-all`}
          placeholder={p}
        />
        <button
          type="submit"
          disabled={!chatInput.trim() || isReplying}
          className={`px-3 py-2 ${styles.accentBg} text-white rounded-xl font-bold transition-all flex items-center justify-center cursor-pointer shrink-0 ${
            !chatInput.trim() || isReplying ? 'opacity-40 cursor-not-allowed' : styles.accentHover
          }`}
        >
          <Icon name="Send" size={12} />
        </button>
      </form>
    </div>
  );
}
