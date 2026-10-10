/**
 * agentStudioHelpers — Agent Studio 的纯类型模块。
 * W204/C186：原 buildMockAgentReply（交互沙箱罐头回复决策）属假真值族，已整体删除；
 * 沙箱回复恒来自真实引擎 POST /api/v1/agent-loop/chat（见 AgentStudioView.tsx）。
 * @license Apache-2.0
 */

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
}
