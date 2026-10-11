/**
 * cognitive/outcomesApi — W→D 结果评估与世界模型推演读取面
 * （详设-09 v1.2 §十一 H-8 P09 四联视图数据面 / PRD-09 §4.2）
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

import {
  asArray,
  callJson,
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
