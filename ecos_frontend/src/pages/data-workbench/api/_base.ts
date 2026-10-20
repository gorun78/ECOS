/**
 * Data Workbench — backend API layer · 共享 HTTP 原语 + 端点常量 + 类型别名
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 *
 * <p>传输与解包单源（§13.2.4 C2 / #143/B5-8）：四动词一律委托
 * `services/httpClient#apiFetchData`，域内不再自设 `fetch`、不再自拆 `ApiResponse`
 * 信封、不再自拼 auth 头。改前三重违例＝自带 `fetch` + 手写「`data` 字段缺失即回落整个
 * 信封」的拆包 + `${url} → ${status}` 私有文案，后果是「故障信封被当业务数据渲染」（fail-open：
 * `success:false` / `code≠0` 一律静默返回），且网络层失败以裸 `TypeError` 上抛、
 * 页面无法与业务错误分态。</p>
 *
 * <p>兼容性红线：`get/post/put/del` 导出签名与 URL 常量逐字不变（API 只增不改），
 * HTTP 非 2xx 的 `Error.message` 仍为既有 `${url} → ${status}` 形态（消费者按原文渲染），
 * 由 `ApiRequestError.httpStatus` 回映射；原始单源错误挂在 `cause` 上以便取号。
 * 回映射产物必须是 `ApiRequestError`（不是裸 `Error`）——裸 `Error` 不带 `status`，
 * `httpClient#isErrorResponse` 会把 HTTP 腿折成 `kind:'unknown'`，域侧无法与网络腿分态。</p>
 * @license Apache-2.0
 */

import { API_BASE, ApiRequestError, NoAccessError, apiFetchData } from '../../../services/httpClient';

// #145/B6-5：`/v1/pipeline/definitions` 族属主在 `api/dataClient`，本行只做域内别名重导出——
// 不含 URL 体 ⇒ 不构成第二个属主，域内 `PIPELINE_DEFS` 导出面逐字不变（API 只增不改）。
export { PIPELINE_DEFINITIONS_BASE as PIPELINE_DEFS } from '../../../api/dataClient';

// ─── API 端点常量 ──────────────────────────────────────────
export const DATANET_DS = '/datanet/datasource';           // DataSourceController
export const INTEGRATION  = `${API_BASE}/integration/metadata`;   // IntegrationMetadataController (connections + syncTasks)
export const LINEAGE_TOPOL = `${API_BASE}/v1/engine/data/lineage/topology`;

/**
 * 单通道委托 + 既有文案回映射。
 *
 * <p>只对「HTTP 非 2xx」这一条腿回映射：该腿改前文案为 `${url} → ${status}`，
 * 消费者（页面 catch / toast）按原文渲染，必须逐字保持。伪失败腿（`success:false`、
 * `code≠0`）与网络层腿（`NetworkError`）不回映射——改前它们要么静默返回故障信封、
 * 要么裸抛 `TypeError`，本批按单源口径如实上抛，属登记的收口差异而非回归。</p>
 */
async function request<T>(url: string, init: RequestInit): Promise<T> {
  try {
    return await apiFetchData<T>(url, init);
  } catch (e) {
    if (e instanceof ApiRequestError && typeof e.httpStatus === 'number') {
      const remapped = new ApiRequestError(`${url} → ${e.httpStatus}`, {
        httpStatus: e.httpStatus,
        traceId: e.traceId,
      });
      (remapped as Error & { cause?: unknown }).cause = e;
      throw remapped;
    }
    throw e;
  }
}

export async function get<T>(url: string, init?: Pick<RequestInit, 'cache'>): Promise<T> {
  return request<T>(url, { ...init });
}

async function post<T>(url: string, body: unknown): Promise<T> {
  return request<T>(url, { method: 'POST', body: JSON.stringify(body) });
}

/** body-less POST — 触发类端点（采集/重建/执行）改前即不上送 body，合流后不得补一个 `{}` */
async function postEmpty<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'POST' });
}

async function put<T>(url: string, body: unknown): Promise<T> {
  return request<T>(url, { method: 'PUT', body: JSON.stringify(body) });
}

