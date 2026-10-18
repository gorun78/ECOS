/**
 * scenarioBlueprintClient — Scenario Blueprint 唯一 REST 通道（补充设计 v1.0 §6 + §13）
 *
 * <p>绑定 workspace `ScenarioBlueprintController`
 * （`@RequestMapping("/api/v1/business/scenarios/{scenarioId}/blueprints")`）真实端点：
 * list / get / create / transition / transitionTo / enter-approval / approve /
 * release / revise / validate / diff。属主 workspace :18090，经 gateway 前缀只增不改。</p>
 *
 * <p>路径单源（api-base-single-source 门禁）：以 {@link API_BASE}（httpClient 导出）
 * 拼接，本文件不出现 `"/api` 字面量；组件一律经本模块导出常量/函数取路径，禁二次硬编码。</p>
 *
 * <p>无假真理 P0：只透传后端 `{code,errorCode,message,data,traceId}` 语义。
 * 409（非法迁移 / 门槛未过 / 并发冲突 / 发布阻断）按 `errorCode` 归一为
 * {@link BlueprintApiError}，绝不本地合成状态或伪装 200；网络失败归 NETWORK。</p>
 *
 * <p>传输单源（C2 收口 #143/B5-10c-2）：本文件不再自起传输，请求一律经
 * `httpClient#apiFetchEnvelope`，auth 头由该通道内部
 * 走 `services/auth.ts#getAuthToken()` 单源拼 Bearer，禁直读 localStorage['token']。</p>
 *
 * @license Apache-2.0
 */

import { API_BASE, apiFetchEnvelope, type ApiEnvelopeOutcome } from './httpClient';

// ── 静态前缀（唯一出口）─────────────────────────────

/** 场景蓝图集合根：`/api/v1/business/scenarios/{scenarioId}/blueprints`。 */
export function blueprintCollectionUrl(scenarioId: string): string {
  return `${API_BASE}/v1/business/scenarios/${encodeURIComponent(scenarioId)}/blueprints`;
}

/** 单蓝图项：`.../blueprints/{id}`。 */
export function blueprintItemUrl(scenarioId: string, blueprintId: string): string {
  return `${blueprintCollectionUrl(scenarioId)}/${encodeURIComponent(blueprintId)}`;
}

/** 蓝图子路径动作：在 `blueprintItemUrl` 末尾追加 `/suffix`（suffix 以 `/` 开头）。 */
export function blueprintActionUrl(scenarioId: string, blueprintId: string, suffix: string): string {
  return `${blueprintItemUrl(scenarioId, blueprintId)}${suffix}`;
}

// ── 状态词表（§6.3，与后端 BlueprintStatus 逐字对齐）────

/** 11 主链态 + 5 异常/回退态（后端 BlueprintStatus 枚举，非本地臆造）。 */
export type BlueprintStatus =
  | 'DRAFT'
  | 'DISCOVERING'
  | 'GAP_ANALYSIS'
  | 'BLUEPRINT_READY'
  | 'VALIDATING'
  | 'READY_FOR_APPROVAL'
  | 'APPROVED'
  | 'RELEASED'
  | 'ACTIVE'
  | 'RUNNING'
  | 'COMPLETED'
  | 'NEEDS_CLARIFICATION'
  | 'BLOCKED'
  | 'VALIDATION_FAILED'
  | 'PAUSED'
  | 'DEPRECATED';

/** 主流程有序步序（进度轨渲染基准；终态不可再迁移）。 */
export const BLUEPRINT_MAIN_FLOW: readonly BlueprintStatus[] = [
  'DRAFT',
  'DISCOVERING',
  'GAP_ANALYSIS',
  'BLUEPRINT_READY',
  'VALIDATING',
  'READY_FOR_APPROVAL',
  'APPROVED',
  'RELEASED',
  'ACTIVE',
  'RUNNING',
  'COMPLETED',
] as const;

/** 异常/回退态（醒目色 + 原因 tooltip）。 */
export const BLUEPRINT_EXCEPTION_STATES: readonly BlueprintStatus[] = [
  'NEEDS_CLARIFICATION',
  'BLOCKED',
  'VALIDATION_FAILED',
  'PAUSED',
  'DEPRECATED',
] as const;

