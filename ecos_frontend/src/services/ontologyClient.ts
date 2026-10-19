/**
 * ontologyClient — 本体域治理读/写单源服务层（F03-16 / W67）
 *
 * 单源原则：本体治理页（口径与指标、Function 审计）的全部后端 URL 只在此声明，
 * 内部按资源分节（calibers / metrics / functionAudit）导出；页面不再各自散写 URL。
 *
 * 传输：统一走 services/httpClient 的 apiFetchData<T>()（token 认证 + ApiResponse 解包），
 * 语义与既有 services/ontologyApi.ts 保持一致（那是"对象/关系/属性"运维面，本文件是
 * "治理面"，二者按资源域分离，避免 1600 行大文件再膨胀）。
 *
 * 后端契约（详见 CaliberController / FunctionController）：
 *   GET  /api/v1/ontology/calibers                          → list(status, limit)
 *   GET  /api/v1/ontology/calibers/{code}                   → detail（含版本历史）
 *   GET  /api/v1/ontology/calibers/{code}/current           → 当前生效 APPROVED 版本（跨引擎只读面）
 *   POST /api/v1/ontology/calibers                          → 新建 DRAFT 口径
 *   POST /api/v1/ontology/calibers/metrics/{m}/binding      → 绑定口径 + 冻结快照
 *   GET  /api/v1/ontology/functions/audit                   → Function 审计分页
 */

import { API_BASE, apiFetchData } from "./httpClient";

/** 组装查询串：跳过 undefined/null/空串，值做 URL 编码（fail-closed 参数化）。 */
function qs(p: Record<string, string | number | undefined>): string {
  const parts: string[] = [];
  for (const [k, v] of Object.entries(p)) {
    if (v === undefined || v === null || v === "") continue;
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  }
  return parts.length ? `?${parts.join("&")}` : "";
}

// ── 类型定义 ────────────────────────────────────────────────

/** 口径主表行（对齐后端 CaliberVO）。 */
export interface Caliber {
  id: string;
  code: string;
  name: string;
  status?: string;
  unit?: string;
  currency?: string;
  ownerRole?: string;
  dimensionJson?: string;
  domain?: string;
  createTime?: string;
  /** 仅详情端点填充。 */
  versions?: CaliberVersion[];
}

/** 口径版本行（后端返回 snake_case 列名，前端做兼容读）。 */
export interface CaliberVersion {
  version_no?: string;
  versionNo?: string;
  formula?: string;
  additive?: boolean | number;
  period_granularity?: string;
  periodGranularity?: string;
  status?: string;
  published_at?: string;
  publishedAt?: string;
  git_ref?: string;
  gitRef?: string;
  [k: string]: unknown;
}

/** Function 审计行（对齐 V172 表列）。 */
export interface FunctionAuditRow {
  id: string;
  function_name?: string;
  functionName?: string;
  expression?: string;
  object_type?: string;
  objectType?: string;
  duration_ms?: number;
  durationMs?: number;
  is_cache_hit?: number;
  cacheHit?: boolean | number;
  caller_id?: string;
  trace_id?: string;
  status?: string;
  error_message?: string;
  create_time?: string;
  created_at?: string;
  [k: string]: unknown;
}

/** Function 审计分页视图（对齐 OntologyFunctionAuditVO）。 */
export interface FunctionAuditPage {
  items: FunctionAuditRow[];
  total?: number;
  page?: number;
  pageSize?: number;
}

/** 口径列表查询参数。 */
export interface CaliberListQuery {
  status?: string;
  limit?: number;
}

/** Function 审计查询参数（fail-closed 参数化，禁前端拼 SQL）。 */
export interface FunctionAuditQuery {
  status?: string;
  callerId?: string;
  page?: number;
  pageSize?: number;
}

/** 新建 DRAFT 口径 DTO。 */
export interface CaliberCreateDTO {
  code: string;
  name: string;
  unit?: string;
  currency?: string;
  ownerRole?: string;
  dimensionJson?: string;
  status?: string;
}

/** 绑定口径 DTO。 */
export interface MetricBindingDTO {
  caliberCode: string;
  formulaVersion: string;
}

// ── URL 单源常量 ────────────────────────────────────────────

const CALIBER_BASE = `${API_BASE}/v1/ontology/calibers`;
const FUNCTION_AUDIT_URL = `${API_BASE}/v1/ontology/functions/audit`;

// ══════════════════════════════════════════════════
// §1 口径 Calibers
// ══════════════════════════════════════════════════

/** 口径列表。 */
export async function fetchCalibers(q: CaliberListQuery = {}): Promise<Caliber[]> {
  const query = qs({ status: q.status, limit: q.limit ?? 50 });
  return apiFetchData<Caliber[]>(`${CALIBER_BASE}${query}`);
}

/** 口径详情（含版本历史）。 */
export async function fetchCaliberDetail(code: string): Promise<Caliber> {
  return apiFetchData<Caliber>(`${CALIBER_BASE}/${encodeURIComponent(code)}`);
}

/** 当前生效（最近 APPROVED）版本行 — 跨引擎只读消费面。 */
export async function fetchCurrentCaliberVersion(code: string): Promise<Record<string, unknown>> {
  return apiFetchData<Record<string, unknown>>(`${CALIBER_BASE}/${encodeURIComponent(code)}/current`);
}

/** 新建 DRAFT 口径。 */
export async function createCaliber(dto: CaliberCreateDTO): Promise<Caliber> {
  return apiFetchData<Caliber>(CALIBER_BASE, {
    method: "POST",
    body: JSON.stringify(dto),
  });
}

// ══════════════════════════════════════════════════
// §2 指标 Metric 绑定
// ══════════════════════════════════════════════════

/** 为指标绑定口径并冻结快照（规则 3 冻结 → 409 ECOS-ONTO-041）。 */
export async function bindMetricCaliber(
  metricCode: string,
  dto: MetricBindingDTO
): Promise<Record<string, unknown>> {
  return apiFetchData<Record<string, unknown>>(
    `${CALIBER_BASE}/metrics/${encodeURIComponent(metricCode)}/binding`,
    { method: "POST", body: JSON.stringify(dto) }
  );
}

// ══════════════════════════════════════════════════
// §3 Function 审计
// ══════════════════════════════════════════════════

/** Function 审计分页查询。 */
export async function fetchFunctionAudit(q: FunctionAuditQuery = {}): Promise<FunctionAuditPage> {
  const query = qs({
    status: q.status,
    callerId: q.callerId,
    page: q.page ?? 1,
    pageSize: q.pageSize ?? 20,
  });
  return apiFetchData<FunctionAuditPage>(`${FUNCTION_AUDIT_URL}${query}`);
}
