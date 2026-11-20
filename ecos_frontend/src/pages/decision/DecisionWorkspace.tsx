/**
 * DecisionWorkspace — 经营决策工作空间（`/#/decision`，原型 v2 形态）。
 *
 * 形态来源：`docs/30-设计/prototype/ECOS经营决策工作空间原型-v2-2026-11-13.html`。
 * 与原型的差别：原型里的演示数字（¥5,000 万、"6 项风险"、"逾期 2 项"）一律不落地，
 * 四个任务全部接真实端点；无数据即如实空态。
 *
 * **本页不打 DemoBadge**：所有数据面都来自真实端点直出，没有一处是前端合成。
 * 给真实数据打"演示数据"徽标是反向的假真理。forecast 面板特意只读运行历史、
 * 不走 `POST /scenarios/{id}/runs` 的合成 series 分支，正是为不产生需要徽标的数据面。
 * DemoBadge 的判定单源见 `services/forecastScenarioMode.ts`。
 *
 * 场景切换语义：四个任务面板都用 `key={scenarioId}` 挂载，换场景时 React 整体
 * 卸载重建，天然清空上一个场景的结果，不存在状态泄漏。
 *
 * @license Apache-2.0
 */
import React, { useMemo, useState } from "react";
import { Compass } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import AskBox from "./AskBox";
import TaskCardGrid from "./TaskCardGrid";
import TaskDetailPanel from "./TaskDetailPanel";
import SystemStatusSection from "./SystemStatusSection";
import ContinueWorkPanel from "./ContinueWorkPanel";
import AttentionPanel from "./AttentionPanel";
import ForecastPane from "./tasks/ForecastPane";
import RiskPane from "./tasks/RiskPane";
import ComparePane from "./tasks/ComparePane";
import ActionsPane from "./tasks/ActionsPane";
import { useDecisionWorkspace } from "./useDecisionWorkspace";
import type { DetailStat, DetailStep, TaskKind } from "./decisionTypes";

