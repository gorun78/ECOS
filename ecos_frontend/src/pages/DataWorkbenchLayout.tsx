/**
 * DataWorkbenchLayout — Shell component
 * Tab navigation + Copilot toggle + conditional rendering of tab modules.
 *
 * PMO-3I (2026-08-25): consolidated 9 tabs → 6 tabs.
 *   - removed: guide, syncs, pipelines (as separate tabs)
 *   - merged syncs + pipelines into pipeline-builder (list + editor dual-pane)
 *   - final tabs: connections / pipeline-builder / health / lineage / engine-config
 * 数据质量（health）内嵌为工作台内 Tab，保留工作台侧边菜单；
 * 独立路由 #/dq_dashboard 保留给 Topbar 入口与深链（?tab=&table=）使用。
 * @license Apache-2.0
 */

import React, { useState, useEffect, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { showToastGlobal } from '../components/common/Toast';
import type { ObjectType, Dataset } from './data-workbench/types';
import LucideIcon from './data-workbench/LucideIcon';
import ConnectionsTab from './data-workbench/tabs/ConnectionsTab';
import DataLineageTab from './data-workbench/tabs/DataLineageTab';
import PipelineBuilderTab from './data-workbench/tabs/PipelineBuilderTab';
import EngineConfigTab from './data-workbench/tabs/EngineConfigTab';
import { AddConnectionModal, AddSyncModal, ExternalInterfacesDrawer } from './data-workbench/Modals';
import { useDataWorkbench } from './data-workbench/hooks/useDataWorkbench';

/** 数据质量中心（独立路由页复用同一组件，此处内嵌展示） */
const DataQualityDashboard = lazy(() => import('./DataQualityDashboard'));
/**
 * 数据资产中心（PMO-data10 新增 Tab）。
 * 消费 datanet 资产 CRUD + 分级分类 REST，展示资产列表与字段级敏感度。
 */
const DataAssetsDashboard = lazy(() => import('./DataAssetsDashboard'));

/** 懒加载 Tab 的加载占位 */
function TabLoading() {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="h-5 w-5 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
    </div>
  );
}

type TabName = 'connections' | 'pipeline-builder' | 'health' | 'lineage' | 'data-assets' | 'engine-config';

/** 侧边栏条目：切换工作台内 tab。 */
interface SideTabItem {
  id: string;
  icon: string;
  i18nKey: string;
}


interface DataWorkbenchLayoutProps {
  objectTypes?: ObjectType[];
  datasets?: Dataset[];
  onAddDataset?: (dataset: Dataset) => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
  activeTab?: TabName;
  onActiveTabChange?: (tab: TabName) => void;
}

/**
 * 侧边栏主菜单（4 项，按用户指定顺序：数据源同步 → 数据管道 → 数据质量 → 数据血缘）。
 * 全部为工作台内 Tab 切换，切换后侧边菜单保持一致可见。
 * 引擎配置不在主菜单内，改为靠底展示（置于「物理数据监控仪表」之上），见下方 SIDE_BOTTOM_TABS。
 */
const TAB_CONFIG: SideTabItem[] = [
  { id: 'connections', icon: 'Database', i18nKey: 'dw.tab.connections' },
  { id: 'pipeline-builder', icon: 'Workflow', i18nKey: 'dw.tab.pipeline_builder' },
  { id: 'health', icon: 'ShieldAlert', i18nKey: 'dw.tab.health' },
  { id: 'lineage', icon: 'Workflow', i18nKey: 'dw.tab.lineage' },
  { id: 'data-assets', icon: 'Package', i18nKey: 'dw.tab.data_assets' },
];

/** 侧边栏底部入口（与主菜单同一样式与选中态）。 */
const SIDE_BOTTOM_TABS: SideTabItem[] = [
  { id: 'engine-config', icon: 'Settings', i18nKey: 'dw.tab.engine_config' },
];

