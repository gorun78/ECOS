/**
 * Security & Audit control-plane API — 安全策略管理页 + 审计查询页
 * （docs/30-设计/详细设计-01-安全域-security与审计底座-2026-09-28.md §三 B 章）。
 *
 * 单通道：数据只经 gateway→security-engine REST（禁前端拼引擎端点）。
 * 传输走 apiFetch（/api 前缀 + Bearer token + UTF-8 query 编码）。
 * ApiResponse 信封 { code, errorCode, message, data, traceId }：本文件统一拆 .data。
 * 离线 503 显式穿透（禁 mock）：抛出原错误，页面保留上次快照 + 顶部错误条。
 */
import { apiFetch } from "../api";
import { authHeaders } from "./auth";

/** 从抓出的错误里尽力提取 traceId（ApiResponse 信封或回显文本）。 */
export function errorTraceId(e: unknown): string | undefined {
  const rec = (e ?? {}) as {
    traceId?: string;
    body?: { traceId?: string };
    message?: string;
  };
  if (rec.traceId) return rec.traceId;
  if (rec.body && typeof rec.body === "object" && rec.body.traceId) return rec.body.traceId;
  if (rec.message) {
    const m = String(rec.message).match(/traceId[:"\s]+([0-9a-fA-F-]{8,})/i);
    if (m) return m[1];
  }
  return undefined;
}

function unwrap<T>(json: unknown): T {
  if (json && typeof json === "object" && "data" in (json as Record<string, unknown>)) {
    const d = (json as Record<string, unknown>).data;
    if (d !== undefined && d !== null) return d as T;
  }
  return json as T;
}

// ── RLS 策略（GET/POST/PUT/DELETE /api/v1/security/rls/policies*）────

export interface RlsBinding {
  name: string;
  source: string;
  value?: string;
  items?: string[];
}

export interface RlsPolicy {
  id: string;
  policyName: string;
  tableName: string;
  resourceId?: string;
  /** 参数化谓词模板 `department_id = :userDeptId` */
  predicateTemplate?: string;
  filterExpr?: string;
  bindings?: RlsBinding[];
  role?: string;
  user?: string;
  priority?: number;
  enabled?: boolean;
  versionNo?: string;
  description?: string;
  updatedAt?: string;
}

export interface RlsPolicyInput {
  policyName: string;
  tableName: string;
  predicateTemplate: string;
  bindings: RlsBinding[];
  role?: string;
  user?: string;
  priority?: number;
  enabled?: boolean;
  description?: string;
}

export async function listRlsPolicies(tableName?: string): Promise<RlsPolicy[]> {
  const qs = tableName ? `?tableName=${encodeURIComponent(tableName)}` : "";
  const json = await apiFetch<unknown>(`/v1/security/rls/policies${qs}`);
  const d = unwrap<unknown>(json);
  return Array.isArray(d) ? (d as RlsPolicy[]) : [];
}

export async function createRlsPolicy(input: RlsPolicyInput): Promise<RlsPolicy> {
  const json = await apiFetch<unknown>("/v1/security/rls/policies", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(input),
  });
  return unwrap<RlsPolicy>(json);
}

export async function updateRlsPolicy(id: string, input: RlsPolicyInput): Promise<RlsPolicy> {
  const json = await apiFetch<unknown>(`/v1/security/rls/policies/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: authHeaders(),
    body: JSON.stringify(input),
  });
  return unwrap<RlsPolicy>(json);
}

export async function deleteRlsPolicy(id: string): Promise<void> {
  await apiFetch(`/v1/security/rls/policies/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: authHeaders(),
  });
}

// ── CLS 策略（/api/v1/security/cls/policies*）────────────────────────

export interface ClsPolicy {
  id: string;
  policyName: string;
  tableName: string;
  resourceId?: string;
  visibleCols?: string[];
  blockedCols?: string[];
  mode?: string; // allow | deny
  role?: string;
  user?: string;
  priority?: number;
  enabled?: boolean;
  description?: string;
  updatedAt?: string;
}

export async function listClsPolicies(tableName?: string): Promise<ClsPolicy[]> {
  const qs = tableName ? `?tableName=${encodeURIComponent(tableName)}` : "";
  const json = await apiFetch<unknown>(`/v1/security/cls/policies${qs}`);
  const d = unwrap<unknown>(json);
  return Array.isArray(d) ? (d as ClsPolicy[]) : [];
}

