/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * ChatPanel — OAG (Ontology-Augmented Generation) chat panel
 * PMO-14 T6-4: OAG 8-step progress bar + message metadata card + SSE streaming
 */

import React, { useRef, useState, useEffect, useCallback } from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';
import OntologyContextPanel from './OntologyContextPanel';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Thread Types (T7-1) ────────────────────────────────────────────

interface ChatThread {
  id: string;
  name: string;
  messages: ChatMessage[];
  createdAt: string;
}

// ── OAG Progress Types ──────────────────────────────────────────────

interface OagStepInfo {
  step: number;
  label: string;
  icon: string;
  status: 'pending' | 'active' | 'completed' | 'error';
  detail?: string;
}

// ── ChatMessage (extended with optional metadata) ───────────────────

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
  metadata?: MessageMetadata;
}

// ── Message Metadata Types ──────────────────────────────────────────

interface MessageMetadata {
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  confidence?: number;
  modelName?: string;
  guardrailStatus?: 'passed' | 'blocked' | 'warning';
}

// ── OAG Progress Bar Sub-component ──────────────────────────────────

function OagProgressBar({
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

// ── Message Metadata Card Sub-component ─────────────────────────────

function MessageMetadataCard({
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

// ── Streaming Message Bubble ────────────────────────────────────────

function StreamingBubble({
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

  // ── OAG 8 steps (component-scoped for t() access) ──────────────
  const OAG_8_STEPS: { label: string; icon: string }[] = [
    { label: t('aiworkbench.chatbot.oagStepIntent'), icon: 'Brain' },
    { label: t('aiworkbench.chatbot.oagStepEntity'), icon: 'Search' },
    { label: t('aiworkbench.chatbot.oagStepOntology'), icon: 'GitBranch' },
    { label: t('aiworkbench.chatbot.oagStepRecall'), icon: 'Database' },
    { label: t('aiworkbench.chatbot.oagStepSecurity'), icon: 'Shield' },
    { label: t('aiworkbench.chatbot.oagStepAssembly'), icon: 'Puzzle' },
    { label: t('aiworkbench.chatbot.oagStepGenerate'), icon: 'Cpu' },
    { label: t('aiworkbench.chatbot.oagStepDeliver'), icon: 'CheckCircle' },
  ];

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

  // ── Local state for OAG progress + SSE streaming ────────────────
  const [oagSteps, setOagSteps] = useState<OagStepInfo[]>(
    OAG_8_STEPS.map((s, i) => ({
      step: i + 1,
      label: s.label,
      icon: s.icon,
      status: 'pending' as const,
    })),
  );
  const [streamingContent, setStreamingContent] = useState('');
  const [streamingMetadata, setStreamingMetadata] = useState<MessageMetadata | null>(null);
  const [sseConnected, setSseConnected] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);
  const onSendRef = useRef(onSend);
  onSendRef.current = onSend;

  // ── Scroll to bottom when messages/streaming change ──────────────
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages, isReplying, streamingContent, oagSteps]);

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

  // ── Reset OAG + streaming state when replying starts/stops ───────
  useEffect(() => {
    if (isReplying) {
      // Reset OAG steps to pending when a new reply begins
      setOagSteps(
        OAG_8_STEPS.map((s, i) => ({
          step: i + 1,
          label: s.label,
          icon: s.icon,
          status: 'pending' as const,
        })),
      );
      setStreamingContent('');
      setStreamingMetadata(null);
    } else {
      // Clean up when reply stops
      setStreamingContent('');
      setStreamingMetadata(null);
      // Mark all remaining pending steps as completed
      setOagSteps(prev =>
        prev.map(s =>
          s.status === 'active' || s.status === 'pending'
            ? { ...s, status: 'completed' as const }
            : s,
        ),
      );
    }
  }, [isReplying, OAG_8_STEPS]);

  // ── SSE connection management ────────────────────────────────────
  const disconnectSSE = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
      setSseConnected(false);
    }
  }, []);

  // Connect to SSE when isReplying becomes true
  useEffect(() => {
    if (!isReplying) {
      disconnectSSE();
      return;
    }

    // Check for the last user message to use as query
    const lastUserMsg = [...chatMessages].reverse().find(m => m.sender === 'user');
    if (!lastUserMsg) return;

    const query = encodeURIComponent(lastUserMsg.content);
    const agentId = encodeURIComponent(activeChatbot.id);
    const role = encodeURIComponent(activeUserRole);
    const dataset = encodeURIComponent(activeContextDataset);

    const sseUrl = `/api/v1/chat/stream?query=${query}&agentId=${agentId}&role=${role}&dataset=${dataset}`;

    let localSteps: OagStepInfo[] = OAG_8_STEPS.map((s, i) => ({
      step: i + 1,
      label: s.label,
      icon: s.icon,
      status: 'pending' as const,
    }));
    let content = '';
    let metadata: MessageMetadata | null = null;
    let completed = false;

    const es = new EventSource(sseUrl);
    eventSourceRef.current = es;
    setSseConnected(true);

    es.addEventListener('oag_step', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data);
        localSteps = localSteps.map(s => {
          if (s.step < data.step && data.status !== 'error') {
            return { ...s, status: 'completed' as const };
          }
          if (s.step === data.step) {
            return {
              ...s,
              status: data.status,
              detail: data.detail || s.detail,
            };
          }
          return s;
        });
        setOagSteps([...localSteps]);
      } catch {
        // Ignore parse errors for non-JSON events
      }
    });

    es.addEventListener('token', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data);
        content += data.text || '';
        setStreamingContent(content);
      } catch {
        // Fallback: raw text in data field
        content += e.data;
        setStreamingContent(content);
      }
    });

    es.addEventListener('metadata', (e: MessageEvent) => {
      try {
        metadata = JSON.parse(e.data);
        setStreamingMetadata(metadata);
      } catch {
        // Ignore
      }
    });

    es.addEventListener('done', () => {
      completed = true;
      localSteps = localSteps.map(s => ({
        ...s,
        status: s.status === 'error' ? ('error' as const) : ('completed' as const),
      }));
      setOagSteps([...localSteps]);
      disconnectSSE();
      setSseConnected(false);
      setStreamingMetadata(metadata);
    });

    es.addEventListener('error', () => {
      if (!completed) {
        localSteps = localSteps.map(s =>
          s.status === 'active' ? { ...s, status: 'error' as const, detail: t('aiworkbench.chatbot.sseDisconnected') } : s,
        );
        setOagSteps([...localSteps]);
      }
      disconnectSSE();
    });

    // Generic message handler for servers that don't use named events
    es.onmessage = (e: MessageEvent) => {
      if (e.data === '[DONE]' || e.data === 'done') {
        if (!completed) {
          completed = true;
          localSteps = localSteps.map(s => ({
            ...s,
            status: s.status === 'error' ? ('error' as const) : ('completed' as const),
          }));
          setOagSteps([...localSteps]);
          disconnectSSE();
        }
        return;
      }
      try {
        const data = JSON.parse(e.data);
        if (data.type === 'oag_step') {
          localSteps = localSteps.map(s => {
            if (s.step < data.step && data.status !== 'error') {
              return { ...s, status: 'completed' as const };
            }
            if (s.step === data.step) {
              return { ...s, status: data.status, detail: data.detail || s.detail };
            }
            return s;
          });
          setOagSteps([...localSteps]);
        } else if (data.type === 'token') {
          content += data.text || '';
          setStreamingContent(content);
        } else if (data.type === 'metadata') {
          metadata = data;
          setStreamingMetadata(metadata);
        }
      } catch {
        // Raw token text
        content += e.data;
        setStreamingContent(content);
      }
    };

    return () => {
      disconnectSSE();
    };
  }, [isReplying, chatMessages, activeChatbot.id, activeUserRole, activeContextDataset, disconnectSSE, OAG_8_STEPS]);

  // ── Cleanup on unmount ────────────────────────────────────────────
  useEffect(() => {
    return () => {
      disconnectSSE();
    };
  }, [disconnectSSE]);

  // ── Simulated OAG progress when SSE isn't connected ───────────────
  useEffect(() => {
    if (!isReplying || sseConnected) return;

    const stepLabels = OAG_8_STEPS.map(s => s.label);
    let currentStep = 0;

    const interval = setInterval(() => {
      if (currentStep >= 8) {
        clearInterval(interval);
        return;
      }

      setOagSteps(prev =>
        prev.map((s, i) => {
          if (i < currentStep) return { ...s, status: 'completed' as const };
          if (i === currentStep) return { ...s, status: 'active' as const, detail: t('aiworkbench.chatbot.oagSimExecuting').replace('{label}', stepLabels[i]) };
          return { ...s, status: 'pending' as const };
        }),
      );

      currentStep++;

      if (currentStep >= 7) {
        setStreamingContent(t('aiworkbench.chatbot.oagSimGenerating'));
      }
    }, 400);

    return () => clearInterval(interval);
  }, [isReplying, sseConnected, OAG_8_STEPS, t]);

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

  // ── Export handler (T7-2) ───────────────────────────────────────
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
        t('aiworkbench.chatbot.exportAgent').replace('{name}', activeChatbot.name),
        '',
      ];
      for (const msg of msgs) {
        const role = msg.sender === 'user' ? t('aiworkbench.chatbot.exportRoleUser') : msg.sender === 'agent' ? t('aiworkbench.chatbot.exportRoleAgent') : t('aiworkbench.chatbot.exportRoleSystem');
        lines.push(`### ${role} — ${msg.timestamp}`);
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
      filename = `chat-export-${activeChatbot.name}-${Date.now()}.md`;
      mimeType = 'text/markdown;charset=utf-8';
    } else {
      const data = {
        exportedAt: new Date().toISOString(),
        agent: { id: activeChatbot.id, name: activeChatbot.name },
        messages: msgs.map(m => ({
          id: m.id,
          sender: m.sender,
          content: m.content,
          timestamp: m.timestamp,
          thinkingTrace: m.thinkingTrace,
          actionProposal: m.actionProposal,
          metadata: m.metadata,
        })),
      };
      content = '\uFEFF' + JSON.stringify(data, null, 2);
      filename = `chat-export-${activeChatbot.name}-${Date.now()}.json`;
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
  }, [chatMessages, activeThread, threads, activeChatbot, showToast, t]);

  // ── Derive display messages (T7-1: support thread view) ────────
  const baseMessages = activeThread ? activeThread.messages : chatMessages;

  // ── Attach metadata to the last agent message when streaming completes ──
  const displayMessages = baseMessages.map(msg => {
    const isLastAgentMsg =
      msg.sender === 'agent' &&
      msg.id === [...baseMessages].reverse().find(m => m.sender === 'agent')?.id;

    if (isLastAgentMsg && streamingMetadata && !msg.metadata) {
      return { ...msg, metadata: streamingMetadata };
    }
    return msg;
  });

  return (
    <div className={`w-full md:w-110 flex flex-col h-full ${styles.inputBg} border-l ${styles.cardBorder}`}>
      {/* Sandbox Setup Panel Header */}
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
          {/* Thread toggle (T7-1) */}
          <button
            onClick={() => setShowThreadPanel(!showThreadPanel)}
            className={`p-1 ${showThreadPanel ? styles.accentText : styles.cardTextMuted} rounded cursor-pointer transition-colors`}
            title={t('aiworkbench.chatbot.threadPanel')}
          >
            <Icon name="MessageSquare" size={11} />
          </button>
          {/* Export dropdown (T7-2) */}
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

      {/* Thread indicator — when viewing a saved thread (T7-1) */}
      {activeThread && (
        <div className={`px-3 py-1.5 ${styles.accentBg}/10 border-b ${styles.cardBorder} flex items-center justify-between text-[10px]`}>
          <span className={`${styles.accentText} font-bold flex items-center gap-1`}>
            <Icon name="Bookmark" size={10} />
            <span>{t('aiworkbench.chatbot.savedThreadViewing').replace('{name}', activeThread.name)}</span>
            <span className={styles.cardTextMuted}>{t('aiworkbench.chatbot.savedThreadMsgCount').replace('{count}', String(activeThread.messages.length))}</span>
          </span>
          <button
            onClick={() => setActiveThreadId(null)}
            className={`${styles.cardTextMuted} hover:${styles.accentText} transition-colors text-[9px] font-bold`}
          >
            {t('aiworkbench.chatbot.backToLive')}
          </button>
        </div>
      )}

      {/* Thread panel sidebar (T7-1) */}
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
          {/* Thread list */}
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
              <option value="EXTERNAL_CONTRACTOR">
                {t('aiworkbench.chatbot.roleContractor')}
              </option>
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
              <option value="only_flights">
                {t('aiworkbench.chatbot.scopeFlightOnly')}
              </option>
            </select>
          </div>
        </div>
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
      <div className={`flex-1 overflow-y-auto p-4 space-y-4 ${styles.appBg}/50`}>
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

        {/* Streaming content during reply */}
        {isReplying && streamingContent && (
          <StreamingBubble
            content={streamingContent}
            agentName={activeChatbot.name}
            agentAvatar={activeChatbot.avatar}
            styles={styles}
          />
        )}

        {/* OAG Progress Bar during reply */}
        {isReplying && (
          <OagProgressBar steps={oagSteps} styles={styles} />
        )}

        {/* SSE connection badge */}
        {isReplying && sseConnected && (
          <div className="flex items-center gap-1 font-mono text-[8px] text-emerald-500">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            <span>{t('aiworkbench.chatbot.sseConnected')}</span>
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
  activeUserRole: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR';
  setActiveUserRole: (role: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR') => void;
  activeContextDataset: string;
  setActiveContextDataset: (ds: string) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  styles: Record<string, string>;
}
