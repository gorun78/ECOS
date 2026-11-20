/**
 * ComparePane — 「比较经营方案」（双 run 数值对比）。
 *
 * 真实端点只有 `GET /api/v1/workspace/forecast-runs/compare?baselineRunId=&candidateRunId=`。
 * 注意 `POST /api/v1/cognitive/scenario/compare` 是模拟器跑分（入参 {name,variables}），
 * 与 runId 无关，不可用于方案对比。
 *
 * runId 来源唯一：场景运行历史里 `SUCCEEDED`+`FORECAST` 的行。后端没有
 * forecast-run 列表端点，因此不能凭空列 run。
 *
 * 可用 run 少于 2 条时按钮禁用且**不发请求**——比较一件事，
 * 缺数据就如实说缺，不拿同一 run 自比自演。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { NoAccessError } from "../../../services/httpClient";
import { fetchScenarioRunHistory, type ScenarioRunRow } from "../../../services/cognitive/outcomesApi";
import { compareForecastRuns, type ForecastRunCompareResult } from "../../../services/decisionWorkspaceApi";
import logger from "../../../services/logger";
import type { AsyncState } from "../decisionTypes";
import { MiniTable, PaneState, compactJson, pickRecord, pickString } from "../panesShared";

const COMPARABLE = new Set(["SUCCEEDED", "SUCCEEDED_DEGRADED"]);

function comparableRuns(rows: ScenarioRunRow[]): ScenarioRunRow[] {
  return rows.filter((r) => {
    const type = (r.runType ?? "").toUpperCase();
    return type.includes("FORECAST") && COMPARABLE.has((r.status ?? "").toUpperCase());
  });
}

function runLabel(r: ScenarioRunRow): string {
  const t = r.createTime ? ` · ${r.createTime}` : "";
  return `${r.runId.slice(0, 8)}${t}`;
}

/** 后端 diff 里为 true 的键=两侧值不同，用户最需要看到这些。 */
function diffEntries(diff: Record<string, unknown> | undefined): Array<{ key: string; differs: boolean }> {
  if (!diff) return [];
  return Object.entries(diff)
    .filter(([k]) => k.endsWith("Differs"))
    .map(([k, v]) => ({ key: k.replace(/Differs$/, ""), differs: v === true }));
}

export interface ComparePaneProps {
  scenarioId: string;
}

