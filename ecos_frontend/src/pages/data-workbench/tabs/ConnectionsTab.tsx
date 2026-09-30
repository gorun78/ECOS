/* Extracted from DataWorkbenchLayout.tsx */
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import type { DataConnection } from '../types';
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";

import { deleteDataSource, updateDataSource, fetchDataSourceResources, fetchCollectStatus, fetchActiveCollectTasks, fetchCollectDiff, listFolderFiles, type FolderFileVo } from '../api';
import HistoryVersionCompareModal from '../HistoryVersionCompareModal';
import CollectProgressPanel from '../CollectProgressPanel';
import IngestSubPanel from './IngestSubPanel';

import ConnectionListPanel from './connections/ConnectionListPanel';
import ConnectionDetailBanner from './connections/ConnectionDetailBanner';
import ConnectionParamsCard from './connections/ConnectionParamsCard';
import CatalogHeader from './connections/CatalogHeader';
import CollectDiffRecords from './connections/CollectDiffRecords';
import FolderFilesPanel from './connections/FolderFilesPanel';
import TableCatalogPanel from './connections/TableCatalogPanel';
import MetadataStrategyPanel from './connections/MetadataStrategyPanel';
import TestLogTerminal from './connections/TestLogTerminal';
import EditConnectionModal from './connections/EditConnectionModal';
import InlineSqlConsole from './connections/InlineSqlConsole';
import type { ActiveCollectTask, CollectDiffRecord, CollectStatusState } from './connections/types';

interface ConnectionsTabProps {
  connections: DataConnection[];
  showToast: (type:string, message:string)=>void;
  setConnections: (v:DataConnection[]) => void;
  handleCreateConnection: () => void;
  testingConnId: string|null;
  setTestingConnId: (v:string|null)=>void;
  testingLogs: string[];
  selectedConnId: string;
  setSelectedConnId: (v:string)=>void;
  showAddConn: boolean;
  setShowAddConn: (v:boolean)=>void;
  newConnName: string;
  setNewConnName: (v:string)=>void;
  newConnType: string;
  setNewConnType: (v:string)=>void;
  newConnHost: string;
  setNewConnHost: (v:string)=>void;
  newConnPort: number;
  setNewConnPort: (v:number)=>void;
  newConnUser: string;
  setNewConnUser: (v:string)=>void;
  onTestConnection: (connId: string) => void;
  t: (key:string)=>string;
  /** PMO-48-T5: type-specific extra fields */
  ncExtra?: Record<string, string | number | boolean>;
  setNcExtraField?: (key: string, val: string | number | boolean) => void;
}

