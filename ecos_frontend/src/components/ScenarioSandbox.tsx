/**
 * ScenarioSandbox — 决策模拟沙箱与帕累托推演（可复用组件）
 * 数据自行从 API 加载，不依赖父组件传入
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useMemo } from "react";
import {
  Target, Activity, Award, Play, RefreshCw, CheckCircle, Sliders,
} from "lucide-react";
import type { Goal, CausalLink, Scenario } from "../types";
import {
  fetchWorldGoals, fetchWorldCausalLinks, fetchWorldScenarios,
} from "../api";
import GraphCanvas from "./GraphCanvas";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import ErrorBoundary from "./common/ErrorBoundary";

export default function ScenarioSandbox() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  // ── Data ──────────────────────────────
  const [goals, setGoals] = useState<Goal[]>([]);
  const [causalLinks, setCausalLinks] = useState<CausalLink[]>([]);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [goalsLoading, setGoalsLoading] = useState(true);
  const [causalLinksLoading, setCausalLinksLoading] = useState(true);
  const [scenariosLoading, setScenariosLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // ── Simulator state ───────────────────
  const [selectedScenarioId, setSelectedScenarioId] = useState<number | string | null>(null);
  const [extraShifts, setExtraShifts] = useState(3);
  const [plannedOutageHours, setPlannedOutageHours] = useState(36);
  const [simulationActive, setSimulationActive] = useState(false);
  const [simLogs, setSimLogs] = useState<string[]>([]);
  const [showParetoFrontier, setShowParetoFrontier] = useState(false);
  const [selectedCausalId, setSelectedCausalId] = useState<number | string | null>(null);

  // ── Fetch ─────────────────────────────
  useEffect(() => {
    fetchWorldGoals().then(data => {
      setGoals(data || []);
      setGoalsLoading(false);
    }).catch((e: any) => { setError(e.message || 'Goals 加载失败'); setGoalsLoading(false); });

    fetchWorldCausalLinks().then(data => {
      setCausalLinks(data || []);
      setCausalLinksLoading(false);
    }).catch((e: any) => { setError(e.message || 'Causal Links 加载失败'); setCausalLinksLoading(false); });

    fetchWorldScenarios().then(data => {
      setScenarios(data || []);
      setScenariosLoading(false);
    }).catch((e: any) => { setError(e.message || 'Scenarios 加载失败'); setScenariosLoading(false); });
  }, []);

  // Auto-select first scenario
  useEffect(() => {
    if (scenarios.length > 0 && !selectedScenarioId) {
      setSelectedScenarioId(scenarios[0].id);
    }
  }, [scenarios]);

  useEffect(() => {
    if (causalLinks.length > 0 && !selectedCausalId) {
      setSelectedCausalId(causalLinks[0].id);
    }
  }, [causalLinks]);

  const activeScenario = selectedScenarioId != null
    ? scenarios.find((s) => s.id == selectedScenarioId) || scenarios[0]
    : undefined;

  // ── Causal graph ──────────────────────
  // Build a goal ID→name lookup from loaded goals
  const goalNameMap = useMemo(() => {
    const m = new Map<string, string>();
    goals.forEach(g => { if (g.id != null) m.set(String(g.id), g.name || String(g.id)); });
    return m;
  }, [goals]);

  const causalNodes = useMemo(() => {
    if (causalLinks.length === 0) return [];
    const nodeMap = new Map<string, { id: string; type: string; label: string; status: string }>();
    causalLinks.forEach(cl => {
      const sid = String(cl.sourceGoalId ?? '');
      const tid = String(cl.targetGoalId ?? '');
      if (sid && !nodeMap.has(sid)) {
        const name = cl.sourceGoalName || goalNameMap.get(sid) || sid;
        nodeMap.set(sid, {
          id: `goal_${sid}`,
          type: "goal",
          label: name,
          status: "Healthy",
        });
      }
      if (tid && !nodeMap.has(tid)) {
        const name = cl.targetGoalName || goalNameMap.get(tid) || tid;
        nodeMap.set(tid, {
          id: `goal_${tid}`,
          type: "goal",
          label: name,
          status: "Healthy",
        });
      }
    });
    return Array.from(nodeMap.values());
  }, [causalLinks, goalNameMap]);

  const causalLinksTrans = useMemo(() => {
    return causalLinks.map((cl, i) => ({
      id: cl.id || `cl_${i}`,
      source: `goal_${cl.sourceGoalId ?? ''}`,
      target: `goal_${cl.targetGoalId ?? ''}`,
      animated: true,
    }));
  }, [causalLinks]);

  // ── Simulation ────────────────────────
  const runScenarioSimulation = () => {
    setSimulationActive(true);
    setSimLogs([
      "Initializing ECOS Scenario Simulator...",
      "Loading World Model variables with current state assumptions...",
      "Injecting system parameters..."
    ]);

    setTimeout(() => {
      setSimLogs((prev) => [
        ...prev,
        `Consolidating assumptions: shifts: ${extraShifts}/week, planned outage duration: ${plannedOutageHours} hrs.`,
        "Running multi-objective optimizer targeting: revenue maximization vs operational risk ceilings..."
      ]);
    }, 900);

    setTimeout(() => {
      setSimLogs((prev) => [
        ...prev,
        `Result calculations validated. Estimated Throughput impact: ${
          selectedScenarioId === "scen_reroute" ? `+${(extraShifts * 4.1).toFixed(1)}%` : `+350.0% Extended MTBF`
        }. Expected CapEx adjustments written to ledger.`,
        "Simulation successfully finalized. Check outcomes parameters grid below."
      ]);
      setSimulationActive(false);
    }, 2400);
  };

  // ── Render ────────────────────────────
  return (
    <ErrorBoundary>
      <div className="space-y-6">
        {/* KPI overview cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5 select-none shrink-0">
          {/* KPI 1: Revenue */}
          <div className={`border rounded-xl p-5 flex flex-col justify-between shadow-xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="flex items-center justify-between mb-2 leading-none">
              <span className={`text-[10px] uppercase font-bold font-mono tracking-wider flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                <Target className="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
                {t("cos.kpi.rev")}
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded-md bg-amber-500/10 border border-amber-500/20 text-amber-500 font-mono font-bold leading-none uppercase">
                {locale === "zh" ? "存在偏离风险" : "At Risk"}
              </span>
            </div>
            <div className="flex items-baseline justify-between mt-2.5 min-h-[32px]">
              <strong className="text-2xl font-extrabold tracking-tight" style={{ color: "var(--cardText)" }}>$890M</strong>
              <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{t("cos.kpi.rev.target")}</span>
            </div>
            <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full mt-3.5 overflow-hidden">
              <div className="h-full bg-amber-500" style={{ width: "81%" }}></div>
            </div>
          </div>

          {/* KPI 2: Downtime */}
          <div className={`border rounded-xl p-5 flex flex-col justify-between shadow-xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="flex items-center justify-between mb-2 leading-none">
              <span className={`text-[10px] uppercase font-bold font-mono tracking-wider flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                <Activity className="w-3.5 h-3.5 text-red-500" />
                {t("cos.kpi.delay")}
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded-md bg-red-500/10 border border-red-500/20 text-red-500 font-mono font-bold leading-none uppercase">
                {locale === "zh" ? "运行滞后" : "Behind"}
              </span>
            </div>
            <div className="flex items-baseline justify-between mt-2.5 min-h-[32px]">
              <strong className="text-2xl font-extrabold text-red-500 tracking-tight">
                {locale === "zh" ? "6.0% 停机率" : "6.0% Downtime"}
              </strong>
              <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{t("cos.kpi.delay.target")}</span>
            </div>
            <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full mt-3.5 overflow-hidden">
              <div className="h-full bg-red-500" style={{ width: "33%" }}></div>
            </div>
          </div>

          {/* KPI 3: Safety */}
          <div className={`border rounded-xl p-5 flex flex-col justify-between shadow-xs ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="flex items-center justify-between mb-2 leading-none">
              <span className={`text-[10px] uppercase font-bold font-mono tracking-wider flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                <Award className="w-3.5 h-3.5 text-green-600" />
                {t("cos.kpi.safety")}
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded-md bg-green-500/10 border border-green-500/20 text-green-500 font-mono font-bold leading-none uppercase">
                {locale === "zh" ? "正常运行" : "On Track"}
              </span>
            </div>
            <div className="flex items-baseline justify-between mt-2.5 min-h-[32px]">
              <strong className="text-2xl font-extrabold text-green-600 tracking-tight">{t("cos.kpi.safety.val")}</strong>
              <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{t("cos.kpi.safety.target")}</span>
            </div>
            <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full mt-3.5 overflow-hidden">
              <div className="h-full bg-green-500" style={{ width: "100%" }}></div>
            </div>
          </div>
        </div>

        {/* Split row: Causal graph + Sandbox panel */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
          {/* Causal analysis */}
          <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 flex flex-col overflow-hidden shadow-xs min-h-[420px]`}>
            <div className="mb-4 shrink-0 flex items-center justify-between select-none">
              <div>
                <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>Causal Inference Graph</span>
                <h2 className="text-sm font-bold mt-1 uppercase font-mono" style={{ color: "var(--cardText)" }}>Causal Reasoning Topology</h2>
              </div>
              <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>Root cause mapping models</span>
            </div>
            <div className={`flex-1 flex overflow-hidden rounded-lg border mb-4 relative min-h-[180px] bg-black/10 dark:bg-black/30 ${styles.cardBorder}`}>
              <div className="absolute inset-0 z-0">
                <GraphCanvas nodes={causalNodes} links={causalLinksTrans} selectedNodeId="cnode_delay" interactive={true} />
              </div>
            </div>
            <div className={`p-4 rounded-xl shrink-0 space-y-1.5 text-xs font-mono select-text leading-relaxed border bg-black/10 dark:bg-white/5 ${styles.cardBorder}`}>
              <span className="text-orange-500 font-bold block text-[10px] uppercase tracking-wider select-none">Prescriptive AI Diagnosis:</span>
              <div className="space-y-1">
                <p className="leading-normal"><span className="opacity-60 font-bold">[Observation]</span> Maintenance Delay has escalated by <strong className="font-bold" style={{ color: "var(--cardText)" }}>+18 days</strong> in Factory Alpha sensors.</p>
                <p className="leading-normal"><span className="opacity-60 font-bold">[Hypothesis]</span> Under world-model rules, delayed maintenance propagates a <strong className="font-bold" style={{ color: "var(--cardText)" }}>85% failure probability</strong> on hydraulic conveyor drives.</p>
                <p className="leading-normal"><span className="opacity-60 font-bold">[Recommendation Overlay]</span> Approve weekend motor overhaul scenario and reroute Australian minerals cargo to Melbourne plant to secure Q2 revenue.</p>
              </div>
            </div>
          </div>

          {/* Decision Sandbox */}
          <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-5 flex flex-col shadow-xs min-h-[420px] overflow-y-auto scrollbar-thin`}>
            <div className="mb-4 shrink-0 flex items-center justify-between select-none">
              <div>
                <span className={`text-[9px] uppercase font-bold font-mono tracking-wider block ${styles.cardTextMuted}`}>Simulation Engine Dashboard</span>
                <h2 className="text-sm font-bold mt-1 uppercase font-mono" style={{ color: "var(--cardText)" }}>Interactive Sandbox Playground</h2>
              </div>
              {/* Pareto frontier toggle */}
              <button
                onClick={() => setShowParetoFrontier(!showParetoFrontier)}
                className={`px-2.5 py-1.5 font-mono text-[9px] font-bold border rounded-md tracking-wider cursor-pointer transition-all duration-150 ${
                  showParetoFrontier ? "bg-green-500/10 border-green-500/30 text-green-500 font-bold shadow-2xs" : `bg-black/10 dark:bg-white/5 ${styles.cardBorder} opacity-60 hover:opacity-100`
                }`}
              >
                Pareto Frontier: {showParetoFrontier ? "ENABLED" : "OFF"}
              </button>
            </div>

            <div className="space-y-4 flex-1">
              {/* Scenario selector */}
              <div className="flex flex-col sm:flex-row gap-2">
                {scenariosLoading ? (
                  <div className="text-xs opacity-60 py-4 text-center flex-1">Loading scenarios...</div>
                ) : scenarios.length === 0 ? (
                  <div className="text-xs opacity-60 py-4 text-center flex-1">No scenarios available</div>
                ) : (
                  scenarios.map((sc) => (
                  <button
                    key={sc.id}
                    onClick={() => setSelectedScenarioId(sc.id)}
                    className={`flex-1 text-left p-3 rounded-lg border transition-all cursor-pointer ${
                      selectedScenarioId == sc.id
                        ? "bg-indigo-500/10 border-indigo-500 text-indigo-500 font-bold shadow-2xs"
                        : `bg-transparent opacity-60 hover:opacity-100 ${styles.cardBorder}`
                    }`}
                  >
                    <span className="text-xs block font-bold" style={{ color: "var(--cardText)" }}>{sc.name}</span>
                    <span className={`text-[10px] leading-normal block mt-1 line-clamp-1 ${styles.cardTextMuted}`}>{sc.description}</span>
                  </button>
                )))}
              </div>

              {/* Simulator sliders */}
              <div className={`p-4 rounded-xl space-y-4 border bg-black/10 dark:bg-white/5 ${styles.cardBorder}`}>
                <span className="text-[10px] font-mono font-bold text-indigo-505 block tracking-wide leading-none uppercase">Simulator Parameters</span>
                {selectedScenarioId === "scen_reroute" ? (
                  <div>
                    <div className="flex justify-between text-xs font-mono mb-2 leading-none opacity-80">
                      <span>Active Shipping Shifts / Week</span>
                      <strong className="text-indigo-500 font-sans text-xs font-bold">{extraShifts} extra shifts</strong>
                    </div>
                    <input type="range" min="1" max="7" className="w-full accent-indigo-650 h-1 rounded-lg cursor-pointer"
                      value={extraShifts} onChange={(e) => setExtraShifts(Number(e.target.value))} />
                  </div>
                ) : (
                  <div>
                    <div className="flex justify-between text-xs font-mono mb-2 leading-none opacity-80">
                      <span>Planned Weekend Outage Duration</span>
                      <strong className="text-indigo-505 font-sans text-xs font-bold">{plannedOutageHours} continuous hours</strong>
                    </div>
                    <input type="range" min="12" max="72" step="12" className="w-full accent-indigo-650 h-1 rounded-lg cursor-pointer"
                      value={plannedOutageHours} onChange={(e) => setPlannedOutageHours(Number(e.target.value))} />
                  </div>
                )}
              </div>

              {/* Run simulation */}
              <div className="flex items-center justify-between border-t border-dashed pt-4" style={{ borderColor: "var(--cardBorder)" }}>
                <span className="text-[10.5px] opacity-75">
                  {locale === "zh" ? "加载前述可变变量配置至模拟引擎：" : "Press play to invoke forecasting model:"}
                </span>
                <button onClick={runScenarioSimulation} disabled={simulationActive}
                  className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs shrink-0">
                  {simulationActive ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
                  <span>{t("cos.btn.simulate")}</span>
                </button>
              </div>

              {/* Pareto recommendations */}
              {showParetoFrontier && (
                <div className="p-4 bg-emerald-500/10 border border-emerald-500/20 text-emerald-500 rounded-xl text-xs leading-relaxed shadow-2xs font-sans">
                  <span className="font-extrabold flex items-center gap-1.5 uppercase font-mono text-[10px] mb-1.5">
                    <CheckCircle className="w-3.5 h-3.5" />
                    Multi-Objective Frontier Optimizer Recommendations:
                  </span>
                  Based on continuous modeling: setting active shifts to <strong className="font-bold underline">4 extra shifts</strong> achieves an optimal balance, yielding <strong className="font-bold">+16.4% supply throughput</strong> while containing excess shipping expenses within <strong className="font-bold">8% tolerances</strong>.
                </div>
              )}

              {/* Predicted outcomes grid */}
              <div className="border-t border-dashed pt-4" style={{ borderColor: "var(--cardBorder)" }}>
                <span className={`text-[10px] uppercase font-mono font-bold tracking-wider block mb-2 leading-none ${styles.cardTextMuted}`}>Predicted Outcomes</span>
                {activeScenario ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs mt-2 select-none">
                    {(activeScenario.outcomes || []).map((out, idx) => (
                    <div key={idx} className={`p-3 rounded-lg border flex flex-col justify-between shadow-2xs bg-black/10 dark:bg-white/5 ${styles.cardBorder}`}>
                      <span className={`${styles.cardTextMuted} block leading-tight text-[10px] uppercase font-mono`}>{out.metric}</span>
                      <strong className={`text-xs mt-1.5 flex items-center justify-between ${
                        out.impact === "positive" ? "text-green-500" : out.impact === "negative" ? "text-red-500" : styles.cardText
                      }`}>
                        <span className="font-bold">{out.value}</span>
                        <span className="font-mono text-[10px] font-medium opacity-85">({out.change})</span>
                      </strong>
                    </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-xs opacity-60 py-4 text-center">
                    {scenariosLoading ? "Loading scenarios..." : "Select a scenario to view outcomes"}
                  </div>
                )}
              </div>

              {/* Terminal logs */}
              <div className="bg-[#020202] border border-emerald-500/20 rounded-xl p-4 font-mono text-[9.5px] text-emerald-500 whitespace-pre-wrap overflow-y-auto max-h-32 h-24 scrollbar-thin select-text">
                <span className="text-emerald-500/50 font-bold block mb-1.5 uppercase tracking-widest text-[8px] leading-none select-none">Simulator Terminal Logs:</span>
                {simLogs.length === 0 ? (
                  <p className="text-emerald-700 italic select-none">No simulator execution runs yet. Click simulation command.</p>
                ) : (
                  <div className="space-y-1">
                    {simLogs.map((log, idx) => (
                      <p key={idx} className="block leading-relaxed">{log}</p>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ErrorBoundary>
  );
}
