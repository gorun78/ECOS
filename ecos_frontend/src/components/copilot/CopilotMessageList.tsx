/**
 * AIP Copilot — chat message list + typing indicator
 * Renders the message stream with the lightweight bold ** line parser,
 * and shows the typing bubble when agent is composing.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { Fragment } from 'react';
import LucideIcon from '../LucideIcon';
import { useLanguage } from '../LanguageContext';

interface CopilotMessage {
  id: string;
  sender: 'user' | 'agent';
  text: string;
  timestamp: string;
  isExecuting?: boolean;
}

interface CopilotMessageListProps {
  messages: CopilotMessage[];
  isTyping: boolean;
  scrollRef?: React.MutableRefObject<HTMLDivElement | null>;
}

/**
 * Parse lightweight `**bold**` segments in a message line.
 * Returns an ordered array of strings / <strong> fragments,
 * so callers can map to <p> elements without any external library.
 */
function parseBoldSegments(line: string, keyPrefix: string): Array<React.ReactNode> {
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  const boldRegex = /\*\*(.*?)\*\*/g;
  let match = boldRegex.exec(line);
  while (match !== null) {
    if (match.index > lastIndex) {
      parts.push(line.substring(lastIndex, match.index));
    }
    parts.push(
      <strong
        key={`${keyPrefix}-${match.index}`}
        className={`text-slate-900 font-extrabold`}
      >
        {match[1]}
      </strong>
    );
    lastIndex = boldRegex.lastIndex;
    match = boldRegex.exec(line);
  }
  if (lastIndex < line.length) {
    parts.push(line.substring(lastIndex));
  }
  return parts.length > 0 ? parts : [line];
}

export default function CopilotMessageList({ messages, isTyping, scrollRef }: CopilotMessageListProps) {
  const { t } = useLanguage();
  const agentLabel = t('copilot.chat.agent');
  const userLabel = t('copilot.chat.user');
  const executing = t('copilot.chat.executingAction');

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-slate-50">
      {messages.map((msg) => {
        const isUser = msg.sender === 'user';
        return (
          <div
            key={msg.id}
            className={`flex flex-col ${isUser ? 'items-end' : 'items-start'}`}
          >
            <div className="flex items-center gap-1.5 text-[9px] text-slate-400 mb-1 font-semibold">
              <LucideIcon
                name={isUser ? 'User' : 'Bot'}
                size={10}
                className={isUser ? 'text-slate-500' : 'text-indigo-500'}
              />
              <span>{isUser ? userLabel : agentLabel}</span>
              <span>•</span>
              <span>{msg.timestamp}</span>
            </div>

            <div
              className={`max-w-[85%] rounded-2xl px-3 py-2.5 text-xs leading-relaxed shadow-xs ${
                isUser
                  ? 'bg-blue-600 text-white rounded-tr-none font-medium'
                  : 'bg-white text-slate-800 border border-slate-200/80 rounded-tl-none font-normal'
              }`}
            >
              <div className="whitespace-pre-line space-y-1">
                {msg.text.split('\n').map((line, i) => {
                  const key = `${msg.id}-${i}`;
                  const segments = parseBoldSegments(line, key);
                  return (
                    <Fragment key={key}>
                      {segments.map((seg, j) => (
                        <span key={`${key}-${j}`}>{seg}</span>
                      ))}
                      {i < msg.text.split('\n').length - 1 && <br />}
                    </Fragment>
                  );
                })}
              </div>

              {msg.isExecuting && (
                <div className="mt-3 pt-2.5 border-t border-slate-100 flex flex-col gap-1 text-[9px] font-mono text-slate-500">
                  <div className="flex items-center gap-1.5 text-indigo-600 font-bold">
                    <span className="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-ping" />
                    <span>{executing}</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        );
      })}

      {isTyping && (
        <div className="flex flex-col items-start">
          <div className="flex items-center gap-1 text-[9px] text-slate-400 mb-1 font-semibold">
            <LucideIcon name="Bot" size={10} className="text-indigo-500" />
            <span>{t('copilot.chat.typing')}</span>
          </div>
          <div className="bg-white rounded-2xl px-4 py-3 border border-slate-200/80 rounded-tl-none shadow-xs">
            <div className="flex gap-1.5">
              <span className="w-2 h-2 rounded-full bg-slate-300 animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="w-2 h-2 rounded-full bg-slate-300 animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="w-2 h-2 rounded-full bg-slate-300 animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
          </div>
        </div>
      )}

      <div ref={scrollRef} />
    </div>
  );
}
