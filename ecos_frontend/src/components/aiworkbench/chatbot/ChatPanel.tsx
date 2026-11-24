/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * ChatPanel — Chatbot Studio 对话面板
 *
 * §4.5（2026-10-13 全页检视报告 E 组）整改：
 * - 原 EventSource `${API_BASE}/v1/chat/stream` 指向后端不存在的 GET 端点（ghost），
 *   连同 oag_step/token/metadata 假事件通道一并移除；
 * - 原「SSE 未连接时 setInterval 模拟 OAG 8 步进度」(oagSimExecuting/oagSimGenerating)
 *   整段删除——回复唯一真实来源是父级 ChatbotStudioView 经 POST /api/v1/agent-loop/chat
 *   写入的 chatMessages；引擎处理期间只显示诚实的 loading 指示。
 *
 * H6-T4 组件行数治理：元数据卡 / 线程面板 / 导出逻辑 / 类型声明保持兄弟文件抽取
 * （MessageMetadataCard.tsx、ChatThreadPanel.tsx、chatExport.ts、chatbotTypes.ts）。
 */

import React, { useRef, useState, useEffect, useCallback } from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { fetchRoles } from '../../../api/iamClient';
import { getUserIdentity } from '../../../services/auth';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';
import OntologyContextPanel from './OntologyContextPanel';
import MessageMetadataCard from './MessageMetadataCard';
import ChatThreadPanel from './ChatThreadPanel';
import { buildChatExport, downloadChatExport } from './chatExport';
import type { ChatMessage, ChatThread } from './chatbotTypes';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Main ChatPanel Component ────────────────────────────────────────

