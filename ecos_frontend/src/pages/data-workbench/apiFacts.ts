/**
 * Data Workbench — 业务事实导入 REST 收口（详细设计-02 B.5 / W66 按域拆分 apiFacts.ts）。
 *
 * 消费 BusinessFactController（/api/v1/datanet/facts），Bearer 由 authOnlyHeaders 注入：
 *   GET  /{factType}/template      — 模板（首行 header + 次行 example + columns/required）
 *   POST /{factType}/import        — 导入（≤100 行/批；返回 batchId/accepted/rejected）
 *   GET  /{factType}               — 分页列表
 *   GET  /batches/{batchId}        — 批次状态（轮询）
 *   POST /{factType}/publish       — 发布批次（PASSED → PUBLISHED）
 *
 * 四类事实：attribution(归属) / stage(环节) / resource(资源) / cost(成本)。
 */
import { authOnlyHeaders } from '../../services/auth';

const FACTS = '/api/v1/datanet/facts';

/** 四类业务事实（详细设计-02 B.5 主入口）。 */
export const FACT_TYPES = ['attribution', 'stage', 'resource', 'cost'] as const;
export type FactType = (typeof FACT_TYPES)[number];

export interface FactTemplate {
  factType: string;
  batchNameSuggested: string;
  currency: string;
  columns: string[];
  required: string[];
  rows: Record<string, unknown>[];
}

/** importBusinessFacts 返回的逐行拒绝（rowNo=行号 / field=字段 / ruleId=规则 ID / message=消息 / suggestion=修复建议）。 */
export interface RejectedRow {
  rowNo: number;
  field: string;
  ruleId: string;
  message: string;
  suggestion: string;
}

export interface FactImportResult {
  batchId: string;
  accepted: number;
  /** 逐行拒绝清单（rowNo/field/ruleId/message/suggestion），非计数。 */
  rejected: RejectedRow[];
  traceId: string;
}

export interface FactBatchStatus {
  batchId: string;
  factType: string;
  accepted: number;
  latestStatus: string;
  updatedAt: string | null;
  publishable: boolean;
}

export interface FactListPage {
  factType: string;
  page: number;
  pageSize: number;
  total: number;
  rows: Record<string, unknown>[];
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...authOnlyHeaders(), ...(init?.headers ?? {}) },
    ...init,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} @ ${path}`);
  let body: { data?: T } & Record<string, unknown> = {};
  try { body = (await res.json()) as typeof body; } catch { body = {}; }
  return (body.data ?? (body as unknown as T)) as T;
}

/** 取模板（含列清单 + 必填清单 + 首行表头/次行示例）。 */
export function getFactTemplate(factType: string): Promise<FactTemplate> {
  return request<FactTemplate>(`${FACTS}/${encodeURIComponent(factType)}/template`);
}

/** 导入（≤100 行/批）。`rejected` 由 service 侧逐行核对；此处 rows 为待导入原始行。 */
export function importFacts(
  factType: string, batchName: string, rows: Record<string, unknown>[],
): Promise<FactImportResult> {
  return request<FactImportResult>(`${FACTS}/${encodeURIComponent(factType)}/import`, {
    method: 'POST',
    body: JSON.stringify({ batchName, rows }),
  });
}

/** 批次状态（轮询 batches/{batchId}?factType=）。 */
export function getFactBatch(factType: string, batchId: string): Promise<FactBatchStatus> {
  const q = new URLSearchParams({ factType });
  return request<FactBatchStatus>(`${FACTS}/batches/${encodeURIComponent(batchId)}?${q.toString()}`);
}

/** 发布批次（仅 PASSED 行生效 → PUBLISHED）。 */
export function publishFactBatch(factType: string, batchId: string): Promise<{ batchId: string; published: number }> {
  return request<{ batchId: string; published: number }>(`${FACTS}/${encodeURIComponent(factType)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ batchId }),
  });
}

/** 分页列表（/batches 或全量；page/size 上限 200）。 */
export function listFacts(
  factType: string, batchId?: string, page = 1, size = 20,
): Promise<FactListPage> {
  const q = new URLSearchParams({ page: String(page), size: String(size) });
  if (batchId) q.set('batchId', batchId);
  return request<FactListPage>(`${FACTS}/${encodeURIComponent(factType)}?${q.toString()}`);
}
