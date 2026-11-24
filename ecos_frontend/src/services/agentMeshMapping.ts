/**
 * AgentMesh 记录 ↔ AIPAgent 纯映射层（从 pages/aiworkbench/api 抽出，体积治理）。
 *
 * 本文件**不含任何 URL**：请求腿仍留在 `pages/aiworkbench/api.ts`（URL 站点计数与
 * 单源门禁口径不变），这里只承载 JSON 形状转换，便于独立复用与测试。
 *
 * metadata 是唯一真值：systemPrompt/toolWhitelist/guardrails/rag/publish/engine/routes/
 * ontologyBinding 全部经 metadata 持久化；后端 AgentRegistryEntity 派生 setter 读-改-写，
 * PUT 浅合并不丢键。缺省键一律按空/undefined 解析，禁合成。
 */
import type { AIPAgent } from '../types/aiworkbench';

export interface AgentMeshAgentRaw {
  id: string;
  name: string;
  role: string;
  description: string;
  systemPrompt: string;
  capability?: string;
  model?: string;
  maxIterations?: number;
  status?: string;
  endpoint?: string;
  metadata?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** AgentMesh 注册实体 → AIPAgent（metadata 单源解析）。 */
export function convertMeshAgentToAIP(raw: AgentMeshAgentRaw): AIPAgent {
  let meta: Record<string, any> = {};
  try {
    meta = raw.metadata ? JSON.parse(raw.metadata) : {};
  } catch { /* plain metadata */ }

  const systemPrompt: string = typeof meta.systemPrompt === 'string'
    ? meta.systemPrompt : (raw.systemPrompt || '');
  // 工具白名单：metadata.toolWhitelist 优先，capability.tools 兜底（旧 builtin 种子形态）
  let toolNames: string[] = Array.isArray(meta.toolWhitelist) ? [...meta.toolWhitelist] : [];
  if (toolNames.length === 0 && raw.capability) {
    try {
      const cap = JSON.parse(raw.capability);
      if (cap && Array.isArray(cap.tools)) toolNames = [...cap.tools];
    } catch { /* plain capability */ }
  }
  const actionIds = toolNames.filter((t: string) => t.startsWith('act_'));
  const functionIds = toolNames.filter((t: string) => !t.startsWith('act_'));
  // guardrailIds 存于 metadata.guardrails，无上报则为空数组（不合成）
  const guardrailIds: string[] = Array.isArray(meta.guardrails) ? [...meta.guardrails] : [];

  return {
    id: raw.id,
    name: raw.name || raw.id,
    avatar: 'Bot',
    role: raw.role || 'assistant',
    description: typeof meta.description === 'string' && meta.description
      ? meta.description : (raw.description || ''),
    modelId: (typeof meta.model === 'string' && meta.model) || 'deepseek-chat',
    systemPrompt,
    assignedTools: { actionIds, functionIds },
    guardrailIds,
    status: (raw.status || '').toUpperCase() === 'ACTIVE' ? 'active' : 'development',
    lastModified: raw.updatedAt || raw.createdAt || new Date().toISOString(),
    engine: typeof meta.engine === 'string' ? meta.engine : undefined,
    routes: Array.isArray(meta.routes) ? [...meta.routes] : undefined,
    toolWhitelist: toolNames,
    // 本体绑定真值 metadata.ontologyBinding；缺省/畸形按空三组解析（禁合成）
    ontologyBinding: meta.ontologyBinding && typeof meta.ontologyBinding === 'object' ? {
      objects: Array.isArray(meta.ontologyBinding.objects) ? [...meta.ontologyBinding.objects] : [],
      actions: Array.isArray(meta.ontologyBinding.actions) ? [...meta.ontologyBinding.actions] : [],
      functions: Array.isArray(meta.ontologyBinding.functions) ? [...meta.ontologyBinding.functions] : [],
    } : undefined,
    rag: meta.rag && typeof meta.rag === 'object' ? {
      docIds: Array.isArray(meta.rag.docIds) ? [...meta.rag.docIds] : [],
      categories: Array.isArray(meta.rag.categories) ? [...meta.rag.categories] : [],
      topK: typeof meta.rag.topK === 'number' ? meta.rag.topK : 5,
    } : undefined,
    publish: meta.publish && typeof meta.publish === 'object' ? {
      status: meta.publish.status === 'published' ? 'published' : 'draft',
      version: typeof meta.publish.version === 'number' ? meta.publish.version : 0,
      channel: typeof meta.publish.channel === 'string' ? meta.publish.channel : undefined,
    } : undefined,
  };
}

/** AIPAgent → AgentMesh 注册实体（metadata 单源：SOUL/工具白名单/护栏/RAG/发布/本体绑定全量落 metadata）。 */
export function toMeshAgentEntity(a: Partial<AIPAgent> & { id: string }): Record<string, unknown> {
  const toolWhitelist = a.toolWhitelist ?? [
    ...(a.assignedTools?.actionIds ?? []),
    ...(a.assignedTools?.functionIds ?? []),
  ];
  const rag = a.rag ?? { docIds: [], categories: [], topK: 5 };
  const publish = a.publish ?? { status: 'draft', version: 0, channel: 'workbench' };
  const metadata: Record<string, unknown> = {
    systemPrompt: a.systemPrompt ?? '',
    description: a.description ?? '',
    model: a.modelId ?? 'deepseek-chat',
    toolWhitelist,
    guardrails: a.guardrailIds ?? [],
    rag,
    publish,
  };
  if (a.engine) metadata.engine = a.engine;
  if (a.routes) metadata.routes = a.routes;
  // 本体绑定单源落 metadata.ontologyBinding（后端 PUT 浅合并，未传键不丢）；
  // 未提供则不写该键，避免把既有绑定覆盖成空三组。
  if (a.ontologyBinding) metadata.ontologyBinding = a.ontologyBinding;
  return {
    id: a.id,
    name: a.name ?? '',
    role: a.role ?? 'assistant',
    description: a.description ?? '',
    systemPrompt: a.systemPrompt ?? '',
    model: a.modelId ?? 'deepseek-chat',
    maxIterations: 10,
    status: a.status === 'development' ? 'INACTIVE' : 'ACTIVE',
    metadata: JSON.stringify(metadata),
    capability: JSON.stringify({ tools: toolWhitelist }),
    endpoint: '',
  };
}
