/**
 * chatbotTypes — OAG 聊天面板共享类型声明
 *
 * 从 `components/aiworkbench/chatbot/ChatPanel.tsx` 机械抽取（H6-T4 组件行数治理），
 * 字段、可选性与注释逐字保留，供 ChatPanel 与其子组件（进度条 / 元数据卡 / 线程面板）共用。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// ── Thread Types (T7-1) ────────────────────────────────────────────

export interface ChatThread {
  id: string;
  name: string;
  messages: ChatMessage[];
  createdAt: string;
}

// ── OAG Progress Types ──────────────────────────────────────────────

export interface OagStepInfo {
  step: number;
  label: string;
  icon: string;
  status: 'pending' | 'active' | 'completed' | 'error';
  detail?: string;
}

// ── ChatMessage (extended with optional metadata) ───────────────────

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
  metadata?: MessageMetadata;
}

// ── Message Metadata Types ──────────────────────────────────────────

export interface MessageMetadata {
  inputTokens?: number;
  outputTokens?: number;
  latencyMs?: number;
  confidence?: number;
  modelName?: string;
  guardrailStatus?: 'passed' | 'blocked' | 'warning';
}
