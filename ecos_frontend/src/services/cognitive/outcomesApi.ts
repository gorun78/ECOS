/**
 * cognitive/outcomesApi — W→D 结果评估与世界模型推演读取面
 * （详设-09 v1.2 §十一 H-8 P09 四联视图数据面 / PRD-09 §4.2）
 *
 * <p>2026-10-13 复盘 S2 收口追加：L3 受控运行面（what-if 推演 `scenario/simulate`
 * 探索通道 + 运行历史溯源 `workspace/scenarios/{id}/runs`，含响应头 traceId 实拿）。
 * 新增一律只增函数不改既有签名。</p>
 *
 * <p>本模块自 `services/cognitiveClient.ts` 拆出（前端开发规范 §十 单文件 ≤800 行，
 * 行数门禁 `file-size.test.ts`）；拆分不改对外语义：路径常量仍由
 * {@link COGNITIVE_BASE} / {@link WORLD_MODEL_BASE} 派生（全仓无第二处字面量，
 * 见 `api/__tests__/cognitivePaths.singleSource.spec.ts`），fetch/auth 仍走
 * cognitiveClient 的**唯一** `callJson` 通道（禁本模块自拼 Bearer 头）。</p>
 *
 * <p>真值纪律（铁律 v2.0 :14）：本模块只做**读取与形状归一**，`predicted/actual/absoluteError/
 * mae/mape` 一律取服务端确定性计算结果；`applicable=false` 行保留并显示「不适用 + 原因」，
 * **禁前端以 0 / 均值 / 上次值填充**（后端 MetricEvaluation javadoc 同口径）。</p>
 *
 * @license Apache-2.0
 */

import { API_BASE } from "../httpClient";
import {
  asArray,
  callJson,
  callJsonWithTrace,
  COGNITIVE_BASE,
  WORLD_MODEL_BASE,
} from "../cognitiveClient";

/** 评估根路径（由 COGNITIVE_BASE 派生，禁第二处字面量；对齐 cognitivePaths 单源守卫口径）。 */
export const OUTCOMES_BASE_PATH = `${COGNITIVE_BASE}/outcomes`;

/** 按预测运行读回评估列表（runId 必 URL 编码，防路径注入）。 */
export function outcomesByRunPath(runId: string): string {
  return `${OUTCOMES_BASE_PATH}/by-run/${encodeURIComponent(runId)}`;
}

/** 场景维度评估列表路径（`scenarioId` 后端必填；`status` 可选过滤）。 */
export function outcomesListPath(scenarioId: string, status?: string): string {
  const q = new URLSearchParams({ scenarioId });
  if (status) q.set("status", status);
  return `${OUTCOMES_BASE_PATH}?${q.toString()}`;
}

/** world-model 子路径构造（WORLD_MODEL_BASE 单源；501 stub 经 callJson 归一为 stub=true）。 */
export function worldModelUrl(suffix: string): string {
  return `${WORLD_MODEL_BASE}${suffix}`;
}

/** 单指标评估行（对齐后端 `MetricEvaluation`；不适用行的数值必为 null，禁前端补零）。 */
export interface OutcomeMetricEvaluation {
  metricRef?: string;
  /** 当时预测值（字符串保精度回显，禁 Number() 转换金额） */
  predicted?: string | number | null;
  /** 已关账实际值 */
  actual?: string | number | null;
  absoluteError?: string | number | null;
  relativeError?: string | number | null;
  /** 确定性计算产物引用（可复算锚点） */
  calculationRef?: string;
  applicable?: boolean;
  notApplicableReason?: string;
  caliberId?: string;
}

/** 诊断行（对齐后端 `OutcomeDiagnosis`；verdict 三态由服务端单源，禁前端推断）。 */
export interface OutcomeDiagnosisItem {
  metricRef?: string;
  verdict?: string;
  explanation?: string;
  assetRefs?: string[];
  /** 关联待复审假设（四联视图第 4 联，服务端产出，禁前端合成） */
  pendingHypotheses?: string[];
  explanationBasis?: boolean;
}

/** 更新建议候选引用（对齐后端 `UpdateCandidateRef`；只存引用非正文）。 */
export interface OutcomeUpdateCandidateItem {
  candidateType?: string;
  candidateRef?: string;
  status?: string;
  basisMetricRefs?: string[];
}

/** 一次评估的结果体（对齐后端 `OutcomeEvaluateResult` 的 `data` 段）。 */
export interface OutcomeEvaluateResultDto {
  evaluationId?: string;
  /** PENDING / EVALUATED / NEEDS_RECONCILE / REJECTED（服务端词表，未识别值原样透传） */
  status?: string;
  metrics: OutcomeMetricEvaluation[];
  diagnoses: OutcomeDiagnosisItem[];
  updateCandidates: OutcomeUpdateCandidateItem[];
  warnings: string[];
  blockers: string[];
  caliberAlignment?: string;
  calculationRefs: string[];
  mae?: string | number | null;
  mape?: string | number | null;
  totalSampleCount?: number;
  applicableSampleCount?: number;
  traceId?: string;
}

