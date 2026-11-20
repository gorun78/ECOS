/**
 * RiskPane — 「找出经营风险」。
 *
 * 后端**没有** RiskController，风险只以子字段形式存在。本 pane 分两条链：
 *
 * ① 默认链（无需用户输入）：读取该场景已发布蓝图里的 `risks[]`。
 *    这是真实登记项，但**不是实时推演**，因此必须挂降级说明条，
 *    不能让它看起来像实时风控结果。
 *
 * ② 推演链（opt-in，默认收起）：`POST /api/v1/cognitive/counterfactual`。
 *    该端点必填 `domain` + `variableName`，且 belief 记录缺失即 404；
 *    而全仓**没有任何端点能枚举 belief domain**。因此绝不能让页面"自动猜"一个
 *    domain 去打——那只会产出必然 404 的请求或错误结论。改成用户手工指定后运行。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { NoAccessError } from "../../../services/httpClient";
import { listBlueprints, type ScenarioBlueprintVO } from "../../../services/scenarioBlueprintClient";
import { runCounterfactualSim } from "../../../services/decisionWorkspaceApi";
import logger from "../../../services/logger";
import type { AsyncState } from "../decisionTypes";
import { MiniTable, PaneState, compactJson, pickArray, pickRecord, pickString } from "../panesShared";

const RELEASED = new Set(["RELEASED", "ACTIVE", "APPROVED", "RUNNING"]);

interface RiskRow {
  title: string;
  detail: string;
}

interface RiskView {
  blueprint: ScenarioBlueprintVO;
  rows: RiskRow[];
}

function toRiskRow(raw: unknown): RiskRow {
  const rec = pickRecord({ v: raw }, ["v"]);
  if (!rec) return { title: "—", detail: compactJson(raw) };
  return {
    title: pickString(rec, ["title", "name", "risk", "id", "type"]) ?? "—",
    detail: pickString(rec, ["description", "detail", "impact", "mitigation", "note"]) ?? compactJson(rec),
  };
}

/** 取版本号最大且处于已发布态的蓝图；无则取版本最大的任意蓝图。 */
function pickBlueprint(list: ScenarioBlueprintVO[]): ScenarioBlueprintVO | null {
  if (list.length === 0) return null;
  const byVersionDesc = [...list].sort((a, b) => Number(b.version ?? 0) - Number(a.version ?? 0));
  return byVersionDesc.find((b) => RELEASED.has((b.status ?? "").toUpperCase())) ?? byVersionDesc[0];
}

export interface RiskPaneProps {
  scenarioId: string;
}

