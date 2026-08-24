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
import { useTheme } from '../../../components/ThemeContext';
import { QUICK_ACTIONS, type ChatMessage, type CopilotPanelProps } from './CopilotPanelTypes';
import { generateFallbackResponse } from './CopilotPanelFallback';
import CopilotPanelMessages from './CopilotPanelMessages';

// Re-export props type so existing imports keep working
export type { CopilotPanelProps } from './CopilotPanelTypes';

const createWelcomeMessage = (): ChatMessage => ({
  id: 'welcome',
  role: 'assistant',
  content: '👋 你好！我是 **ECOS Copilot**，你的数据工程 AI 助手。\n\n我可以帮你：\n- 📝 编写 Pipeline DSL / YAML 定义\n- 🔍 分析数据异常值\n- 🧹 推荐数据清洗步骤\n- 🐍 生成 Python UDF 代码\n- ❓ 解答 PB 函数用法\n\n请随意提问！',
  timestamp: new Date(),
});

const CopilotPanel: React.FC<CopilotPanelProps> = ({ className = '', onClose }) => {
  const { styles } = useTheme();
  const [messages, setMessages] = useState<ChatMessage[]>([createWelcomeMessage()]);
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
      const resp = await apiFetch<{ data: { reply: string } }>(
        '/api/v1/engine/data/copilot/chat',
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
      const reply = (resp as any)?.data?.reply || (resp as any)?.reply;
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
        const fallback = generateFallbackResponse(msgText);
        setMessages((prev) => [
          ...prev,
          {
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: fallback,
            timestamp: new Date(),
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
          content: fallback,
          timestamp: new Date(),
        },
      ]);
    } finally {
      setLoading(false);
    }
  }, [inputValue, loading, messages]);

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
    setMessages([
      {
        id: 'welcome',
        role: 'assistant',
        content: '对话已清空。有什么我可以帮你的？',
        timestamp: new Date(),
      },
    ]);
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg} ${className}`}>
      {/* Header */}
      <div className={`flex items-center justify-between px-3 py-2.5 border-b ${styles.cardBorder} bg-slate-50 shrink-0`}>
        <div className="flex items-center gap-2">
          <Sparkles size={15} className="text-purple-600" />
          <span className={`text-xs font-bold ${styles.cardText} uppercase tracking-wider`}>
            Copilot
          </span>
          <span className="text-[9px] px-1 py-0.5 rounded bg-purple-100 text-purple-700 font-medium">
            AI
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleClear}
            className={`p-1 rounded hover:bg-slate-200 ${styles.cardTextMuted} hover:text-slate-600 transition-colors`}
            title="清空对话"
          >
            <Trash2 size={13} />
          </button>
          {onClose && (
            <button
              onClick={onClose}
              className={`p-1 rounded hover:bg-slate-200 ${styles.cardTextMuted} transition-colors`}
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
            <button
              key={i}
              onClick={() => handleSend(action.prompt)}
              className={`text-[10px] px-2 py-1 rounded-full border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-purple-50 hover:border-purple-200 hover:text-purple-600 whitespace-nowrap transition-colors`}
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>

      {/* Input */}
      <div className={`px-3 py-2 border-t ${styles.cardBorder} bg-slate-50 shrink-0`}>
        <div className="flex gap-2">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入问题，如: 帮我写一个过滤活跃用户的 Pipeline..."
            rows={2}
            className={`flex-1 px-3 py-1.5 text-xs border border-slate-200 rounded-lg resize-none focus:border-purple-400 focus:ring-1 focus:ring-purple-200 outline-none transition-colors ${styles.cardBg} ${styles.cardText}`}
            disabled={loading}
          />
          <button
            onClick={() => handleSend()}
            disabled={!inputValue.trim() || loading}
            className="shrink-0 flex items-center justify-center w-9 h-9 bg-purple-600 hover:bg-purple-700 text-white rounded-lg transition-colors disabled:opacity-50 self-end"
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
