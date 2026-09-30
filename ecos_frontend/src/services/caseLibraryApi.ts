/**
 * 案例库 REST 收口 (H6-T2) — 自 src/components/CaseLibraryView.tsx 迁入。
 * 原语义保持：无鉴权头；返回整包 JSON。
 */
const API_BASE = "/cases";

export async function fetchCaseLibrary(): Promise<any> {
  const resp = await fetch(`${API_BASE}`);
  return resp.json();
}

export async function searchCaseLibrary(q: string, k = 10): Promise<any> {
  const resp = await fetch(`${API_BASE}/search?q=${encodeURIComponent(q)}&k=${k}`);
  return resp.json();
}

export async function fetchCaseDetail(id: number): Promise<any> {
  const resp = await fetch(`${API_BASE}/${id}`);
  return resp.json();
}

export async function recordCase(body: Record<string, unknown>): Promise<any> {
  const resp = await fetch(`${API_BASE}/record`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return resp.json();
}
