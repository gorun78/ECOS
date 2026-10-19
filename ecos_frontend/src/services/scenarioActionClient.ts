/**
 * scenarioActionClient — 场景经营动作/决策闭环唯一 REST 通道（H-8 第一批 · P08 动作回链）
 *
 * <p>绑定 workspace `ScenarioActionController`
 * （`@RequestMapping("/api/v1/workspace")`）真实端点：
 * `POST /scenarios/{id}/actions`、`GET /scenarios/{id}/actions`、
 * `GET /scenarios/{id}/decisions`、`POST /actions/{actionId}/approve`。
 * 属主 workspace :18090，经 gateway 前缀只增不改（VersionPrefixRewriteFilter 对
 * `/api/v1/workspace/` 为 KEEP，无需新增代理条目）。</p>
 *
 * <p>路径单源（api-base-single-source 门禁）：以 {@link API_BASE}（httpClient 导出）
 * 拼接，本文件不出现 `"/api` 字面量；组件一律经本模块导出常量/函数取路径。</p>
 *
 * <p>传输单源（C2′ 私设传输门禁，#143/B5-15）：唯一一条传输腿改经
 * {@link rawAuthedJsonUrlFetch}——Bearer 拼接与默认 `Content-Type: application/json`
 * 及调用方头逐键合并都在该单源内完成（身份取 `auth.ts#getAuthToken()`，禁直读
 * `localStorage['token']`）。选这条而不是 `rawAuthedUrlFetch`/`apiFetchEnvelope`：
 * 本域自持 NETWORK/400 逐字段/503/409 分态与 `X-Trace-Id` 取号顺位，
 * 前者的 PMO-43 归一会把网络失败折叠成全局掉线、后者会加 401 清会话副作用，
 * 两者都超出「传输合流」的零语义变化授权面（裁决依据见该通道文档注释）。</p>
 *
 * <p>审批人反伪造（卷07 F07-10 / 详设-09 §五）：后端审批人主体一律取 JWT subject，
 * **绝不从请求体取**。前端表单只把登录主体作为「审批人」只读展示以完成 5 必填的即时提示，
 * 提交载荷必须不含 approver 字段。</p>
 *
 * <p>无假真理（架构铁律 :14）：只透传后端 `{code,errorCode,message,data,traceId}` 语义。
 * 400 `ACTION_FIELDS_MISSING` 的 `data.missingFields[]` 原样回给表单逐字段渲染；
 * 503 `COGNITIVE_UNAVAILABLE` 归一为引擎不可用；绝不本地合成 actionId 或伪装 200。</p>
 *
 * <p>金额精度：`expected_impact NUMERIC(18,2)`，载荷以字符串透传，禁先转 number 再序列化。</p>
 *
 * @license Apache-2.0
 */

import { API_BASE, rawAuthedJsonUrlFetch } from './httpClient';

// ── 路径单源（唯一出口）───────────────────────────────

/** workspace 域基址（派生自 API_BASE，不重复定义前缀）。 */
const WORKSPACE_BASE = `${API_BASE}/v1/workspace`;

/** 场景动作集合：`/api/v1/workspace/scenarios/{id}/actions`。 */
export function scenarioActionsUrl(scenarioId: string): string {
  return `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/actions`;
}

/** 场景决策流：`/api/v1/workspace/scenarios/{id}/decisions`。 */
export function scenarioDecisionsUrl(scenarioId: string): string {
  return `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/decisions`;
}

/** 动作审批：`/api/v1/workspace/actions/{actionId}/approve`。 */
export function actionApproveUrl(actionId: string): string {
  return `${WORKSPACE_BASE}/actions/${encodeURIComponent(actionId)}/approve`;
}

/** 场景清单：`/api/v1/workspace/scenarios`（GET 全量列表，决策面板边界内只投影 id/name/businessGoal/status）。 */
export function workspaceScenariosUrl(): string {
  return `${WORKSPACE_BASE}/scenarios`;
}

// ── 五必填单源（与 ActionProposalValidator 逐字对齐）────

/**
 * 后端必填字段 key（`ActionProposalValidator.validate` 的 missing 列表来源）：
 * owner / dueDate / approver / expectedImpact / controlMetric。
 * `approver` 由后端从 JWT 主体判定，前端只校验「登录主体存在」，不进请求体。
 */
export const ACTION_REQUIRED_FIELDS = [
  'owner',
  'dueDate',
  'approver',
  'expectedImpact',
  'controlMetric',
] as const;

export type ActionRequiredField = (typeof ACTION_REQUIRED_FIELDS)[number];

/** 表单草稿（UI 态；金额/日期保持字符串）。 */
export interface ActionDraft {
  ownerUserId: string;
  dueDate: string;
  expectedImpact: string;
  controlMetric: string;
  forecastRunId: string;
  description: string;
}

/** 提交载荷（ActionProposal 投影；无 approver 字段）。 */
export interface ActionProposalPayload {
  ownerUserId: string;
  dueDate: string;
  expectedImpact: string;
  controlMetric: string;
  forecastRunId?: string;
  description?: string;
}