export default function ChatPanel({
  activeChatbot,
  chatMessages,
  chatInput,
  setChatInput,
  isReplying,
  onSend,
  onProposalConsent,
  onResetChat,
  activeUserRole,
  setActiveUserRole,
  activeContextDataset,
  setActiveContextDataset,
  showToast,
  styles,
}: ChatPanelProps) {
  const { t } = useLanguage();
  const chatEndRef = useRef<HTMLDivElement>(null);

  // ── 沙箱角色候选：真值 GET /api/v1/system/roles（禁写死演示角色） ──
  const [roleList, setRoleList] = useState<{ code: string; name: string }[] | null>(null);
  const [roleError, setRoleError] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false;
    fetchRoles()
      .then((res) => {
        if (cancelled) return;
        const rows: any[] = Array.isArray(res?.data) ? res.data : [];
        setRoleList(rows
          .filter((r) => r && (r.roleCode || r.roleId))
          .map((r) => ({ code: String(r.roleCode || r.roleId), name: String(r.roleName || r.roleCode || r.roleId) })));
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setRoleList([]);
        setRoleError(e instanceof Error && e.message ? e.message : String(e));
      });
    return () => { cancelled = true; };
  }, []);
  /** 角色候选：真实角色列表 + 当前登录身份（列表未回前至少能显示自身），去重保序。 */
  const roleOptions = (() => {
    const opts = [...(roleList ?? [])];
    const mine = getUserIdentity()?.roles ?? [];
    for (const r of mine) {
      if (!opts.some(o => o.code === r)) opts.push({ code: r, name: r });
    }
    if (opts.length === 0) opts.push({ code: activeUserRole, name: activeUserRole });
    return opts;
  })();

  /** 数据上下文候选：已绑定本体对象（metadata.ontologyBinding.objects），空则不列二级项。 */
  const boundObjects = activeChatbot.ontologyBinding?.objects ?? [];

  // ── Thread state (T7-1) ──────────────────────────────────────────
  const [threads, setThreads] = useState<ChatThread[]>([
    { id: 'thread-1', name: t('aiworkbench.chatbot.defaultThread'), messages: [], createdAt: new Date().toISOString() },
  ]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [showThreadPanel, setShowThreadPanel] = useState(false);
  const [newThreadName, setNewThreadName] = useState('');
  const [showNewThreadInput, setShowNewThreadInput] = useState(false);
  const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
  const prevMsgCountRef = useRef(0);

  // ── Scroll to bottom when messages change ──────────────
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages, isReplying]);

  // ── Sync parent chatMessages to active thread (T7-1) ─────────────
  useEffect(() => {
    if (activeThreadId) return; // Don't sync when viewing a saved thread
    if (chatMessages.length <= prevMsgCountRef.current) {
      prevMsgCountRef.current = chatMessages.length;
      return;
    }
    // New messages arrived from parent — append to thread-1 (live)
    const newMsgs = chatMessages.slice(prevMsgCountRef.current);
    prevMsgCountRef.current = chatMessages.length;
    if (newMsgs.length === 0) return;
    setThreads(prev =>
      prev.map(t =>
        t.id === 'thread-1'
          ? { ...t, messages: [...t.messages, ...newMsgs] }
          : t,
      ),
    );
  }, [chatMessages, activeThreadId]);

  // ── Active thread derivation (used by handlers + display) ──────
  const activeThread = activeThreadId ? threads.find(t => t.id === activeThreadId) : null;

  // ── Thread actions (T7-1) ───────────────────────────────────────
  const handleSaveThread = useCallback(() => {
    if (chatMessages.length === 0) return;
    const threadName = t('aiworkbench.chatbot.threadSavePrefix') + ' ' + new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
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
    const name = newThreadName.trim() || t('aiworkbench.chatbot.newThread') + ' ' + (threads.length + 1);
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

  // ── Export handler (T7-2) — 组装/下载逻辑见 ./chatExport ────────
  const handleExport = useCallback((format: 'md' | 'json') => {
    const msgs = activeThread ? activeThread.messages : chatMessages;
    if (msgs.length === 0) {
      showToast?.('info', t('aiworkbench.chatbot.noMessages'));
      return;
    }
    downloadChatExport(buildChatExport(format, msgs, activeChatbot, t));
    setExportDropdownOpen(false);
    showToast?.('success', t('aiworkbench.chatbot.exportSuccess').replace('{format}', format.toUpperCase()));
  }, [chatMessages, activeThread, threads, activeChatbot, showToast, t]);

  // ── Derive display messages (T7-1: support thread view) ────────
  const baseMessages = activeThread ? activeThread.messages : chatMessages;
  // §4.5：原「把 SSE 流末 metadata 附加到最后一条 agent 消息」的映射已移除
  // （ghost SSE 无元数据可附）；消息自带的真实 metadata 仍由 MessageMetadataCard 渲染。
  const displayMessages = baseMessages;

  return (
    <div className={`w-full md:w-110 flex flex-col h-full ${styles.inputBg} border-l ${styles.cardBorder}`}>
      {/* Sandbox Setup Panel Header */}
      <div
        className={`p-3 ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between shrink-0`}
      >
        <div className="flex items-center gap-1.5">
          <Icon name="Bot" size={12} className={styles.accentText} />
          <span className={`font-extrabold ${styles.cardText}`}>
            {t('aiworkbench.chatbot.sandboxTitle')}
          </span>
        </div>
        <div className="flex items-center gap-1 relative">
          {/* Thread toggle (T7-1) */}
          <button type="button"
            onClick={() => setShowThreadPanel(!showThreadPanel)}
            className={`p-1 ${showThreadPanel ? styles.accentText : styles.cardTextMuted} rounded cursor-pointer transition-colors`}
            title={t('aiworkbench.chatbot.threadPanel')}
          >
            <Icon name="MessageSquare" size={11} />
          </button>
          {/* Export dropdown (T7-2) */}
          <div className="relative">
            <button type="button"
              onClick={() => setExportDropdownOpen(!exportDropdownOpen)}
              className={`p-1 ${styles.cardTextMuted} rounded cursor-pointer transition-colors`}
              title={t('aiworkbench.chatbot.exportTitle')}
            >
              <Icon name="Download" size={11} />
            </button>
            {exportDropdownOpen && (
              <div className={`absolute right-0 top-full mt-1 z-50 ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-lg py-1 min-w-[120px]`}>
                <button type="button"
                  onClick={() => handleExport('md')}
                  className={`w-full text-left px-3 py-1.5 text-[10px] ${styles.cardText} flex items-center gap-1.5`}
                >
                  <Icon name="FileText" size={10} />
                  <span>{t('aiworkbench.chatbot.exportMd')}</span>
                </button>
                <button type="button"
                  onClick={() => handleExport('json')}
                  className={`w-full text-left px-3 py-1.5 text-[10px] ${styles.cardText} flex items-center gap-1.5`}
                >
                  <Icon name="Code" size={10} />
                  <span>{t('aiworkbench.chatbot.exportJson')}</span>
                </button>
              </div>
            )}
          </div>
          <button type="button"
            onClick={onResetChat}
            className={`p-1 ${styles.cardTextMuted} rounded cursor-pointer transition-colors`}
            title={t('aiworkbench.chatbot.clearHistory')}
          >
            <Icon name="RotateCw" size={11} />
          </button>
        </div>
      </div>

      {/* Thread indicator — when viewing a saved thread (T7-1) */}
      {activeThread && (
        <div className={`px-3 py-1.5 ${styles.badgeBg} border-b ${styles.cardBorder} flex items-center justify-between text-[10px]`}>
          <span className={`${styles.accentText} font-bold flex items-center gap-1`}>
            <Icon name="Bookmark" size={10} />
            <span>{t('aiworkbench.chatbot.savedThreadViewing').replace('{name}', activeThread.name)}</span>
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.savedThreadMsgCount').replace('{count}', String(activeThread.messages.length))}</span>
          </span>
          <button type="button"
            onClick={() => setActiveThreadId(null)}
            className={`${styles.accentText} transition-colors text-[9px] font-bold`}
          >
            {t('aiworkbench.chatbot.backToLive')}
          </button>
        </div>
      )}

      {/* Thread panel sidebar (T7-1) */}
      {showThreadPanel && (
        <ChatThreadPanel
          threads={threads}
          activeThreadId={activeThreadId}
          showNewThreadInput={showNewThreadInput}
          newThreadName={newThreadName}
          saveDisabled={chatMessages.length === 0}
          setShowNewThreadInput={setShowNewThreadInput}
          setNewThreadName={setNewThreadName}
          onNewThread={handleNewThread}
          onSaveThread={handleSaveThread}
          onSwitchThread={handleSwitchThread}
          onDeleteThread={handleDeleteThread}
          styles={styles}
        />
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
                setActiveUserRole(e.target.value);
                const picked = roleOptions.find(r => r.code === e.target.value);
                showToast?.(
                  'info',
                  t('aiworkbench.chatbot.toastRoleSwitch').replace('{role}', picked?.name ?? e.target.value),
                );
              }}
              className={`w-full p-1 ${styles.cardBg} border ${styles.cardBorder} rounded font-bold text-[10px] outline-none`}
            >
              {roleOptions.map(r => (
                <option key={r.code} value={r.code}>{r.name} ({r.code})</option>
              ))}
            </select>
            {roleError && (
              <span className={`text-[8px] ${styles.dangerText} block`}>
                {t('aiworkbench.chatbot.bindingLoadFailed').replace('{message}', roleError)}
              </span>
            )}
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
              <option value="all_ontology">{t('aiworkbench.chatbot.scopeAllBound')}</option>
              {boundObjects.map(code => (
                <option key={code} value={code}>{t('aiworkbench.chatbot.scopeOnlyObject').replace('{object}', code)}</option>
              ))}
            </select>
          </div>
        </div>
        {/* 诚实标注：两项均为调试上下文，鉴权与取数由服务端按登录身份裁决 */}
        <p className={`text-[8px] ${styles.cardTextMuted} leading-normal`}>
          {t('aiworkbench.chatbot.sandboxSimulationNote')}
        </p>
      </div>

      {/* Ontology Context Panel (T7-3) — collapsible */}
      <div className={`px-3 pt-2 shrink-0`}>
        <OntologyContextPanel
          activeChatbot={activeChatbot}
          styles={styles}
          compact
        />
      </div>

      {/* Chat History */}
      <div className={`flex-1 overflow-y-auto p-4 space-y-4 ${styles.appBg}`}>
        {displayMessages.map((msg) => (
          <div key={msg.id} className="space-y-2">
            <MessageBubble
              msg={msg}
              styles={styles}
              agentName={activeChatbot.name}
              agentAvatar={activeChatbot.avatar}
              onProposalConsent={onProposalConsent}
            />
            {msg.metadata && msg.sender === 'agent' && (
              <div className="ml-9">
                <MessageMetadataCard metadata={msg.metadata} styles={styles} />
              </div>
            )}
          </div>
        ))}

        {/* §4.5：引擎处理中指示——真实通道为父级 POST /api/v1/agent-loop/chat，
            回复到达前只显示 loading，不再演出 OAG 8 步假进度 / 假流式文本 / 假在线徽标 */}
        {isReplying && (
          <div className={`flex items-center gap-2 font-mono text-[9px] ${styles.cardTextMuted}`}>
            <span className={`w-3 h-3 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
            <span>{t('aiworkbench.chatbot.chat.engineWorking')}</span>
          </div>
        )}

        <div ref={chatEndRef} />
      </div>

      {/* Chat Input */}
      <ChatInput
        chatInput={chatInput}
        setChatInput={setChatInput}
        isReplying={isReplying}
        onSend={onSend}
        styles={styles}
      />
    </div>
  );
}

// ── Props (unchanged from original — ChatbotStudioView untouched) ───

interface ChatPanelProps {
  activeChatbot: AIPAgent;
  chatMessages: ChatMessage[];
  chatInput: string;
  setChatInput: (val: string) => void;
  isReplying: boolean;
  onSend: (textToSend?: string) => void;
  onProposalConsent: (msgId: string, approved: boolean) => void;
  onResetChat: () => void;
  activeUserRole: string;
  setActiveUserRole: (role: string) => void;
  activeContextDataset: string;
  setActiveContextDataset: (ds: string) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  styles: Record<string, string>;
}
