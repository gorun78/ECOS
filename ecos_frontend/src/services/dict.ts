/**
 * Data Dictionary API Service Layer
 * Encapsulates all CRUD operations for data dictionary tables & columns.
 * Backend returns ApiResponse<T>: { code: 0 (success), message, data }
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

const API_BASE = '/api/dict';

// ── Types ─────────────────────────────────────────────

export interface DictColumn {
  id: string;
  tableId: string;
  name: string;
  type: string;
  length?: number;
  precision?: number;
  scale?: number;
  nullable: boolean;
  primaryKey: boolean;
  defaultValue?: string;
  description: string;
  sortOrder: number;
  createdAt: string;
  updatedAt?: string;
}

export interface DictTable {
  id: string;
  code: string | null;
  name: string;
  nameZh: string;
  schema: string;
  description: string;
  status: string;          // DRAFT | PUBLISHED | DEPRECATED
  source: string;          // e.g. MySQL, PostgreSQL, Hive
  rowCount?: number;
  storageSize?: string;
  owner: string | null;
  tags: string[];
  columns: DictColumn[];
  createdBy: string | null;
  createdAt: string;
  updatedAt?: string;
}

export interface DictTableListResult {
  items: DictTable[];
  total: number;
}

// ── ApiResponse ───────────────────────────────────────

interface ApiResponse<T> {
  code: number;
  message?: string;
  data?: T;
  timestamp?: number;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!res.ok) {
    throw new Error(`Dict API ${path} returned HTTP ${res.status}`);
  }

  const body = await res.json() as ApiResponse<T>;

  if (body.code !== 0) {
    throw new Error(body.message || `API error code=${body.code}`);
  }

  return body.data as T;
}

// ── Table CRUD ────────────────────────────────────────

/**
 * Fetch all dictionary tables with optional filters.
 */
export async function getDictTables(params?: {
  schema?: string;
  status?: string;
  search?: string;
}): Promise<DictTableListResult> {
  const qs = new URLSearchParams();
  if (params?.schema) qs.set('schema', params.schema);
  if (params?.status) qs.set('status', params.status);
  if (params?.search) qs.set('search', params.search);
  const query = qs.toString();
  const path = query ? `/tables?${query}` : '/tables';

  const data = await request<DictTable[]>(path);
  return { items: data ?? [], total: data?.length ?? 0 };
}

/**
 * Fetch a single table with its columns.
 */
export async function getDictTable(id: string): Promise<DictTable> {
  return request<DictTable>(`/tables/${id}`);
}

/**
 * Create a new dictionary table entry.
 */
export async function createDictTable(data: {
  name: string;
  nameZh: string;
  schema: string;
  description?: string;
  source?: string;
}): Promise<{ id: string }> {
  const table = await request<DictTable>('/tables', {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return { id: table.id };
}

/**
 * Update an existing dictionary table.
 */
export async function updateDictTable(
  id: string,
  data: {
    name?: string;
    nameZh?: string;
    description?: string;
    status?: string;
    tags?: string[];
  }
): Promise<void> {
  await request(`/tables/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/**
 * Delete a dictionary table.
 */
export async function deleteDictTable(id: string): Promise<void> {
  await request(`/tables/${id}`, { method: 'DELETE' });
}

// ── Column CRUD ───────────────────────────────────────

/**
 * Add a column to a table.
 */
export async function createDictColumn(
  tableId: string,
  data: {
    name: string;
    type: string;
    length?: number;
    precision?: number;
    scale?: number;
    nullable?: boolean;
    primaryKey?: boolean;
    defaultValue?: string;
    description?: string;
    sortOrder?: number;
  }
): Promise<DictColumn> {
  return request<DictColumn>(`/tables/${tableId}/columns`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

/**
 * Update a column definition.
 */
export async function updateDictColumn(
  tableId: string,
  columnId: string,
  data: {
    name?: string;
    type?: string;
    length?: number;
    precision?: number;
    scale?: number;
    nullable?: boolean;
    primaryKey?: boolean;
    defaultValue?: string;
    description?: string;
    sortOrder?: number;
  }
): Promise<void> {
  await request(`/tables/${tableId}/columns/${columnId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

/**
 * Delete a column.
 */
export async function deleteDictColumn(
  tableId: string,
  columnId: string
): Promise<void> {
  await request(`/tables/${tableId}/columns/${columnId}`, {
    method: 'DELETE',
  });
}

/**
 * Reorder columns.
 */
export async function reorderDictColumns(
  tableId: string,
  columnIds: string[]
): Promise<void> {
  await request(`/tables/${tableId}/columns/reorder`, {
    method: 'PUT',
    body: JSON.stringify({ columnIds }),
  });
}
