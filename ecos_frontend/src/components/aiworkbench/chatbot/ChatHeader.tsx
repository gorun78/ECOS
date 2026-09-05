/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * ChatHeader — Agent 选择器 + 会话标题 + 操作按钮
 * PMO-19: 调用 AgentSelector 而非重新实现
 */

import React, { useState, useCallback } from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import AgentSelector from './AgentSelector';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Types ──────────────────────────────────────────────────────────

interface ChatThread {
  id: string;
  name: string;
  messages: { sender: string; content: string; timestamp: string; thinkingTrace?: string[]; metadata?: any; id?: string }[];
  createdAt: string;
}

interface ChatMessage {
  id: string;
  sender: 'user' | 'agent' | 'system';
  content: string;
  timestamp: string;
  thinkingTrace?: string[];
  actionProposal?: {
    id?: string;
    actionId: string;
    actionName: string;
    payload: Record<string, string>;
    status: 'pending' | 'approved' | 'rejected';
  };
  metadata?: any;
}

interface ChatHeaderProps {
  agents: AIPAgent[];
  selectedAgentId: string;
  onSelectAgent: (id: string) => void;
  onStartCreate: () => void;
  activeChatbot: AIPAgent | undefined;
  chatbotVersion: string;
  chatMessages: ChatMessage[];
  activeUserRole: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR';
  setActiveUserRole: (role: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR') => void;
  activeContextDataset: string;
  setActiveContextDataset: (ds: string) => void;
  onResetChat: () => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  styles: Record<string, string>;
}

// ── Main ChatHeader Component ──────────────────────────────────────

export default function ChatHeader({
  agents,
  selectedAgentId,
  onSelectAgent,
  onStartCreate,
  activeChatbot,
  chatbotVersion,
  chatMessages,
  activeUserRole,
  setActiveUserRole,
  activeContextDataset,
  setActiveContextDataset,
  onResetChat,
  showToast,
  styles,
}: ChatHeaderProps) {
  const { t } = useLanguage();

  // ── Thread state (moved from ChatPanel) ────────────────────────
  const [threads, setThreads] = useState<ChatThread[]>([
    { id: 'thread-1', name: t('aiworkbench.chatbot.welcomeSelectAgent'), messages: [], createdAt: new Date().toISOString() },
  ]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [showThreadPanel, setShowThreadPanel] = useState(false);
  const [newThreadName, setNewThreadName] = useState('');
  const [showNewThreadInput, setShowNewThreadInput] = useState(false);
  const [exportDropdownOpen, setExportDropdownOpen] = useState(false);

  const activeThread = activeThreadId ? threads.find(t => t.id === activeThreadId) : null;

  // ── Thread actions ──────────────────────────────────────────────
  const handleSaveThread = useCallback(() => {
    if (chatMessages.length === 0) return;
    const threadName = `${t('aiworkbench.chatbot.threadSaveBtn')} ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`;
    const newThread: ChatThread = {
      id: `thread-${Date.now()}`,
      name: threadName,
      messages: [...chatMessages],
      createdAt: new Date().toISOString(),
    };
    setThreads(prev => [...prev, newThread]);
    setActiveThreadId(newThread.id);
    setShowThreadPanel(false);
    showToast?.('success', t('aiworkbench.chatbot.threadSaved').replace('{name}', threadName));
  }, [chatMessages, showToast, t]);

  const handleNewThread = useCallback(() => {
    const name = newThreadName.trim() || `${t('aiworkbench.chatbot.newThread')} ${threads.length + 1}`;
    const newThread: ChatThread = {
      id: `thread-${Date.now()}`,
      name,
      messages: [],
      createdAt: new Date().toISOString(),
    };
    setThreads(prev => [...prev, newThread]);
    setNewThreadName('');
    setShowNewThreadInput(false);
    showToast?.('success', t('aiworkbench.chatbot.threadCreated').replace('{name}', name));
  }, [newThreadName, threads.length, showToast, t]);

  const handleDeleteThread = useCallback((threadId: string) => {
    if (threadId === 'thread-1') return;
    setThreads(prev => prev.filter(t => t.id !== threadId));
    if (activeThreadId === threadId) setActiveThreadId(null);
  }, [activeThreadId]);

  const handleSwitchThread = useCallback((threadId: string) => {
    if (activeThreadId === threadId) {
      setActiveThreadId(null);
    } else {
      setActiveThreadId(threadId);
    }
    setShowThreadPanel(false);
  }, [activeThreadId]);

  // ── Export handler ──────────────────────────────────────────────
  const handleExport = useCallback((format: 'md' | 'json') => {
    const msgs = activeThread ? activeThread.messages : chatMessages;
    if (msgs.length === 0) {
      showToast?.('info', t('aiworkbench.chatbot.noMessages'));
      return;
    }
    let content = '';
    let filename = '';
    let mimeType = '';

    if (format === 'md') {
      const lines: string[] = [
        t('aiworkbench.chatbot.exportHeader'),
        t('aiworkbench.chatbot.exportTime').replace('{time}', new Date().toLocaleString('zh-CN')),
        t('aiworkbench.chatbot.exportAgent').replace('{name}', activeChatbot?.name || ''),
        '',
      ];
      for (const msg of msgs) {
        const roleLabel = msg.sender === 'user' ? t('aiworkbench.chatbot.exportRoleUser') : msg.sender === 'agent' ? t('aiworkbench.chatbot.exportRoleAgent') : t('aiworkbench.chatbot.exportRoleSystem');
        lines.push(`### ${roleLabel} — ${msg.timestamp}`);
        lines.push('');
        lines.push(msg.content);
        lines.push('');
        if (msg.thinkingTrace && msg.thinkingTrace.length > 0) {
          lines.push('<details>');
          lines.push(`<summary>${t('aiworkbench.chatbot.exportThinking')}</summary>`);
          lines.push('');
          for (const tr of msg.thinkingTrace) {
            lines.push(`- ${tr}`);
          }
          lines.push('');
          lines.push('</details>');
          lines.push('');
        }
        if (msg.metadata) {
          lines.push('> **' + t('aiworkbench.chatbot.metadataTitle') + '**: ' + JSON.stringify(msg.metadata));
          lines.push('');
        }
      }
      content = '\uFEFF' + lines.join('\n');
      filename = `chat-export-${activeChatbot?.name || 'chat'}-${Date.now()}.md`;
      mimeType = 'text/markdown;charset=utf-8';
    } else {
      const data = {
        exportedAt: new Date().toISOString(),
        agent: { id: activeChatbot?.id || '', name: activeChatbot?.name || '' },
        messages: msgs.map(m => ({
          id: m.id,
          sender: m.sender,
          content: m.content,
          timestamp: m.timestamp,
          thinkingTrace: m.thinkingTrace,
          metadata: m.metadata,
        })),
      };
      content = '\uFEFF' + JSON.stringify(data, null, 2);
      filename = `chat-export-${activeChatbot?.name || 'chat'}-${Date.now()}.json`;
      mimeType = 'application/json;charset=utf-8';
    }

    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    setExportDropdownOpen(false);
    showToast?.('success', t('aiworkbench.chatbot.exportSuccess').replace('{format}', format.toUpperCase()));
  }, [chatMessages, activeThread, activeChatbot, showToast, t]);

  return (
    <>
      {/* AgentSelector (stable left sidebar) */}
      <AgentSelector
        agents={agents}
        selectedAgentId={selectedAgentId}
        onSelectAgent={onSelectAgent}
        onStartCreate={onStartCreate}
        styles={styles}
      />

      {/* Chat sandbox control area — shown when agent is selected */}
      {activeChatbot && (
        <div className={`flex flex-col shrink-0`}>
          {/* Sandbox header bar */}
          <div
            className={`p-3 ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between shrink-0`}
          >
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse" />
              <span className={`font-extrabold ${styles.cardText}`}>
                {t('aiworkbench.chatbot.sandboxTitle')}
              </span>
            </div>
            <div className="flex items-center gap-1 relative">
              <button
                onClick={() => setShowThreadPanel(!showThreadPanel)}
                className={`p-1 ${showThreadPanel ? styles.accentText : styles.cardTextMuted} rounded cursor-pointer transition-colors`}
                title={t('aiworkbench.chatbot.threadPanel')}
              >
                <Icon name="MessageSquare" size={11} />
              </button>
              <div className="relative">
                <button
                  onClick={() => setExportDropdownOpen(!exportDropdownOpen)}
                  className={`p-1 ${styles.cardTextMuted} rounded cursor-pointer transition-colors`}
                  title={t('aiworkbench.chatbot.exportTitle')}
                >
                  <Icon name="Download" size={11} />
                </button>
                {exportDropdownOpen && (
                  <div className={`absolute right-0 top-full mt-1 z-50 ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-lg py-1 min-w-[120px]`}>
                    <button
                      onClick={() => handleExport('md')}
                      className={`w-full text-left px-3 py-1.5 text-[10px] ${styles.cardText} hover:${styles.inputBg} flex items-center gap-1.5`}
                    >
                      <Icon name="FileText" size={10} />
                      <span>{t('aiworkbench.chatbot.exportMd')}</span>
                    </button>
                    <button
                      onClick={() => handleExport('json')}
                      className={`w-full text-left px-3 py-1.5 text-[10px] ${styles.cardText} hover:${styles.inputBg} flex items-center gap-1.5`}
                    >
                      <Icon name="Code" size={10} />
                      <span>{t('aiworkbench.chatbot.exportJson')}</span>
                    </button>
                  </div>
                )}
              </div>
              <button
                onClick={onResetChat}
                className={`p-1 ${styles.cardTextMuted} rounded cursor-pointer transition-colors`}
                title={t('aiworkbench.chatbot.clearHistory')}
              >
                <Icon name="RotateCw" size={11} />
              </button>
            </div>
          </div>

          {/* Thread indicator */}
          {activeThread && (
            <div className={`px-3 py-1.5 ${styles.accentBg}/10 border-b ${styles.cardBorder} flex items-center justify-between text-[10px]`}>
              <span className={`${styles.accentText} font-bold flex items-center gap-1`}>
                <Icon name="Bookmark" size={10} />
                <span>{t('aiworkbench.chatbot.savedThreadViewing').replace('{name}', activeThread.name)}</span>
                <span className={styles.cardTextMuted}>
                  ({t('aiworkbench.chatbot.savedThreadMsgCount').replace('{count}', String(activeThread.messages.length))})
                </span>
              </span>
              <button
                onClick={() => setActiveThreadId(null)}
                className={`${styles.cardTextMuted} hover:${styles.accentText} transition-colors text-[9px] font-bold`}
              >
                {t('aiworkbench.chatbot.backToLive')}
              </button>
            </div>
          )}

          {/* Thread panel */}
          {showThreadPanel && (
            <div className={`border-b ${styles.cardBorder} ${styles.inputBg} p-3 space-y-2 shrink-0`}>
              <div className="flex items-center justify-between">
                <span className={`font-extrabold text-[10px] ${styles.cardText} flex items-center gap-1`}>
                  <Icon name="MessagesSquare" size={11} className={styles.accentText} />
                  <span>{t('aiworkbench.chatbot.threadPanelTitle').replace('{count}', String(threads.length))}</span>
                </span>
                <div className="flex items-center gap-1">
                  {showNewThreadInput ? (
                    <form
                      onSubmit={(e) => { e.preventDefault(); handleNewThread(); }}
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
                        onClick={handleSaveThread}
                        className={`p-1 ${styles.cardTextMuted} hover:${styles.accentText} rounded cursor-pointer text-[9px] font-bold`}
                        title={t('aiworkbench.chatbot.saveCurrentThread')}
                        disabled={chatMessages.length === 0}
                      >
                        <Icon name="Save" size={10} />
                      </button>
                    </>
                  )}
                </div>
              </div>
              <div className="space-y-1 max-h-40 overflow-y-auto">
                {threads.map(thread => (
                  <div
                    key={thread.id}
                    onClick={() => handleSwitchThread(thread.id)}
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
                        onClick={(e) => { e.stopPropagation(); handleDeleteThread(thread.id); }}
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
          )}

          {/* Sandbox context modifier */}
          <div
            className={`p-3 ${styles.inputBg} border-b ${styles.cardBorder} space-y-2 shrink-0`}
          >
            <div className="grid grid-cols-2 gap-2 text-[9px] font-sans">
              <div className="space-y-1">
                <label
                  className={`${styles.cardTextMuted} font-extrabold flex items-center gap-1`}
                >
                  <Icon name="User" size={10} className={styles.accentText} />
                  <span>{t('aiworkbench.chatbot.sandboxRole')}</span>
                </label>
                <select
                  value={activeUserRole}
                  onChange={(e) => {
                    setActiveUserRole(e.target.value as any);
                    showToast?.(
                      'info',
                      t('aiworkbench.chatbot.toastRoleSwitch').replace('{role}',
                        e.target.value === 'AOC_DIRECTOR'
                          ? t('aiworkbench.chatbot.roleDirectorShort')
                          : t('aiworkbench.chatbot.roleContractorShort')
                      ),
                    );
                  }}
                  className={`w-full p-1 ${styles.cardBg} border ${styles.cardBorder} rounded font-bold text-[10px] outline-none`}
                >
                  <option value="AOC_DIRECTOR">{t('aiworkbench.chatbot.roleDirector')}</option>
                  <option value="EXTERNAL_CONTRACTOR">{t('aiworkbench.chatbot.roleContractor')}</option>
                </select>
              </div>

              <div className="space-y-1">
                <label
                  className={`${styles.cardTextMuted} font-extrabold flex items-center gap-1`}
                >
                  <Icon name="Database" size={10} className={styles.accentText} />
                  <span>{t('aiworkbench.chatbot.sandboxScope')}</span>
                </label>
                <select
                  value={activeContextDataset}
                  onChange={(e) => setActiveContextDataset(e.target.value)}
                  className={`w-full p-1 ${styles.cardBg} border ${styles.cardBorder} rounded font-bold text-[10px] outline-none`}
                >
                  <option value="all_ontology">{t('aiworkbench.chatbot.scopeAll')}</option>
                  <option value="only_flights">{t('aiworkbench.chatbot.scopeFlightOnly')}</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

// Export activeThread info for ChatPanel to consume
export type { ChatThread, ChatMessage as ChatHeaderMessage };
