/**
 * useDecisionWorkspace — 决策工作空间的场景维度状态（原型 v2 形态）。
 *
 * 职责边界：只负责「有哪些场景、当前选哪个」，不取任何任务结果。
 * 四类任务各自在 pane 内取数，场景切换时由外层用 `key={scenarioId}` 强制重建，
 * 因此本 hook 不需要手写「清空下游结果」逻辑（漏清是这类页面最常见的状态泄漏）。
 *
 * 默认选中：优先第一个 ACTIVE；无 ACTIVE 则取列表首项；列表为空则空串
 * （详情面板据此禁用场景下拉并提示先在场景工作台创建场景）。
 *
 * @license Apache-2.0
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { apiFetchData } from "../../api";
import { NoAccessError } from "../../services/httpClient";
import { workspaceScenariosUrl } from "../../services/scenarioActionClient";
import logger from "../../services/logger";
import type { AsyncState, ScenarioOption } from "./decisionTypes";

interface RawScenario {
  id?: unknown;
  name?: unknown;
  status?: unknown;
  businessGoal?: unknown;
}

function toOption(raw: RawScenario): ScenarioOption | null {
  if (typeof raw.id !== "string" || raw.id === "") return null;
  return {
    id: raw.id,
    name: typeof raw.name === "string" && raw.name !== "" ? raw.name : raw.id,
    status: typeof raw.status === "string" ? raw.status : undefined,
    businessGoal: typeof raw.businessGoal === "string" ? raw.businessGoal : undefined,
  };
}

/** 默认选中第一个 ACTIVE，其次首项。 */
function pickDefault(list: ScenarioOption[]): string {
  const active = list.find((s) => s.status === "ACTIVE");
  return (active ?? list[0])?.id ?? "";
}

export interface DecisionWorkspaceState {
  options: ScenarioOption[];
  selectedId: string;
  state: AsyncState<ScenarioOption[]>;
  selected: ScenarioOption | null;
  setSelectedId: (id: string) => void;
  reload: () => void;
}

export function useDecisionWorkspace(): DecisionWorkspaceState {
  const [state, setState] = useState<AsyncState<ScenarioOption[]>>({ kind: "loading" });
  const [selectedId, setSelectedId] = useState("");

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    try {
      const data = await apiFetchData<RawScenario[]>(workspaceScenariosUrl());
      const list = (Array.isArray(data) ? data : [])
        .map(toOption)
        .filter((s): s is ScenarioOption => s !== null);
      setState({ kind: "ready", value: list });
      // 已选场景仍在列表里就保留用户选择，否则回落到默认项
      setSelectedId((prev) => (list.some((s) => s.id === prev) ? prev : pickDefault(list)));
    } catch (e) {
      if (e instanceof NoAccessError) {
        setState({ kind: "denied" });
        setSelectedId("");
        return;
      }
      logger.error("decision-workspace-scenarios", e);
      setState({ kind: "error", message: e instanceof Error ? e.message : String(e) });
      setSelectedId("");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const options = useMemo(
    () => (state.kind === "ready" ? state.value : []),
    [state],
  );
  const selected = useMemo(
    () => options.find((s) => s.id === selectedId) ?? null,
    [options, selectedId],
  );

  return {
    options,
    selectedId,
    state,
    selected,
    setSelectedId,
    reload: () => {
      void load();
    },
  };
}
