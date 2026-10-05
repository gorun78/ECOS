/**
 * 分册10 消费侧 API client — W Agent 编排域（DIK/C 赋能，24 端点）。
 *
 * <b>单通道纪律</b>：本文件是 P01~P13 界面（载体卷 08 §2.1）与 Agent 工具链
 * 消费 `/api/v1/wagent/*` 的<b>唯一前端通道</b>（同 C181 gitService 纪律）。
 * 端点族 = ai-engine（aiming :18084，vite.config.ts `/api/v1/wagent` → :18084）。
 *
 * <b>字段类型纪律</b>（金额铁律 C116）：所有金额/比率/概率字段 = {@link String}
 *（NUMERIC(18,2)/9,6/5,4 序列化透传，禁本地 double 运算，铁律 §0.6），
 * 数字一致性（F10-24）由 Agent 侧 {@code ClaimVerifier} 或 CM registry 承载。
 *
 * 端点族（§5.2 24 op）：
 *   - goals 2：createGoal/reviseGoal
 *   - questions 2：createQuestion/getQuestion
 *   - runs 6：startRun/getRun/streamRunEvents/submitRunInput/approveRunGate/cancelRun
 *   - flags 1：setKillSwitch
 *   - readiness 2：getReadiness/refillReadiness
 *   - tools 3：searchTools/describeTool/validateToolContract
 *   - candidates 4：listCandidates/getCandidate/reviewCandidate/publishCandidate
 *   - claims+evidence 2：listClaims/traceEvidence
 *   - decisions 1：recordDecision
 *   - actions 2：draftAction/commitAction
 */
import { apiFetchData } from "../api";

const W = "/api/v1/wagent";
const GOALS = `${W}/goals`;
const QUESTIONS = `${W}/questions`;
const RUNS = `${W}/runs`;
const FLAGS = `${W}/flags`;
const READINESS = `${W}/readiness`;
const TOOLS = `${W}/tools`;
const CANDIDATES = `${W}/candidates`;
const CLAIMS = `${W}/claims`;
const EVIDENCE = `${W}/evidence`;
const DECISIONS = `${W}/decisions`;
const ACTIONS = `${W}/actions`;

// ── 语义类型（契约透传，非权威——权威在 ai-engine wagent 域 + DDL V227~V240）────

export type RunStatus =
  | "PLANNING" | "AWAITING_INPUT" | "AWAITING_APPROVAL" | "PLAN_VALIDATED"
  | "EXECUTING" | "WAITING_TASK" | "PAUSED" | "COMPLETED"
  | "COMPLETED_DEGRADED" | "FAILED" | "CANCELLED" | "TIMED_OUT";

export type ReadinessGrade = "A" | "B" | "C" | "D";

export type CandidateType =
  | "KNOWLEDGE_PROFILE" | "METRIC_DEFINITION" | "METRIC_CALIBER" | "SEMANTIC_METRIC"
  | "RULE" | "GRAPH_EDGE" | "PROMPT" | "PLAYBOOK" | "SKILL"
  | "ACTION" | "DECISION" | "DATA_SNAPSHOT";

export type DegradeCode =
  | "DG_K1" | "DG_D1" | "DG_C1" | "DG_C2" | "DG_L1" | "DG_T1" | "DG_M1" | "DG_M2";

export interface GoalView {
  goalId: string;
  goalType: string; // 8 IntentType
  scopeJson: string;
  timeRange: string;
  asOfDate: string;
  metricId: string;
  metricVersion: string;
  caliberVersion: string;
  successCriteria: string;
  ownerId: string;
  dueDate: string;
  status: string;
  slotsSourceJson: string;
  inferredCount: number;
  supersedesGoalId: string | null;
  versionNo: string; // DATE dtype is string (V227)
}

export interface CreateGoalRequest {
  name: string;
  goalType: string;
  scope: Record<string, unknown>;       // → scopeJson
  timeRange: string;                    // e.g. "2026-01-01..2026-12-31"
  asOfDate: string;
  metricId: string;
  metricVersion: string;
  caliberVersion: string;
  successCriteria: string;
  ownerId: string;
  dueDate: string;
  idempotencyKey?: string;              // header-driven dedup (F10-11 幂等)
}