export default function ComparePane({ scenarioId }: ComparePaneProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [runs, setRuns] = useState<AsyncState<ScenarioRunRow[]>>({ kind: "loading" });
  const [baselineId, setBaselineId] = useState("");
  const [candidateId, setCandidateId] = useState("");
  const [result, setResult] = useState<AsyncState<ForecastRunCompareResult>>({ kind: "idle" });

  const load = useCallback(async () => {
    setRuns({ kind: "loading" });
    try {
      const { rows } = await fetchScenarioRunHistory(scenarioId, 20);
      const list = comparableRuns(rows);
      setRuns({ kind: "ready", value: list });
      if (list.length >= 2) {
        setBaselineId(list[list.length - 1].runId);
        setCandidateId(list[0].runId);
      } else if (list.length === 1) {
        setBaselineId(list[0].runId);
        setCandidateId("");
      } else {
        setBaselineId("");
        setCandidateId("");
      }
    } catch (e) {
      if (e instanceof NoAccessError) {
        setRuns({ kind: "denied" });
        return;
      }
      logger.error("decision-compare-runs", e);
      setRuns({ kind: "error", message: e instanceof Error ? e.message : String(e) });
    }
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  const canCompare = baselineId !== "" && candidateId !== "" && baselineId !== candidateId;

  const run = async () => {
    if (!canCompare) return;
    setResult({ kind: "loading" });
    try {
      const data = await compareForecastRuns(baselineId, candidateId);
      setResult({ kind: "ready", value: data });
    } catch (e) {
      if (e instanceof NoAccessError) {
        setResult({ kind: "denied" });
        return;
      }
      logger.error("decision-compare", e);
      const msg = e instanceof Error ? e.message : String(e);
      // 501 STUB_CAPABILITY：能力未上线，与"加载失败"是两回事
      setResult({ kind: "error", message: msg });
    }
  };

  const diff = useMemo(
    () => diffEntries(result.kind === "ready" ? (result.value.diff as Record<string, unknown> | undefined) : undefined),
    [result],
  );
  const stubCapability = result.kind === "error" && /STUB_CAPABILITY|501/i.test(result.message);

  return (
    <div className="space-y-3">
      <PaneState
        state={runs}
        isEmpty={(v) => v.length < 2}
        emptyTextKey="platform.decision.task.compare.empty"
        onRetry={load}
      >
        {(list) => (
          <div className="space-y-3">
            <div className="flex flex-col sm:flex-row gap-2">
              <label className={`flex-1 text-[11px] ${styles.cardTextMuted}`}>
                {t("platform.decision.task.compare.stat.baseline")}
                <select
                  value={baselineId}
                  onChange={(e) => setBaselineId(e.target.value)}
                  className={`mt-1 w-full rounded border px-2 py-1 text-xs font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                >
                  {list.map((r) => <option key={r.runId} value={r.runId}>{runLabel(r)}</option>)}
                </select>
              </label>
              <label className={`flex-1 text-[11px] ${styles.cardTextMuted}`}>
                {t("platform.decision.task.compare.stat.candidate")}
                <select
                  value={candidateId}
                  onChange={(e) => setCandidateId(e.target.value)}
                  className={`mt-1 w-full rounded border px-2 py-1 text-xs font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                >
                  {list.map((r) => <option key={r.runId} value={r.runId}>{runLabel(r)}</option>)}
                </select>
              </label>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={run}
                disabled={!canCompare || result.kind === "loading"}
                className={`rounded px-3 py-1.5 text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed ${styles.accentBg} ${styles.accentSolidText}`}
              >
                {t("platform.decision.task.compare.run")}
              </button>
              {!canCompare && (
                <span className={`text-[11px] ${styles.warningText}`}>
                  {t("platform.decision.task.compare.sameRun")}
                </span>
              )}
            </div>

            {result.kind === "loading" && (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.decision.task.loading")}</p>
            )}
            {result.kind === "denied" && (
              <p className={`text-[11px] ${styles.warningText}`}>{t("platform.decision.task.denied")}</p>
            )}
            {result.kind === "error" && (
              <div className={`rounded-md border px-3 py-2 text-[11px] break-words ${
                stubCapability
                  ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
                  : `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
              }`}>
                {stubCapability
                  ? t("platform.decision.task.compare.notLive")
                  : `${t("platform.decision.loadFailed")}${result.message}`}
              </div>
            )}
            {result.kind === "ready" && (
              <div className="space-y-2">
                {diff.length > 0 && (
                  <MiniTable head={[t("platform.decision.task.compare.col.field"), t("platform.decision.task.compare.col.state")]}>
                    {diff.map((d) => (
                      <tr key={d.key} className={`border-t ${styles.cardBorder}`}>
                        <td className={`px-2.5 py-1.5 font-sans ${styles.cardText}`}>{d.key}</td>
                        <td className={`px-2.5 py-1.5 font-sans ${d.differs ? styles.warningText : styles.cardTextMuted}`}>
                          {d.differs
                            ? t("platform.decision.task.compare.differs")
                            : t("platform.decision.task.compare.same")}
                        </td>
                      </tr>
                    ))}
                  </MiniTable>
                )}
                {diff.length === 0 && (
                  <p className={`text-[11px] break-words font-mono ${styles.cardTextMuted}`}>
                    {pickString(pickRecord({ v: result.value }, ["v"]) ?? {}, ["summary"]) ?? compactJson(result.value, 400)}
                  </p>
                )}
              </div>
            )}
          </div>
        )}
      </PaneState>
    </div>
  );
}