/** 容错解析未知态（禁默认值伪装）；未知/空返回 null，调用方按非法态处理。 */
export function parseBlueprintStatus(raw: unknown): BlueprintStatus | null {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  const hit = BLUEPRINT_MAIN_FLOW.find((s) => s === trimmed)
    ?? BLUEPRINT_EXCEPTION_STATES.find((s) => s === trimmed);
  return hit ?? null;
}

/** 主链步序（异常态返回 -1）。 */
export function statusMainStep(status: BlueprintStatus | null): number {
  if (!status) return -1;
  return BLUEPRINT_MAIN_FLOW.indexOf(status);
}

/** 是否异常/回退态。 */
export function isExceptionStatus(status: BlueprintStatus | null): boolean {
  if (!status) return false;
  return BLUEPRINT_EXCEPTION_STATES.includes(status);
}

/** 是否终态（COMPLETED 不可再迁移；DEPRECATED 只读可追溯）。 */
export function isTerminalStatus(status: BlueprintStatus | null): boolean {
  return status === 'COMPLETED' || status === 'DEPRECATED';
}

/** 状态语义色档（供 UI 三重编码：色 + 图标 + 文字）。 */
export type StatusTone = 'initial' | 'progress' | 'ready' | 'approved' | 'live' | 'terminal' | 'exception' | 'unknown';

export function statusTone(status: BlueprintStatus | null): StatusTone {
  switch (status) {
    case 'DRAFT':
      return 'initial';
    case 'DISCOVERING':
    case 'GAP_ANALYSIS':
    case 'BLUEPRINT_READY':
    case 'VALIDATING':
      return 'progress';
    case 'READY_FOR_APPROVAL':
      return 'ready';
    case 'APPROVED':
      return 'approved';
    case 'RELEASED':
    case 'ACTIVE':
    case 'RUNNING':
      return 'live';
    case 'COMPLETED':
    case 'DEPRECATED':
      return 'terminal';
    case 'NEEDS_CLARIFICATION':
    case 'BLOCKED':
    case 'VALIDATION_FAILED':
    case 'PAUSED':
      return 'exception';
    default:
      return 'unknown';
  }
}

// ── 逻辑模型形状（读侧 VO / 写侧 DTO 投影，字段名对齐后端）──

export interface ScenarioBlueprintVO {
  id: string;
  scenarioId: string;
  version: number;
  status: string;
  goalStatement?: string;
  successCriteria?: string[];
  scope?: Record<string, unknown>;
  dependencies?: Array<Record<string, unknown>>;
  capabilities?: Array<Record<string, unknown>>;
  gaps?: Array<Record<string, unknown>>;
  risks?: Array<Record<string, unknown>>;
  tests?: Array<Record<string, unknown>>;
  approvalRequired?: boolean;
  approvalRef?: string | null;
  artifactDigest?: string | null;
  contextDigest?: string | null;
  traceId?: string | null;
  actor?: string | null;
  transitionReason?: string | null;
  releasedTime?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  /** 前端按钮态唯一来源：当前态允许的后继迁移目标列表。 */
  allowedTransitions?: string[];
}

/** 建稿入参（BlueprintSaveDTO 投影）。 */
export interface BlueprintSavePayload {
  scenarioId: string;
  goalStatement: string;
  successCriteria?: string[];
  scope?: Record<string, unknown>;
  dependencies?: Array<Record<string, unknown>>;
  capabilities?: Array<Record<string, unknown>>;
  risks?: Array<Record<string, unknown>>;
  tests?: Array<Record<string, unknown>>;
  approvalRequired?: boolean;
  approvalPolicyRef?: string;
  contextDigest?: string;
  note?: string;
}

/** validate 四道门槛明细（ScenarioBlueprintValidator.ValidationResult）。 */
export interface BlueprintValidateDetails {
  openBlockerGaps?: Array<Record<string, unknown>>;
  missingRequiredDeps?: Array<Record<string, unknown>>;
  unavailableCapabilities?: Array<Record<string, unknown>>;
  failedP0Tests?: Array<Record<string, unknown>>;
}

