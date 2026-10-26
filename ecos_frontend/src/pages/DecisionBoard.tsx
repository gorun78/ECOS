/**
 * DecisionBoard — 决策面板（#/decision，2026-10-14 菜单重构·总览组唯一项）。
 *
 * 参考《ECOS前端可交互原型-v2.0》block4 经营决策域（Q-Panel + ScenarioList 形态），
 * 本 MVP 只落三个区段，全部真实数据 + fail-closed（禁 Mock/假空态/假绿）：
 *   A 引擎运行摘要 KPI 行（内核/认知/任务/场景 四卡，各自三态）
 *   B 活跃场景卡（GET /api/v1/workspace/scenarios，点卡进场景详情 L2）
 *   C 认知资产速览（GET /api/v1/cognitive/assets/catalog 词表计数，非业务实例清单）
 * 原型重型区段（预测向导 / 问 ECOS SSE / 行动跟踪 Stepper）不在本波范围。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Brain, Compass, Inbox, RefreshCw } from "lucide-react";
import { Icon } from '../components/LucideIcon';
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import { apiFetchData, apiHealth } from "../api";
import { apiTaskStats } from "../services/taskCenter";
import { fetchCognitiveAssetCatalog, fetchEngineHealth, type CognitiveAssetCatalog } from "../services/cognitiveClient";
import { workspaceScenariosUrl } from "../services/scenarioActionClient";
import logger from "../services/logger";

interface ScenarioSummary {
  id: string;
  name: string;
  businessGoal?: string;
  status?: string;
}

interface KpiState {
  loading: boolean;
  error: string | null;
}

/** 认知引擎公开健康 VO（CognitiveEngineOpenHealthVO 对应字段）。 */
interface EngineOpenHealth {
  status?: string;
  version?: string;
  uptimeMs?: number;
  components?: Record<string, { status?: string }>;
}

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function DecisionBoard() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 区段 A：四张 KPI 卡各自独立取数（单卡失败不影响其它卡，六态各自渲染）──
  const [kernel, setKernel] = useState<{ state: KpiState; status: string }>({
    state: { loading: true, error: null }, status: "",
  });
  const [cognitive, setCognitive] = useState<{ state: KpiState; health: EngineOpenHealth | null }>({
    state: { loading: true, error: null }, health: null,
  });
  const [task, setTask] = useState<{ state: KpiState; running: number; pending: number; total: number }>({
    state: { loading: true, error: null }, running: 0, pending: 0, total: 0,
  });
  const [scenarioCard, setScenarioCard] = useState<{ state: KpiState; count: number | null }>({
    state: { loading: true, error: null }, count: null,
  });

  // ── 区段 B：活跃场景 ──
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [sceneState, setSceneState] = useState<KpiState>({ loading: true, error: null });

  // ── 区段 C：认知资产词表 ──
  const [catalog, setCatalog] = useState<CognitiveAssetCatalog | null>(null);
  const [catalogState, setCatalogState] = useState<KpiState>({ loading: true, error: null });

  const loadAll = useCallback(async () => {
    // A1 内核：apiHealth() —— httpClient 单源通道（基址只定义于 httpClient；
    // 不可达时该单源本身 fail-closed 报 "DOWN"，与 App shell 同源口径）
    try {
      const status = await apiHealth();
      setKernel((p) => ({ status, state: { ...p.state, loading: false, error: null } }));
    } catch (e) {
      logger.error("decision-kernel", e);
      setKernel((p) => ({ status: "", state: { ...p.state, loading: false, error: errMsg(e) } }));
    }

    // A2 认知引擎：fetchEngineHealth()（services/cognitiveEngineApi 单源
    // GET {COGNITIVE_ENGINE_BASE}/health；status 三元派生 +
    // version/uptimeMs 真实下发）。注意 NOT /api/v1/cognitive/health（网关侧被
    // excludeFilters 禁用的冲突路径）。
    try {
      const data = await fetchEngineHealth();
      const health: EngineOpenHealth | null =
        data && typeof data === "object" ? (data as EngineOpenHealth) : null;
      setCognitive((p) => ({ health, state: { ...p.state, loading: false, error: null } }));
    } catch (e) {
      logger.error("decision-cognitive", e);
      setCognitive((p) => ({ health: null, state: { ...p.state, loading: false, error: errMsg(e) } }));
    }

    // A3 任务引擎
    try {
      const s = await apiTaskStats();
      setTask((p) => ({ total: s.total, running: s.running, pending: s.pending, state: { ...p.state, loading: false, error: null } }));
    } catch (e) {
      logger.error("decision-tasks", e);
      setTask((p) => ({ total: 0, running: 0, pending: 0, state: { ...p.state, loading: false, error: errMsg(e) } }));
    }

    // B 活跃场景（A4 卡计数同一次请求派生，不重复拉取）
    try {
      const d = await apiFetchData<ScenarioSummary[]>(workspaceScenariosUrl());
      const list = Array.isArray(d) ? d : [];
      setScenarios(list);
      setScenarioCard((p) => ({ count: list.length, state: { ...p.state, loading: false, error: null } }));
      setSceneState({ loading: false, error: null });
    } catch (e) {
      logger.error("decision-scenarios", e);
      setScenarios([]);
      const msg = errMsg(e);
      setScenarioCard((p) => ({ count: null, state: { ...p.state, loading: false, error: msg } }));
      setSceneState({ loading: false, error: msg });
    }

    // C 认知资产词表（只读词表/治理类/生命周期，非业务实例清单）
    try {
      const c = await fetchCognitiveAssetCatalog();
      setCatalog(c);
      setCatalogState({ loading: false, error: null });
    } catch (e) {
      logger.error("decision-catalog", e);
      setCatalog(null);
      setCatalogState({ loading: false, error: errMsg(e) });
    }
  }, []);

  useEffect(() => {
    void loadAll();
    const interval = setInterval(() => void loadAll(), 30000);
    return () => clearInterval(interval);
  }, [loadAll]);

  const kpiCard = (key: string, titleKey: string, icon: string, body: React.ReactNode) => (
    <div key={key} className={`rounded-lg border p-3 min-w-0 ${styles.cardBg} ${styles.cardBorder}`}>
      <div className={`flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide ${styles.cardTextMuted}`}>
        <Icon name={icon} size={13} />
        {t(titleKey)}
      </div>
      <div className="mt-2 text-sm font-bold font-mono">{body}</div>
    </div>
  );

  const statusPill = (status: string | undefined) => {
    const up = status === "UP";
    const cls = up ? `${styles.successBg} ${styles.successText}` : `${styles.dangerBg} ${styles.dangerText}`;
    return (
      <span className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${cls} ${styles.cardBorder}`}>
        {up ? t("platform.decision.kpi.up") : t("platform.decision.kpi.down")}
      </span>
    );
  };

  const retry = (): void => { void loadAll(); };

  const layerEntries = catalog ? Object.entries(catalog.layers ?? {}).map(([k, v]) => ({ k, v })) : [];
  const objectCount = catalog ? catalog.objects.length : 0;

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* 页头 */}
      <div className={`px-4 py-4 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0 flex items-center justify-between`}>
        <div className="flex items-center gap-2">
          <span className={`p-1.5 rounded-md flex items-center justify-center ${styles.accentBg} ${styles.cardText}`}>
            <Compass size={16} />
          </span>
          <div>
            <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>{t("platform.decision.title")}</h1>
            <p className={`text-[11px] ${styles.cardTextMuted} -mt-0.5`}>{t("platform.decision.subtitle")}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {/* R4 Mind 治理入口（P0-6 / B3-c）：列表+详情嵌套在 /decision 子路由，不另立并行入口树 */}
          <Link
            to="/decision/mind"
            className={`inline-flex items-center gap-1 text-[11px] font-bold rounded border px-2 py-1 ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
          >
            <Brain size={12} />
            {t("platform.mind.open")}
          </Link>
        <button
          type="button"
          onClick={retry}
          className={`p-1.5 rounded-md border ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80 cursor-pointer`}
          title={t("platform.decision.retry")}
        >
          <RefreshCw size={14} />
        </button>
        </div>
      </div>

      {/* 区段 A：KPI 行 */}
      <section className="p-4">
        <div className="grid gap-3 grid-cols-2 md:grid-cols-4">
          {kpiCard("kpi-kernel", "platform.decision.kpi.kernel", "Activity",
            kernel.state.loading ? <Spinner /> :
            kernel.state.error ? <ErrorBox detail={kernel.state.error} onRetry={retry} /> :
            statusPill(kernel.status))}

          {kpiCard("kpi-cognitive", "platform.decision.kpi.cognitive", "Brain",
            cognitive.state.loading ? <Spinner /> :
            cognitive.state.error ? <ErrorBox detail={cognitive.state.error} onRetry={retry} /> :
            (cognitive.health ? (
              <span className="flex items-center gap-2 flex-wrap">
                {statusPill(cognitive.health.status)}
                {cognitive.health.version && <span className={`text-[11px] font-bold`}>{cognitive.health.version}</span>}
              </span>
            ) : <SpanDash />))}

          {kpiCard("kpi-task", "platform.decision.kpi.task", "ListChecks",
            task.state.loading ? <Spinner /> :
            task.state.error ? <ErrorBox detail={task.state.error} onRetry={retry} /> :
            (task.total === 0 && task.running === 0 ? <SpanDash textOnly={t("platform.resource.countUnknown")} /> : (
              <span className="flex items-center gap-2 flex-wrap">
                {task.running > 0 && <span className={styles.successText}>{task.running} {t("platform.decision.kpi.running")}</span>}
                {task.pending > 0 && <span className={styles.warningText}>{task.pending} {t("platform.decision.kpi.pending")}</span>}
                <span className={`${styles.cardTextMuted} text-[11px]`}>{task.total} {t("sidebar.footer.total")}</span>
              </span>
            )))}

          {kpiCard("kpi-scene", "platform.decision.kpi.scenarios", "Inbox",
            scenarioCard.state.loading ? <Spinner /> :
            scenarioCard.state.error ? <ErrorBox detail={scenarioCard.state.error} onRetry={retry} /> :
            (scenarioCard.count === null ? <SpanDash /> : <span>{scenarioCard.count}</span>))}
        </div>
      </section>

      {/* 区段 B：活跃场景 */}
      <section className="px-4 pb-4">
        <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder} p-4`}>
          <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-1`}>{t("platform.decision.scenes")}</h2>
          <p className={`text-[11px] ${styles.cardTextMuted} mb-3`}>{t("platform.decision.scenes.desc")}</p>

          {sceneState.loading ? (
            <div className="flex items-center gap-2 py-6 justify-center text-xs">
              <Spinner size="lg" />
            </div>
          ) : sceneState.error ? (
            <ErrorBox detail={sceneState.error} onRetry={retry} boxed />
          ) : scenarios.length === 0 ? (
            <div className={`py-8 text-center border rounded-lg ${styles.cardBorder}`}>
              <Inbox size={22} className={`mb-2 mx-auto ${styles.cardTextMuted}`} />
              <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.decision.scenes.empty")}</p>
            </div>
          ) : (
            <div className="grid gap-3 grid-cols-1 md:grid-cols-2 xl:grid-cols-3">
              {scenarios.map((s) => (
                <Link
                  key={s.id}
                  to={`/workbench/${s.id}`}
                  className={`block rounded-lg border p-3 min-w-0 transition-all hover:opacity-90 cursor-pointer ${styles.cardBg} ${styles.cardBorder}`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <h3 className={`text-sm font-bold ${styles.cardText} leading-snug`}>{s.name}</h3>
                    {s.status ? (
                      <span className={`shrink-0 text-[10px] font-mono border rounded px-1.5 py-0.5 ${styles.accentText} ${styles.accentBorder}`}>
                        {s.status}
                      </span>
                    ) : null}
                  </div>
                  {s.businessGoal && (
                    <p className={`mt-1 text-[11px] ${styles.cardTextMuted} line-clamp-2`}>{s.businessGoal}</p>
                  )}
                  <span className={`mt-2 inline-flex items-center gap-1 text-[11px] font-bold ${styles.accentText}`}>
                    {t("platform.decision.scenes.open")}
                    <ArrowRight size={12} />
                  </span>
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* 区段 C：认知资产速览（服务端下发的词表/治理类/生命周期，非实例清单） */}
      <section className="px-4 pb-6">
        <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder} p-4`}>
          <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-1`}>{t("platform.decision.assets")}</h2>
          <p className={`text-[11px] ${styles.cardTextMuted} mb-3`}>{t("platform.decision.assets.desc")}</p>

          {catalogState.loading ? (
            <div className="flex items-center gap-2 py-6 justify-center text-xs">
              <Spinner size="lg" />
            </div>
          ) : catalogState.error ? (
            <ErrorBox detail={catalogState.error} onRetry={retry} boxed />
          ) : !catalog || (layerEntries.length === 0 && objectCount === 0) ? (
            <p className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.assets.empty")}</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {layerEntries.map(({ k, v }) => (
                <span key={k} className={`inline-flex items-center gap-1.5 rounded border px-2.5 py-1 text-[11px] font-mono ${styles.cardTextMuted} ${styles.cardBorder}`}>
                  <Brain size={12} />
                  <span className={`${styles.cardText} font-semibold`}>{k}</span>
                  <span>{v}</span>
                </span>
              ))}
              {objectCount > 0 && (
                <span className={`inline-flex items-center gap-1.5 rounded border px-2.5 py-1 text-[11px] font-mono ${styles.cardTextMuted} ${styles.cardBorder}`}>
                  C {objectCount}
                </span>
              )}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function Spinner({ size = "sm" }: { size?: "sm" | "lg" }) {
  const cls = size === "lg" ? "h-5 w-5" : "h-3.5 w-3.5";
  return <span className={`${cls} inline-block animate-spin rounded-full border-2 border-current border-t-transparent opacity-60`} />;
}

function SpanDash({ textOnly }: { textOnly?: string }) {
  const { styles } = useTheme();
  return <span className={`${styles.cardTextMuted} text-xs`}>{textOnly ?? "—"}</span>;
}

function ErrorBox({ detail, onRetry, boxed = false }: { detail: string; onRetry: () => void; boxed?: boolean }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div className={`rounded-md px-3 py-2 border text-[11px] break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder} ${boxed ? "py-6 text-center" : ""}`}>
      <span>{t("platform.decision.loadFailed")}{detail}</span>
      <button type="button" onClick={onRetry} className={`ml-2 rounded border px-2 py-0.5 cursor-pointer align-middle ${styles.inputBorder} ${styles.inputText}`}>
        {t("platform.decision.retry")}
      </button>
    </div>
  );
}
