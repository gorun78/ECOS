/**
 * 场景引擎配置页（全局单份，2026-11-27 用户裁决）。
 *
 * <p>三块内容：算法池（原「场景配置」页迁入）、场景模板（原独立模板页迁入）、
 * 认知资产（信念/假设/证据）分类维护（只读分类视图 + 既有写动作）。</p>
 *
 * <p>入口：L1 场景工作台页头右侧；旧路径 {@code /workbench/config}、
 * {@code /workbench/templates} 在路由层重定向到本页对应 Tab。</p>
 */
import React, { useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, BrainCircuit, Cpu, LayoutGrid } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import AlgorithmsPanel from './AlgorithmsPanel';
import TemplatesPanel from './TemplatesPanel';
import CognitiveAssetsPanel from './CognitiveAssetsPanel';

type EngineTab = 'algorithms' | 'templates' | 'cognitive';

const TAB_ORDER: EngineTab[] = ['algorithms', 'templates', 'cognitive'];

export default function ScenarioEngineConfig() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();

  const tab = useMemo<EngineTab>(() => {
    const raw = searchParams.get('tab');
    return raw === 'templates' || raw === 'cognitive' ? raw : 'algorithms';
  }, [searchParams]);

  const switchTab = (next: EngineTab) => {
    const p = new URLSearchParams(searchParams);
    p.set('tab', next);
    setSearchParams(p, { replace: true });
  };

  const tabMeta: Record<EngineTab, { icon: React.ReactNode; key: string }> = {
    algorithms: { icon: <Cpu size={12} />, key: 'workspace.enginecfg.tab.algorithms' },
    templates: { icon: <LayoutGrid size={12} />, key: 'workspace.enginecfg.tab.templates' },
    cognitive: { icon: <BrainCircuit size={12} />, key: 'workspace.enginecfg.tab.cognitive' },
  };

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      <div className={`p-3 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex items-center gap-2 flex-wrap`}>
        <Link
          to="/workbench"
          className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <ArrowLeft size={12} />
          {t('workspace.config.back')}
        </Link>
        <h1 className={`text-lg font-bold ${styles.appText}`}>{t('workspace.enginecfg.title')}</h1>
        <div className="ml-auto flex items-center gap-1.5 flex-wrap justify-end">
          {TAB_ORDER.map((id) => {
            const active = tab === id;
            const cls = active
              ? `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`
              : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
            return (
              <button
                key={id}
                type="button"
                onClick={() => switchTab(id)}
                aria-pressed={active}
                className={`inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded border cursor-pointer ${cls}`}
              >
                {tabMeta[id].icon}
                {t(tabMeta[id].key)}
              </button>
            );
          })}
        </div>
      </div>

      <div className="p-4">
        {tab === 'algorithms' && <AlgorithmsPanel />}
        {tab === 'templates' && <TemplatesPanel />}
        {tab === 'cognitive' && <CognitiveAssetsPanel />}
      </div>
    </div>
  );
}
