/**
 * capabilityClient — Model & Capability Center 目录唯一 REST 通道
 * （《前端设计方案-知识库认知资产-AI认知工作台去留-场景工作台重构-2026-10-11》§3.6 /
 *  《认知域与智能域补充详细设计 v1.0》§10 §13）
 *
 * <p>绑定 ai-engine `AiCapabilityController`
 * （`@RequestMapping(AiCapabilityApiPaths.BASE_PREFIX = "/api/v1/ai")`）真实端点：
 * {@code GET /api/v1/ai/capabilities}（目录 + 契约版本，只读）。属主 aiming :18084，
 * 经 gateway 前缀只增不改。</p>
 *
 * <p><b>契约核验（2026-10-11 本机经 gateway :8080 实跑）</b>：响应 =
 * `ApiResponse<List<CapabilityReadVO>>`，逐字段与后端 record
 * {@code CapabilityReadVO(capabilityId, family, contractVersion, evidenceRequired,
 * automationLevel, costUnit, toolset)} 对齐；目录冻结 11 项（{@code AiCapabilityId.all()}）。
 * 本面板只呈现这 7 个已核验字段——`operationClass`(READ/SUGGEST/WRITE) 与 `riskLevel`
 * 仅存在于 WAgent Tool Contract 内部（`WAgentApiPaths.TOOLS_COLLECTION` 无任何 Controller
 * 消费者，无 REST 源），遵「不接受假实现」铁律<b>不在本面渲染</b>，留 §3.12 如实回写。</p>
 *
 * <p>路径单源（api-base-single-source 门禁）：以 {@link API_BASE}（httpClient 导出）
 * 拼接，本文件不出现 `"/api` 字面量；组件一律经本模块导出常量/函数取路径，禁二次硬编码。</p>
 *
 * <p>无假真理 P0：只透传后端 `{code,errorCode,message,data,traceId}` 语义。
 * 404/400/503/NETWORK 归 {@link CapabilityApiError}，绝不本地合成状态或伪装 200；
 * 目录为空即真空态（禁补默认能力项）。costUnit/toolset 未知词返回 null（禁默认值伪装）。</p>
 *
 * <p>auth 单源：本域不再自建传输——唯一一条腿改经 {@link rawAuthedJsonUrlFetch}
 * （#143/B5-15），Bearer 拼接在该单源内完成（其内部即 {@code auth.ts#getAuthToken()}，
 * 三 key union），禁直读 localStorage['token']；通道的默认头与逐键合并规则与本文件改前
 * 逐字相同，网络失败仍归 {@link CapabilityApiError}(code=NETWORK, status=0)，
 * 不套 PMO-43 归一、不做 401 清会话/403 广播（域内自持分态，取舍依据见通道文档注释）。</p>
 *
 * @license Apache-2.0
 */

import { API_BASE, rawAuthedJsonUrlFetch } from './httpClient';

// ── 静态前缀（唯一出口）─────────────────────────────

/** 能力目录集合根：`/api/v1/ai/capabilities`（§13 #1 getCapabilities，只读）。 */
export function capabilityCollectionUrl(): string {
  return `${API_BASE}/v1/ai/capabilities`;
}

/** 单能力动作子路径：`/api/v1/ai/capabilities/{id}{suffix}`（suffix 以 `/` 开头）。 */
export function capabilityActionUrl(capabilityId: string, suffix: string): string {
  return `${capabilityCollectionUrl()}/${encodeURIComponent(capabilityId)}${suffix}`;
}

/** 异步任务状态回显：`/api/v1/ai/capabilities/tasks/{taskId}/status`（§13 #4）。 */
export function capabilityTaskStatusUrl(taskId: string): string {
  return `${capabilityCollectionUrl()}/tasks/${encodeURIComponent(taskId)}/status`;
}

// ── 目录行模型（§10 三要件 + §10.6 计量，逐字段对齐 CapabilityReadVO）──

/** 后端冻结的 11 项能力目录行（只读快照）。 */
export interface CapabilityRow {
  capabilityId: string;
  family: string;
  contractVersion: string;
  evidenceRequired: boolean;
  automationLevel: number;
  costUnit: string;
  toolset: string;
}

/** 计量口径词表（实跑观测：TOKEN / CALL / INPUT_SCALE）。未知返回 null（禁伪装）。 */
export type CostUnit = 'TOKEN' | 'CALL' | 'INPUT_SCALE';

export function parseCostUnit(raw: unknown): CostUnit | null {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim().toUpperCase();
  return trimmed === 'TOKEN' || trimmed === 'CALL' || trimmed === 'INPUT_SCALE'
    ? (trimmed as CostUnit)
    : null;
}

/** 工具集命中标签（实跑观测：core / analytics / action）。未知返回 null。 */
export type ToolsetTag = 'core' | 'analytics' | 'action';

export function parseToolset(raw: unknown): ToolsetTag | null {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim().toLowerCase();
  return trimmed === 'core' || trimmed === 'analytics' || trimmed === 'action'
    ? (trimmed as ToolsetTag)
    : null;
}

/**
 * 自动化档语义色档（供 UI 三重编码：色 + 图标 + 文字）。
 * 后端 automationLevel 为 int（默认档）；<0 视为未知。禁本地臆造上限。
 */
export type AutomationTone = 'none' | 'assist' | 'auto' | 'unknown';

export function automationTone(level: number): AutomationTone {
  if (!Number.isFinite(level) || level < 0) return 'unknown';
  if (level === 0) return 'none';
  if (level === 1) return 'assist';
  return 'auto';
}

