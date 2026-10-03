/**
 * Data Workbench — 五层载体 REST 收口（详细设计-02 B.4 / W66 按域拆分 apiLayers.ts）。
 *
 * 消费 data-engine 分层端点（Bearer 由 authOnlyHeaders 注入）：
 *   GET  /api/v1/engine/data/layers                              — 分层概览（5 层 count + declared）
 *   GET  /api/v1/engine/data/layers/{layer}                      — 按层列载体（td_data_resource 行）
 *   GET  /api/v1/engine/data/layers/{layer}/resources/{id}/rows  — 实例行增量读取（水位线）
 *   GET  /api/v1/engine/data/layers/{layer}/resources/{id}/sample— 实例行抽样（dry-run）
 *   POST /api/v1/engine/data/layers/{layer}/carriers             — 登记载体（管理员 / D.2 registerLayerCarrier）
 *
 * 注意：后端 `data` 字段即 ApiResponse.data（`{ code, message, data }` 包裹已在这里拆包）。
 */
import { authOnlyHeaders } from '../../services/auth';

const LAYERS = '/api/v1/engine/data/layers';

/** 五层（顺序 = DataLayer 枚举序）。 */
export const LAYER_ORDER = ['SOURCE', 'RAW', 'CURATED', 'SEMANTIC', 'APPLICATION'] as const;
export type DataLayerName = (typeof LAYER_ORDER)[number];

/** 分层概览中单层形态（DataLayerService.getLayerSummary）。 */
export interface LayerSummary {
  count: number;
  declared: boolean;
}
export interface LayerSummaryMap {
  total: number;
  [layer: string]: LayerSummary | number;
}

/** td_data_resource 一行（载体），只标注 B.4 需要展示的字段；其余键保持透传。 */
export interface LayerCarrier {
  resource_id: string;
  resource_name: string;
  resource_type?: string;
  layer?: string;
  layer_bucket?: string;
  carrier_ref?: string;
  storage_kind?: string;
  declared_flag?: string;
  record_count?: number;
  field_count?: number;
  last_sync_time?: string;
  update_time?: string;
  description?: string;
  status?: string;
  [k: string]: unknown;
}

/** DataLayerRowsVO（后端强类型）——实例行读取/抽样结果。 */
export interface DataLayerRows {
  layer: string;
  resourceId: string;
  rows: Record<string, unknown>[];
  count: number;
  nextWatermark: string | null;
  hasMore: boolean;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...authOnlyHeaders(), ...(init?.headers ?? {}) },
    ...init,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} @ ${path}`);
  let body: { data?: T } & Record<string, unknown> = {};
  try { body = (await res.json()) as typeof body; } catch { body = {}; }
  // ApiResponse<T>：data 字段。部分端点 data 直接是载荷；本模块所有端点均走 ApiResponse.success。
  return (body.data ?? (body as unknown as T)) as T;
}

/** 分层概览 → Map<layer, LayerSummary> + total。 */
export function getLayerSummary(): Promise<LayerSummaryMap> {
  return request<LayerSummaryMap>(LAYERS);
}

/** 按层列载体（td_data_resource 行）。 */
export function getResourcesByLayer(layer: string): Promise<{ layer: string; resources: LayerCarrier[]; total: number }> {
  return request<{ layer: string; resources: LayerCarrier[]; total: number }>(
    `${LAYERS}/${encodeURIComponent(layer)}`,
  );
}

/** 实例行增量读取（水位线）。`watermark` 空 = 从头读；`limit` 空 = 默认 1000。 */
export function readLayerRows(
  layer: string, resourceId: string, watermark?: string, limit?: number,
): Promise<DataLayerRows> {
  const q = new URLSearchParams();
  if (watermark) q.set('watermark', watermark);
  if (limit) q.set('limit', String(limit));
  const qs = q.toString() ? `?${q.toString()}` : '';
  return request<DataLayerRows>(
    `${LAYERS}/${encodeURIComponent(layer)}/resources/${encodeURIComponent(resourceId)}/rows${qs}`,
  );
}

/** 实例行抽样（dry-run 预览）。`limit` 空 = 默认 100，硬上限 1000。 */
export function sampleLayerRows(layer: string, resourceId: string, limit?: number): Promise<DataLayerRows> {
  const q = new URLSearchParams();
  if (limit) q.set('limit', String(limit));
  const qs = q.toString() ? `?${q.toString()}` : '';
  return request<DataLayerRows>(
    `${LAYERS}/${encodeURIComponent(layer)}/resources/${encodeURIComponent(resourceId)}/sample${qs}`,
  );
}

/** 登记层载体（L3 准入 / 管理员）。 */
export function registerLayerCarrier(
  layer: string, carrierRef: string, storageKind: string,
): Promise<{ resourceId: string; layer: string; carrierRef: string; storageKind: string }> {
  return request<{ resourceId: string; layer: string; carrierRef: string; storageKind: string }>(
    `${LAYERS}/${encodeURIComponent(layer)}/carriers`,
    { method: 'POST', body: JSON.stringify({ carrierRef, storageKind }) },
  );
}
