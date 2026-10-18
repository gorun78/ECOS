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
 * 由 `ApiRequestError.httpStatus` 回映射；原始单源错误挂在 `cause` 上以便取号。</p>
 * @license Apache-2.0
 */

import { API_BASE, ApiRequestError, apiFetchData } from '../../../services/httpClient';

// ─── API 端点常量 ──────────────────────────────────────────
export const DATANET_DS = '/datanet/datasource';           // DataSourceController
export const INTEGRATION  = `${API_BASE}/integration/metadata`;   // IntegrationMetadataController (connections + syncTasks)
export const PIPELINE_DEFS = `${API_BASE}/v1/pipeline/definitions`; // PipelineController
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
      const remapped = new Error(`${url} → ${e.httpStatus}`);
      (remapped as Error & { cause?: unknown }).cause = e;
      throw remapped;
    }
    throw e;
  }
}

export async function get<T>(url: string): Promise<T> {
  return request<T>(url, {});
}

async function post<T>(url: string, body: unknown): Promise<T> {
  return request<T>(url, { method: 'POST', body: JSON.stringify(body) });
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

// re-export so sub-modules (and 既有 `from './api'` 消费者) 可继续拿到写路径原语
export { post, put, del };
