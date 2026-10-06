/**
 * E4.1 域拆分 · Security Policies (RLS/CLS/ABAC/Mask/Catalog)
 * 原 api.ts L1179-1597
 */
import { apiFetchData } from "./services/httpClient";

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

// ── Data Catalog (table list for RLS/CLS table reference) ────────────────────────

export interface CatalogTable {
  catalogId: string;
  resourceId: string;
  resourceName: string;
  resourceType: string;
  orgName?: string;
  description?: string;
}

/** GET /datanet/catalog/search — 从数据目录获取表/视图列表 */
export async function fetchCatalogTables(keyword?: string): Promise<CatalogTable[]> {
  try {
    const sp = new URLSearchParams();
    sp.set('page', '1');
    sp.set('pageSize', '200');
    if (keyword) sp.set('keyword', keyword);
    const data = await apiFetchData<any[]>(`/datanet/catalog/search?${sp.toString()}`);
    return Array.isArray(data) ? data.map(d => ({
      catalogId: d.catalogId || d.catalog_id || '',
      resourceId: d.resourceId || d.resource_id || '',
      resourceName: d.resourceName || d.resource_name || '',
      resourceType: d.resourceType || d.resource_type || 'TABLE',
      orgName: d.orgName || d.org_name,
      description: d.description,
    })) : [];
  } catch (e) {
    console.warn('fetchCatalogTables: backend unavailable', e);
    return [];
  }
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

// ── Data Masking Demo (P1-5) ─────────────────────────────

export interface DataMaskingDemoData {
  comparisons?: Array<{
    original?: string;
    raw?: string;
    masked?: string;
    type?: string;
    rule?: string;
  }>;
  [key: string]: any;
}

export interface DataMaskingApplyResult {
  original?: string;
  masked?: string;
  detections?: Array<{
    type?: string;
    rule?: string;
    count?: number;
  }>;
  [key: string]: any;
}

/** GET /api/v1/data-masking/demo — shows masked/unmasked comparison cards */
export async function fetchDataMaskingDemo(): Promise<DataMaskingDemoData> {
  try {
    return await apiFetchData<DataMaskingDemoData>("/v1/data-masking/demo");
  } catch (e) {
    console.warn("fetchDataMaskingDemo: backend unavailable", e);
    return { comparisons: [] };
  }
}

/** POST /api/v1/data-masking/apply — input textarea + rules selector → show result */
export async function applyDataMasking(body: {
  text: string;
  rules: string[];
}): Promise<DataMaskingApplyResult> {
  return apiFetchData<DataMaskingApplyResult>("/v1/data-masking/apply", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
