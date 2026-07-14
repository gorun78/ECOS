/**
 * C2EOS API Service Layer
 * All API calls go through the BFF (server.ts) which proxies to the Java backend.
 * Falls back to mock data when backend is unavailable.
 */
import type {
  DataAsset, EntityDefinition, EntityInstance, AuditEvent,
  AgentDefinition, ToolDefinition, PromptTemplate,
  KnowledgeNode, KnowledgeEdge, Goal, CausalLink, Scenario,
  DataSource, DataResource, DataField,
} from "./types";

// Import mock data as fallback
import {
  MOCK_DATA_ASSETS, MOCK_ONTOLOGY_ENTITIES, MOCK_ENTITY_INSTANCES,
} from "./mockData";

const API_BASE = "/api";

// ── Internal fetch helpers ────────────────────────────────────

/** Global token expiration handler — clears auth and redirects to login */
function handleAuthExpired(): never {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  localStorage.removeItem('roles');
  // Hash-based redirect avoids React Router race conditions
  window.location.hash = '#/login';
  throw new Error('登录已过期，请重新登录');
}

/** Check response status for auth failure */
function checkAuthExpired(status: number): void {
  if (status === 401 || status === 403) handleAuthExpired();
}

/**
 * Standard apiFetch – returns full JSON.
 * Prefixes path with /api and encodes query parameters for Tomcat UTF-8.
 */
