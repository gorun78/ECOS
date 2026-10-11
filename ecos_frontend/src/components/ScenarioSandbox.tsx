/**
 * ScenarioSandbox — 世界模型情景推演沙箱（可复用组件）
 *
 * <p>H-8 第一批（详设-09 §十一 登记表 / R-63 ②）：本文件原有 3 张 KPI 假卡
 * （$890M / 6.0% 停机率 / 硬编码进度宽度）、Prescriptive AI Diagnosis 段落、
 * `setTimeout` 假日志与 `+16.4%` 帕累托推荐块——全部为前端合成数字，已整体摘除
 * （架构铁律 v2.0 :14 禁前端造兜底数字）。现存数据面一律来自真实端点：</p>
 * <ul>
 *   <li>目标 / 因果链 / 情景清单：`/api/v1/ecos/world-model-graph/*`（经 `src/api` 单源 client）；</li>
 *   <li>情景推演：`POST /api/v1/world-model/scenarios`（经 `services/cognitiveClient`
 *       `runWorldModelScenarioSimulation`）。后端 `WorldModelController.java:33` 现为
 *       <b>真实 HTTP 501 + `data.stub=true`</b> 显式 stub ⇒ 本组件渲染既有「未实现」占位态
 *       （`CognitiveErrorState` stub 分支，`data-testid="cog-stub-placeholder"`），
 *       绝不本地合成推演结论。</li>
 * </ul>
 *
 * <p>降级/空/加载态复用项目既有约定：`CognitiveSkeleton` / `CognitiveEmptyState` /
 * `CognitiveErrorState` / `DegradationBanner`（详设-05 B.5 七态矩阵口径），不新造一套。</p>
 *
 * <p>规范：文案全走 `t()`（§六）；色彩全走 `useTheme()` token，无字面量色（§七）；
 * 图标仅 `lucide-react`；本文件 &lt; 800 行。</p>
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Play, RefreshCw } from "lucide-react";
import type { CausalLink, Goal, Scenario } from "../types";
import { fetchWorldCausalLinks, fetchWorldGoals, fetchWorldScenarios } from "../api";
import GraphCanvas from "./GraphCanvas";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import ErrorBoundary from "./common/ErrorBoundary";
import DegradationBanner from "./common/status/DegradationBanner";
import {
  CognitiveEmptyState,
  CognitiveErrorState,
  CognitiveSkeleton,
  cognitiveErrorTitleKey,
} from "../pages/scenario/cognition/CognitionStateViews";
import {
  CognitiveApiError,
  isCognitiveApiError,
} from "../services/cognitiveClient";
import { runWorldModelScenarioSimulation } from "../services/cognitive/outcomesApi";

export default function ScenarioSandbox() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── 真实数据通道 ─────────────────────────────────
  const [goals, setGoals] = useState<Goal[]>([]);
  const [causalLinks, setCausalLinks] = useState<CausalLink[]>([]);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [loading, setLoading] = useState(true);
  /** 清单通道故障（网络/HTTP）：仅收集真实失败原因，不伪造空态成功。 */
  const [loadErrors, setLoadErrors] = useState<string[]>([]);

  // ── 选择态 ───────────────────────────────────────
  const [selectedScenarioId, setSelectedScenarioId] = useState<number | string | null>(null);

  // ── 推演（真端点，未上线即 501 stub）──────────────
  const [simulating, setSimulating] = useState(false);
  const [simResult, setSimResult] = useState<Record<string, unknown> | null>(null);
  const [simError, setSimError] = useState<CognitiveApiError | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadErrors([]);
    const reasons: string[] = [];
    const [g, c, s] = await Promise.allSettled([
      fetchWorldGoals(),
      fetchWorldCausalLinks(),
      fetchWorldScenarios(),
    ]);
    if (g.status === "fulfilled") setGoals(g.value ?? []);
    else reasons.push(t("scenario.sandbox.err.goals"));
    if (c.status === "fulfilled") setCausalLinks(c.value ?? []);
    else reasons.push(t("scenario.sandbox.err.links"));
    if (s.status === "fulfilled") setScenarios(s.value ?? []);
    else reasons.push(t("scenario.sandbox.err.scenarios"));
    setLoadErrors(reasons);
    setLoading(false);
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (scenarios.length > 0 && selectedScenarioId == null) {
      setSelectedScenarioId(scenarios[0].id);
    }
  }, [scenarios, selectedScenarioId]);

  const activeScenario = useMemo(
    () =>
      selectedScenarioId != null
        ? scenarios.find((s) => s.id === selectedScenarioId) ?? scenarios[0]
        : undefined,
    [scenarios, selectedScenarioId],
  );

  // ── 因果拓扑（全部取服务端字段，名称缺失回落 ID 文本而非编造名）──
  const goalNameMap = useMemo(() => {
    const m = new Map<string, string>();
    goals.forEach((g) => {
      if (g.id != null) m.set(String(g.id), g.name || String(g.id));
    });
    return m;
  }, [goals]);

  const causalNodes = useMemo(() => {
    if (causalLinks.length === 0) return [];
    const nodeMap = new Map<string, { id: string; type: string; label: string; status: string }>();
    causalLinks.forEach((cl) => {
      const sid = String(cl.sourceGoalId ?? "");
      const tid = String(cl.targetGoalId ?? "");
      if (sid && !nodeMap.has(sid)) {
        nodeMap.set(sid, {
          id: `goal_${sid}`,
          type: "goal",
          label: cl.sourceGoalName || goalNameMap.get(sid) || sid,
          status: cl.relationshipType ?? cl.relationType ?? cl.type ?? "",
        });
      }
      if (tid && !nodeMap.has(tid)) {
        nodeMap.set(tid, {
          id: `goal_${tid}`,
          type: "goal",
          label: cl.targetGoalName || goalNameMap.get(tid) || tid,
          status: cl.relationshipType ?? cl.relationType ?? cl.type ?? "",
        });
      }
    });
    return Array.from(nodeMap.values());
  }, [causalLinks, goalNameMap]);

  const causalLinksTrans = useMemo(
    () =>
      causalLinks.map((cl, i) => ({
        id: String(cl.id ?? `cl_${i}`),
        source: `goal_${cl.sourceGoalId ?? ""}`,
        target: `goal_${cl.targetGoalId ?? ""}`,
        animated: true,
      })),
    [causalLinks],
  );

  // ── 推演：只调真端点，结果原样渲染；未上线走既有 stub 占位 ──
  const runSimulation = useCallback(async () => {
    if (!activeScenario) return;
    setSimulating(true);
    setSimError(null);
    setSimResult(null);
    try {
      const payload: Record<string, unknown> = {
        id: activeScenario.id,
        name: activeScenario.name,
        description: activeScenario.description,
        assumptions: activeScenario.assumptions ?? [],
      };
      const data = await runWorldModelScenarioSimulation(payload);
      setSimResult(data ?? {});
    } catch (e) {
      if (isCognitiveApiError(e)) setSimError(e);
      else setSimError(new CognitiveApiError({ code: "NETWORK", status: 0, message: String(e) }));
    } finally {
      setSimulating(false);
    }
  }, [activeScenario]);

  const simRows = useMemo(() => {
    if (!simResult) return [];
    return Object.entries(simResult).filter(([, v]) => v === null || typeof v !== "object");
  }, [simResult]);

  return (
    <ErrorBoundary>
      <div className="space-y-5">
        {/* 清单通道故障：既有降级条（原因来自真实 catch，不推断） */}
        {loadErrors.length > 0 && (
          <DegradationBanner active reasons={loadErrors} />
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">
          {/* 因果拓扑（服务端 goals / links 驱动） */}
          <section className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 flex flex-col overflow-hidden min-h-[360px]`}>
            <header className="mb-3 shrink-0 flex items-start justify-between gap-2">
              <div>
                <h2 className={`text-sm font-bold ${styles.cardText}`}>{t("scenario.sandbox.graph.title")}</h2>
                <p className={`text-[11px] ${styles.cardTextMuted} mt-0.5`}>{t("scenario.sandbox.graph.hint")}</p>
              </div>
              <button
                type="button"
                onClick={() => void load()}
                disabled={loading}
                aria-label={t("scenario.sandbox.refresh")}
                data-testid="sandbox-refresh"
                className={`shrink-0 inline-flex items-center gap-1 px-2 py-1 text-[10px] font-bold border rounded cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted} disabled:opacity-50`}
              >
                <RefreshCw size={11} className={loading ? "animate-spin" : ""} aria-hidden />
                {t("scenario.sandbox.refresh")}
              </button>
            </header>

            {loading ? (
              <CognitiveSkeleton rows={4} />
            ) : causalNodes.length === 0 ? (
              <CognitiveEmptyState labelKey="scenario.sandbox.graph.empty" testId="sandbox-graph-empty" />
            ) : (
              <div className={`flex-1 overflow-hidden rounded-lg border min-h-[220px] ${styles.cardBorder}`}>
                <GraphCanvas nodes={causalNodes} links={causalLinksTrans} interactive />
              </div>
            )}

            {!loading && causalLinks.length > 0 && (
              <p className={`mt-3 text-[10px] font-mono ${styles.cardTextMuted}`} data-testid="sandbox-link-count">
                {t("scenario.sandbox.graph.count", { n: causalLinks.length })}
              </p>
            )}
          </section>

          {/* 情景清单 + 推演入口 */}
          <section className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 flex flex-col min-h-[360px]`}>
            <header className="mb-3 shrink-0">
              <h2 className={`text-sm font-bold ${styles.cardText}`}>{t("scenario.sandbox.scenarios.title")}</h2>
              <p className={`text-[11px] ${styles.cardTextMuted} mt-0.5`}>{t("scenario.sandbox.scenarios.hint")}</p>
            </header>

            {loading ? (
              <CognitiveSkeleton rows={3} />
            ) : scenarios.length === 0 ? (
              <CognitiveEmptyState labelKey="scenario.sandbox.scenarios.empty" testId="sandbox-scenario-empty" />
            ) : (
              <div className="space-y-2 flex-1 overflow-y-auto">
                {scenarios.map((sc) => {
                  const selected = activeScenario?.id === sc.id;
                  return (
                    <button
                      key={String(sc.id)}
                      type="button"
                      onClick={() => setSelectedScenarioId(sc.id)}
                      aria-pressed={selected}
                      className={`w-full text-left p-3 rounded-lg border transition-colors cursor-pointer ${
                        selected ? `${styles.accentBorder} ${styles.accentBg}` : `${styles.cardBorder} ${styles.cardBg}`
                      }`}
                    >
                      <span className={`block text-xs font-bold ${styles.cardText}`}>{sc.name}</span>
                      {sc.description && (
                        <span className={`block text-[10px] mt-1 line-clamp-2 ${styles.cardTextMuted}`}>
                          {sc.description}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            )}

            {/* 情景既有事实（服务端字段；缺失即不渲染，禁补零） */}
            {!loading && activeScenario && (
              <div className="mt-3 space-y-2">
                {(activeScenario.assumptions ?? []).length > 0 && (
                  <dl className={`text-[11px] space-y-1 p-3 rounded-lg border ${styles.cardBorder}`}>
                    <dt className={`font-bold uppercase text-[10px] ${styles.cardTextMuted}`}>
                      {t("scenario.sandbox.assumptions")}
                    </dt>
                    {activeScenario.assumptions!.map((a, i) => (
                      <dd key={i} className={styles.cardText}>
                        {a.target} · {a.action} · {a.value}
                      </dd>
                    ))}
                  </dl>
                )}
                {(activeScenario.outcomes ?? []).length > 0 && (
                  <dl className={`text-[11px] space-y-1 p-3 rounded-lg border ${styles.cardBorder}`}>
                    <dt className={`font-bold uppercase text-[10px] ${styles.cardTextMuted}`}>
                      {t("scenario.sandbox.outcomes")}
                    </dt>
                    {activeScenario.outcomes!.map((o, i) => (
                      <dd key={i} className={`${styles.cardText} flex justify-between gap-2`}>
                        <span>{o.metric}</span>
                        <span className={`font-mono ${styles.cardTextMuted}`}>{o.value} ({o.change})</span>
                      </dd>
                    ))}
                  </dl>
                )}
              </div>
            )}

            {/* 推演触发（真 POST；后端 501 stub ⇒ 既有未实现占位态） */}
            <div className={`mt-4 pt-3 border-t ${styles.appBorder}`}>
              <button
                type="button"
                onClick={() => void runSimulation()}
                disabled={!activeScenario || simulating}
                data-testid="sandbox-simulate"
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentText} disabled:opacity-50`}
              >
                {simulating ? <RefreshCw size={12} className="animate-spin" aria-hidden /> : <Play size={12} aria-hidden />}
                {t("scenario.sandbox.simulate")}
              </button>

              {simError && (
                <div className="mt-3">
                  <CognitiveErrorState
                    error={simError}
                    titleKey={cognitiveErrorTitleKey(simError)}
                    onRetry={() => void runSimulation()}
                  />
                </div>
              )}

              {!simError && simResult && (
                <div className="mt-3" data-testid="sandbox-sim-result">
                  {simRows.length === 0 ? (
                    <CognitiveEmptyState labelKey="scenario.sandbox.sim.empty_fields" testId="sandbox-sim-empty" />
                  ) : (
                    <dl className={`text-[11px] space-y-1 p-3 rounded-lg border ${styles.cardBorder}`}>
                      {simRows.map(([k, v]) => (
                        <div key={k} className="flex justify-between gap-2">
                          <dt className={styles.cardTextMuted}>{k}</dt>
                          <dd className={`font-mono ${styles.cardText}`}>{String(v ?? "—")}</dd>
                        </div>
                      ))}
                    </dl>
                  )}
                </div>
              )}
            </div>
          </section>
        </div>
      </div>
    </ErrorBoundary>
  );
}
