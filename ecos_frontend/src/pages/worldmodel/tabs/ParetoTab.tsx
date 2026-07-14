import React from "react";
import {
  Zap, Clock, AlertCircle, XCircle, Loader2,
} from "lucide-react";
import ReactECharts from "echarts-for-react";
import type { ParetoOptimizeResult, ParetoProblem, ParetoSolution } from "../../../api";

export interface ParetoTabProps {
  locale: string;
  styles: any;
  paretoHistory: ParetoProblem[];
  paretoResult: ParetoOptimizeResult | null;
  paretoForm: {
    numObjectives: number;
    numVariables: number;
    populationSize: number;
    generations: number;
  };
  setParetoForm: React.Dispatch<React.SetStateAction<{
    numObjectives: number;
    numVariables: number;
    populationSize: number;
    generations: number;
  }>>;
  paretoLoading: boolean;
  paretoError: string;
  setParetoError: (e: string) => void;
  runParetoOptimize: () => Promise<void>;
  loadParetoResult: (problemId: string) => Promise<void>;
}

// ═══════ TAB 6: Pareto Optimization ═══════
export default function ParetoTab({
  locale, styles,
  paretoHistory, paretoResult, paretoForm, setParetoForm,
  paretoLoading, paretoError, setParetoError,
  runParetoOptimize, loadParetoResult,
}: ParetoTabProps) {
  return (
    <div className="flex-1 flex gap-4 min-h-0">
      {/* Left: History sidebar */}
      <div className={`w-56 shrink-0 border rounded-lg p-3 flex flex-col min-h-0 overflow-hidden ${styles.cardBorder} ${styles.cardBg}`}>
        <div className="flex items-center gap-2 mb-3 shrink-0">
          <Clock className="w-3.5 h-3.5 opacity-50" />
          <span className="text-xs font-semibold opacity-70">
            {locale === "zh" ? "历史优化" : "History"}
          </span>
          <span className="text-[10px] opacity-40 ml-auto">{paretoHistory.length}</span>
        </div>
        <div className="flex-1 overflow-y-auto space-y-1">
          {paretoHistory.length === 0 ? (
            <p className="text-[11px] opacity-40 text-center py-6">
              {locale === "zh" ? "暂无历史记录" : "No history yet"}
            </p>
          ) : (
            paretoHistory.map((p) => (
              <button
                key={p.problemId}
                onClick={() => loadParetoResult(p.problemId)}
                className={`w-full text-left px-2 py-1.5 rounded text-[11px] transition-all cursor-pointer border
                  ${paretoResult?.problemId === p.problemId
                    ? "border-indigo-500/40 bg-indigo-500/10"
                    : `${styles.cardBorder} hover:bg-white/5`
                  }`}
              >
                <div className="font-medium truncate">{p.problemName || p.problemId}</div>
                <div className="opacity-50 flex items-center gap-2 mt-0.5">
                  <span>{locale === "zh" ? "前沿" : "Front"}: {p.frontSize}</span>
                  <span>{p.timestamp?.slice(0, 10)}</span>
                </div>
              </button>
            ))
          )}
        </div>
      </div>

      {/* Right: Main content */}
      <div className="flex-1 flex flex-col min-h-0 overflow-y-auto space-y-4">
        {/* Pareto error */}
        {paretoError && (
          <div className="px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 flex items-center gap-2 shrink-0">
            <AlertCircle className="w-3.5 h-3.5 shrink-0" />
            {paretoError}
            <button onClick={() => setParetoError("")} className="ml-auto hover:opacity-80">
              <XCircle className="w-3 h-3" />
            </button>
          </div>
        )}

        {/* Optimization form */}
        <div className={`border rounded-lg p-4 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}>
          <div className="flex items-center gap-2 mb-3">
            <Zap className="w-4 h-4 text-amber-400" />
            <h3 className="text-sm font-bold">
              {locale === "zh" ? "优化参数" : "Optimization Parameters"}
            </h3>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-3">
            <div>
              <label className="text-[10px] opacity-50 block mb-1">
                {locale === "zh" ? "目标数量" : "Objectives"} (1-3)
              </label>
              <input
                type="number"
                min={1} max={3}
                value={paretoForm.numObjectives}
                onChange={(e) => setParetoForm(prev => ({ ...prev, numObjectives: Number(e.target.value) }))}
                className={`w-full px-2 py-1.5 rounded border text-xs font-mono ${styles.cardBorder} ${styles.cardBg} ${styles.appText} outline-hidden`}
              />
            </div>
            <div>
              <label className="text-[10px] opacity-50 block mb-1">
                {locale === "zh" ? "变量数量" : "Variables"} (1-5)
              </label>
              <input
                type="number"
                min={1} max={5}
                value={paretoForm.numVariables}
                onChange={(e) => setParetoForm(prev => ({ ...prev, numVariables: Number(e.target.value) }))}
                className={`w-full px-2 py-1.5 rounded border text-xs font-mono ${styles.cardBorder} ${styles.cardBg} ${styles.appText} outline-hidden`}
              />
            </div>
            <div>
              <label className="text-[10px] opacity-50 block mb-1">
                {locale === "zh" ? "种群大小" : "Pop Size"}
              </label>
              <input
                type="number"
                min={20} max={500}
                value={paretoForm.populationSize}
                onChange={(e) => setParetoForm(prev => ({ ...prev, populationSize: Number(e.target.value) }))}
                className={`w-full px-2 py-1.5 rounded border text-xs font-mono ${styles.cardBorder} ${styles.cardBg} ${styles.appText} outline-hidden`}
              />
            </div>
            <div>
              <label className="text-[10px] opacity-50 block mb-1">
                {locale === "zh" ? "世代数" : "Generations"}
              </label>
              <input
                type="number"
                min={10} max={500}
                value={paretoForm.generations}
                onChange={(e) => setParetoForm(prev => ({ ...prev, generations: Number(e.target.value) }))}
                className={`w-full px-2 py-1.5 rounded border text-xs font-mono ${styles.cardBorder} ${styles.cardBg} ${styles.appText} outline-hidden`}
              />
            </div>
          </div>
          <button
            onClick={runParetoOptimize}
            disabled={paretoLoading}
            className={`flex items-center gap-1.5 px-4 py-2 rounded text-xs font-medium transition-all
              ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-50 disabled:cursor-not-allowed`}
          >
            {paretoLoading ? (
              <><Loader2 className="w-3.5 h-3.5 animate-spin" />{locale === "zh" ? "优化中..." : "Optimizing..."}</>
            ) : (
              <><Zap className="w-3.5 h-3.5" />{locale === "zh" ? "执行优化" : "Run Optimization"}</>
            )}
          </button>
        </div>

        {/* Result: Scatter plot + table */}
        {paretoResult && (
          <>
            {/* Meta info */}
            <div className="flex items-center gap-4 text-xs shrink-0">
              <span className="opacity-50">
                Problem: <span className="font-mono text-indigo-400">{paretoResult.problemId}</span>
              </span>
              <span className="opacity-50">
                {locale === "zh" ? "前沿解" : "Front"}: <span className="font-mono text-amber-400">{paretoResult.frontSize}</span>
              </span>
              <span className="opacity-50">
                {locale === "zh" ? "耗时" : "Time"}: <span className="font-mono">{paretoResult.elapsed_ms}ms</span>
              </span>
            </div>

            {/* ECharts scatter plot */}
            <div className={`border rounded-lg p-4 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}>
              <h3 className="text-xs font-semibold mb-3 opacity-70">
                {locale === "zh" ? "帕累托前沿散点图" : "Pareto Front Scatter"}
              </h3>
              <ReactECharts
                style={{ height: 280 }}
                option={{
                  tooltip: {
                    trigger: "item",
                    formatter: (params: any) => {
                      const d = params.data;
                      return `f₁ = ${d[0].toFixed(4)}<br/>f₂ = ${d[1].toFixed(4)}`;
                    },
                  },
                  grid: { top: 20, right: 30, bottom: 40, left: 50 },
                  xAxis: {
                    name: "f₁ (minimize)",
                    nameLocation: "center",
                    nameGap: 25,
                    type: "value",
                    nameTextStyle: { fontSize: 10, opacity: 0.6 },
                  },
                  yAxis: {
                    name: "f₂ (minimize)",
                    nameLocation: "center",
                    nameGap: 35,
                    type: "value",
                    nameTextStyle: { fontSize: 10, opacity: 0.6 },
                  },
                  series: [
                    {
                      name: locale === "zh" ? "帕累托前沿" : "Pareto Front",
                      type: "scatter",
                      data: paretoResult.solutions.map((s: ParetoSolution) => {
                        const fKeys = Object.keys(s.objectives).sort();
                        return [s.objectives[fKeys[0]] ?? 0, s.objectives[fKeys[1]] ?? 0];
                      }),
                      symbolSize: 8,
                      itemStyle: {
                        color: "#f59e0b",
                        borderColor: "#fbbf24",
                        borderWidth: 1,
                      },
                      emphasis: {
                        scale: 1.5,
                        itemStyle: { color: "#f97316" },
                      },
                    },
                  ],
                }}
                opts={{ renderer: "canvas" }}
                notMerge
              />
            </div>

            {/* Solutions table */}
            <div className={`border rounded-lg p-4 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}>
              <h3 className="text-xs font-semibold mb-3 opacity-70">
                {locale === "zh" ? "帕累托前沿解详情" : "Pareto Front Solutions"}
              </h3>
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className={`border-b ${styles.cardBorder} text-left`}>
                      <th className="px-3 py-2 font-mono text-[10px] uppercase tracking-wider opacity-60">#</th>
                      {Object.keys(paretoResult.solutions[0]?.variables ?? {}).sort().map((vk) => (
                        <th key={vk} className="px-3 py-2 font-mono text-[10px] uppercase tracking-wider opacity-60">
                          {vk}
                        </th>
                      ))}
                      {Object.keys(paretoResult.solutions[0]?.objectives ?? {}).sort().map((ok) => (
                        <th key={ok} className="px-3 py-2 font-mono text-[10px] uppercase tracking-wider opacity-60 text-amber-400">
                          {ok}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {paretoResult.solutions.map((s: ParetoSolution, i: number) => {
                      const vKeys = Object.keys(s.variables).sort();
                      const oKeys = Object.keys(s.objectives).sort();
                      return (
                        <tr key={i} className="hover:bg-white/5 transition-colors">
                          <td className="px-3 py-1.5 font-mono opacity-40">{i + 1}</td>
                          {vKeys.map((vk) => (
                            <td key={vk} className="px-3 py-1.5 font-mono">{s.variables[vk].toFixed(4)}</td>
                          ))}
                          {oKeys.map((ok) => (
                            <td key={ok} className="px-3 py-1.5 font-mono text-amber-400">{s.objectives[ok].toFixed(4)}</td>
                          ))}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}

        {/* Empty state */}
        {!paretoResult && !paretoLoading && (
          <div className="flex-1 flex items-center justify-center min-h-[200px]">
            <div className="text-center opacity-50">
              <Zap className="w-10 h-10 mb-3 mx-auto opacity-30" />
              <p className="text-sm">
                {locale === "zh"
                  ? "配置参数并点击「执行优化」开始帕累托寻优"
                  : "Configure parameters and click 'Run Optimization' to start Pareto search"}
              </p>
              <p className="text-xs mt-1">
                {locale === "zh"
                  ? "将使用 NSGA-II 算法搜索多目标最优解"
                  : "Uses NSGA-II algorithm for multi-objective optimization"}
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
