/**
 * 场景运行页（场景工作台简化 v0.4）。
 *
 * <p>2026-11-24 用户裁决：运行页只保留「问题驱动」主链路
 * （输入问题 → 意图识别 → 确认假设 → 心智模型 → 执行 → 过程与结果 → 对比 → 建议）。
 * 原「发起运行」折叠面板、按分析类型的运行记录表、预测对比、复盘入口与
 * 「高级/调试（认知运行面）」整块自本页摘除——高级面改在「场景定义」页可达，
 * 能力未删（摘入口 ≠ 摘能力）。</p>
 */
import React from 'react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import QuestionRunPanel from './QuestionRunPanel';
import ResearchRunPanel from './ResearchRunPanel';

interface Props {
  scenarioId: string;
  /** 切到「编辑场景定义」次级入口（顶部按钮只剩运行/配置后的可达路径） */
  onEditDefinition?: () => void;
}

export default function RunTab({ scenarioId, onEditDefinition }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="p-4 space-y-4">
      {onEditDefinition && (
        <div className="flex justify-end -mb-2">
          <button
            type="button"
            onClick={onEditDefinition}
            className={`text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
          >
            {t('workspace.run.edit_definition')}
          </button>
        </div>
      )}
      <QuestionRunPanel scenarioId={scenarioId} />
      {/* L3 研究循环（RD-Agent 模式首切片）：默认折叠，不改既有 L1/L2 使用路径 */}
      <ResearchRunPanel scenarioId={scenarioId} />
    </div>
  );
}