/**
 * 五必填即时校验（镜像后端键名，缺啥报啥，不做兜底）。
 * @param approverSubject 登录主体（审批人只读来源；空 ⇒ 后端必判 approver 缺失）。
 */
export function validateActionDraft(
  draft: ActionDraft,
  approverSubject: string,
): ActionRequiredField[] {
  const missing: ActionRequiredField[] = [];
  if (!draft.ownerUserId.trim()) missing.push('owner');
  if (!draft.dueDate.trim()) missing.push('dueDate');
  if (!approverSubject.trim()) missing.push('approver');
  if (!draft.expectedImpact.trim()) missing.push('expectedImpact');
  if (!draft.controlMetric.trim()) missing.push('controlMetric');
  return missing;
}

/** 空草稿（表单初值；金额留空而非 0，禁 0 填充假值）。 */
export function emptyActionDraft(forecastRunId = ''): ActionDraft {
  return {
    ownerUserId: '',
    dueDate: '',
    expectedImpact: '',
    controlMetric: '',
    forecastRunId,
    description: '',
  };
}

/** 草稿 → 载荷：裁剪空白，绝不携带 approver（后端取 JWT 主体）。 */
export function toActionPayload(draft: ActionDraft): ActionProposalPayload {
  const payload: ActionProposalPayload = {
    ownerUserId: draft.ownerUserId.trim(),
    dueDate: draft.dueDate.trim(),
    expectedImpact: draft.expectedImpact.trim(),
    controlMetric: draft.controlMetric.trim(),
  };
  const runId = draft.forecastRunId.trim();
  if (runId) payload.forecastRunId = runId;
  const desc = draft.description.trim();
  if (desc) payload.description = desc;
  return payload;
}

// ── 读侧 VO（后端 Map<String,Object> 投影，未知字段留 undefined）──

export interface ScenarioActionVO {
  actionId?: string;
  scenarioId?: string;
  ownerUserId?: string;
  dueDate?: string;
  expectedImpact?: string | number | null;
  controlMetric?: string;
  forecastRunId?: string;
  description?: string;
  status?: string;
  approver?: string;
  approveTime?: string;
  traceId?: string;
  [key: string]: unknown;
}

export interface ScenarioDecisionVO {
  decisionId?: string;
  actionId?: string;
  scenarioId?: string;
  forecastRunId?: string;
  decisionType?: string;
  status?: string;
  rationale?: string;
  actor?: string;
  createTime?: string;
  traceId?: string;
  [key: string]: unknown;
}

/** 写侧结果（DecisionGateway.proposeAction 透传，不假设字段齐备）。 */
export type ScenarioActionResult = Record<string, unknown>;

/** 从结果里取 actionId（缺失返回 null，供回链判定，不伪造）。 */
export function pickActionId(result: ScenarioActionResult | null | undefined): string | null {
  const v = result?.actionId;
  return typeof v === 'string' && v.trim() ? v : null;
}

// ── 错误归一 ─────────────────────────────────────────

export type ActionErrorCode =
  | 'ACTION_FIELDS_MISSING'
  | 'BAD_REQUEST'
  | 'NOT_FOUND'
  | 'FORBIDDEN'
  | 'COGNITIVE_UNAVAILABLE'
  | 'BASELINE_RUN_MODE_MISMATCH'
  | 'NETWORK'
  | 'HTTP_ERROR'
  | 'BUSINESS_ERROR'
  | 'UNKNOWN';

/** 动作域统一错误；调用方按 code/status/missingFields 分支渲染。 */
export class ScenarioActionError extends Error {
  readonly code: ActionErrorCode;
  readonly status: number;
  readonly rawErrorCode?: string;
  readonly traceId?: string;
  readonly missingFields: ActionRequiredField[];
  constructor(info: {
    code: ActionErrorCode;
    status: number;
    message: string;
    rawErrorCode?: string;
    traceId?: string;
    missingFields?: string[];
  }) {
    super(info.message);
    this.name = 'ScenarioActionError';
    this.code = info.code;
    this.status = info.status;
    this.rawErrorCode = info.rawErrorCode;
    this.traceId = info.traceId;
    this.missingFields = (info.missingFields ?? []).filter((f): f is ActionRequiredField =>
      (ACTION_REQUIRED_FIELDS as readonly string[]).includes(f),
    );
  }
}

export function isScenarioActionError(e: unknown): e is ScenarioActionError {
  return e instanceof ScenarioActionError;
}

function normalizeCode(status: number, errorCode?: string): ActionErrorCode {
  if (errorCode === 'ACTION_FIELDS_MISSING' || errorCode === 'BAD_REQUEST') {
    return errorCode;
  }
  if (errorCode === 'COGNITIVE_UNAVAILABLE') return 'COGNITIVE_UNAVAILABLE';
  if (errorCode && /RUN_MODE|BASELINE/i.test(errorCode)) return 'BASELINE_RUN_MODE_MISMATCH';
  switch (status) {
    case 400:
      return 'BAD_REQUEST';
    case 403:
      return 'FORBIDDEN';
    case 404:
      return 'NOT_FOUND';
    case 503:
      return 'COGNITIVE_UNAVAILABLE';
    default:
      return 'UNKNOWN';
  }
}

