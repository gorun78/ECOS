/**
 * DataWorkbenchLayout — Shell component
 * Tab navigation + Copilot toggle + conditional rendering of 6 tab modules.
 *
 * PMO-3I (2026-08-25): consolidated 9 tabs → 6 tabs.
 *   - removed: guide, syncs, pipelines (as separate tabs)
 *   - merged syncs + pipelines into pipeline-builder (list + editor dual-pane)
 *   - final tabs: connections / pipeline-builder / health / lineage / sql-query / engine-config
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import type { ObjectType, Dataset } from './data-workbench/types';
import LucideIcon from './data-workbench/LucideIcon';
import ConnectionsTab from './data-workbench/tabs/ConnectionsTab';
import HealthTab from './data-workbench/tabs/HealthTab';
import DataLineageTab from './data-workbench/tabs/DataLineageTab';
import PipelineBuilderTab from './data-workbench/tabs/PipelineBuilderTab';
import SqlQueryTab from './data-workbench/tabs/SqlQueryTab';
import EngineConfigTab from './data-workbench/tabs/EngineConfigTab';
import { AddConnectionModal, AddSyncModal, AddHealthCheckModal, ExternalInterfacesDrawer } from './data-workbench/Modals';
import { CopilotPanel } from '../components/CopilotPanel';
import { useDataWorkbench } from './data-workbench/hooks/useDataWorkbench';

type TabName = 'connections' | 'pipeline-builder' | 'health' | 'lineage' | 'sql-query' | 'engine-config';

interface DataWorkbenchLayoutProps {
  objectTypes?: ObjectType[];
  datasets?: Dataset[];
  onAddDataset?: (dataset: Dataset) => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
  activeTab?: TabName;
  onActiveTabChange?: (tab: TabName) => void;
}

const TAB_CONFIG: { id: TabName; icon: string; i18nKey: string }[] = [
  { id: 'connections', icon: 'Database', i18nKey: 'dw.tab.connections' },
  { id: 'pipeline-builder', icon: 'Workflow', i18nKey: 'dw.tab.pipeline_builder' },
  { id: 'health', icon: 'ShieldAlert', i18nKey: 'dw.tab.health' },
  { id: 'lineage', icon: 'Workflow', i18nKey: 'dw.tab.lineage' },
  { id: 'sql-query', icon: 'Search', i18nKey: 'dw.tab.sql_query' },
  { id: 'engine-config', icon: 'Settings', i18nKey: 'dw.tab.engine_config' },
];

export default function DataWorkbenchLayout({
  objectTypes: propObjectTypes, showToast: propShowToast, activeTab: propActiveTab, onActiveTabChange,
}: DataWorkbenchLayoutProps = {}) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const showToast = propShowToast || ((type: string, msg: string) => console.log('[toast]', type, msg));

  const dw = useDataWorkbench(showToast, t);

  // ── Tab navigation ──
  const [localActiveTab, setLocalActiveTab] = useState<TabName>('connections');
  const activeTab = propActiveTab !== undefined ? propActiveTab : localActiveTab;
  const setActiveTab = (tab: string) => onActiveTabChange ? onActiveTabChange(tab as TabName) : setLocalActiveTab(tab as TabName);

  // ── UI toggles ──
  const [showCopilot, setShowCopilot] = useState(false);
  const [showExtIfaces, setShowExtIfaces] = useState(false);

  // ── Render ──
  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} relative overflow-hidden font-sans`}>
      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <div className={`w-52 ${styles.sidebarBg} border-r ${styles.sidebarBorder} flex flex-col justify-between shrink-0 select-none`}>
          <div className="py-3 px-3 space-y-1 overflow-y-auto">
            <div className={`text-xs font-bold ${styles.cardText} px-2.5 mb-3`}>{t('databench.layout.sidebarTitle')}</div>
            {TAB_CONFIG.map((tab, idx) => {
              const divider = idx === 4; // divider before tools group (sql-query, engine-config)
              return (
                <React.Fragment key={tab.id}>
                  {divider && <div className={`my-2 border-t ${styles.cardBorder}`} />}
                  <button onClick={() => setActiveTab(tab.id)}
                    className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${activeTab === tab.id ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} border-l-2 ${styles.accentBorder} font-extrabold shadow-sm` : `${styles.cardTextMuted} hover:opacity-80`}`}>
                    <LucideIcon name={tab.icon} size={14} className={activeTab === tab.id ? styles.accentText : styles.cardTextMuted} />
                    <span className="truncate">{t(tab.i18nKey)}</span>
                  </button>
                </React.Fragment>
              );
            })}
          </div>
          <div>
            <div className="px-3 pb-2">
              <button onClick={() => setShowCopilot(!showCopilot)}
                className={`w-full flex items-center gap-2 px-3 py-1.5 text-[10px] rounded transition-colors cursor-pointer ${showCopilot ? `${styles.accentBg} ${styles.accentText}` : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}`}>
                <LucideIcon name="MessageSquare" size={11} /><span>{t('dw.btn.copilot')}</span>
              </button>
            </div>
            <div className={`p-4 border-t ${styles.cardBorder} ${styles.cardBg} space-y-2 text-[10px] ${styles.cardTextMuted}`}>
              <div className={`font-semibold ${styles.cardText}`}>{t('dw.txt.419d9f')}</div>
              <div className="flex justify-between"><span>{t('dw.txt.f7d9ac')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{dw.connections.length} {t('dw.label.connections_count')}</span></div>
              <div className="flex justify-between"><span>{t('dw.txt.1988fc')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{dw.syncTasks.length} {t('dw.label.syncs_count')}</span></div>
              <div className="flex justify-between"><span>{t('dw.txt.44a230')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{(propObjectTypes || []).length} {t('dw.label.objects_count')}</span></div>
            </div>
          </div>
        </div>

        {/* Body */}
        <div className="flex-1 flex overflow-hidden min-w-0">
          {activeTab === 'connections' && <ConnectionsTab connections={dw.connections} setConnections={dw.setConnections} showToast={showToast} handleCreateConnection={dw.createConnection} testingConnId={dw.testingConnId} setTestingConnId={dw.setTestingConnId} testingLogs={dw.testingLogs} selectedConnId={dw.selConnId} setSelectedConnId={dw.setSelConnId} showAddConn={dw.showAddConn} setShowAddConn={dw.setShowAddConn} newConnName={dw.ncName} setNewConnName={dw.setNcName} newConnType={dw.ncType} setNewConnType={dw.setNcType as any} newConnHost={dw.ncHost} setNewConnHost={dw.setNcHost} newConnPort={dw.ncPort} setNewConnPort={dw.setNcPort} newConnUser={dw.ncUser} setNewConnUser={dw.setNcUser} t={t} />}
          {activeTab === 'pipeline-builder' && <PipelineBuilderTab connections={dw.connections} pipelines={dw.pipelines} syncTasks={dw.syncTasks} computeEngine={dw.computeEngine} setComputeEngine={dw.setComputeEngine} showToast={showToast} onPipelinesChange={dw.setPipelines} onSyncTasksChange={dw.setSyncTasks} onTriggerSync={dw.triggerSync} />}
          {activeTab === 'health' && <HealthTab healthChecks={dw.healthChecks} setHealthChecks={dw.setHealthChecks} showToast={showToast} showAddCheck={dw.showAddCheck} setShowAddCheck={dw.setShowAddCheck} newCheckName={dw.nhName} setNewCheckName={dw.setNhName} newCheckDs={dw.nhDs} setNewCheckDs={dw.setNhDs} checkType={dw.nhType} setCheckType={dw.setNhType as any} newCheck={{}} t={t} />}
          {activeTab === 'lineage' && <DataLineageTab />}
          {activeTab === 'sql-query' && <SqlQueryTab showToast={showToast} />}
          {activeTab === 'engine-config' && <EngineConfigTab showToast={showToast} />}
        </div>
      </div>

      {/* Modals */}
      {dw.showAddConn && <AddConnectionModal t={t} locale={locale} newConnName={dw.ncName} setNewConnName={dw.setNcName} newConnType={dw.ncType} setNewConnType={dw.setNcType as any} newConnHost={dw.ncHost} setNewConnHost={dw.setNcHost} newConnPort={dw.ncPort} setNewConnPort={dw.setNcPort} newConnUser={dw.ncUser} setNewConnUser={dw.setNcUser} onClose={() => dw.setShowAddConn(false)} onCreate={dw.createConnection} />}
      {dw.showAddSync && <AddSyncModal t={t} locale={locale} newSyncName={dw.nsName} setNewSyncName={dw.setNsName} newSyncConn={dw.nsConn} setNewSyncConn={dw.setNsConn} newSyncTable={dw.nsTable} setNewSyncTable={dw.setNsTable} newSyncMode={dw.nsMode} setNewSyncMode={dw.setNsMode as any} newSyncSched={dw.nsSched} setNewSyncSched={dw.setNsSched as any} connections={dw.connections} onClose={() => dw.setShowAddSync(false)} onCreate={dw.createSync} />}
      {dw.showAddCheck && <AddHealthCheckModal t={t} locale={locale} newCheckName={dw.nhName} setNewCheckName={dw.setNhName} newCheckDs={dw.nhDs} setNewCheckDs={dw.setNhDs} newCheckType={dw.nhType} setNewCheckType={dw.setNhType as any} newCheckThreshold={dw.nhThr} setNewCheckThreshold={dw.setNhThr} onClose={() => dw.setShowAddCheck(false)} onCreate={dw.createHealth} />}
      {showExtIfaces && <ExternalInterfacesDrawer t={t} connections={dw.connections} onClose={() => setShowExtIfaces(false)} />}
      {showCopilot && <div className={`absolute top-12 right-0 bottom-0 w-80 border-l ${styles.cardBorder} ${styles.cardBg} shadow-2xl z-40 flex flex-col overflow-hidden`}><CopilotPanel agentType="data" /></div>}
    </div>
  );
}

export function DataWorkbenchLayoutStandalone() {
  const { styles } = useTheme();
  return <div className={`h-screen flex flex-col ${styles.appBg} ${styles.appText} font-sans`}><DataWorkbenchLayout /></div>;
}
