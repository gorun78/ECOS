/**
 * Data Quality (DQ) 域 API 切片 — E4.1 域拆分 slice-2 (P0 顶层共因)。
 *
 * 源: 原单源 `src/api.ts` "Data Quality Dashboard" 段 (原 L1021-1071) 逐行抽出;
 * `src/api.ts` 通过 `export *` re-export 保持 168 个 importer 契约零改动。
 *
 * 本域 = `/api/v1/dq/**` 8 函数 (无 interface, 均为 any 载荷; DQ REST 契约见
 * `docs/30-设计` 分册02 D.1/D.4 处置表, 未强类型化)。
 *
 * 依赖面: 仅 `doFetch` (src/services/httpClient)。
 */
import { doFetch } from "./services/httpClient";

const DQ_BASE = "/api/v1/dq";

export async function fetchDqRules(): Promise<any> {
  const resp = await doFetch(`${DQ_BASE}/rules`);
  const arr = resp?.data?.data;  // ApiResponse<{data:[...],total}>
  if (Array.isArray(arr)) return arr;
  return resp?.data || resp || [];
}

export async function fetchDqIssues(): Promise<any> {
  const resp = await doFetch(`${DQ_BASE}/issues`);
  const arr = resp?.data?.data;  // ApiResponse<{data:[...],total}>
  if (Array.isArray(arr)) return arr;
  return resp?.data || resp || [];
}

export async function fetchDqDashboard(): Promise<any> {
  const resp = await doFetch(`${DQ_BASE}/dashboard`);
  return resp?.data || resp || null;
}

export async function fetchDqAll(): Promise<[any, any, any]> {
  const [r, i, d] = await Promise.allSettled([fetchDqRules(), fetchDqIssues(), fetchDqDashboard()]);
  return [
    r.status === "fulfilled" ? r.value : [],
    i.status === "fulfilled" ? i.value : [],
    d.status === "fulfilled" ? d.value : null,
  ];
}

export async function createDqItem(type: string, body: any): Promise<any> {
  return doFetch(`${DQ_BASE}/${type}`, { method: "POST", body: JSON.stringify(body) });
}

export async function updateDqItem(type: string, id: string, body: any): Promise<any> {
  return doFetch(`${DQ_BASE}/${type}/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export async function deleteDqItem(type: string, id: string): Promise<any> {
  return doFetch(`${DQ_BASE}/${type}/${id}`, { method: "DELETE" });
}

export async function runDqCheck(): Promise<any> {
  return doFetch(`${DQ_BASE}/check`, { method: "POST" });
}

export async function resolveDqIssue(issueId: string, body: any): Promise<any> {
  // Backend uses PUT /api/dq/issues/{id} with status in body
  return doFetch(`${DQ_BASE}/issues/${issueId}`, { method: "PUT", body: JSON.stringify(body) });
}
