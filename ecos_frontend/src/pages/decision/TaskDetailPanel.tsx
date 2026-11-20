/**
 * TaskDetailPanel — 任务详情面板骨架（原型 v2 的 .detail 区）。
 *
 * 四个任务共用同一骨架，差异全部由 props 注入：
 * 标题/副标题走 `platform.decision.task.<kind>.*`，三个统计槽与三个步骤由各 pane
 * 传真实值；传 `undefined` 时渲染「—」而不是补造数字。
 *
 * 场景下拉放在这里（一次实现四处复用）：所有真实端点都需要 scenarioId，
 * 而首页本身没有场景上下文，因此必须由用户显式选定，默认取第一个 ACTIVE。
 *
 * @license Apache-2.0
 */
import React from "react";
import { CheckCircle2, ChevronDown, Info, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { DetailStat, DetailStep, ScenarioOption, TaskKind } from "./decisionTypes";

export interface TaskDetailPanelProps {
  kind: TaskKind;
  scenarios: ScenarioOption[];
  scenario: ScenarioOption | null;
  onScenarioChange: (id: string) => void;
  onClose: () => void;
  stats?: DetailStat[];
  steps?: DetailStep[];
  children: React.ReactNode;
}

function StepIcon({ status }: { status: DetailStep["status"] }) {
  const { styles } = useTheme();
  if (status === "ok") {
    return (
      <span className={`inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${styles.successBg} ${styles.successText}`}>
        <CheckCircle2 size={13} />
      </span>
    );
  }
  if (status === "warn") {
    return (
      <span className={`inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${styles.warningBg} ${styles.warningText}`}>
        <Info size={13} />
      </span>
    );
  }
  return (
    <span className={`inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${styles.badgeBg} ${styles.badgeText}`}>
      <Info size={13} />
    </span>
  );
}

export default function TaskDetailPanel({
  kind,
  scenarios,
  scenario,
  onScenarioChange,
  onClose,
  stats,
  steps,
  children,
}: TaskDetailPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const noScenario = scenarios.length === 0;

  return (
    <section
      className={`rounded-2xl border p-5 ${styles.cardBg} ${styles.cardBorder}`}
      aria-live="polite"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className={`text-[11px] font-bold tracking-[0.14em] uppercase ${styles.accentText}`}>
            {t("platform.decision.task.eyebrow")}
          </div>
          <h2 className={`mt-1 text-lg font-bold ${styles.appText}`}>{t(`platform.decision.task.${kind}.title`)}</h2>
          <p className={`mt-1 text-xs ${styles.cardTextMuted}`}>{t(`platform.decision.task.${kind}.desc`)}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className={`shrink-0 inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted}`}
        >
          <X size={12} />
          {t("platform.decision.task.close")}
        </button>
      </div>

      {/* 场景选择：四类任务的所有真实端点都需要 scenarioId */}
      <div className={`mt-4 rounded-lg border p-3 ${styles.cardBorder}`}>
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor="decision-scenario" className={`text-xs font-semibold ${styles.cardText}`}>
            {t("platform.decision.task.scenario.label")}
          </label>
          <div className="relative">
            <select
              id="decision-scenario"
              value={scenario?.id ?? ""}
              onChange={(e) => onScenarioChange(e.target.value)}
              disabled={noScenario}
              className={`appearance-none rounded-lg border pl-2.5 pr-7 py-1.5 text-xs cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            >
              {noScenario ? (
                <option value="">{t("platform.decision.task.scenario.none")}</option>
              ) : (
                scenarios.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}{s.status ? ` · ${s.status}` : ""}
                  </option>
                ))
              )}
            </select>
            <ChevronDown size={12} className={`pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
          </div>
          {noScenario && (
            <span className={`text-[11px] ${styles.warningText}`}>{t("platform.decision.task.noScenario")}</span>
          )}
        </div>
        {!noScenario && (
          <p className={`mt-1.5 text-[11px] ${styles.cardTextMuted}`}>{t("platform.decision.task.scenario.hint")}</p>
        )}
      </div>

      {/* 三个统计槽：值缺失渲染「—」，不补造 */}
      <div className="mt-4 grid gap-2 grid-cols-1 sm:grid-cols-3">
        {(stats && stats.length > 0 ? stats : [
          { labelKey: "", value: null },
          { labelKey: "", value: null },
          { labelKey: "", value: null },
        ]).map((s, i) => (
          <div key={`${s.labelKey}-${i}`} className={`rounded-lg border p-3 min-w-0 ${styles.cardBorder}`}>
            <small className={`block text-[11px] ${styles.cardTextMuted}`}>
              {s.labelKey ? t(s.labelKey) : "—"}
            </small>
            <strong className={`block mt-1 text-base font-mono break-words ${styles.cardText}`}>
              {s.value ?? "—"}
            </strong>
          </div>
        ))}
      </div>

      {/* 准备步骤：状态来自真实探测，不推断 */}
      {steps && steps.length > 0 && (
        <div className="mt-4">
          <h3 className={`text-xs font-bold uppercase mb-2 ${styles.cardTextMuted}`}>
            {t("platform.decision.task.readiness")}
          </h3>
          <div className="space-y-2">
            {steps.map((s, i) => (
              <div key={i} className={`flex items-start gap-2.5 rounded-lg p-2.5 ${styles.islandCardSoft}`}>
                <StepIcon status={s.status} />
                <div className="min-w-0">
                  <strong className={`block text-xs ${styles.cardText}`}>{t(s.titleKey)}</strong>
                  {s.descKey && (
                    <small className={`block mt-0.5 text-[11px] ${styles.cardTextMuted}`}>{t(s.descKey)}</small>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="mt-4">{children}</div>
    </section>
  );
}
