/**
 * knowledgeClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import type {
  KnowledgeNode,
  KnowledgeEdge,
  Goal,
  CausalLink,
  Scenario,
} from "../types";
import { API_BASE, apiFetch, apiFetchData, doFetch } from "../services/httpClient";
import { fetchWorldGoals, fetchWorldScenarios, fetchWorldCausalLinks } from "./monitorClient";

// ── Knowledge Graph ─────────────────────────────────────
export async function fetchKnowledgeGraph(): Promise<{
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}> {
  try {
    const resp = await apiFetch<{ success: boolean; data: { nodes: any[]; edges: any[] } }>("/knowledge/graph");
    if (resp.data && resp.data.nodes && resp.data.nodes.length > 0) {
      return {
        nodes: resp.data.nodes.map((n: any) => ({
          id: n.id,
          label: n.label,
          type: n.nodeType || n.type,
          nodeType: n.nodeType,
          description: n.description,
          propertiesJson: n.propertiesJson,
          properties: (() => { try { return typeof n.propertiesJson === 'string' ? JSON.parse(n.propertiesJson) : n.propertiesJson; } catch { return {}; } })(),
          createdAt: n.createdAt,
        })),
        edges: resp.data.edges.map((e: any) => ({
          id: e.id,
          source: e.sourceNodeId || e.source,
          target: e.targetNodeId || e.target,
          sourceNodeId: e.sourceNodeId,
          targetNodeId: e.targetNodeId,
          relationship: e.relationship,
          weight: e.weight,
        })),
      };
    }
    return (resp.data as any) || { nodes: [], edges: [] };
  } catch {
    return { nodes: [], edges: [] };
  }
}


// ── Knowledge Search (Cognitive Operating System) ─────────────
export const KNOWLEDGE_BASE = `${API_BASE}/v1/knowledge`;

/**
 * GET /api/v1/engine/knowledge/health — 知识引擎公开健康 VO
 * （与 data/ontology/cognitive/security 同族 /api/v1/engine/{k}/health 契约，
 * 返回 HealthCheck{status, components?, version?, uptimeMs?}）。
 */
export async function fetchKnowledgeEngineHealth(): Promise<Record<string, unknown>> {
  return apiFetchData<Record<string, unknown>>(`${API_BASE}/v1/engine/knowledge/health`);
}

export async function searchKnowledge(q: string): Promise<any> {
  return doFetch(`${KNOWLEDGE_BASE}/search?q=${encodeURIComponent(q)}`);
}

/** GET /api/knowledge/path?s=srcId&t=tgtId — find shortest path between two nodes */
export async function fetchKnowledgePath(s: string, t: string): Promise<{
  path: string[];
  edges: { id: string; source: string; target: string; relationship: string }[];
  length: number;
}> {
  return apiFetch<{ success: boolean; data: any }>(
    `/v1/knowledge/path?s=${encodeURIComponent(s)}&t=${encodeURIComponent(t)}`
  ).then(r => r.data);
}

/** GET /api/knowledge/neighbors/{id}?d=1 — get neighbors of a node */
export async function fetchKnowledgeNeighbors(id: string, d: number = 1): Promise<{
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}> {
  return apiFetch<{ success: boolean; data: any }>(
    `/v1/knowledge/neighbors/${encodeURIComponent(id)}?d=${d}`
  ).then(r => {
    const data = r.data || { nodes: [], edges: [] };
    return {
      nodes: (data.nodes || []).map((n: any) => ({
        id: n.id,
        label: n.label,
        type: n.nodeType || n.type,
        nodeType: n.nodeType,
        description: n.description,
        propertiesJson: n.propertiesJson,
        properties: (() => { try { return typeof n.propertiesJson === 'string' ? JSON.parse(n.propertiesJson) : n.propertiesJson; } catch { return {}; } })(),
        createdAt: n.createdAt,
      })),
      edges: (data.edges || []).map((e: any) => ({
        id: e.id,
        source: e.sourceNodeId || e.source,
        target: e.targetNodeId || e.target,
        sourceNodeId: e.sourceNodeId,
        targetNodeId: e.targetNodeId,
        relationship: e.relationship,
        weight: e.weight,
      })),
    };
  });
}

// ── Global Unified Search ──────────────────────────────────────

/** 后端 /api/portal/search 返回的条目 */
export interface SearchHit {
  type: string;   // OntologyEntity|Asset|Goal|Scenario|Object|Workflow|Pipeline|Knowledge|Agent
  id: string;
  name: string;
  url: string;    // SPA hash route, e.g. "/app/ontology-designer"
}

/**
 * 统一全局搜索 — 跨 Ontology / Asset / Goal / Scenario / Object
 * / Workflow / Pipeline / Knowledge / Agent 多表 ILIKE 检索。
 *
 * @param q    搜索关键词
 * @param type 搜索范围: all | ontology | asset | goal | scenario | object | workflow | pipeline | knowledge | agent
 */
export async function globalSearch(
  q: string,
  type: string = 'all'
): Promise<SearchHit[]> {
  return apiFetchData<SearchHit[]>(`${API_BASE}/v1/portal/search?q=${encodeURIComponent(q)}&type=${encodeURIComponent(type)}`);
}

// ── Goals / Causal / Scenarios (async with mock fallback) ─
export async function getGoals(): Promise<Goal[]> { return fetchWorldGoals(); }
export async function getCausalLinks(): Promise<CausalLink[]> { return fetchWorldCausalLinks(); }
export async function getScenarios(): Promise<Scenario[]> { return fetchWorldScenarios(); }


// ── ECOS Knowledge Graph ─────────────────────────────────
export async function fetchEcosKnowledgeGraph(): Promise<any> {
  return apiFetchData(`${API_BASE}/v1/knowledge/ecos-graph`);
}