// ── 脱敏（GET /api/v1/security/masking/demo，POST /apply）──────────────

export interface MaskSample {
  rule?: string;
  raw?: string;
  masked?: string;
}

export interface MaskingDemo {
  samples: MaskSample[];
  supportedRules: string[];
  description?: string;
}

export async function fetchMaskingDemo(): Promise<MaskingDemo> {
  const json = await apiFetch<unknown>("/v1/security/masking/demo");
  const d = unwrap<Record<string, unknown>>(json) ?? {};
  return {
    samples: Array.isArray(d.samples) ? (d.samples as MaskSample[]) : [],
    supportedRules: Array.isArray(d.supportedRules) ? (d.supportedRules as string[]) : [],
    description: typeof d.description === "string" ? d.description : undefined,
  };
}

// ── ABAC 策略（/api/v1/security/abac/policies）────────────────────────

export interface AbacPolicy {
  id: string;
  policyId?: string;
  policyName?: string;
  subjectCondition?: string;
  resourceCondition?: string;
  actionCondition?: string;
  environmentCondition?: string;
  effect?: string; // ALLOW | DENY
  priority?: number;
  scopeType?: string; // GLOBAL | TENANT | ORG
  scopeId?: string;
  description?: string;
  updatedAt?: string;
}

export interface AbacPage {
  items: AbacPolicy[];
  total: number;
  page: number;
  pageSize: number;
}

export async function listAbacPolicies(
  keyword?: string,
  page = 1,
  pageSize = 20
): Promise<AbacPage> {
  const sp = new URLSearchParams();
  if (keyword) sp.set("keyword", keyword);
  sp.set("page", String(page));
  sp.set("pageSize", String(pageSize));
  const json = await apiFetch<unknown>(`/v1/security/abac/policies?${sp.toString()}`);
  const d = unwrap<Record<string, unknown>>(json) ?? {};
    const raw = d.data ?? d.items ?? d.records ?? d.rows;
    const items = Array.isArray(raw)
      ? (raw as AbacPolicy[]).map((p) => ({ ...p, id: p.id ?? p.policyId ?? "" }))
      : [];
  return {
    items,
    total: typeof d.total === "number" ? d.total : items.length,
    page: typeof d.page === "number" ? d.page : page,
    pageSize: typeof d.pageSize === "number" ? d.pageSize : pageSize,
  };
}

// ── 加解密审计（/api/v1/security/audit/crypto/logs, /verify）──────────

export interface CryptoAuditLog {
  id: string;
  eventType?: string;
  resource?: string;
  action?: string;
  operatorId?: string;
  payload?: string;
  prevHash?: string;
  currentHash?: string;
  timestamp?: number | string;
  verified?: boolean;
  [k: string]: unknown;
}

export interface CryptoVerifyResult {
  valid?: boolean;
  total?: number;
  brokenAt?: (number | string)[];
  [k: string]: unknown;
}

export async function listCryptoAuditLogs(page = 1, pageSize = 20): Promise<{ items: CryptoAuditLog[]; total: number }> {
  const json = await apiFetch<unknown>(
    `/v1/security/audit/crypto/logs?page=${page}&pageSize=${pageSize}`
  );
  const d = unwrap<Record<string, unknown>>(json) ?? {};
  const raw = d.data ?? d.items ?? d.rows ?? d.records;
  const items = Array.isArray(raw) ? (raw as CryptoAuditLog[]) : [];
  return { items, total: typeof d.total === "number" ? d.total : items.length };
}

export async function verifyCryptoAudit(): Promise<CryptoVerifyResult> {
  const json = await apiFetch<unknown>("/v1/security/audit/crypto/verify");
  return unwrap<CryptoVerifyResult>(json);
}

// ── 敏感数据目录 / 豁免登记（只读，登记表为唯一源；/api/v1/security/assets）──

export interface SecurityAsset {
  id?: string;
  assetKey: string;
  kind?: string; // COLUMN | TABLE
  sensitivity?: string; // S0..S4
  guardCombo?: string;
  exemptionRef?: string;
  ownerRole?: string;
  description?: string;
  versionNo?: string;
  isDeleted?: number;
  tenantId?: string;
}

