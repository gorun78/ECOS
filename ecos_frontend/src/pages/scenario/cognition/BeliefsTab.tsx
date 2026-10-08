/**
 * BeliefsTab — E4 信念列表 `?mind=`（design-05 B.4 / F05-06）。
 *
 * 数据源：`GET /api/v1/business/scenarios/{id}/cognition/beliefs?mind=MID`（E4 契约端点，
 * 经 {@link fetchScenarioBeliefs}）。按 `updatedAt desc` 排序（客户端兜底）；每行渲染
 * `variable / domain / probability / strength / capability`。
 *
 * **capability=null 的行必须保留**（REQ-COG-01 §1.2 E4 / F05-06）：前端行内显示
 * `t('scenario.cognition.unset')` 而不丢弃；后端 `data.issues[]` 透传展示（禁静默隐藏）。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BrainCircuit, Info } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { CognitiveSkeleton, CognitiveErrorState, cognitiveErrorTitleKey } from "./CognitionStateViews";
import {
  fetchScenarioBeliefs,
  isCognitiveApiError,
  CognitiveApiError,
  debounce,
  COGNITION_POLL_INTERVAL_MS,
  COGNITION_DEBOUNCE_MS,
  COGNITION_DEFAULT_LIMIT,
  type BeliefRow,
} from "../../../services/cognitiveClient";

export interface BeliefsTabProps {
  scenarioId: string;
  mindId?: string;
}

type Status =
  | { kind: "loading" }
  | { kind: "empty" }
  | { kind: "ok"; rows: BeliefRow[]; issues?: string[] }
  | { kind: "error"; error: CognitiveApiError };

const INITIAL: Status = { kind: "loading" };

export default function BeliefsTab({ scenarioId, mindId }: BeliefsTabProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [status, setStatus] = useState<Status>(INITIAL);
  const reqSeq = useRef(0);

  const run = useCallback(
    async (opts: { silent?: boolean } = {}) => {
      const seq = ++reqSeq.current;
      if (!opts.silent) setStatus({ kind: "loading" });
      try {
        const res = await fetchScenarioBeliefs(scenarioId, {
          mind: mindId || undefined,
          limit: COGNITION_DEFAULT_LIMIT, // E4 契约：?limit= 必传（默认 10，上限 50）
        });
        if (seq !== reqSeq.current) return;
        const rows = (res?.rows ?? []).slice().sort((a, b) =>
          (b.updatedAt ? new Date(b.updatedAt).getTime() : 0) - (a.updatedAt ? new Date(a.updatedAt).getTime() : 0)
        );
        if (rows.length === 0) setStatus({ kind: "empty" });
        else setStatus({ kind: "ok", rows, issues: res.issues });
      } catch (e) {
        if (seq !== reqSeq.current) return;
        const err: CognitiveApiError = isCognitiveApiError(e)
          ? e
          : new CognitiveApiError({ code: "NETWORK", status: 0, message: e instanceof Error ? e.message : String(e) });
        setStatus({ kind: "error", error: err });
      }
    },
    [scenarioId, mindId],
  );

  const runRef = useRef(run);
  runRef.current = run;
  // 重复触发防抖（契约 100–300ms）+ 30s 后台静默轮询（design-05 B.6 / X-52）
  const debouncedRun = useMemo(
    () => debounce(() => { void runRef.current(); }, COGNITION_DEBOUNCE_MS),
    [],
  );

  useEffect(() => {
    debouncedRun();
    const poll = setInterval(() => { void runRef.current({ silent: true }); }, COGNITION_POLL_INTERVAL_MS);
    return () => {
      clearInterval(poll);
      debouncedRun.cancel();
    };
  }, [run, debouncedRun]);

  const renderCapability = (c: string | null | undefined) => {
    if (c == null || c === "") {
      // design-05 E4 硬性约束："capability=null 行保留 + 行内显示 t('scenario.cognition.unset')"
      return (
        <span
          className={`inline-flex items-center gap-1 text-[9px] font-mono px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.cardTextMuted}`}
          role="note"
          aria-label={t("scenario.cognition.unset")}
        >
          <Info size={9} />
          {t("scenario.cognition.unset")}
        </span>
      );
    }
    return (
      <span
        className={`text-[10px] px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.cardText}`}
      >
        {c}
      </span>
    );
  };

  return (
    <div className="space-y-3 p-3">
      <div className="flex items-center gap-2">
        <BrainCircuit size={13} className={styles.muted} />
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}>
          {t("scenario.cognition.beliefs")}
        </span>
      </div>

      {status.kind === "loading" && <CognitiveSkeleton rows={4} />}

      {status.kind === "empty" && (
        <div className={`p-4 ${styles.muted} text-[11px]`} role="status">
          {t("scenario.cognition.empty")}
        </div>
      )}

      {status.kind === "error" && (
        <CognitiveErrorState
          error={status.error}
          titleKey={cognitiveErrorTitleKey(status.error)}
          onRetry={debouncedRun}
        />
      )}

      {status.kind === "ok" && (
        <>
          {status.issues && status.issues.length > 0 && (
            <div
              role="status"
              className={`p-2.5 rounded-md text-[10px] ${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}`}
              aria-live="polite"
            >
              <div className="flex items-center gap-1.5 font-bold">
                <Info size={11} />
                <span>{t("scenario.cognition.belief.issues")}</span>
              </div>
              <ul className="mt-1 list-disc list-inside space-y-0.5 opacity-90">
                {status.issues.map((issue, i) => <li key={i}>{issue}</li>)}
              </ul>
            </div>
          )}
          <div className="space-y-1.5" role="list" aria-label={t("scenario.cognition.beliefs")}>
            {status.rows.map((r, i) => {
              const key = `${r.variableName ?? "row"}-${i}`;
              return (
                <div
                  key={key}
                  role="listitem"
                  className={`flex flex-wrap items-center gap-3 p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded-md`}
                >
                  <span
                    className={`min-w-0 truncate w-2/5 text-[11px] font-bold ${styles.cardText}`}
                    title={r.variableName}
                  >
                    {r.variableName ?? "—"}
                  </span>
                  <span className={`min-w-0 truncate w-1/5 text-[10px] ${styles.muted}`} title={r.domain}>
                    {r.domain ?? "—"}
                  </span>
                  <span
                    className={`text-[10px] font-mono ${styles.muted}`}
                    role="meter"
                    aria-valuemin={0}
                    aria-valuemax={1}
                    aria-valuenow={typeof r.probability === "number" ? r.probability : undefined}
                    aria-label={t("scenario.cognition.belief.probability")}
                  >
                    {t("scenario.cognition.belief.probability")}:{' '}
                    <span className={`font-bold ${styles.cardText}`}>
                      {typeof r.probability === "number" ? r.probability.toFixed(4) : "—"}
                    </span>
                  </span>
                  <span
                    className={`text-[10px] font-mono ${styles.muted}`}
                    role="meter"
                    aria-valuemin={0}
                    aria-valuenow={typeof r.strength === "number" ? r.strength : undefined}
                    aria-label={t("scenario.cognition.belief.strength")}
                  >
                    {t("scenario.cognition.belief.strength")}:{' '}
                    <span className={`font-bold ${styles.cardText}`}>
                      {typeof r.strength === "number" ? r.strength.toFixed(4) : "—"}
                    </span>
                  </span>
                  <span className={`ml-auto`}>{renderCapability(r.capability)}</span>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
