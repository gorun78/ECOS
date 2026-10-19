/**
 * govern.ts — 知识治理 3 子 Tab 契约（B.9 / F04-17 / K-55 拆分）。
 *
 * 覆盖 Tab：`govern`
 *   ├─ profiles      历史画像（生成任务、退化链、置信度、发布）  → REQ-KB-02 / F04-06
 *   ├─ assumptions   假设库（情景、状态机、审批、unverified）    → REQ-KB-03 / F04-07
 *   ├─ retrieval     RAG 调试（命中来源、CLS 掩蔽、vectorDegraded）
 *   + runEval / seeds（评测：原 QualityMetricsTable / EvalRunPanel 依赖）
 *
 * base 单源：KNOWLEDGE_BASE = /api/v1/knowledge
 *
 * 错误态语义（F04-03）：全部走 `kbFetchData` → 5xx / `ECOS-KB-*` 抛 `KbApiError`。
 *   - profiles publish 403 → ECOS-KB-062（非知识管理员）
 *   - assumptions approve/modify 409 → ECOS-KB-071（APPROVED 不可改）
 *   - assumptions expired 410 → ECOS-KB-072
 *   - profiles generate 503 → ECOS-KB-021（runtime-task 不可用）
 *   - retrieval / runEval 5xx → 显式失败态（PRD B.5）
 */
import { apiFetchData } from '../../../api';
// #143/B5-14：seed 上传腿合流到 httpClient 裸响应通道。该通道基头集合 = 「有 token 时仅
// `Authorization: Bearer …`、无 token 时空对象」，与本腿改前的 `token ? {Authorization} : {}`
// 逐键相同；且不写 `Content-Type`（multipart 的 boundary 必须由浏览器自己生成）。
import { rawAuthedUrlFetch } from '../../../services/httpClient';
import { createSseParser } from '../../../services/sseParser';
import { kbFetchData, kbSseRequest } from './kbFetch';
import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';
import type {
  EvalSeedQuery,
  EvalReport,
} from '../typesAndConstants';

// ── 画像 REQ-KB-02 / F04-06 ────────────────────────────────────────────────────

export interface KbProfileStats {
  p10?: number;
  p50?: number;
  p90?: number;
  mean?: number;
  ciLow?: number;
  ciHigh?: number;
  sampleCount?: number;
  missingRate?: number;
  statsMethod?: string;
}

export interface KbProfileRow {
  profileId: string;
  profileKey: string;
  metric?: string;
  dims?: Record<string, unknown>;
  profileVersion?: string;
  status?: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | string;
  confidence?: number;
  degradeFrom?: string | null;
  stats?: KbProfileStats;
  createdAt?: string;
  updatedAt?: string;
}

export interface KbProfileGenerateReq {
  metricCodes: string[];
  dims: Record<string, unknown>;
  windowFrom: string;
  windowTo: string;
  dryRun?: boolean;
}

export interface KbProfileGenerateResp {
  taskId: string;
  status: 'SUBMITTED' | string;
  traceId?: string;
}

export interface KbProfileResolveResp {
  profileId: string;
  profileKey: string;
  profileVersion: string;
  confidence: number;
  degradeChain: Array<{ level: string; sampleCount: number; confidence: number }>;
  stats: KbProfileStats;
  traceId?: string;
}

const PROFILES_BASE = `${KNOWLEDGE_BASE}/profiles`;

