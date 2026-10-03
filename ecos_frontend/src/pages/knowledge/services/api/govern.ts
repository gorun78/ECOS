// B.9 / K-55 — Tab「govern 知识治理(RAG 调试 + 引擎配置)」API 切片。
// 内容自原 knowledgeApi.ts 逐字搬移；原文件退化为 barrel，语义与签名保持不变（API 只增不改）。
import { apiFetchData } from '../../../../api';
import { KNOWLEDGE_BASE, KB_V1 } from './base';
import type {
  RagRequest,
  RagResult,
  EngineConfigScope,
  EngineConfig,
} from '../../typesAndConstants';

// SSE-capable RAG query — falls back to POST /rag when backend hasn't wired SSE
export async function runRAGQuerySSE(query: string, onToken: (token: string) => void): Promise<{ answerGenerated: boolean }> {
  // Try: GET /api/v1/knowledge/rag?query=...&stream=true
  const token = localStorage.getItem('token') || '';
  try {
    const url = `${KB_V1}/rag?query=${encodeURIComponent(query)}&stream=true`;
    const res = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);
    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let answerGenerated = true;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      // SSE-format `data: xxx` lines; strip newline and parse
      const lines = chunk.split('\n');
      for (const line of lines) {
        const m = line.match(/^data:\s*(.+)$/);
        if (m) onToken(m[1].trim());
      }
    }
    return { answerGenerated };
  } catch (e: unknown) {
    // SSE not available — fall back to POST; report answer not generated
    console.info('SSE fallback to POST:', (e as { message?: string } | undefined)?.message);
    const result = await runRAGQuery({ query });
    onToken(result.answer || '');
    return { answerGenerated: Boolean(result.answerGenerated) };
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

// ── PMO-54 — Engine config ─────────────────────────────────────────────────────

export async function fetchEngineConfig(scope: EngineConfigScope): Promise<{ config: EngineConfig; version: number; updatedAt: string }> {
  try {
    const data = await apiFetchData<EngineConfig>(`${KB_V1}/engine-config?scope=${encodeURIComponent(scope)}`);
    return { config: data || {}, version: 1, updatedAt: new Date().toISOString() };
  } catch {
    // fallback: cognitive group + local knowledge_engine group
    try {
      const data = await apiFetchData<EngineConfig>('/api/v1/cognitive/config');
      const result: EngineConfig = {};
      for (const [k, v] of Object.entries(data || {})) result[`knowledge.${scope}.${k}`] = String(v);
      return { config: result, version: 1, updatedAt: new Date().toISOString() };
    } catch {
      return { config: {}, version: 1, updatedAt: new Date().toISOString() };
    }
  }
}

export async function saveEngineConfig(scope: EngineConfigScope, cfg: Record<string, unknown>): Promise<{ config: EngineConfig; version: number; updatedAt: string }> {
  // Try PMO-54-specific endpoint; fall back to sys_config group=knowledge_engine
  const asyncRp = apiFetchData<{ config: EngineConfig; version: number; updatedAt: string } | EngineConfig | null>(`${KB_V1}/engine-config?scope=${encodeURIComponent(scope)}`, {
    method: 'PUT',
    body: JSON.stringify(cfg),
  });

  const settle = async (): Promise<{ config: EngineConfig; version: number; updatedAt: string }> => {
    const primary = await asyncRp.catch(function (): { config: EngineConfig; version: number; updatedAt: string } | EngineConfig | null { return null; });
    if (primary && typeof primary === 'object' && 'config' in primary) return primary as { config: EngineConfig; version: number; updatedAt: string };
    if (primary && typeof primary === 'object' && 'config' in (primary as Record<string, unknown>)) {
      const p = primary as Record<string, unknown>;
      return { config: (p.config as EngineConfig) || (cfg as EngineConfig), version: Number(p.version ?? 1), updatedAt: String(p.updatedAt ?? new Date().toISOString()) };
    }
    if (primary) return { config: (primary as EngineConfig) || (cfg as EngineConfig), version: 1, updatedAt: new Date().toISOString() };
    // 回退到 cognitive/config
    const updates = Object.entries(cfg).map(([config_key, config_value]) => ({ config_key: `knowledge_engine.${scope}.${config_key}`, config_value }));
    const fbResp = await apiFetchData<Record<string, unknown> | null>('/api/v1/cognitive/config', { method: 'PUT', body: JSON.stringify(updates) });
    return { config: (fbResp ? fbResp as EngineConfig : cfg) as EngineConfig, version: 1, updatedAt: new Date().toISOString() };
  };
  try {
    return await settle();
  } catch {
    return { config: cfg as EngineConfig, version: 1, updatedAt: new Date().toISOString() };
  }
}