export async function listSecurityAssets(tenantId?: string, assetKeyLike?: string): Promise<SecurityAsset[]> {
  const sp = new URLSearchParams();
  if (tenantId) sp.set("tenantId", tenantId);
  if (assetKeyLike) sp.set("assetKeyLike", assetKeyLike);
  const qs = sp.toString() ? `?${sp.toString()}` : "";
  const json = await apiFetch<unknown>(`/v1/security/assets${qs}`);
  const d = unwrap<unknown>(json);
  return Array.isArray(d) ? (d as SecurityAsset[]) : [];
}

// ── 审计查询（/api/v1/security/audit/logs*、/verify-chain、/stats）──────

export interface AuditLog {
  id: string;
  eventId?: string;
  userId?: string;
  action?: string;
  eventType?: string;
  resource?: string;
  resourceType?: string;
  result?: string; // SUCCESS | FAILURE
  ipAddress?: string;
  userAgent?: string;
  requestId?: string;
  traceId?: string;
  timestamp?: string;
  duration?: number;
  detailJson?: string | Record<string, unknown>;
  /** 链验证字段 */
  chainStatus?: string; // ok | broken | unstamped
  prevHash?: string;
  currentHash?: string;
}

export interface AuditPage {
  items: AuditLog[];
  total: number;
  page: number;
  pageSize: number;
}

export interface AuditQuery {
  from?: string;
  to?: string;
  userId?: string;
  action?: string;
  result?: "all" | "success" | "denied";
  traceId?: string;
  page?: number;
  size?: number;
}

export async function listAuditLogs(q: AuditQuery): Promise<AuditPage> {
  const sp = new URLSearchParams();
  if (q.userId) sp.set("userId", q.userId);
  if (q.action) sp.set("action", q.action);
  if (q.result && q.result !== "all") sp.set("result", q.result);
  if (q.traceId) sp.set("traceId", q.traceId);
  if (q.from) sp.set("startTime", q.from);
  if (q.to) sp.set("endTime", q.to);
  sp.set("page", String(q.page ?? 1));
  sp.set("pageSize", String(q.size ?? 20));
  const json = await apiFetch<unknown>(`/v1/security/audit/logs?${sp.toString()}`);
  const d = unwrap<Record<string, unknown>>(json) ?? {};
  const raw = d.data ?? d.items ?? d.rows ?? d.records ?? (Array.isArray(d) ? d : undefined);
  let items = Array.isArray(raw) ? (raw as AuditLog[]) : [];
  // result 过滤（success/denied）前端兜底，避免后端不支持该参数
  if (q.result && q.result !== "all") {
    items = items.filter((a) => {
      const r = String(a.result ?? "").toUpperCase();
      return q.result === "success" ? r.includes("SUCCESS") : r.includes("FAIL") || r.includes("DENY");
    });
  }
  if (q.traceId) {
    items = items.filter((a) =>
      String(a.traceId ?? a.requestId ?? "").includes(q.traceId as string)
    );
  }
  return {
    items,
    total: typeof d.total === "number" ? d.total : items.length,
    page: typeof d.page === "number" ? d.page : q.page ?? 1,
    pageSize: typeof d.pageSize === "number" ? d.pageSize : q.size ?? 20,
  };
}

export async function getAuditLog(id: string): Promise<AuditLog> {
  const json = await apiFetch<unknown>(`/v1/security/audit/logs/${encodeURIComponent(id)}`);
  return unwrap<AuditLog>(json);
}

export interface ChainVerifyResult {
  valid: boolean;
  totalChecked?: number;
  brokenAt?: (number | string)[];
  algorithm?: string;
  from?: number | string;
  to?: number | string;
}

export async function verifyChain(from?: number, to?: number): Promise<ChainVerifyResult> {
  const body: { from?: number; to?: number } = {};
  if (from != null) body.from = from;
  if (to != null) body.to = to;
  const json = await apiFetch<unknown>("/v1/security/audit/verify-chain", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(body),
  });
  const d = unwrap<Record<string, unknown> | null>(json);
  if (!d || typeof d !== "object") {
    return { valid: false, brokenAt: [] };
  }
  return {
    valid: d.valid === true,
    totalChecked: typeof d.totalChecked === "number" ? d.totalChecked : undefined,
    brokenAt: Array.isArray(d.brokenAt) ? (d.brokenAt as (number | string)[]) : [],
    algorithm: typeof d.algorithm === "string" ? d.algorithm : undefined,
    from: d.from != null ? (d.from as number | string) : undefined,
    to: d.to != null ? (d.to as number | string) : undefined,
  };
}