export interface QuestionView {
  questionId: string;
  missionId: string;
  rawText: string;
  intent: string;                        // IntentType
  intentConfidence: string;              // NUMERIC(5,4) → string
  slotsJson: string;
  dependsOnJson: string;
  status: string;
  latestRunId: string | null;
  latestGrade: ReadinessGrade | null;
  answerSummary: string;
}

export interface CreateQuestionRequest {
  missionId: string;
  rawText: string;
  hintIntent?: string;                   // 意图初判 hint; confidence < 0.70 → CLARIFY
}

export interface RunView {
  runId: string;
  status: RunStatus;
  planSource: "PLAYBOOK" | "EXPLORED";
  questionId: string;
  missionId: string;
  parentRunId: string | null;
  initiatedBy: string;
  idempotencyKey: string;
  budgetJson: string;                    // 预算快照（F10-11）
  consumptionJson: string;               // 消耗快照
  degradations: DegradeCode[];           // F10-10 降级标注
  situationSnapshotJson: string;         // §9.2 Situation 引用（G2 补列）
  resultRef: string | null;
  startTime: string | null;
  endTime: string | null;
  timeline: string[];                    // 事件文本数组（F10-10 时间线页消费）
}

export interface StartRunRequest {
  questionId: string;
  missionId?: string;
  parentRunId?: string;
  idempotencyKey: string;                // 应用侧必送（DB 唯一索引）
  maxAutomationLevel?: "L0" | "L1" | "L2" | "L3";
  respondAsync?: boolean;                // Prefer: respond-async header 决定 201/202
}

export interface KillSwitchDto {
  scope: "SYSTEM" | "TENANT";
  tenantId?: string;
  enabled: boolean;
  reason: string;
}

export interface ReadinessView {
  questionId: string;
  runId: string | null;
  profileId: string;
  profileVersion: string;
  grade: ReadinessGrade;
  layerD: string;
  layerI: string;
  layerK: string;
  layerC: string;
  layerSummaryJson: string;
  gaps: Array<{
    layer: string;
    severity: string;
    description: string;
    impact: string;
    unlockFrom: ReadinessGrade;
    unlockTo: ReadinessGrade;
    suggestedAction: { type: "fetch" | "task" | "confirm" | "approve"; target: string };
  }>;
  refillRound: number;
  refunded: boolean;
  refillCapped: boolean;
}

export interface ToolContractView {
  name: string;
  version: string;
  engine: string;
  category: "QUERY" | "ANALYZE" | "COMMIT" | "AGGREGATE";
  sideEffect: string;
  minLevel: number;
  requiredPermission: string;
  inputSchemaJson: string;
  outputSchemaJson: string;
  timeoutMs: number;
  isIdempotent: boolean;
  costClass: string;
  isEvidenceOutput: boolean;
  dataClassificationText: string;
  rollbackPlanText: string | null;      // commit 类 NOT NULL
  endpointKind: "rest";
  endpointUrl: string;
  endpointMethod: string;
  owner: string;
  slaText: string;
  status: "DRAFT" | "REGISTERED" | "VALIDATED" | "ACTIVE" | "DEPRECATED" | "RETIRED";
  deprecatedBy: string | null;
  toolset: string;
  summary: string;
  disclosure: string;
  capabilityDepsText: string;
}

export interface ToolSearchParameters {
  toolset?: string;
  caller?: {
    userId: string;
    tenantId: string;
    maxAutomationLevel: number;
  };
  query?: string;
}

export interface CandidateView {
  candidateId: string;
  candidateType: CandidateType;
  targetEngine: string;
  targetObjectRefJson: string;
  payloadJson: string;
  payloadHash: string;
  generatedByRun: string;
  generatedByStep: string;
  confidence: string;                    // NUMERIC(5,4)
  basisJson: string;
  validationStructure: "PASS" | "FAIL" | "WARN";
  validationReference: "PASS" | "FAIL" | "WARN";
  validationConflict: "PASS" | "FAIL" | "WARN";
  validationImpact: "PASS" | "FAIL" | "WARN";
  validationRegression: "PASS" | "FAIL" | "WARN";
  status: string;
  batchId: string;
  engineDraftRef: string | null;
  publishedRef: string | null;
  publishedGitRef: string | null;
  expireTime: string | null;
}

