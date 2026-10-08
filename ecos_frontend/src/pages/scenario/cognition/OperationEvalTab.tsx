/**
 * OperationEvalTab — E2 运行评价 `?mind=&limit=`（design-05 B.4 / F05-04）。
 *
 * 数据源：`GET /api/v1/business/scenarios/{id}/cognition/operation-eval?limit=N&amp;mind=MID`，
 * 经 {@link fetchOperationEval}。`limit` 默认 10，可调 1..50；后端硬截 50 且
 * `truncated=true`（前端显示 `t('scenario.cognition.eval.truncated')`，禁静默）。
 * `mind` 不属于该场景 → 400，本 Tab 显示 "所选 Mind 不属于该场景"。
 *
 * 每行：`name / capability / score 0..1` 进度条 / `trend`（本期恒 FLAT，
 * `trendSource=NOT_IMPLEMENTED` 真实标注，禁伪造趋势，REQ-COG-01 §1.1 E2）。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BarChart3, AlertTriangle, Minus, Compass } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { CognitiveSkeleton, CognitiveErrorState, cognitiveErrorTitleKey } from "./CognitionStateViews";
import {
  fetchOperationEval,
  isCognitiveApiError,
  CognitiveApiError,
  debounce,
  COGNITION_POLL_INTERVAL_MS,
  COGNITION_DEBOUNCE_MS,
  COGNITION_MAX_LIMIT,
  COGNITION_DEFAULT_LIMIT,
  type OperationEvalResponse,
} from "../../../services/cognitiveClient";

export interface OperationEvalTabProps {
  scenarioId: string;
  mindId?: string;
}

// 契约（B.6 / D.3）：E2 Top-N 默认 10、上限 50；常量单源于 cognitiveClient，禁本地魔法数
const MIN_LIMIT = 1, MAX_LIMIT = COGNITION_MAX_LIMIT, DEFAULT_LIMIT = COGNITION_DEFAULT_LIMIT;

type Status =
  | { kind: "loading" }
  | { kind: "ok"; data: OperationEvalResponse; truncated: boolean }
  | { kind: "empty" }
  | { kind: "error"; error: CognitiveApiError };
const INITIAL: Status = { kind: "loading" };

export default function OperationEvalTab({ scenarioId, mindId }: OperationEvalTabProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [limit, setLimit] = useState<number>(DEFAULT_LIMIT);
  const [status, setStatus] = useState<Status>(INITIAL);
  const reqSeq = useRef(0);

  const run = useCallback(
    async (signal?: AbortSignal, silent?: boolean) => {
      const seq = ++reqSeq.current;
      if (!silent) setStatus({ kind: "loading" });
      try {
        const clamped = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, Math.floor(limit) || DEFAULT_LIMIT));
        const res = await fetchOperationEval(scenarioId, { mind: mindId || undefined, limit: clamped });
        if (signal?.aborted || seq !== reqSeq.current) return;
        if (!res || !Array.isArray(res.rows) || res.rows.length === 0) { setStatus({ kind: "empty" }); return; }
        setStatus({ kind: "ok", data: res, truncated: res.truncated === true });
      } catch (e) {
        if (signal?.aborted || seq !== reqSeq.current) return;
        const err: CognitiveApiError = isCognitiveApiError(e)
          ? e
          : new CognitiveApiError({ code: "NETWORK", status: 0, message: e instanceof Error ? e.message : String(e) });
        setStatus({ kind: "error", error: err });
      }
    },
    [scenarioId, mindId, limit],
  );

  const runRef = useRef(run);
  runRef.current = run;
  // 重复触发防抖（切 Mind/limit、连点重试合并；契约 100–300ms）
  const debouncedRun = useMemo(
    () => debounce(() => { void runRef.current(); }, COGNITION_DEBOUNCE_MS),
    [],
  );

  useEffect(() => {
    const c = new AbortController();
    debouncedRun();
    // 30s 后台静默轮询（design-05 B.6 / X-52）
    const poll = setInterval(
      () => { void runRef.current(c.signal, true); },
      COGNITION_POLL_INTERVAL_MS,
    );
    return () => {
      c.abort();
      clearInterval(poll);
      debouncedRun.cancel();
    };
  }, [run, debouncedRun]);

  const renderScoreBar = (score: number | undefined) => {
    const s = typeof score === "number" ? Math.max(0, Math.min(1, score)) : 0;
    const pct = Math.round(s * 100);
    return (
      <div className={`h-1.5 w-24 rounded ${styles.inputBg} border ${styles.inputBorder}`}>
        <div className={`h-full rounded ${styles.accentBg}`} style={{ width: `${pct}%` }}
          role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} />
      </div>
    );
  };

  // 错误态文案键统一在 CognitionStateViews#cognitiveErrorTitleKey（单源，防各 Tab 漂移）

  return (
    <div className="space-y-3 p-3">
      <div className="flex items-center gap-2">
        <BarChart3 size={13} className={styles.muted} />
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}>
          {t("scenario.cognition.operationEval")}
        </span>
        {/* COG-3 / B-3：探索式（反事实/评价）通道必须显式标注"非核算口径"，禁与核算口径混淆 */}
        <span
          role="note"
          data-testid="cog-non-accounting-hint"
          title={t("scenario.cognition.nonAccounting")}
          className={`inline-flex items-center gap-1 text-[9px] font-mono px-1.5 py-0.5 rounded border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
        >
          <Compass size={9} aria-hidden />
          {t("scenario.cognition.nonAccounting")}
        </span>
        <label
          htmlFor="ecos-cog-eval-limit"
          className={`ml-auto text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}
        >
          {t("scenario.cognition.eval.limit")}
        </label>
        <input
          id="ecos-cog-eval-limit"
          type="number"
          aria-label={t("scenario.cognition.eval.limit")}
          min={MIN_LIMIT}
          max={MAX_LIMIT}
          value={limit}
          onChange={(e) => {
            const v = Number(e.target.value || DEFAULT_LIMIT);
            setLimit(Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, Math.floor(v) || DEFAULT_LIMIT)));
          }}
          className={`w-16 px-1.5 py-0.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} rounded outline-none`}
        />
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
          onRetry={() => void run()}
        />
      )}

      {status.kind === "ok" && (
        <>
          <div
            className={`flex items-center gap-2 text-[10px] px-2 py-1 rounded ${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}`}
            role="status"
            aria-live="polite"
            style={{ display: status.truncated ? "flex" : "none" }}
          >
            <AlertTriangle size={11} />
            <span>{t("scenario.cognition.eval.truncated")}</span>
          </div>
          <div className="space-y-1.5" role="list" aria-label={t("scenario.cognition.operationEval")}>
            {status.data.rows.map((r, i) => {
              const flat = r.trend !== "UP" && r.trend !== "DOWN";
              return (
                <div
                  key={`${r.name ?? "row"}-${i}`}
                  role="listitem"
                  className={`flex items-center gap-3 p-2 ${styles.inputBg} border ${styles.inputBorder} rounded-md`}
                >
                  <span className={`w-2/5 min-w-0 truncate text-[11px] font-bold ${styles.cardText}`}>
                    {r.name ?? "—"}
                  </span>
                  <span
                    className={`w-1/4 min-w-0 truncate text-[10px] ${styles.muted}`}
                    title={r.capability ?? t("scenario.cognition.unset")}
                  >
                    {r.capability ?? t("scenario.cognition.unset")}
                  </span>
                  <div className={`w-1/4 flex items-center gap-2 text-[10px] ${styles.muted}`}>
                    {renderScoreBar(r.score)}
                    <span className="font-mono">{typeof r.score === "number" ? r.score.toFixed(3) : "—"}</span>
                  </div>
                  <span className={`ml-auto flex items-center gap-1 text-[10px] font-mono ${styles.muted}`}>
                    {flat ? <Minus size={10} /> : null}
                    {t("scenario.cognition.trendFlat")}
                    {r.trendSource ? <span className="opacity-60">({r.trendSource})</span> : null}
                  </span>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