/** 证据要求徽标签义（evidenceRequired=true ⇒ 高合规，醒目色）。 */
export type EvidenceTone = 'required' | 'optional';

export function evidenceTone(required: boolean): EvidenceTone {
  return required ? 'required' : 'optional';
}

/** 容错解析后端单行（缺关键字段返回 null，禁半装伪装成合法项）。 */
export function parseCapabilityRow(raw: unknown): CapabilityRow | null {
  if (!raw || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  const capabilityId = typeof o.capabilityId === 'string' ? o.capabilityId : '';
  const family = typeof o.family === 'string' ? o.family : '';
  const contractVersion = typeof o.contractVersion === 'string' ? o.contractVersion : '';
  if (!capabilityId || !family || !contractVersion) return null;
  return {
    capabilityId,
    family,
    contractVersion,
    evidenceRequired: o.evidenceRequired === true,
    automationLevel: typeof o.automationLevel === 'number' ? o.automationLevel : Number.NaN,
    costUnit: typeof o.costUnit === 'string' ? o.costUnit : '',
    toolset: typeof o.toolset === 'string' ? o.toolset : '',
  };
}

// ── 蓝图能力交叉引用（§3.6 缺口标记；不新增后端字段，仅本地比对）──

/**
 * 从蓝图 `capabilities` 投影（后端自由结构）尽力抽取已声明 capabilityId 集合。
 * 非字符串/结构缺 capabilityId 者忽略——绝不合成不存在的声明。
 */
export function extractDeclaredCapabilityIds(capabilities: unknown): Set<string> {
  const out = new Set<string>();
  if (!Array.isArray(capabilities)) return out;
  for (const item of capabilities) {
    if (typeof item === 'string') {
      const s = item.trim();
      if (s) out.add(s);
    } else if (item && typeof item === 'object') {
      const o = item as Record<string, unknown>;
      const id = o.capabilityId ?? o.id;
      if (typeof id === 'string' && id.trim()) out.add(id.trim());
    }
  }
  return out;
}

/** 目录行是否被蓝图声明所需（用于「所需」列，非缺口真值判定）。 */
export function isRequiredByBlueprint(row: CapabilityRow, declared: ReadonlySet<string>): boolean {
  return declared.has(row.capabilityId);
}

// ── 错误归一 ─────────────────────────────────────────

export type CapabilityErrorCode =
  | 'NOT_FOUND'
  | 'BAD_REQUEST'
  | 'ENGINE_DISABLED'
  | 'SCHEMA_NOT_READY'
  | 'NETWORK'
  | 'HTTP_ERROR'
  | 'BUSINESS_ERROR'
  | 'UNKNOWN';

export class CapabilityApiError extends Error {
  readonly code: CapabilityErrorCode;
  readonly status: number;
  readonly rawErrorCode?: string;
  readonly traceId?: string;
  constructor(info: {
    code: CapabilityErrorCode;
    status: number;
    message: string;
    rawErrorCode?: string;
    traceId?: string;
  }) {
    super(info.message);
    this.name = 'CapabilityApiError';
    this.code = info.code;
    this.status = info.status;
    this.rawErrorCode = info.rawErrorCode;
    this.traceId = info.traceId;
  }
}

export function isCapabilityApiError(e: unknown): e is CapabilityApiError {
  return e instanceof CapabilityApiError;
}

function normalizeCode(status: number, _errorCode?: string, message?: string): CapabilityErrorCode {
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

async function callCapability(url: string, init: RequestInit = {}): Promise<unknown> {
  let res: Response;
  try {
    res = await rawAuthedJsonUrlFetch(url, init);
  } catch (e) {
    throw new CapabilityApiError({
      code: 'NETWORK',
      status: 0,
      message: e instanceof Error ? e.message : String(e),
    });
  }
  const text = await res.text().catch(() => '');
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      /* 非 JSON：保持 null */
    }
  }
  const o = (body && typeof body === 'object' ? body : {}) as Record<string, unknown>;
  const traceId = (res.headers.get('X-Trace-Id')
    ?? (typeof o.traceId === 'string' ? o.traceId : undefined)) as string | undefined;
  const message = typeof o.message === 'string' ? o.message : text || `HTTP ${res.status}`;
  const rawErrorCode = typeof o.errorCode === 'string' ? o.errorCode : undefined;

  if (!res.ok) {
    throw new CapabilityApiError({
      code: normalizeCode(res.status, rawErrorCode, message),
      status: res.status,
      message,
      rawErrorCode,
      traceId,
    });
  }
  if (typeof o.code === 'number' && o.code !== 200 && o.code !== 0 && o.code !== 201) {
    throw new CapabilityApiError({
      code: 'BUSINESS_ERROR',
      status: res.status,
      message,
      rawErrorCode,
      traceId,
    });
  }
  return o.data !== undefined ? o.data : body;
}

function asArray<T>(v: unknown): T[] {
  if (Array.isArray(v)) return v as T[];
  return [];
}

// ── 契约方法（逐一对应 Controller 端点）───────────────

/**
 * GET /api/v1/ai/capabilities：读取冻结能力目录（11 项快照，只读）。
 * 非法/缺三要件行经 {@link parseCapabilityRow} 过滤为 null 后剔除（禁伪装成合法项）。
 */
export async function listCapabilities(): Promise<CapabilityRow[]> {
  const data = await callCapability(capabilityCollectionUrl());
  return asArray<unknown>(data)
    .map(parseCapabilityRow)
    .filter((r): r is CapabilityRow => r !== null);
}
