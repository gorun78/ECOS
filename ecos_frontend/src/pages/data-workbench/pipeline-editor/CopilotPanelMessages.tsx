/**
 * CopilotPanelMessages — 消息列表 + 代码块渲染 + 加载指示器
 * 从 CopilotPanel 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import { Bot, User, Copy, Check } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import type { ChatMessage } from './CopilotPanelTypes';
import { useTheme } from '../../../components/ThemeContext';

interface Props {
  messages: ChatMessage[];
  loading: boolean;
  copiedId: string | null;
  onCopy: (text: string, id: string) => void;
  messagesEndRef: React.RefObject<HTMLDivElement>;
}

const CopilotPanelMessages: React.FC<Props> = ({
  messages,
  loading,
  copiedId,
  onCopy,
  messagesEndRef,
}) => {
  const { styles } = useTheme();
  // Render code block with copy button
  const renderCodeBlock = (code: string, language: string = '') => {
    return (
      <div className="relative group my-2">
        <div className={`flex items-center justify-between px-3 py-1 ${styles.cardBg} rounded-t-lg text-[10px] ${styles.cardTextMuted}`}>
          <span>{language || 'code'}</span>
          <button
            onClick={() => onCopy(code, `code-${code.slice(0, 20)}`)}
            className={`flex items-center gap-1 ${styles.muted} hover:${styles.cardText} transition-colors`}
          >
            {copiedId === `code-${code.slice(0, 20)}` ? (
              <Check size={10} className={`${styles.successText}`} />
            ) : (
              <Copy size={10} />
            )}
          </button>
        </div>
        <pre className={`${styles.cardBg} ${styles.cardText} p-3 rounded-b-lg overflow-x-auto text-xs font-mono leading-relaxed`}>
          <code>{code}</code>
        </pre>
      </div>
    );
  };

  return (
    <div className="flex-1 overflow-y-auto p-3 space-y-3">
      {messages.map((msg) => (
        <div
          key={msg.id}
          className={`flex gap-2 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}
        >
          {/* Avatar */}
          <div
            className={`w-7 h-7 rounded-full flex items-center justify-center shrink-0 ${
              msg.role === 'user'
                ? `${styles.accentBg} ${styles.cardText}`
                : `${styles.accentBg} ${styles.cardText}`
            }`}
          >
            {msg.role === 'user' ? <User size={14} /> : <Bot size={14} />}
          </div>

          {/* Bubble */}
          <div
            className={`max-w-[85%] rounded-xl px-3 py-2 ${
              msg.role === 'user'
                ? `${styles.accentBg} ${styles.cardText}`
                : `${styles.sidebarBg} ${styles.cardText}`
            }`}
          >
            {msg.role === 'assistant' ? (
              <div className="text-xs prose prose-sm max-w-none dark:prose-invert">
                <ReactMarkdown
                  components={{
                    code({ children, className: codeClass, ...rest }) {
                      const match = /language-(\w+)/.exec(codeClass || '');
                      const codeStr = String(children).replace(/\n$/, '');
                      if (match) {
                        return renderCodeBlock(codeStr, match[1]);
                      }
                      return (
                        <code className={`${styles.sidebarBg} ${styles.cardText} px-1 py-0.5 rounded text-[10px] font-mono`} {...rest}>
                          {children}
                        </code>
                      );
                    },
                    pre({ children }) {
                      return <>{children}</>;
                    },
                  }}
                >
                  {msg.content}
                </ReactMarkdown>
              </div>
            ) : (
              <div className="text-xs whitespace-pre-wrap">{msg.content}</div>
            )}
          </div>
        </div>
      ))}

      {/* Loading indicator */}
      {loading && (
        <div className="flex gap-2">
          <div className={`w-7 h-7 rounded-full ${styles.accentBg} ${styles.cardText} flex items-center justify-center shrink-0`}>
            <Bot size={14} />
          </div>
          <div className={`${styles.sidebarBg} rounded-xl px-4 py-2.5`}>
            <div className="flex gap-1">
              <span className={`w-1.5 h-1.5 ${styles.sidebarBg} rounded-full animate-bounce`} style={{ animationDelay: '0ms' }} />
              <span className={`w-1.5 h-1.5 ${styles.sidebarBg} rounded-full animate-bounce`} style={{ animationDelay: '150ms' }} />
              <span className={`w-1.5 h-1.5 ${styles.sidebarBg} rounded-full animate-bounce`} style={{ animationDelay: '300ms' }} />
            </div>
          </div>
        </div>
      )}

      <div ref={messagesEndRef} />
    </div>
  );
};

export default CopilotPanelMessages;