export interface ReviewCandidateDto {
  reviewerId: string;
  decision: "APPROVE" | "REJECT";
  commentText?: string;
  changesJson?: string;
  approvalToken: string;
}

export interface PublishCandidateDto {
  approvalToken: string;
  actor: string;
}

export interface ClaimView {
  claimId: string;
  runId: string;
  stepId: string;
  claimText: string;
  claimType: "FACT" | "INFERENCE" | "RECOMMENDATION";
  numericValue: string | null;           // NUMERIC(18,6)
  numericUnit: string | null;
  valueSource: string;                    // CM_01..CM_05 / DATA_QUERY / DERIVED / NONE
  evidenceGrade: ReadinessGrade;
  verifyStatus: "PENDING" | "VERIFIED" | "FAILED" | "REGENERATING";
  evidenceIds: string[];
}

export interface EvidenceView {
  evidenceId: string;
  evidenceType: string;
  sourceEngine: string;
  sourceObjectType: string;
  sourceObjectId: string;
  sourceVersion: string;
  snapshotRefJson: string;
  locatorText: string;
  contentHash: string;
  classifyLevel: string;
  isValid: boolean;
}

export interface RecordDecisionRequest {
  questionId: string;
  goalId: string;
  readinessId: string;
  decisionMakerId: string;
  rationale: string;
  selectedOptionIndex: number;
}

export interface DraftActionRequest {
  optionId: string;
  title: string;
  ownerId: string;
  role: string;
  dueDate: string;                       // ISO date
  kpiText: string;
  sourceType: string;                    // e.g. "OPTION"
  sourceObjectId: string;
  publishTarget: string;
  publishMode: string;
}

// ── 24 端点 fetch 消费函数─────────────────────────────────────────────────────

// #1  POST /goals
export async function createGoal(req: CreateGoalRequest, idempotencyKey?: string) {
  return apiFetchData<GoalView>(`${GOALS}`, {
    method: "POST",
    body: JSON.stringify(req),
    headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
  });
}

// #2  PATCH /goals/{goalId}
export async function reviseGoal(goalId: string, req: Partial<CreateGoalRequest>) {
  return apiFetchData<GoalView>(`${GOALS}/${goalId}`, {
    method: "PATCH",
    body: JSON.stringify(req),
  });
}

// #3  POST /questions
export async function createQuestion(req: CreateQuestionRequest) {
  return apiFetchData<QuestionView & { status?: string; clarify?: boolean }>(`${QUESTIONS}`, {
    method: "POST",
    body: JSON.stringify(req),
  });
}

// #4  GET /questions/{questionId}
export async function getQuestion(questionId: string) {
  return apiFetchData<QuestionView>(`${QUESTIONS}/${questionId}`, { method: "GET" });
}

// #5  POST /runs（Prefer: respond-async ⇒ 202 run_id）
export async function startRun(req: StartRunRequest) {
  const res = await apiFetchData<RunView>(`${RUNS}`, {
    method: "POST",
    body: JSON.stringify(req),
    headers: req.respondAsync ? { Prefer: "respond-async" } : undefined,
  });
  return res;
}

// #6  GET /runs/{runId}
export async function getRun(runId: string) {
  return apiFetchData<RunView>(`${RUNS}/${runId}`, { method: "GET" });
}

// #7  GET /runs/{runId}/events（P-4 未闭合前在 controller 端 501 → 前端降级 polling）
export async function streamRunEvents(runId: string, lastEventId?: string) {
  return apiFetchData<{ degradedTo?: string; events?: unknown[] }>(
    `${RUNS}/${runId}/events`,
    { method: "GET", headers: lastEventId ? { "Last-Event-ID": lastEventId } : undefined },
  );
}

