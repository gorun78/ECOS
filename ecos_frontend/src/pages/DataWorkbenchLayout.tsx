/**
 * DataWorkbenchLayout — Shell component
 * Tab navigation + Copilot toggle + conditional rendering of 12 tab modules.
 * @license Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { DataConnection, DataSyncTask, DataPipeline, DataHealthCheck, Dataset, ObjectType } from './data-workbench/types';
import LucideIcon from './data-workbench/LucideIcon';
import ConnectionsTab from './data-workbench/tabs/ConnectionsTab';
import SyncsTab from './data-workbench/tabs/SyncsTab';
import PipelinesTab from './data-workbench/tabs/PipelinesTab';
import HealthTab from './data-workbench/tabs/HealthTab';
import DataLineageTab from './data-workbench/tabs/DataLineageTab';
import PipelineBuilderTab from './data-workbench/tabs/PipelineBuilderTab';
import CodeReposTab from './data-workbench/tabs/CodeReposTab';
import CodeWorkbooksTab from './data-workbench/tabs/CodeWorkbooksTab';
import ContourTab from './data-workbench/tabs/ContourTab';
import SqlQueryTab from './data-workbench/tabs/SqlQueryTab';
import EngineConfigTab from './data-workbench/tabs/EngineConfigTab';
import GuideTab from './data-workbench/GuideTab';
import { AddConnectionModal, AddSyncModal, AddHealthCheckModal, ExternalInterfacesDrawer } from './data-workbench/Modals';
import { CopilotPanel } from '../components/CopilotPanel';

type TabName = 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide' | 'sql-query' | 'engine-config';

interface DataWorkbenchLayoutProps {
  objectTypes?: ObjectType[];
  datasets?: Dataset[];
  onAddDataset?: (dataset: Dataset) => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
  activeTab?: TabName;
  onActiveTabChange?: (tab: TabName) => void;
}

const TAB_CONFIG: { id: TabName; icon: string; i18nKey: string }[] = [
  { id: 'guide', icon: 'Lightbulb', i18nKey: 'dw.tab.guide' },
  { id: 'connections', icon: 'Database', i18nKey: 'dw.tab.connections' },
  { id: 'syncs', icon: 'Import', i18nKey: 'dw.tab.syncs' },
  { id: 'pipelines', icon: 'Cpu', i18nKey: 'dw.tab.pipelines' },
  { id: 'health', icon: 'ShieldAlert', i18nKey: 'dw.tab.health' },
  { id: 'lineage', icon: 'Workflow', i18nKey: 'dw.tab.lineage' },
  { id: 'pipeline-builder', icon: 'Workflow', i18nKey: 'dw.tab.pipeline_builder' },
  { id: 'code-repositories', icon: 'FileCode', i18nKey: 'dw.tab.code_repositories' },
  { id: 'code-workbooks', icon: 'BookOpen', i18nKey: 'dw.tab.code_workbooks' },
  { id: 'contour', icon: 'Layers', i18nKey: 'dw.tab.contour' },
  { id: 'sql-query', icon: 'Search', i18nKey: 'dw.tab.sql_query' },
  { id: 'engine-config', icon: 'Settings', i18nKey: 'dw.tab.engine_config' },
];

export default function DataWorkbenchLayout({
  objectTypes: propObjectTypes, showToast: propShowToast, activeTab: propActiveTab, onActiveTabChange,
}: DataWorkbenchLayoutProps = {}) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const showToast = propShowToast || ((type: string, msg: string) => console.log('[toast]', type, msg));

  // ── Data loaded from API ──
  const [connections, setConnections] = useState<DataConnection[]>([]);
  const [syncTasks, setSyncTasks] = useState<DataSyncTask[]>([]);
  const [pipelines, setPipelines] = useState<DataPipeline[]>([]);
  const [healthChecks, setHealthChecks] = useState<DataHealthCheck[]>([]);

  useEffect(() => {
    import('./data-workbench/api').then(({ fetchDataConnections, fetchDataSyncTasks, fetchDataPipelines, fetchDataHealthChecks }) => {
      fetchDataConnections().then(setConnections).catch(console.error);
      fetchDataSyncTasks().then(setSyncTasks).catch(console.error);
      fetchDataPipelines().then(setPipelines).catch(console.error);
      fetchDataHealthChecks().then(setHealthChecks).catch(console.error);
    }).catch(console.error);
  }, []);

  // ── Tab navigation ──
  const [localActiveTab, setLocalActiveTab] = useState<TabName>('connections');
  const activeTab = propActiveTab !== undefined ? propActiveTab : localActiveTab;
  const setActiveTab = (tab: string) => onActiveTabChange ? onActiveTabChange(tab as TabName) : setLocalActiveTab(tab as TabName);

  // ── UI toggles ──
  const [showCopilot, setShowCopilot] = useState(false);
  const [showExtIfaces, setShowExtIfaces] = useState(false);

  // ── Selected IDs ──
  const [selConnId, setSelConnId] = useState<string>('postgres_prod_db');
  const [selTaskId, setSelTaskId] = useState<string>('sync_flights_schedule');
  const [selCheckId, setSelCheckId] = useState<string>('check_flights_freshness');
  const [editingPipelineId, setEditingPipelineId] = useState<string | null>(null);
  const [computeEngine, setComputeEngine] = useState<'memory' | 'doris'>('memory');

  // ── Pipeline builder output ──
  const [pbOutput, setPbOutput] = useState<{ datasetPath: string; columns: string[]; rowCount: number; lastCompiled: string; expressionsCount: number } | null>(null);

  // ── Connection testing ──
  const [testingConnId, setTestingConnId] = useState<string | null>(null);
  const [testingLogs, setTestingLogs] = useState<string[]>([]);

  // ── Modal toggles ──
  const [showAddConn, setShowAddConn] = useState(false);
  const [showAddSync, setShowAddSync] = useState(false);
  const [showAddCheck, setShowAddCheck] = useState(false);

  // ── Connection form ──
  const [ncName, setNcName] = useState('');
  const [ncType, setNcType] = useState<'postgresql' | 's3' | 'rest_api' | 'sftp' | 'sap'>('postgresql');
  const [ncHost, setNcHost] = useState('');
  const [ncPort, setNcPort] = useState(5432);
  const [ncUser, setNcUser] = useState('');

  // ── Sync form ──
  const [nsName, setNsName] = useState('');
  const [nsConn, setNsConn] = useState('');
  const [nsTable, setNsTable] = useState('');
  const [nsMode, setNsMode] = useState<'snapshot' | 'incremental' | 'append'>('snapshot');
  const [nsSched, setNsSched] = useState<'manual' | 'hourly' | 'daily' | 'cron'>('hourly');

  // ── Health check form ──
  const [nhName, setNhName] = useState('');
  const [nhDs, setNhDs] = useState('');
  const [nhType, setNhType] = useState<'row_count' | 'null_check' | 'schema_check' | 'freshness'>('row_count');
  const [nhThr, setNhThr] = useState('1000');

  // ── Handlers ──
  const testConnection = (connId: string) => {
    setTestingConnId(connId); setTestingLogs([]);
    const conn = connections.find(c => c.id === connId);
    if (!conn) return;
    const addLog = (msg: string, d: number) => setTimeout(() => setTestingLogs(p => [...p, msg]), d);
    addLog(`${t('databench.layout.testLog.initDriver')}[${conn.type.toUpperCase()}]`, 100);
    addLog(t('databench.layout.testLog.connecting'), 400);
    setTimeout(() => {
      if (connId === 'crew_schedules_sftp') {
        setTestingLogs(p => [...p, t('databench.layout.testLog.sshFailed'), t('databench.layout.testLog.testFailed')]);
        setConnections(p => p.map(c => c.id === connId ? { ...c, status: 'error' as const } : c));
        showToast('error', t('databench.layout.toast.connTestFailed', { name: conn.name }));
      } else {
        const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
        setTestingLogs(p => [...p, t('databench.layout.testLog.connected'), t('databench.layout.testLog.metadataOk')]);
        setConnections(p => p.map(c => c.id === connId ? { ...c, status: 'connected' as const, config: { ...c.config, lastTested: tn } } : c));
        showToast('success', t('databench.layout.toast.connTestPassed', { name: conn.name }));
      }
      setTestingConnId(null);
    }, 1800);
  };

  const createConnection = () => {
    if (!ncName.trim()) { showToast('error', t('databench.layout.toast.connNameRequired')); return; }
    const id = `conn_${Date.now().toString().slice(-4)}`;
    const c: DataConnection = { id, name: ncName, type: ncType, status: 'testing', config: { host: ncHost || 'localhost', port: ncPort, username: ncUser || 'anonymous' }, tablesAvailable: [{ name: 'raw_imported_table_1', rowCount: 25000, columns: [{ name: 'id', type: 'integer' }, { name: 'record_payload', type: 'string' }, { name: 'sync_timestamp', type: 'timestamp' }] }] };
    setConnections([...connections, c]); setSelConnId(id); setShowAddConn(false);
    showToast('success', t('databench.layout.toast.connCreated', { name: ncName }));
    setTimeout(() => testConnection(id), 500);
  };

  const triggerSync = (taskId: string) => {
    setSyncTasks(p => p.map(t => t.id === taskId ? { ...t, status: 'running' as const } : t));
    showToast('info', t('databench.layout.toast.syncRunning'));
    setTimeout(() => {
      const task = syncTasks.find(t => t.id === taskId);
      const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
      if (taskId !== 'sync_sap_costs') {
        setSyncTasks(p => p.map(t => t.id === taskId ? { ...t, status: 'success' as const, lastRunTime: tn, recordsSynced: (t.recordsSynced || 0) + Math.floor(Math.random() * 200) + 5, errorMessage: undefined } : t));
        showToast('success', t('databench.layout.toast.syncSuccess', { name: task?.name }));
      } else {
        setSyncTasks(p => p.map(t => t.id === taskId ? { ...t, status: 'failed' as const, lastRunTime: tn, errorMessage: '[SAP RFC ERROR] Gateway timed out.' } : t));
        showToast('error', t('databench.layout.toast.syncFailed', { name: task?.name }));
      }
    }, 1500);
  };

  const createSync = () => {
    if (!nsName.trim() || !nsTable.trim()) { showToast('error', t('databench.layout.toast.syncFormIncomplete')); return; }
    const t: DataSyncTask = { id: `sync_${Date.now().toString().slice(-4)}`, name: nsName, sourceConnectionId: nsConn || (connections[0]?.id || ''), sourceTable: nsTable, targetDatasetId: `ds_raw_${nsTable.toLowerCase()}`, syncMode: nsMode, schedule: nsSched, status: 'paused', recordsSynced: 0 };
    setSyncTasks([...syncTasks, t]); setSelTaskId(t.id); setShowAddSync(false);
    showToast('success', t('databench.layout.toast.syncCreated', { name: nsName }));
  };

  const createHealth = () => {
    if (!nhName.trim()) { showToast('error', t('databench.layout.toast.healthNameRequired')); return; }
    const c: DataHealthCheck = { id: `check_${Date.now().toString().slice(-4)}`, datasetId: nhDs || 'ds_flights_clean', name: nhName, checkType: nhType, config: { minRows: nhType === 'row_count' ? parseInt(nhThr) : undefined, maxNullPercentage: nhType === 'null_check' ? parseFloat(nhThr) : undefined, maxDelayMinutes: nhType === 'freshness' ? parseInt(nhThr) : undefined, targetColumn: 'pilot_id' }, status: 'pending', lastChecked: t('databench.layout.health.none'), message: t('databench.layout.health.notRun') };
    setHealthChecks([...healthChecks, c]); setSelCheckId(c.id); setShowAddCheck(false);
    showToast('success', t('databench.layout.toast.healthCreated', { name: nhName }));
  };

  const runCheck = (checkId: string) => {
    setHealthChecks(p => p.map(c => c.id === checkId ? { ...c, status: 'pending' as const, message: computeEngine === 'doris' ? t('databench.layout.health.dorisCheck') : t('databench.layout.health.memoryCheck') } : c));
    setTimeout(() => {
      const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
      setHealthChecks(p => p.map(c => {
        if (c.id !== checkId) return c;
        const finalStatus = c.checkType === 'freshness' ? 'warning' as const : 'passed' as const;
        const msg = c.checkType === 'freshness' ? t('databench.layout.health.warningFreshness') : t('databench.layout.health.passed');
        return { ...c, status: finalStatus, lastChecked: tn, message: msg };
      }));
      showToast('success', t('databench.layout.toast.healthCheckComplete'));
    }, 1000);
  };

  // ── Render ──
  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} relative overflow-hidden font-sans`}>
      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <div className={`w-52 ${styles.sidebarBg} border-r ${styles.sidebarBorder} flex flex-col justify-between shrink-0 select-none`}>
          <div className="py-3 px-3 space-y-1 overflow-y-auto">
            <div className={`text-xs font-bold ${styles.cardText} px-2.5 mb-3`}>{t('databench.layout.sidebarTitle')}</div>
            {TAB_CONFIG.map((tab, idx) => {
              const prevGrp = idx > 0 ? TAB_CONFIG[idx - 1].i18nKey.split('.')[2] : '';
              const thisGrp = tab.i18nKey.split('.')[2];
              const divider = idx === 6 || idx === 10; // divider before dev group and tools group
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
              <div className="flex justify-between"><span>{t('dw.txt.f7d9ac')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{connections.length} {t('dw.label.connections_count')}</span></div>
              <div className="flex justify-between"><span>{t('dw.txt.1988fc')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{syncTasks.length} {t('dw.label.syncs_count')}</span></div>
              <div className="flex justify-between"><span>{t('dw.txt.44a230')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{(propObjectTypes || []).length} {t('dw.label.objects_count')}</span></div>
            </div>
          </div>
        </div>

        {/* Body */}
        <div className="flex-1 flex overflow-hidden min-w-0">
          {activeTab === 'guide' && <GuideTab showToast={showToast} setActiveTab={setActiveTab} />}
          {activeTab === 'connections' && <ConnectionsTab connections={connections} setConnections={setConnections} showToast={showToast} handleCreateConnection={createConnection} testingConnId={testingConnId} setTestingConnId={setTestingConnId} testingLogs={testingLogs} selectedConnId={selConnId} setSelectedConnId={setSelConnId} showAddConn={showAddConn} setShowAddConn={setShowAddConn} newConnName={ncName} setNewConnName={setNcName} newConnType={ncType} setNewConnType={setNcType as any} newConnHost={ncHost} setNewConnHost={setNcHost} newConnPort={ncPort} setNewConnPort={setNcPort} newConnUser={ncUser} setNewConnUser={setNcUser} t={t} />}
          {activeTab === 'syncs' && <SyncsTab syncTasks={syncTasks} setSyncTasks={setSyncTasks} showToast={showToast} showAddSync={showAddSync} setShowAddSync={setShowAddSync} newSyncName={nsName} setNewSyncName={setNsName} newSyncConn={nsConn} setNewSyncConn={setNsConn} newSyncTable={nsTable} setNewSyncTable={setNsTable} newSyncMode={nsMode} setNewSyncMode={setNsMode as any} newSyncSched={nsSched} setNewSyncSched={setNsSched as any} handleCreateSyncTask={createSync} selectedTaskId={selTaskId} setSelectedTaskId={setSelTaskId} connections={connections} triggerSyncTask={triggerSync} t={t} />}
          {activeTab === 'pipelines' && <PipelinesTab pipelines={pipelines} editingPipelineId={editingPipelineId} setEditingPipelineId={setEditingPipelineId} setPipelines={setPipelines} showToast={showToast} connections={connections} computeEngine={computeEngine} setComputeEngine={setComputeEngine as any} t={t} />}
          {activeTab === 'health' && <HealthTab healthChecks={healthChecks} setHealthChecks={setHealthChecks} showToast={showToast} showAddCheck={showAddCheck} setShowAddCheck={setShowAddCheck} newCheckName={nhName} setNewCheckName={setNhName} newCheckDs={nhDs} setNewCheckDs={setNhDs} checkType={nhType} setCheckType={setNhType as any} newCheck={{}} t={t} />}
          {activeTab === 'lineage' && <DataLineageTab />}
          {activeTab === 'pipeline-builder' && <PipelineBuilderTab connections={connections} pipelines={pipelines} computeEngine={computeEngine} setComputeEngine={setComputeEngine} showToast={showToast} pipelineBuilderOutput={pbOutput} setPipelineBuilderOutput={setPbOutput} />}
          {activeTab === 'code-repositories' && <CodeReposTab />}
          {activeTab === 'code-workbooks' && <CodeWorkbooksTab />}
          {activeTab === 'contour' && <ContourTab />}
          {activeTab === 'sql-query' && <SqlQueryTab showToast={showToast} />}
          {activeTab === 'engine-config' && <EngineConfigTab showToast={showToast} />}
        </div>
      </div>

      {/* Modals */}
      {showAddConn && <AddConnectionModal t={t} locale={locale} newConnName={ncName} setNewConnName={setNcName} newConnType={ncType} setNewConnType={setNcType as any} newConnHost={ncHost} setNewConnHost={setNcHost} newConnPort={ncPort} setNewConnPort={setNcPort} newConnUser={ncUser} setNewConnUser={setNcUser} onClose={() => setShowAddConn(false)} onCreate={createConnection} />}
      {showAddSync && <AddSyncModal t={t} locale={locale} newSyncName={nsName} setNewSyncName={setNsName} newSyncConn={nsConn} setNewSyncConn={setNsConn} newSyncTable={nsTable} setNewSyncTable={setNsTable} newSyncMode={nsMode} setNewSyncMode={setNsMode as any} newSyncSched={nsSched} setNewSyncSched={setNsSched as any} connections={connections} onClose={() => setShowAddSync(false)} onCreate={createSync} />}
      {showAddCheck && <AddHealthCheckModal t={t} locale={locale} newCheckName={nhName} setNewCheckName={setNhName} newCheckDs={nhDs} setNewCheckDs={setNhDs} newCheckType={nhType} setNewCheckType={setNhType as any} newCheckThreshold={nhThr} setNewCheckThreshold={setNhThr} onClose={() => setShowAddCheck(false)} onCreate={createHealth} />}
      {showExtIfaces && <ExternalInterfacesDrawer t={t} connections={connections} onClose={() => setShowExtIfaces(false)} />}
      {showCopilot && <div className="absolute top-12 right-0 bottom-0 w-80 border-l border-[var(--border)] bg-[var(--card)] shadow-2xl z-40 flex flex-col overflow-hidden"><CopilotPanel agentType="data" /></div>}
    </div>
  );
}

export function DataWorkbenchLayoutStandalone() {
  const { styles } = useTheme();
  return <div className={`h-screen flex flex-col ${styles.appBg} ${styles.appText} font-sans`}><DataWorkbenchLayout /></div>;
}
