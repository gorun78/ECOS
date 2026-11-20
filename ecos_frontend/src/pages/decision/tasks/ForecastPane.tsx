/**
 * ForecastPane — 「预测今年利润」（只读历史运行）。
 *
 * 数据来源唯一：`GET /api/v1/workspace/scenarios/{id}/runs` 的历史行。
 * 绝不调用 `POST /scenarios/{id}/runs`：该端点在不传 `series` 时会用
 * `Math.pow(0.97, i)` 合成伪时间序列（ScenarioRunService），属假数据；
 * 而前端没有任何端点能提供场景的真实历史指标序列。因此本 pane 只做回看。
 *
 * 无可用运行时的正确行为是空态 + 深链到场景工作台去真实发起预测。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { NoAccessError } from "../../../services/httpClient";
import { fetchScenarioRunHistory, type ScenarioRunRow } from "../../../services/cognitive/outcomesApi";
import logger from "../../../services/logger";
import type { AsyncState } from "../decisionTypes";
import { MiniTable, PaneState, compactJson, pickArray, pickRecord, pickString } from "../panesShared";

interface ForecastPoint {
  step: string;
  value: string;
  lower: string;
  upper: string;
}

interface ForecastView {
  run: ScenarioRunRow;
  metric: string | null;
  confidence: string | null;
  points: ForecastPoint[];
}

const FORECAST_STATUSES = new Set(["SUCCEEDED", "SUCCEEDED_DEGRADED"]);

function hasForecast(r: ScenarioRunRow): boolean {
  const type = (r.runType ?? "").toUpperCase();
  return type.includes("FORECAST") && FORECAST_STATUSES.has((r.status ?? "").toUpperCase());
}

function toPoints(forecast: Record<string, unknown>): ForecastPoint[] {
  const raw = pickArray(forecast, ["points", "series", "predictions"]);
  return raw
    .map((p) => {
      const rec = pickRecord({ v: p }, ["v"]);
      if (!rec) return null;
      return {
        step: pickString(rec, ["step", "t", "period", "index"]) ?? "—",
        value: pickString(rec, ["value", "point", "predicted", "y"]) ?? "—",
        lower: pickString(rec, ["lowerBound", "lower", "p10"]) ?? "—",
        upper: pickString(rec, ["upperBound", "upper", "p90"]) ?? "—",
      };
    })
    .filter((p): p is ForecastPoint => p !== null);
}

export interface ForecastPaneProps {
  scenarioId: string;
}

export default function ForecastPane({ scenarioId }: ForecastPaneProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<AsyncState<ForecastView[]>>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    try {
      const { rows } = await fetchScenarioRunHistory(scenarioId, 10);
      const views = rows
        .filter(hasForecast)
        .map<ForecastView | null>((run) => {
          const forecast = pickRecord(run as Record<string, unknown>, ["forecast", "forecast_result", "forecastResult"]);
          if (!forecast) return null;
          return {
            run,
            metric: pickString(forecast, ["metric", "metricName"]),
            confidence: pickString(forecast, ["confidence", "score"]),
            points: toPoints(forecast),
          };
        })
        .filter((v): v is ForecastView => v !== null);
      setState({ kind: "ready", value: views });
    } catch (e) {
      if (e instanceof NoAccessError) {
        setState({ kind: "denied" });
        return;
      }
      logger.error("decision-forecast", e);
      setState({ kind: "error", message: e instanceof Error ? e.message : String(e) });
    }
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  const latest = state.kind === "ready" ? state.value[0] : undefined;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className={`text-xs font-semibold ${styles.cardText}`}>
          {t("platform.decision.task.forecast.resultTitle")}
        </span>
        {latest?.metric && (
          <span className={`rounded border px-1.5 py-0.5 text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}>
            {latest.metric}
          </span>
        )}
        {latest?.confidence && (
          <span className={`rounded border px-1.5 py-0.5 text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}>
            {t("platform.decision.task.forecast.stat.confidence")} {latest.confidence}
          </span>
        )}
      </div>

      <PaneState
        state={state}
        isEmpty={(v) => v.length === 0}
        emptyTextKey="platform.decision.task.forecast.empty"
        onRetry={load}
      >
        {(views) => {
          const view = views[0];
          return (
            <div className="space-y-3">
              {view.points.length > 0 ? (
                <MiniTable head={[
                  t("platform.decision.task.forecast.col.step"),
                  t("platform.decision.task.forecast.col.value"),
                  t("platform.decision.task.forecast.col.lower"),
                  t("platform.decision.task.forecast.col.upper"),
                ]}>
                  {view.points.map((p, i) => (
                    <tr key={i} className={`border-t ${styles.cardBorder}`}>
                      <td className={`px-2.5 py-1.5 ${styles.cardText}`}>{p.step}</td>
                      <td className={`px-2.5 py-1.5 font-bold ${styles.cardText}`}>{p.value}</td>
                      <td className={`px-2.5 py-1.5 ${styles.cardTextMuted}`}>{p.lower}</td>
                      <td className={`px-2.5 py-1.5 ${styles.cardTextMuted}`}>{p.upper}</td>
                    </tr>
                  ))}
                </MiniTable>
              ) : (
                <p className={`text-[11px] break-words ${styles.cardTextMuted}`}>
                  {t("platform.decision.task.forecast.rawPrefix")}
                  {compactJson(view.run.forecast ?? view.run.forecast_result ?? view.run)}
                </p>
              )}
              <div className="flex flex-wrap items-center gap-3">
                <span className={`text-[11px] font-mono ${styles.cardTextMuted}`}>
                  run {view.run.runId.slice(0, 8)}
                  {view.run.createTime ? ` · ${view.run.createTime}` : ""}
                </span>
                <Link
                  to={`/workbench/${scenarioId}/forecast`}
                  className={`inline-flex items-center gap-1 text-[11px] font-bold ${styles.accentText}`}
                >
                  {t("platform.decision.task.forecast.openWorkbench")}
                  <ArrowRight size={12} />
                </Link>
              </div>
            </div>
          );
        }}
      </PaneState>
    </div>
  );
}
