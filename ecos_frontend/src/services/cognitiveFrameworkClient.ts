/**
 * cognitiveFrameworkClient — 认知框架注册/策略选择/计划校验唯一 REST 通道。
 *
 * <p>端点（后端 cognitive-engine，2026-11-03 落地）：
 * GET  /api/v1/cognitive/frameworks
 * POST /api/v1/cognitive/frameworks/select
 * POST /api/v1/cognitive/plans/validate
 * 传输复用 httpClient#apiFetchData（auth/解包/traceId 单源），本文件只做契约类型与错误分类。</p>
 */
import { API_BASE, apiFetchData } from './httpClient';

const FRAMEWORK_BASE = `${API_BASE}/v1/cognitive/frameworks`;
const PLAN_VALIDATE = `${API_BASE}/v1/cognitive/plans/validate`;

export interface CognitiveFrameworkVO {
  frameworkId: string;
  version: string;
  name: string;
  category: string;
  purpose?: string;
  status: string;
  problemTypes?: string[];
  domains?: string[];
  preconditions?: string[];
  inputSchemaRef?: string;
  outputSchemaRef?: string;
  requiredCapabilities?: string[];
  contractResolvable?: boolean;
  contentHash?: string;
}

export interface FrameworkCandidateVO {
  frameworkId: string;
  version: string;
  name?: string;
  category?: string;
  score?: number;
  scoreKind?: string;
  calibrated?: boolean;
  confidence?: number | null;
  reasons?: string[];
  /** 选中原因码（如 HARD_FILTERS_PASSED），与 version 共同构成“用哪个版本、为什么”。 */
  selectionReason?: string;
}

export interface UsedFrameworkVersionVO {
  frameworkId: string;
  version: string;
  selectionReason?: string;
}

export interface FrameworkPlanRevisionVO {
  decision: string;
  reason: string;
  issues?: ValidationIssueVO[];
  usedFrameworkVersions?: UsedFrameworkVersionVO[];
}

export interface FrameworkSelectionResultVO {
  selectedFrameworks: FrameworkCandidateVO[];
  excludedFrameworks: FrameworkCandidateVO[];
  missingInputs: string[];
  planDraft: Record<string, unknown> | null;
  planValidation?: { passed: boolean; dagAcyclic: boolean; issues: ValidationIssueVO[] };
  validationRequirements: string[];
  stopConditions: string[];
  riskDecision?: Record<string, unknown>;
  selectionTrace?: Record<string, unknown>;
  /** 实际使用的框架版本与选中原因（后端 B21 起返回）。 */
  usedFrameworkVersions?: UsedFrameworkVersionVO[];
  /** 仅在计划校验失败（REPLAN_OR_REJECT）时返回，记录重规划原因与版本。 */
  planRevision?: FrameworkPlanRevisionVO;
  status: string;
}

export interface ValidationIssueVO {
  code: string;
  severity: string;
  detail: string;
}

export interface PlanValidationResultVO {
  passed: boolean;
  dagAcyclic: boolean;
  issues: ValidationIssueVO[];
}

export class CognitiveFrameworkApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;

  constructor(message: string, status: number, code: string, traceId?: string) {
    super(message);
    this.name = 'CognitiveFrameworkApiError';
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}

export function isCognitiveFrameworkApiError(e: unknown): e is CognitiveFrameworkApiError {
  return e instanceof CognitiveFrameworkApiError;
}

function toError(e: unknown): CognitiveFrameworkApiError {
  const raw = e as { status?: number; code?: string; traceId?: string; message?: string };
  return new CognitiveFrameworkApiError(
    raw?.message ?? String(e),
    typeof raw?.status === 'number' ? raw.status : 0,
    raw?.code ?? 'UNKNOWN',
    raw?.traceId,
  );
}

function qs(params: Record<string, string | undefined>): string {
  const parts = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v as string)}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

export async function listFrameworks(params: {
  problemType?: string;
  domain?: string;
  status?: string;
} = {}): Promise<CognitiveFrameworkVO[]> {
  try {
    return await apiFetchData<CognitiveFrameworkVO[]>(`${FRAMEWORK_BASE}${qs(params)}`);
  } catch (e) {
    throw toError(e);
  }
}

export async function selectFrameworks(body: Record<string, unknown>): Promise<FrameworkSelectionResultVO> {
  try {
    return await apiFetchData<FrameworkSelectionResultVO>(`${FRAMEWORK_BASE}/select`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  } catch (e) {
    throw toError(e);
  }
}

export async function validatePlan(plan: Record<string, unknown>): Promise<PlanValidationResultVO> {
  try {
    return await apiFetchData<PlanValidationResultVO>(PLAN_VALIDATE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(plan),
    });
  } catch (e) {
    throw toError(e);
  }
}