// ── 传输核心（透传后端语义，禁合成）───────────────────

interface Envelope {
  ok: boolean;
  status: number;
  body: Record<string, unknown>;
  text: string;
  traceId?: string;
}

async function rawCall(url: string, init: RequestInit = {}): Promise<Envelope> {
  let res: Response;
  try {
    res = await rawAuthedJsonUrlFetch(url, init);
  } catch (e) {
    throw new ScenarioActionError({
      code: 'NETWORK',
      status: 0,
      message: e instanceof Error ? e.message : String(e),
    });
  }
  const text = await res.text().catch(() => '');
  let body: Record<string, unknown> = {};
  if (text) {
    try {
      const parsed: unknown = JSON.parse(text);
      if (parsed && typeof parsed === 'object') body = parsed as Record<string, unknown>;
    } catch {
      /* 非 JSON：保持空对象，由 status 归一 */
    }
  }
  const headerTrace = res.headers.get('X-Trace-Id');
  const traceId = headerTrace ?? (typeof body.traceId === 'string' ? body.traceId : undefined);
  return { ok: res.ok, status: res.status, body, text, traceId };
}

/** 信封 → 错误（失败路径唯一出口；missingFields 从 data 结构化取）。 */
function toError(env: Envelope): ScenarioActionError {
  const errorCode = typeof env.body.errorCode === 'string' ? env.body.errorCode : undefined;
  const message =
    typeof env.body.message === 'string' && env.body.message
      ? env.body.message
      : env.text || `HTTP ${env.status}`;
  const data = env.body.data;
  const missing =
    data && typeof data === 'object'
      ? (data as Record<string, unknown>).missingFields
      : undefined;
  return new ScenarioActionError({
    code: normalizeCode(env.status, errorCode),
    status: env.status,
    message,
    rawErrorCode: errorCode,
    traceId: env.traceId,
    missingFields: Array.isArray(missing) ? missing.map((m) => String(m)) : [],
  });
}

function unwrap(env: Envelope): unknown {
  if (!env.ok) throw toError(env);
  const code = env.body.code;
  if (typeof code === 'number' && code !== 200 && code !== 0 && code !== 201) {
    throw toError(env);
  }
  return env.body.data !== undefined ? env.body.data : env.body;
}

/** RLS 读侧信封：`{ items: [...] }`（ScenarioReadGuard 列级裁决后）。 */
function asItems<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object') {
    const o = data as Record<string, unknown>;
    if (Array.isArray(o.items)) return o.items as T[];
    if (Array.isArray(o.rows)) return o.rows as T[];
  }
  return [];
}

// ── 契约方法（逐一对应 Controller 端点）───────────────

/** POST 创建动作提案（五必填 + forecastRunId 回链；400 逐字段 / 503 引擎不可用）。 */
export async function createScenarioAction(
  scenarioId: string,
  payload: ActionProposalPayload,
): Promise<ScenarioActionResult> {
  const env = await rawCall(scenarioActionsUrl(scenarioId), {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = unwrap(env);
  return (data && typeof data === 'object' ? data : {}) as ScenarioActionResult;
}

/** GET 动作列表（经 security-engine RLS/CLS，默认 DENY ⇒ 空集不伪造）。 */
export async function listScenarioActions(scenarioId: string): Promise<ScenarioActionVO[]> {
  const env = await rawCall(scenarioActionsUrl(scenarioId));
  return asItems<ScenarioActionVO>(unwrap(env));
}

/** GET 决策流列表。 */
export async function listScenarioDecisions(scenarioId: string): Promise<ScenarioDecisionVO[]> {
  const env = await rawCall(scenarioDecisionsUrl(scenarioId));
  return asItems<ScenarioDecisionVO>(unwrap(env));
}

/** POST 审批（审批人由后端从 JWT 主体取，请求体为空）。 */
export async function approveScenarioAction(actionId: string): Promise<ScenarioActionResult> {
  const env = await rawCall(actionApproveUrl(actionId), { method: 'POST' });
  const data = unwrap(env);
  return (data && typeof data === 'object' ? data : {}) as ScenarioActionResult;
}

/**
 * 登录主体（审批人只读展示 + approver 必填即时提示）。
 * 与 `pages/ontology/ProposalPanel.tsx` 同约定：localStorage['username']。
 * 取不到即返回空串 ⇒ 表单报 approver 缺失，绝不本地编造用户名。
 */
export function readApproverSubject(): string {
  try {
    if (typeof localStorage === 'undefined') return '';
    return localStorage.getItem('username') ?? '';
  } catch {
    return '';
  }
}

// ── 回链展示辅助（纯格式化，不做数值兜底）─────────────

/** 金额展示：字符串原样千分位；空/非法返回 null（调用方渲染占位，不填 0）。 */
export function formatExpectedImpact(raw: unknown): string | null {
  if (raw === null || raw === undefined || raw === '') return null;
  const s = String(raw).trim();
  if (!/^-?\d+(\.\d+)?$/.test(s)) return s;
  const [int, frac] = s.split('.');
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return frac === undefined ? grouped : `${grouped}.${frac}`;
}