export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const finalPath = encodeQueryString(path);
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${finalPath}`, {
    headers,
    ...options,
  });
  if (res.status === 401 || res.status === 403) handleAuthExpired();
  if (!res.ok) throw new Error(`API ${path} returned ${res.status}`);
  return res.json();
}

/**
 * Data-aware fetch – calls an arbitrary URL (no automatic /api prefix),
 * extracts `.data` field from response, handles both {success,data}
 * and {code,data} response formats.
 */
export async function apiFetchData<T>(url: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, {
    headers,
    ...options,
  });
  if (res.status === 401 || res.status === 403) handleAuthExpired();
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  const json = await res.json();
  if (json.success === false) throw new Error(json.message || "Request failed");
  if (json.code && json.code !== 200 && json.code !== 0) throw new Error(json.message || `Error ${json.code}`);
  return json.data !== undefined ? json.data : json;
}

/**
 * Simple doFetch – returns full JSON body from arbitrary URL.
 * Includes Authorization Bearer token from localStorage when available.
 */
async function doFetch(url: string, opts: RequestInit = {}): Promise<any> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  try {
    const r = await fetch(url, { headers, ...opts });
    if (r.status === 401 || r.status === 403) handleAuthExpired();
    if (!r.ok) throw new Error(`${r.status}`);
    const ct = r.headers.get('content-type');
    return ct && ct.includes('application/json') ? await r.json() : null;
  } catch (e) {
    throw e;
  }
}

/** Encode query parameters for Tomcat UTF-8 compatibility */
function encodeQueryString(path: string): string {
  const qIdx = path.indexOf('?');
  if (qIdx < 0) return path;
  const basePath = path.substring(0, qIdx);
  const qs = path.substring(qIdx + 1);
  const params = new URLSearchParams(qs);
  const encoded = new URLSearchParams();
  params.forEach((v, k) => {
    encoded.append(encodeURIComponent(k), encodeURIComponent(v));
  });
  return basePath + '?' + encoded.toString();
}

// ── Auth ────────────────────────────────────────────────
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  username?: string;
  roles?: string[];
}

/** Login — POST /api/v1/auth/login (no auth header, user isn't logged in yet) */
export async function authLogin(body: LoginRequest): Promise<LoginResponse> {
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error("用户名或密码错误");
    throw new Error(`登录失败 (${res.status})`);
  }
  const json = await res.json();
  const data = json.data || json;
  if (!data.accessToken) throw new Error("登录响应无效，缺少 accessToken");
  return data;
}

// ── Datasets ────────────────────────────────────────────
export async function fetchDatasets(): Promise<DataAsset[]> {
  try {
    // Get first active datasource and its resources
    const dsResp = await doFetch("/api/v1/datanet/datasource");
    const sources = dsResp?.data ?? [];
    if (!Array.isArray(sources) || sources.length === 0) {
      console.warn("fetchDatasets: no datasources registered, using mock");
      return MOCK_DATA_ASSETS;
    }
    const active = sources.find((s: any) => s.status === "ACTIVE");
    if (!active) {
      console.warn("fetchDatasets: no active datasource, using mock");
      return MOCK_DATA_ASSETS;
    }
    // Try to get resources from this datasource
    const resResp = await doFetch(`/api/v1/datanet/metadata/resources/${active.datasourceId}`);
    const resources = resResp?.data ?? [];
    const items = Array.isArray(resources) ? resources : [];
    if (items.length === 0) {
      console.warn("fetchDatasets: no resources collected, using mock");
      return MOCK_DATA_ASSETS;
    }
    return items.map((r: any, i: number) => ({
      id: r.tableName || r.name || `ds_${i}`,
      name: r.tableName || r.name || `dataset_${i}`,
      description: r.comment || r.description || "",
      type: "table",
      owner: active.createBy || "data-team",
      domain: r.schema || "public",
      tags: [],
      status: "Healthy" as const,
      qualityScore: 85 + (i % 15),
      rows: r.rowCount || 0,
      columns: r.columnCount || 0,
      storageSize: r.dataSize || "—",
      updatedAt: r.updatedAt || new Date().toISOString().slice(0, 10),
      schema: [],
      qualityRules: [],
      history: [],
      permissions: { owner: [active.createBy || "data-team"], editor: [], viewer: [] },
    }));
  } catch (e) {
    console.warn("fetchDatasets: backend unavailable, using mock", e);
    return MOCK_DATA_ASSETS;
  }
}

export async function fetchDataset(id: string): Promise<DataAsset | null> {
  try {
    // 使用批量端点一次拿到所有资源（含数据源名）
    const all = await fetchAllResources();
    const found = all.find((r: any) =>
      r.resourceId === id || r.resourceName === id || r.tableName === id
    );
    if (found) {
      // 获取字段详情
      let schema: any[] = [];
      try {
        const fieldsResp = await doFetch(`${DATANET_META}/fields/${found.resourceId || id}`);
        const fields: any[] = Array.isArray(fieldsResp?.data) ? fieldsResp.data :
                              Array.isArray(fieldsResp?.data?.data) ? fieldsResp.data.data : [];
        schema = fields.map((f: any) => ({
          name: f.fieldName || f.columnName || f.name || "?",
          type: f.dataType || f.fieldType || f.type || "VARCHAR",
          nullable: f.nullable !== undefined ? f.nullable : true,
          primaryKey: f.primaryKey || false,
          qualityScore: 100,
          description: f.comment || f.remarks || "",
        }));
      } catch (e) { /* schema optional */ }

      return {
        id: found.resourceId || id,
        name: found.resourceName || id,
        description: found.sourcePath || found.datasourceName || "",
        type: found.resourceType === "VIEW" ? "view" : "dataset",
        owner: found.datasourceName || "data-team",
        domain: found.sourcePath?.split(".")[0] || "default",
        tags: [found.resourceType || "TABLE", found.datasourceType || "JDBC"],
        status: "Healthy" as const,
        qualityScore: 85,
        rows: 0,
        columns: schema.length || found.fieldCount || 0,
        storageSize: "—",
        updatedAt: "—",
        schema,
        qualityRules: [],
        history: [],
        permissions: { owner: [found.datasourceName || "data-team"], editor: [], viewer: [] },
      };
    }
  } catch (e) {
    console.warn("fetchDataset: backend unavailable", e);
  }
  // Fallback: try mock data, then construct minimal asset
  const mockAsset = MOCK_DATA_ASSETS.find(a => a.id === id || a.name === id);
  if (mockAsset) return mockAsset;
  // Construct minimal asset so the page isn't blank
  return {
    id,
    name: id,
    description: `Dataset: ${id}`,
    type: "dataset",
    owner: "data-team",
    domain: "default",
    tags: [],
    status: "Healthy" as const,
    qualityScore: 80,
    rows: 0,
    columns: 0,
    storageSize: "—",
    updatedAt: new Date().toISOString().slice(0, 10),
    schema: [],
    qualityRules: [],
    history: [],
    permissions: { owner: ["data-team"], editor: [], viewer: [] },
  };
}

// ── Ontology ────────────────────────────────────────────
export async function fetchOntology(): Promise<{
  entities: EntityDefinition[];
  instances: Record<string, EntityInstance[]>;
}> {
  try {
    const resp = await apiFetchData<{ data?: any[]; records?: any[] }>(`${ONT_BASE}/ont001/entities`);
    const records = resp?.data || resp?.records || [];

    if (records.length > 0) {
      const entities: EntityDefinition[] = records.map((rec: any) => ({
        id: rec.code || rec.id || rec.name,
        name: rec.name || rec.code || "",
        description: rec.description || "",
        properties: (rec.properties || []).map((p: any) => ({
          name: p.code || p.name || "",
          type: (p.propertyType || "string") as any,
          searchable: p.searchableFlag === 1,
          editable: true,
        })),
        relationships: [],
        actions: [],
      }));
      return { entities, instances: {} };
    }
  } catch (e) {
    console.warn("fetchOntology failed, using mock fallback", e);
  }

  // Mock fallback
  return buildMockOntology();
}

function buildMockOntology(): { entities: EntityDefinition[]; instances: Record<string, EntityInstance[]> } {
  const entities: EntityDefinition[] = [
    { id: "ent_customer", name: "客户 (Customer)", description: "企业客户主数据实体", properties: [
      { name: "customer_id", type: "string", searchable: true, editable: false },
      { name: "name", type: "string", searchable: true, editable: true },
      { name: "region", type: "string", searchable: true, editable: true },
      { name: "revenue", type: "number", searchable: false, editable: true },
    ], relationships: [{ name: "has", targetEntity: "ent_order", cardinality: "many" }], actions: [] },
    { id: "ent_order", name: "订单 (Order)", description: "销售订单实体", properties: [
      { name: "order_id", type: "string", searchable: true, editable: false },
      { name: "amount", type: "number", searchable: false, editable: true },
      { name: "status", type: "string", searchable: true, editable: true },
    ], relationships: [{ name: "belongs_to", targetEntity: "ent_customer", cardinality: "one" }, { name: "contains", targetEntity: "ent_product", cardinality: "many" }], actions: [] },
    { id: "ent_product", name: "产品 (Product)", description: "产品主数据", properties: [
      { name: "product_id", type: "string", searchable: true, editable: false },
      { name: "name", type: "string", searchable: true, editable: true },
      { name: "category", type: "string", searchable: true, editable: true },
      { name: "price", type: "number", searchable: false, editable: true },
    ], relationships: [{ name: "part_of", targetEntity: "ent_order", cardinality: "many" }], actions: [] },
    { id: "ent_machine", name: "设备 (Machine)", description: "生产设备实体", properties: [
      { name: "machine_id", type: "string", searchable: true, editable: false },
      { name: "temperature_c", type: "number", searchable: false, editable: true },
      { name: "has_fault", type: "boolean", searchable: true, editable: true },
    ], relationships: [], actions: [] },
  ];
  return { entities, instances: {} };
}

/** Build a fallback EntityDefinition from instance data */
function buildFallbackEntity(name: string, instances: EntityInstance[]): EntityDefinition {
  // Extract property names from the first instance
  const propNames: string[] = [];
  if (instances.length > 0) {
    Object.keys(instances[0].properties).forEach(k => {
      if (!propNames.includes(k)) propNames.push(k);
    });
  }
  
  return {
    id: name,
    name: `${name} Entity`,
    description: `${name} 实体（动态解析）`,
    properties: propNames.map(pn => ({
      name: pn,
      type: "string" as const,
      searchable: pn === "name" || pn === "code",
      editable: true,
    })),
    relationships: [],
    actions: [],
  };
}

// ── Agent Chat ──────────────────────────────────────────
export async function agentChat(
  agentId: string, message: string, promptTemplate?: string, datasetContext?: any
): Promise<{
  success: boolean;
  source: string;
  responseText: string;
  thoughtTrace: { type: string; summary: string }[];
  logId: string;
  // ── Human-in-the-loop: optional Action Proposal returned by the agent ──
  actionProposal?: {
    actionId: string;
    actionName?: string;
    payload?: Record<string, string>;
    status?: "pending" | "approved" | "rejected";
  };
}> {
  return apiFetch("/agent/chat", {
    method: "POST",
    body: JSON.stringify({ agentId, message, promptTemplate, datasetContext }),
  });
}

export async function fetchAgents(): Promise<AgentDefinition[]> {
  try {
    const resp = await apiFetch<{ success: boolean; data: any[] }>("/agent/agents");
    const raw = resp.data || [];
    return raw.map((a: any) => {
      let tools: string[] = [];
      if (a.toolset) {
        try { tools = typeof a.toolset === "string" ? JSON.parse(a.toolset) : a.toolset; } catch { tools = []; }
      } else if (a.tools) {
        tools = a.tools;
      }
      return {
        id: a.id,
        name: a.name,
        role: a.role,
        goal: a.goal || a.description || "",
        tools,
        systemPrompt: a.systemPrompt || "",
        capabilities: a.capabilities || [],
      };
    });
  } catch {
    return [];
  }
}

export async function fetchTools(): Promise<ToolDefinition[]> {
  try {
    const resp = await apiFetch<{ success: boolean; data: ToolDefinition[] }>("/agent/tools");
    return resp.data || [];
  } catch {
    return [];
  }
}

export async function fetchPrompts(): Promise<PromptTemplate[]> {
  try {
    const resp = await apiFetch<{ success: boolean; data: any[] }>("/agent/prompts");
    const raw = resp.data || [];
    return raw.map((p: any) => ({
      id: p.id,
      title: p.name || p.title || "",
      filename: p.filename || `${p.id}.md`,
      content: p.template || p.content || "",
      version: p.version || "1.0",
      category: p.category || "planning",
    }));
  } catch {
    return [];
  }
}

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

// ── Audit Logs ──────────────────────────────────────────
// 对接 AuditController (/api/v1/audit/logs)
export async function fetchAuditLogs(
  userId?: string,
  action?: string,
  resourceType?: string,
  page = 1,
  pageSize = 50
): Promise<{ data: AuditEvent[]; total: number; page: number; pageSize: number }> {
  try {
    const params = new URLSearchParams();
    if (userId) params.set('userId', userId);
    if (action) params.set('action', action);
    if (resourceType) params.set('resourceType', resourceType);
    params.set('page', String(page));
    params.set('pageSize', String(pageSize));
    return await apiFetchData(`/api/v1/audit/logs?${params.toString()}`);
  } catch (e) {
    console.warn("fetchAuditLogs: backend unavailable, returning empty", e);
    return { data: [], total: 0, page, pageSize };
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
  try {
    const params = new URLSearchParams();
    if (keyword) params.set("keyword", keyword);
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    return await apiFetchData<CryptAuditLogPage>(
      `/v1/audit/crypto/logs?${params.toString()}`
    );
  } catch (e) {
    console.warn("fetchCryptAuditLogs: backend unavailable", e);
    return { data: [], total: 0, page, pageSize };
  }
}

export async function fetchCryptAuditVerify(): Promise<CryptAuditVerifyResult> {
  try {
    return await apiFetchData<CryptAuditVerifyResult>("/v1/audit/crypto/verify");
  } catch (e: any) {
    throw new Error(e.message || "Chain verification failed");
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
  } catch (e: any) {
    throw new Error(e.message || "Failed to post crypto audit record");
  }
}

export async function fetchCryptAuditLogById(id: number): Promise<CryptAuditLog> {
  try {
    return await apiFetch<CryptAuditLog>(`/v1/audit/crypto/logs/${id}`);
  } catch (e: any) {
    throw new Error(e.message || "Failed to fetch crypto audit log");
  }
}

// ── Knowledge Graph ─────────────────────────────────────
export async function fetchKnowledgeGraph(): Promise<{
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}> {
  try {
    const resp = await apiFetch<{ success: boolean; data: { nodes: any[]; edges: any[] } }>("/knowledge/graph");
    if (resp.data && resp.data.nodes && resp.data.nodes.length > 0) {
      return {
        nodes: resp.data.nodes.map((n: any) => ({
          id: n.id,
          label: n.label,
          type: n.nodeType || n.type,
          nodeType: n.nodeType,
          description: n.description,
          propertiesJson: n.propertiesJson,
          properties: (() => { try { return typeof n.propertiesJson === 'string' ? JSON.parse(n.propertiesJson) : n.propertiesJson; } catch { return {}; } })(),
          createdAt: n.createdAt,
        })),
        edges: resp.data.edges.map((e: any) => ({
          id: e.id,
          source: e.sourceNodeId || e.source,
          target: e.targetNodeId || e.target,
          sourceNodeId: e.sourceNodeId,
          targetNodeId: e.targetNodeId,
          relationship: e.relationship,
          weight: e.weight,
        })),
      };
    }
    return (resp.data as any) || { nodes: [], edges: [] };
  } catch {
    return { nodes: [], edges: [] };
  }
}

// ── Marketplace ──────────────────────────────────────────
export interface MarketplaceAsset {
  assetId: string;
  name: string;
  type: string;
  owner: string;
  tags: string[];
  views: number;
  stars: number;
  downloads: number;
  createdAt: string;
  summary: string;
  hotScore: number;
}

export async function fetchMarketplaceAssets(
  sort: string = "popular",
  limit: number = 10
): Promise<{ total: number; items: MarketplaceAsset[]; sort: string }> {
  const resp = await apiFetch<{
    success: boolean;
    data: { total: number; items: MarketplaceAsset[]; sort: string };
  }>(`/marketplace/assets?sort=${sort}&limit=${limit}`);
  return resp.data || { total: 0, items: [], sort };
}

export async function requestMarketplaceAccess(
  assetId: string,
  reason: string
): Promise<{ requestId: string; status: string }> {
  return apiFetch("/marketplace/request-access", {
    method: "POST",
    body: JSON.stringify({ assetId, reason }),
  }).then(r => (r as any).data || r);
}

// ── Marketplace P1-4 Browser ────────────────────────────────

export interface MarketplaceBrowserAsset {
  id: string;
  name: string;
  description: string;
  category: string;
  owner: string;
  rating: number;
  popularity: number;
  status: "published" | "draft" | "archived";
  tags: string[];
  createdAt: string;
  updatedAt: string;
  reviews?: MarketplaceReview[];
}

export interface MarketplaceReview {
  id: string;
  userId: string;
  userName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface MarketplaceDashboard {
  totalAssets: number;
  avgRating: number;
  pendingRequests: number;
}

/** GET /api/marketplace/assets — 获取市场资产列表，支持关键词和分类筛选 */
export async function fetchMarketAssets(
  params?: { keyword?: string; category?: string; page?: number; pageSize?: number }
): Promise<{ data: MarketplaceBrowserAsset[]; total: number }> {
  const qs = new URLSearchParams();
  if (params?.keyword) qs.set("keyword", params.keyword);
  if (params?.category) qs.set("category", params.category);
  if (params?.page) qs.set("page", String(params.page));
  if (params?.pageSize) qs.set("pageSize", String(params.pageSize));
  const q = qs.toString();
  const raw = await apiFetch<{ success: boolean; data: { items?: MarketplaceBrowserAsset[]; total?: number; sort?: string } }>(
    `/marketplace/assets${q ? "?" + q : ""}`
  );
  return { data: raw.data?.items || [], total: raw.data?.total || 0 };
}

/** GET /api/marketplace/dashboard — 市场仪表盘统计数据 */
export async function fetchMarketDashboard(): Promise<MarketplaceDashboard> {
  return apiFetch<{ success: boolean; data: MarketplaceDashboard }>("/marketplace/dashboard")
    .then(r => r.data || { totalAssets: 0, avgRating: 0, pendingRequests: 0 });
}

/** POST /api/marketplace/assets — 发布新资产 */
export async function publishMarketAsset(body: {
  name: string;
  description: string;
  category: string;
  tags?: string[];
}): Promise<MarketplaceBrowserAsset> {
  return apiFetch<{ success: boolean; data: MarketplaceBrowserAsset }>("/marketplace/assets", {
    method: "POST",
    body: JSON.stringify(body),
  }).then(r => r.data);
}

/** GET /api/marketplace/search — 搜索市场资产 */
export async function searchMarketAssets(
  keyword: string,
  category?: string
): Promise<MarketplaceBrowserAsset[]> {
  const qs = new URLSearchParams();
  qs.set("keyword", keyword);
  if (category) qs.set("category", category);
  return apiFetch<{ success: boolean; data: MarketplaceBrowserAsset[] }>(
    `/marketplace/search?${qs.toString()}`
  ).then(r => r.data || []);
}

/** GET /api/marketplace/assets/{id} — 获取单个资产详情 */
export async function fetchMarketAssetDetail(id: string): Promise<MarketplaceBrowserAsset> {
  return apiFetch<{ success: boolean; data: MarketplaceBrowserAsset }>(`/marketplace/assets/${id}`)
    .then(r => r.data);
}

/** POST /api/marketplace/request-access — 申请资产访问权限 */
export async function requestAccess(
  assetId: string,
  reason: string
): Promise<{ requestId: string; status: string }> {
  return apiFetch<{ success: boolean; data: { requestId: string; status: string } }>(
    "/marketplace/request-access",
    { method: "POST", body: JSON.stringify({ assetId, reason }) }
  ).then(r => r.data || r as any);
}

// ── Object Explorer (Object Runtime) ─────────────────────────
const OBJ_BASE = "/api/v1/ecos/objects";

export interface ObjectData {
  id: string;
  entityCode: string;
  status: string;
  createdAt: string;
  updatedAt?: string;
  [key: string]: any;
}

export interface SchemaProperty {
  code: string;
  name: string;
  type: string;
  required: boolean;
  searchable: boolean;
}

export async function fetchObjects(entityCode: string, keyword?: string, page = 1, size = 50) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(size) });
  if (keyword) params.set("keyword", keyword);
  return apiFetchData<{ data: ObjectData[]; total: number; page: number; pageSize: number }>(`${OBJ_BASE}/${entityCode}?${params}`);
}

export async function searchObjects(q: string, entityCode?: string) {
  const params = new URLSearchParams({ q, pageSize: "50" });
  if (entityCode) params.set("entityCode", entityCode);
  return apiFetchData<{ data: ObjectData[]; total: number }>(`${OBJ_BASE}/search?${params}`);
}

export async function fetchObjectDetail(entityCode: string, id: string) {
  return apiFetchData<ObjectData & { relations: any[]; timeline: any[] }>(`${OBJ_BASE}/${entityCode}/${id}`);
}

export async function fetchObjectSchema(entityCode: string) {
  return apiFetchData<{ entityCode: string; entityName: string; properties: SchemaProperty[] }>(`${OBJ_BASE}/${entityCode}/schema`);
}

export async function createObject(entityCode: string, properties: Record<string, any>) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}`, {
    method: "POST",
    body: JSON.stringify(properties),
  });
}

