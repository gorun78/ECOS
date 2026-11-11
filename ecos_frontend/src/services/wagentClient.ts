/**
 * wagentClient.ts — WAgent 编排面服务层（批 C，2026-11-11 WAgent 三块差距计划）。
 *
 * 消费 wagent-v1 契约已实现端点（路径单源 = WAgentApiPaths.java +
 * docs/10-架构/api-contracts/openapi/wagent-v1.yaml）：
 *   createGoal / createCandidate / listCandidates / getCandidate / reviewCandidate /
 *   publishCandidate / createApprovalToken / searchTools
 *
 * 传输单源 = services/httpClient.apiFetchData（Bearer + 401/403/网络态统一处理）。
 * 无假真理（P0）：501/403/409 如实上抛，由 UI 层呈现横幅，禁本地兜底合成。
 * 请求体/响应体字段逐字对齐服务端 record（GoalCreateRequest / CandidatePublishResult /
 * ApprovalTokenService.IssuedToken），禁自造字段名。
 *
 * @license Apache-2.0
 */

import { API_BASE, apiFetchData } from "../api";

// 基址单源（W206/C188）：由 httpClient 的 API_BASE 派生，禁本文件私设 "/api…" 字面量
const WAGENT_BASE = `${API_BASE}/v1/wagent`;

// ── DTO ────────────────────────────────────────────────

export interface WagentGoalRow {
  goal_id: string;
  name: string | null;
  goal_type: string | null;
  status: string | null;
  [key: string]: unknown;
}

/** 五段校验结果（服务端 CandidateValidationVO 逐字；未校验时为 null 各段）。 */
export interface WagentCandidateValidation {
  structure: string | null;
  reference: string | null;
  conflict: string | null;
  impact: string | null;
  regression: string | null;
}

export interface WagentCandidateRow {
  candidate_id: string;
  candidate_type: string;
  target_engine: string | null;
  /** 目标对象引用（服务端按 key 字典序规范化后的形态；null ⇒ conflict 段 skipped）。 */
  target_object_ref_json?: string | null;
  status: string;
  batch_id: string | null;
  payload_hash: string | null;
  published_ref_text: string | null;
  published_git_ref: string | null;
  expire_time: string | null;
  validation?: WagentCandidateValidation | null;
  [key: string]: unknown;
}

export interface WagentToolRow {
  name: string;
  version: string;
  engine: string;
  category: string;
  side_effect?: string | null;
  status: string;
  [key: string]: unknown;
}

/** 签发结果（ApprovalTokenService.IssuedToken 逐字；token 明文仅签发响应出现一次）。 */
export interface ApprovalTokenIssued {
  approvalId: string;
  token: string;
  scopeType: string;
  scopeId: string;
  expiredAt: string;
  ttlSeconds: number;
  oneTimeUse: boolean;
  tokenEcho?: string;
  [key: string]: unknown;
}

/** 发布结果（CandidatePublishResult 逐字，camelCase）。 */
export interface WagentPublishResult {
  candidateId: string;
  status: string;
  publishedRef: string | null;
  gitRef: string | null;
  gitTag: string | null;
  repositoryId: string;
  traceId: string;
  [key: string]: unknown;
}

// ── 目标（向导①；GoalCreateRequest 字段名逐字）───────────

const GOAL_INTENTS = [
  "FORECAST",
  "DIAGNOSE",
  "SIMULATE",
  "RANK",
  "MONITOR",
  "BUILD",
  "DECIDE",
  "REVIEW",
] as const;

export type WagentGoalIntent = (typeof GOAL_INTENTS)[number];

export const WAGENT_GOAL_INTENTS: readonly WagentGoalIntent[] = GOAL_INTENTS;

