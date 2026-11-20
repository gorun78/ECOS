/**
 * TaskCardGrid — 四张任务卡（原型 v2 区块 3）。
 *
 * 纯受控展示：`active` 由外层持有，点击只回传 kind。卡片不含任何数据取数，
 * 也不宣称"已匹配场景/数据"——场景匹配发生在详情面板内，由各 pane 如实呈现。
 *
 * @license Apache-2.0
 */
import React from "react";
import { ArrowRight, BarChart3, GitCompare, ListChecks, ShieldAlert } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { TASK_KINDS, type TaskKind } from "./decisionTypes";

const ICONS: Record<TaskKind, typeof BarChart3> = {
  forecast: BarChart3,
  risk: ShieldAlert,
  compare: GitCompare,
  actions: ListChecks,
};

export interface TaskCardGridProps {
  active: TaskKind | null;
  onSelect: (kind: TaskKind) => void;
}

export default function TaskCardGrid({ active, onSelect }: TaskCardGridProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <section>
      <div className="flex items-baseline justify-between gap-3 mb-3">
        <h2 className={`text-sm font-bold ${styles.appText}`}>{t("platform.decision.tasks.title")}</h2>
        <span className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.decision.tasks.hint")}</span>
      </div>
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
        {TASK_KINDS.map((kind) => {
          const IconCmp = ICONS[kind];
          const isActive = active === kind;
          return (
            <button
              key={kind}
              type="button"
              onClick={() => onSelect(kind)}
              className={`text-left rounded-xl border p-4 min-h-[148px] flex flex-col cursor-pointer transition-all ${
                isActive
                  ? `${styles.accentBorder} ${styles.accentBg}`
                  : `${styles.cardBg} ${styles.cardBorder} hover:opacity-90`
              }`}
            >
              <span className={`mb-3 inline-flex h-9 w-9 items-center justify-center rounded-lg ${styles.badgeBg} ${styles.accentText}`}>
                <IconCmp size={17} />
              </span>
              <strong className={`text-sm ${styles.cardText}`}>{t(`platform.decision.task.${kind}.title`)}</strong>
              <small className={`mt-1 text-[11px] leading-relaxed ${styles.cardTextMuted}`}>
                {t(`platform.decision.task.${kind}.desc`)}
              </small>
              <span className={`mt-auto pt-3 inline-flex items-center gap-1 text-[11px] font-bold ${styles.accentText}`}>
                {t(`platform.decision.task.${kind}.cta`)}
                <ArrowRight size={12} />
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
