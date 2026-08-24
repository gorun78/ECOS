/**
 * CopilotPanelTypes — 类型定义与常量
 * 从 CopilotPanel 拆分而来，逻辑不变
 * @license Apache-2.0
 */

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

export interface CopilotPanelProps {
  className?: string;
  onClose?: () => void;
}

// ─── Quick actions ────────────────────────────────────

export const QUICK_ACTIONS = [
  { label: '帮我写一个过滤活跃用户的 Pipeline', prompt: '帮我写一个过滤活跃用户的 Pipeline，输出 YAML DSL 定义' },
  { label: '这个表里有哪些异常值？', prompt: '这个表里有哪些异常值？请分析并列出可能的异常检测方法' },
  { label: '推荐数据清洗步骤', prompt: '为我的数据集推荐一套完整的数据清洗 Pipeline 步骤' },
  { label: 'UDF: 计算 RFM 模型', prompt: '帮我写一个 Python UDF 函数，用于计算用户 RFM 模型' },
];
