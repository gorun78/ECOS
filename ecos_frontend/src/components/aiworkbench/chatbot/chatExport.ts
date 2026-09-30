/**
 * chatExport — 会话导出（Markdown / JSON）纯逻辑
 *
 * 从 `components/aiworkbench/chatbot/ChatPanel.tsx` 的 handleExport 机械抽取
 * （H6-T4 组件行数治理）：导出行序、BOM 前缀、文件名模板与 Blob 下载动作逐字保留；
 * 空列表守卫、下拉关闭与 toast 仍由 ChatPanel 负责。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import type { AIPAgent } from '../../../types/aiworkbench';
import type { ChatMessage } from './chatbotTypes';

type TranslateFn = (key: string, params?: Record<string, string | number>) => string;

export interface ChatExportPayload {
  content: string;
  filename: string;
  mimeType: string;
}

export function buildChatExport(
  format: 'md' | 'json',
  msgs: ChatMessage[],
  activeChatbot: AIPAgent,
  t: TranslateFn,
): ChatExportPayload {
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

  return { content, filename, mimeType };
}

export function downloadChatExport({ content, filename, mimeType }: ChatExportPayload): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
