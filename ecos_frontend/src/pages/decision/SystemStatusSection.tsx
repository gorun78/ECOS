/**
 * SystemStatusSection — 决策工作空间底部「系统状态」区。
 *
 * 本组件是原 `DecisionBoard.tsx` 区段 A（引擎 KPI 四卡）与区段 C（认知资产速览）
 * 的原样搬迁（2026-11-20 原型化改造）。搬迁不改取数逻辑与节拍：
 * 五条真实请求各自独立三态、单卡失败不污染其它卡、30s 轮询。
 *
 * 原区段 B（活跃场景卡网格）未搬迁：场景选择已迁入任务详情面板的下拉，
 * 网格形态重复且占用首屏。
 *
 * 真值纪律：四卡各自 fail-closed，`apiHealth` 不可达时单源本身回 "DOWN"；
 * 任务卡 `total===0 && running===0` 显示「计数未知」而非 0（与后端 runtime-task
 * 未装配时的语义一致）。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Brain, Inbox, RefreshCw } from "lucide-react";
import { Icon } from "../../components/LucideIcon";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { apiFetchData, apiHealth } from "../../api";
import { apiTaskStats } from "../../services/taskCenter";
import {
  fetchCognitiveAssetCatalog,
  fetchEngineHealth,
  type CognitiveAssetCatalog,
} from "../../services/cognitiveClient";
import logger from "../../services/logger";
import { workspaceScenariosUrl } from "../../services/scenarioActionClient";
import type { AsyncState } from "./decisionTypes";

/** 认知引擎公开健康 VO（CognitiveEngineOpenHealthVO 对应字段）。 */
interface EngineOpenHealth {
  status?: string;
  version?: string;
  uptimeMs?: number;
  components?: Record<string, { status?: string }>;
}

