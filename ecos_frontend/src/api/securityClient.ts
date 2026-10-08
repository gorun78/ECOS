/**
 * securityClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import type {
  AuditEvent,
  AuditStats,
} from "../types";
import { apiFetch, apiFetchData, toNetworkError, notifyNetworkDown } from "../services/httpClient";

// ── Security Profile ───────────────────────────────────────
export interface SecurityProfile {
  clearanceLevel: number;
  linkedWorkstation: string;
  auditMode: string;
  sandboxMandatory: boolean;
  // Scope
  scopeType?: string;
  tenantId?: string;
  orgId?: string;
  // Password policy
  passwordMinLength?: number;
  mfaEnabled?: boolean;
  passwordExpireDays?: number;
  // Session management
  sessionTimeout?: number;
  maxConcurrentSessions?: number;
}

/** Fetch the current security profile — GET /api/v1/security/profile */
export async function fetchSecurityProfile(params?: string): Promise<SecurityProfile> {
  const path = params ? `/v1/security/profile${params}` : "/v1/security/profile";
  const json = await apiFetch<{ code: number; data: SecurityProfile }>(path);
  return json.data;
}

/** Update the security profile — PUT /api/v1/security/profile */
export async function updateSecurityProfile(
  body: Partial<SecurityProfile>
): Promise<SecurityProfile> {
  const json = await apiFetch<{ code: number; data: SecurityProfile }>("/v1/security/profile", {
    method: "PUT",
    body: JSON.stringify(body),
  });
  return json.data;
}

// ── Per-User Security Profile (td_user_security_profile) ─────

/** Fetch a specific user's security profile — GET /api/v1/security-profiles/user/{userId} */
export async function fetchUserSecurityProfile(userId: string): Promise<SecurityProfile & { levelName?: string; isDefault?: boolean }> {
  try {
    return await apiFetchData<SecurityProfile & { levelName?: string; isDefault?: boolean }>(
      `/api/v1/security-profiles/user/${userId}`
    );
  } catch (e) {
    console.warn("fetchUserSecurityProfile: backend unavailable", e);
    return { clearanceLevel: 0, linkedWorkstation: "", auditMode: "standard", sandboxMandatory: false };
  }
}

/** Update a specific user's security profile — PUT /api/v1/security-profiles/user/{userId} */
export async function updateUserSecurityProfile(
  userId: string,
  body: Partial<SecurityProfile>
): Promise<SecurityProfile & { levelName?: string }> {
  return apiFetchData<SecurityProfile & { levelName?: string }>(
    `/api/v1/security-profiles/user/${userId}`,
    { method: "PUT", body: JSON.stringify(body) }
  );
}

// ── Audit Logs ──────────────────────────────────────────
// 对接 AuditController (/api/v1/audit/logs)
export async function fetchAuditLogs(
  userId?: string,
  action?: string,
  resourceType?: string,
  page = 1,
  pageSize = 50
): Promise<{ data: AuditEvent[]; total: number; page: number; pageSize: number }> {
    const params = new URLSearchParams();
    if (userId) params.set('userId', userId);
    if (action) params.set('action', action);
    if (resourceType) params.set('resourceType', resourceType);
    params.set('page', String(page));
    params.set('pageSize', String(pageSize));
    try {
      return await apiFetchData(`/api/v1/audit/logs?${params.toString()}`);
    } catch (e) {
      // T1: catch must propagate — no silent empty return. Surface the
      // outage to the global NetworkErrorBanner, then rethrow a typed
      // NetworkError so the caller can render a real error state.
      notifyNetworkDown(e);
      throw toNetworkError(e, 'audit/logs');
    }
}

// ── Audit Stats ───────────────────────────────────────────
// 对接 AuditController: GET /api/v1/audit/stats
export async function fetchAuditStats(): Promise<AuditStats> {
  try {
    return await apiFetchData<AuditStats>('/api/v1/audit/stats');
  } catch (e) {
    console.warn("fetchAuditStats: backend unavailable", e);
    return { todayCount: 0, failureCount: 0, activeUsers: 0, anomalyIps: 0 };
  }
}

// ── Crypto Audit Logs ────────────────────────────────────
// 对接 CryptoAuditController: POST /api/v1/audit/crypto/record, GET /api/v1/audit/crypto/logs, GET /api/v1/audit/crypto/verify, GET /api/v1/audit/crypto/logs/{id}