export interface BlueprintValidateResult {
  blueprintId: string;
  scenarioId: string;
  version: number;
  status: string;
  passed: boolean;
  reasons: string[];
  details: BlueprintValidateDetails;
}

export interface BlueprintDiffResult {
  fromVersion?: number;
  toVersion?: number;
  statusFrom?: string;
  statusTo?: string;
  dependencies?: Record<string, unknown>;
  capabilities?: Record<string, unknown>;
  gaps?: Record<string, unknown>;
  contentJsonChanged?: boolean;
}

// ── 错误归一 ─────────────────────────────────────────

/** 归一错误码：与后端 WorkspaceExceptionHandler + blueprint 异常族 errorCode 对齐。 */
export type BlueprintErrorCode =
  | 'ILLEGAL_TRANSITION'
  | 'TERMINAL_STATE'
  | 'DRAFT_DIRECT_ACTIVE_FORBIDDEN'
  | 'GATE_NOT_PASSED'
  | 'BLUEPRINT_STATE_CONFLICT'
  | 'BLUEPRINT_VALIDATION_FAILED'
  | 'BLUEPRINT_APPROVAL_MISSING'
  | 'BLUEPRINT_ALREADY_RELEASED'
  | 'BLUEPRINT_CONTENT_INVALID'
  | 'BLUEPRINT_NOT_FOUND'
  | 'NOT_FOUND'
  | 'BAD_REQUEST'
  | 'ENGINE_DISABLED'
  | 'SCHEMA_NOT_READY'
  | 'NETWORK'
  | 'HTTP_ERROR'
  | 'BUSINESS_ERROR'
  | 'UNKNOWN';

const ILLEGAL_TRANSITION_CODES: readonly BlueprintErrorCode[] = [
  'ILLEGAL_TRANSITION',
  'TERMINAL_STATE',
  'DRAFT_DIRECT_ACTIVE_FORBIDDEN',
  'GATE_NOT_PASSED',
];

/** 非法迁移族（前端灰化不可达步骤的对应错误）。 */
export function isIllegalTransitionCode(code: BlueprintErrorCode): boolean {
  return ILLEGAL_TRANSITION_CODES.includes(code);
}

const PUBLISH_BLOCKED_CODES: readonly BlueprintErrorCode[] = [
  'BLUEPRINT_VALIDATION_FAILED',
  'BLUEPRINT_APPROVAL_MISSING',
  'BLUEPRINT_ALREADY_RELEASED',
];

/** 发布/审批门阻断族（§6.6 门槛未过、审批缺失、重复发布）。 */
export function isPublishBlockedCode(code: BlueprintErrorCode): boolean {
  return PUBLISH_BLOCKED_CODES.includes(code);
}

/** 蓝图域统一错误；调用方按 code/status 分支渲染六态之一。 */
export class BlueprintApiError extends Error {
  readonly code: BlueprintErrorCode;
  readonly status: number;
  readonly rawErrorCode?: string;
  readonly traceId?: string;
  constructor(info: { code: BlueprintErrorCode; status: number; message: string; rawErrorCode?: string; traceId?: string }) {
    super(info.message);
    this.name = 'BlueprintApiError';
    this.code = info.code;
    this.status = info.status;
    this.rawErrorCode = info.rawErrorCode;
    this.traceId = info.traceId;
  }
}

export function isBlueprintApiError(e: unknown): e is BlueprintApiError {
  return e instanceof BlueprintApiError;
}

