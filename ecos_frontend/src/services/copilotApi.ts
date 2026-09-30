/**
 * Copilot REST 收口 (H6-T2) — 自 src/components/CopilotPanel.tsx 迁入。
 * 原语义保持：无鉴权头；返回 {ok, json}，仅 ok 时解析。
 */
export async function fetchCopilotQuickQuestions(agentType: string): Promise<{ ok: boolean; json?: any }> {
  const res = await fetch(`/api/v1/agent/copilot/quick-questions?agentId=agent-${agentType}`);
  return { ok: res.ok, json: res.ok ? await res.json() : undefined };
}

export async function copilotChat(body: { agentId: string; message: string; sessionId: string }): Promise<{ ok: boolean; json?: any }> {
  const res = await fetch('/api/v1/agent/copilot/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { ok: res.ok, json: res.ok ? await res.json() : undefined };
}
