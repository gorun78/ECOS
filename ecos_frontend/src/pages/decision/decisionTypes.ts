/**
 * 经营决策工作空间 — 共享类型（原型 v2 形态，`/#/decision`）。
 *
 * 状态载体统一用 `AsyncState<T>` 而非 `{loading,error}` 二元组：
 * 必须把 403（无权限）与其它失败分开，否则 `apiFetchData` 抛出的
 * `NoAccessError` 会被渲染成「加载失败」，用户会反复重试。
 *
 * @license Apache-2.0
 */

/** 原型四张任务卡。 */
export type TaskKind = "forecast" | "risk" | "compare" | "actions";

/**
 * 取数六态。渲染顺序固定 `loading → denied → error → empty → ready`，
 * 顺序错了会把权限问题折成失败态。
 */
export type AsyncState<T> =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "ready"; value: T }
  | { kind: "error"; message: string; traceId?: string }
  | { kind: "denied" };

/** 场景选项（GET /api/v1/workspace/scenarios 的活跃子集）。 */
export interface ScenarioOption {
  id: string;
  name: string;
  status?: string;
  businessGoal?: string;
}

/** 详情面板的三个统计槽。`value` 为 null 表示服务端未给出，渲染「—」。 */
export interface DetailStat {
  labelKey: string;
  value: string | null;
}

/** 详情面板的准备步骤。status 只表达真实状态，不推断。 */
export interface DetailStep {
  status: "ok" | "warn" | "info";
  titleKey: string;
  descKey?: string;
}

export const TASK_KINDS: readonly TaskKind[] = ["forecast", "risk", "compare", "actions"] as const;

/** 场景无数据时的安全空值，避免各 pane 各写一份。 */
export const EMPTY_SCENARIOS: ScenarioOption[] = [];