export default function DecisionWorkspace() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const { options, selectedId, state, selected, setSelectedId, reload } = useDecisionWorkspace();
  const [activeTask, setActiveTask] = useState<TaskKind | null>(null);
  const [matchedBy, setMatchedBy] = useState<"keyword" | "manual" | null>(null);

  const pick = (kind: TaskKind, source: "keyword" | "manual") => {
    setActiveTask(kind);
    setMatchedBy(source);
  };

  const scenarioHint = useMemo(() => {
    if (state.kind === "error") return t("platform.decision.loadFailed") + state.message;
    if (state.kind === "denied") return t("platform.decision.task.denied");
    if (state.kind === "ready" && state.value.length === 0) return t("platform.decision.task.noScenario");
    return null;
  }, [state, t]);

  const pane = activeTask && selectedId ? renderPane(activeTask, selectedId) : null;

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      <div className="w-full max-w-[1220px] mx-auto px-4 py-5 md:px-8 md:py-7 space-y-6">
        {/* 页头 */}
        <header className="space-y-1.5">
          <div className={`flex items-center gap-2 text-[11px] font-bold tracking-[0.14em] uppercase ${styles.accentText}`}>
            <Compass size={13} />
            {t("platform.decision.workspace.eyebrow")}
          </div>
          <h1 className={`text-2xl md:text-3xl font-bold tracking-tight ${styles.appText}`}>
            {t("platform.decision.workspace.greeting")}
          </h1>
          <p className={`text-xs md:text-sm max-w-3xl ${styles.cardTextMuted}`}>
            {t("platform.decision.workspace.intro")}
          </p>
        </header>

        <AskBox onIntent={(kind) => pick(kind, "keyword")} />

        {scenarioHint && (
          <div className={`rounded-lg border px-3 py-2 text-[11px] break-words ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            {scenarioHint}
            <button
              type="button"
              onClick={reload}
              className={`ml-2 rounded border px-2 py-0.5 cursor-pointer align-middle ${styles.inputBorder} ${styles.inputText}`}
            >
              {t("platform.decision.retry")}
            </button>
          </div>
        )}

        <TaskCardGrid active={activeTask} onSelect={(k) => pick(k, "manual")} />

        {activeTask && (
          <TaskDetailPanel
            kind={activeTask}
            scenarios={options}
            scenario={selected}
            onScenarioChange={setSelectedId}
            onClose={() => {
              setActiveTask(null);
              setMatchedBy(null);
            }}
            stats={buildStats(activeTask)}
            steps={buildSteps(activeTask)}
          >
            {pane}
          </TaskDetailPanel>
        )}

        {matchedBy === "keyword" && activeTask && (
          <p className={`-mt-3 text-[11px] ${styles.cardTextMuted}`}>
            {t("platform.decision.ask.matchedHint")}
          </p>
        )}

        <div className="grid gap-4 grid-cols-1 lg:grid-cols-[minmax(0,1.55fr)_minmax(280px,1fr)]">
          <ContinueWorkPanel />
          <AttentionPanel scenarioId={selectedId || null} />
        </div>

        <SystemStatusSection />

        <p className={`text-center text-[10px] pb-2 ${styles.cardTextMuted}`}>
          {t("platform.decision.workspace.footer")}
        </p>
      </div>
    </div>
  );
}

/**
 * 四个 pane 的「三个统计槽」。值一律由各 pane 自己取真实数据后回填；
 * 此处只提供标签与空值占位（渲染「—」），绝不在外层编造数字。
 */
function buildStats(kind: TaskKind): DetailStat[] {
  switch (kind) {
    case "forecast":
      return [
        { labelKey: "platform.decision.task.forecast.stat.lastValue", value: null },
        { labelKey: "platform.decision.task.forecast.stat.confidence", value: null },
        { labelKey: "platform.decision.task.forecast.stat.model", value: null },
      ];
    case "risk":
      return [
        { labelKey: "platform.decision.task.risk.stat.count", value: null },
        { labelKey: "platform.decision.task.risk.stat.blueprintVersion", value: null },
        { labelKey: "platform.decision.task.risk.stat.blueprintStatus", value: null },
      ];
    case "compare":
      return [
        { labelKey: "platform.decision.task.compare.stat.baseline", value: null },
        { labelKey: "platform.decision.task.compare.stat.candidate", value: null },
        { labelKey: "platform.decision.task.compare.stat.diffCount", value: null },
      ];
    case "actions":
      return [
        { labelKey: "platform.decision.task.actions.stat.total", value: null },
        { labelKey: "platform.decision.task.actions.stat.approved", value: null },
        { labelKey: "platform.decision.task.actions.stat.groups", value: null },
      ];
  }
}

/** 准备步骤：原型的三步语义。状态恒为 info——真实结论由各 pane 的数据面给出。 */
function buildSteps(kind: TaskKind): DetailStep[] {
  return [
    { status: "info", titleKey: `platform.decision.task.${kind}.step.scenario`, descKey: "platform.decision.task.step.scenarioDesc" },
    { status: "info", titleKey: `platform.decision.task.${kind}.step.data`, descKey: "platform.decision.task.step.dataDesc" },
    { status: "info", titleKey: `platform.decision.task.${kind}.step.run`, descKey: "platform.decision.task.step.runDesc" },
  ];
}

function renderPane(kind: TaskKind, scenarioId: string): React.ReactNode {
  switch (kind) {
    case "forecast":
      return <ForecastPane key={scenarioId} scenarioId={scenarioId} />;
    case "risk":
      return <RiskPane key={scenarioId} scenarioId={scenarioId} />;
    case "compare":
      return <ComparePane key={scenarioId} scenarioId={scenarioId} />;
    case "actions":
      return <ActionsPane key={scenarioId} scenarioId={scenarioId} />;
  }
}
