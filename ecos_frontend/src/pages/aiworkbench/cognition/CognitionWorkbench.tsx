/**
 * CognitionWorkbench — the single frontend home of the cognitive workbench (火 W + 木 C).
 *
 * <p>Renders the 7 V2 cognition screens behind an internal horizontal sub-navigation.
 * The sub-pages are intentionally NOT registered as routes (architecture rule v1.5 §0.5):
 * the whole cognitive workbench is one entry point inside the AI Workbench `cognition` tab,
 * and the active sub-page is held in local state.</p>
 *
 * <p>The sub-nav reuses the AI Workbench mobile tab-bar visual pattern
 * (`src/pages/aiworkbench/index.tsx`): top-accent border tabs with the same
 * theme tokens / dark-mode active treatment.</p>
 */
import { useState } from 'react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import CognitionOverview from './overview';
import SituationDiagnosis from './situationDiagnosis';
import CausalAnalysis from './causalAnalysis';
import ScenarioSimulation from './scenarioSimulation';
import HypothesisManagement from './hypothesis';
import CognitiveModels from './cognitiveModels';
import CognitiveState from './cognitiveState';

/** One id per V2 cognition screen (no router route is registered for these). */
type CognitionSubPage =
  | 'overview'
  | 'situationDiagnosis'
  | 'causalAnalysis'
  | 'scenarioSimulation'
  | 'hypothesis'
  | 'cognitiveModels'
  | 'cognitiveState';

/** Sub-nav entry: sub-page id + i18n key + zh fallback (mirrors the page's own title call). */
interface SubNavItem {
  id: CognitionSubPage;
  labelKey: string;
  fallback: string;
}

const SUB_NAV: SubNavItem[] = [
  { id: 'overview', labelKey: 'knowledge.cognition.title.overview', fallback: '认知总览' },
  { id: 'situationDiagnosis', labelKey: 'knowledge.cognition.situation.title', fallback: '情境诊断' },
  { id: 'causalAnalysis', labelKey: 'knowledge.cognition.causal.title', fallback: '因果分析' },
  { id: 'scenarioSimulation', labelKey: 'knowledge.cognition.scenario.title', fallback: '情景推演' },
  { id: 'hypothesis', labelKey: 'knowledge.cognition.hypothesis.title', fallback: '假设管理' },
  { id: 'cognitiveModels', labelKey: 'knowledge.cognition.models.title', fallback: '认知模型' },
  { id: 'cognitiveState', labelKey: 'knowledge.cognition.state.title', fallback: '认知状态' },
];

export default function CognitionWorkbench() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [active, setActive] = useState<CognitionSubPage>('overview');

  return (
    <div className={`h-full w-full flex flex-col overflow-hidden ${styles.appBg} ${styles.appText} font-sans`}>
      {/* Sub-navigation — mirrors the AI Workbench tab-bar styling */}
      <div
        className={`flex-shrink-0 border-b overflow-x-auto whitespace-nowrap px-3 ${styles.cardBg}`}
        style={{ borderColor: styles.cardBorder }}
      >
        <div className="flex flex-nowrap gap-0.5 items-end pt-2">
          {SUB_NAV.map((item) => {
            const isActive = active === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActive(item.id)}
                className={`h-9 px-3 rounded-t-md border-t-2 flex items-center gap-2 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition ${
                  isActive
                    ? `bg-transparent border-transparent ${styles.cardText} font-bold ${styles.accentBorder} border-t-indigo-500 dark:border-t-emerald-500 dark:text-emerald-400`
                    : `border-transparent opacity-70 hover:opacity-100 ${styles.cardTextMuted}`
                }`}
              >
                {t(item.labelKey, item.fallback)}
              </button>
            );
          })}
        </div>
      </div>

      {/* Active sub-page (in-page cross-links are routed through this shell's setState) */}
      <div className={`flex-1 overflow-hidden relative flex flex-col w-full min-w-0 ${styles.appBg}`}>
        <div className="flex-1 overflow-y-auto p-6">
          {active === 'overview' && <CognitionOverview />}
          {active === 'situationDiagnosis' && (
            <SituationDiagnosis onBackToOverview={() => setActive('overview')} />
          )}
          {active === 'causalAnalysis' && (
            <CausalAnalysis onGoScenario={() => setActive('scenarioSimulation')} />
          )}
          {active === 'scenarioSimulation' && <ScenarioSimulation />}
          {active === 'hypothesis' && <HypothesisManagement />}
          {active === 'cognitiveModels' && <CognitiveModels />}
          {active === 'cognitiveState' && <CognitiveState />}
        </div>
      </div>
    </div>
  );
}
