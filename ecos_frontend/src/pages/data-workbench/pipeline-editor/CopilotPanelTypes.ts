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
  /** true = 后端不可达/无回复时渲染的本地兜底模板（非模型输出，必须显式标注） */
  fallback?: boolean;
}

export interface CopilotPanelProps {
  className?: string;
  onClose?: () => void;
}

// ─── Quick actions ────────────────────────────────────
// label/prompt 走 dw.copilot.quick.* 双语键（无假真理/全 i18n）。

export const QUICK_ACTIONS = [
  { labelKey: 'dw.copilot.quick.1.label', promptKey: 'dw.copilot.quick.1.prompt' },
  { labelKey: 'dw.copilot.quick.2.label', promptKey: 'dw.copilot.quick.2.prompt' },
  { labelKey: 'dw.copilot.quick.3.label', promptKey: 'dw.copilot.quick.3.prompt' },
  { labelKey: 'dw.copilot.quick.4.label', promptKey: 'dw.copilot.quick.4.prompt' },
];