export interface CryptAuditLog {
  id: number;
  eventType: string;
  resource: string;
  action: string;
  operatorId: string;
  payload?: string;
  hash: string;
  previousHash: string;
  timestamp: string;
  verified: boolean;
}

export interface CryptAuditLogPage {
  data: CryptAuditLog[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CryptAuditVerifyResult {
  intact: boolean;
  totalBlocks: number;
  tamperedBlocks: number[];
  message: string;
}

export async function fetchCryptAuditLogs(
  keyword?: string,
  page = 1,
  pageSize = 20
): Promise<CryptAuditLogPage> {
    const params = new URLSearchParams();
    if (keyword) params.set("keyword", keyword);
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    try {
      return await apiFetchData<CryptAuditLogPage>(
        `/v1/audit/crypto/logs?${params.toString()}`
      );
    } catch (e) {
      // T1: same propagation rule as fetchAuditLogs — no silent empty
      // return. Surface the outage then rethrow a typed NetworkError.
      notifyNetworkDown(e);
      throw toNetworkError(e, 'audit/crypto/logs');
    }
}

export async function fetchCryptAuditVerify(): Promise<CryptAuditVerifyResult> {
  try {
    return await apiFetchData<CryptAuditVerifyResult>("/v1/audit/crypto/verify");
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Chain verification failed");
  }
}

export async function postCryptAuditRecord(body: {
  eventType: string;
  resource: string;
  action: string;
  operatorId: string;
  payload?: string;
}): Promise<CryptAuditLog> {
  try {
    return await apiFetch<CryptAuditLog>("/v1/audit/crypto/record", {
      method: "POST",
      body: JSON.stringify(body),
    });
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Failed to post crypto audit record");
  }
}

export async function fetchCryptAuditLogById(id: number): Promise<CryptAuditLog> {
  try {
    return await apiFetch<CryptAuditLog>(`/v1/audit/crypto/logs/${id}`);
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Failed to fetch crypto audit log");
  }
}


// ── Security Policies (RLS / CLS / ABAC Evaluate / Masking) ────────────────────────

export interface RlsPolicy {
  id?: string | number;
  policyName: string;
  tableName: string;
  filterExpression: string;
  roles: string[];
  status: string;
  description?: string;
  priority?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClsPolicy {
  id?: string | number;
  policyName: string;
  tableName: string;
  visibleColumns: string[];
  blockedColumns: string[];
  roles: string[];
  status: string;
  description?: string;
  priority?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AbacEvaluateRequest {
  subject: { userId: string; roles: string[] };
  resource: { type: string; id: string };
  action: string;
  context?: Record<string, any>;
}

export interface AbacEvaluateResponse {
  allowed: boolean;
  message?: string;
  details?: Record<string, any>;
}

export interface MaskRequest {
  input: string;
  maskType: string;
}

export interface MaskResponse {
  masked: string;
  maskType: string;
}

/** Map backend RLS snake_case → frontend camelCase */
function mapRlsPolicy(raw: any): RlsPolicy {
  return {
    id: raw.id,
    policyName: raw.policy_name || raw.policyName || '',
    tableName: raw.table_name || raw.tableName || '',
    filterExpression: raw.filter_expr || raw.filterExpression || '',
    roles: raw.role_id ? [raw.role_id] : (raw.roles || []),
    status: raw.enabled === false ? 'INACTIVE' : 'ACTIVE',
    description: raw.description || '',
    priority: raw.priority ?? 0,
    createdAt: raw.created_at || raw.createdAt,
    updatedAt: raw.updated_at || raw.updatedAt,
  };
}

/** Map frontend RLS → backend request body */
function mapToRlsBackend(p: Partial<RlsPolicy>): Record<string, any> {
  return {
    policyName: p.policyName,
    tableName: p.tableName,
    filterExpression: p.filterExpression,
    roleId: (p.roles || [])[0] || null,
    status: p.status || 'ACTIVE',
    description: p.description || '',
    priority: p.priority ?? 0,
  };
}

/** Map backend CLS snake_case → frontend camelCase */
function mapClsPolicy(raw: any): ClsPolicy {
  let visibleCols: string[] = [];
  let blockedCols: string[] = [];
  try {
    visibleCols = typeof raw.visible_cols === 'string' ? JSON.parse(raw.visible_cols) : (raw.visible_cols || raw.visibleColumns || []);
    blockedCols = typeof raw.blocked_cols === 'string' ? JSON.parse(raw.blocked_cols) : (raw.blocked_cols || raw.blockedColumns || []);
  } catch { /* ignore parse error */ }
  return {
    id: raw.id,
    policyName: raw.policy_name || raw.policyName || '',
    tableName: raw.table_name || raw.tableName || '',
    visibleColumns: visibleCols,
    blockedColumns: blockedCols,
    roles: raw.role_id ? [raw.role_id] : (raw.roles || []),
    status: raw.enabled === false ? 'INACTIVE' : 'ACTIVE',
    description: raw.description || '',
    priority: raw.priority ?? 0,
    createdAt: raw.created_at || raw.createdAt,
    updatedAt: raw.updated_at || raw.updatedAt,
  };
}

/** Map frontend CLS → backend request body */
function mapToClsBackend(p: Partial<ClsPolicy>): Record<string, any> {
  return {
    policyName: p.policyName,
    tableName: p.tableName,
    visibleColumns: p.visibleColumns || [],
    blockedColumns: p.blockedColumns || [],
    roleId: (p.roles || [])[0] || null,
    status: p.status || 'ACTIVE',
    description: p.description || '',
    priority: p.priority ?? 0,
  };
}

/** GET /api/v1/security/rls/policies — 列表RLS策略 */
export async function fetchRlsPolicies(params?: {
  tableName?: string; page?: number; pageSize?: number;
}): Promise<{ data: RlsPolicy[]; total: number }> {
  try {
    const sp = new URLSearchParams();
    if (params?.tableName) sp.set('tableName', params.tableName);
    sp.set('page', String(params?.page || 1));
    sp.set('pageSize', String(params?.pageSize || 50));
    const raw = await apiFetchData<any[]>(`/api/v1/security/rls/policies?${sp.toString()}`);
    const list = Array.isArray(raw) ? raw.map(mapRlsPolicy) : [];
    return { data: list, total: list.length };
  } catch (e) {
    console.warn('fetchRlsPolicies: backend unavailable', e);
    return { data: [], total: 0 };
  }
}

/** POST /api/v1/security/rls/policies — 创建RLS策略 */
export async function createRlsPolicy(data: Partial<RlsPolicy>): Promise<RlsPolicy> {
  const res = await apiFetchData<any>('/api/v1/security/rls/policies', {
    method: 'POST',
    body: JSON.stringify(mapToRlsBackend(data)),
  });
  return mapRlsPolicy(res);
}

/** PUT /api/v1/security/rls/policies/{id} — 更新RLS策略 */
export async function updateRlsPolicy(id: string | number, data: Partial<RlsPolicy>): Promise<RlsPolicy> {
  const res = await apiFetchData<any>(`/api/v1/security/rls/policies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(mapToRlsBackend(data)),
  });
  return mapRlsPolicy(res);
}

/** DELETE /api/v1/security/rls/policies/{id} — 删除RLS策略 */
export async function deleteRlsPolicy(id: string | number): Promise<void> {
  await apiFetchData(`/api/v1/security/rls/policies/${id}`, { method: 'DELETE' });
}

/** GET /api/v1/security/cls/policies — 列表CLS策略 */
export async function fetchClsPolicies(params?: {
  tableName?: string; page?: number; pageSize?: number;
}): Promise<{ data: ClsPolicy[]; total: number }> {
  try {
    const sp = new URLSearchParams();
    if (params?.tableName) sp.set('tableName', params.tableName);
    sp.set('page', String(params?.page || 1));
    sp.set('pageSize', String(params?.pageSize || 50));
    const raw = await apiFetchData<any[]>(`/api/v1/security/cls/policies?${sp.toString()}`);
    const list = Array.isArray(raw) ? raw.map(mapClsPolicy) : [];
    return { data: list, total: list.length };
  } catch (e) {
    console.warn('fetchClsPolicies: backend unavailable', e);
    return { data: [], total: 0 };
  }
}

/** POST /api/v1/security/cls/policies — 创建CLS策略 */
export async function createClsPolicy(data: Partial<ClsPolicy>): Promise<ClsPolicy> {
  const res = await apiFetchData<any>('/api/v1/security/cls/policies', {
    method: 'POST',
    body: JSON.stringify(mapToClsBackend(data)),
  });
  return mapClsPolicy(res);
}

/** PUT /api/v1/security/cls/policies/{id} — 更新CLS策略 */
export async function updateClsPolicy(id: string | number, data: Partial<ClsPolicy>): Promise<ClsPolicy> {
  const res = await apiFetchData<any>(`/api/v1/security/cls/policies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(mapToClsBackend(data)),
  });
  return mapClsPolicy(res);
}

/** DELETE /api/v1/security/cls/policies/{id} — 删除CLS策略 */
export async function deleteClsPolicy(id: string | number): Promise<void> {
  await apiFetchData(`/api/v1/security/cls/policies/${id}`, { method: 'DELETE' });
}

/** POST /api/v1/security/policy/evaluate — ABAC策略评估 */
export async function evaluateAbacPolicy(
  data: AbacEvaluateRequest
): Promise<AbacEvaluateResponse> {
  const raw = await apiFetchData<any>('/api/v1/security/policy/evaluate', {
    method: 'POST',
    body: JSON.stringify(data),
  });
  // 后端返回 {input, result: {allow, policy, opaStatus, source}}
  const result = raw?.result || raw;
  return {
    allowed: result.allow ?? result.allowed ?? false,
    message: result.source ? `来源: ${result.source}, OPA状态: ${result.opaStatus ?? 'N/A'}` : undefined,
    details: result,
  };
}

/** POST /api/v1/data-masking/apply — 数据脱敏 */
export async function maskData(data: MaskRequest): Promise<MaskResponse> {
  // 后端规则名: email, phone, idCard (小写); 前端: SHA256/PHONE/EMAIL/ID_CARD/AMOUNT
  const ruleMap: Record<string, string> = {
    PHONE: 'phone', EMAIL: 'email', ID_CARD: 'idCard',
    SHA256: 'phone', AMOUNT: 'phone', // 不支持的类型降级到phone
  };
  const rule = ruleMap[data.maskType] || data.maskType.toLowerCase();
  const raw = await apiFetchData<any>('/api/v1/data-masking/apply', {
    method: 'POST',
    body: JSON.stringify({ data: [data.input], rules: [rule] }),
  });
  // 后端返回 {total, results: [{original, masked, rule, error?}]}
  const firstResult = raw?.results?.[0] || {};
  return {
    masked: firstResult.masked || '',
    maskType: firstResult.rule || data.maskType,
  };
}


// ── ABAC Policy Manager (P1-1.4) ────────────────────────
export interface AbacPolicy {
  id: string | number;
  name: string;
  resource: string;
  action: string;
  effect: string;
  conditionExpression: string;
  priority: number;
  scopeType?: string;
  scopeId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AbacPolicyListResponse {
  data: AbacPolicy[];
  total: number;
  page: number;
  pageSize: number;
}

/** Map backend AbacPolicy fields → frontend AbacPolicy */
function mapBackendPolicy(raw: any): AbacPolicy {
  return {
    id: raw.policyId || raw.id,
    name: raw.policyName || raw.name || '',
    resource: raw.resourceCondition || raw.resource || '',
    action: raw.actionCondition || raw.action || '',
    effect: raw.effect || '',
    conditionExpression: raw.subjectCondition || raw.environmentCondition || raw.conditionExpression || '',
    priority: raw.priority ?? 100,
    scopeType: raw.scopeType,
    scopeId: raw.scopeId,
    createdAt: raw.createdTime || raw.createdAt,
  };
}

/** Map frontend AbacPolicy fields → backend request body */
function mapToBackendPolicy(p: Partial<AbacPolicy>): Record<string, any> {
  return {
    policyName: p.name,
    resourceCondition: p.resource,
    actionCondition: p.action,
    effect: p.effect,
    subjectCondition: p.conditionExpression,
    priority: p.priority,
    scopeType: p.scopeType,
    scopeId: p.scopeId,
  };
}

/** GET /api/v1/abac/policies — list+search+paginate */
export async function fetchAbacPolicies(
  keyword?: string,
  page = 1,
  pageSize = 10
): Promise<AbacPolicyListResponse> {
  try {
    const params = new URLSearchParams();
    if (keyword) params.set("keyword", keyword);
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    const data: any = await apiFetchData(
      `/api/v1/abac/policies?${params.toString()}`
    );
    const rawList: any[] = data?.data || [];
    return {
      data: rawList.map(mapBackendPolicy),
      total: data?.total || 0,
      page: data?.page || page,
      pageSize: data?.pageSize || pageSize,
    };
  } catch (e) {
    console.warn("fetchAbacPolicies: backend unavailable", e);
    return { data: [], total: 0, page, pageSize };
  }
}

/** POST /api/v1/abac/policies — create */
export async function createAbacPolicy(body: Partial<AbacPolicy>): Promise<AbacPolicy> {
  const raw: any = await apiFetchData("/api/v1/abac/policies", {
    method: "POST",
    body: JSON.stringify(mapToBackendPolicy(body)),
  });
  return mapBackendPolicy(raw);
}

/** PUT /api/v1/abac/policies/{id} — update */
export async function updateAbacPolicy(
  id: string | number,
  body: Partial<AbacPolicy>
): Promise<AbacPolicy> {
  const raw: any = await apiFetchData(`/api/v1/abac/policies/${id}`, {
    method: "PUT",
    body: JSON.stringify(mapToBackendPolicy(body)),
  });
  return mapBackendPolicy(raw);
}

/** DELETE /api/v1/abac/policies/{id} — delete */
export async function deleteAbacPolicy(id: string | number): Promise<void> {
  await apiFetchData(`/api/v1/abac/policies/${id}`, { method: "DELETE" });
}


// ── Policy Engine (OPA Rego) ─────────────────────────────

export interface PolicyEngineStatus {
  engine: string;
  status: string;
  connected: boolean;
  opaLatency: string;
  policies: number;
  timestamp: number;
}

export interface PolicyEvalResult {
  allow: boolean;
  result: boolean;
  policy?: string;
  opaStatus?: number;
  details?: string;
}

/** GET /api/v1/policy-engine/policies — 获取所有策略名称列表 */
export async function fetchPolicyEnginePolicies(): Promise<string[]> {
  try {
    const resp = await apiFetch<{ data: string[] }>("/v1/policy-engine/policies");
    return resp.data || [];
  } catch (e) {
    console.warn("fetchPolicyEnginePolicies: backend unavailable", e);
    return [];
  }
}

/** GET /api/v1/policy-engine/policies/{name} — 获取指定策略的 Rego 源码 */
export async function fetchPolicyEnginePolicy(name: string): Promise<string> {
  try {
    const resp = await apiFetch<{ data: { name: string; content: string } }>(`/v1/policy-engine/policies/${encodeURIComponent(name)}`);
    return resp.data?.content || "";
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Failed to fetch policy content");
  }
}

/** PUT /api/v1/policy-engine/policies/{name} — 更新 Rego 策略并热加载 */
export async function updatePolicyEnginePolicy(
  name: string,
  body: { content: string }
): Promise<void> {
  await apiFetch(`/v1/policy-engine/policies/${encodeURIComponent(name)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** POST /api/v1/policy-engine/evaluate — 评估策略 */
export async function evaluatePolicyEngine(body: {
  policy: string;
  input: Record<string, any>;
}): Promise<PolicyEvalResult> {
  const resp = await apiFetch<{ data: { policy: string; allow: boolean; opaStatus: number } }>("/v1/policy-engine/evaluate", {
    method: "POST",
    body: JSON.stringify(body),
  });
  return {
    ...resp.data,
    result: resp.data.allow,
  };
}

/** GET /api/v1/policy-engine/status — OPA 连接状态 */
export async function fetchPolicyEngineStatus(): Promise<PolicyEngineStatus> {
  try {
    const resp = await apiFetch<{ data: { engine: string; status: string; opaLatency: string; policies: number; timestamp: number } }>("/v1/policy-engine/status");
    return {
      ...resp.data,
      connected: resp.data.status === "connected",
    };
  } catch (e) {
    console.warn("fetchPolicyEngineStatus: backend unavailable", e);
    throw e;
  }
}

