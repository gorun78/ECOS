/**
 * decision/panesShared — 四个任务 pane 共用的读取与渲染原语。
 *
 * 关键纪律：后端多处把结果以 `Map<String,Object>` / JSONB 原样透传
 * （`ScenarioRunService` 的 `forecast_result`、`ScenarioBlueprintVO.risks[]`），
 * 没有强类型 DTO。因此这里只做**安全取值**与**如实透传**：
 * 已知键优先，取不到就回退到紧凑 JSON，绝不猜测缺失字段的语义、绝不补默认值。
 *
 * @license Apache-2.0
 */
import React from "react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { AsyncState } from "./decisionTypes";

/** 按候选键顺序取第一个非空字符串；全空返回 null（调用方渲染「—」）。 */
export function pickString(o: Record<string, unknown>, keys: string[]): string | null {
  for (const k of keys) {
    const v = o[k];
    if (typeof v === "string" && v.trim() !== "") return v;
    if (typeof v === "number" && Number.isFinite(v)) return String(v);
  }
  return null;
}

/** 紧凑 JSON 摘要（原值透传，超长截断）。只用于「结构未知」字段的兜底展示。 */
export function compactJson(v: unknown, max = 200): string {
  if (v == null) return "";
  let s: string;
  try {
    s = JSON.stringify(v);
  } catch {
    s = String(v);
  }
  return s.length > max ? `${s.slice(0, max)}…` : s;
}

/** 从 `Map` 透传结果里取数组字段（后端可能给 camelCase 或 snake_case）。 */
export function pickArray(o: Record<string, unknown>, keys: string[]): unknown[] {
  for (const k of keys) {
    const v = o[k];
    if (Array.isArray(v)) return v;
  }
  return [];
}

export function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

/** 取嵌套对象字段（如 run row 的 `forecast` / `forecast_result`）。 */
export function pickRecord(o: Record<string, unknown>, keys: string[]): Record<string, unknown> | null {
  for (const k of keys) {
    const rec = asRecord(o[k]);
    if (rec) return rec;
  }
  return null;
}

/** 六态统一渲染：loading → denied → error → empty → ready。顺序不可调换。 */
export interface PaneStateProps<T> {
  state: AsyncState<T>;
  isEmpty: (v: T) => boolean;
  emptyTextKey: string;
  onRetry?: () => void;
  children: (v: T) => React.ReactNode;
}

export function PaneState<T>({ state, isEmpty, emptyTextKey, onRetry, children }: PaneStateProps<T>) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (state.kind === "idle" || state.kind === "loading") {
    return (
      <div className="flex items-center gap-2 py-6 justify-center text-xs">
        <span className={`h-4 w-4 inline-block animate-spin rounded-full border-2 border-current border-t-transparent opacity-60 ${styles.cardTextMuted}`} />
        <span className={styles.cardTextMuted}>{t("platform.decision.task.loading")}</span>
      </div>
    );
  }

  if (state.kind === "denied") {
    return (
      <div className={`rounded-md border px-3 py-4 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
        {t("platform.decision.task.denied")}
      </div>
    );
  }

  if (state.kind === "error") {
    return (
      <div className={`rounded-md border px-3 py-3 text-[11px] break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
        <div>{t("platform.decision.loadFailed")}{state.message}</div>
        {state.traceId && (
          <div className={`mt-1 font-mono ${styles.cardTextMuted}`}>traceId: {state.traceId}</div>
        )}
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className={`mt-2 rounded border px-2 py-0.5 cursor-pointer ${styles.inputBorder} ${styles.inputText}`}
          >
            {t("platform.decision.retry")}
          </button>
        )}
      </div>
    );
  }

  if (isEmpty(state.value)) {
    return <p className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>{t(emptyTextKey)}</p>;
  }

  return <>{children(state.value)}</>;
}

/** 轻量表格外壳（与设计系统的表格样式保持一致的边框/字号）。 */
export function MiniTable({ head, children }: { head: string[]; children: React.ReactNode }) {
  const { styles } = useTheme();
  return (
    <div className={`overflow-x-auto rounded-lg border ${styles.cardBorder}`}>
      <table className="w-full text-[11px]">
        <thead>
          <tr className={`${styles.islandCardSoft}`}>
            {head.map((h, i) => (
              <th key={i} className={`px-2.5 py-1.5 text-left font-semibold ${styles.cardTextMuted}`}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="font-mono">{children}</tbody>
      </table>
    </div>
  );
}
