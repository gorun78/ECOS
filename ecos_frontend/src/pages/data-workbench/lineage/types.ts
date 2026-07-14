/**
 * LineageMapView — shared types
 * Extracted from LineageMapView.tsx
 * @license Apache-2.0
 */

import type { DataConnection, DataSyncTask, DataPipeline, DataHealthCheck, ObjectType, Dataset } from '../types';

// ─── Props ───────────────────────────────────────────────────

export interface LineageMapViewProps {
  connections: DataConnection[];
  syncTasks: DataSyncTask[];
  pipelines: DataPipeline[];
  healthChecks: DataHealthCheck[];
  objectTypes: ObjectType[];
  datasets: Dataset[];
}


// ─── 自定义节点数据接口 ──────────────────────────────────────

export interface SourceNodeData {
  label: string;
  sublabel: string;
  type: string;
  status: DataConnection['status'];
  tablesAvailable: number;
  raw: DataConnection;
  [key: string]: unknown;
}

export interface IngestNodeData {
  label: string;
  sublabel: string;
  status: DataSyncTask['status'];
  syncMode?: string;
  recordsSynced?: number;
  raw: DataSyncTask;
  [key: string]: unknown;
}

export interface PipelineNodeData {
  label: string;
  sublabel: string;
  status: DataPipeline['status'];
  nodeCount: number;
  expressionsCount?: number;
  raw: DataPipeline;
  [key: string]: unknown;
}

export interface DatasetNodeData {
  label: string;
  sublabel: string;
  columns?: string[];
  rowCount?: number;
  raw: Dataset;
  [key: string]: unknown;
}

export interface OntologyNodeData {
  label: string;
  sublabel: string;
  domain: string;
  propertiesCount: number;
  raw: ObjectType;
  [key: string]: unknown;
}

