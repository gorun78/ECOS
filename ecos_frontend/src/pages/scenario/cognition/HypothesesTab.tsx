/**
 * HypothesesTab — E3 假设列表 `?mind=`（design-05 B.4 / F05-05）。
 *
 * 数据源：`GET /api/v1/business/scenarios/{id}/cognition/hypotheses?mind=MID`。无 `mind` 传场景全部、
 * 带 `mind` 严格过滤；后端恒 `is_deleted=0`、按 `updatedAt desc`（客户端再兜底一次）。
 * 四态徽标 `PROPOSED / EVIDENCED / BELIEVED / REFUTED`（canonical）；读侧兼容
 * `VALID → BELIEVED`、`INVALIDATED → REFUTED`、`ARCHIVED → REFUTED`。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Lightbulb, Gauge } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { CognitiveSkeleton, CognitiveErrorState, cognitiveErrorTitleKey } from "./CognitionStateViews";
import {
  fetchScenarioHypotheses,
  isCognitiveApiError,
  CognitiveApiError,
  debounce,
  COGNITION_POLL_INTERVAL_MS,
  COGNITION_DEBOUNCE_MS,
  COGNITION_DEFAULT_LIMIT,
  type HypothesisRow,
} from "../../../services/cognitiveClient";

export interface HypothesesTabProps {
  scenarioId: string;
  mindId?: string;
}

type Status =
  | { kind: "loading" }
  | { kind: "empty" }
  | { kind: "ok"; rows: HypothesisRow[] }
  | { kind: "error"; error: CognitiveApiError };

const INITIAL: Status = { kind: "loading" };

/** canonical 四态 + 旧值读侧收敛映射（VALID/INVALIDATED/ARCHIVED 只读映射，禁写入旧值） */
const LEGACY_TO_CANONICAL: Record<string, "PROPOSED" | "EVIDENCED" | "BELIEVED" | "REFUTED"> = {
  PROPOSED: "PROPOSED", EVIDENCED: "EVIDENCED", BELIEVED: "BELIEVED", REFUTED: "REFUTED",
  VALID: "BELIEVED", INVALIDATED: "REFUTED", ARCHIVED: "REFUTED",
};
type StatusNormal = "PROPOSED" | "EVIDENCED" | "BELIEVED" | "REFUTED";
const normalizeStatus = (s?: string): StatusNormal =>
  (!s ? "PROPOSED" : (LEGACY_TO_CANONICAL[s.toUpperCase()] ?? "PROPOSED"));

function confidenceBucket(c?: number): "weak" | "mid" | "strong" {
  if (typeof c !== "number" || Number.isNaN(c)) return "mid";
  if (c >= 0.8) return "strong";
  if (c < 0.5) return "weak";
  return "mid";
}

export default function HypothesesTab({ scenarioId, mindId }: HypothesesTabProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [status, setStatus] = useState<Status>(INITIAL);
  const reqSeq = useRef(0);

  // `silent`=轮询刷新时不闪骨架屏（30s 后台轮询），仅在首屏/手动重试显示加载态
  const run = useCallback(
    async (opts: { silent?: boolean } = {}) => {
      const seq = ++reqSeq.current;
      if (!opts.silent) setStatus({ kind: "loading" });
      try {
        const rows = await fetchScenarioHypotheses(scenarioId, {
          mind: mindId || undefined,
          limit: COGNITION_DEFAULT_LIMIT, // E3 契约：?limit= 必传（默认 10，上限 50）
        });
        if (seq !== reqSeq.current) return;
        if (!rows || rows.length === 0) { setStatus({ kind: "empty" }); return; }
        // 按 updatedAt desc 客户端兜底排序（后端已排，此处保险）
        const sorted = [...rows].sort((a, b) =>
          (b.updatedAt ? new Date(b.updatedAt).getTime() : 0) - (a.updatedAt ? new Date(a.updatedAt).getTime() : 0)
        );
        setStatus({ kind: "ok", rows: sorted });
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
  // 重复触发防抖（切 Mind / 连点重试合并为末次，契约 100–300ms）
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

  const renderBadge = (st: StatusNormal) => {
    const cls =
      st === "BELIEVED" ? `${styles.successBg} ${styles.successText}`
      : st === "REFUTED" ? `${styles.dangerBg} ${styles.dangerText}`
      : st === "EVIDENCED" ? `${styles.infoBg} ${styles.infoText}`
      : `${styles.badgeBg} ${styles.cardTextMuted}`;
    return (
      <span
        className={`text-[9px] font-mono font-bold px-1.5 py-0.5 rounded ${cls}`}
        title={st}
        role="status"
        aria-label={t(`scenario.cognition.hyp.status.${st}`)}
      >
        {st}
      </span>
    );
  };

  // 置信度三色分段：颜色 + lucide 图标 + 文字档位三重表意（B.7 色盲可读，不只靠颜色）
  const renderConfidence = (c: number | undefined) => {
    const b = confidenceBucket(c);
    const cls = b === "strong" ? `${styles.successBg} ${styles.successText}`
      : b === "weak" ? `${styles.warningBg} ${styles.warningText}`
      : `${styles.infoBg} ${styles.infoText}`;
    const filled = b === "strong" ? 3 : b === "mid" ? 2 : 1;
    const tierKey = `scenario.cognition.hyp.confidence.${b}`;
    return (
      <span
        className="inline-flex items-center gap-1"
        title={typeof c === "number" ? c.toFixed(2) : "—"}
        role="meter"
        aria-valuemin={0}
        aria-valuemax={1}
        aria-valuenow={typeof c === "number" ? c : undefined}
        aria-valuetext={t(tierKey)}
      >
        <span className="inline-flex items-center gap-0.5" aria-hidden>
          {[0, 1, 2].map((i) => (
            <span key={i} className={`h-1 w-3 rounded-full ${i < filled ? styles.accentBg : styles.inputBg}`} />
          ))}
        </span>
        <Gauge size={10} className="shrink-0" aria-hidden />
        <span className="text-[9px] font-medium">{t(tierKey)}</span>
        <span className={`text-[9px] font-mono px-1 py-0 rounded ${cls}`}>
          {typeof c === "number" ? c.toFixed(2) : "—"}
        </span>
      </span>
    );
  };

  return (
    <div className="space-y-3 p-3">
      <div className="flex items-center gap-2">
        <Lightbulb size={13} className={styles.muted} />
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}>
          {t("scenario.cognition.hypotheses")}
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
        <div className="space-y-1.5" role="list" aria-label={t("scenario.cognition.hypotheses")}>
          {status.rows.map((r, i) => {
            const canon = normalizeStatus(r.status);
            const key = r.hypothesisId ?? r.mindId ?? `hyp-${i}`;
            return (
              <div
                key={key}
                role="listitem"
                className={`flex flex-col gap-1.5 p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded-md`}
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className={`text-[10px] font-mono ${styles.muted}`}>
                    {r.hypothesisId ?? `#${i + 1}`}
                  </span>
                  <span className={`ml-auto min-w-0 truncate text-[11px] ${styles.cardTextMuted}`} title={r.reason}>
                    {r.reason || t("scenario.cognition.hyp.reason")}
                  </span>
                  {renderConfidence(r.confidence)}
                  {renderBadge(canon)}
                </div>
                {r.updatedAt && (
                  <span className={`text-[9px] font-mono ${styles.muted}`}>{r.updatedAt}</span>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
