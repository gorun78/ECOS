/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * ChatHistory — 消息列表区域，从 ChatPanel 中提取
 * PMO-19: 消息列表+滚动加载+时间戳+MessageBubble渲染
 */

import React, { useRef, useEffect } from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import MessageBubble from './MessageBubble';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Types ──────────────────────────────────────────────────────────

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

interface MessageMetadata {
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  confidence?: number;
  modelName?: string;
  guardrailStatus?: 'passed' | 'blocked' | 'warning';
}

interface OagStepInfo {
  step: number;
  label: string;
  icon: string;
  status: 'pending' | 'active' | 'completed' | 'error';
  detail?: string;
}

// ── OAG Progress Bar ───────────────────────────────────────────────

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
      <div className="flex items-center justify-between">
        <span className={`font-extrabold text-[10px] ${styles.accentText} flex items-center gap-1.5`}>
          <Icon name="Zap" size={11} className="animate-pulse" />
          <span>{t('aiworkbench.chatbot.oagPipelineTitle')}</span>
        </span>
        <span className={`font-mono text-[9px] font-bold ${hasError ? 'text-rose-500' : 'text-emerald-500'}`}>
          {hasError
            ? t('aiworkbench.chatbot.oagAbnormal')
            : t('aiworkbench.chatbot.oagStepFormat').replace('{completed}', String(completed)).replace('{total}', String(total)).replace('{pct}', String(pct))}
        </span>
      </div>

      <div className={`w-full h-1.5 ${styles.inputBg} rounded-full overflow-hidden`}>
        <div
          className={`h-full rounded-full transition-all duration-500 ease-out ${
            hasError ? 'bg-rose-500' : 'bg-gradient-to-r from-blue-500 to-emerald-500'
          }`}
          style={{ width: `${pct}%` }}
        />
      </div>

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
              title={`${step.step}. ${step.label}${step.detail ? ' — ' + step.detail : ''}`}
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

// ── Message Metadata Card ──────────────────────────────────────────

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

// ── Streaming Bubble ───────────────────────────────────────────────

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

// ── Typing Indicator ────────────────────────────────────────────────

function TypingIndicator({
  activeChatbot,
  styles,
}: {
  activeChatbot: AIPAgent;
  styles: Record<string, string>;
}) {
  const { t } = useLanguage();
  return (
    <div className="flex gap-2.5">
      <span
        className={`p-1.5 rounded-lg ${styles.sidebarActiveBg} shrink-0 h-7 w-7 flex items-center justify-center text-white`}
      >
        <Icon name={activeChatbot.avatar} size={12} className="animate-spin" />
      </span>
      <div
        className={`p-3 rounded-2xl ${styles.cardBg} ${styles.cardTextMuted} border ${styles.cardBorder} rounded-tl-none font-sans text-[11px] flex items-center gap-1.5 shadow-3xs`}
      >
        <span
          className={`h-1.5 w-1.5 ${styles.cardTextMuted} rounded-full animate-bounce`}
          style={{ animationDelay: '0ms' }}
        />
        <span
          className={`h-1.5 w-1.5 ${styles.cardTextMuted} rounded-full animate-bounce`}
          style={{ animationDelay: '150ms' }}
        />
        <span
          className={`h-1.5 w-1.5 ${styles.cardTextMuted} rounded-full animate-bounce`}
          style={{ animationDelay: '300ms' }}
        />
        <span className={`text-[10px] ${styles.cardTextMuted} ml-1`}>
          {t('aiworkbench.chatbot.typingIndicator')}
        </span>
      </div>
    </div>
  );
}

// ── ChatHistory Props ──────────────────────────────────────────────

interface ChatHistoryProps {
  activeChatbot: AIPAgent;
  displayMessages: ChatMessage[];
  isReplying: boolean;
  streamingContent: string;
  streamingMetadata: MessageMetadata | null;
  oagSteps: OagStepInfo[];
  sseConnected: boolean;
  onProposalConsent: (msgId: string, approved: boolean) => void;
  styles: Record<string, string>;
}

// ── Main ChatHistory Component ─────────────────────────────────────

export default function ChatHistory({
  activeChatbot,
  displayMessages,
  isReplying,
  streamingContent,
  streamingMetadata,
  oagSteps,
  sseConnected,
  onProposalConsent,
  styles,
}: ChatHistoryProps) {
  const { t } = useLanguage();
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [displayMessages, isReplying, streamingContent, oagSteps]);

  return (
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

      {/* Fallback typing indicator */}
      {isReplying && !streamingContent && (
        <TypingIndicator activeChatbot={activeChatbot} styles={styles} />
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
  );
}
