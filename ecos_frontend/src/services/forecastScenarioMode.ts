/**
 * forecastScenarioMode — 企业经营管控（年度经营预测）场景的演示/生产模式单源判定（H-8）。
 *
 * <p>依据裁决 R-63 ②（详设-09 §八）与 PRD-09 §八 首部注记：<b>真实四源接入前，任何演示数据面
 * 必须打 DEMO 徽标，不打 = 虚假验收</b>（架构铁律 v2.0 :14）。</p>
 *
 * <p>「真实四源」= REQ-FC-02 §2.2 的取值优先级 1~4 全部经真实端点可达：
 * ① ACTUAL 事实行（`V167` 五表，data-engine 管道）② PROJECT_PLAN 事实行 ③ PROFILE_IMPUTED
 * （`/api/v1/knowledge/profiles`，`V176`）④ MANUAL_OVERRIDE（`/api/v1/knowledge/assumptions`，
 * `V177`）。详设-09 §十一 登记的阻塞项 H-1（`V197` 增列）与 H-5（forecast run 编排面 8 条端点）
 * 代码侧已结案，但<b>本场景运行面尚未实测串通四源</b> ⇒ 本开关仍为 false。</p>
 *
 * <p>置 true 的前置条件（逐项可验，禁提前翻牌）：
 * ① H-5 的 `POST /api/v1/workspace/forecast-runs` + `/{runId}/results` 实测返回确定性金额；
 * ② H-1 增列（`snapshot_id`/`formula_version`）实跑且六要素证据抽屉 ✓×6；
 * ③ 演练 2/3 的金标准对账在 Playwright 留档。三项齐备由主会话/PM 裁定后改此常量，
 *    不得在页面里各自写死 `demo=false`（防徽标漂移）。</p>
 *
 * @license Apache-2.0
 */

/** 真实四源是否已在本场景运行面串通（H-1/H-5 未结案 ⇒ 恒 false）。 */
export const FORECAST_REAL_SOURCES_CONNECTED = false;

/** 当前是否处于演示态（true ⇒ 所有演示数据面必须渲染 DEMO 徽标）。 */
export function isForecastDemoMode(): boolean {
  return !FORECAST_REAL_SOURCES_CONNECTED;
}

/**
 * 单个评估面的 DEMO 徽标判定（V367 批次 D）。
 *
 * <p>全局开关未解除时，只有「全部指标 applicable=true 且 actualValueSource=ACTION_OUTCOME」
 * 才可隐藏该面的徽标；任一指标缺实际值即保持显示（禁提前摘牌）。</p>
 */
export function shouldShowDemoBadge(
  metrics?: ReadonlyArray<{ applicable?: boolean; actualValueSource?: string }>,
): boolean {
  if (!isForecastDemoMode()) return false;
  if (!metrics || metrics.length === 0) return true;
  return !metrics.every((m) => m.applicable === true && m.actualValueSource === 'ACTION_OUTCOME');
}
