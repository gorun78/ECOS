import { apiFetchData } from '../../../api';
import type {
  KnowledgeSettings,
  RagRequest,
  RagResult,
  SyncStatus,
  SyncLog,
  MetadataAsset,
} from '../typesAndConstants';

const KNOWLEDGE_BASE = '/api/knowledge';
const GRAPH_BASE = '/api/knowledge';
const GLOSSARY_BASE = '/api/glossary';
const CATALOG_BASE = '/api/catalog';
const COGNITIVE_BASE = '/api/v1/cognitive';

export async function fetchGraph(domain?: string) {
  const q = domain ? `?domain=${encodeURIComponent(domain)}` : '';
  return apiFetchData<{ nodes: unknown[]; edges: unknown[] }>(`${GRAPH_BASE}/graph${q}`);
}

export async function fetchNode(id: string) {
  return apiFetchData(`${GRAPH_BASE}/nodes/${encodeURIComponent(id)}`);
}

export async function searchKnowledge(query: string) {
  return apiFetchData(`${GRAPH_BASE}/search?q=${encodeURIComponent(query)}`);
}

export async function findPath(source: string, target: string) {
  return apiFetchData(`${GRAPH_BASE}/path?s=${encodeURIComponent(source)}&t=${encodeURIComponent(target)}`);
}

export async function fetchNeighbors(id: string, depth = 1) {
  return apiFetchData(`${GRAPH_BASE}/neighbors/${encodeURIComponent(id)}?d=${depth}`);
}

export async function createNode(data: Record<string, unknown>) {
  return apiFetchData(`${GRAPH_BASE}/nodes`, { method: 'POST', body: JSON.stringify(data) });
}

export async function createEdge(data: Record<string, unknown>) {
  return apiFetchData(`${GRAPH_BASE}/edges`, { method: 'POST', body: JSON.stringify(data) });
}

export async function getDataSource() {
  return apiFetchData(`${GRAPH_BASE}/source`);
}

export async function fetchSyncStatuses(): Promise<SyncStatus[]> {
  try {
    const data = await apiFetchData<Record<string, unknown>>(`${KNOWLEDGE_BASE}/sync/status`);
    return (data?.objectTypes as SyncStatus[]) || [];
  } catch {
    return [];
  }
}

export async function triggerFullSync() {
  return apiFetchData(`${KNOWLEDGE_BASE}/sync/trigger`, { method: 'POST' });
}

export async function triggerObjectSync(objectType: string) {
  return apiFetchData(`${KNOWLEDGE_BASE}/sync/object/${encodeURIComponent(objectType)}`, { method: 'POST' });
}

