/**
 * 工作台 L2 — 「高级/调试」子面（设计方案 §3.8 R1~R2 迁一删一）。
 *
 * <p>承接原 {@code ScenarioManagementView}（#/project_workbench）「认知」Tab 的运行面：
 * 左区 {@link CognitionPanel}（POST /api/v1/workspace/scenarios/{id}/runs 运行编排入口）、
 * 右区 {@link CognitiveRightPanel}（认知四卡 E1~E4 唯一渲染入口，design-05 B.0）。
 * 与 #/project_workbench 合流去重后，本页是 CognitiveRightPanel 在全应用的唯一挂载点
 * （E2E COG-IA 单实例断言据此成立）。原视图剩余融合/决策/Git 能力经「高级视图」链接可达，
 * 不制造死链（R4）。</p>
 *
 * <p>无假真理：本组件不合成任何认知结论；运行/卡片数据全部由子组件走真实 API，
 * 引擎不可用时子组件自行渲染 503 横幅 + traceId。</p>
 */
import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChevronDown,
  ChevronUp,
  ExternalLink,
  FlaskConical
} from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import CognitionPanel from '../../../scenario/CognitionPanel';
import CognitiveRightPanel from '../../../scenario/cognition/CognitiveRightPanel';
import type { BusinessScenario } from '../../../project-workbench/types';

/**
 * CognitionPanel 仅消费 `scenario.id`（runs/history API 入参），
 * L2 上下文只有后端 ScenarioDetailVO，构造最小视图模型（只含真实 id，不伪造字段）。
 */
function toRunFaceVM(scenarioId: string): BusinessScenario {
  return { id: scenarioId } as Pick<BusinessScenario, 'id'> as BusinessScenario;
}

export default function AdvancedDebugPanel({ scenarioId }: { scenarioId: string }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [expanded, setExpanded] = useState(true);

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        className={`w-full flex items-center justify-between gap-2 px-3 py-2 cursor-pointer text-left ${styles.cardBg} hover:opacity-90`}
      >
        <span className={`flex items-center gap-1.5 text-xs font-bold uppercase ${styles.cardTextMuted}`}>
          <FlaskConical size={13} />
          {t('workspace.adv.title')}
        </span>
        <span className={`flex items-center gap-1 text-[10px] ${styles.cardTextMuted}`}>
          {expanded ? t('workspace.adv.collapse') : t('workspace.adv.expand')}
          {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
        </span>
      </button>

      {expanded && (
        <div className="px-3 pb-3 space-y-3">
          <div className="flex flex-col lg:flex-row gap-3 min-h-0">
            <div className="flex-1 min-w-0">
              <CognitionPanel scenario={toRunFaceVM(scenarioId)} />
            </div>
            {/* 认知四卡（E1~E4）唯一入口（design-05 B.0）；<768px 组件内部紧凑模式 + 下方堆叠（B.8） */}
            <aside
              className={`w-full lg:w-[380px] shrink-0 rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}
              aria-label={t('workspace.adv.cards')}
            >
              <CognitiveRightPanel scenarioId={scenarioId} />
            </aside>
          </div>

          {/* §3.8 R4：原 #/project_workbench 收编为「高级视图」，保留可达、不制造死链 */}
          <div className={`flex items-center justify-end border-t ${styles.cardBorder} pt-2`}>
            <Link
              to="/project_workbench"
              className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.accentText} ${styles.cardBorder} hover:opacity-80`}
            >
              <ExternalLink size={11} />
              {t('workspace.adv.legacy_link')}
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
