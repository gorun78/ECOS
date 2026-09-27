/**
 * api.ts — 场景沙盘 (Scenario Sandbox) API 层
 *
 * 调 §2.7 后端 10 个端点（v2.0 契约）：
 *   6 类资源可用：GET /api/v1/workspace/scenarios/available/{cat}
 *   布局：GET / POST /api/v1/workspace/scenarios/{id}/sandbox/layout
 *   心智 variants：GET / POST / PATCH / DELETE /api/v1/workspace/scenarios/{id}/minds[/{mindId}]
 *   兼容 base：GET / POST / DELETE /api/v1/workspace/scenarios/{id}/minds/base
 *   认知 4 件套（P3b T25 后端，本期 stub）：POST /api/v1/workspace/scenarios/{id}/cognitive/{ep}
 *
 * 复用 src/api.ts 的 apiFetch<T>（自动 /api 前缀 + Bearer + 401/403/network 处理 + 返回 full JSON body）。
 *
 * @license Apache-2.0
 */

import { apiFetch } from "../../api";
import type {
  SdLayout,
  SdLayoutSaveRequest,
  SdMindBase,
  SdMindVariant,
  SdResourceItem,
  CognitiveEp,
  ResourceCategory,
} from "./types";

// ── 资源可用 6 类 ────────────────────────────────────────

/** 资源类别可用端点分类（§2.7 后端 rule-based 路由到各引擎） */
export type SandboxAvailableCategory =
  | "datasets"
  | "objects"
  | "knowledge"
  | "agents"
  | "security"
  | "interfaces";

/** 资源 cat（UI 选用后保存时的原始 category 枚举） */
export const CATEGORY_TO_ENDPOINT: Record<ResourceCategory, SandboxAvailableCategory> = {
  DATASOURCE: "datasets",
  ONTOLOGY_ENTITY: "objects",
  KNOWLEDGE_ARTICLE: "knowledge",
  AGENT_PROFILE: "agents",
  SECURITY_POLICY: "security",
  INTERFACE_REF: "interfaces",
};

/**
 * 拉某类资源可用列表（catalog 级端点，后端 §2.7 锚定引擎摘要）
 * GET /api/v1/workspace/scenarios/available/{cat}?{params}
 */
export function fetchSandboxAvailable(
  cat: SandboxAvailableCategory,
  params?: Record<string, string>
): Promise<SdResourceItem[]> {
  const qs = params
    ? `?${Object.entries(params)
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join("&")}`
    : "";
  // apiFetch 不提取 data 字段，返回 body；这里假定 { items: SdResourceItem[] }
  return apiFetch<{ items: SdResourceItem[] }>(`/v1/workspace/scenarios/available/${cat}${qs}`).then(
    (body) => (Array.isArray((body as { items?: SdResourceItem[] }).items) ? (body as { items: SdResourceItem[] }).items : [])
  );
}

// ── 布局（GET / POST）────────────────────────────────────

/**
 * 拉场景沙盘布局
 * GET /api/v1/workspace/scenarios/{id}/sandbox/layout
 */
export function fetchSandboxLayout(scenarioId: string): Promise<SdLayout | null> {
  return apiFetch<{ layout?: SdLayout | null; version?: number; layoutVersion?: number }>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/sandbox/layout`
  ).then((body) => {
    const layouts = body as { layout?: unknown } as { layout?: SdLayout };
    return layouts.layout ?? null;
  });
}

/**
 * 保存场景沙盘布局（乐观锁 expectedVersion）
 * POST /api/v1/workspace/scenarios/{id}/sandbox/layout
 * - 200：保存成功，body.layoutVersion 为新版本号
 * - 409：版本冲突，body.error 含当前版本（这里抛 SaveConflict）
 */
export async function saveSandboxLayout(
  scenarioId: string,
  body: SdLayoutSaveRequest
): Promise<{ layoutVersion: number }> {
  const res = await apiFetch<{ layoutVersion?: number; version?: number; error?: string }>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/sandbox/layout`,
    { method: "POST", body: JSON.stringify(body) }
  );
  const version = res.layoutVersion ?? res.version ?? 0;
  return { layoutVersion: version };
}

// ── 心智 variants（4 CRUD）───────────────────────────────

/**
 * 列场景的心智 variants
 * GET /api/v1/workspace/scenarios/{id}/minds
 */
export function listScenarioMinds(scenarioId: string): Promise<SdMindVariant[]> {
  return apiFetch<{ items?: SdMindVariant[] }>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds`
  ).then(
    (body) =>
      Array.isArray((body as { items?: SdMindVariant[] }).items)
        ? (body as { items: SdMindVariant[] }).items
        : []
  );
}

/**
 * 保存（新增或全量替换）某个心智 variant
 * POST /api/v1/workspace/scenarios/{id}/minds
 */
export function saveScenarioMind(
  scenarioId: string,
  body: SdMindVariant
): Promise<SdMindVariant> {
  return apiFetch<SdMindVariant>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds`,
    { method: "POST", body: JSON.stringify(body) }
  );
}