// 后端 errorCode → 归一 code（未识别保留原始串到 rawErrorCode，禁吞成通用码）
function normalizeCode(status: number, errorCode?: string, message?: string): BlueprintErrorCode {
  const known: readonly BlueprintErrorCode[] = [
    'ILLEGAL_TRANSITION', 'TERMINAL_STATE', 'DRAFT_DIRECT_ACTIVE_FORBIDDEN', 'GATE_NOT_PASSED',
    'BLUEPRINT_STATE_CONFLICT', 'BLUEPRINT_VALIDATION_FAILED', 'BLUEPRINT_APPROVAL_MISSING',
    'BLUEPRINT_ALREADY_RELEASED', 'BLUEPRINT_CONTENT_INVALID', 'BLUEPRINT_NOT_FOUND',
  ];
  if (errorCode && (known as readonly string[]).includes(errorCode)) return errorCode as BlueprintErrorCode;
  switch (status) {
    case 404:
      return 'NOT_FOUND';
    case 400:
      return 'BAD_REQUEST';
    case 503:
      return /schema not ready|SCHEMA_NOT_READY/i.test(message ?? '') ? 'SCHEMA_NOT_READY' : 'ENGINE_DISABLED';
    default:
      return 'UNKNOWN';
  }
}

// ── 传输核心（透传后端语义，禁合成）───────────────────
/**
 * 蓝图域唯一请求通道（C2 收口 #143/B5-10c-2：传输/auth/解包/取号委托
 * `httpClient#apiFetchEnvelope` 单源，域内只保留按 status + 后端原始 errorCode 的分类）。
 *
 * <p>不并入 `apiFetchData`：该通道不透出响应体原文与原始 `errorCode`，照搬会摧毁
 * 409 迁移冲突族的名码优先口径。D-B5-31 同批收口：域内私有取号腿只看 `X-Trace-Id`，
 * 而网关对每个响应写的是 `X-Request-Id` ⇒ 真机 traceId 恒缺，现由单源顺位表裁决。</p>
 */
async function callBlueprint(url: string, init: RequestInit = {}): Promise<unknown> {
  let out: ApiEnvelopeOutcome;
  try {
    out = await apiFetchEnvelope(url, init);
  } catch (e) {
    // 网络层：单源已广播 ecos-network-down 并抛出 NetworkError 口径，域内仍归一 NETWORK。
    throw new BlueprintApiError({
      code: 'NETWORK',
      status: 0,
      message: e instanceof Error ? e.message : String(e),
    });
  }
  const { status, ok, text, traceId, envelope } = out;
  /** 文案口径逐字保持既有行为：信封 message 优先 → 响应体原文 → `HTTP ${status}`。 */
  const message = envelope.message ?? (text || `HTTP ${status}`);
  const rawErrorCode = envelope.errorCode;

  if (status === 409) {
    // 迁移/门槛/发布/并发冲突族：优先后端 errorCode，缺失按 HTTP 归一。
    const code = normalizeCode(409, rawErrorCode, message);
    throw new BlueprintApiError({
      code: code === 'UNKNOWN' ? 'BLUEPRINT_STATE_CONFLICT' : code,
      status: 409,
      message,
      rawErrorCode,
      traceId,
    });
  }
  if (status === 404) {
    throw new BlueprintApiError({ code: normalizeCode(404, rawErrorCode, message), status: 404, message, rawErrorCode, traceId });
  }
  if (status === 400) {
    throw new BlueprintApiError({ code: normalizeCode(400, rawErrorCode, message), status: 400, message, rawErrorCode, traceId });
  }
  if (status === 503) {
    throw new BlueprintApiError({ code: normalizeCode(503, rawErrorCode, message), status: 503, message, rawErrorCode, traceId });
  }
  if (!ok) {
    throw new BlueprintApiError({ code: 'HTTP_ERROR', status, message, rawErrorCode, traceId });
  }
  // 200/201：{code, data} 信封；code!=200/0/201 视为业务错误（透传，禁伪装）
  if (typeof envelope.code === 'number' && envelope.code !== 200 && envelope.code !== 0 && envelope.code !== 201) {
    throw new BlueprintApiError({ code: 'BUSINESS_ERROR', status, message, rawErrorCode, traceId });
  }
  return out.data;
}

function asArray<T>(v: unknown): T[] {
  if (Array.isArray(v)) return v as T[];
  if (v && typeof v === 'object') {
    const o = v as Record<string, unknown>;
    if (Array.isArray(o.rows)) return o.rows as T[];
    if (Array.isArray(o.data)) return o.data as T[];
  }
  return [];
}

// ── 契约方法（逐一对应 Controller 端点）───────────────