async function del<T>(url: string): Promise<T> {
  return request<T>(url, { method: 'DELETE' });
}

/** 前端连接类型 → 后端规范类型名（本地文件的规范名为 FILESYSTEM，后端无 FS 类型） */
const API_TYPE_ALIASES: Record<string, string> = { fs: 'FILESYSTEM' };

/** 归一化连接类型为后端可识别的大写类型名 */
export function toApiType(type: string): string {
  return API_TYPE_ALIASES[type.toLowerCase()] ?? type.toUpperCase();
}

// ─── 单源错误的文案还原（#143/B5-10b）───────────────────────

/**
 * 老接口仍有一层 `{code, data:{data:[…]}}` 双包装，单源解包后剩内层。
 * 这里只做「数组形状归位」，不是拆信封（键面固定为 `data` 一层，无回落整封的 fail-open）。
 */
export function asArray<T>(raw: unknown): T[] {
  if (Array.isArray(raw)) return raw as T[];
  const inner = (raw as { data?: unknown } | null)?.data;
  return Array.isArray(inner) ? (inner as T[]) : [];
}

/** 单源抛出的「后端有应答」错误族：HTTP 非 2xx（含 403）与 HTTP 200 伪失败 */
type SourcedError = ApiRequestError | NoAccessError;

function isSourcedError(e: unknown): e is SourcedError {
  return e instanceof ApiRequestError || e instanceof NoAccessError;
}

function statusOf(e: SourcedError): number | undefined {
  return e instanceof NoAccessError ? e.status : e.httpStatus;
}

/**
 * `HTTP ${status}` 口径腿：改前域内写 `throw new Error(\`HTTP ${res.status}\`)`，
 * 该文案是「状态码」而非后端原文，合流后由 `status` 数字还原，禁顺带把响应体推到 UI。
 *
 * @returns `null` ＝「后端没有应答」（网络层 `NetworkError`/未知抛出物）⇒ 调用方保留自身的
 * 通用失败形状（多为 i18n 词条）。把内部网络文案当后端文案渲染，等于让用户以为请求到达了服务端。
 */
export function httpStatusText(e: unknown, fallback: string): string | null {
  if (!isSourcedError(e)) return null;
  const status = statusOf(e);
  if (typeof status === 'number') return `HTTP ${status}`;
  return e.message || fallback;
}

/** 错误体可能是 JSON 信封，也可能是网关 HTML/纯文本 ⇒ 尽力解析，解析不出不编造 */
function parseErrorBody(text: string): { message?: unknown; error?: unknown } | null {
  if (!text) return null;
  try {
    const parsed = JSON.parse(text) as unknown;
    return parsed && typeof parsed === 'object' ? (parsed as { message?: unknown; error?: unknown }) : null;
  } catch {
    return null;
  }
}

/**
 * 后端原文优先腿：非 2xx 取错误体 `message` → `error` → 回落 `HTTP ${status}`；
 * 伪失败取单源 message（即信封 message 或 `Error ${code}`）；网络腿取单源 `NetworkError` 文案。
 *
 * <p>非 2xx 的响应体原文被本模块的文案回映射替换成了 `${url} → ${status}`，故优先读 `cause`
 * 上挂的原始单源错误——那是 `apiFetchData` 放进 `ApiRequestError.message` 的 body 原文。</p>
 */
export function envelopeErrorText(e: unknown, fallback: string): string {
  if (isSourcedError(e)) {
    const status = statusOf(e);
    if (typeof status === 'number') {
      const raw = e.cause instanceof Error && e.cause.message ? e.cause.message : e.message;
      const body = parseErrorBody(raw);
      const fromBody = typeof body?.message === 'string' ? body.message
        : typeof body?.error === 'string' ? body.error
        : '';
      return fromBody || `HTTP ${status}`;
    }
    return e.message || fallback;
  }
  return (e instanceof Error ? e.message : '') || fallback;
}

// re-export so sub-modules (and 既有 `from './api'` 消费者) 可继续拿到写路径原语
export { post, postEmpty, put, del };