/**
 * 局部更新（激活切换/label 改）
 * PATCH /api/v1/workspace/scenarios/{id}/minds/{mindId}
 */
export function patchScenarioMind(
  scenarioId: string,
  mindId: string,
  partial: Partial<SdMindVariant>
): Promise<SdMindVariant> {
  return apiFetch<SdMindVariant>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds/${encodeURIComponent(mindId)}`,
    { method: "PATCH", body: JSON.stringify(partial) }
  );
}

/**
 * 删除心智 variant
 * DELETE /api/v1/workspace/scenarios/{id}/minds/{mindId}
 */
export function deleteScenarioMind(
  scenarioId: string,
  mindId: string
): Promise<{ deleted?: boolean }> {
  return apiFetch<{ deleted?: boolean }>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds/${encodeURIComponent(mindId)}`,
    { method: "DELETE" }
  );
}

// ── 兼容 mind base ───────────────────────────────────────

/** 拉场景心智 base */
export function fetchScenarioMindBase(
  scenarioId: string
): Promise<SdMindBase | null> {
  return apiFetch<{ base?: SdMindBase | null }>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds/base`
  ).then((body) => (body as { base?: SdMindBase }).base ?? null);
}

/** 保存场景心智 base */
export function saveScenarioMindBase(
  scenarioId: string,
  body: SdMindBase
): Promise<SdMindBase> {
  return apiFetch<SdMindBase>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds/base`,
    { method: "POST", body: JSON.stringify(body) }
  );
}

/** 删除场景心智 base */
export function deleteScenarioMindBase(
  scenarioId: string
): Promise<{ deleted?: boolean }> {
  return apiFetch<{ deleted?: boolean }>(
    `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/minds/base`,
    { method: "DELETE" }
  );
}

// ── 认知四件套（P3b T25 后端已落地，PMO-66 B4 前端去 stub 切真实 /runs） ────────

/**
 * 触发场景认知演练 — 经 workspace :18090 的 `/runs` 端点真调>DcchengClient>
 * cognitive :18084 四件套（DIAGNOSE/FORECAST/SIMULATE/SAFEGUARD）。
 *
 * <p>§0.6.4.3：沙盘内认知演练必真接引擎，禁止 stub/hash 当结论落 UI。
 * 引擎不可达 → 后端按 §0.6.4.3 graceful 降级返回 `degraded:true` + error payload
 * （不静默回 stub-hash），由 UI 层按 IsInfoCard 的 outline 分支呈现。</p>
 *
 * @returns `ok` 标志：true = 后端实际调用并写库成功；false = 后端不可达返回降级
 */
export async function runInsight(
  scenarioId: string,
  ep: CognitiveEp,
  mindId?: string
): Promise<{ summary: string; payloadHash: string; ok: boolean; degraded?: boolean; runId?: string }> {
  // runTypes 映射：沙盘「四件套」按钮 → run 的 runTypes 枚举
  const runTypesMap: Record<CognitiveEp, string[]> = {
    diagnose: ["DIAGNOSE"],
    forecast: ["FORECAST"],
    simulate: ["SIMULATE"],
    policy: ["STRATEGY"],
  };
  try {
    const res = await apiFetch<{ onResponse: unknown } & { degraded?: boolean; runId?: string; status?: string }>(
      `/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/runs`,
      {
        method: "POST",
        body: JSON.stringify({
          runTypes: runTypesMap[ep] ?? ["DIAGNOSE"],
          metric: "安全指标",
          ...(mindId ? { mindId } : {}),
        }),
      }
    );
    const data = (res as unknown) as {
      runId?: string;
      status?: string;
      degraded?: boolean;
      diagnosis?: { rootCause?: string; suggestions?: string[]; confidence?: number };
    };
    const summary = data?.diagnosis?.rootCause
      ?? (data?.diagnosis?.confidence !== undefined ? `confidence=${data.diagnosis.confidence}` : "")
      ?? "";
    return {
      summary,
      // 真实 runId 取代 stub hash
      payloadHash: data?.runId ?? `run-unknown-${Date.now().toString(36)}`,
      ok: true,
      degraded: Boolean(data?.degraded),
      runId: data?.runId,
    };
  } catch (e) {
    // 引擎 / 后端不可达：返回 fallback，UI 走 outline 分支（§0.6.4.3 不静默回 stub-hash）
    void mindId;
    return {
      summary: "",
      payloadHash: `err-${ep}-${(e as Error).message ?? "netfail"}`,
      ok: false,
      degraded: true,
    };
  }
}
