/**
 * WizardShell — 蓝图构建向导步骤壳（设计方案 §3.4 十幕工作流的呈现层编排，复盘 S1 收口）。
 *
 * <p>把 L2 原"面板堆叠"重组为「目标与状态 → 上下文构建 → 逻辑模型与能力 →
 * 发布门槛 → 发布与回流」五步工作流，复用既有面板为步骤内容（不复制逻辑）。
 * 步骤推进是**纯呈现层导览**：不写任何状态、不合成迁移规则——迁移权威仍在
 * 状态轨（后端 allowedTransitions 驱动）；推荐步仅按当前状态的 tone 给视觉默认值，
 * 用户可自由跳看任一步。十幕中 W Agent 目标理解/Gap 交互/回放/运行反馈诸幕
 * 依赖的后端契约未核验面，按"禁假实现"红线不入本向导（设计显式留口）。</p>
 * 单源守卫注记：步骤状态 data 由父层 BlueprintPanel 经 scenarioBlueprintClient
 * 取得后注入本壳，本壳不另开第二条取数通道（blueprintPaths.singleSource 守卫口径）。
 */
import React, { useEffect, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Route
} from 'lucide-react';
import { Icon } from '../../../../components/LucideIcon';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import { parseBlueprintStatus, statusTone } from '../../../../services/scenarioBlueprintClient';

export interface WizardStep {
  key: string;
  icon: string;
  /** 步骤内容（由父层注入既有面板，WizardShell 不感知其内部状态机）。 */
  content: React.ReactNode;
}

interface Props {
  steps: WizardStep[];
  /** 当前蓝图状态（raw 字符串，单源 client 解析）；呈现层据此给推荐步视觉默认值。 */
  status?: string;
}

/** 状态 tone → 推荐步骤（0 基）的视觉映射：仅呈现层默认值，非迁移规则。 */
function recommendedStepOf(status: string | undefined): number {
  switch (statusTone(parseBlueprintStatus(status))) {
    case 'progress':
      return 1;
    case 'ready':
      return 3;
    case 'approved':
    case 'live':
    case 'terminal':
      return 4;
    default:
      return 0;
  }
}

export default function WizardShell({ steps, status }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const recommended = recommendedStepOf(status);
  const [active, setActive] = useState(() => Math.min(Math.max(recommended, 0), steps.length - 1));
  /** 行275 订正：用户手动选步后，状态推进的推荐步副作用不再覆盖用户选择（头注释承诺兑现）。 */
  const [userPicked, setUserPicked] = useState(false);

  // 状态推进后推荐步变化时跟随（仅限用户尚未手动选步）
  useEffect(() => {
    setActive((prev) => (prev >= steps.length ? steps.length - 1 : prev));
  }, [steps.length]);

  useEffect(() => {
    if (userPicked) return;
    setActive(Math.min(Math.max(recommended, 0), steps.length - 1));
  }, [recommended, userPicked, steps.length]);

  const pick = (i: number): void => {
    setUserPicked(true);
    setActive(i);
  };

  const cur = Math.min(active, steps.length - 1);

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-3`}>
      <div className="flex items-center gap-2 flex-wrap">
        <Route size={14} className={styles.accentText} />
        <h3 className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.wiz.title')}</h3>
        <span className={`ml-auto text-[10px] font-mono ${styles.cardTextMuted}`}>
          {t('workspace.wiz.progress', { cur: cur + 1, total: steps.length })}
        </span>
      </div>

      {/* 步骤条（呈现层导览，非状态机） */}
      <ol className="flex items-stretch gap-1 overflow-x-auto pb-1" role="tablist" aria-label={t('workspace.wiz.title')}>
        {steps.map((s, i) => {
          const isCur = i === cur;
          const done = i < cur;
          const cls = isCur
            ? `${styles.accentText} ${styles.accentBorder} ${styles.badgeBg}`
            : done
              ? `${styles.successText} ${styles.successBorder} ${styles.cardBg}`
              : `${styles.cardTextMuted} ${styles.cardBorder} ${styles.cardBg}`;
          return (
            <li key={s.key} className="flex items-center gap-1 shrink-0">
              <button
                type="button"
                role="tab"
                aria-selected={isCur}
                onClick={() => pick(i)}
                className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-1 rounded border cursor-pointer transition-colors ${cls}`}
              >
                <Icon name={s.icon} size={11} />
                {t(`workspace.wiz.step.${s.key}`)}
              </button>
              {i < steps.length - 1 && <span className={`w-2 h-px ${styles.cardTextMuted} bg-current opacity-40`} aria-hidden />}
            </li>
          );
        })}
      </ol>

      {/* 当前步内容 */}
      <div role="tabpanel">{steps[cur]?.content}</div>

      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => pick(Math.max(0, cur - 1))}
          disabled={cur === 0}
          className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded border cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <ChevronLeft size={12} />
          {t('workspace.wiz.prev')}
        </button>
        <span className={`text-[10px] ${styles.cardTextMuted} max-w-md`}>{t('workspace.wiz.note')}</span>
        <button
          type="button"
          onClick={() => pick(Math.min(steps.length - 1, cur + 1))}
          disabled={cur >= steps.length - 1}
          className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded border cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed ${styles.accentBg} ${styles.cardText} ${styles.accentBorder}`}
        >
          {t('workspace.wiz.next')}
          <ChevronRight size={12} />
        </button>
      </div>
    </div>
  );
}
