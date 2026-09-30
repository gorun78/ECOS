/**
 * ChatThreadPanel — 会话线程面板（T7-1：新建 / 保存 / 切换 / 删除线程）
 *
 * 从 `components/aiworkbench/chatbot/ChatPanel.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 线程列表条件类名、表单交互与 i18n key 逐字保留；state 仍由 ChatPanel 持有。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ChatThread } from './chatbotTypes';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface ChatThreadPanelProps {
  threads: ChatThread[];
  activeThreadId: string | null;
  showNewThreadInput: boolean;
  newThreadName: string;
  saveDisabled: boolean;
  setShowNewThreadInput: (value: boolean) => void;
  setNewThreadName: (value: string) => void;
  onNewThread: () => void;
  onSaveThread: () => void;
  onSwitchThread: (threadId: string) => void;
  onDeleteThread: (threadId: string) => void;
  styles: Record<string, string>;
}

export default function ChatThreadPanel({
  threads,
  activeThreadId,
  showNewThreadInput,
  newThreadName,
  saveDisabled,
  setShowNewThreadInput,
  setNewThreadName,
  onNewThread,
  onSaveThread,
  onSwitchThread,
  onDeleteThread,
  styles,
}: ChatThreadPanelProps) {
  const { t } = useLanguage();

  return (
    <div className={`border-b ${styles.cardBorder} ${styles.inputBg} p-3 space-y-2 shrink-0`}>
      <div className="flex items-center justify-between">
        <span className={`font-extrabold text-[10px] ${styles.cardText} flex items-center gap-1`}>
          <Icon name="MessagesSquare" size={11} className={styles.accentText} />
          <span>{t('aiworkbench.chatbot.threadPanelTitle').replace('{count}', String(threads.length))}</span>
        </span>
        <div className="flex items-center gap-1">
          {showNewThreadInput ? (
            <form
              onSubmit={(e) => { e.preventDefault(); onNewThread(); }}
              className="flex items-center gap-1"
            >
              <input
                type="text"
                value={newThreadName}
                onChange={(e) => setNewThreadName(e.target.value)}
                placeholder={t('aiworkbench.chatbot.newThreadPlaceholder')}
                className={`w-24 p-0.5 text-[9px] ${styles.inputBg} border ${styles.cardBorder} rounded outline-none`}
                autoFocus
              />
              <button type="submit" className={`p-0.5 ${styles.accentText} cursor-pointer`}>
                <Icon name="Check" size={10} />
              </button>
              <button
                type="button"
                onClick={() => { setShowNewThreadInput(false); setNewThreadName(''); }}
                className={`p-0.5 ${styles.cardTextMuted} cursor-pointer`}
              >
                <Icon name="X" size={10} />
              </button>
            </form>
          ) : (
            <>
              <button
                onClick={() => setShowNewThreadInput(true)}
                className={`p-1 ${styles.accentText} hover:${styles.accentHover} rounded cursor-pointer text-[9px] font-bold`}
                title={t('aiworkbench.chatbot.newThread')}
              >
                <Icon name="Plus" size={10} />
              </button>
              <button
                onClick={onSaveThread}
                className={`p-1 ${styles.cardTextMuted} hover:${styles.accentText} rounded cursor-pointer text-[9px] font-bold`}
                title={t('aiworkbench.chatbot.saveCurrentThread')}
                disabled={saveDisabled}
              >
                <Icon name="Save" size={10} />
              </button>
            </>
          )}
        </div>
      </div>
      {/* Thread list */}
      <div className="space-y-1 max-h-40 overflow-y-auto">
        {threads.map(thread => (
          <div
            key={thread.id}
            onClick={() => onSwitchThread(thread.id)}
            className={`p-1.5 rounded cursor-pointer transition-all flex items-center justify-between text-[10px] ${
              activeThreadId === thread.id
                ? `${styles.accentBg}/20 ${styles.accentText} font-bold`
                : thread.id === 'thread-1' && !activeThreadId
                ? `${styles.accentBg}/10 ${styles.cardText} font-bold`
                : `${styles.cardTextMuted} hover:${styles.inputBg}`
            }`}
          >
            <div className="flex items-center gap-1.5 min-w-0">
              <Icon
                name={thread.id === 'thread-1' ? 'Radio' : 'Bookmark'}
                size={9}
                className={activeThreadId === thread.id ? styles.accentText : styles.cardTextMuted}
              />
              <span className="truncate">{thread.name}</span>
              <span className={`text-[8px] ${styles.cardTextMuted} font-mono`}>
                {t('aiworkbench.chatbot.msgCount').replace('{count}', String(thread.messages.length))}
              </span>
            </div>
            {thread.id !== 'thread-1' && (
              <button
                onClick={(e) => { e.stopPropagation(); onDeleteThread(thread.id); }}
                className={`p-0.5 ${styles.cardTextMuted} hover:text-rose-500 cursor-pointer`}
                title={t('aiworkbench.chatbot.deleteThread')}
              >
                <Icon name="Trash2" size={9} />
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
