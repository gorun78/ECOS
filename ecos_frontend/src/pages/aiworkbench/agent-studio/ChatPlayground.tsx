/**
 * ChatPlayground — Agent Studio 右侧交互沙箱 Chat Mode 面板（消息流 + 快捷提示 + 输入栏）。
 * 由 AgentStudioView.tsx 机械抽取（PMO-74 H6-T4），JSX 结构与样式与原文逐行一致；
 * 仅 selectedAgent.name → prop agentName、handleSendChat → onSend、handleActionConsent → onConsent、
 * chatInput/setChatInput → props chatInput/onChatInputChange。
 * @license Apache-2.0
 */
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { Icon } from './Icon';
import type { ChatMessage } from './agentStudioHelpers';

export default function ChatPlayground({
  chatMessages,
  agentName,
  isReplying,
  chatInput,
  onChatInputChange,
  onSend,
  onConsent,
}: {
  chatMessages: ChatMessage[];
  agentName: string;
  isReplying: boolean;
  chatInput: string;
  onChatInputChange: (value: string) => void;
  onSend: (text?: string) => void;
  onConsent: (msgId: string, approved: boolean) => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
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
                {!isUser && <span className={`font-bold ${styles.cardTextMuted}`}>{agentName}</span>}
                <span>{msg.timestamp}</span>
                {isUser && <span className={`font-bold ${styles.accentText}`}>{t('aiworkbench.as.youRole')}</span>}
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
                  <span className={`text-[8px] ${styles.accentText} uppercase font-extrabold block mb-1`}>{t('aiworkbench.as.aipTrace')}</span>
                  {msg.thinkingTrace.map((log, idx) => (
                    <div key={idx} className="flex items-start gap-1">
                      <span className={styles.cardTextMuted}>▶</span>
                      <span>{log}</span>
                    </div>
                  ))}
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
                    <div><span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.as.targetFlight')}:</span> {msg.actionProposal.payload.flight_number}</div>
                    <div><span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.as.delayMinutes')}:</span> {msg.actionProposal.payload.delay_minutes} {t('aiworkbench.as.minutes')}</div>
                    <div><span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.as.execInstruction')}:</span> {msg.actionProposal.payload.new_status}</div>
                    <div className="text-[8px] text-rose-500 font-bold bg-rose-50 p-1 rounded mt-1">{t('aiworkbench.as.overrideWarn')}</div>
                  </div>

                  <div className="flex gap-1.5 pt-1 border-t border-amber-200/50">
                    <button
                      type="button"
                      onClick={() => onConsent(msg.id, true)}
                      className="flex-1 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg text-[10px] transition-colors cursor-pointer flex items-center justify-center gap-1"
                    >
                      <Icon name="Check" size={10} />
                      <span>{t('aiworkbench.as.confirmWrite')}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => onConsent(msg.id, false)}
                      className={`px-2.5 py-1.5 border ${styles.cardBorder} hover:${styles.inputBg} rounded-lg text-[10px] font-semibold ${styles.cardTextMuted} transition-colors cursor-pointer`}
                    >
                      <span>{t('aiworkbench.as.reject')}</span>
                    </button>
                  </div>
                </div>
              )}

              {msg.actionProposal && msg.actionProposal.status === 'approved' && (
                <div className={`w-[85%] ${styles.appBg} border border-emerald-300 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-emerald-700 font-semibold`}>
                  <span className="p-1 rounded bg-emerald-100 text-emerald-600">
                    <Icon name="CheckCircle2" size={12} />
                  </span>
                  <span>{t('aiworkbench.as.approvedWriteDone')}</span>
                </div>
              )}

              {msg.actionProposal && msg.actionProposal.status === 'rejected' && (
                <div className={`w-[85%] ${styles.appBg} border border-red-200 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-red-600 font-semibold`}>
                  <span className="p-1 rounded bg-red-100 text-red-600">
                    <Icon name="XCircle" size={12} />
                  </span>
                  <span>{t('aiworkbench.as.rejectedBlocked')}</span>
                </div>
              )}

            </div>
          );
        })}

        {isReplying && (
          <div className="flex flex-col gap-1.5 items-start">
            <div className={`flex items-center gap-1.5 text-[9px] ${styles.cardTextMuted} font-mono`}>
              <span className={`font-bold ${styles.cardTextMuted}`}>{agentName}</span>
              <span>{t('aiworkbench.as.thinking')}</span>
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
          t('aiworkbench.as.quickQuery'),
          t('aiworkbench.as.quickDispatch')
        ].map(p => (
          <button
            type="button"
            key={p}
            onClick={() => onSend(p)}
            className={`px-2.5 py-1 ${styles.cardBg} ${styles.accentHover} ${styles.accentBorder} hover:border-blue-200 border ${styles.cardBorder} rounded-full text-[10px] ${styles.cardTextMuted} font-medium whitespace-nowrap cursor-pointer transition-colors`}
          >
            {p}
          </button>
        ))}
      </div>

      {/* Input Bar */}
      <div className={`p-3 border-t ${styles.cardBorder} ${styles.cardBg} flex items-center gap-2 shrink-0`}>
        <input
          type="text"
          placeholder={t('aiworkbench.as.inputPlaceholder')}
          value={chatInput}
          onChange={e => onChatInputChange(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && onSend()}
          className={`flex-1 h-8 px-3 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500`}
        />
        <button
          type="button"
          onClick={() => onSend()}
          disabled={isReplying || !chatInput.trim()}
          className={`h-8 w-8 ${styles.accentBg} ${styles.accentHover} text-white rounded-lg flex items-center justify-center cursor-pointer transition-colors disabled:opacity-50 disabled:cursor-not-allowed shrink-0`}
        >
          <Icon name="Send" size={13} />
        </button>
      </div>
    </div>
  );
}
