import type { GuardrailPolicy, MaskType, PolicySeverity, PolicyStatus, PolicyType } from './types';

/** Build auth headers — 单源定义见 services/auth.ts (H6-T1)；传输单源见 services/httpClient.ts（#143/B5-11g） */
import { rawAuthedUrlFetch } from '../../services/httpClient';

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────

/**
 * Generic guarded fetch returning JSON; handles {success,data} envelopes.
 *
 * <p>#143/B5-11g 传输合流：域内裸 `fetch(` 换成单源 `rawAuthedUrlFetch`，头集合逐键不变
 * （改前 `authHeaders()` = Content-Type + 条件 Authorization，单源只拼 Authorization，
 * 故 Content-Type 由本调用面显式带上；POST/PUT 带 JSON body 却无 Content-Type 会 415，
 * 这一层不能少）。401 清 token 跳登录 / 403 文案 / !ok 取 body 文本 / content-type 容差
 * 返回 null / 信封解包五面逐字留在本域。</p>
 *
 * <p>⚠ 一处与改前的真实差别：改前 `fetch(url, { headers: authHeaders(), ...options })`
 * 是**整组替换**（调用方传 `headers` 会把 Authorization 整个丢掉 ⇒ 该请求变成无身份），
 * 合流后单源按**逐键合并**（调用方同名键覆盖、Authorization 保留）。实测 `pages/GuardrailsView.tsx`
 * 7 处 `apiCall` 调用点（POST/PUT/DELETE/GET 四面）零处传 `headers` ⇒ 本应用内两者取值相同；
 * 该整组替换形态属 D-B5-34 缺陷族，调用点约束由本域单测钉成扫描派生值（不写死计数），
 * 防后续新增调用点踩坑。</p>
 */
export async function apiCall<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await rawAuthedUrlFetch(url, { headers: { 'Content-Type': 'application/json' }, ...options });
  // PMO-43 T4: 401 and 403 semantics split.
  // 401 = unauthenticated/expired → clear token + redirect to login.
  // 403 = authenticated but permission denied → surface as normal error;
  // never clear the token or bounce the user back to /login.
  if (res.status === 401) {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('登录已过期，请重新登录');
  }
  if (res.status === 403) {
    const forbidden = await res.text().catch(() => '');
    throw new Error(`无权限访问该资源 (${forbidden || '403 Forbidden'})`);
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get('content-type');
  if (!ct || !ct.includes('application/json')) return null as unknown as T;
  const json = await res.json();
  // unwrap common envelopes
  if (json && typeof json === 'object' && 'data' in json && json.data !== undefined) return json.data as T;
  return json as T;
}

/** Normalize an arbitrary backend record into a GuardrailPolicy. */
export function normalizePolicy(raw: any): GuardrailPolicy {
  return {
    id: String(raw.id ?? raw.policy_id ?? raw.name ?? ''),
    name: raw.name ?? raw.policyName ?? raw.title ?? '未命名策略',
    description: raw.description ?? raw.desc ?? '',
    type: (raw.type ?? raw.policyType ?? 'custom') as PolicyType,
    severity: (raw.severity ?? (raw.action === 'block' ? 'block' : 'warn')) as PolicySeverity,
    isEnabled: raw.isEnabled ?? raw.enabled ?? raw.active ?? true,
    status: (raw.status ?? (raw.compiled ? 'COMPILED' : 'DRAFT')) as PolicyStatus,
    table: raw.table ?? raw.tableName,
    column: raw.column ?? raw.columnName,
    maskType: (raw.maskType ?? raw.mask_type ?? 'REDACT') as MaskType,
    condition: raw.condition ?? raw.sqlCondition ?? raw.predicate,
    config: raw.config ?? raw.params,
    compiledAt: raw.compiledAt ?? raw.compiled_at,
    compileLogs: raw.compileLogs ?? raw.compile_logs ?? [],
    createdAt: raw.createdAt ?? raw.created_at,
    updatedAt: raw.updatedAt ?? raw.updated_at,
  };
}

export function emptyPolicy(): GuardrailPolicy {
  return {
    id: '',
    name: '',
    description: '',
    type: 'column_masking',
    severity: 'block',
    isEnabled: true,
    status: 'DRAFT',
    table: '',
    column: '',
    maskType: 'REDACT',
    condition: '',
    config: {},
    compileLogs: [],
  };
}

/** Flatten preview rows whether returned as array or {table: []} map. */
export function getPreviewRows(data: any[] | Record<string, any[]>): { rows: any[]; columns: string[] } {
  if (Array.isArray(data)) {
    const rows = data;
    const columns = rows.length > 0 ? Object.keys(rows[0]) : [];
    return { rows, columns };
  }
  // object map — pick the first table bucket
  const keys = Object.keys(data);
  if (keys.length === 0) return { rows: [], columns: [] };
  const rows = data[keys[0]] || [];
  const columns = rows.length > 0 ? Object.keys(rows[0]) : [];
  return { rows, columns };
}
