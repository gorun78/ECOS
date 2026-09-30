/**
 * Data Workbench — backend API layer · 共享 HTTP 原语 + 端点常量 + 类型别名
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */

// 鉴权头单源定义见 services/auth.ts (H6-T1)
import { authOnlyHeaders as authHeaders } from '../../../services/auth';

// ─── API 端点常量 ──────────────────────────────────────────
export const DATANET_DS = '/datanet/datasource';           // DataSourceController
export const INTEGRATION  = '/api/integration/metadata';   // IntegrationMetadataController (connections + syncTasks)
export const PIPELINE_DEFS = '/api/v1/pipeline/definitions'; // PipelineController
export const LINEAGE_TOPOL = '/api/v1/engine/data/lineage/topology';

export async function get<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { ...authHeaders() } });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  // ApiResponse<T> 包裹: { code, message, data }
  return (json.data ?? json) as T;
}

async function post<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

async function put<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

async function del<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: 'DELETE', headers: { ...authHeaders() } });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

/** 前端连接类型 → 后端规范类型名（本地文件的规范名为 FILESYSTEM，后端无 FS 类型） */
const API_TYPE_ALIASES: Record<string, string> = { fs: 'FILESYSTEM' };

/** 归一化连接类型为后端可识别的大写类型名 */
export function toApiType(type: string): string {
  return API_TYPE_ALIASES[type.toLowerCase()] ?? type.toUpperCase();
}

// re-export so sub-modules (and 既有 `from './api'` 消费者) 可继续拿到写路径原语
export { post, put, del };
