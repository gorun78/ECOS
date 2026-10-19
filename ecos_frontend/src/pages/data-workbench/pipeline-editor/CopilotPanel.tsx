/**
 * CopilotPanel — AI 助手聊天面板
 * 聊天式对话框，支持 Markdown/代码块渲染
 * 拆分后：兜底回复移至 CopilotPanelFallback.ts，消息列表移至
 * CopilotPanelMessages.tsx，类型/常量移至 CopilotPanelTypes.ts。逻辑不变。
 * @license Apache-2.0
 */
import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  Send, Sparkles, X, Trash2, Loader2,
} from 'lucide-react';
import { apiFetch } from '../../../api';
import { API_BASE } from '../../../services/httpClient';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { QUICK_ACTIONS, type ChatMessage, type CopilotPanelProps } from './CopilotPanelTypes';
import { generateFallbackResponse } from './CopilotPanelFallback';
import CopilotPanelMessages from './CopilotPanelMessages';

// Re-export props type so existing imports keep working
export type { CopilotPanelProps } from './CopilotPanelTypes';

const CopilotPanel: React.FC<CopilotPanelProps> = ({ className = '', onClose }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const createWelcomeMessage = useCallback((): ChatMessage => ({
    id: 'welcome',
    role: 'assistant',
    content: t('dw.copilot.welcome'),
    timestamp: new Date(),
  }), [t]);
  const [messages, setMessages] = useState<ChatMessage[]>(() => [{
    id: 'welcome',
    role: 'assistant',
    content: t('dw.copilot.welcome'),
    timestamp: new Date(),
  }]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Auto-focus input
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Send message
  const handleSend = useCallback(async (text?: string) => {
    const msgText = (text || inputValue).trim();
    if (!msgText || loading) return;

    const userMsg: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: msgText,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInputValue('');
    setLoading(true);

    try {
      const resp = await apiFetch<{ data?: { reply?: string }; reply?: string }>(
        `${API_BASE}/v1/engine/data/copilot/chat`,
        {
          method: 'POST',
          body: JSON.stringify({
            message: msgText,
            history: messages.slice(-5).map((m) => ({
              role: m.role,
              content: m.content,
            })),
          }),
        }
      );
      const reply = resp?.data?.reply || resp?.reply;
      if (reply) {
        setMessages((prev) => [
          ...prev,
          {
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: reply,
            timestamp: new Date(),
          },
        ]);
      } else {
        // 无回复 = 后端未给出模型输出 ⇒ 明示本地兜底模板（fallback 标记驱动徽标）
        const fallback = generateFallbackResponse(msgText);
        setMessages((prev) => [
          ...prev,
          {
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: `> ⚠️ ${t('dw.copilot.fallbackNotice')}\n\n${fallback}`,
            timestamp: new Date(),
            fallback: true,
          },
        ]);
      }
    } catch {
      const fallback = generateFallbackResponse(msgText);
      setMessages((prev) => [
        ...prev,
        {
          id: `assistant-${Date.now()}`,
          role: 'assistant',
          content: `> ⚠️ ${t('dw.copilot.fallbackNotice')}\n\n${fallback}`,
          timestamp: new Date(),
          fallback: true,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }, [inputValue, loading, messages, t]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleClear = () => {
    setMessages([createWelcomeMessage()]);
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg} ${className}`}>
      {/* Header */}
      <div className={`flex items-center justify-between px-3 py-2.5 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <div className="flex items-center gap-2">
          <Sparkles size={15} className={`${styles.infoText}`} />
          <span className={`text-xs font-bold ${styles.cardText} uppercase tracking-wider`}>
            Copilot
          </span>
          <span className={`text-[9px] px-1 py-0.5 rounded ${styles.infoBg} ${styles.infoText} font-medium`}>
            AI
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button type="button"
            onClick={handleClear}
            className={`p-1 rounded hover:opacity-70 ${styles.cardTextMuted} transition-colors`}
            title={t('dw.copilot.clearTitle')}
          >
            <Trash2 size={13} />
          </button>
          {onClose && (
            <button type="button"
              onClick={onClose}
              className={`p-1 rounded hover:opacity-70 ${styles.cardTextMuted} transition-colors`}
              title={t('dw.copilot.closeTitle')}
            >
              <X size={13} />
            </button>
          )}
        </div>
      </div>

      {/* Messages */}
      <CopilotPanelMessages
        messages={messages}
        loading={loading}
        copiedId={copiedId}
        onCopy={handleCopy}
        messagesEndRef={messagesEndRef}
      />

      {/* Quick actions */}
      <div className={`px-3 py-2 border-t ${styles.cardBorder} shrink-0 overflow-x-auto`}>
        <div className="flex gap-1.5">
          {QUICK_ACTIONS.map((action, i) => (
            <button type="button"
              key={i}
              onClick={() => handleSend(t(action.promptKey))}
              className={`text-[10px] px-2 py-1 rounded-full border ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80 whitespace-nowrap transition-colors`}
            >
              {t(action.labelKey)}
            </button>
          ))}
        </div>
      </div>

      {/* Input */}
      <div className={`px-3 py-2 border-t ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <div className="flex gap-2">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={t('dw.copilot.inputPlaceholder')}
            rows={2}
            className={`flex-1 px-3 py-1.5 text-xs border ${styles.cardBorder} rounded-lg resize-none focus:ring-1 focus:ring-current outline-none transition-colors ${styles.cardBg} ${styles.cardText}`}
            disabled={loading}
          />
          <button type="button"
            onClick={() => handleSend()}
            disabled={!inputValue.trim() || loading}
            className={`shrink-0 flex items-center justify-center w-9 h-9 ${styles.accentBg} hover:opacity-90 ${styles.cardText} rounded-lg transition-colors disabled:opacity-50 self-end`}
          >
            {loading ? (
              <Loader2 size={15} className="animate-spin" />
            ) : (
              <Send size={15} />
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CopilotPanel;
