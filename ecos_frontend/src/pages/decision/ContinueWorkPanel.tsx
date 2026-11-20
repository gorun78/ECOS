/**
 * ContinueWorkPanel — 「继续上次的工作」（原型 v2 左下面板）。
 *
 * 数据源：`GET /api/v1/task/list`，走 `fetchTaskList({offset,limit})`。
 * **不能用** `apiTaskList`——它发 `page/size`，后端 `TaskController` 只认
 * `offset/limit`，参数会被静默忽略从而永远返回第一页。
 *
 * `TaskVO` 没有 status 字段，因此本面板**不渲染状态**：列一个「—」比
 * 逐条补发 N+1 请求换取一个可能过期的状态更诚实，也更省。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { NoAccessError } from "../../services/httpClient";
import { fetchTaskList, type TaskSummary } from "../../services/taskCenter";
import logger from "../../services/logger";
import type { AsyncState } from "./decisionTypes";

const RECENT_LIMIT = 5;

export default function ContinueWorkPanel() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<AsyncState<TaskSummary[]>>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    try {
      const result = await fetchTaskList({ offset: 0, limit: RECENT_LIMIT });
      setState({ kind: "ready", value: result?.items ?? [] });
    } catch (e) {
      if (e instanceof NoAccessError) {
        setState({ kind: "denied" });
        return;
      }
      logger.error("decision-continue-work", e);
      setState({ kind: "error", message: e instanceof Error ? e.message : String(e) });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const body = (() => {
    if (state.kind === "loading" || state.kind === "idle") {
      return <p className={`py-5 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.task.loading")}</p>;
    }
    if (state.kind === "denied") {
      return <p className={`py-5 text-center text-xs ${styles.warningText}`}>{t("platform.decision.task.denied")}</p>;
    }
    if (state.kind === "error") {
      return (
        <p className={`py-3 text-[11px] break-words ${styles.dangerText}`}>
          {t("platform.decision.loadFailed")}{state.message}
        </p>
      );
    }
    if (state.value.length === 0) {
      return <p className={`py-5 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.continue.empty")}</p>;
    }
    return (
      <ul className="divide-y">
        {state.value.map((task) => (
          <li key={task.taskId} className={`py-2.5 border-b last:border-b-0 ${styles.cardBorder}`}>
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <strong className={`block text-xs break-words ${styles.cardText}`}>{task.taskName || task.taskId}</strong>
                <small className={`block mt-0.5 text-[11px] break-words ${styles.cardTextMuted}`}>
                  {task.taskType}
                  {task.createTime ? ` · ${task.createTime}` : ""}
                </small>
              </div>
              {task.category && (
                <span className={`shrink-0 rounded border px-1.5 py-0.5 text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}>
                  {task.category}
                </span>
              )}
            </div>
          </li>
        ))}
      </ul>
    );
  })();

  return (
    <section className={`rounded-xl border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
      <div className="flex items-center justify-between gap-2 mb-1">
        <h2 className={`text-xs font-bold ${styles.cardText}`}>{t("platform.decision.continue.title")}</h2>
        <button
          type="button"
          onClick={load}
          className={`text-[11px] cursor-pointer ${styles.accentText}`}
        >
          {t("platform.decision.continue.refresh")}
        </button>
      </div>
      {body}
    </section>
  );
}
