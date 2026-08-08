/**
 * DomainDesignerView — 本体设计器页 Shell
 *
 * 三栏布局: EntityTreePanel | DomainCanvas | PropertyEditor
 * Shell 只保留状态管理 + 布局编排，子组件通过 props 通信。
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Network, Plus, GitBranch, Maximize2,
  PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen, Sparkles, History,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import type { ThemeStyles } from '../components/ThemeContext';
import { useWorkbenchStore } from '../stores/useWorkbenchStore';
import { extractDomainCode } from './ontology/ontologyHelpers';
import EntityTreePanel from './ontology/EntityTreePanel';
import DomainCanvas from './ontology/DomainCanvas';
import PropertyEditor from './ontology/PropertyEditor';
import CreateEntityModal from '../components/ontology-workbench/modals/CreateEntityModal';
import CreateRelationshipModal from '../components/ontology-workbench/modals/CreateRelationshipModal';
import AutoDiscoverPanel from './ontology/AutoDiscoverPanel';
import VersionTimeline from './ontology/VersionTimeline';

// ── 缺域参数提示 ──────────────────────────────────────────

function MissingDomainView({ t, styles, onBack }: { t: (k: string) => string; styles: ThemeStyles; onBack: () => void }) {
  return (
    <div className={`flex-1 flex items-center justify-center ${styles.appBg}`}>
      <div className="text-center">
        <Network size={40} className="text-slate-500 mx-auto mb-3 opacity-30" />
        <p className="text-sm text-red-400">{t('ontology.designer.missingDomainParam')}</p>
        <p className="text-xs text-slate-500 mt-1">{t('ontology.designer.enterFromDomainList')}</p>
        <button
          onClick={onBack}
          className="mt-4 px-4 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600"
        >
          {t('ontology.designer.backToDomainList')}
        </button>
      </div>
    </div>
  );
}

// ── 主组件 ──────────────────────────────────────────────────

export default function DomainDesignerView() {
  const domainCode = useMemo(() => extractDomainCode(), []);
  const { styles } = useTheme();
  const store = useWorkbenchStore();
  const { t } = useLanguage();
  const { entities, relationships, selectedEntityId, entitiesLoading,
    relationshipsLoading, savingEntity, error, leftPanelCollapsed,
    rightPanelCollapsed } = store;

  const [showCreateEntity, setShowCreateEntity] = useState(false);
  const [showCreateRelation, setShowCreateRelation] = useState(false);
  const [createRelationSourceId, setCreateRelationSourceId] = useState<string>('');
  const [entitySearch, setEntitySearch] = useState('');
  const [showAutoDiscover, setShowAutoDiscover] = useState(false);
  const [showVersionTimeline, setShowVersionTimeline] = useState(false);

  useEffect(() => {
    if (domainCode) { store.setCurrentDomain(domainCode); store.fetchDomainData(); }
  }, [domainCode]);

  const selectedEntity = useMemo(
    () => entities.find((e) => e.id === selectedEntityId),
    [entities, selectedEntityId],
  );

  const entityRelationships = useMemo(() => {
    if (!selectedEntityId) return [];
    return relationships.filter((r) => r.sourceEntityId === selectedEntityId || r.targetEntityId === selectedEntityId);
  }, [relationships, selectedEntityId]);

  const handleBack = useCallback(() => { window.location.hash = '#/ontology_workbench/domains'; }, []);
  const handleOpenCreateRelation = useCallback((sourceId?: string) => {
    setCreateRelationSourceId(sourceId || selectedEntityId || '');
    setShowCreateRelation(true);
  }, [selectedEntityId]);

  const isLoading = entitiesLoading || relationshipsLoading;

  if (!domainCode) return <MissingDomainView t={t} styles={styles} onBack={handleBack} />;

  return (
    <div className={`flex-1 flex h-full overflow-hidden ${styles.appBg} font-sans`}>
      <EntityTreePanel
        entities={entities} entitiesLoading={entitiesLoading}
        selectedEntityId={selectedEntityId} entitySearch={entitySearch}
        onSearchChange={setEntitySearch} onSelectEntity={(id) => store.selectEntity(id)}
        onBack={handleBack} domainCode={domainCode} relationships={relationships}
        onCreateEntity={() => setShowCreateEntity(true)}
        leftPanelCollapsed={leftPanelCollapsed}
      />

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* 工具栏 */}
        <div className={`flex items-center justify-between px-4 py-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <div className="flex items-center gap-1.5">
            <button onClick={() => store.toggleLeftPanel()}
              className={`p-1.5 rounded hover:${styles.cardBg}/5 text-slate-400 hover:text-slate-300 transition`}
              title={leftPanelCollapsed ? t('ontology.designer.expandEntityTree') : t('ontology.designer.collapseEntityTree')}>
              {leftPanelCollapsed ? <PanelLeftOpen size={14} /> : <PanelLeftClose size={14} />}
            </button>
            <div className={`w-px h-5 border-l ${styles.cardBorder} mx-1`} />
            <Network size={14} className="text-indigo-400" />
            <span className="text-xs font-medium text-slate-300">{t('ontology.designer.ontologyDesigner')}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <button onClick={() => setShowCreateEntity(true)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 hover:bg-indigo-600/30 transition">
              <Plus size={11} />{t('ontology.designer.entity')}
            </button>
            <button disabled={entities.length < 2} onClick={() => handleOpenCreateRelation()}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] bg-emerald-600/20 text-emerald-300 border border-emerald-500/30 hover:bg-emerald-600/30 disabled:opacity-30 disabled:cursor-not-allowed transition">
              <GitBranch size={11} />{t('ontology.designer.relationship')}
            </button>
            <button onClick={() => setShowAutoDiscover(true)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] bg-purple-600/20 text-purple-300 border border-purple-500/30 hover:bg-purple-600/30 transition">
              <Sparkles size={11} />{t('ontology.autoDiscover.title')}
            </button>
            <button onClick={() => setShowVersionTimeline(true)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] bg-cyan-600/20 text-cyan-300 border border-cyan-500/30 hover:bg-cyan-600/30 transition">
              <History size={11} />{t('ontology.version.title')}
            </button>
            <div className={`w-px h-5 border-l ${styles.cardBorder} mx-1`} />
            <button className={`p-1.5 rounded hover:${styles.cardBg}/5 text-slate-400 hover:text-slate-300 transition`}
              title={t('ontology.designer.autoLayout')}>
              <Maximize2 size={13} />
            </button>
            <button onClick={() => store.toggleRightPanel()}
              className={`p-1.5 rounded hover:${styles.cardBg}/5 text-slate-400 hover:text-slate-300 transition`}
              title={rightPanelCollapsed ? t('ontology.designer.expandPropertyPanel') : t('ontology.designer.collapsePropertyPanel')}>
              {rightPanelCollapsed ? <PanelRightOpen size={14} /> : <PanelRightClose size={14} />}
            </button>
          </div>
        </div>

        <div className="flex-1 relative overflow-hidden">
          <DomainCanvas entities={entities} relationships={relationships}
            selectedEntityId={selectedEntityId} onSelectEntity={(id) => store.selectEntity(id as string)}
            onCreateRelation={handleOpenCreateRelation} isLoading={isLoading}
            error={error} onRetry={() => store.fetchDomainData()}
            domainCode={domainCode}
            onEditEntity={(id) => {
              // 选中实体后可通过 PropertyEditor 编辑
              store.selectEntity(id as string);
            }}
            onDeleteEntity={(id) => store.deleteEntity(id)}
            onAddProperty={(id) => {
              // Sprint 3: 打开属性编辑面板
              store.selectEntity(id as string);
            }}
          />
        </div>
      </div>

      {!rightPanelCollapsed && (
        <div className={`w-[320px] min-w-[260px] border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-y-auto`}>
          <PropertyEditor entity={selectedEntity || null} relationships={entityRelationships}
            allEntities={entities} onCreateRelation={handleOpenCreateRelation} saving={savingEntity} />
        </div>
      )}

      <CreateEntityModal open={showCreateEntity} onClose={() => setShowCreateEntity(false)} domainCode={domainCode} />
      <CreateRelationshipModal open={showCreateRelation} onClose={() => setShowCreateRelation(false)}
        sourceEntityId={createRelationSourceId} entities={entities} />
      {showAutoDiscover && <AutoDiscoverPanel domainCode={domainCode} onClose={() => setShowAutoDiscover(false)} />}
      {showVersionTimeline && <VersionTimeline domainCode={domainCode} onClose={() => setShowVersionTimeline(false)} />}
    </div>
  );
}