export async function fetchSyncLogs(): Promise<SyncLog[]> {
  try {
    const data = await apiFetchData<SyncLog[]>(`${KNOWLEDGE_BASE}/sync/logs`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function fetchOntologyMappings() {
  try {
    return await apiFetchData('/api/v1/ontology/mappings');
  } catch {
    return { mappings: [] };
  }
}

export async function saveOntologyMappings(mappings: unknown) {
  try {
    return await apiFetchData('/api/v1/ontology/mappings', {
      method: 'POST',
      body: JSON.stringify(mappings),
    });
  } catch {
    return null;
  }
}

export async function exportOntology() {
  try {
    return await apiFetchData('/api/v1/ontology/export');
  } catch {
    return '';
  }
}

export async function fetchIndexStatus() {
  try {
    return await apiFetchData(`${KNOWLEDGE_BASE}/index-status`);
  } catch {
    return { nodeCount: 0, relationshipCount: 0 };
  }
}

export async function syncVectors(config: Record<string, unknown>) {
  try {
    return await apiFetchData('/api/v1/knowledge/sync', {
      method: 'POST',
      body: JSON.stringify(config),
    });
  } catch {
    return { status: 'queued' };
  }
}

export async function runRAGQuery(req: RagRequest): Promise<RagResult> {
  try {
    const data = await apiFetchData<RagResult>('/api/v1/knowledge/rag', {
      method: 'POST',
      body: JSON.stringify(req),
    });
    return data || { answer: '', sources: [], tokensUsed: 0 };
  } catch {
    return { answer: '', sources: [], tokensUsed: 0 };
  }
}

export async function runKnowledgeQuery(query: string) {
  try {
    return await apiFetchData('/api/v1/knowledge/query', {
      method: 'POST',
      body: JSON.stringify({ query }),
    });
  } catch {
    return [];
  }
}

export async function getSettings(): Promise<KnowledgeSettings> {
  try {
    const data = await apiFetchData<KnowledgeSettings>(`${KNOWLEDGE_BASE}/settings`);
    return data || {
      defaultVectorModel: 'text-embedding-004',
      defaultChunkSize: 512,
      defaultOverlap: 50,
      neo4jEnabled: false,
      autoSyncEnabled: false,
      maxRetrievalResults: 5,
    };
  } catch {
    return {
      defaultVectorModel: 'text-embedding-004',
      defaultChunkSize: 512,
      defaultOverlap: 50,
      neo4jEnabled: false,
      autoSyncEnabled: false,
      maxRetrievalResults: 5,
    };
  }
}

export async function updateSettings(data: Partial<KnowledgeSettings>) {
  return apiFetchData(`${KNOWLEDGE_BASE}/settings`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function fetchGlossaryTerms(params?: { domain?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  const query = qs.toString();
  const url = query ? `${GLOSSARY_BASE}/terms?${query}` : `${GLOSSARY_BASE}/terms`;
  try {
    const data = await apiFetchData<unknown[]>(url);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function createGlossaryTerm(data: { name: string; definition: string; domain?: string }) {
  return apiFetchData(`${GLOSSARY_BASE}/terms`, { method: 'POST', body: JSON.stringify(data) });
}

export async function updateGlossaryTerm(id: string, data: Record<string, unknown>) {
  return apiFetchData(`${GLOSSARY_BASE}/terms/${id}`, { method: 'PUT', body: JSON.stringify(data) });
}

export async function deleteGlossaryTerm(id: string) {
  return apiFetchData(`${GLOSSARY_BASE}/terms/${id}`, { method: 'DELETE' });
}

export async function classifyAsset(assetId: string) {
  return apiFetchData(`${CATALOG_BASE}/assets/${encodeURIComponent(assetId)}/auto-classify`, { method: 'POST' });
}

export async function fetchCognitiveConfig() {
  return apiFetchData(`${COGNITIVE_BASE}/config`);
}

export async function updateCognitiveConfig(updates: Array<{ config_key: string; config_value: string }>) {
  return apiFetchData(`${COGNITIVE_BASE}/config`, {
    method: 'PUT',
    body: JSON.stringify(updates),
  });
}

export async function fetchLineageImpact(startNode: string) {
  try {
    return await apiFetchData(`/api/v1/lineage/impact?startNode=${encodeURIComponent(startNode)}`);
  } catch {
    return null;
  }
}

export async function parseLineage(format: string, payload: string) {
  try {
    return await apiFetchData('/api/v1/lineage/parse', {
      method: 'POST',
      body: JSON.stringify({ format, payload }),
    });
  } catch {
    return null;
  }
}

export async function fetchIntegrationMetadata() {
  try {
    return await apiFetchData('/api/integration/metadata');
  } catch {
    return null;
  }
}

export async function fetchIntegrationLogs() {
  try {
    return await apiFetchData('/api/integration/logs');
  } catch {
    return [];
  }
}

export async function toggleSimulationDrift(type: string, enabled: boolean) {
  try {
    return await apiFetchData('/api/integration/metadata/drift', {
      method: 'POST',
      body: JSON.stringify({ type, enabled }),
    });
  } catch {
    return null;
  }
}

export const knowledgeApi = {
  fetchGraph,
  fetchNode,
  searchKnowledge,
  findPath,
  fetchNeighbors,
  createNode,
  createEdge,
  getDataSource,
  fetchSyncStatuses,
  triggerFullSync,
  triggerObjectSync,
  fetchSyncLogs,
  fetchOntologyMappings,
  saveOntologyMappings,
  exportOntology,
  fetchIndexStatus,
  syncVectors,
  runRAGQuery,
  runKnowledgeQuery,
  getSettings,
  updateSettings,
  fetchGlossaryTerms,
  createGlossaryTerm,
  updateGlossaryTerm,
  deleteGlossaryTerm,
  classifyAsset,
  fetchCognitiveConfig,
  updateCognitiveConfig,
  fetchLineageImpact,
  parseLineage,
  fetchIntegrationMetadata,
  fetchIntegrationLogs,
  toggleSimulationDrift,
};