export default function DataWorkbenchLayout({
  objectTypes: propObjectTypes, showToast: propShowToast, activeTab: propActiveTab, onActiveTabChange,
}: DataWorkbenchLayoutProps = {}) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const showToast = propShowToast || ((type: 'success' | 'info' | 'error', msg: string) => showToastGlobal(type, msg));

  const dw = useDataWorkbench(showToast, t);

  // ── URL query 支持:/data-workbench?lineageTable=X 自动切到血缘 tab + 透传表名 ──
  const [searchParams, setSearchParams] = useSearchParams();
  const initialLineageTable = searchParams.get('lineageTable') || undefined;

  // ── Tab navigation ──
  const [localActiveTab, setLocalActiveTab] = useState<TabName>('connections');
  const activeTab = propActiveTab !== undefined ? propActiveTab : localActiveTab;
  const setActiveTab = (tab: string) => onActiveTabChange ? onActiveTabChange(tab as TabName) : setLocalActiveTab(tab as TabName);

  // 当 ?lineageTable=X 存在时,自动切到血缘 tab(消费一次后清掉参数避免状态污染)
  useEffect(() => {
    if (initialLineageTable) {
      setActiveTab('lineage');
      // 清空 query,避免下次进入或刷新时又触发
      const next = new URLSearchParams(searchParams);
      next.delete('lineageTable');
      setSearchParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialLineageTable]);

  // ── UI toggles ──
  const [showExtIfaces, setShowExtIfaces] = useState(false);

  /** 渲染一个侧边菜单按钮（主菜单与底部入口共用，保证样式与选中态一致）。 */
  const renderSideTab = (tab: SideTabItem) => {
    const active = activeTab === tab.id;
    return (
      <button key={tab.id} onClick={() => setActiveTab(tab.id)}
          className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold ${active ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} border-l-2 ${styles.accentBorder} font-extrabold shadow-sm` : `${styles.cardTextMuted} hover:opacity-80`}`}>
          <LucideIcon name={tab.icon} size={14} className={active ? styles.accentText : styles.cardTextMuted} />
          <span className="truncate">{t(tab.i18nKey)}</span>
      </button>
    );
  };

  // ── Render ──
  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} relative overflow-hidden font-sans`}>
      <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
        {/* Sidebar — 桌面双栏；移动端落到顶部，md 起恢复为左侧固定 208px 宽度 */}
        <div className={`w-full md:w-52 md:h-full ${styles.sidebarBg} border-r ${styles.sidebarBorder} flex flex-col justify-between md:shrink-0 select-none`}>
          <div className="py-3 px-3 space-y-1 overflow-y-auto">
            <div className={`text-xs font-bold ${styles.cardText} px-2.5 mb-3`}>{t('databench.layout.sidebarTitle')}</div>
            {TAB_CONFIG.map(renderSideTab)}
          </div>
          <div>
            {/* 引擎配置：靠底展示，置于「物理数据监控仪表」之上 */}
            <div className={`px-3 pt-3 pb-1 border-t ${styles.cardBorder}`}>
              {SIDE_BOTTOM_TABS.map(renderSideTab)}
            </div>
            <div className={`p-4 border-t ${styles.cardBorder} ${styles.cardBg} space-y-2 text-[10px] ${styles.cardTextMuted}`}>
              <div className={`font-semibold ${styles.cardText}`}>{t('dw.txt.419d9f')}</div>
              <div className="flex justify-between"><span>{t('dw.txt.f7d9ac')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{dw.connections.length} {t('dw.label.connections_count')}</span></div>
              <div className="flex justify-between"><span>{t('dw.txt.1988fc')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{dw.syncTasks.length} {t('dw.label.syncs_count')}</span></div>
              <div className="flex justify-between"><span>{t('dw.txt.44a230')}</span><span className={`font-mono ${styles.cardText} font-semibold`}>{(propObjectTypes || []).length} {t('dw.label.objects_count')}</span></div>
            </div>
          </div>
        </div>

        {/* Body — 桌面双栏内容区；移动端落到下方，min-w-0 防表格内容溢出 */}
        <div className="w-full flex-1 min-w-0 flex overflow-hidden">
          {activeTab === 'connections' && <ConnectionsTab connections={dw.connections} setConnections={dw.setConnections} showToast={showToast} handleCreateConnection={dw.createConnection} testingConnId={dw.testingConnId} setTestingConnId={dw.setTestingConnId} testingLogs={dw.testingLogs} selectedConnId={dw.selConnId} setSelectedConnId={dw.setSelConnId} showAddConn={dw.showAddConn} setShowAddConn={dw.setShowAddConn} newConnName={dw.ncName} setNewConnName={dw.setNcName} newConnType={dw.ncType} setNewConnType={dw.handleNcTypeChange} newConnHost={dw.ncHost} setNewConnHost={dw.setNcHost} newConnPort={dw.ncPort} setNewConnPort={dw.setNcPort} newConnUser={dw.ncUser} setNewConnUser={dw.setNcUser} onTestConnection={dw.testConnection} t={t} ncExtra={dw.ncExtra} setNcExtraField={dw.setNcExtraField} />}
          {activeTab === 'pipeline-builder' && <PipelineBuilderTab connections={dw.connections} pipelines={dw.pipelines} syncTasks={dw.syncTasks} computeEngine={dw.computeEngine} setComputeEngine={dw.setComputeEngine} showToast={showToast} pipelineBuilderOutput={dw.pbOutput} setPipelineBuilderOutput={dw.setPbOutput} editingPipelineId={dw.editingPipelineId} setEditingPipelineId={dw.setEditingPipelineId} triggerSync={dw.triggerSync} t={t} />}
          {activeTab === 'health' && (
            <Suspense fallback={<TabLoading />}>
              <DataQualityDashboard />
            </Suspense>
          )}
          {activeTab === 'lineage' && <DataLineageTab initialTable={initialLineageTable} />}
          {activeTab === 'data-assets' && (
            <Suspense fallback={<TabLoading />}>
              <DataAssetsDashboard showToast={showToast} t={t} locale={locale} />
            </Suspense>
          )}
          {activeTab === 'engine-config' && <EngineConfigTab showToast={showToast} />}
        </div>
      </div>
      {/* Modals */}
      {dw.showAddConn && <AddConnectionModal t={t} locale={locale} newConnName={dw.ncName} setNewConnName={dw.setNcName} newConnType={dw.ncType} setNewConnType={dw.setNcType as any} newConnHost={dw.ncHost} setNewConnHost={dw.setNcHost} newConnPort={dw.ncPort} setNewConnPort={dw.setNcPort} newConnUser={dw.ncUser} setNewConnUser={dw.setNcUser} newConnPassword={dw.ncPassword} setNewConnPassword={dw.setNcPassword} newConnDatabase={dw.ncDatabase} setNewConnDatabase={dw.setNcDatabase} ncExtra={dw.ncExtra} setNcExtraField={dw.setNcExtraField} onClose={() => dw.setShowAddConn(false)} onCreate={dw.createConnection} onTestConnection={dw.testConnectionRaw} />}
      {dw.showAddSync && <AddSyncModal t={t} locale={locale} newSyncName={dw.nsName} setNewSyncName={dw.setNsName} newSyncConn={dw.nsConn} setNewSyncConn={dw.setNsConn} newSyncTable={dw.nsTable} setNewSyncTable={dw.setNsTable} newSyncMode={dw.nsMode} setNewSyncMode={dw.setNsMode as any} newSyncSched={dw.nsSched} setNewSyncSched={dw.setNsSched as any} connections={dw.connections} onClose={() => dw.setShowAddSync(false)} onCreate={dw.createSync} />}
      {showExtIfaces && <ExternalInterfacesDrawer t={t} connections={dw.connections} onClose={() => setShowExtIfaces(false)} />}
    </div>
  );
}

export function DataWorkbenchLayoutStandalone() {
  // 直接渲染 DataWorkbenchLayout — 它内部统一消费 useTheme。
  // 旧版本这里额外调用一次 useTheme() 拿 shell 样式，导致懒加载 chunk
  // 与主 bundle ThemeContext 不一致时抛 "useTheme must be used within a
  // ThemeProvider"，被 ErrorBoundary 捕获后整个数据工作台按钮全部失效。
  return <DataWorkbenchLayout />;
}
