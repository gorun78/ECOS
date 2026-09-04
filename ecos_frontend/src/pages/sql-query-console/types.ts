/**
 * SQL Query Console — Type definitions
 * @license Apache-2.0
 */

export interface DataSource {
  datasourceId: string;
  datasourceName: string;
  datasourceType: string;
}

export interface SchemaTreeNode {
  name: string;
  type: 'schema' | 'table' | 'view' | 'column';
  children?: SchemaTreeNode[];
  dataType?: string;
}

export interface QueryResult {
  columns: string[];
  rows: Record<string, any>[];
  rowCount: number;
  elapsedMs: number;
}

export interface QueryTemplate {
  id: string;
  name: string;
  description?: string;
  datasourceId: string;
  sqlContent: string;
  sql?: string;
  paramsJson?: Record<string, any>;
  timeoutSeconds: number;
  maxRows: number;
  createdAt: string;
}

export interface TreeNode {
  id: string;
  name: string;
  type?: string;
  [key: string]: unknown;
}

export interface QueryHistoryItem {
  id: string;
  datasourceId: string;
  sqlContent: string;
  status: string;
  rowsReturned: number;
  elapsedMs: number;
  startedAt: string;
  errorMessage?: string;
  errorMsg?: string; // 兼容旧 schema（typo 字段，渲染时合并到 errorMessage）
}

export interface ColumnMeta {
  name: string;
  type: string;
  nullable?: boolean;
  comment?: string;
}

export interface Pagination {
  page: number;
  pageSize: number;
  total: number;
}

export interface QueryExecuteRequest {
  datasourceId: string;
  sql: string;
  params?: Record<string, unknown>;
  timeoutSeconds?: number;
  maxRows?: number;
  page?: number;
  pageSize?: number;
}

export interface QueryExecuteResponse {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  elapsedMs: number;
  columnsMeta?: ColumnMeta[];
  page?: number;
  pageSize?: number;
  total?: number;
  executionTimeMs?: number;
}

export interface SaveTemplateRequest {
  name: string;
  description?: string;
  datasourceId: string;
  sql: string;
  paramsJson?: Record<string, unknown>;
}
