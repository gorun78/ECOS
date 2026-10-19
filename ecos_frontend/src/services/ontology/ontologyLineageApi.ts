/**
 * 数据血缘 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Lineage（T2.5）—— 血缘边 CRUD、图、trace、parse、impact。
 * 端点前缀：/api/v1/lineage。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";
import { API_BASE } from "../httpClient";
import type {
  LineageEdge,
  CreateLineageDTO,
  UpdateLineageDTO,
  LineageGraph,
  LineageTraceResult,
  LineageImpactResult,
  ParseLineageResult,
} from "../../types/ontology";

// ================================================================
// 数据血缘 (Lineage) — T2.5
// ================================================================

const LINEAGE_BASE = `${API_BASE}/v1/lineage`;

export async function fetchLineages(params?: { source?: string; target?: string; direction?: string }) {
  const qs = new URLSearchParams();
  if (params?.source) qs.set("source", params.source);
  if (params?.target) qs.set("target", params.target);
  if (params?.direction) qs.set("direction", params.direction);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<LineageEdge[]>(`${LINEAGE_BASE}${query}`);
}

export async function fetchLineage(id: string) {
  return apiFetchData<LineageEdge>(`${LINEAGE_BASE}/${id}`);
}

export async function createLineage(data: CreateLineageDTO) {
  return apiFetchData<LineageEdge>(LINEAGE_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateLineage(id: string, data: UpdateLineageDTO) {
  return apiFetchData<LineageEdge>(`${LINEAGE_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteLineage(id: string) {
  await apiFetchData(`${LINEAGE_BASE}/${id}`, { method: "DELETE" });
}

export async function fetchLineageGraph() {
  return apiFetchData<LineageGraph>(`${LINEAGE_BASE}/graph`);
}

export async function traceLineage(nodeId: string) {
  return apiFetchData<LineageTraceResult>(`${LINEAGE_BASE}/trace/${nodeId}`);
}

export async function fetchLineageEntities() {
  return apiFetchData(`${LINEAGE_BASE}/entities`);
}

export async function parseLineage(data: { format: string; data: string }) {
  return apiFetchData<ParseLineageResult>(`${LINEAGE_BASE}/parse`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function fetchLineageImpact(params?: { objectId?: string; rootObject?: string; depth?: number }) {
  const qs = new URLSearchParams();
  if (params?.objectId) qs.set("objectId", params.objectId);
  if (params?.rootObject) qs.set("rootObject", params.rootObject);
  if (params?.depth) qs.set("depth", String(params.depth));
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<LineageImpactResult>(`${LINEAGE_BASE}/impact${query}`);
}
