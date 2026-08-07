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
}

interface MessageBubbleProps {
  msg: ChatMessage;
  styles: Record<string, string>;
  agentName: string;
  agentAvatar: string;
  onProposalConsent: (msgId: string, approved: boolean) => void;
}

export default function MessageBubble({
  msg,
  styles,
  agentName,
  agentAvatar,
  onProposalConsent,
}: MessageBubbleProps) {
  const { t } = useLanguage();
  const isUser = msg.sender === 'user';
  const isSystem = msg.sender === 'system';

  if (isSystem) {
    return (
      <div key={msg.id} className={`p-3 ${styles.cardBg} border border-emerald-100 rounded-xl space-y-2 shadow-2xs font-mono text-[10px]`}>
        <div className="flex items-center gap-1.5 text-emerald-600 font-extrabold border-b border-emerald-50 pb-1.5">
          <Icon name="ShieldCheck" size={13} />
          <span>{msg.content.split('\n\n')[0].replace('###', '').trim()}</span>
        </div>
        <div className={`${styles.cardTextMuted} leading-relaxed whitespace-pre-line font-sans text-[10px]`}>
          {msg.content.substring(msg.content.indexOf('\n\n') + 2)}
        </div>
      </div>
    );
  }

  return (
    <div key={msg.id} className={`flex gap-2.5 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {/* Avatar */}
      <span className={`p-1.5 rounded-lg shrink-0 h-7 w-7 flex items-center justify-center font-bold text-white shadow-3xs ${
        isUser ? styles.accentBg : styles.sidebarActiveBg
      }`}>
        <Icon name={isUser ? 'User' : agentAvatar} size={12} />
      </span>

      {/* Bubble body */}
      <div className="space-y-1.5 max-w-[85%]">
        <div className={`p-3 rounded-2xl leading-normal text-[11px] whitespace-pre-line shadow-3xs ${
          isUser
            ? `${styles.accentBg} text-white font-medium rounded-tr-none`
            : `${styles.cardBg} ${styles.cardText} ${styles.cardBorder} border rounded-tl-none font-sans`
        }`}>
          {msg.content}
        </div>

        {/* Thinking Trace */}
        {!isUser && msg.thinkingTrace && msg.thinkingTrace.length > 0 && (
          <details className={`group ${styles.cardBg}/60 ${styles.cardBorder}/50 border rounded-lg p-1.5 transition-all`}>
            <summary className={`flex items-center gap-1 font-mono text-[9px] ${styles.cardTextMuted} font-bold cursor-pointer select-none outline-none`}>
              <span className="transition-transform group-open:rotate-90">▶</span>
              <Icon name="Cpu" size={9} />
              <span>{t('aiworkbench.chatbot.thinkingTraceLabel')}</span>
            </summary>
            <div className={`mt-1.5 pl-3 border-l ${styles.cardBorder} font-mono text-[8.5px] ${styles.cardTextMuted} space-y-1 leading-normal`}>
              {msg.thinkingTrace.map((trace, tIdx) => (
                <p key={tIdx} className={trace.includes('🛡️') || trace.includes('⛔') ? 'text-rose-600 font-bold' : trace.includes('🔍') ? `${styles.accentText} font-medium` : styles.cardTextMuted}>
                  {trace}
                </p>
              ))}
            </div>
          </details>
        )}

        {/* Interactive Ontology Action Proposal Consent Card */}
        {!isUser && msg.actionProposal && (
          <div className={`p-3.5 ${styles.cardBg} border ${styles.accentBorder} rounded-xl space-y-2.5 shadow-2xs`}>
            <div className={`flex items-center justify-between border-b ${styles.badgeBg} pb-2`}>
              <span className={`font-extrabold text-[11px] ${styles.accentText} flex items-center gap-1.5`}>
                <Icon name="Settings" size={12} />
                <span>{t('aiworkbench.chatbot.proposalCard')}</span>
              </span>
              <span className={`px-1.5 py-0.2 rounded font-extrabold text-[8px] uppercase ${
                msg.actionProposal.status === 'approved' ? 'bg-emerald-100 text-emerald-700' :
                msg.actionProposal.status === 'rejected' ? 'bg-rose-100 text-rose-700' :
                'bg-amber-100 text-amber-700 animate-pulse'
              }`}>
                {msg.actionProposal.status === 'approved' ? t('aiworkbench.chatbot.proposalApproved') :
                 msg.actionProposal.status === 'rejected' ? t('aiworkbench.chatbot.proposalRejected') : t('aiworkbench.chatbot.proposalPending')}
              </span>
            </div>

            <div className={`font-mono text-[9px] space-y-1 ${styles.cardTextMuted} leading-normal ${styles.inputBg} p-2.5 rounded-lg border ${styles.cardBorder}`}>
              <p><span className={`${styles.cardTextMuted} font-bold`}>{t('aiworkbench.chatbot.proposalActionId')}</span>: <span className={`${styles.cardText} font-extrabold`}>{msg.actionProposal.actionId}</span></p>
              <p><span className={`${styles.cardTextMuted} font-bold`}>{t('aiworkbench.chatbot.proposalTargetTable')}</span>: <span className={`${styles.accentText} font-extrabold`}>ds_flights_clean / flights_raw</span></p>
              <div className={`border-t ${styles.cardBorder} my-1.5 pt-1.5 space-y-0.5`}>
                <p><span className={`${styles.cardTextMuted} font-bold`}>{t('aiworkbench.chatbot.proposalFields')}</span>:</p>
                {Object.entries(msg.actionProposal.payload).map(([k, v]) => (
                  <p key={k} className="pl-2">
                    <span className={styles.cardTextMuted}>• {k}</span>: <span className={`${styles.cardText} font-bold`}>"{v}"</span>
                  </p>
                ))}
              </div>
            </div>

            {msg.actionProposal.status === 'pending' && (
              <div className="flex gap-2 text-[10px] font-bold">
                <button
                  onClick={() => onProposalConsent(msg.id, true)}
                  className="flex-1 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg transition-colors cursor-pointer flex items-center justify-center gap-1"
                >
                  <Icon name="Check" size={11} />
                  <span>{t('aiworkbench.chatbot.proposalApprove')}</span>
                </button>
                <button
                  onClick={() => onProposalConsent(msg.id, false)}
                  className={`px-3 py-1.5 ${styles.cardBg} ${styles.accentHover} ${styles.cardTextMuted} border ${styles.cardBorder} rounded-lg transition-colors cursor-pointer`}
                >
                  <span>{t('aiworkbench.chatbot.proposalRejectBtn')}</span>
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
