/**
 * SQL Query Console — API Layer
 * 连接 ECOS 后端: DataSourceController, EngineDataQueryController
 * @license Apache-2.0
 */

import { apiFetchData } from '../../api';
import type {
  DataSource,
  TreeNode,
  QueryExecuteRequest,
  QueryExecuteResponse,
  QueryHistoryItem,
  QueryTemplate,
  SaveTemplateRequest,
} from './types';

const DATASOURCE_URL = '/datanet/datasource';
const SCHEMA_URL = '/api/v1/engine/data/query/schema';
const EXECUTE_URL = '/api/v1/engine/data/query/execute';
const HISTORY_URL = '/api/v1/engine/data/query/history';
const TEMPLATE_URL = '/api/v1/engine/data/query/template';

/** 通用 GET 请求辅助函数 */
async function get<T>(url: string): Promise<T> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, { headers });
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('登录已过期');
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  const json = await res.json();
  return (json.data !== undefined ? json.data : json) as T;
}

/** 通用 POST 请求辅助函数 */
async function post<T>(url: string, body: unknown): Promise<T> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('登录已过期');
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  const json = await res.json();
  return (json.data !== undefined ? json.data : json) as T;
}

/** 通用 DELETE 请求辅助函数 */
async function del<T>(url: string): Promise<T> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(url, {
    method: 'DELETE',
    headers,
  });
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('登录已过期');
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  const json = await res.json();
  return (json.data !== undefined ? json.data : json) as T;
}

// ─── 数据源 ───────────────────────────────────────────────

/** 获取数据源列表 */
export async function fetchDataSources(): Promise<DataSource[]> {
  try {
    return await get<DataSource[]>(DATASOURCE_URL);
  } catch (e) {
    console.warn('[sql-query-console] fetchDataSources failed:', e);
    return [];
  }
}

// ─── Schema 树 ────────────────────────────────────────────

/** 获取数据源的 Schema 树 */
export async function fetchSchemaTree(datasourceId: string): Promise<TreeNode[]> {
  try {
    return await get<TreeNode[]>(`${SCHEMA_URL}/${datasourceId}`);
  } catch (e) {
    console.warn('[sql-query-console] fetchSchemaTree failed:', e);
    return [];
  }
}

// ─── SQL 执行 ─────────────────────────────────────────────

/** 执行 SQL 查询 */
export async function executeQuery(req: QueryExecuteRequest): Promise<QueryExecuteResponse> {
  return post<QueryExecuteResponse>(EXECUTE_URL, {
    datasourceId: req.datasourceId,
    sql: req.sql,
    params: req.params,
    page: req.page || 1,
    pageSize: req.pageSize || 50,
  });
}

// ─── 查询历史 ─────────────────────────────────────────────

/** 获取查询历史 */
export async function fetchQueryHistory(
  page = 1,
  pageSize = 20
): Promise<{ data: QueryHistoryItem[]; total: number }> {
  try {
    const result = await get<{ data: QueryHistoryItem[]; total: number }>(
      `${HISTORY_URL}?page=${page}&pageSize=${pageSize}`
    );
    return result;
  } catch (e) {
    console.warn('[sql-query-console] fetchQueryHistory failed:', e);
    return { data: [], total: 0 };
  }
}

/** 删除历史记录 */
export async function deleteHistoryItem(id: string): Promise<boolean> {
  try {
    await del(`${HISTORY_URL}/${id}`);
    return true;
  } catch (e) {
    console.warn('[sql-query-console] deleteHistoryItem failed:', e);
    return false;
  }
}

/** 清空历史记录 */
export async function clearQueryHistory(): Promise<boolean> {
  try {
    await del(HISTORY_URL);
    return true;
  } catch (e) {
    console.warn('[sql-query-console] clearQueryHistory failed:', e);
    return false;
  }
}

// ─── 模板管理 ─────────────────────────────────────────────

/** 获取模板列表 */
export async function fetchTemplates(): Promise<QueryTemplate[]> {
  try {
    return await get<QueryTemplate[]>(TEMPLATE_URL);
  } catch (e) {
    console.warn('[sql-query-console] fetchTemplates failed:', e);
    return [];
  }
}

/** 保存模板 */
export async function saveTemplate(req: SaveTemplateRequest): Promise<QueryTemplate> {
  return post<QueryTemplate>(TEMPLATE_URL, req);
}

/** 删除模板 */
export async function deleteTemplate(id: string): Promise<boolean> {
  try {
    await del(`${TEMPLATE_URL}/${id}`);
    return true;
  } catch (e) {
    console.warn('[sql-query-console] deleteTemplate failed:', e);
    return false;
  }
}