interface KernelValue {
  status: string;
}
interface TaskValue {
  running: number;
  pending: number;
  total: number;
}

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function SystemStatusSection() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [kernel, setKernel] = useState<AsyncState<KernelValue>>({ kind: "loading" });
  const [cognitive, setCognitive] = useState<AsyncState<EngineOpenHealth | null>>({ kind: "loading" });
  const [task, setTask] = useState<AsyncState<TaskValue>>({ kind: "loading" });
  const [sceneCount, setSceneCount] = useState<AsyncState<number>>({ kind: "loading" });
  const [catalog, setCatalog] = useState<AsyncState<CognitiveAssetCatalog | null>>({ kind: "loading" });

  const loadAll = useCallback(async () => {
    try {
      const status = await apiHealth();
      setKernel({ kind: "ready", value: { status } });
    } catch (e) {
      logger.error("decision-kernel", e);
      setKernel({ kind: "error", message: errMsg(e) });
    }

    try {
      const data = await fetchEngineHealth();
      const health = data && typeof data === "object" ? (data as EngineOpenHealth) : null;
      setCognitive({ kind: "ready", value: health });
    } catch (e) {
      logger.error("decision-cognitive", e);
      setCognitive({ kind: "error", message: errMsg(e) });
    }

    try {
      const s = await apiTaskStats();
      setTask({ kind: "ready", value: { total: s.total, running: s.running, pending: s.pending } });
    } catch (e) {
      logger.error("decision-tasks", e);
      setTask({ kind: "error", message: errMsg(e) });
    }

    try {
      const list = await apiFetchData<unknown[]>(workspaceScenariosUrl());
      setSceneCount({ kind: "ready", value: Array.isArray(list) ? list.length : 0 });
    } catch (e) {
      logger.error("decision-scenarios", e);
      setSceneCount({ kind: "error", message: errMsg(e) });
    }

    try {
      const c = await fetchCognitiveAssetCatalog();
      setCatalog({ kind: "ready", value: c });
    } catch (e) {
      logger.error("decision-catalog", e);
      setCatalog({ kind: "error", message: errMsg(e) });
    }
  }, []);

  useEffect(() => {
    void loadAll();
    const interval = setInterval(() => void loadAll(), 30000);
    return () => clearInterval(interval);
  }, [loadAll]);

  const retry = (): void => {
    void loadAll();
  };

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

  const layerEntries = catalog.kind === "ready" && catalog.value
    ? Object.entries(catalog.value.layers ?? {}).map(([k, v]) => ({ k, v }))
    : [];
  const objectCount = catalog.kind === "ready" && catalog.value ? catalog.value.objects.length : 0;

  return (
    <section className="space-y-4">
      <div>
        <h2 className={`text-sm font-bold ${styles.appText}`}>{t("platform.decision.system.title")}</h2>
        <p className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.decision.system.desc")}</p>
      </div>

      <div className="grid gap-3 grid-cols-2 md:grid-cols-4">
        {kpiCard("kpi-kernel", "platform.decision.kpi.kernel", "Activity",
          kernel.kind === "loading" ? <Spinner /> :
          kernel.kind === "error" ? <ErrorBox detail={kernel.message} onRetry={retry} /> :
          statusPill(kernel.kind === "ready" ? kernel.value.status : undefined))}

        {kpiCard("kpi-cognitive", "platform.decision.kpi.cognitive", "Brain",
          cognitive.kind === "loading" ? <Spinner /> :
          cognitive.kind === "error" ? <ErrorBox detail={cognitive.message} onRetry={retry} /> :
          (cognitive.kind === "ready" && cognitive.value ? (
            <span className="flex items-center gap-2 flex-wrap">
              {statusPill(cognitive.value.status)}
              {cognitive.value.version && <span className="text-[11px] font-bold">{cognitive.value.version}</span>}
            </span>
          ) : <SpanDash />))}

        {kpiCard("kpi-task", "platform.decision.kpi.task", "ListChecks",
          task.kind === "loading" ? <Spinner /> :
          task.kind === "error" ? <ErrorBox detail={task.message} onRetry={retry} /> :
          (task.kind === "ready" && task.value.total === 0 && task.value.running === 0
            ? <SpanDash textOnly={t("platform.resource.countUnknown")} />
            : task.kind === "ready" ? (
              <span className="flex items-center gap-2 flex-wrap">
                {task.value.running > 0 && <span className={styles.successText}>{task.value.running} {t("platform.decision.kpi.running")}</span>}
                {task.value.pending > 0 && <span className={styles.warningText}>{task.value.pending} {t("platform.decision.kpi.pending")}</span>}
                <span className={`${styles.cardTextMuted} text-[11px]`}>{task.value.total} {t("sidebar.footer.total")}</span>
              </span>
            ) : <SpanDash />))}

        {kpiCard("kpi-scene", "platform.decision.kpi.scenarios", "Inbox",
          sceneCount.kind === "loading" ? <Spinner /> :
          sceneCount.kind === "error" ? <ErrorBox detail={sceneCount.message} onRetry={retry} /> :
          sceneCount.kind === "ready" ? <span>{sceneCount.value}</span> : <SpanDash />)}
      </div>

      <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder} p-4`}>
        <div className="flex items-start justify-between gap-2 mb-1">
          <div>
            <h3 className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>{t("platform.decision.assets")}</h3>
            <p className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.decision.assets.desc")}</p>
          </div>
          <button
            type="button"
            onClick={retry}
            className={`shrink-0 p-1.5 rounded-md border ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80 cursor-pointer`}
            title={t("platform.decision.retry")}
          >
            <RefreshCw size={13} />
          </button>
        </div>

        {catalog.kind === "loading" ? (
          <div className="flex items-center gap-2 py-6 justify-center text-xs"><Spinner size="lg" /></div>
        ) : catalog.kind === "error" ? (
          <ErrorBox detail={catalog.message} onRetry={retry} boxed />
        ) : catalog.kind !== "ready" || !catalog.value || (layerEntries.length === 0 && objectCount === 0) ? (
          <p className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.assets.empty")}</p>
        ) : (
          <div className="flex flex-wrap gap-2 mt-3">
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