export default function RiskPane({ scenarioId }: RiskPaneProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<AsyncState<RiskView | null>>({ kind: "loading" });
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [domain, setDomain] = useState("");
  const [variable, setVariable] = useState("");
  const [sim, setSim] = useState<AsyncState<Record<string, unknown>>>({ kind: "idle" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    try {
      const list = await listBlueprints(scenarioId);
      const blueprint = pickBlueprint(list);
      setState({
        kind: "ready",
        value: blueprint
          ? { blueprint, rows: (blueprint.risks ?? []).map(toRiskRow) }
          : null,
      });
    } catch (e) {
      if (e instanceof NoAccessError) {
        setState({ kind: "denied" });
        return;
      }
      logger.error("decision-risk", e);
      setState({ kind: "error", message: e instanceof Error ? e.message : String(e) });
    }
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  const runSim = async () => {
    const d = domain.trim();
    const v = variable.trim();
    if (!d || !v) return;
    setSim({ kind: "loading" });
    try {
      const result = await runCounterfactualSim({ scenarioId, domain: d, variableName: v });
      setSim({ kind: "ready", value: pickRecord({ v: result }, ["v"]) ?? {} });
    } catch (e) {
      if (e instanceof NoAccessError) {
        setSim({ kind: "denied" });
        return;
      }
      logger.error("decision-risk-sim", e);
      setSim({ kind: "error", message: e instanceof Error ? e.message : String(e) });
    }
  };

  const riskMetrics = sim.kind === "ready" ? pickRecord(sim.value, ["riskMetrics"]) : null;

  return (
    <div className="space-y-3">
      <div className={`rounded-md border px-3 py-2 text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
        {t("platform.decision.task.risk.degradedHint")}
      </div>

      <PaneState
        state={state}
        isEmpty={(v) => v === null}
        emptyTextKey="platform.decision.task.risk.empty"
        onRetry={load}
      >
        {(view) => (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded border px-1.5 py-0.5 text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}>
                v{view.blueprint.version ?? "—"}
              </span>
              <span className={`rounded border px-1.5 py-0.5 text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}>
                {view.blueprint.status ?? "—"}
              </span>
              <span className={`text-[11px] ${styles.cardTextMuted}`}>
                {t("platform.decision.task.risk.stat.count")} {view.rows.length}
              </span>
            </div>
            {view.rows.length > 0 ? (
              <MiniTable head={[t("platform.decision.task.risk.col.title"), t("platform.decision.task.risk.col.detail")]}>
                {view.rows.map((r, i) => (
                  <tr key={i} className={`border-t ${styles.cardBorder}`}>
                    <td className={`px-2.5 py-1.5 font-sans font-semibold ${styles.cardText}`}>{r.title}</td>
                    <td className={`px-2.5 py-1.5 font-sans break-words ${styles.cardTextMuted}`}>{r.detail}</td>
                  </tr>
                ))}
              </MiniTable>
            ) : (
              <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.decision.task.risk.noRisksOnBlueprint")}</p>
            )}
          </div>
        )}
      </PaneState>

      {/* 推演链：opt-in。domain 无端点可发现，必须人工指定 */}
      <div className={`rounded-lg border ${styles.cardBorder}`}>
        <button
          type="button"
          onClick={() => setAdvancedOpen((v) => !v)}
          className={`w-full flex items-center gap-1.5 px-3 py-2 text-[11px] font-semibold cursor-pointer ${styles.cardTextMuted}`}
        >
          {advancedOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
          {t("platform.decision.task.risk.advanced")}
        </button>
        {advancedOpen && (
          <div className="px-3 pb-3 space-y-2">
            <p className={`text-[11px] ${styles.cardTextMuted}`}>
              {t("platform.decision.task.risk.advancedHint")}
            </p>
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                value={domain}
                onChange={(e) => setDomain(e.target.value)}
                placeholder={t("platform.decision.task.risk.domain")}
                className={`flex-1 rounded border px-2 py-1 text-xs ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
              <input
                value={variable}
                onChange={(e) => setVariable(e.target.value)}
                placeholder={t("platform.decision.task.risk.variable")}
                className={`flex-1 rounded border px-2 py-1 text-xs ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
              <button
                type="button"
                onClick={runSim}
                disabled={!domain.trim() || !variable.trim() || sim.kind === "loading"}
                className={`rounded px-3 py-1 text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed ${styles.accentBg} ${styles.accentSolidText}`}
              >
                {t("platform.decision.task.risk.runSim")}
              </button>
            </div>

            {sim.kind === "loading" && (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.decision.task.loading")}</p>
            )}
            {sim.kind === "denied" && (
              <p className={`text-[11px] ${styles.warningText}`}>{t("platform.decision.task.denied")}</p>
            )}
            {sim.kind === "error" && (
              <p className={`text-[11px] break-words ${styles.dangerText}`}>{sim.message}</p>
            )}
            {sim.kind === "ready" && (
              riskMetrics ? (
                <div className={`grid gap-2 grid-cols-2 sm:grid-cols-4`}>
                  {[
                    ["expectedBenefit", "platform.decision.task.risk.metric.expectedBenefit"],
                    ["lossProbability", "platform.decision.task.risk.metric.lossProbability"],
                    ["maxDrawdown", "platform.decision.task.risk.metric.maxDrawdown"],
                    ["volatilityRange", "platform.decision.task.risk.metric.volatilityRange"],
                  ].map(([key, labelKey]) => (
                    <div key={key} className={`rounded border p-2 ${styles.cardBorder}`}>
                      <small className={`block text-[10px] ${styles.cardTextMuted}`}>{t(labelKey)}</small>
                      <strong className={`block mt-0.5 text-xs font-mono break-words ${styles.cardText}`}>
                        {key === "volatilityRange"
                          ? (pickArray(riskMetrics, ["volatilityRange"]).map((x) => String(x)).join(" ~ ") || "—")
                          : (pickString(riskMetrics, [key]) ?? "—")}
                      </strong>
                    </div>
                  ))}
                </div>
              ) : (
                <p className={`text-[11px] break-words font-mono ${styles.cardTextMuted}`}>
                  {compactJson(sim.value, 400)}
                </p>
              )
            )}
          </div>
        )}
      </div>
    </div>
  );
}