/** 服务端行 → DTO 归一：数组字段缺失一律转空集（DG-6「空集与故障严格可分」，禁造元素）。 */
function toOutcomeResult(raw: unknown): OutcomeEvaluateResultDto {
  const o = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  return {
    evaluationId: typeof o.evaluationId === "string" ? o.evaluationId : undefined,
    status: typeof o.status === "string" ? o.status : undefined,
    metrics: asArray<OutcomeMetricEvaluation>(o.metrics),
    diagnoses: asArray<OutcomeDiagnosisItem>(o.diagnoses),
    updateCandidates: asArray<OutcomeUpdateCandidateItem>(o.updateCandidates),
    warnings: asArray<string>(o.warnings),
    blockers: asArray<string>(o.blockers),
    caliberAlignment: typeof o.caliberAlignment === "string" ? o.caliberAlignment : undefined,
    calculationRefs: asArray<string>(o.calculationRefs),
    mae: (o.mae as string | number | null | undefined) ?? null,
    mape: (o.mape as string | number | null | undefined) ?? null,
    totalSampleCount: typeof o.totalSampleCount === "number" ? o.totalSampleCount : undefined,
    applicableSampleCount: typeof o.applicableSampleCount === "number" ? o.applicableSampleCount : undefined,
    traceId: typeof o.traceId === "string" ? o.traceId : undefined,
  };
}

/** `GET /outcomes/by-run/{runId}`：一次 run 可能对应多次评估，空集 = 200 + []（非故障）。 */
export async function fetchOutcomesByRun(runId: string): Promise<OutcomeEvaluateResultDto[]> {
  const data = await callJson(outcomesByRunPath(runId));
  return asArray<unknown>(data).map(toOutcomeResult);
}

/** `GET /outcomes?scenarioId=`：场景维度列表（status 可选过滤）。 */
export async function fetchOutcomesByScenario(
  scenarioId: string,
  status?: string,
): Promise<OutcomeEvaluateResultDto[]> {
  const data = await callJson(outcomesListPath(scenarioId, status));
  return asArray<unknown>(data).map(toOutcomeResult);
}

/**
 * 世界模型情景推演（`POST /api/v1/world-model/scenarios`）。
 *
 * <p>后端 `cognitive2/controller/WorldModelController.java:38` 当前为**显式 501 stub**
 * （真实 HTTP 501 + `data.stub=true`），经 `callJson` 归一为 `CognitiveApiError{code:"STUB",
 * stub:true}` ⇒ 调用方渲染「该能力尚未上线」占位，**禁本地合成推演结果**（DG-9 / 铁律 :14）。</p>
 */
export async function runWorldModelScenarioSimulation(
  scenario: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const data = await callJson(worldModelUrl("/scenarios"), {
    method: "POST",
    body: JSON.stringify(scenario),
  });
  return (data && typeof data === "object" ? data : {}) as Record<string, unknown>;
}

// ── L3 受控运行面（复盘 S2 收口 2026-10-13）─────────────────────────
//
// 两块数据面在此单源登记（详设-09 §3.2 L3「受控运行 + 推演标注」）：
// ① what-if 推演 = `POST /api/v1/cognitive/scenario/simulate`（探索通道，
//    后端 cognitive2 `ScenarioController.simulate`：请求**不带 mode** ⇒
//    走 `ScenarioSimulatorServiceImpl.simulate`，出口强制 `exploratory=true`
//    + `caliber=NON_ACCOUNTING`；`mode=DETERMINISTIC_DIFF` 为确定性重算通道，
//    响应形态不同且无 exploratory 字段 ⇒ 本函数不发送 mode，徽标判定一律
//    以响应回显的 `exploratory` 字段为准，禁按请求参数猜测、禁前端合成）。
// ② 运行溯源 = `GET /api/v1/workspace/scenarios/{id}/runs`（workspace
//    `ScenarioRunController.history:58`，行 = ecos_scenario_run 的
//    id/run_type/status/degraded/create_time 等列）；traceId 经
//    `callJsonWithTrace` 从响应头 `X-Request-Id`（gateway RequestContextFilter
//    :51 恒回写）实拿。run 行**无** blueprint_version/context_digest 列
//    （WorkspaceDataDao.listRunHistory:447 实证 SELECT 列清单），前端禁伪造关联。

/** scenario/simulate 路径（由 COGNITIVE_BASE 派生，禁第二处字面量）。 */
export const COGNITIVE_SCENARIO_SIMULATE_PATH = `${COGNITIVE_BASE}/scenario/simulate`;

/** what-if 推演入参（对齐 ScenarioController.simulate 探索分支读取的三键）。 */
export interface WhatIfSimulateInput {
  name: string;
  domain?: string;
  variables?: Record<string, string | number>;
}