/** 提交画像生成任务（经 ITaskManagementService；503 → ECOS-KB-021 → KbApiError） */
export async function kbGenerateProfiles(req: KbProfileGenerateReq): Promise<KbProfileGenerateResp> {
  return kbFetchData<KbProfileGenerateResp>(`${PROFILES_BASE}/generate`, {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function kbFetchProfiles(params?: {
  metric?: string;
  dims?: string;
  status?: string;
  page?: number;
  size?: number;
}): Promise<KbProfileRow[]> {
  const qs = new URLSearchParams();
  if (params?.metric) qs.set('metric', params.metric);
  if (params?.dims) qs.set('dims', params.dims);
  if (params?.status) qs.set('status', params.status);
  qs.set('page', String(params?.page ?? 1));
  qs.set('size', String(Math.min(Math.max(1, params?.size ?? 20), 500)));
  const data = await kbFetchData<KbProfileRow[] | { list?: KbProfileRow[]; items?: KbProfileRow[] }>(
    `${PROFILES_BASE}?${qs.toString()}`,
  );
  if (Array.isArray(data)) return data;
  return data?.list || data?.items || [];
}

/** 发布画像 —— 403/ECOS-KB-062（非知识管理员）→ KbApiError */
export async function kbPublishProfile(id: string) {
  return kbFetchData(`${PROFILES_BASE}/${encodeURIComponent(id)}/publish`, { method: 'POST' });
}

/** 画像解析（DEV/查询） —— 退化链 + 置信度 + 统计 */
export async function kbResolveProfile(params: {
  metric: string;
  dims?: string;
  atVersion?: string;
}): Promise<KbProfileResolveResp> {
  const qs = new URLSearchParams();
  qs.set('metric', params.metric);
  if (params.dims) qs.set('dims', params.dims);
  if (params.atVersion) qs.set('atVersion', params.atVersion);
  return kbFetchData<KbProfileResolveResp>(`${PROFILES_BASE}/resolve?${qs.toString()}`);
}

// ── 假设库 REQ-KB-03 / F04-07 ──────────────────────────────────────────────────

export interface KbAssumptionRow {
  id: string;
  assumptionKey: string;
  scenarioType?: string;
  metric?: string;
  dimScope?: string;
  value?: number | string;
  valueSemantic?: string;
  baseAssumptionId?: string | null;
  reason?: string;
  evidenceRef?: string | null;
  expireAt?: string | null;
  assumptionVersion?: string;
  unverified?: boolean;
  status?: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'ACTIVE' | 'EXPIRED' | 'REJECTED' | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface KbAssumptionCreateReq {
  scenarioType: string;
  metric: string;
  dimScope?: string;
  value: number | string;
  valueSemantic?: string;
  baseAssumptionId?: string | null;
  reason?: string;
  evidenceRef?: string;
  expireAt?: string;
}

const ASSUMPTIONS_BASE = `${KNOWLEDGE_BASE}/assumptions`;

/** 建假设 DRAFT —— 201 → {id, assumptionVersion, status:"DRAFT"} */
export async function kbCreateAssumption(req: KbAssumptionCreateReq): Promise<KbAssumptionRow> {
  return kbFetchData<KbAssumptionRow>(ASSUMPTIONS_BASE, {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function kbFetchAssumptions(params?: {
  scenarioType?: string;
  metric?: string;
  status?: string;
}): Promise<KbAssumptionRow[]> {
  const qs = new URLSearchParams();
  if (params?.scenarioType) qs.set('scenarioType', params.scenarioType);
  if (params?.metric) qs.set('metric', params.metric);
  if (params?.status) qs.set('status', params.status);
  const q = qs.toString();
  const data = await kbFetchData<KbAssumptionRow[] | { list?: KbAssumptionRow[]; items?: KbAssumptionRow[] }>(
    q ? `${ASSUMPTIONS_BASE}?${q}` : ASSUMPTIONS_BASE,
  );
  if (Array.isArray(data)) return data;
  return data?.list || data?.items || [];
}

/** 提交假设 —— 409/ECOS-KB-071 → KbApiError */
export async function kbSubmitAssumption(id: string) {
  return kbFetchData(`${ASSUMPTIONS_BASE}/${encodeURIComponent(id)}/submit`, { method: 'POST' });
}

/** 审批假设 —— 409/ECOS-KB-071 → KbApiError（APPROVED 不可再改） */
export async function kbApproveAssumption(id: string) {
  return kbFetchData(`${ASSUMPTIONS_BASE}/${encodeURIComponent(id)}/approve`, { method: 'POST' });
}

export interface KbAssumptionResolveItem {
  id: string;
  value: number | string;
  valueSemantic?: string;
  baseAssumptionId?: string | null;
  assumptionVersion?: string;
  unverified: boolean;
  expireAt?: string | null;
}

export async function kbResolveAssumptions(params: {
  scenarioType: string;
  metric: string;
  dims?: string;
  period?: string;
}): Promise<{ items: KbAssumptionResolveItem[] }> {
  const qs = new URLSearchParams();
  qs.set('scenarioType', params.scenarioType);
  qs.set('metric', params.metric);
  if (params.dims) qs.set('dims', params.dims);
  if (params.period) qs.set('period', params.period);
  return kbFetchData<{ items: KbAssumptionResolveItem[] }>(`${ASSUMPTIONS_BASE}/resolve?${qs.toString()}`);
}

// ── Eval（评测） —— 原 QualityMetricsTable / EvalRunPanel 数据源 ────────────────

export async function fetchEvalSeeds(): Promise<EvalSeedQuery[]> {
  try {
    const data = await apiFetchData<EvalSeedQuery[]>(`${KNOWLEDGE_BASE}/eval/seeds`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function kbFetchEvalSeeds(): Promise<EvalSeedQuery[]> {
  const data = await kbFetchData<EvalSeedQuery[]>(`${KNOWLEDGE_BASE}/eval/seeds`);
  return Array.isArray(data) ? data : [];
}

export async function uploadEvalSeed(file: File): Promise<{ name: string; count: number }> {
  const fd = new FormData();
  fd.append('file', file);
  const res = await rawAuthedUrlFetch(`${KNOWLEDGE_BASE}/eval/seeds/upload`, {
    method: 'POST',
    body: fd,
  });
  if (!res.ok) throw new Error(`seed upload failed ${res.status}`);
  const json = await res.json();
  return json?.data || json;
}

export async function runEval(seedSetName: string): Promise<EvalReport> {
  try {
    const data = await apiFetchData<EvalReport>(`${KNOWLEDGE_BASE}/eval/run`, {
      method: 'POST',
      body: JSON.stringify({ seedSetName }),
    });
    return data || { reportId: '', seedSetName, printedAt: new Date().toISOString(), recallAt5: 0, mrrAt5: 0, ndcgAt5: 0 };
  } catch (e: unknown) {
    // Degraded mode: 后端不可用时本地降级标记
    console.info('runEval backend unavailable — degraded', (e as { message?: string } | undefined)?.message);
    return {
      reportId: `local-${Date.now()}`,
      seedSetName,
      printedAt: new Date().toISOString(),
      recallAt5: 0,
      mrrAt5: 0,
      ndcgAt5: 0,
      degraded: true,
    };
  }
}

/** KbApiError 版 runEval —— govern/retrieval + EvalRunPanel 错误态分治 */
export async function kbRunEval(seedSetName: string): Promise<EvalReport> {
  return kbFetchData<EvalReport>(`${KNOWLEDGE_BASE}/eval/run`, {
    method: 'POST',
    body: JSON.stringify({ seedSetName }),
  });
}

// ── RAG SSE（govern/retrieval 流式） — K-55 auth 头单源 ────────────────────────

/**
 * SSE-capable RAG query —— SSE 走 `kbSseRequest`（auth via auth.ts）；
 * SSE 不可用时 fallback 到 `runRAGQuery`（silent 版），整体上保留既有语义。
 *
 * 线路级切分单源在 `services/sseParser`（D-B5-11 收口：此处此前按 `chunk.split('\n')`
 * 逐行正则匹配 `^data:`，一条 token 被分包切成两段时前半截当完整 token 上屏、后半截直接丢失）。
 * 零 data 帧视为「本通道不可用」而不是「成功但无内容」：后端 `GET /rag?stream=true` 端点
 * 实不存在（登记 D-B5-12），应答是普通 JSON 体 ⇒ 必须显式转兜底通道，禁静默空回答。
 */
export async function runRAGQuerySSE(query: string, onToken: (token: string) => void): Promise<{ answerGenerated: boolean }> {
  try {
    const url = `${KNOWLEDGE_BASE}/rag?query=${encodeURIComponent(query)}&stream=true`;
    const res = await kbSseRequest(url);
    if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);
    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    const parser = createSseParser();
    let tokens = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      for (const evt of parser.push(decoder.decode(value, { stream: true }))) {
        onToken(evt.data);
        tokens += 1;
      }
    }
    parser.end();
    if (tokens === 0) throw new Error('SSE 通道未产出任何 data 帧');
    return { answerGenerated: true };
  } catch (e: unknown) {
    // SSE 不可用 → fallback 到 POST（preserve 既有 fallback 语义）
    console.info('SSE fallback to POST:', (e as { message?: string } | undefined)?.message);
    const { kbRunRAGQuery } = await import('./wiki');
    const result = await kbRunRAGQuery({ query });
    onToken(result.answer || '');
    return { answerGenerated: Boolean(result.answerGenerated) };
  }
}
