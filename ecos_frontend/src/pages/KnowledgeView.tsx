// @ts-nocheck
// 知识工作台主导航（B8 / Q5：15 Tab / 7 组）
import { useCallback, useMemo, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import {
  ArrowLeft,
  Binary,
  ListChecks,
  Database,
  Download,
  FileText,
  Gauge,
  GitBranch,
  LayoutDashboard,
  Network,
  Settings,
  Shield,
  ShieldCheck,
  Star,
  Tag,
  Workflow,
  Zap,
} from 'lucide-react';
import { useTheme } from '../components/ThemeContext';
import { useLanguage } from '../components/LanguageContext';
import { KNOWLEDGE_TAB_GROUPS, type KnowledgeTabId } from './knowledge/typesAndConstants';
import OverviewDashboard from './knowledge/tabs/OverviewDashboard';
import DataWorkbenchImportTab from './knowledge/tabs/DataWorkbenchImportTab';
import DocumentUploadTab from './knowledge/tabs/DocumentUploadTab';
import ExtractionReviewTab from './knowledge/tabs/ExtractionReviewTab';
import OntologyModelTab from './knowledge/tabs/OntologyModelTab';
import GraphBuilderTab from './knowledge/tabs/GraphBuilderTab';
import VectorIndexTab from './knowledge/tabs/VectorIndexTab';
import ClassificationTab from './knowledge/tabs/ClassificationTab';
import RagTab from './knowledge/tabs/RagTab';
import GraphExplorerTab from './knowledge/tabs/GraphExplorerTab';
import KnowledgeRuleRepositoryTab from './knowledge/tabs/KnowledgeRuleRepositoryTab';
import KnowledgeEvalTab from './knowledge/tabs/KnowledgeEvalTab';
import LifecycleManagerTab from './knowledge/tabs/LifecycleManagerTab';
import ComplianceTab from './knowledge/tabs/KnowledgeComplianceCheckTab';
import EngineConfigTab from './knowledge/tabs/EngineConfigTab';

const ICON_MAP: Record<string, LucideIcon> = {
  LayoutDashboard,
  Download,
  FileText,
  ListChecks,
  Workflow,
  Database,
  Binary,
  Tag,
  Zap,
  Network,
  ShieldCheck,
  Gauge,
  GitBranch,
  Shield,
  Settings,
};

const TAB_COMPONENTS: Record<KnowledgeTabId, React.ComponentType> = {
  overview: OverviewDashboard,
  import: DataWorkbenchImportTab,
  upload: DocumentUploadTab,
  review: ExtractionReviewTab,
  ontology_model: OntologyModelTab,
  graph_build: GraphBuilderTab,
  vector_index: VectorIndexTab,
  classification: ClassificationTab,
  rag: RagTab,
  graph_explorer: GraphExplorerTab,
  rules: KnowledgeRuleRepositoryTab,
  eval: KnowledgeEvalTab,
  lifecycle: LifecycleManagerTab,
  compliance: ComplianceTab,
  engine_config: EngineConfigTab,
};

interface KnowledgeViewProps {
  onBack?: () => void;
  activeTab?: KnowledgeTabId;
}

export default function KnowledgeView({ onBack, activeTab: controlledTab }: KnowledgeViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [internalTab, setInternalTab] = useState<KnowledgeTabId>('overview');
  const [showToast] = useState(false);
  const [starred] = useState<Record<string, boolean>>({});
  const prevTabRef = useRef<KnowledgeTabId>('overview');

  const activeTab = controlledTab ?? internalTab;
  const setActiveTab = useCallback((id: KnowledgeTabId) => {
    setInternalTab(id);
  }, []);

  const groups = useMemo(() => KNOWLEDGE_TAB_GROUPS, []);
  const TabIcon = ICON_MAP[groups.find(g => g.tabs.some(x => x.id === activeTab))?.tabs.find(x => x.id === activeTab)?.icon || 'LayoutDashboard'];

  const activeGroup = groups.find(g => g.tabs.some(x => x.id === activeTab));
  const ActiveTabComponent = TAB_COMPONENTS[activeTab] ?? OverviewDashboard;

  // PMO-49 v1.4 默认"knowledge" tab，fallback 时避免从 null 退化
  const safePrevTab = (prevTabRef.current && TAB_COMPONENTS[prevTabRef.current]) || 'overview';
  const handleTabSwitch = (id: KnowledgeTabId) => {
    const comp = TAB_COMPONENTS[id];
    if (!comp) return;
    setActiveTab(id);
    prevTabRef.current = safePrevTab;
  };

  return (
    <div className="flex h-full overflow-hidden" style={{ background: styles.appBg, color: styles.cardText }}>
      {/* 左侧导航 */}
      <nav className="w-64 flex-shrink-0 border-r flex flex-col" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        {/* Header */}
        <div className="h-14 flex items-center gap-2 px-4 border-b" style={{ borderColor: styles.cardBorder }}>
          <button
            onClick={onBack}
            className="p-1 rounded hover:opacity-70"
            style={{ color: styles.cardTextMuted }}
            aria-label={t('knowledge.back')}
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="font-semibold text-sm leading-tight">
              <span className="tracking-[0.25em] uppercase">{t('knowledge.navigation.title')}</span>
            </div>
            <div className="text-[10px] font-mono tracking-wider uppercase opacity-60">{t('knowledge.navigation.subtitle')}</div>
          </div>
        </div>

        {/* Tab 列表 */}
        <div className="flex-1 overflow-y-auto py-3">
          {groups.map(group => {
            const groupTabs = group.tabs as readonly (typeof group.tabs)[number][];
            return (
              <div key={group.id} className="mb-3">
                <div className="px-4 pb-1.5 text-[10px] font-mono tracking-wider uppercase opacity-50">
                  {t(`knowledge.group.${group.id}`)}
                </div>
                {groupTabs.map(tab => {
                  const isActive = activeTab === tab.id;
                  return (
                    <button
                      key={tab.id}
                      onClick={() => handleTabSwitch(tab.id)}
                      className={`w-full flex items-center gap-2 px-4 py-2 text-xs transition-colors border-l-2 ${
                        isActive ? `${styles.accentBg} ${styles.accentBorder} font-semibold` : `border-transparent hover:opacity-70`
                      }`}
                      style={isActive ? { color: '#fff' } : { color: styles.cardText }}
                    >
                      <Star className="w-3.5 h-3.5 opacity-50" style={isActive ? { color: '#fff' } : {}} />
                      <span className="truncate">
                        {t(`knowledge.nav.${tab.id}`)}
                      </span>
                    </button>
                  );
                })}
              </div>
            );
          })}
        </div>

        {/* 底部状态 */}
        <div className="px-4 py-3 border-t text-[10px] font-mono tracking-wider uppercase opacity-50" style={{ borderColor: styles.cardBorder }}>
          <div className="flex items-center gap-1.5">
            <span className={styles.successText}>●</span>
            <span>{t('knowledge.engine')}</span>
          </div>
          <div className="mt-0.5 opacity-70">KB · Cogn · Sec · Data</div>
        </div>
      </nav>

      {/* 右侧内容 */}
      <main className="flex-1 flex flex-col overflow-hidden">
        {/* Top bar */}
        <div
          className="h-14 flex-shrink-0 flex items-center justify-between px-6 border-b"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          <div className="flex items-center gap-2 text-sm">
            <TabIcon className="w-4 h-4" />
            <span className="font-medium">{activeGroup ? t(`knowledge.group.${activeGroup.id}`) : ''}</span>
            <span className="text-xs opacity-50">/</span>
            <span className="font-semibold">{t(`knowledge.nav.${activeTab}`)}</span>
          </div>
          <div className="text-[10px] font-mono tracking-wider uppercase opacity-60">
            {t('knowledge.nav.updated_tag')}
          </div>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-hidden">
          <ActiveTabComponent showToast={showToast} />
        </div>
      </main>
    </div>
  );
}