/** 推演结果 DTO（对齐后端 SimulationResult；未回传字段保持 undefined，禁合成）。 */
export interface WhatIfSimulationResultDto {
  id?: string;
  scenarioId?: string;
  /** 服务端 SimulationStatus（RUNNING/COMPLETED/FAILED 原样透传，失败也是 200，UI 必须如实渲染） */
  status?: string;
  summary?: string;
  confidence?: number;
  baseline?: Record<string, unknown>;
  predicted?: Record<string, unknown>;
  deltas?: Record<string, unknown>;
  trends?: Record<string, unknown>;
  predictions?: Record<string, unknown>;
  assumptions: string[];
  /** 后端 SimulationResult.exploratory 回显（Jackson `isExploratory()` ⇒ JSON `exploratory`）；UI 徽标唯一判定源 */
  exploratory?: boolean;
  /** 后端 caliber 回显（探索通道恒 NON_ACCOUNTING；缺失即不显示口径文案） */
  caliber?: string;
}

function toObjectOrUndefined(v: unknown): Record<string, unknown> | undefined {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined;
}

function toWhatIfResult(raw: unknown): WhatIfSimulationResultDto {
  const o = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  return {
    id: typeof o.id === "string" ? o.id : undefined,
    scenarioId: typeof o.scenarioId === "string" ? o.scenarioId : undefined,
    status: typeof o.status === "string" ? o.status : undefined,
    summary: typeof o.summary === "string" ? o.summary : undefined,
    confidence: typeof o.confidence === "number" ? o.confidence : undefined,
    baseline: toObjectOrUndefined(o.baseline),
    predicted: toObjectOrUndefined(o.predicted),
    deltas: toObjectOrUndefined(o.deltas),
    trends: toObjectOrUndefined(o.trends),
    predictions: toObjectOrUndefined(o.predictions),
    assumptions: asArray<string>(o.assumptions),
    exploratory: typeof o.exploratory === "boolean" ? o.exploratory : undefined,
    caliber: typeof o.caliber === "string" ? o.caliber : undefined,
  };
}

/**
 * what-if 推演（探索通道）。**不发送 `mode`** ⇒ 后端必走 exploratory=true 分支；
 * 但 UI 徽标仍只认响应回显的 `exploratory===true`（判定源=响应字段，双保险防漂移）。
 * 引擎/LLM 不可用时按 callJson 错误矩阵归一（503/NETWORK…），调用方渲染降级横幅。
 */
export async function simulateScenarioWhatIf(input: WhatIfSimulateInput): Promise<WhatIfSimulationResultDto> {
  const body: Record<string, unknown> = { name: input.name };
  if (input.domain) body.domain = input.domain;
  if (input.variables && Object.keys(input.variables).length > 0) body.variables = input.variables;
  const data = await callJson(COGNITIVE_SCENARIO_SIMULATE_PATH, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return toWhatIfResult(data);
}

/** workspace 场景运行历史路径（API_BASE 派生，对齐 ScenarioRunController `/{id}/runs`）。 */
export function scenarioRunsHistoryPath(scenarioId: string, limit = 5): string {
  const capped = Math.min(20, Math.max(1, Math.floor(limit)));
  const q = new URLSearchParams({ limit: String(capped) });
  return `${API_BASE}/v1/workspace/scenarios/${encodeURIComponent(scenarioId)}/runs?${q.toString()}`;
}

/** 运行历史行（`List<Map>` 原样透传；JdbcTemplate 列名为 snake_case，读取面容错取值）。 */
export interface ScenarioRunRow {
  runId: string;
  runType?: string;
  status?: string;
  degraded?: boolean;
  createTime?: string;
  metric?: unknown;
  deviation?: unknown;
  [key: string]: unknown;
}

export interface ScenarioRunHistoryResult {
  rows: ScenarioRunRow[];
  /** 本次读取请求的 traceId（响应头 X-Request-Id 实拿；无则 undefined，UI 显示「—」） */
  traceId?: string;
}

function toRunRow(raw: unknown): ScenarioRunRow | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const id = o.id ?? o.runId;
  if (typeof id !== "string" || id === "") return null;
  const degradedRaw = o.degraded;
  return {
    runId: id,
    runType: typeof o.run_type === "string" ? o.run_type : typeof o.runType === "string" ? o.runType : undefined,
    status: typeof o.status === "string" ? o.status : undefined,
    degraded: typeof degradedRaw === "boolean" ? degradedRaw : undefined,
    createTime:
      typeof o.create_time === "string" ? o.create_time : typeof o.createTime === "string" ? o.createTime : undefined,
    metric: o.metric,
    deviation: o.deviation,
    ...o,
  };
}

/** `GET /api/v1/workspace/scenarios/{id}/runs?limit=`：运行溯源读取面（含响应头 traceId）。 */
export async function fetchScenarioRunHistory(scenarioId: string, limit = 5): Promise<ScenarioRunHistoryResult> {
  const { data, traceId } = await callJsonWithTrace(scenarioRunsHistoryPath(scenarioId, limit));
  return { rows: asArray<unknown>(data).map(toRunRow).filter((r): r is ScenarioRunRow => r !== null), traceId };
}
