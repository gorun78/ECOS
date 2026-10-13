/**
 * BlueprintStatusTrack — 场景蓝图状态机进度轨（补充设计 §6.3 / 设计方案 §3.3）。
 *
 * <p>11 主链态渲染为可读步进条（色 + 图标 + 文字三重编码，非原始枚举直显）；
 * 异常/回退态以醒目徽标 + 原因呈现。可迁移后继由后端 `allowedTransitions` 决定
 * （唯一权威），前端不臆造迁移规则；不可达步骤灰化 = 非法迁移（如 DRAFT→ACTIVE）
 * 的视觉护栏，与后端状态机一致。</p>
 *
 * <p>纯展示组件：无 fetch、无本地合成状态；数据全部来自父层注入的
 * {@link ScenarioBlueprintVO} 字段。遵守主题 token 与 i18n 铁律。</p>
 */
import React from 'react';
import LucideIcon from '../../../../components/LucideIcon';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import {
  BLUEPRINT_MAIN_FLOW,
  isExceptionStatus,
  parseBlueprintStatus,
  statusMainStep,
  statusTone,
  type BlueprintStatus,
  type StatusTone,
  type ScenarioBlueprintVO,
} from '../../../../services/scenarioBlueprintClient';

/**
 * tone → 主题 token 类（零 palette 字面量：点/连接线用「语义文本色 + bg-current」，
 * 背景色取自 currentColor，四主题自动跟随）。
 */
interface ToneClass {
  dot: string;
  text: string;
  ring: string;
}

function toneClass(tone: StatusTone, s: ReturnType<typeof useTheme>['styles']): ToneClass {
  switch (tone) {
    case 'initial':
      return { dot: `${s.sidebarText} bg-current`, text: s.cardTextMuted, ring: s.cardBorder };
    case 'progress':
      return { dot: `${s.infoText} bg-current`, text: s.infoText, ring: s.infoBorder };
    case 'ready':
      return { dot: `${s.warningText} bg-current`, text: s.warningText, ring: s.warningBorder };
    case 'approved':
      return { dot: `${s.accentText} bg-current`, text: s.accentText, ring: s.accentBorder };
    case 'live':
      return { dot: `${s.successText} bg-current`, text: s.successText, ring: s.successBorder };
    case 'terminal':
      return { dot: `${s.cardTextMuted} bg-current`, text: s.cardTextMuted, ring: s.cardBorder };
    case 'exception':
      return { dot: `${s.dangerText} bg-current`, text: s.dangerText, ring: s.dangerBorder };
    default:
      return { dot: `${s.cardTextMuted} bg-current`, text: s.cardTextMuted, ring: s.cardBorder };
  }
}

const TONE_ICON: Record<StatusTone, string> = {
  initial: 'FileEdit',
  progress: 'Compass',
  ready: 'ClipboardCheck',
  approved: 'Stamp',
  live: 'Activity',
  terminal: 'CheckCircle2',
  exception: 'AlertOctagon',
  unknown: 'HelpCircle',
};

interface Props {
  blueprint: ScenarioBlueprintVO | null;
  /** 触发迁移（父层负责调 API 与错误呈现）。 */
  onTransition?: (target: BlueprintStatus) => void;
  /** 进行中禁用所有按钮。 */
  busy?: boolean;
}

export default function BlueprintStatusTrack({ blueprint, onTransition, busy }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const status = parseBlueprintStatus(blueprint?.status);
  const allowed = new Set((blueprint?.allowedTransitions ?? []).map((x) => String(x)));
  const currentIdx = statusMainStep(status);
  const exception = isExceptionStatus(status);
  const tone = statusTone(status);
  const tc = toneClass(tone, styles);

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <div className="flex items-center gap-1.5 mb-3">
        <LucideIcon name="GitBranch" size={13} className={styles.accentText} />
        <h3 className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.bp.current_state')}</h3>
        <span className={`ml-auto inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 rounded border ${tc.text} ${tc.ring}`}>
          <LucideIcon name={TONE_ICON[tone]} size={11} />
          {status ? t(`workspace.bp.step.${status}`) : t('workspace.bp.empty_field')}
        </span>
      </div>

      {/* 主链步进条 */}
      <ol className="flex items-stretch gap-1 overflow-x-auto pb-1">
        {BLUEPRINT_MAIN_FLOW.map((step, idx) => {
          const done = currentIdx >= 0 && idx < currentIdx;
          const isCurrent = idx === currentIdx;
          const reachable = allowed.has(step);
          const stepTc = toneClass(statusTone(step), styles);
          return (
            <li key={step} className="flex items-center gap-1 shrink-0">
              <button
                type="button"
                disabled={!reachable || busy || !onTransition}
                title={reachable ? t('workspace.bp.action.transition', { target: t(`workspace.bp.step.${step}`) }) : undefined}
                onClick={() => onTransition?.(step)}
                className={[
                  'flex items-center gap-1 rounded px-1.5 py-1 text-[10px] font-bold border transition-colors',
                  isCurrent
                    ? `${stepTc.text} ${stepTc.ring}`
                    : done
                      ? `${styles.successText} ${styles.successBorder} ${styles.successBg}`
                      : reachable
                        ? `${styles.cardText} ${styles.accentBorder} ${styles.cardBg} cursor-pointer`
                        : `${styles.cardTextMuted} ${styles.cardBorder} ${styles.cardBg} opacity-50 cursor-not-allowed`,
                ].join(' ')}
              >
                <span className={`h-2 w-2 rounded-full ${done ? `${styles.successText} bg-current` : isCurrent ? stepTc.dot : `${styles.cardTextMuted} bg-current opacity-40`}`} />
                {t(`workspace.bp.step.${step}`)}
              </button>
              {idx < BLUEPRINT_MAIN_FLOW.length - 1 && (
                <span className={`h-px w-3 ${done ? `${styles.successText} bg-current` : `${styles.cardTextMuted} bg-current opacity-40`}`} aria-hidden />
              )}
            </li>
          );
        })}
      </ol>

      {/* 异常态：醒目横幅 + 原因（actor/时间/traceId 由父层 detail 呈现） */}
      {exception && status && (
        <div className={`mt-2 flex items-start gap-1.5 text-[11px] rounded border px-2 py-1.5 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <LucideIcon name="AlertOctagon" size={12} className="mt-0.5 shrink-0" />
          <div>
            <span className="font-bold">{t(`workspace.bp.step.${status}`)}</span>
            {blueprint?.transitionReason ? <span className="ml-1">{blueprint.transitionReason}</span> : null}
            {blueprint?.traceId ? (
              <span className={`ml-2 font-mono ${styles.cardTextMuted}`}>{t('workspace.bp.traceId')}={blueprint.traceId}</span>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}
