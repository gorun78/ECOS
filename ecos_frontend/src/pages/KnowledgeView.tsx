import React, { useState, useCallback } from 'react';
import {
  FileText, Combine, Network, Workflow, Cpu, SearchCheck,
  Database, Tag, Brain,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { KNOWLEDGE_TAB_GROUPS, type KnowledgeTabId } from './knowledge/typesAndConstants';
import ClosedLoopTab from './knowledge/tabs/ClosedLoopTab';
import SyncTab from './knowledge/tabs/SyncTab';
import LineageTab from './knowledge/tabs/LineageTab';
import OntologyTab from './knowledge/tabs/OntologyTab';
import IndexTab from './knowledge/tabs/IndexTab';
import RagTab from './knowledge/tabs/RagTab';
import GraphSyncTab from './knowledge/tabs/GraphSyncTab';
import ClassificationTab from './knowledge/tabs/ClassificationTab';
import CognitiveConfigTab from './knowledge/tabs/CognitiveConfigTab';

const ICON_MAP: Record<string, React.ComponentType<{ size?: number; className?: string }>> = {
  FileText, Combine, Network, Workflow, Cpu, SearchCheck,
  Database, Tag, Brain,
};

const TAB_COMPONENTS: Record<KnowledgeTabId, React.ComponentType> = {
  closed_loop: ClosedLoopTab,
  sync: SyncTab,
  lineage: LineageTab,
  ontology: OntologyTab,
  graph_sync: GraphSyncTab,
  classification: ClassificationTab,
  index: IndexTab,
  rag: RagTab,
  cognitive_config: CognitiveConfigTab,
};

export default function KnowledgeView() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState<KnowledgeTabId>('closed_loop');
  const [toast, setToast] = useState<{ type: 'success' | 'info' | 'error'; msg: string } | null>(null);

  const showToast = useCallback((type: 'success' | 'info' | 'error', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3500);
  }, []);

  const ActiveComponent = TAB_COMPONENTS[activeTab];

  return (
    <div className="flex h-full select-none text-xs overflow-hidden">
      <div className={`w-48 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col shrink-0 h-full p-2.5 space-y-1 overflow-y-auto`}>
        <div className={`px-2 py-1.5 text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider`}>
          {locale === 'zh' ? '知识工作台' : 'Knowledge Workbench'}
        </div>

        {KNOWLEDGE_TAB_GROUPS.map(group => (
          <React.Fragment key={group.id}>
            <div className={`px-2 pt-3 pb-1 text-[9px] font-bold ${styles.muted} uppercase tracking-wider`}>
              {locale === 'zh' ? group.labelZh : group.label}
            </div>
            {group.tabs.map(tab => {
              const isActive = activeTab === tab.id;
              const Icon = ICON_MAP[tab.icon];
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`w-full px-3 py-2 rounded-lg font-bold text-left flex items-center gap-2 transition-all cursor-pointer ${
                    isActive
                      ? `${styles.accentBg} text-white shadow-xs`
                      : `${styles.cardTextMuted} hover:bg-slate-800/30`
                  }`}
                >
                  {Icon && <Icon size={13} />}
                  <span>{locale === 'zh' ? tab.labelZh : tab.label}</span>
                </button>
              );
            })}
          </React.Fragment>
        ))}

        <div className={`mt-auto p-2.5 ${styles.cardBg} rounded-xl border ${styles.cardBorder} space-y-2`}>
          <p className="font-extrabold text-[10px] text-slate-100 flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            <span>{locale === 'zh' ? '知识底座就绪' : 'Knowledge Ready'}</span>
          </p>
          <p className="text-[9px] text-slate-400 leading-relaxed font-sans">
            {locale === 'zh' ? '统一知识体系：数据 → 图谱 → 检索' : 'Unified: Data → Graph → Retrieval'}
          </p>
        </div>
      </div>

      <div className={`flex-1 ${styles.appBg} p-6 overflow-y-auto h-full`}>
        {ActiveComponent && <ActiveComponent />}
      </div>

      {toast && (
        <div className="fixed bottom-6 right-6 z-50 animate-fade-in">
          <div className={`px-4 py-2.5 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2
            ${toast.type === 'success' ? 'bg-emerald-600 text-white' : ''}
            ${toast.type === 'error' ? 'bg-red-600 text-white' : ''}
            ${toast.type === 'info' ? 'bg-slate-700 text-slate-100' : ''}`}>
            {toast.msg}
          </div>
        </div>
      )}
    </div>
  );
}