// #8  POST /runs/{runId}/input
export async function submitRunInput(runId: string, input: Record<string, unknown>) {
  return apiFetchData<RunView>(`${RUNS}/${runId}/input`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

// #9  POST /runs/{runId}/approval
export async function approveRunGate(runId: string, approvalToken: string, actor: string) {
  return apiFetchData<RunView>(`${RUNS}/${runId}/approval`, {
    method: "POST",
    body: JSON.stringify({ approvalToken, actor }),
  });
}

// #10  POST /runs/{runId}/cancel（幂等）
export async function cancelRun(runId: string) {
  return apiFetchData<RunView>(`${RUNS}/${runId}/cancel`, { method: "POST" });
}

// #11  POST /flags/kill-switch
export async function setKillSwitch(dto: KillSwitchDto, actor: string) {
  return apiFetchData<{ runIdCount: number }>(`${FLAGS}/kill-switch`, {
    method: "POST",
    body: JSON.stringify({ ...dto, actor }),
  });
}

// #12  GET /readiness/{questionId}
export async function getReadiness(questionId: string) {
  return apiFetchData<ReadinessView>(`${READINESS}/${questionId}`, { method: "GET" });
}

// #13  POST /readiness/{questionId}/refill
export async function refillReadiness(questionId: string) {
  return apiFetchData<ReadinessView>(`${READINESS}/${questionId}/refill`, { method: "POST" });
}

// #14  GET /tools
export async function searchTools(params: ToolSearchParameters = {}) {
  const qs = new URLSearchParams();
  if (params.toolset) qs.set("toolset", params.toolset);
  if (params.query) qs.set("q", params.query);
  return apiFetchData<ToolContractView[]>(`${TOOLS}?${qs.toString()}`, { method: "GET" });
}

// #15  GET /tools/{name}
export async function describeTool(name: string) {
  return apiFetchData<ToolContractView>(`${TOOLS}/${name}`, { method: "GET" });
}

// #16  POST /tools/{name}/validate
export async function validateToolContract(name: string) {
  return apiFetchData<{
    gates: Array<{ name: string; ok: boolean; detail: string }>;
    allPass: boolean;
  }>(`${TOOLS}/${name}/validate`, { method: "POST" });
}

// #17  GET /candidates
export async function listCandidates(filters?: {
  candidateType?: CandidateType;
  status?: string;
  batchId?: string;
}) {
  const qs = new URLSearchParams();
  if (filters?.candidateType) qs.set("type", filters.candidateType);
  if (filters?.status) qs.set("status", filters.status);
  if (filters?.batchId) qs.set("batchId", filters.batchId);
  return apiFetchData<CandidateView[]>(`${CANDIDATES}?${qs.toString()}`, { method: "GET" });
}

// #18  GET /candidates/{candidateId}
export async function getCandidate(candidateId: string) {
  return apiFetchData<CandidateView>(`${CANDIDATES}/${candidateId}`, { method: "GET" });
}

// #19  POST /candidates/{candidateId}/review
export async function reviewCandidate(candidateId: string, dto: ReviewCandidateDto) {
  return apiFetchData<CandidateView>(`${CANDIDATES}/${candidateId}/review`, {
    method: "POST",
    body: JSON.stringify(dto),
  });
}

// #20  POST /candidates/{candidateId}/publish
export async function publishCandidate(candidateId: string, dto: PublishCandidateDto) {
  return apiFetchData<CandidateView>(`${CANDIDATES}/${candidateId}/publish`, {
    method: "POST",
    body: JSON.stringify(dto),
  });
}

// #21  GET /claims/{runId}
export async function listClaims(runId: string) {
  return apiFetchData<ClaimView[]>(`${CLAIMS}/${runId}`, { method: "GET" });
}

// #22  GET /evidence/{evidenceId}
export async function traceEvidence(evidenceId: string) {
  return apiFetchData<EvidenceView>(`${EVIDENCE}/${evidenceId}`, { method: "GET" });
}

// #23  POST /decisions
export async function recordDecision(req: RecordDecisionRequest) {
  return apiFetchData<{ decisionId: string }>(`${DECISIONS}`, {
    method: "POST",
    body: JSON.stringify(req),
  });
}

// #24a  POST /actions
export async function draftAction(req: DraftActionRequest) {
  return apiFetchData<{ actionId: string }>(`${ACTIONS}`, {
    method: "POST",
    body: JSON.stringify(req),
  });
}

// #24b  POST /actions/{id}/commit
export async function commitAction(actionId: string, approvalToken: string, actor: string) {
  return apiFetchData<{ committed: boolean; publishedGitRef: string }>(
    `${ACTIONS}/${actionId}/commit`,
    { method: "POST", body: JSON.stringify({ approvalToken, actor }) },
  );
}
