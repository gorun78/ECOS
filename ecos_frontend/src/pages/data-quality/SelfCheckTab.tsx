/**
 * PMO-48-A T5 — 数据质量中心 · 模块自检 Tab
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 调 POST /api/v1/dq/health/selfcheck (T6 提供), 渲染勾叉结果。
 * T6 未完成前: placeholder "等待后端自检出"+ 重试按钮。
 */

import React, { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  RefreshCw,
  Loader2,
  Server,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { DqSelfCheckResult, runDqSelfCheck } from "./api";

export default function SelfCheckTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<DqSelfCheckResult | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const r = await runDqSelfCheck();
      setResult(r);
    } catch (e) {
      setResult(null);
      setError(e instanceof Error ? e.message : (t("dw.dqRule.selfCheckFailed")));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void load();
  }, [load]);

  // T6 未就绪(404/业务错误)或 success=false → 占位
  const showPlaceholder = !loading && (result === null || !result.success);

  if (loading) {
    return (
      <div className="h-full flex flex-col items-center justify-center">
        <Loader2 className={`w-6 h-6 animate-spin ${styles.accentText}`} />
        <span className={`mt-3 text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.loading")}</span>
      </div>
    );
  }

  if (showPlaceholder) {
    return (
      <div className="h-full flex flex-col items-center justify-center">
        <AlertTriangle className={`w-10 h-10 ${styles.warningText} opacity-50`} />
        <span className="mt-4 text-sm font-bold">{t("dw.dqRule.selfCheckWait")}</span>
        <span className={`mt-1.5 text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.selfCheckHint")}</span>
        {error ? (
          <span
            className="mt-2 text-[11px] font-mono max-w-md text-center px-4 overflow-hidden text-ellipsis whitespace-nowrap"
            title={error}
          >
            {error}
          </span>
        ) : null}
        <button
          onClick={() => void load()}
          className={`mt-5 flex items-center gap-2 px-4 py-2 text-xs font-bold ${styles.accentBg} ${styles.accentHover} ${styles.cardText} rounded-lg shadow-xs transition cursor-pointer`}
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t("dw.dqRule.retry")}
        </button>
      </div>
    );
  }

  const total = result?.total ?? 0;
  const passed = result?.passed ?? 0;
  const failed = result?.failed ?? 0;
  const passRate = total > 0 ? Math.round((passed / total) * 100) : 0;

  return (
    <div className="h-full overflow-y-auto">
      {/* 顶部统计 */}
      <div className="grid grid-cols-4 gap-4 mb-6 px-1">
        <StatCard styles={styles} label={t("dw.dqRule.total")} value={total} />
        <StatCard styles={styles} label={t("dw.dqRule.passed")} value={passed} tone="success" />
        <StatCard styles={styles} label={t("dw.dqRule.failed")} value={failed} tone="danger" />
        <StatCard styles={styles} label={t("dw.dqRule.passRate")} value={`${passRate}%`} tone={passRate >= 80 ? "success" : passRate >= 50 ? "warning" : "danger"} />
      </div>

      {/* 各 check 列表 */}
      <div className={`border ${styles.cardBorder} rounded-xl ${styles.cardBg} overflow-hidden`}>
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder}`}>
          <span className={`text-sm font-bold ${styles.cardText}`}>{t("dw.dqRule.selfCheckItems")}</span>
          <button
            onClick={() => void load()}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 rounded-lg transition cursor-pointer`}
          >
            <RefreshCw className="w-3.5 h-3.5" />
            {t("dw.dqRule.refresh")}
          </button>
        </div>
        <ul className="divide-y" >
          {(result?.checks ?? []).map((c, i) => (
            <li key={i} className="flex items-start gap-3 px-4 py-3">
              {c.passed ? (
                <CheckCircle2 className={`w-4 h-4 mt-0.5 shrink-0 ${styles.successText}`} />
              ) : (
                <XCircle className={`w-4 h-4 mt-0.5 shrink-0 ${styles.dangerText}`} />
              )}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <Server className={`w-3 h-3 ${styles.cardTextMuted}`} />
                  <span className={`text-xs font-semibold ${styles.cardText} truncate`}>{c.name}</span>
                </div>
                {c.message ? (
                  <div className={`mt-1 text-[11px] font-mono ${styles.cardTextMuted} whitespace-pre-wrap break-words`}>
                    {c.message}
                  </div>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** KPI 小卡片 — 统计/通过/失败/通过率 */
function StatCard({
  styles, label, value, tone,
}: {
  styles: ReturnType<typeof useTheme>["styles"];
  label: string;
  value: number | string;
  tone?: "success" | "warning" | "danger";
}) {
  const toneCls = tone
    ? {
        success: `${styles.successBg} ${styles.successText} border ${styles.successBorder}`,
        warning: `${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}`,
        danger: `${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder}`,
      }[tone]
    : `${styles.cardBg} ${styles.cardText} border ${styles.cardBorder}`;
  return (
    <div className={`p-3.5 rounded-xl border ${toneCls} shadow-3xs`}>
      <div className="text-[10px] font-mono uppercase tracking-wider opacity-70">{label}</div>
      <div className="mt-1 text-2xl font-extrabold tabular-nums">{value}</div>
    </div>
  );
}