export async function createWagentGoal(payload: {
  name: string;
  goalType: WagentGoalIntent | string;
  scopeJson?: string;
  successCriteriaText?: string;
  ownerId?: string;
  timeRangeText?: string;
}): Promise<WagentGoalRow> {
  return apiFetchData<WagentGoalRow>(`${WAGENT_BASE}/goals`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

// ── 候选（加班核心：创建 → 评审 → 发布）─────────────────

/** createCandidate：初始状态恒 generated（服务端语义），载荷哈希由服务端计算。 */
export async function createWagentCandidate(payload: {
  candidateType: string;
  payloadJson: string;
  targetEngine?: string;
  basisJson?: string;
  generatedByRun?: string;
  batchId?: string;
  /** 目标对象引用 JSON 串；服务端规范化后落库并驱动 validate 的 conflict 段。非法 JSON ⇒ 400。 */
  targetObjectRefJson?: string;
}): Promise<WagentCandidateRow> {
  return apiFetchData<WagentCandidateRow>(`${WAGENT_BASE}/candidates`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

/** listCandidates：类型/状态/批次/Run 受控过滤（词表越界 400）。 */
export async function listWagentCandidates(params?: {
  candidateType?: string;
  status?: string;
  batchId?: string;
  runId?: string;
}): Promise<{ candidates: WagentCandidateRow[]; total: number }> {
  const qs = new URLSearchParams();
  if (params?.candidateType) qs.set("candidateType", params.candidateType);
  if (params?.status) qs.set("status", params.status);
  if (params?.batchId) qs.set("batchId", params.batchId);
  if (params?.runId) qs.set("runId", params.runId);
  const q = qs.toString();
  return apiFetchData<{ candidates: WagentCandidateRow[]; total: number }>(
    `${WAGENT_BASE}/candidates${q ? "?" + q : ""}`,
  );
}

export async function getWagentCandidate(candidateId: string): Promise<WagentCandidateRow> {
  return apiFetchData<WagentCandidateRow>(
    `${WAGENT_BASE}/candidates/${encodeURIComponent(candidateId)}`,
  );
}

/**
 * reviewCandidate：四眼评审（仅 validated/review_pending 可审；APPROVE 必带 approvalToken）。
 * 状态闸/四眼不符时服务端 409/403 如实上抛，UI 不得伪成功。
 */
export async function reviewWagentCandidate(
  candidateId: string,
  payload: {
    decision: "APPROVE" | "REJECT" | "REVISE";
    approvalToken?: string;
    commentText?: string;
    changesJson?: string;
  },
): Promise<WagentCandidateRow> {
  return apiFetchData<WagentCandidateRow>(
    `${WAGENT_BASE}/candidates/${encodeURIComponent(candidateId)}/review`,
    { method: "POST", body: JSON.stringify(payload) },
  );
}

/**
 * validateCandidate：五段自检（D-WC-1）。
 *
 * 服务端语义：仅 generated/validating/validated 可校验，过期或已进评审链 409。
 * 任一段 fail ⇒ 状态停 validating（仍返回 200 + 五段明细）；全非 fail ⇒ validated。
 * 因此 200 不等于"已可评审"，调用方必须读 status 再决定下一步。
 */
export async function validateWagentCandidate(candidateId: string): Promise<WagentCandidateRow> {
  return apiFetchData<WagentCandidateRow>(
    `${WAGENT_BASE}/candidates/${encodeURIComponent(candidateId)}/validate`,
    { method: "POST" },
  );
}

/** publishCandidate：L3 发布（凭证必填；四眼/一次性消费由服务端裁决）。 */
export async function publishWagentCandidate(
  candidateId: string,
  approvalToken: string,
): Promise<WagentPublishResult> {
  return apiFetchData<WagentPublishResult>(
    `${WAGENT_BASE}/candidates/${encodeURIComponent(candidateId)}/publish`,
    { method: "POST", body: JSON.stringify({ approvalToken }) },
  );
}

// ── 审批凭证（admin 签发；四眼 = approver ≠ requestedBy）────

export async function createApprovalToken(payload: {
  scopeType: "CANDIDATE_PUBLISH" | "ACTION_COMMIT" | "RUN_APPROVAL";
  scopeId: string;
  requestedBy: string;
  ttlSeconds?: number;
}): Promise<ApprovalTokenIssued> {
  return apiFetchData<ApprovalTokenIssued>(`${WAGENT_BASE}/approval-tokens`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

// ── 工具契约（装配面工具检索；缺省只披露 active）────────────

export async function searchWagentTools(params?: {
  engine?: string;
  toolset?: string;
  keyword?: string;
  disclosure?: string;
}): Promise<WagentToolRow[]> {
  const qs = new URLSearchParams();
  if (params?.engine) qs.set("engine", params.engine);
  if (params?.toolset) qs.set("toolset", params.toolset);
  if (params?.keyword) qs.set("keyword", params.keyword);
  if (params?.disclosure) qs.set("disclosure", params.disclosure);
  const q = qs.toString();
  const data = await apiFetchData<{ tools?: WagentToolRow[] } | WagentToolRow[]>(
    `${WAGENT_BASE}/tools${q ? "?" + q : ""}`,
  );
  return Array.isArray(data) ? data : (data?.tools ?? []);
}
