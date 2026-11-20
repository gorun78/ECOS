/**
 * decisionWorkspaceApi — 经营决策工作空间缺口的两个端点补位。
 *
 * <p>本文件只补"前端尚无 service"的两条真实端点，不接管已有单源：
 * 场景列表仍走 `scenarioActionClient.workspaceScenariosUrl()`，
 * 行动/决策仍走 `scenarioActionClient`，运行历史仍走 `cognitive/outcomesApi`。</p>
 *
 * <p>路径纪律：`preValidateScenario` 裸引用 `WORKSPACE_SCENARIOS_BASE`（该常量已计入
 * URL 族棘轮，不新增站点）；`compareForecastRuns` 用 `outcomesApi` 的
 * `forecastRunsComparePath`（同族派生）。本文件**不出现 `"/api"` 字面量**。</p>
 *
 * @license Apache-2.0
 */

import { apiFetchData } from "./httpClient";
import { WORKSPACE_SCENARIOS_BASE } from "./scenarioActionClient";
import { forecastRunsComparePath } from "./cognitive/outcomesApi";
import { COGNITIVE_BASE } from "./cognitiveClient";

/** 单条场景预检结果（对齐 `PreValidateCheckVO {name, pass, detail}`）。 */
export interface PreValidateCheck {
  /** 检查项标识：mind / sandbox_coverage / bindings_reachability */
  name: string;
  pass: boolean;
  /** 服务端技术明细（中文硬编码，只作原始明细展示，不当业务文案） */
  detail?: string;
}

/** `POST /api/v1/workspace/scenarios/{id}/pre-validate` 场景运行条件预检。 */
export async function preValidateScenario(scenarioId: string): Promise<PreValidateCheck[]> {
  const url = `${WORKSPACE_SCENARIOS_BASE}/${encodeURIComponent(scenarioId)}/pre-validate`;
  const data = await apiFetchData<PreValidateCheck[]>(url, { method: "POST" });
  return Array.isArray(data) ? data : [];
}

/** 双 run 对比中的差异标记（后端 `ForecastRunOrchestrationService` 组装的 `diff` 键集）。 */
export interface ForecastRunCompareDiff {
  caliberIdDiffers?: boolean;
  caliberVersionDiffers?: boolean;
  asOfTimeDiffers?: boolean;
  snapshotIdDiffers?: boolean;
  formulaVersionDiffers?: boolean;
  [key: string]: unknown;
}

export interface ForecastRunCompareResult {
  baseline?: Record<string, unknown>;
  candidate?: Record<string, unknown>;
  diff?: ForecastRunCompareDiff;
  [key: string]: unknown;
}

/**
 * `GET /api/v1/workspace/forecast-runs/compare?baselineRunId=&candidateRunId=`
 * 双 run 数值对比。注意：`POST /api/v1/cognitive/scenario/compare` 是模拟器跑分，
 * 与 runId 无关，不可替代本端点。
 *
 * 计算 SPI 未装配时后端返回 501 `STUB_CAPABILITY`，调用方须单独识别。
 */
export async function compareForecastRuns(
  baselineRunId: string,
  candidateRunId: string,
): Promise<ForecastRunCompareResult> {
  const data = await apiFetchData<ForecastRunCompareResult>(
    forecastRunsComparePath(baselineRunId, candidateRunId),
  );
  return data && typeof data === "object" ? data : {};
}

/**
 * 反事实推演请求体（对齐后端 `CounterfactualRequest`）。
 *
 * 注意：`cognitiveEngineApi.runCounterfactual` 发的是 `{variable,newValue,context}`，
 * 与后端 `{domain,variableName,...}` **契约不符**（该函数无任何生产调用方）。
 * 本函数按后端真实契约组包，不复用那条死路径。
 *
 * `domain` 与 `variableName` 必须已在 belief 表中存在，否则后端 404；
 * 而全仓没有枚举 belief domain 的端点，故只能由调用方（用户）显式提供。
 */
export interface CounterfactualRequest {
  scenarioId?: string;
  domain: string;
  variableName: string;
  sampleCount?: number;
  seed?: number;
}

/** `POST /api/v1/cognitive/counterfactual` do(A) 干预式反事实推演。 */
export async function runCounterfactualSim(
  body: CounterfactualRequest,
): Promise<Record<string, unknown>> {
  const data = await apiFetchData<Record<string, unknown>>(`${COGNITIVE_BASE}/counterfactual`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return data && typeof data === "object" ? data : {};
}
