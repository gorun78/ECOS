/**
 * 告警 REST 收口 (H6-T2) — 自 src/components/AlertPanel.tsx 迁入。
 * 原语义保持：无鉴权头；GET 返回整包 JSON；写操作不解析响应。
 */
const API = "/api/alerts";

export async function fetchAlertRules(): Promise<any> {
  const r = await fetch(`${API}/rules`);
  return r.json();
}

export async function fetchAlertHistory(limit = 20): Promise<any> {
  const r = await fetch(`${API}/history?limit=${limit}`);
  return r.json();
}

export async function alertTest(body: Record<string, unknown>): Promise<void> {
  await fetch(`${API}/test`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function alertAck(id: number): Promise<void> {
  await fetch(`${API}/${id}/ack`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ acknowledged_by: "admin" }),
  });
}

export async function alertCreateRule(rule: Record<string, unknown>): Promise<void> {
  await fetch(`${API}/rules`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(rule),
  });
}

export async function alertDeleteRule(id: number): Promise<void> {
  await fetch(`${API}/rules/${id}`, { method: "DELETE" });
}
