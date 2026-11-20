/**
 * ActionsPane — 「跟踪决策执行」。
 *
 * 后端 `ScenarioActionController` 依赖 `DecisionGateway`，而全仓
 * **只有接口、零实现 Bean**，因此该端点在当前构建下必然回 503
 * `COGNITIVE_UNAVAILABLE`。这不是"加载失败"，而是能力尚未装配，
 * 两者必须分开渲染：前者重试有用，后者重试无用。
 *
 * 逾期数不展示：`ScenarioActionVO` 没有服务端计算的 overdue 字段，
 * 用本地时钟推断等于把猜测当真理。只按 status 分组计数。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { NoAccessError } from "../../../services/httpClient";
import {
  isScenarioActionError,
  listScenarioActions,
  type ScenarioActionVO,
} from "../../../services/scenarioActionClient";
import logger from "../../../services/logger";
import type { AsyncState } from "../decisionTypes";
import { MiniTable } from "../panesShared";

interface ActionsView {
  rows: ScenarioActionVO[];
  /** 按 status 分组的计数（status 缺失归到「未知」桶，不补默认值） */
  byStatus: Array<{ status: string; count: number }>;
  approved: number;
}

function groupByStatus(rows: ScenarioActionVO[]): ActionsView["byStatus"] {
  const map = new Map<string, number>();
  for (const r of rows) {
    const key = typeof r.status === "string" && r.status !== "" ? r.status : "—";
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  return [...map.entries()]
    .map(([status, count]) => ({ status, count }))
    .sort((a, b) => b.count - a.count);
}

export interface ActionsPaneProps {
  scenarioId: string;
}

export default function ActionsPane({ scenarioId }: ActionsPaneProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<AsyncState<ActionsView>>({ kind: "loading" });
  /** DecisionGateway 未装配：与普通失败分开，重试无意义 */
  const [unavailable, setUnavailable] = useState(false);

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    setUnavailable(false);
    try {
      const rows = await listScenarioActions(scenarioId);
      setState({
        kind: "ready",
        value: {
          rows,
          byStatus: groupByStatus(rows),
          approved: rows.filter((r) => Boolean(r.approver)).length,
        },
      });
    } catch (e) {
      if (e instanceof NoAccessError) {
        setState({ kind: "denied" });
        return;
      }
      if (isScenarioActionError(e) && e.code === "COGNITIVE_UNAVAILABLE") {
        setUnavailable(true);
        setState({ kind: "ready", value: { rows: [], byStatus: [], approved: 0 } });
        return;
      }
      logger.error("decision-actions", e);
      setState({ kind: "error", message: e instanceof Error ? e.message : String(e) });
    }
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (unavailable) {
    return (
      <div className={`rounded-md border px-3 py-3 text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
        <div className="font-semibold">{t("platform.decision.task.actions.unavailableTitle")}</div>
        <div className="mt-1">{t("platform.decision.task.actions.unavailableReason")}</div>
        <Link
          to={`/workbench/${scenarioId}/decisions`}
          className={`mt-2 inline-flex items-center gap-1 font-bold ${styles.accentText}`}
        >
          {t("platform.decision.task.actions.openWorkbench")}
          <ArrowRight size={12} />
        </Link>
      </div>
    );
  }

  if (state.kind === "loading" || state.kind === "idle") {
    return (
      <p className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>
        {t("platform.decision.task.loading")}
      </p>
    );
  }

  if (state.kind === "denied") {
    return (
      <p className={`py-6 text-center text-xs ${styles.warningText}`}>{t("platform.decision.task.denied")}</p>
    );
  }

  if (state.kind === "error") {
    return (
      <div className={`rounded-md border px-3 py-2 text-[11px] break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
        {t("platform.decision.loadFailed")}{state.message}
        <button
          type="button"
          onClick={load}
          className={`ml-2 rounded border px-2 py-0.5 cursor-pointer align-middle ${styles.inputBorder} ${styles.inputText}`}
        >
          {t("platform.decision.retry")}
        </button>
      </div>
    );
  }

  const view = state.value;
  if (view.rows.length === 0) {
    return <p className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.task.actions.empty")}</p>;
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {view.byStatus.map((g) => (
          <span
            key={g.status}
            className={`rounded border px-1.5 py-0.5 text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}
          >
            {g.status} {g.count}
          </span>
        ))}
        <span className={`text-[11px] ${styles.cardTextMuted}`}>
          {t("platform.decision.task.actions.approvedCount")} {view.approved}
        </span>
      </div>

      <MiniTable head={[
        t("platform.decision.task.actions.col.desc"),
        t("platform.decision.task.actions.col.owner"),
        t("platform.decision.task.actions.col.due"),
        t("platform.decision.task.actions.col.status"),
      ]}>
        {view.rows.map((r) => (
          <tr key={r.actionId} className={`border-t ${styles.cardBorder}`}>
            <td className={`px-2.5 py-1.5 font-sans break-words max-w-[22rem] ${styles.cardText}`}>
              {r.description ?? "—"}
            </td>
            <td className={`px-2.5 py-1.5 ${styles.cardTextMuted}`}>{r.ownerUserId ?? "—"}</td>
            <td className={`px-2.5 py-1.5 ${styles.cardTextMuted}`}>{r.dueDate ?? "—"}</td>
            <td className={`px-2.5 py-1.5 ${styles.cardTextMuted}`}>{r.status ?? "—"}</td>
          </tr>
        ))}
      </MiniTable>

      <Link
        to={`/workbench/${scenarioId}/decisions`}
        className={`inline-flex items-center gap-1 text-[11px] font-bold ${styles.accentText}`}
      >
        {t("platform.decision.task.actions.openWorkbench")}
        <ArrowRight size={12} />
      </Link>
    </div>
  );
}
