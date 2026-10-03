/**
 * Data Workbench — 共享 HTTP 层（W66 拆分抽出）。
 * 各域文件 (apiDatasource/apiPipeline/apiDatalake) 统一从此处复用 fetch/JSON 助手；
 * 鉴权头由各域文件直接从 services/auth 注入（单源），此处不重复 export。
 * @license Apache-2.0
 */
import { authOnlyHeaders as authHeaders } from '../../services/auth';
// 鉴权头单源：此处是 data-workbench 域唯一 import authOnlyHeaders 的落点。
// 需要裸 fetch 的域文件请从本模块 `import { authHeaders } from './httpClient'`。
export { authHeaders };

async function get<T>(url: string): Promise<T> {
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

export { get, post, put, del };