export async function updateObject(entityCode: string, id: string, properties: Record<string, any>) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}/${id}`, {
    method: "PUT",
    body: JSON.stringify(properties),
  });
}

export async function changeObjectStatus(entityCode: string, id: string, status: string, override = false) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, override: String(override) }),
  });
}

export async function deleteObject(entityCode: string, id: string) {
  return apiFetchData<null>(`${OBJ_BASE}/${entityCode}/${id}`, { method: "DELETE" });
}

// ── Gap 1: Status transitions ──
export interface ObjectTransition {
  transitionCode: string;
  toStatus: string;
  transitionName: string;
  requireRole: string | null;
}

export interface AvailableTransitions {
  currentStatus: string;
  availableTransitions: ObjectTransition[];
}

export async function fetchAvailableTransitions(entityCode: string, id: string) {
  return apiFetchData<AvailableTransitions>(`${OBJ_BASE}/${entityCode}/${id}/transitions`);
}

export async function executeTransition(entityCode: string, id: string, transition: string, actor: string, comment?: string) {
  return apiFetchData<{ newStatus: string; [key: string]: any }>(`${OBJ_BASE}/${entityCode}/${id}/transition`, {
    method: "POST",
    body: JSON.stringify({ transition, actor, comment: comment || "" }),
  });
}

// ── Gap 2: Object relationships ──
export async function createObjectRelationship(
  entityCode: string,
  id: string,
  body: {
    targetObjectId: string;
    targetEntityCode: string;
    relationshipCode: string;
    relationshipType: string;
    properties?: Record<string, any>;
  }
) {
  return apiFetchData<any>(`${OBJ_BASE}/${entityCode}/${id}/relationships`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Gap 3: Timeline pagination ──
export interface TimelineEvent {
  id: string;
  eventType: string;
  eventDetail: any;
  operator: string;
  createdAt: string;
}

export interface TimelinePage {
  data: TimelineEvent[];
  total: number;
  page: number;
  size: number;
}

export async function fetchObjectTimeline(entityCode: string, id: string, page = 1, size = 20) {
  return apiFetchData<TimelinePage>(`${OBJ_BASE}/${entityCode}/${id}/timeline?page=${page}&size=${size}`);
}

// ── Agent Mesh ───────────────────────────────────────────────
const AGENT_MESH_BASE = "/api/agent-mesh";

export interface AgentMeshAgent {
  id: string;
  name: string;
  role: string;
  description: string;
  systemPrompt: string;
  toolset: string;
  model: string;
  maxIterations: number;
  status: string;
}

export interface AgentMeshMission {
  id: string;
  title: string;
  description: string;
  mode: string;
  status: string;
  inputParams?: string;
  outputResult?: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface AgentMeshTask {
  id: string;
  missionId: string;
  seq: number;
  agentId: string;
  agentName: string;
  instruction: string;
  status: string;
  resultSummary?: string;
  resultDetail?: string;
  errorMessage?: string;
  durationMs?: number;
}

export async function fetchAgentMeshAgents(): Promise<AgentMeshAgent[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/agents`);
}

