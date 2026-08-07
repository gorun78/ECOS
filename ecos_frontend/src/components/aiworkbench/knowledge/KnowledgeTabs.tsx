/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ThemeStyles } from '../../../components/ThemeContext';
import * as Icons from 'lucide-react';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export type KnowledgeSubTab = 'architecture' | 'sync' | 'lineage' | 'ontology' | 'index' | 'rag';

interface KnowledgeTabsProps {
  styles: ThemeStyles;
  activeSubTab: KnowledgeSubTab;
  setActiveSubTab: (tab: KnowledgeSubTab) => void;
}

export default function KnowledgeTabs({ styles, activeSubTab, setActiveSubTab }: KnowledgeTabsProps) {
  const { t } = useLanguage();
  return (
    <div className={`w-48 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col shrink-0 h-full p-2.5 space-y-1`}>
      <div className={`px-2 py-1.5 text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider`}>
        {t('aiworkbench.knowledge.sidebarTitle')}
      </div>
      {[
        { id: 'architecture', label: t('aiworkbench.knowledge.tabArchitecture'), icon: 'FileText' },
        { id: 'sync', label: t('aiworkbench.knowledge.tabSync'), icon: 'Combine' },
        { id: 'lineage', label: t('aiworkbench.knowledge.tabLineage'), icon: 'Network' },
        { id: 'ontology', label: t('aiworkbench.knowledge.tabOntology'), icon: 'Workflow' },
        { id: 'index', label: t('aiworkbench.knowledge.tabIndex'), icon: 'Cpu' },
        { id: 'rag', label: t('aiworkbench.knowledge.tabRag'), icon: 'SearchCheck' }
      ].map(item => {
        const isActive = activeSubTab === item.id;
        return (
          <button
            key={item.id}
            onClick={() => setActiveSubTab(item.id as KnowledgeSubTab)}
            className={`w-full px-3 py-2 rounded-lg font-bold text-left flex items-center gap-2 transition-all cursor-pointer ${
              isActive
                ? `${styles.accentBg} text-white shadow-xs`
                : 'styles.cardTextMuted hover:styles.inputBg'
            }`}
          >
            <Icon name={item.icon} size={13} />
            <span>{item.label}</span>
          </button>
        );
      })}

      <div className={`mt-auto p-2.5 ${styles.inputBg} rounded-xl border ${styles.cardBorder} space-y-2`}>
        <p className={`font-extrabold text-[10px] ${styles.cardText} flex items-center gap-1.5`}>
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
          <span>{t('aiworkbench.knowledge.readyStatus')}</span>
        </p>
        <p className={`text-[9px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
          {t('aiworkbench.knowledge.sidebarDesc')}
        </p>
      </div>
    </div>
  );
}