const ConnectionsTab: React.FC<ConnectionsTabProps> = ({ connections, showToast, setConnections, handleCreateConnection, testingConnId, setTestingConnId, testingLogs, selectedConnId, setSelectedConnId, showAddConn, setShowAddConn, newConnName, setNewConnName, newConnType, setNewConnType, newConnHost, setNewConnHost, newConnPort, setNewConnPort, newConnUser, setNewConnUser, onTestConnection, t, ncExtra, setNcExtraField }) => {
  const { styles } = useTheme();
  const { t: tt } = useLanguage();
  const [editingConn, setEditingConn] = useState<DataConnection | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [loadingTables, setLoadingTables] = useState(false);
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize] = useState(10); // 数据表目录默认每页显示 10 条
  // 文件夹数据源（fs）目录文件列表（非结构化近源层采集源）
  const [folderFiles, setFolderFiles] = useState<FolderFileVo[]>([]);
  const [folderFilesLoading, setFolderFilesLoading] = useState(false);
  // 目录列表复选框选中集合（fs 为文件名，结构源为表名）—— 共享给「采集到近源层」banner
  const [selectedNames, setSelectedNames] = useState<Set<string>>(new Set());
  // 采集方案 Tab：'metadata' | 'ingest'，默认元数据采集（用户主档口先看采集，再切入数据湖写入）
  const [planTab, setPlanTab] = useState<'metadata' | 'ingest'>('metadata');
  // PMO-37 元数据获取策略
  const [collecting, setCollecting] = useState(false);
  const [lastCollectInfo, setLastCollectInfo] = useState<{ time?: string; countMethod?: string } | null>(null);
  // 活跃采集任务状态（对接异步任务中心）
  const [activeTasks, setActiveTasks] = useState<ActiveCollectTask[]>([]);
  // 采集差异记录（采集完成后展示最近 N 次 diff 摘要）
  const [diffRecords, setDiffRecords] = useState<CollectDiffRecord[]>([]);
  const [showDiffDetail, setShowDiffDetail] = useState<number | null>(null);
  const [loadingDiff, setLoadingDiff] = useState(false);
  // 历史版本比较对话框（数据表目录 Git 版本对比）
  const [showVersionCompare, setShowVersionCompare] = useState(false);
  // 同步采集面板：触发后立即展示，轮询后端 upsert 任务状态 → progress/step/ok/failed 实时刷新
  const [collectTaskId, setCollectTaskId] = useState<string | null>(null);
  const [collectStatus, setCollectStatus] = useState<CollectStatusState | null>(null);
  const collectPollingRef = useRef(false);

  // 轮询活跃采集任务（5s 间隔，选中数据源变化时重置）
  useEffect(() => {
    if (!selectedConnId) { setActiveTasks([]); return; }
    let cancelled = false;
    const poll = async () => {
      if (cancelled) return;
      const tasks = await fetchActiveCollectTasks(selectedConnId);
      if (!cancelled) setActiveTasks(tasks);
    };
    poll();
    const timer = setInterval(poll, 5000);
    return () => { cancelled = true; clearInterval(timer); };
  }, [selectedConnId]);

  // 同步采集任务轮询（2s 间隔）：collectTaskId 出现时启动，完成（SUCCEEDED/FAILED/CANCELLED）时刷新目录与差异记录
  useEffect(() => {
    if (!collectTaskId) return;
    let cancelled = false;
    collectPollingRef.current = true;
    const tick = async () => {
      if (cancelled) return;
      const st = await fetchCollectStatus(collectTaskId);
      if (cancelled || !st) return;
      setCollectStatus(st);
      const done = st.status === 'SUCCEEDED' || st.status === 'FAILED' || st.status === 'CANCELLED' || st.status === 'error';
      if (done) {
        cancelled = true;
        collectPollingRef.current = false;
        // 同步采集完成 → 刷新目录 + 差异 + 提示
        try {
          const fresh = await fetchDataSourceResources(selectedConnId);
          if (fresh && Array.isArray(fresh) && !cancelled) {
            setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: fresh } : c));
          }
          fetchCollectDiff(selectedConnId, 5).then(d => setDiffRecords(d || [])).catch(() => {});
        } catch { /* 网络抖动忽略 */ }
        if (st.status === 'SUCCEEDED') {
          showToast('success', t('dw.conn.refreshTables') + ' → ' + (st.totalTables ?? 0) + ' ' + t('dw.tablesUnit'));
        } else {
          showToast('error', t('dw.conn.refreshTables') + ' → ' + (st.errorMessage || st.status || 'FAILED'));
        }
        // 延迟 1.2s 释放面板让用户看到最终进度条
        setTimeout(() => { if (!cancelled) { setCollectTaskId(null); setCollectStatus(null); } }, 1200);
        return;
      }
    };
    tick();
    const timer = setInterval(tick, 2000);
    return () => { cancelled = true; collectPollingRef.current = false; clearInterval(timer); };
  }, [collectTaskId, selectedConnId, showToast, t, connections, setConnections]);

  // 加载最近 5 次采集差异记录
  useEffect(() => {
    if (!selectedConnId) { setDiffRecords([]); return; }
    let cancelled = false;
    loadDiff();
    async function loadDiff() {
      if (cancelled) return;
      setLoadingDiff(true);
      try {
        const diffs = await fetchCollectDiff(selectedConnId, 5);
        if (!cancelled) setDiffRecords(diffs || []);
      } catch (e) {
        console.warn('[ConnTab] loadDiff failed:', e);
      } finally {
        if (!cancelled) setLoadingDiff(false);
      }
    }
    return () => { cancelled = true; };
  }, [selectedConnId]);

  /** 文件夹数据源（fs）：列出目录内可采集文件（非结构化近源层的采集来源） */
  const refreshFolderFiles = useCallback(async (dsId: string) => {
    setFolderFilesLoading(true);
    try {
      setFolderFiles(await listFolderFiles(dsId));
    } catch (e) {
      setFolderFiles([]);
      showToast('error', tt('dw.folder.listFailed').replace('{err}', e instanceof Error ? e.message : 'HTTP'));
    } finally {
      setFolderFilesLoading(false);
    }
  }, [showToast, tt]);

  // 选中连接时获取目录：数据库源拉表目录，文件夹源列目录文件
  useEffect(() => {
    setTablePage(1);
    setSelectedNames(new Set());
    // fs 文件即元数据（无元数据采集任务），默认切入数据采集 Tab
    const initialConn = selectedConnId ? connections.find(c => c.id === selectedConnId) : null;
    setPlanTab(initialConn?.type === 'fs' ? 'ingest' : 'metadata');
    if (!selectedConnId) return;
    const conn = connections.find(c => c.id === selectedConnId);
    if (!conn) return;
    if (conn.type === 'fs') {
      refreshFolderFiles(conn.id);
      return;
    }
    if (conn.tablesAvailable.length > 0) return;
    setLoadingTables(true);
    fetchDataSourceResources(selectedConnId).then(tables => {
      if (tables.length > 0) {
        setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: tables } : c));
      }
      setLoadingTables(false);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedConnId]);

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(tt('dw.conn.deleteConfirm') + ': ' + name)) return;
    setDeletingId(id);
    const ok = await deleteDataSource(id);
    if (ok) {
      setConnections(connections.filter(c => c.id !== id));
      showToast('success', tt('dw.conn.deleteSuccess'));
      if (selectedConnId === id) setSelectedConnId('');
    } else {
      showToast('error', tt('dw.conn.deleteFailed'));
    }
    setDeletingId(null);
  };

  const handleSaveEdit = async (updated: DataConnection) => {
    const result = await updateDataSource(updated.id, {
      name: updated.name, type: updated.type, host: updated.config.host || '',
      port: updated.config.port || 5432, username: updated.config.username || '',
    });
    if (result) {
      setConnections(connections.map(c => c.id === updated.id ? result : c));
      showToast('success', tt('dw.conn.updateSuccess'));
      setEditingConn(null);
    } else {
      showToast('error', tt('dw.conn.updateFailed'));
    }
  };

  return (
<div className="flex-1 flex overflow-hidden">
  {/* Connections list panel */}
  <ConnectionListPanel
    connections={connections}
    selectedConnId={selectedConnId}
    setSelectedConnId={setSelectedConnId}
    setEditingConn={setEditingConn}
    handleDelete={handleDelete}
    deletingId={deletingId}
    setShowAddConn={setShowAddConn}
    t={t}
  />

  {/* Connection Detail View */}
  {(() => {
    const conn = connections.find(c => c.id === selectedConnId);
    if (!conn) return <div className={`flex-1 p-6 ${styles.cardTextMuted}`}>{t("dw.txt.282170")}</div>;
    /** 文件夹数据源（fs）：以目录文件列表替代数据库表目录 */
    const isFsConn = conn.type === 'fs';
    return (
      <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
        {/* Detail banner */}
        <ConnectionDetailBanner
          conn={conn}
          onTestConnection={onTestConnection}
          testingConnId={testingConnId}
          t={t}
        />

        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* ① 连接参数（横向扁平卡，紧凑） */}
          <ConnectionParamsCard conn={conn} t={t} />

          {/* ② 目录列表（heading 跟随下面 fs / 结构 分支渲染） */}
          <div className="space-y-3">
            <CatalogHeader
              conn={conn}
              isFsConn={isFsConn}
              folderFiles={folderFiles}
              folderFilesLoading={folderFilesLoading}
              loadingTables={loadingTables}
              refreshFolderFiles={refreshFolderFiles}
              setShowVersionCompare={setShowVersionCompare}
              t={t}
            />

            {/* 采集差异记录 — 仅结构源有差异记录（fs 文件无需版本对比） */}
            {!isFsConn && diffRecords.length > 0 && (
              <CollectDiffRecords
                diffRecords={diffRecords}
                loadingDiff={loadingDiff}
                showDiffDetail={showDiffDetail}
                setShowDiffDetail={setShowDiffDetail}
                t={t}
              />
            )}

            {/* fs 文件列表 / 结构表列表 */}
            {isFsConn ? (
              <FolderFilesPanel
                folderFiles={folderFiles}
                folderFilesLoading={folderFilesLoading}
                selectedNames={selectedNames}
                setSelectedNames={setSelectedNames}
                t={t}
              />
            ) : (
              <TableCatalogPanel
                conn={conn}
                loadingTables={loadingTables}
                tablePage={tablePage}
                setTablePage={setTablePage}
                tablePageSize={tablePageSize}
                selectedNames={selectedNames}
                setSelectedNames={setSelectedNames}
                t={t}
              />
            )}
          </div>

          {/* ③ 采集方案（Tab：元数据采集 | 数据采集，紧贴目录列表下方） */}
          <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.appBg}`}>
            {/* Tab 条 */}
            <div className={`flex border-b ${styles.cardBorder} ${styles.sidebarBg}/60`}>
              {(['metadata', 'ingest'] as const).map(tabKey => {
                const active = planTab === tabKey;
                const label = tabKey === 'metadata' ? t("dw.strategy.section") : t("dw.ingest.title");
                return (
                  <button
                    key={tabKey}
                    onClick={() => setPlanTab(tabKey)}
                    className={`px-4 py-2.5 text-xs transition-colors cursor-pointer font-medium border-b-2 ${
                      active
                        ? `border-current ${styles.accentText} ${styles.cardText}`
                        : `border-transparent ${styles.cardTextMuted} hover:${styles.accentText}`
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>

            {/* Tab 内容 */}
            <div className="p-4 space-y-3">
              {planTab === 'metadata' && (
                <MetadataStrategyPanel
                  conn={conn}
                  connections={connections}
                  setConnections={setConnections}
                  showToast={showToast}
                  t={t}
                  folderFiles={folderFiles}
                  refreshFolderFiles={refreshFolderFiles}
                  collecting={collecting}
                  setCollecting={setCollecting}
                  collectTaskId={collectTaskId}
                  setCollectTaskId={setCollectTaskId}
                  setCollectStatus={setCollectStatus}
                  activeTasks={activeTasks}
                />
              )}

              {planTab === 'ingest' && (
                <IngestSubPanel
                  conn={conn}
                  selectedNames={selectedNames}
                  setSelectedNames={setSelectedNames}
                  isFs={isFsConn}
                  showToast={showToast}
                />
              )}
            </div>
          </div>

          {/* Diagnostic Log Terminal — 连接器调试面板 */}
          {testingLogs.length > 0 && (
            <TestLogTerminal testingLogs={testingLogs} t={t} />
          )}

          {/* PMO-37 修复：采集实时进度面板 — 与连接器调试面板同窗口展示，
              替代原先在右侧独立新窗口的行为，避免对抗布局切换。
              CollectProgressPanel 渲染在详情视图末尾 so 它紧贴调试面板，
              用户在同一窗口内同时看到采集进度 + 调试日志。 */}
          {collectTaskId !== null && (
            <CollectProgressPanel
              taskId={collectTaskId}
              status={collectStatus}
              onClose={() => { if (!collectPollingRef.current) { setCollectTaskId(null); setCollectStatus(null); } }}
            />
          )}

          {/* SQL Query Console */}
          <InlineSqlConsole datasourceId={conn.id} />
        </div>
      </div>
    );
  })()}

  {editingConn && (
    <EditConnectionModal
      conn={editingConn}
      onSave={handleSaveEdit}
      onCancel={() => setEditingConn(null)}
    />
  )}

  {/* PMO-37 修复：历史版本比较对话框 — 原先已定义 showVersionCompare 状态但
      在该处遗漏渲染，导致点击「历史版本比较」按钮后无任何 UI 反应（无内容显示）。
      补上渲染，使用当前选中数据源 id 与名称，onClose 复位 state。 */}
  {showVersionCompare && selectedConnId && (() => {
    const activeConn = connections.find(c => c.id === selectedConnId);
    return activeConn ? (
      <HistoryVersionCompareModal
        datasourceId={activeConn.id}
        datasourceName={activeConn.name}
        onClose={() => setShowVersionCompare(false)}
      />
    ) : null;
  })()}

</div>
  );
};

export default ConnectionsTab;