export async function fetchAgentMeshMissions(limit = 30): Promise<AgentMeshMission[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions?limit=${limit}`);
}

export async function fetchAgentMeshMission(id: string): Promise<{ mission: AgentMeshMission; tasks: AgentMeshTask[] }> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}`);
}

export async function fetchAgentMeshMissionTasks(id: string): Promise<AgentMeshTask[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}/tasks`);
}

export async function createAgentMeshMission(body: any): Promise<AgentMeshMission> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function executeAgentMeshMission(id: string): Promise<AgentMeshMission> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}/execute`, { method: "POST" });
}

// ── Workflow Designer ────────────────────────────────────────
const WF_BASE = "/api/v1/ecos/workflows";

export async function fetchWorkflows(pageSize = 50): Promise<{ data: any[]; total: number }> {
  return apiFetchData(`${WF_BASE}?pageSize=${pageSize}`);
}

export async function fetchWorkflow(id: string): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}`);
}

export async function createWorkflow(body?: any): Promise<any> {
  return apiFetchData(WF_BASE, {
    method: "POST",
    body: JSON.stringify(body || { name: "新建流程", code: "wf_" + Date.now(), workflowType: "APPROVAL" }),
  });
}

export async function updateWorkflow(id: string, body: any): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function publishWorkflow(id: string): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}/publish`, { method: "PATCH" });
}

