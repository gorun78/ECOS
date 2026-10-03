// B.9 / K-55 — Tab「wiki 知识导航 + governance(config/rules)」API 切片。
// 内容自原 knowledgeApi.ts 逐字搬移；原文件退化为 barrel，语义与签名保持不变（API 只增不改）。
import { apiFetchData } from '../../../../api';
import { GLOSSARY_BASE, CATALOG_BASE, COGNITIVE_BASE, RULES_BASE, KB_V1 } from './base';
import type { GlossaryTerm, RuleRepository, RuleVersion } from '../../typesAndConstants';

export async function fetchGlossaryTerms(params?: { domain?: string; status?: string }): Promise<GlossaryTerm[]> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  const query = qs.toString();
  const url = query ? `${GLOSSARY_BASE}/terms?${query}` : `${GLOSSARY_BASE}/terms`;
  try {
    const data = await apiFetchData<GlossaryTerm[]>(url);
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

export async function fetchIntegrationMetadata(): Promise<{ simulationState?: { isSchemaDriftActive?: boolean; isSlaBreachActive?: boolean } } | null> {
  try {
    return await apiFetchData<{ simulationState?: { isSchemaDriftActive?: boolean; isSlaBreachActive?: boolean } }>('/api/v1/integration/metadata');
  } catch {
    return null;
  }
}

export async function fetchIntegrationLogs() {
  try {
    return await apiFetchData('/api/v1/integration/logs');
  } catch {
    return [];
  }
}

export async function toggleSimulationDrift(type: string, enabled: boolean) {
  try {
    return await apiFetchData('/api/v1/integration/metadata/drift', {
      method: 'POST',
      body: JSON.stringify({ type, enabled }),
    });
  } catch {
    return null;
  }
}

// ── Rule Repository ─────────────────────────────────────

export async function fetchRules(params?: { domain?: string; status?: string; keyword?: string }): Promise<RuleRepository[]> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  if (params?.keyword) qs.set('keyword', params.keyword);
  const query = qs.toString();
  const url = query ? `${RULES_BASE}?${query}` : RULES_BASE;
  try {
    const data = await apiFetchData<RuleRepository[]>(url);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function createRule(data: Partial<RuleRepository>) {
  return apiFetchData<RuleRepository>(RULES_BASE, { method: 'POST', body: JSON.stringify(data) });
}

export async function updateRule(id: string, data: Partial<RuleRepository>) {
  return apiFetchData<RuleRepository>(`${RULES_BASE}/${id}`, { method: 'PUT', body: JSON.stringify(data) });
}

export async function deleteRule(id: string) {
  return apiFetchData(`${RULES_BASE}/${id}`, { method: 'DELETE' });
}

export async function fetchRuleVersions(ruleId: string): Promise<RuleVersion[]> {
  try {
    const data = await apiFetchData<RuleVersion[]>(`${RULES_BASE}/${ruleId}/versions`);
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
