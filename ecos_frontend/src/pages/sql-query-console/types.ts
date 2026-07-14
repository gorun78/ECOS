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
  paramsJson?: Record<string, any>;
  timeoutSeconds: number;
  maxRows: number;
  createdAt: string;
}

export interface QueryHistoryItem {
  id: string;
  datasourceId: string;
  sqlContent: string;
  status: string;
  rowsReturned: number;
  elapsedMs: number;
  errorMsg?: string;
  startedAt: string;
}
