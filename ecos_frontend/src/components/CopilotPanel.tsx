import React, { useState, useRef, useEffect } from 'react';
import { MessageSquare, Send, Clock, Wrench, Download } from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';

interface CopilotPanelProps {
  agentType: string;
}

interface Message {
  role: 'user' | 'assistant';
  content: string;
  thoughtChain?: string[];
  toolCalls?: Array<{ tool: string; input: string; output: string; duration: number }>;
  sources?: Array<{ type: string; id: string; name: string }>;
}

export const CopilotPanel: React.FC<CopilotPanelProps> = ({ agentType }) => {
  const { locale } = useLanguage();
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [quickQuestions, setQuickQuestions] = useState<string[]>([]);
  const [sessionId] = useState(() => `session-${Date.now()}`);
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchQuickQuestions();
  }, [agentType]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const fetchQuickQuestions = async () => {
    try {
      const res = await fetch(`/api/v1/agent/copilot/quick-questions?agentId=agent-${agentType}`);
      if (res.ok) {
        const data = await res.json();
        setQuickQuestions((data.data || data).map((q: any) => q.question));
      }
    } catch { /* ignore */ }
  };

  const sendMessage = async (text: string) => {
    if (!text.trim()) return;
    const userMsg: Message = { role: 'user', content: text };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const res = await fetch('/api/v1/agent/copilot/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ agentId: `agent-${agentType}`, message: text, sessionId })
      });
      if (res.ok) {
        const wrapped = await res.json();
        const data = wrapped.data || wrapped;
        const assistantMsg: Message = {
          role: 'assistant',
          content: data.answer || '',
          thoughtChain: data.thoughtChain || [],
          toolCalls: data.toolCalls || [],
          sources: data.sources || []
        };
        setMessages(prev => [...prev, assistantMsg]);
      }
    } catch {
      setMessages(prev => [...prev, { role: 'assistant', content: locale === 'zh' ? '错误：获取响应失败' : 'Error: Failed to get response' }]);
    }
    setLoading(false);
  };

  const exportChat = () => {
    const text = messages.map(m => `${m.role}: ${m.content}`).join('\n');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `copilot-${agentType}-${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex flex-col h-full border-l border-[var(--border)] bg-[var(--card)]">
      <div className="p-3 border-b border-[var(--border)] flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MessageSquare className="w-4 h-4 text-[var(--primary)]" />
          <span className="text-sm font-medium">{locale === 'zh' ? '智能助手' : 'Copilot'}</span>
        </div>
        <button onClick={exportChat} className="p-1 hover:bg-[var(--muted)] rounded">
          <Download className="w-3 h-3" />
        </button>
      </div>

      {quickQuestions.length > 0 && messages.length === 0 && (
        <div className="p-3 border-b border-[var(--border)]">
          <div className="text-xs text-[var(--muted-foreground)] mb-2">
            {locale === 'zh' ? '快捷提问' : 'Quick Questions'}
          </div>
          <div className="flex flex-wrap gap-1">
            {quickQuestions.map((q, i) => (
              <button key={i} onClick={() => sendMessage(q)}
                className="text-xs px-2 py-1 rounded border border-[var(--border)] hover:bg-[var(--muted)]">
                {q}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {messages.map((msg, i) => (
          <div key={i} className={`text-sm ${msg.role === 'user' ? 'text-right' : ''}`}>
            <div className={`inline-block max-w-[80%] p-2 rounded-lg ${
              msg.role === 'user'
                ? 'bg-[var(--primary)] text-[var(--primary-foreground)]'
                : 'bg-[var(--muted)]'
            }`}>
              {msg.content}
            </div>
            {msg.thoughtChain && msg.thoughtChain.length > 0 && (
              <div className="mt-1 text-xs text-[var(--muted-foreground)]">
                {msg.thoughtChain.map((step, j) => (
                  <div key={j} className="flex items-center gap-1">
                    <Clock className="w-2 h-2" /> {step}
                  </div>
                ))}
              </div>
            )}
            {msg.toolCalls && msg.toolCalls.length > 0 && (
              <div className="mt-1 text-xs text-[var(--muted-foreground)]">
                {msg.toolCalls.map((tc, j) => (
                  <div key={j} className="flex items-center gap-1">
                    <Wrench className="w-2 h-2" /> {tc.tool} ({tc.duration}ms)
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        {loading && <div className="text-xs text-[var(--muted-foreground)] animate-pulse">...</div>}
        <div ref={chatEndRef} />
      </div>

      <div className="p-3 border-t border-[var(--border)]">
        <div className="flex gap-2">
          <input value={input} onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && sendMessage(input)}
            className="flex-1 text-sm p-2 border border-[var(--border)] rounded bg-[var(--background)]"
            placeholder={locale === 'zh' ? '输入消息...' : 'Type a message...'} />
          <button onClick={() => sendMessage(input)} disabled={loading}
            className="p-2 bg-[var(--primary)] text-[var(--primary-foreground)] rounded hover:opacity-90 disabled:opacity-50">
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
