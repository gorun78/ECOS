/**
 * Data Workbench — 数据血缘 (拓扑 / 影响度 / 重建)
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { get, LINEAGE_TOPOL } from './_base';
import { authOnlyHeaders as authHeaders } from '../../../services/auth';

/** Data Lineage — 查询持久化拓扑（全局表级/字段级血缘全景图） */
/** 后端 DataLineageService.getTopology 返回 Map（nodes/edges/total_nodes/total_edges/from_db） */
export interface LineageTopologyNode {
  id: string;
  type: string;
  label: string;
  table?: string;
  pipeline_task_id?: string;
  pipeline_task_name?: string;
}
export interface LineageTopologyEdge {
  id: string;
  source: string;
  target: string;
  transform?: string;
  pipeline_task_id?: string;
  pipeline_task_name?: string;
}
export interface LineageTopology {
  nodes: LineageTopologyNode[];
  edges: LineageTopologyEdge[];
  total_nodes: number;
  total_edges: number;
  from_db?: boolean;
}
export async function fetchLineageTopology(): Promise<LineageTopology> {
  try {
    const data = await get<Partial<LineageTopology>>(LINEAGE_TOPOL);
    return {
      nodes: Array.isArray(data?.nodes) ? data.nodes : [],
      edges: Array.isArray(data?.edges) ? data.edges : [],
      total_nodes: Number(data?.total_nodes) || 0,
      total_edges: Number(data?.total_edges) || 0,
      from_db: data?.from_db,
    };
  } catch (e) {
    console.warn('[data-workbench] fetchLineageTopology failed:', e);
    return { nodes: [], edges: [], total_nodes: 0, total_edges: 0, from_db: false };
  }
}

/** Data Lineage — 影响度分析：从 startNode 做双向 N 层 BFS */
/** 后端 DataLineageService.computeImpact 返回 Map，downstream/upstream 每项含 id/hop/label/riskScore */
interface LineageImpactNodeRaw {
  id?: string;
  hop?: number;
  label?: string;
  riskScore?: number;
}
interface LineageImpactRaw {
  startNode?: string;
  canonicalStartNode?: string;
  matched?: boolean;
  depth?: number;
  severity?: string;
  totalRisk?: number;
  branchCount?: number;
  downstream?: LineageImpactNodeRaw[];
  upstream?: LineageImpactNodeRaw[];
}
export async function fetchLineageImpact(
  startNode: string,
  depth = 3
): Promise<{
  startNode: string;
  canonicalStartNode: string;
  matched: boolean;
  depth: number;
  severity: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  totalRisk: number;
  downstream: { id: string; hop: number; label: string; riskScore: number }[];
  upstream: { id: string; hop: number; label: string; riskScore: number }[];
  branchCount: number;
} | null> {
  if (!startNode || !startNode.trim()) return null;
  try {
    const params = new URLSearchParams();
    params.set('startNode', startNode);
    params.set('depth', String(depth));
    const url = '/api/v1/engine/data/lineage/impact?' + params.toString();
    const res = await fetch(url, { headers: { ...authHeaders() } });
    if (!res.ok) return null;
    const json = await res.json();
    const data: LineageImpactRaw = json?.data ?? json;
    if (!data) return null;
    const toArr = (v: unknown): LineageImpactNodeRaw[] => (Array.isArray(v) ? (v as LineageImpactNodeRaw[]) : []);
    return {
      startNode: data.startNode as string,
      canonicalStartNode: (data.canonicalStartNode as string) || data.startNode || '',
      matched: Boolean(data.matched),
      depth: Number(data.depth) || 1,
      severity: (data.severity as string) || 'NONE',
      totalRisk: Number(data.totalRisk) || 0,
      downstream: toArr(data.downstream).map((n) => ({
        id: n.id as string, hop: Number(n.hop) || 1,
        label: n.label as string, riskScore: Number(n.riskScore) || 0,
      })),
      upstream: toArr(data.upstream).map((n) => ({
        id: n.id as string, hop: Number(n.hop) || 1,
        label: n.label as string, riskScore: Number(n.riskScore) || 0,
      })),
      branchCount: Number(data.branchCount) || 0,
    };
  } catch (e) {
    console.warn('[data-workbench] fetchLineageImpact failed:', e);
    return null;
  }
}

/** Data Lineage — 重建并持久化血缘（同步全量解析 pipeline_task） */
export async function rebuildLineage(limit = 0): Promise<{
  total_nodes: number; total_edges: number;
  tasks_scanned: number; tasks_parsed: number; sql_failed: number;
  definitions_scanned?: number;
}> {
  try {
    const res = await fetch(`${LINEAGE_TOPOL}/rebuild?limit=${limit}`, {
      method: 'POST', headers: { ...authHeaders() },
    });
    if (!res.ok) throw new Error(`${LINEAGE_TOPOL}/rebuild → ${res.status}`);
    const json = await res.json();
    const data = json?.data ?? json;
    return {
      total_nodes: Number(data?.total_nodes) || 0,
      total_edges: Number(data?.total_edges) || 0,
      tasks_scanned: Number(data?.tasks_scanned) || 0,
      tasks_parsed: Number(data?.tasks_parsed) || 0,
      sql_failed: Number(data?.sql_failed) || 0,
      ...(data?.definitions_scanned != null
        ? { definitions_scanned: Number(data.definitions_scanned) }
        : {}),
    };
  } catch (e) {
    console.warn('[data-workbench] rebuildLineage failed:', e);
    return { total_nodes: 0, total_edges: 0, tasks_scanned: 0, tasks_parsed: 0, sql_failed: 0 };
  }
}