export async function testWorkflow(id: string, body?: any): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}/test`, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  });
}

// ── Pipeline Builder ──────────────────────────────────────────
const PIPELINE_BASE = "/api/pipeline/definitions";

export interface PipelineDefinition {
  id?: string;
  name: string;
  description?: string;
  nodes: { nodeId: string; type: string; config?: Record<string, any> }[];
  edges: { from: string; to: string; label?: string }[];
  createdAt?: string;
  updatedAt?: string;
  status?: string;
}

export interface PipelineExecution {
  id: string;
  pipelineId: string;
  status: "pending" | "running" | "success" | "failed";
  startedAt?: string;
  finishedAt?: string;
  logs?: string[];
  error?: string;
}

/** GET /api/pipeline/definitions — 列表 */
export async function fetchPipelines(pageSize = 50): Promise<{ data: PipelineDefinition[]; total: number }> {
  return apiFetchData(`${PIPELINE_BASE}?pageSize=${pageSize}`);
}

/** GET /api/pipeline/definitions/{id} — 详情 */
export async function fetchPipeline(id: string): Promise<PipelineDefinition> {
  return apiFetchData(`${PIPELINE_BASE}/${id}`);
}

/** POST /api/pipeline/definitions — 创建 */
export async function createPipeline(body: {
  name: string;
  description?: string;
  nodes?: { nodeId: string; type: string; config?: Record<string, any> }[];
  edges?: { from: string; to: string; label?: string }[];
}): Promise<PipelineDefinition> {
  return apiFetchData(PIPELINE_BASE, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** PUT /api/pipeline/definitions/{id} — 更新 */
export async function updatePipeline(id: string, body: Partial<PipelineDefinition>): Promise<PipelineDefinition> {
  return apiFetchData(`${PIPELINE_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** DELETE /api/pipeline/definitions/{id} — 删除 */
export async function deletePipeline(id: string): Promise<void> {
  await apiFetchData(`${PIPELINE_BASE}/${id}`, { method: "DELETE" });
}

/** POST /api/pipeline/definitions/{id}/execute — 执行 */
export async function executePipeline(id: string): Promise<PipelineExecution> {
  return apiFetchData(`${PIPELINE_BASE}/${id}/execute`, { method: "POST" });
}

/** GET /api/pipeline/executions/{id} — 执行状态 */
export async function getExecution(executionId: string): Promise<PipelineExecution> {
  return apiFetchData(`/api/pipeline/executions/${executionId}`);
}

// ── Ontology Designer ─────────────────────────────────────────
const ONT_BASE = "/api/v1/ecos/ontologies";

export interface OntologyEntity {
  id: string;
  ontologyId: string;
  code: string;
  name: string;
  description?: string;
  entityType: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface OntologyProperty {
  id: string;
  entityId: string;
  code: string;
  name: string;
  propertyType: string;
  functionType?: string;
  functionExpression?: string;
  requiredFlag: number;
  searchableFlag: number;
  sortOrder?: number;
}

export interface OntologyRelationship {
  id: string;
  sourceEntityId: string;
  targetEntityId: string;
  code: string;
  name: string;
  relationshipType: string;
}

export async function fetchOntologyEntities(ontologyId = "ont001"): Promise<OntologyEntity[]> {
  return apiFetchData(`${ONT_BASE}/${ontologyId}/entities`);
}

// ── ObjectExplorer entity list (dynamic from API) ──────────────
export interface EntityListItem {
  code: string;
  name: string;
  description?: string;
  entityType?: string;
}

export async function fetchEntityList(): Promise<EntityListItem[]> {
  return apiFetchData<EntityListItem[]>(`${ONT_BASE}/ont001/entities`);
}

export async function fetchEntityProperties(entityId: string): Promise<OntologyProperty[]> {
  return apiFetchData(`${ONT_BASE}/entities/${entityId}/properties`);
}

export async function fetchOntologyRelationships(): Promise<OntologyRelationship[]> {
  return apiFetchData(`${ONT_BASE}/relationships`);
}

export async function fetchEntityRelationships(entityId: string): Promise<OntologyRelationship[]> {
  return apiFetchData(`${ONT_BASE}/entities/${entityId}/relationships`);
}

export async function createOntologyEntity(ontologyId: string, body: Partial<OntologyEntity>): Promise<OntologyEntity> {
  return apiFetchData(`${ONT_BASE}/${ontologyId}/entities`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateOntologyEntity(ontologyId: string, entityId: string, body: Partial<OntologyEntity>): Promise<OntologyEntity> {
  return apiFetchData(`${ONT_BASE}/${ontologyId}/entities/${entityId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteOntologyEntity(ontologyId: string, entityId: string): Promise<void> {
  await apiFetchData(`${ONT_BASE}/${ontologyId}/entities/${entityId}`, { method: "DELETE" });
}

export async function createEntityProperty(entityId: string, body: Partial<OntologyProperty>): Promise<OntologyProperty> {
  return apiFetchData(`${ONT_BASE}/entities/${entityId}/properties`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateEntityProperty(entityId: string, propId: string, body: Partial<OntologyProperty>): Promise<OntologyProperty> {
  return apiFetchData(`${ONT_BASE}/entities/${entityId}/properties/${propId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteEntityProperty(entityId: string, propId: string): Promise<void> {
  await apiFetchData(`${ONT_BASE}/entities/${entityId}/properties/${propId}`, { method: "DELETE" });
}

export async function createEntityRelationship(entityId: string, body: Partial<OntologyRelationship>): Promise<OntologyRelationship> {
  return apiFetchData(`${ONT_BASE}/entities/${entityId}/relationships`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deleteEntityRelationship(entityId: string, relId: string): Promise<void> {
  await apiFetchData(`${ONT_BASE}/entities/${entityId}/relationships/${relId}`, { method: "DELETE" });
}

// ── Data Quality Dashboard ────────────────────────────────────
const DQ_BASE = "/api/dq";

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

// ── World Model Viewer ────────────────────────────────────────
const WM_BASE = "/api/v1/ecos/world-model-graph";

export async function fetchWorldGoals(): Promise<Goal[]> {
  const resp = await doFetch(`${WM_BASE}/goals`);
  const arr = resp?.data?.data;  // API returns {data:{data:[...],total}}
  if (Array.isArray(arr)) return arr as Goal[];
  return [];
}

export async function fetchWorldScenarios(): Promise<Scenario[]> {
  try {
    const resp = await doFetch(`${WM_BASE}/scenarios`);
    const arr = resp?.data?.data;
    if (Array.isArray(arr)) return arr as Scenario[];
  } catch { /* not yet available */ }
  return [];
}

export async function fetchWorldCausalLinks(): Promise<CausalLink[]> {
  const resp = await doFetch(`${WM_BASE}/links`);
  const arr = resp?.data?.data;
  if (Array.isArray(arr)) return arr as CausalLink[];
  return [];
}

export async function fetchWorldCausalGraph(): Promise<any> {
  try {
    return await doFetch(`${WM_BASE}/causal-graph`);
  } catch { /* not yet available */ }
  return { nodes: [], edges: [] };
}

export async function fetchWorldGoalTree(): Promise<any> {
  const resp = await doFetch(`${WM_BASE}/goals/tree`);
  return resp?.data ?? [];
}

export async function fetchWorldModelAll(): Promise<[any, any, any, any, any]> {
  return Promise.all([
    fetchWorldGoals(), fetchWorldScenarios(),
    fetchWorldCausalLinks(), fetchWorldCausalGraph(),
    fetchWorldGoalTree(),
  ]);
}

export async function createWorldModelItem(type: string, body: any): Promise<any> {
  return doFetch(`${WM_BASE}/${type}`, { method: "POST", body: JSON.stringify(body) });
}

export async function updateWorldModelItem(type: string, id: string, body: any): Promise<any> {
  return doFetch(`${WM_BASE}/${type}/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export async function deleteWorldModelItem(type: string, id: string): Promise<any> {
  return doFetch(`${WM_BASE}/${type}/${id}`, { method: "DELETE" });
}

export async function compareWorldScenarios(body: any): Promise<any> {
  return doFetch(`${WM_BASE}/compare`, { method: "POST", body: JSON.stringify(body) });
}

// ── Pareto Optimization ───────────────────────────────────────
const PARETO_BASE = "/api/pareto";

export interface ParetoSolution {
  variables: Record<string, number>;
  objectives: Record<string, number>;
}

export interface ParetoOptimizeResult {
  problemId: string;
  frontSize: number;
  solutions: ParetoSolution[];
  elapsed_ms: number;
}

export interface ParetoProblem {
  problemId: string;
  problemName: string;
  frontSize: number;
  timestamp: string;
}

/** POST /api/pareto/optimize — run multi-objective optimization */
export async function paretoOptimize(body: {
  numObjectives: number;
  numVariables: number;
  populationSize: number;
  generations: number;
}): Promise<ParetoOptimizeResult> {
  const resp = await doFetch(`${PARETO_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as ParetoOptimizeResult;
}

/** GET /api/pareto/problems — list previous optimization runs */
export async function fetchParetoProblems(): Promise<ParetoProblem[]> {
  const resp = await doFetch(`${PARETO_BASE}/problems`);
  const arr = resp?.data?.data ?? resp?.data ?? resp;
  if (Array.isArray(arr)) return arr as ParetoProblem[];
  return [];
}

/** GET /api/pareto/result/{problemId} — fetch a specific result */
export async function fetchParetoResult(problemId: string): Promise<ParetoOptimizeResult> {
  const resp = await doFetch(`${PARETO_BASE}/result/${encodeURIComponent(problemId)}`);
  return (resp?.data ?? resp) as ParetoOptimizeResult;
}

/** POST /api/pareto/from-scenario — generate demo from scenario */
export async function paretoFromScenario(body: { scenarioId?: string }): Promise<ParetoOptimizeResult> {
  const resp = await doFetch(`${PARETO_BASE}/from-scenario`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as ParetoOptimizeResult;
}

// ── P2-4: Causal Graph (Neo4j-backed) ─────────────────────────
const CAUSAL_BASE = "/api/v1/ecos/world-model-graph";

export async function fetchCausalGraph(): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/causal-graph`);
}

export async function fetchCausalPaths(from: string, to: string): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/paths?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}

export async function compareCausalScenarios(body: any): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/compare`, { method: "POST", body: JSON.stringify(body) });
}

// ── Datanet: 物理表注册 & 元数据采集 ───────────────────────────
const DATANET_DS = "/api/v1/datanet/datasource";
const DATANET_META = "/api/v1/datanet/metadata";
const DATANET_CATALOG = "/api/v1/datanet/catalog";

/** GET /datanet/datasource — 获取所有数据源列表 */
export async function fetchDataSources(): Promise<DataSource[]> {
  const resp = await doFetch(DATANET_DS);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as DataSource[];
  return [];
}

/** POST /datanet/datasource — 创建数据源 */
export async function createDataSource(body: Record<string, any>): Promise<DataSource> {
  const resp = await doFetch(DATANET_DS, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as DataSource;
}

/** DELETE /datanet/datasource/{id} — 删除数据源 */
export async function deleteDataSource(id: string): Promise<void> {
  await doFetch(`${DATANET_DS}/${id}`, { method: "DELETE" });
}

/** POST /datanet/datasource/test — 前置连接测试（不保存） */
export async function testRawConnection(body: { datasourceType: string; connectionConfig: string }): Promise<{ success: boolean; message: string }> {
  const resp = await doFetch(`${DATANET_DS}/test`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as { success: boolean; message: string };
}

/** POST /datanet/datasource/{id}/test — 测试数据源连接 */
export async function testDataSourceConnection(id: string): Promise<{ success: boolean }> {
  const resp = await doFetch(`${DATANET_DS}/${id}/test`, { method: "POST" });
  return (resp?.data ?? resp) as { success: boolean };
}

/** POST /datanet/metadata/collect/{datasourceId} — 触发元数据采集 */
export async function collectMetadata(datasourceId: string): Promise<{ resourcesCollected: number; elapsedMs: number }> {
  const resp = await doFetch(`${DATANET_META}/collect/${datasourceId}`, { method: "POST" });
  return (resp?.data ?? resp) as { resourcesCollected: number; elapsedMs: number };
}

/** GET /datanet/metadata/resources/{datasourceId} — 获取数据源的资源（表/视图）列表 */
export async function fetchResources(datasourceId: string): Promise<DataResource[]> {
  const resp = await doFetch(`${DATANET_META}/resources/${datasourceId}`);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as DataResource[];
  return [];
}

/** GET /datanet/metadata/resources — 批量获取所有资源（含数据源信息，一次请求替代N+1） */
export interface BulkResource {
  resourceId: string;
  resourceName: string;
  resourceType: string;
  sourcePath?: string;
  fieldCount?: number;
  datasourceId: string;
  datasourceName: string;
  datasourceType: string;
}
export async function fetchAllResources(): Promise<BulkResource[]> {
  const resp = await doFetch(`${DATANET_META}/resources/all`);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as BulkResource[];
  return [];
}

/** GET /datanet/metadata/fields/{resourceId} — 获取资源的字段列表 */
export async function fetchFields(resourceId: string): Promise<DataField[]> {
  const resp = await doFetch(`${DATANET_META}/fields/${resourceId}`);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as DataField[];
  return [];
}

/** GET /datanet/metadata/preview/{resourceId} — 预览数据行 */
export async function fetchPreview(resourceId: string, limit = 50): Promise<{rows: Record<string,any>[], columns: number, rowCount: number}> {
  const resp = await doFetch(`${DATANET_META}/preview/${resourceId}?limit=${limit}`);
  return resp?.data ?? { rows: [], columns: 0, rowCount: 0 };
}

// ── Monitoring Dashboard ───────────────────────────────
// 对接 MonitorController (/api/monitor)
const MONITOR_BASE = "/api/monitor";

export interface MonitoringKpi {
  label: string;
  value: string;
  desc: string;
  icon: string;
  color: string;
}

export interface MonitoringChartPoint {
  time: string;
  [key: string]: number | string;
}

export interface MonitoringProcess {
  name: string;
  type: string;
  status: string;
  uptime: string;
  items: string;
}

export interface MonitoringAlert {
  time: string;
  module: string;
  level: string;
  message: string;
}

export interface MonitoringDashboard {
  systemMetrics: MonitoringKpi[];
  chartData: MonitoringChartPoint[];
  processes: MonitoringProcess[];
  alerts: MonitoringAlert[];
}

export async function fetchMonitoringDashboard(): Promise<MonitoringDashboard> {
  try {
    const raw = await apiFetchData<any>(MONITOR_BASE);
    const sys = raw?.system || {};
    const recentAlerts: any[] = raw?.recent_alerts || [];

    // Map backend response → MonitoringDashboard
    const systemMetrics: MonitoringKpi[] = [
      { label: "CPU Cores", value: String(sys.cpu_cores || "N/A"), desc: "Available", icon: "Cpu", color: "text-blue-400" },
      { label: "CPU Load", value: String(sys.cpu_load || "N/A"), desc: "System load", icon: "Cpu", color: "text-cyan-400" },
      { label: "Heap Used", value: (sys.heap_used_mb || 0) + " MB", desc: "JVM heap", icon: "Server", color: "text-emerald-400" },
      { label: "Heap Max", value: (sys.heap_max_mb || 0) + " MB", desc: "Max heap", icon: "Server", color: "text-violet-400" },
      { label: "Active Alerts", value: String(raw.active_alerts ?? 0), desc: "Open alerts", icon: "AlertTriangle", color: "text-red-400" },
      { label: "DQ Issues", value: String(raw.open_dq_issues ?? 0), desc: "Open DQ issues", icon: "Database", color: "text-amber-400" },
    ];

    const alerts: MonitoringAlert[] = recentAlerts.map((a: any) => ({
      time: a.created_at || "",
      module: a.rule_name || "System",
      level: a.level || "INFO",
      message: a.message || "",
    }));

    // Map backend processes (or fallback mock)
    const backendProcesses: any[] = raw?.processes || [];
    const processes: MonitoringProcess[] = backendProcesses.length > 0
      ? backendProcesses.map((p: any) => ({
          name: p.name || "Unknown",
          type: p.type || "process",
          status: p.status || "UNKNOWN",
          uptime: p.uptime || "N/A",
          items: String(p.items ?? ""),
        }))
      : [
          { name: "ECOS Gateway", type: "Java/Spring", status: "RUNNING", uptime: "N/A", items: "Port 8080" },
          { name: "PostgreSQL", type: "Database", status: "RUNNING", uptime: "N/A", items: "Port 5432" },
        ];

    // Map backend chartData (or fallback empty)
    const chartData: MonitoringChartPoint[] = (raw?.chartData || []).map((c: any) => ({
      time: c.time || "",
      cpu: c.cpu,
      memory: c.memory,
    }));

    return {
      systemMetrics,
      chartData,
      processes,
      alerts,
    };
  } catch {
    return { systemMetrics: [], chartData: [], processes: [], alerts: [] };
  }
}

export async function runSystemDiagnostics(): Promise<{ database: { status: string }; uptime_ms: number; version: string; status: string }> {
  const health = await apiFetchData<any>(`${MONITOR_BASE}/health`);
  return {
    database: { status: health?.database?.status || "UNKNOWN" },
    uptime_ms: health?.uptime_ms || 0,
    version: health?.version || "N/A",
    status: health?.status || "DOWN",
  };
}

// ── Knowledge Search (Cognitive Operating System) ─────────────
const KNOWLEDGE_SEARCH_BASE = "/api/knowledge";

export async function searchKnowledge(q: string): Promise<any> {
  return doFetch(`${KNOWLEDGE_SEARCH_BASE}/search?q=${encodeURIComponent(q)}`);
}

/** GET /api/knowledge/path?s=srcId&t=tgtId — find shortest path between two nodes */
export async function fetchKnowledgePath(s: string, t: string): Promise<{
  path: string[];
  edges: { id: string; source: string; target: string; relationship: string }[];
  length: number;
}> {
  return apiFetch<{ success: boolean; data: any }>(
    `/knowledge/path?s=${encodeURIComponent(s)}&t=${encodeURIComponent(t)}`
  ).then(r => r.data);
}

/** GET /api/knowledge/neighbors/{id}?d=1 — get neighbors of a node */
export async function fetchKnowledgeNeighbors(id: string, d: number = 1): Promise<{
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}> {
  return apiFetch<{ success: boolean; data: any }>(
    `/knowledge/neighbors/${encodeURIComponent(id)}?d=${d}`
  ).then(r => {
    const data = r.data || { nodes: [], edges: [] };
    return {
      nodes: (data.nodes || []).map((n: any) => ({
        id: n.id,
        label: n.label,
        type: n.nodeType || n.type,
        nodeType: n.nodeType,
        description: n.description,
        propertiesJson: n.propertiesJson,
        properties: (() => { try { return typeof n.propertiesJson === 'string' ? JSON.parse(n.propertiesJson) : n.propertiesJson; } catch { return {}; } })(),
        createdAt: n.createdAt,
      })),
      edges: (data.edges || []).map((e: any) => ({
        id: e.id,
        source: e.sourceNodeId || e.source,
        target: e.targetNodeId || e.target,
        sourceNodeId: e.sourceNodeId,
        targetNodeId: e.targetNodeId,
        relationship: e.relationship,
        weight: e.weight,
      })),
    };
  });
}

// ── Global Unified Search ──────────────────────────────────────

/** 后端 /api/portal/search 返回的条目 */
export interface SearchHit {
  type: string;   // OntologyEntity|Asset|Goal|Scenario|Object|Workflow|Pipeline|Knowledge|Agent
  id: string;
  name: string;
  url: string;    // SPA hash route, e.g. "/app/ontology-designer"
}

/**
 * 统一全局搜索 — 跨 Ontology / Asset / Goal / Scenario / Object
 * / Workflow / Pipeline / Knowledge / Agent 多表 ILIKE 检索。
 *
 * @param q    搜索关键词
 * @param type 搜索范围: all | ontology | asset | goal | scenario | object | workflow | pipeline | knowledge | agent
 */
export async function globalSearch(
  q: string,
  type: string = 'all'
): Promise<SearchHit[]> {
  return apiFetchData<SearchHit[]>(`/api/portal/search?q=${encodeURIComponent(q)}&type=${encodeURIComponent(type)}`);
}

// ── Ontology Actions Execution (Operational Apps) ────────────
export async function executeOntologyAction(body: {
  actionId: string;
  entityType: string;
  instanceId: string;
  operatorName: string;
  fields: Record<string, any>;
}): Promise<any> {
  return apiFetchData('/api/v1/gsxk/actions/execute', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

// ── Goals / Causal / Scenarios (async with mock fallback) ─
export async function getGoals(): Promise<Goal[]> { return fetchWorldGoals(); }
export async function getCausalLinks(): Promise<CausalLink[]> { return fetchWorldCausalLinks(); }
export async function getScenarios(): Promise<Scenario[]> { return fetchWorldScenarios(); }

// ── IAM: User & Role & Org & Permission Management ──────────

export interface IamUser {
  userId: string; username: string; realName: string;
  email?: string; phone?: string; orgId?: string;
  status: string; locked: string;
  lastLoginTime?: string; createdTime?: string;
}
export interface IamRole {
  roleId: string; roleName: string; roleCode: string;
  description?: string; roleType: string; status: string;
  tenantId?: string; parentRoleId?: string;
  createdTime?: string; updatedTime?: string;
}
export interface IamOrg {
  orgId: string; orgName: string; orgCode: string;
  parentOrgId?: string; orgType: string; description?: string;
  status: string; remark?: string; path?: string;
  createdTime?: string;
  children?: IamOrg[];
}
export interface IamPermission {
  permissionId: string; resource: string; action: string;
  description?: string; conditionExpr?: string;
}

// Users
export async function fetchUsers(keyword?: string, page=1, pageSize=20): Promise<{data:IamUser[], total:number}> {
  try {
    const q = keyword ? `&keyword=${encodeURIComponent(keyword)}` : '';
    return await apiFetchData(`/api/v1/system/users?page=${page}&pageSize=${pageSize}${q}`);
  } catch (e) { console.warn("fetchUsers failed", e); return { data: [], total: 0 }; }
}
export async function createUser(body: Record<string,any>) {
  return apiFetchData('/api/v1/system/users', { method:'POST', body:JSON.stringify(body) });
}
export async function updateUser(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/users/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deleteUser(id:string) {
  return apiFetchData(`/api/v1/system/users/${id}`, { method:'DELETE' });
}
export async function resetPassword(id:string, password:string) {
  return apiFetchData(`/api/v1/system/users/${id}/password`, { method:'PUT', body:JSON.stringify({password}) });
}
export async function toggleUserStatus(id:string, status:string) {
  return apiFetchData(`/api/v1/system/users/${id}/status`, { method:'PUT', body:JSON.stringify({status}) });
}
/** Fetch organizations for a specific user */
export async function fetchUserOrganizations(userId:string): Promise<IamOrg[]> {
  try { return await apiFetchData<IamOrg[]>(`/api/v1/system/users/${userId}/organizations`); }
  catch (e) { console.warn("fetchUserOrganizations failed", e); return []; }
}

// Roles
export async function fetchRoles(): Promise<{data:IamRole[], total:number}> {
  try { return await apiFetchData('/api/v1/system/roles'); }
  catch (e) { console.warn("fetchRoles failed", e); return { data: [], total: 0 }; }
}
export async function createRole(body:Record<string,any>) {
  return apiFetchData('/api/v1/system/roles', { method:'POST', body:JSON.stringify(body) });
}
export async function updateRole(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/roles/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deleteRole(id:string) {
  return apiFetchData(`/api/v1/system/roles/${id}`, { method:'DELETE' });
}

// Organizations
export async function fetchOrgs(): Promise<IamOrg[]> {
  try { return await apiFetchData('/api/v1/system/organizations/all'); }
  catch (e) { console.warn("fetchOrgs failed", e); return []; }
}
/** Fetch organization tree — returns nested children structure.
 *  Backend returns single root node wrapped in ApiResponse.data (Map, not Array).
 *  Normalize to IamOrg[] so callers can safely use .map(). */
export async function fetchOrgTree(): Promise<IamOrg[]> {
  try {
    const data = await apiFetchData<any>('/api/v1/system/organizations/tree');
    if (Array.isArray(data)) return data;
    return data ? [data] : [];
  } catch (e) { console.warn("fetchOrgTree failed", e); return []; }
}
export async function createOrg(body:Record<string,any>) {
  return apiFetchData('/api/v1/system/organizations', { method:'POST', body:JSON.stringify(body) });
}

// Permissions
export async function fetchPermissions(): Promise<IamPermission[]> {
  try { return await apiFetchData<IamPermission[]>('/api/system/permissions'); }
  catch (e) { console.warn("fetchPermissions: backend unavailable", e); return []; }
}
export async function createPermission(body: Record<string,any>) {
  return apiFetchData('/api/system/permissions', { method:'POST', body:JSON.stringify(body) });
}
export async function updatePermission(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/system/permissions/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deletePermission(id:string) {
  return apiFetchData(`/api/system/permissions/${id}`, { method:'DELETE' });
}

// User-Role assignments
export async function fetchUserRoles(userId:string): Promise<string[]> {
  try { return await apiFetchData<string[]>(`/api/v1/system/users/${userId}/roles`); }
  catch (e) { console.warn("fetchUserRoles failed", e); return []; }
}
export async function assignUserRoles(userId:string, roleIds:string[]): Promise<void> {
  return apiFetchData(`/api/v1/system/users/${userId}/roles`, { method:'PUT', body:JSON.stringify({roleIds}) });
}

// Role-Permission assignments
export async function fetchRolePermissions(roleId:string): Promise<string[]> {
  try { return await apiFetchData<string[]>(`/api/v1/system/roles/${roleId}/permissions`); }
  catch (e) { console.warn("fetchRolePermissions failed", e); return []; }
}
export async function assignRolePermissions(roleId:string, permissionIds:string[]): Promise<void> {
  return apiFetchData(`/api/v1/system/roles/${roleId}/permissions`, { method:'PUT', body:JSON.stringify({permissionIds}) });
}

// Organization update/delete
export async function updateOrg(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/organizations/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deleteOrg(id:string) {
  return apiFetchData(`/api/v1/system/organizations/${id}`, { method:'DELETE' });
}

// ── Tenant Management (对接 TenantController) ─────────────────
// 后端端点: /api/v1/system/tenants

export interface TenantInfo {
  tenantId: string;
  tenantName: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  status: string;
  quotaTypes?: number;
  todayUsage?: number;
  createdTime?: string;
  updatedTime?: string;
}

export interface TenantQuota {
  quotaType: string;
  dailyLimit: number;
  monthlyLimit: number;
  usedToday?: number;
}

export interface TenantQuotaUpdateRequest {
  quota_type: string;
  daily_limit: number;
  monthly_limit: number;
}

/** GET /api/v1/system/tenants — 分页查询租户列表 */
export async function fetchTenants(
  keyword?: string,
  page = 1,
  pageSize = 20
): Promise<{ data: TenantInfo[]; total: number }> {
  try {
    const params = new URLSearchParams();
    if (keyword) params.set("keyword", keyword);
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    return await apiFetchData<{ data: TenantInfo[]; total: number }>(
      `/api/v1/system/tenants?${params.toString()}`
    );
  } catch (e) {
    console.warn("fetchTenants: backend unavailable", e);
    return { data: [], total: 0 };
  }
}

/** POST /api/v1/system/tenants — 创建租户 */
export async function createTenant(body: {
  tenantName: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
}): Promise<TenantInfo> {
  return apiFetchData<TenantInfo>("/api/v1/system/tenants", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** PUT /api/v1/system/tenants/{tenantId} — 更新租户 */
export async function updateTenant(
  tenantId: string,
  body: Partial<TenantInfo>
): Promise<TenantInfo> {
  return apiFetchData<TenantInfo>(`/api/v1/system/tenants/${tenantId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** DELETE /api/v1/system/tenants/{tenantId} — 删除租户 */
export async function deleteTenant(tenantId: string): Promise<void> {
  await apiFetchData(`/api/v1/system/tenants/${tenantId}`, { method: "DELETE" });
}

/** GET /api/v1/system/tenants/{tenantId}/quota — 获取租户配额 */
export async function fetchTenantQuota(
  tenantId: string
): Promise<{ data: TenantQuota[]; total: number }> {
  try {
    return await apiFetchData<{ data: TenantQuota[]; total: number }>(
      `/api/v1/system/tenants/${tenantId}/quota`
    );
  } catch (e) {
    console.warn("fetchTenantQuota: backend unavailable", e);
    return { data: [], total: 0 };
  }
}

/** PUT /api/v1/system/tenants/{tenantId}/quota — 更新租户配额 */
export async function updateTenantQuota(
  tenantId: string,
  body: TenantQuotaUpdateRequest
): Promise<TenantQuota> {
  return apiFetchData<TenantQuota>(`/api/v1/system/tenants/${tenantId}/quota`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
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

// ── Biz Dashboard ──────────────────────────────────────────
export async function fetchBizDashboard(): Promise<any> {
  return apiFetchData('/api/v1/ecos/biz/dashboard');
}

// ── Goal Tracking ────────────────────────────────────────
export async function fetchGoalTracking(goalId?: number): Promise<any> {
  const qs = goalId ? `?goalId=${goalId}` : '';
  return apiFetchData(`/api/dq/goal-tracking${qs}`);
}

// ── ECOS Knowledge Graph ─────────────────────────────────
export async function fetchEcosKnowledgeGraph(): Promise<any> {
  return apiFetchData('/api/v1/ecos/knowledge-graph');
}

// ── Diagnostic Agent ──────────────────────────────────────
export async function callDiagnosticAgent(query: string): Promise<any> {
  return apiFetchData('/api/v1/agent/call', {
    method: "POST",
    body: JSON.stringify({ agent: "diagnostic", query }),
  });
}

// ── Entity Instances (Operational Apps) ──────────────────
export async function fetchEntityInstances(entityType: string): Promise<any[]> {
  try {
    return await apiFetchData<any[]>(`/api/ontology/entities/${entityType}/instances`);
  } catch {
    return [];
  }
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
  } catch (e: any) {
    throw new Error(e.message || "Failed to fetch policy content");
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

// ── Digital Twin ─────────────────────────────────────────────
// 对接 DeviceTwinController (/api/twins)

export interface TwinHealth {
  mqtt: { status: string };
  device_count: number;
}

export interface TwinDevice {
  deviceId: string;
  name: string;
  type: string;
  unit: string;
  status: string;
}

export interface TwinTelemetry {
  deviceId: string;
  value: number;
  ts: string;
}

export interface TwinShadowState {
  desired: Record<string, any>;
  reported: Record<string, any>;
}

export interface TwinDeviceStatus {
  deviceId: string;
  status: string;
  shadow: TwinShadowState;
  telemetryCount: number;
}

export interface TwinCommandResult {
  status: string;
  deviceId: string;
  command: string;
  params: Record<string, any>;
}

export async function fetchTwinHealth(): Promise<TwinHealth> {
  try {
    return await apiFetchData<TwinHealth>('/api/twins/health');
  } catch {
    console.warn('fetchTwinHealth: backend unavailable');
    return { mqtt: { status: 'DOWN' }, device_count: 0 };
  }
}

export async function fetchTwinDevices(): Promise<TwinDevice[]> {
  try {
    return await apiFetchData<TwinDevice[]>('/api/twins/devices');
  } catch {
    console.warn('fetchTwinDevices: backend unavailable');
    return [];
  }
}

export async function fetchTwinTelemetry(deviceId: string, limit = 20): Promise<TwinTelemetry[]> {
  try {
    return await apiFetchData<TwinTelemetry[]>(`/api/twins/${encodeURIComponent(deviceId)}/telemetry?limit=${limit}`);
  } catch {
    console.warn(`fetchTwinTelemetry(${deviceId}): backend unavailable`);
    return [];
  }
}

export async function sendTwinCommand(deviceId: string, command: string, params: Record<string, any> = {}): Promise<TwinCommandResult> {
  return apiFetchData<TwinCommandResult>(`/api/twins/${encodeURIComponent(deviceId)}/command`, {
    method: 'POST',
    body: JSON.stringify({ command, params }),
  });
}

export async function fetchTwinDeviceStatus(deviceId: string): Promise<TwinDeviceStatus> {
  try {
    return await apiFetchData<TwinDeviceStatus>(`/api/twins/${encodeURIComponent(deviceId)}/status`);
  } catch {
    console.warn(`fetchTwinDeviceStatus(${deviceId}): backend unavailable`);
    return { deviceId, status: 'offline', shadow: { desired: {}, reported: {} }, telemetryCount: 0 };
  }
}

// ── System Config Manager ────────────────────────────────────

export interface SysConfigItem {
  key: string;
  value: string;
  label: string;
  labelZh: string;
  description: string;
  descriptionZh: string;
  group: string;
  type: 'string' | 'number' | 'boolean' | 'json';
  options?: string[];
  impactScope?: string;
  edition?: string;
  default_value?: string;
  isConsumed?: boolean;
  consumedBy?: string;
}

export interface SysConfigGrouped {
  group: string;
  groupLabel: string;
  groupLabelZh: string;
  items: SysConfigItem[];
}

/** GET /api/v1/system/config — fetch all system configs, optionally filtered by group */
export async function fetchSysConfigs(group?: string): Promise<SysConfigGrouped[]> {
  try {
    const qs = group ? `?group=${encodeURIComponent(group)}` : '';
    const resp = await apiFetch<{ success: boolean; data: { data: any[]; total: number } }>(`/v1/system/config${qs}`);
    const items: any[] = resp?.data?.data || [];
    // Map backend flat items → SysConfigItem
    const mapped: SysConfigItem[] = items.map((it: any) => ({
      key: it.config_key || it.key,
      value: String(it.config_value ?? it.value ?? ''),
      label: it.config_label || it.label || it.config_key,
      labelZh: it.config_label || it.labelZh || it.config_key,
      description: it.description || '',
      descriptionZh: it.description || '',
      group: it.config_group || it.group || 'global',
      type: (it.config_type || it.type || 'string') as SysConfigItem['type'],
      options: it.config_options || it.options || undefined,
      impactScope: it.impact_scope || it.impactScope || '',
      edition: it.edition || 'all',
      default_value: it.default_value || '',
      isConsumed: it.is_consumed || it.isConsumed || false,
      consumedBy: it.consumed_by || it.consumedBy || '',
    }));
    // Group by config_group
    const groupMap: Record<string, SysConfigItem[]> = {};
    for (const item of mapped) {
      const g = item.group;
      if (!groupMap[g]) groupMap[g] = [];
      groupMap[g].push(item);
    }
    return Object.entries(groupMap).map(([g, items]) => ({
      group: g,
      groupLabel: g,
      groupLabelZh: g,
      items,
    }));
  } catch (e) {
    console.warn('fetchSysConfigs: backend unavailable', e);
    return [];
  }
}

/** PUT /api/v1/system/config/{key} — update a single config value */
export async function updateSysConfig(key: string, value: string): Promise<SysConfigItem> {
  return apiFetch<SysConfigItem>(`/v1/system/config/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify({ value }),
  });
}

/** GET /api/v1/system/config/audit — config consumption audit */
export interface SysConfigAuditItem {
  key: string;
  label: string;
  value: string;
  consumed: boolean;
  consumedBy: string;
  consumedAt: string;
}

export async function fetchConfigAudit(): Promise<SysConfigAuditItem[]> {
  try {
    const resp = await apiFetch<{ success: boolean; data: SysConfigAuditItem[] }>('/v1/system/config/audit');
    return resp?.data || [];
  } catch (e) {
    console.warn('fetchConfigAudit: backend unavailable', e);
    return [];
  }
}

// ── Cognitive Engine (S5-1.5) ──────────────────────────────────
const COGNITIVE_BASE = "/api/v1/cognitive";

/** POST /api/v1/cognitive/reason — 规则推理 / 因果分析 */
export async function apiCognitiveReason(body: {
  mode: string;
  facts: Record<string, any>;
  context?: Record<string, any>;
  options?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/reason`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** POST /api/v1/cognitive/optimize — 帕累托优化 */
export async function apiCognitiveOptimize(body: {
  problem: Record<string, any>;
  params?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/blueprint — 六层蓝图健康度 */
export async function apiCognitiveBlueprint(layer?: string): Promise<any> {
  const qs = layer ? `?layer=${encodeURIComponent(layer)}` : '';
  return apiFetchData(`${COGNITIVE_BASE}/blueprint${qs}`);
}

/** POST /api/v1/cognitive/plan — 创建执行计划 */
export async function apiCognitiveCreatePlan(body: {
  source: Record<string, any>;
  priority?: string;
  targets?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/plan`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/plan/{id} — 查询执行计划 */
export async function apiCognitiveGetPlan(id: string): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/plan/${encodeURIComponent(id)}`);
}

/** GET /api/v1/cognitive/health — 认知引擎健康检查 */
export async function apiCognitiveHealth(): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/health`);
}

// ── Task Center (S5-1.5) ──────────────────────────────────────
const TASK_BASE = "/api/v1/task";

/** GET /api/v1/task/list — 任务列表 */
export async function apiTaskList(params?: {
  status?: string;
  type?: string;
  page?: number;
  size?: number;
}): Promise<any> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.type) qs.set("type", params.type);
  if (params?.page != null) qs.set("page", String(params.page));
  if (params?.size != null) qs.set("size", String(params.size));
  const q = qs.toString();
  return apiFetchData(`${TASK_BASE}/list${q ? "?" + q : ""}`);
}

/** POST /api/v1/task/submit — 提交任务 */
export async function apiTaskSubmit(body: {
  taskName: string;
  taskType: string;
  config: Record<string, any>;
  runner: string;
  priority?: string;
  maxRetries?: number;
}): Promise<any> {
  return apiFetchData(`${TASK_BASE}/submit`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/task/{id}/status — 查询任务状态 */
export async function apiTaskStatus(id: string): Promise<any> {
  return apiFetchData(`${TASK_BASE}/${encodeURIComponent(id)}/status`);
}

/** POST /api/v1/task/{id}/cancel — 取消任务 */
export async function apiTaskCancel(id: string): Promise<any> {
  return apiFetchData(`${TASK_BASE}/${encodeURIComponent(id)}/cancel`, {
    method: "POST",
  });
}

/** GET /api/v1/task/stats — 任务实时统计 */
export interface TaskStats {
  total: number;
  running: number;
  pending: number;
  succeeded: number;
  failed: number;
  cancelled: number;
}

export async function apiTaskStats(): Promise<TaskStats> {
  return apiFetchData(`${TASK_BASE}/stats`);
}

// ── System Dict (字典项管理) ────────────────────────────

export interface DictType {
  dictType: string;
  dictName: string;
  description?: string;
  status?: string;
  subsystem?: string;
  itemCount?: number;
}

export interface DictItem {
  id: number;
  dictType: string;
  dictCode: string;
  dictLabel: string;
  dictLabelEn?: string;
  sortOrder: number;
  status: string;
  parentCode?: string;
  extValue?: string;
  createdAt: string;
  updatedAt: string;
}

const DICT_BASE = '/api/v1/system/dict';

/** GET /api/v1/system/dict/types — 获取所有字典类型 */
export async function listDictTypes(): Promise<DictType[]> {
  return apiFetchData(`${DICT_BASE}/types`);
}

/** GET /api/v1/system/dict/{type} — 获取某类型下的所有字典项 */
export async function getDictItems(dictType: string): Promise<DictItem[]> {
  return apiFetchData(`${DICT_BASE}/${encodeURIComponent(dictType)}`);
}

/** POST /api/v1/system/dict — 创建字典项 */
export async function createDictItem(body: {
  dictType: string;
  dictCode: string;
  extValue?: string;
  dictLabel: string;
  status?: string;
  sortOrder?: number;
}): Promise<DictItem> {
  return apiFetchData(`${DICT_BASE}`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** PUT /api/v1/system/dict/{type}/{code} — 更新字典项 */
export async function updateDictItem(
  dictType: string,
  dictCode: string,
  body: {
    extValue?: string;
    dictLabel?: string;
    status?: string;
    sortOrder?: number;
  }
): Promise<DictItem> {
  return apiFetchData(
    `${DICT_BASE}/${encodeURIComponent(dictType)}/${encodeURIComponent(dictCode)}`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    }
  );
}

/** DELETE /api/v1/system/dict/{type}/{code} — 删除字典项 */
export async function deleteDictItem(
  dictType: string,
  dictCode: string
): Promise<void> {
  await apiFetchData(
    `${DICT_BASE}/${encodeURIComponent(dictType)}/${encodeURIComponent(dictCode)}`,
    { method: 'DELETE' }
  );
}

/** GET /api/v1/system/dict/subsystems — 按G1-G5子系统分组 */
export async function fetchDictSubsystems(): Promise<Record<string, DictType[]>> {
  return apiFetchData(`${DICT_BASE}/subsystems`);
}

/** GET /api/v1/system/dict/{type}/usage — 字典审计：哪些模块使用了该字典 */
export async function fetchDictUsage(dictType: string): Promise<{
  dictType: string;
  itemCount: number;
  usedByModules: { subsystem: string; module: string }[];
  usedByCount: number;
}> {
  return apiFetchData(`${DICT_BASE}/${encodeURIComponent(dictType)}/usage`);
}