/** GET 场景蓝图版本表（升序）。 */
export async function listBlueprints(scenarioId: string): Promise<ScenarioBlueprintVO[]> {
  const data = await callBlueprint(blueprintCollectionUrl(scenarioId));
  return asArray<ScenarioBlueprintVO>(data);
}

/** GET {id} 蓝图详情（含 allowedTransitions 按钮态）。 */
export async function getBlueprint(scenarioId: string, blueprintId: string): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintItemUrl(scenarioId, blueprintId));
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** POST 创建草稿（返回 201 + DRAFT 新行）。 */
export async function createBlueprint(scenarioId: string, payload: BlueprintSavePayload): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintCollectionUrl(scenarioId), {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** POST {id}/transition 状态迁移（五要素留痕；非法 409）。 */
export async function transitionBlueprint(
  scenarioId: string,
  blueprintId: string,
  target: string,
  reason?: string,
): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintActionUrl(scenarioId, blueprintId, '/transition'), {
    method: 'POST',
    body: JSON.stringify({ target, ...(reason ? { reason } : {}) }),
  });
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** POST {id}/enter-approval 审批门（验收门槛全过才进 READY_FOR_APPROVAL；未过 409）。 */
export async function enterApprovalGate(
  scenarioId: string,
  blueprintId: string,
  reason?: string,
): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintActionUrl(scenarioId, blueprintId, '/enter-approval'), {
    method: 'POST',
    body: JSON.stringify(reason ? { reason } : {}),
  });
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** POST {id}/approve 审批（approvalRef 缺失由后端 409 拒）。 */
export async function approveBlueprint(
  scenarioId: string,
  blueprintId: string,
  approvalRef: string,
): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintActionUrl(scenarioId, blueprintId, '/approve'), {
    method: 'POST',
    body: JSON.stringify({ approvalRef }),
  });
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** POST {id}/release 发布（门槛全过才 201；不可变）。 */
export async function releaseBlueprint(scenarioId: string, blueprintId: string): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintActionUrl(scenarioId, blueprintId, '/release'), {
    method: 'POST',
  });
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** POST {id}/revise 修订（本版本为模板生成 version+1 DRAFT）。 */
export async function reviseBlueprint(scenarioId: string, blueprintId: string): Promise<ScenarioBlueprintVO> {
  const data = await callBlueprint(blueprintActionUrl(scenarioId, blueprintId, '/revise'), {
    method: 'POST',
  });
  return (data ?? {}) as ScenarioBlueprintVO;
}

/** GET {id}/validate 发布前四道静态门槛（只读，200 + passed 明细）。 */
export async function validateBlueprint(scenarioId: string, blueprintId: string): Promise<BlueprintValidateResult> {
  const data = (await callBlueprint(blueprintActionUrl(scenarioId, blueprintId, '/validate'))) as Record<string, unknown>;
  const o = (data && typeof data === 'object' ? data : {}) as Record<string, unknown>;
  return {
    blueprintId: typeof o.blueprintId === 'string' ? o.blueprintId : '',
    scenarioId: typeof o.scenarioId === 'string' ? o.scenarioId : '',
    version: typeof o.version === 'number' ? o.version : NaN,
    status: typeof o.status === 'string' ? o.status : '',
    passed: o.passed === true,
    reasons: asArray<string>(o.reasons),
    details: (o.details && typeof o.details === 'object' ? o.details : {}) as BlueprintValidateDetails,
  };
}

/** GET {id}/diff 版本 diff（gap/依赖/content 结构化对比）。 */
export async function diffBlueprint(
  scenarioId: string,
  blueprintId: string,
  fromVersion?: number,
  toVersion?: number,
): Promise<BlueprintDiffResult> {
  const params = new URLSearchParams();
  if (typeof fromVersion === 'number') params.set('fromVersion', String(fromVersion));
  if (typeof toVersion === 'number') params.set('toVersion', String(toVersion));
  const qs = params.toString();
  const url = `${blueprintActionUrl(scenarioId, blueprintId, '/diff')}${qs ? `?${qs}` : ''}`;
  const data = await callBlueprint(url);
  return (data ?? {}) as BlueprintDiffResult;
}
