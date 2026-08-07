/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import type { ThemeStyles } from '../../ThemeContext';
import * as Icons from 'lucide-react';
import SimulationTab from './SimulationTab';
import { useLanguage } from '../../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export interface ChatMessage {
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

interface AgentToolPanelProps {
  sandboxMode: 'chat' | 'simulation';
  setSandboxMode: (v: 'chat' | 'simulation') => void;
  chatMessages: ChatMessage[];
  setChatMessages: React.Dispatch<React.SetStateAction<ChatMessage[]>>;
  chatInput: string;
  setChatInput: (v: string) => void;
  isReplying: boolean;
  selectedAgent: AIPAgent;
  onSendChat: (textToSend?: string) => void;
  onActionConsent: (msgId: string, approved: boolean) => void;
  handleRunSimulation: () => Promise<void>;
  simUserId: string;
  setSimUserId: (v: string) => void;
  simDatasetId: string;
  setSimDatasetId: (v: string) => void;
  simQuery: string;
  setSimQuery: (v: string) => void;
  isSimulating: boolean;
  simResult: any | null;
  expandedNodes: Record<string, boolean>;
  toggleNodeExpanded: (nodeId: string) => void;
  styles: ThemeStyles;
}

export default function AgentToolPanel({
  sandboxMode,
  setSandboxMode,
  chatMessages,
  setChatMessages,
  chatInput,
  setChatInput,
  isReplying,
  selectedAgent,
  onSendChat,
  onActionConsent,
  handleRunSimulation,
  simUserId,
  setSimUserId,
  simDatasetId,
  setSimDatasetId,
  simQuery,
  setSimQuery,
  isSimulating,
  simResult,
  expandedNodes,
  toggleNodeExpanded,
  styles,
}: AgentToolPanelProps) {
  const { t } = useLanguage();
  return (
    <div className={`w-[450px] ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0`}>
      
      {/* Header with Tab Selectors */}
      <div className={`p-2 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between shrink-0`}>
        <div className={`flex ${styles.inputBg} p-1 rounded-lg`}>
          <button
            onClick={() => setSandboxMode('chat')}
            className={`px-3 py-1.5 rounded-md font-bold text-[10px] transition-all cursor-pointer flex items-center gap-1 ${
              sandboxMode === 'chat'
                ? `${styles.cardBg} ${styles.cardText} shadow-xs`
                : styles.cardTextMuted
            }`}
          >
            <Icon name="MessageSquare" size={10} />
            <span>{t("aiworkbench.agentStudio.chatTab")}</span>
          </button>
          <button
            onClick={() => setSandboxMode('simulation')}
            className={`px-3 py-1.5 rounded-md font-bold text-[10px] transition-all cursor-pointer flex items-center gap-1 ${
              sandboxMode === 'simulation'
                ? `${styles.accentBg} text-white shadow-xs`
                : styles.cardTextMuted
            }`}
          >
            <Icon name="ShieldAlert" size={10} className="text-amber-400" />
            <span>{t("aiworkbench.agentStudio.sandboxTab")}</span>
          </button>
        </div>

        {sandboxMode === 'chat' ? (
          <button
            onClick={() => {
              setChatMessages([
                {
                  id: 'welcome',
                  sender: 'agent',
                  content: t("aiworkbench.agentStudio.chatRestarted"),
                  timestamp: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
                }
              ]);
            }}
            className={`p-1 ${styles.cardTextMuted} cursor-pointer`}
            title={t("aiworkbench.agentStudio.clearChatHistory")}
          >
            <Icon name="RefreshCcw" size={11} />
          </button>
        ) : (
          <span className="px-2 py-0.5 bg-amber-500/10 text-amber-600 rounded text-[9px] font-black">
            SIMULATOR v1.2
          </span>
        )}
      </div>

      {/* TAB 1: Chat Mode */}
      {sandboxMode === 'chat' && (
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Message Area */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {chatMessages.map(msg => {
              const isUser = msg.sender === 'user';
              const isSys = msg.sender === 'system';

              if (isSys) {
                return (
                  <div key={msg.id} className={`p-2.5 ${styles.appBg} rounded-lg text-[11px] ${styles.cardTextMuted} border ${styles.cardBorder}/50 leading-relaxed font-sans`}>
                    {msg.content}
                  </div>
                );
              }

              return (
                <div key={msg.id} className={`flex flex-col gap-1.5 ${isUser ? 'items-end' : 'items-start'}`}>
                  
                  {/* Message Head */}
                  <div className={`flex items-center gap-1.5 text-[9px] ${styles.cardTextMuted} font-mono`}>
                    {!isUser && <span className={`font-bold ${styles.cardTextMuted}`}>{selectedAgent.name}</span>}
                    <span>{msg.timestamp}</span>
                    {isUser && <span className={`font-bold ${styles.accentText}`}>{t("aiworkbench.agentStudio.userDispatchDirector")}</span>}
                  </div>

                  {/* Chat Bubble */}
                  <div className={`p-3 rounded-2xl max-w-[85%] leading-relaxed whitespace-pre-wrap text-[11px] ${
                    isUser
                      ? `${styles.accentBg} text-white rounded-tr-none font-medium`
                      : `${styles.inputBg} ${styles.cardText} rounded-tl-none border ${styles.cardBorder}/40`
                  }`}>
                    {msg.content}
                  </div>

                  {/* Embedded Reasoning Trace */}
                  {msg.thinkingTrace && msg.thinkingTrace.length > 0 && (
                    <div className={`w-[85%] ${styles.appBg} ${styles.cardTextMuted} rounded-lg p-2.5 font-mono text-[9px] space-y-1`}>
                      <span className={`text-[8px] ${styles.accentText} uppercase font-extrabold block mb-1`}>{t("aiworkbench.agentStudio.aipTrace")}</span>
                      {msg.thinkingTrace.map((log, idx) => (
                        <div key={idx} className="flex items-start gap-1">
                          <span className={styles.cardTextMuted}>▶</span>
                          <span>{log}</span>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Embedded Action Consent Approval Card */}
                  {msg.actionProposal && msg.actionProposal.status === 'pending' && (
                    <div className="w-[85%] border-2 border-amber-400 bg-amber-50/50 rounded-xl p-3 space-y-2.5 shadow-sm">
                      <div className="flex items-center gap-2 font-bold text-amber-800 text-[10px] border-b border-amber-200 pb-1.5">
                        <span className="p-1 rounded bg-amber-100 text-amber-600">
                          <Icon name="ShieldAlert" size={11} className="animate-pulse" />
                        </span>
                        <span>{msg.actionProposal.actionName}</span>
                      </div>
                      
                      <div className={`space-y-1 font-mono text-[9px] ${styles.cardTextMuted}`}>
                        <div><span className={`font-bold ${styles.cardText}`}>{t("aiworkbench.agentStudio.targetFlight")}</span> {msg.actionProposal.payload.flight_number}</div>
                        <div><span className={`font-bold ${styles.cardText}`}>{t("aiworkbench.agentStudio.delayMinutes")}</span> {msg.actionProposal.payload.delay_minutes} {t("aiworkbench.agentStudio.minutes")}</div>
                        <div><span className={`font-bold ${styles.cardText}`}>{t("aiworkbench.agentStudio.execCommand")}</span> {msg.actionProposal.payload.new_status}</div>
                        <div className="text-[8px] text-rose-500 font-bold bg-rose-50 p-1 rounded mt-1">{t("aiworkbench.agentStudio.actionWarning")}</div>
                      </div>

                      <div className="flex gap-1.5 pt-1 border-t border-amber-200/50">
                        <button
                          onClick={() => onActionConsent(msg.id, true)}
                          className="flex-1 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg text-[10px] transition-colors cursor-pointer flex items-center justify-center gap-1"
                        >
                          <Icon name="Check" size={10} />
                          <span>{t("aiworkbench.agentStudio.confirmAuthorize")}</span>
                        </button>
                        <button
                          onClick={() => onActionConsent(msg.id, false)}
                          className={`px-2.5 py-1.5 border ${styles.cardBorder} hover:${styles.inputBg} rounded-lg text-[10px] font-semibold ${styles.cardTextMuted} transition-colors cursor-pointer`}
                        >
                          <span>{t("aiworkbench.agentStudio.reject")}</span>
                        </button>
                      </div>
                    </div>
                  )}

                  {msg.actionProposal && msg.actionProposal.status === 'approved' && (
                    <div className={`w-[85%] ${styles.appBg} border border-emerald-300 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-emerald-700 font-semibold`}>
                      <span className="p-1 rounded bg-emerald-100 text-emerald-600">
                        <Icon name="CheckCircle2" size={12} />
                      </span>
                      <span>{t("aiworkbench.agentStudio.actionAuthorized")}</span>
                    </div>
                  )}

                  {msg.actionProposal && msg.actionProposal.status === 'rejected' && (
                    <div className={`w-[85%] ${styles.appBg} border border-red-200 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-red-600 font-semibold`}>
                      <span className="p-1 rounded bg-red-100 text-red-600">
                        <Icon name="XCircle" size={12} />
                      </span>
                      <span>{t("aiworkbench.agentStudio.actionBlocked")}</span>
                    </div>
                  )}

                </div>
              );
            })}

            {isReplying && (
              <div className="flex flex-col gap-1.5 items-start">
                <div className={`flex items-center gap-1.5 text-[9px] ${styles.cardTextMuted} font-mono`}>
                  <span className={`font-bold ${styles.cardTextMuted}`}>{selectedAgent.name}</span>
                  <span>{t("aiworkbench.agentStudio.thinking")}</span>
                </div>
                <div className={`p-3 ${styles.appBg} rounded-2xl rounded-tl-none border ${styles.cardBorder}/40 flex items-center gap-1.5`}>
                  <span className={`w-1.5 h-1.5 ${styles.cardTextMuted} rounded-full animate-bounce`} style={{ animationDelay: '0ms' }} />
                  <span className={`w-1.5 h-1.5 ${styles.cardTextMuted} rounded-full animate-bounce`} style={{ animationDelay: '150ms' }} />
                  <span className={`w-1.5 h-1.5 ${styles.cardTextMuted} rounded-full animate-bounce`} style={{ animationDelay: '300ms' }} />
                </div>
              </div>
            )}
          </div>

          {/* Quick Prompts list */}
          <div className={`px-3 py-1.5 border-t ${styles.cardBorder} flex items-center gap-1.5 overflow-x-auto shrink-0 ${styles.appBg}`}>
            {[
              t("aiworkbench.agentStudio.quickPrompt1"),
              t("aiworkbench.agentStudio.quickPrompt2")
            ].map(p => (
              <button
                key={p}
                onClick={() => onSendChat(p)}
                className={`px-2.5 py-1 ${styles.cardBg} ${styles.accentHover} ${styles.accentBorder} hover:border-blue-200 border ${styles.cardBorder} rounded-full text-[10px] ${styles.cardTextMuted} font-medium whitespace-nowrap cursor-pointer transition-colors`}
              >
                {p}
              </button>
            )}
          </div>

          {/* Input Bar */}
          <div className={`p-3 border-t ${styles.cardBorder} ${styles.cardBg} flex items-center gap-2 shrink-0`}>
            <input
              type="text"
              placeholder={t("aiworkbench.agentStudio.chatPlaceholder")}
              value={chatInput}
              onChange={e => setChatInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && onSendChat()}
              className={`flex-1 h-8 px-3 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500`}
            />
            <button
              onClick={() => onSendChat()}
              disabled={isReplying || !chatInput.trim()}
              className={`h-8 w-8 ${styles.accentBg} ${styles.accentHover} text-white rounded-lg flex items-center justify-center cursor-pointer transition-colors disabled:opacity-50 disabled:cursor-not-allowed shrink-0`}
            >
              <Icon name="Send" size={13} />
            </button>
          </div>
        </div>
      )}

      {/* TAB 2: Simulation Sandbox Mode */}
      {sandboxMode === 'simulation' && (
        <SimulationTab
          simUserId={simUserId}
          setSimUserId={setSimUserId}
          simDatasetId={simDatasetId}
          setSimDatasetId={setSimDatasetId}
          simQuery={simQuery}
          setSimQuery={setSimQuery}
          isSimulating={isSimulating}
          simResult={simResult}
          expandedNodes={expandedNodes}
          toggleNodeExpanded={toggleNodeExpanded}
          handleRunSimulation={handleRunSimulation}
          styles={styles}
        />
      )}

    </div>
  );
}
