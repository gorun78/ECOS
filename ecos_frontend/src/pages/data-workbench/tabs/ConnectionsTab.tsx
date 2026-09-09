/* Extracted from DataWorkbenchLayout.tsx */
import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { GitCompare } from 'lucide-react';
import LucideIcon from '../LucideIcon';
import { getSourceIcon, getSourceTypeLabel } from '../helpers';
import type { DataConnection, TableInfo } from '../types';
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";

import { deleteDataSource, updateDataSource, fetchDataSourceResources, triggerMetadataCollect, fetchCollectStatus, saveMetadataStrategy, fetchActiveCollectTasks, fetchCollectDiff, fetchPreview } from '../api';
import HistoryVersionCompareModal from '../HistoryVersionCompareModal';

const STRATEGY_OPTIONS: { value: string; key: string }[] = [
  { value: 'MANUAL', key: 'dw.strategy.manual' },
  { value: 'ON_SAVE', key: 'dw.strategy.onSave' },
  { value: 'ON_SCHEDULE', key: 'dw.strategy.onSchedule' },
];
const COUNT_METHOD_OPTIONS: { value: string; key: string }[] = [
  { value: 'OFF', key: 'dw.strategy.countOff' },
  { value: 'ESTIMATE', key: 'dw.strategy.countEstimate' },
  { value: 'EXACT', key: 'dw.strategy.countExact' },
];


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
  // PMO-37 元数据获取策略
  const [collecting, setCollecting] = useState(false);
  const [lastCollectInfo, setLastCollectInfo] = useState<{ time?: string; countMethod?: string } | null>(null);
  // 活跃采集任务状态（对接异步任务中心）
  const [activeTasks, setActiveTasks] = useState<{ taskId: string; status: string; progress: number; startTime?: string }[]>([]);
  // 采集差异记录（采集完成后展示最近 N 次 diff 摘要）
  const [diffRecords, setDiffRecords] = useState<{ collectedAt: string; taskId?: string; diffSummary?: string; diffMarkdown?: string; gitCommit?: string; tablesTotal?: number }[]>([]);
  const [showDiffDetail, setShowDiffDetail] = useState<number | null>(null);
  const [loadingDiff, setLoadingDiff] = useState(false);
  // 历史版本比较对话框（数据表目录 Git 版本对比）
  const [showVersionCompare, setShowVersionCompare] = useState(false);
  // 同步采集面板：触发后立即展示，轮询后端 upsert 任务状态 → progress/step/ok/failed 实时刷新
  const [collectTaskId, setCollectTaskId] = useState<string | null>(null);
  const [collectStatus, setCollectStatus] = useState<{
    status?: string; progress?: number; message?: string; statusMessage?: string;
    processedRecords?: number; totalRecords?: number;
    collectedTables?: number; totalTables?: number; tablesOk?: number; tablesFailed?: number; errorMessage?: string;
  } | null>(null);
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

  // 选中连接时获取数据表目录
  useEffect(() => {
    setTablePage(1);
    if (!selectedConnId) return;
    const conn = connections.find(c => c.id === selectedConnId);
    if (!conn || conn.tablesAvailable.length > 0) return;
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
  <div className={`w-72 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col overflow-hidden shrink-0`}>
    <div className={`p-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}/40`}>
      <h3 className={`text-xs font-bold ${styles.cardText}`}>{t("dw.conn.title")}</h3>
      <button
        onClick={() => setShowAddConn(true)}
        className={`p-1 rounded ${styles.accentBg} ${styles.cardText} ${styles.accentHover} text-xs flex items-center gap-1 cursor-pointer font-medium`}
      >
        <LucideIcon name="Plus" size={12} />
        <span>{t("dw.txt.30f7dd")}</span>
      </button>
    </div>

    <div className="flex-1 overflow-y-auto p-2 space-y-1">
      {connections.length === 0 && (
        <div className={`text-center py-8 ${styles.cardTextMuted} text-xs`}>{t("dw.conn.empty")}</div>
      )}
      {connections.map(conn => {
        const isSelected = selectedConnId === conn.id;
        return (
          // 用 div[role=button] 取代原 <button>：HTML 严禁 <button> 内嵌 <button>，
          // 否则 React 19 会报 hydration/nesting 警告，且会破坏内层编辑/删除
          // 按钮的事件委托，导致「数据源卡片点不动」。
          <div
            key={conn.id}
            role="button"
            tabIndex={0}
            onClick={() => setSelectedConnId(conn.id)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                setSelectedConnId(conn.id);
              }
            }}
            className={`w-full text-left p-3 rounded-lg border transition-all text-xs flex flex-col gap-1.5 cursor-pointer focus:outline-none ${
              isSelected
                ? `${styles.badgeBg} ${styles.accentBorder} shadow-2xs`
                : `${styles.cardBorder} hover:${styles.appBg}`
            }`}
          >
            <div className="flex justify-between items-center">
              <span className={`font-semibold ${styles.cardText} truncate pr-2`}>{conn.name}</span>
              <div className="flex items-center gap-1">
                <button
                  onClick={(e) => { e.stopPropagation(); setEditingConn(conn); }}
                  className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.accentText} transition-colors cursor-pointer`}
                  title={tt('dw.conn.edit')}
                >
                  <LucideIcon name="Edit3" size={11} />
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); handleDelete(conn.id, conn.name); }}
                  disabled={deletingId === conn.id}
                  className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.dangerText} transition-colors cursor-pointer disabled:opacity-50`}
                  title={tt('dw.conn.delete')}
                >
                  <LucideIcon name="Trash2" size={11} />
                </button>
                <span className={`h-2 w-2 rounded-full ${
                  conn.status === 'connected' ? styles.successBg :
                  conn.status === 'error' ? styles.dangerBg : styles.warningBg
                }`} title={conn.status} />
              </div>
            </div>
            <div className={`flex justify-between text-[10px] ${styles.cardTextMuted} font-mono`}>
              <span>{t("dw.type")} {conn.type.toUpperCase()}</span>
              <span>{conn.tablesAvailable.length} {t("dw.tablesDirs")}</span>
            </div>
          </div>
        );
      })}
    </div>
  </div>

  {/* Connection Detail View */}
  {(() => {
    const conn = connections.find(c => c.id === selectedConnId);
    if (!conn) return <div className={`flex-1 p-6 ${styles.cardTextMuted}`}>{t("dw.txt.282170")}</div>;
    return (
      <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
        {/* Detail banner */}
        <div className={`p-6 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}/50`}>
          <div className="flex items-center gap-3">
            <div className={`p-2.5 rounded-full border ${styles.accentBorder} ${styles.badgeBg} ${styles.badgeText} flex items-center justify-center`}>
              <LucideIcon name={getSourceIcon(conn.type)} size={20} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className={`text-sm font-bold ${styles.cardText}`}>{conn.name}</span>
                <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} font-mono px-2 py-0.5 rounded-full uppercase`}>
                  {conn.type}
                </span>
              </div>
              <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{getSourceTypeLabel(conn.type, t)}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => onTestConnection(conn.id)}
              disabled={testingConnId !== null}
              className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} text-xs font-semibold rounded transition-all cursor-pointer flex items-center gap-1.5`}
            >
              <LucideIcon name="Wifi" size={13} />
              <span>{t("dw.conn.testBtn")}</span>
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Technical specifications */}
          <div className="grid grid-cols-3 gap-6">
            <div className={`col-span-1 ${styles.appBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
              <h4 className={`text-xs font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-1.5 flex items-center gap-1.5`}>
                <LucideIcon name="Settings" size={12} className={`${styles.cardTextMuted}`} />
                {t("dw.connConfigParams")}
              </h4>

              <div className="text-xs space-y-2.5">
                {conn.config.host && (
                  <div>
                    <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.16e578")}</span>
                    <span className={`font-mono font-medium ${styles.cardText}`}>{conn.config.host}</span>
                  </div>
                )}
                {conn.config.port && (
                  <div>
                    <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.4016cf")}</span>
                    <span className={`font-mono font-medium ${styles.cardText}`}>{conn.config.port}</span>
                  </div>
                )}
                {conn.config.username && (
                  <div>
                    <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.1169ed")}</span>
                    <span className={`font-mono font-medium ${styles.cardText}`}>{conn.config.username}</span>
                  </div>
                )}
                {conn.config.bucket && (
                  <div>
                    <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.eb9003")}</span>
                    <span className={`font-mono font-medium ${styles.cardText} truncate block`}>{conn.config.bucket}</span>
                  </div>
                )}
                {conn.config.endpointUrl && (
                  <div>
                    <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.3cd968")}</span>
                    <span className={`font-mono font-medium ${styles.cardText} truncate block`}>{conn.config.endpointUrl}</span>
                  </div>
                )}
                <hr className={`${styles.cardBorder}`} />
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono`}>{t("dw.txt.165c7b")}</span>
                   <span className={`${styles.cardTextMuted} text-[11px] font-medium`}>{conn.config.lastTested || t("dw.neverTested")}</span>
                </div>

                {/* PMO-37 元数据获取策略 */}
                <hr className={`${styles.cardBorder}`} />
                <div>
                  <span className={`text-[10px] ${styles.cardTextMuted} uppercase block font-mono mb-2`}>{t("dw.strategy.section")}</span>
                  <div className="space-y-2">
                    <div>
                      <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>{t("dw.strategy.trigger")}</label>
                      <select
                        value={conn.strategy?.trigger || 'MANUAL'}
                        onChange={async e => {
                          const newTrigger = e.target.value;
                          const newCount = conn.strategy?.countMethod || 'OFF';
                          setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, trigger: newTrigger as any } } : c));
                          await saveMetadataStrategy(conn.id, newTrigger, newCount);
                        }}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
                      >
                        {STRATEGY_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.key)}</option>)}
                      </select>
                    </div>
                    {/* 定时采集策略 — 选择 ON_SCHEDULE 时显示 cron 配置 */}
                    {conn.strategy?.trigger === 'ON_SCHEDULE' && (
                    <div className="space-y-1.5 p-2 rounded-lg border border-dashed border-slate-500/40">
                      <label className={`text-[10px] ${styles.cardTextMuted} block`}>{t("dw.strategy.cronLabel")}</label>
                      <select
                        value={conn.strategy?.scheduleCron || '0 0 * * *'}
                        onChange={async e => {
                          const newCron = e.target.value;
                          const newTrigger = conn.strategy?.trigger || 'ON_SCHEDULE';
                          const newCount = conn.strategy?.countMethod || 'OFF';
                          setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, scheduleCron: newCron } } : c));
                          await saveMetadataStrategy(conn.id, newTrigger, newCount, newCron);
                          showToast('success', t('dw.strategy.updateSuccess') || '策略已保存');
                        }}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText} font-mono`}
                      >
                        <option value="0 0 * * *">{t('dw.strategy.cron.daily')}</option>
                        <option value="0 */6 * * *">{t('dw.strategy.cron.sixHourly')}</option>
                        <option value="0 0 */2 * *">{t('dw.strategy.cron.twoDays')}</option>
                        <option value="0 0 * * 1">{t('dw.strategy.cron.weekly')}</option>
                      </select>
                      <p className={`text-[10px] ${styles.cardTextMuted} font-mono`}>cron: {t('dw.strategy.cron.format')}</p>
                    </div>
                    )}
                    <div>
                      <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>{t("dw.strategy.count")}</label>
                      <select
                        value={conn.strategy?.countMethod || 'OFF'}
                        onChange={async e => {
                          const newCount = e.target.value;
                          const newTrigger = conn.strategy?.trigger || 'MANUAL';
                          setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, countMethod: newCount as any } } : c));
                          await saveMetadataStrategy(conn.id, newTrigger, newCount);
                        }}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
                      >
                        {COUNT_METHOD_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.key)}</option>)}
                      </select>
                    </div>
                    <p className={`text-[10px] ${styles.cardTextMuted}`}>{t("dw.strategy.hint")}</p>
                    <div className="flex gap-2">
                      <button
                        disabled={collecting || collectTaskId !== null}
                        onClick={async () => {
                          setCollecting(true);
                          try {
                            // Wave-3 lower 注：triggerCollectSync 端点暂未落地（Wave3 上 补齐）；
                            // 这里保留元数据异步任务的轮询入口；详见 fetchCollectStatus。
                            const r = await triggerMetadataCollect(conn.id);
                            if (r?.taskId) {
                              setCollectTaskId(r.taskId);
                              setCollectStatus(null);
                              showToast('info', t('dw.strategy.collectStarted').replace('{id}', r.taskId.slice(0, 8)));
                            } else {
                              showToast('error', t('dw.strategy.collectFailed').replace('{err}', 'HTTP'));
                            }
                          } finally {
                            setCollecting(false);
                          }
                        }}
                        className={`px-2 py-1 text-[11px] font-semibold rounded transition-colors flex items-center gap-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} disabled:opacity-40`}
                      >
                        <LucideIcon name="RefreshCw" size={11} className={(collecting || collectTaskId !== null) ? 'animate-spin' : ''} />
                        {(collecting || collectTaskId !== null) ? t('dw.strategy.collecting') : t('dw.strategy.collectNow')}
                      </button>
                    </div>
                    {/* 元数据采集当前轮询状态（异步任务中心统一展示，本地 inline 面板已下线） */}
                    <div className={`text-[10px] ${styles.cardTextMuted}`}>
                      {t("dw.strategy.lastCollect")}:{' '}
                      {conn.metadataConfig?.lastCollectTime
                        ? new Date(String(conn.metadataConfig.lastCollectTime)).toLocaleString()
                        : t('dw.strategy.neverCollected')}
                    </div>
                    {/* 活跃采集任务状态指示器 — 对接异步任务中心 */}
                    {activeTasks.length > 0 && (
                      <div className={`space-y-1.5 p-2 rounded-lg ${styles.appBg} border ${styles.cardBorder}`}>
                        <div className={`flex items-center gap-2 text-[11px]`}>
                          <LucideIcon name="Loader2" size={13} className={`animate-spin ${styles.accentText}`} />
                          <span className={`font-semibold ${styles.cardText}`}>
                            {t('dw.strategy.taskRunning') || '任务执行中'}
                          </span>
                        </div>
                        {activeTasks.map((task, i) => (
                          <div key={task.taskId} className={`text-[10px] font-mono ${styles.cardTextMuted} flex items-center gap-2`}>
                            <span className={`${task.status === 'RUNNING' ? styles.successText : styles.warningText} font-bold`}>
                              {task.status}
                            </span>
                            <span>{task.taskId.slice(0, 8)}...{task.progress}%</span>
                            {task.startTime && (
                              <span className="opacity-70">{new Date(task.startTime).toLocaleTimeString()}</span>
                            )}
                          </div>
                        ))}
                        <button
                          onClick={() => window.open('#/task-center', '_blank')}
                          className={`text-[10px] ${styles.accentText} hover:underline cursor-pointer flex items-center gap-1`}
                        >
                          <LucideIcon name="ExternalLink" size={10} />
                          {t('dw.strategy.viewInTaskCenter') || '在任务中心查看'}
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Database physical table browser */}
            <div className="col-span-2 space-y-4">
              <h4 className={`text-xs font-bold ${styles.cardText} flex items-center justify-between`}>
                <span>{t("dw.txt.42bc1b")}</span>
                <div className="flex items-center gap-3">
                  <span className={`text-[10px] ${styles.cardTextMuted} font-normal`}> {t("dw.ontologyReadonly")} ({conn.tablesAvailable.length} {t("dw.tablesUnit")})</span>
                  <button
                    onClick={() => setShowVersionCompare(true)}
                    disabled={loadingTables}
                    className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.accentText} transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1 text-[10px]`}
                    title={t("dw.histCompare.button")}
                  >
                    <GitCompare size={12} />
                    <span>{t("dw.histCompare.button")}</span>
                  </button>
                  <button
                    onClick={async () => {
                      setLoadingTables(true);
                      const r = await triggerMetadataCollect(conn.id);
                      if (r?.taskId) {
                        let done = false;
                        for (let i = 0; i < 60 && !done; i++) {
                          await new Promise(res => setTimeout(res, 1000));
                          const st = await fetchCollectStatus(r.taskId);
                          if (st?.status === 'SUCCEEDED' || st?.status === 'FAILED' || st?.status === 'error') {
                            done = true;
                            if (st.status === 'SUCCEEDED') {
                              const fresh = await fetchDataSourceResources(conn.id);
                              setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: fresh } : c));
                              // 采集成功后刷新差异记录列表
                              fetchCollectDiff(conn.id, 5).then(diffs => setDiffRecords(diffs || [])).catch(() => {});
                              // 根据采集结果给用户准确反馈
                              if (st.totalTables === 0 && fresh.length === 0) {
                                showToast('warning', `${t('dw.conn.refreshTables')} → ${t('dw.conn.noTablesFound') || '采集完成但未发现可用数据表，请检查数据源连接配置'}`);
                              } else {
                                showToast('success', `${t('dw.conn.refreshTables')} → ${fresh.length} ${t('dw.tablesUnit') || '张表'}`);
                              }
                            } else if (st.status === 'FAILED') {
                              showToast('error', `${t('dw.conn.refreshTables')} → ${st.errorMessage || t('dw.conn.collectFailed') || '采集任务执行失败'}`);
                              // 任务失败也尝试重拉目录（可能之前已有数据）
                              const fresh = await fetchDataSourceResources(conn.id);
                              setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: fresh } : c));
                            }
                            break;
                          }
                        }
                        // 轮询超时
                        if (!done) {
                          const fresh = await fetchDataSourceResources(conn.id);
                          setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: fresh } : c));
                          showToast('info', t('dw.conn.collectTimeout') || '采集任务超时，已拉取当前可用目录');
                        }
                      } else {
                        const fresh = await fetchDataSourceResources(conn.id);
                        setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: fresh } : c));
                        if (!r) {
                          showToast('error', t('dw.conn.collectSubmitFailed') || '采集任务提交失败');
                        }
                      }
                      setLoadingTables(false);
                    }}
                    disabled={loadingTables}
                    className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.accentText} transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1 text-[10px]`}
                    title={t("dw.conn.refreshTables")}
                  >
                    <LucideIcon name="RefreshCw" size={12} className={loadingTables ? 'animate-spin' : ''} />
                    <span>{t("dw.conn.refreshTables")}</span>
                  </button>
                </div>
              </h4>

              {/* 采集差异记录 — Task2 版本差异可视化 */}
              {diffRecords.length > 0 && (
                <div className={`mb-4 border ${styles.cardBorder} rounded-lg overflow-hidden ${styles.appBg}`}>
                  <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.sidebarBg}/60 flex items-center justify-between`}>
                    <span className={`text-[10px] font-semibold ${styles.cardText} flex items-center gap-1.5`}>
                      <LucideIcon name="GitCommit" size={12} className={styles.accentText} />
                      {t('dw.strategy.diffTitle') || '采集差异记录'}
                    </span>
                    {loadingDiff && <LucideIcon name="Loader2" size={11} className="animate-spin" />}
                  </div>
                  <div className="divide-y" style={{ borderColor: styles.cardBorder }}>
                    {diffRecords.map((rec, idx) => (
                      <div key={idx} className={`group`}>
                        {showDiffDetail === idx ? (
                          <button
                            onClick={() => setShowDiffDetail(null)}
                            className="w-full text-left px-3 py-2 text-[10px] font-mono whitespace-pre-wrap break-words cursor-pointer hover:bg-opacity-50 transition-colors"
                            style={{ background: styles.cardBg }}
                          >
                            {rec.diffMarkdown || '(空)'}
                          </button>
                        ) : (
                          <button
                            onClick={() => setShowDiffDetail(idx)}
                            className="w-full text-left px-3 py-2 transition-colors hover:bg-opacity-50 cursor-pointer"
                            style={{ background: styles.cardBg }}
                          >
                            <div className="flex items-center justify-between gap-2">
                              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                                {rec.collectedAt && new Date(rec.collectedAt).toLocaleString()}
                              </span>
                              <span className={`text-[10px] font-mono ${styles.cardText}`}>{rec.diffSummary || ''}</span>
                            </div>
                            {rec.gitCommit && (
                              <div className={`text-[9px] font-mono ${styles.cardTextMuted} mt-1 flex items-center gap-1`}>
                                <LucideIcon name="GitBranch" size={9} />
                                {rec.gitCommit}
                              </div>
                            )}
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {loadingTables ? (
                 <div className={`p-8 text-center ${styles.cardTextMuted} text-xs flex items-center justify-center gap-2`}>
                   <LucideIcon name="RefreshCw" size={14} className="animate-spin" />
                   {t('dw.loading') || 'Loading...'}
                 </div>
              ) : conn.tablesAvailable.length === 0 ? (
                 <div className={`p-8 border border-dashed ${styles.cardBorder} rounded-xl text-center ${styles.cardTextMuted} text-xs flex flex-col items-center gap-2`}>
                  <LucideIcon name="AlertTriangle" size={24} className={`${styles.warningText}`} />
                  <span>{t("dw.txt.2ce9e0")}</span>
                  <span>{t("dw.txt.44e8b3")}</span>
                </div>
              ) : (
                <>
                <div className="space-y-4">
                  {conn.tablesAvailable.slice((tablePage - 1) * tablePageSize, tablePage * tablePageSize).map(tbl => (
                    <TableExpandRow
                      key={tbl.name}
                      connId={conn.id}
                      table={tbl}
                    />
                  ))}
                </div>
                {conn.tablesAvailable.length > tablePageSize && (
                  <div className={`flex items-center justify-between pt-2 border-t ${styles.cardBorder}`}>
                    <button
                      onClick={() => setTablePage(p => Math.max(1, p - 1))}
                      disabled={tablePage <= 1}
                      className={`px-3 py-1 text-[10px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText} cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1`}
                    >
                      <LucideIcon name="ChevronLeft" size={11} />
                      {t("dw.conn.pagePrev")}
                    </button>
                    <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                      {t("dw.conn.pageInfo").replace('{page}', String(tablePage)).replace('{total}', String(Math.ceil(conn.tablesAvailable.length / tablePageSize)))}
                    </span>
                    <button
                      onClick={() => setTablePage(p => Math.min(Math.ceil(conn.tablesAvailable.length / tablePageSize), p + 1))}
                      disabled={tablePage >= Math.ceil(conn.tablesAvailable.length / tablePageSize)}
                      className={`px-3 py-1 text-[10px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText} cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1`}
                    >
                      {t("dw.conn.pageNext")}
                      <LucideIcon name="ChevronRight" size={11} />
                    </button>
                  </div>
                )}
                </>
              )}
            </div>
          </div>

          {/* Diagnostic Log Terminal */}
          {testingLogs.length > 0 && (
            <div className={`${styles.sidebarBg} p-4 rounded-xl text-xs font-mono ${styles.sidebarText} space-y-1.5 border ${styles.sidebarBorder} select-text leading-relaxed`}>
              <div className={`text-[10px] ${styles.cardTextMuted} tracking-wider uppercase font-semibold mb-2 border-b ${styles.sidebarBorder} pb-1 flex justify-between items-center select-none`}>
                <span>{t("dw.txt.26079a")}</span>
                <span className={`${styles.accentText}`}>JDBC API Log v1.4</span>
              </div>
              {testingLogs.map((log, i) => (
                <div key={i} className={
                  log.includes('ERROR') || log.includes('❌') ? `${styles.dangerText}` :
                  log.includes('SUCCESS') || log.includes('✅') ? `${styles.successText}` :
                  log.includes('🔑') ? `${styles.accentText}` : `${styles.cardTextMuted}`
                }>
                  {log}
                </div>
              ))}
            </div>
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

</div>
  );
};

// ── TableExpandRow ──────────────────────────────────────
// 数据表卡片 + 表名右侧 Chevron 图标：
//   首次点击「向下展开」，懒加载 fetchPreview 全量列，
//   面积以 max-height + opacity 过渡实现（340ms cubic-bezier）。
//   再次点击 ChevecDown 图标（轴线反转为 ChevronUp）→ 收起隐藏。
// 主题感知 (useTheme)，i18n (useLanguage)，禁 hardcoded 中文/颜色。
function TableExpandRow({ connId, table }: { connId: string; table: TableInfo }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [expanded, setExpanded] = useState(false);
  const [columns, setColumns] = useState<{ name: string; type: string; label?: string }[]>(table.columns || []);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loadedRef = useRef(false);

  const toggle = useCallback(() => {
    setExpanded(prev => {
      const next = !prev;
      // 首次展开时懒加载全量列
      if (next && !loadedRef.current && table.resourceId) {
        setLoading(true);
        setError(null);
        (async () => {
          try {
            const p = await fetchPreview(table.resourceId!, 1);
            const cols = (p?.columns ?? []).map(c => ({
              name: c.name,
              type: c.type,
              label: c.label,
            }));
            if (cols.length > 0) setColumns(cols);
            loadedRef.current = true;
          } catch (e) {
            setError(e instanceof Error ? e.message : String(e));
          } finally {
            setLoading(false);
          }
        })();
      }
      return next;
    });
  }, [table.resourceId]);

  const isLoading = expanded && loading;
  const expandedCls = expanded
    ? 'max-h-[800px] opacity-100 translate-y-0'
    : 'max-h-0 opacity-0 -translate-y-1 pointer-events-none';
  const transition = 'max-height 0.34s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.25s ease, transform 0.3s cubic-bezier(0.16, 1, 0.3, 1)';

  return (
    <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.appBg}/50 transition-shadow hover:ring-1`}>
      {/* 表头行：表名 + 右侧展开/收起图标 */}
      <div
        className={`${styles.sidebarBg}/70 px-4 py-2 flex items-center justify-between gap-2 border-b ${styles.cardBorder}`}
      >
        <div className="flex items-center gap-2 text-xs flex-1 min-w-0">
          <LucideIcon name="Table" size={13} className={styles.accentText} />
          <span className={`font-bold font-mono ${styles.cardText} truncate`}>{table.name}</span>
          {table.resourceId && (
            <span className="text-[9px] font-mono" style={{ color: styles.cardTextMuted }} title={table.resourceId}>
              {table.resourceId.slice(0, 12)}…
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className={`text-[10px] ${styles.cardTextMuted} ${styles.cardBg} border ${styles.cardBorder} px-2 py-0.5 rounded-full font-mono`}>
            {t('dw.physicalRows')} {table.rowCount != null && table.rowCount > 0 ? table.rowCount.toLocaleString() : t('dw.conn.rowsUnknown')}
            {table.rowCount != null && table.rowCount > 0 ? ' ' + t('dw.rowsUnit') : ''}
          </span>
          <button
            type="button"
            onClick={toggle}
            aria-expanded={expanded}
            aria-label={expanded ? t('dw.expandRow.collapse') : t('dw.expandRow.expand')}
            title={expanded ? t('dw.expandRow.collapse') : t('dw.expandRow.expand')}
            className={`p-1 rounded flex items-center justify-center transition-transform ${
              expanded ? 'rotate-180' : ''
            } ${styles.cardTextMuted} hover:${styles.accentText}`}
          >
            <LucideIcon name="ChevronDown" size={14} />
          </button>
        </div>
      </div>

      {/* 展开区域：全量列网格（懒加载） */}
      <div
        className={`overflow-hidden transition-all ${expandedCls}`}
        style={{ transition }}
        aria-hidden={!expanded}
      >
        <div className={`p-3 ${styles.cardBg} space-y-2`}>
          {isLoading ? (
            <div className={`flex items-center justify-center py-4 text-xs ${styles.cardTextMuted}`}>
              <LucideIcon name="RefreshCw" size={13} className="animate-spin mr-2" />
              {t('dw.loading') || 'Loading...'}
            </div>
          ) : error ? (
            <div className={`p-3 rounded border text-xs ${styles.dangerText}`} style={{ borderColor: styles.dangerText }}>
              {error}
            </div>
          ) : columns.length === 0 ? (
            <div className={`p-3 rounded border text-center text-xs ${styles.cardTextMuted} ${styles.cardBorder}`}>
              {t('db.preview.empty') || 'No columns'}
            </div>
          ) : (
            <div className="grid grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2 text-[11px]">
              {columns.map((col, i) => (
                <div
                  key={col.name + i}
                  className={`p-1.5 ${styles.appBg} rounded border ${styles.cardBorder} flex flex-col font-mono`}
                  title={col.name}
                >
                  <span className={`${styles.cardText} truncate font-semibold`}>
                    {col.label || col.name}
                  </span>
                  <span className={`text-[9px] ${styles.cardTextMuted} mt-0.5 truncate`} title={col.type}>
                    {col.type}
                  </span>
                </div>
              ))}
            </div>
          )}
          <div className={`text-[10px] ${styles.cardTextMuted} font-mono pt-1`}>
            {t('dw.expandRow.colCount').replace('{n}', String(columns.length))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Edit Connection Modal ─────────────────────────────────
function EditConnectionModal({ conn, onSave, onCancel }: {
  conn: DataConnection;
  onSave: (conn: DataConnection) => void;
  onCancel: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [name, setName] = useState(conn.name);
  const [host, setHost] = useState(conn.config.host || '');
  const [port, setPort] = useState(conn.config.port || 5432);
  const [username, setUsername] = useState(conn.config.username || '');
  const [password, setPassword] = useState('');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className={`w-96 rounded-xl border ${styles.cardBorder} ${styles.cardBg} shadow-2xl p-6 space-y-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText}`}>{t('dw.conn.editTitle')}</h3>
        <div className="space-y-3">
          <div>
            <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.name')}</label>
            <input value={name} onChange={e => setName(e.target.value)}
              className={`w-full p-2 border ${styles.inputBorder} rounded text-xs ${styles.inputBg} ${styles.inputText}`} />
          </div>
          <div>
            <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.host')}</label>
            <input value={host} onChange={e => setHost(e.target.value)}
              className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.port')}</label>
              <input type="number" value={port} onChange={e => setPort(Number(e.target.value))}
                className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
            </div>
            <div>
              <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.username')}</label>
              <input value={username} onChange={e => setUsername(e.target.value)}
                className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
            </div>
          </div>
          <div>
            <label className={`text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('dw.conn.editPassword')}</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="******"
              className={`w-full p-2 border ${styles.inputBorder} rounded text-xs font-mono ${styles.inputBg} ${styles.inputText}`} />
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onCancel}
            className={`px-4 py-1.5 text-xs rounded border ${styles.cardBorder} ${styles.cardTextMuted} cursor-pointer hover:${styles.appBg}`}>
            {t('dw.conn.cancel')}
          </button>
          <button onClick={() => onSave({ ...conn, name, config: { ...conn.config, host, port, username, password: password || conn.config.password } })}
            className={`px-4 py-1.5 text-xs rounded ${styles.accentBg} ${styles.accentHover} ${styles.cardText} font-semibold cursor-pointer`}>
            {t('dw.conn.save')}
          </button>
        </div>
      </div>
    </div>
  );
}

// Inline SQL Query Console (embedded, reuses datasource ID)
function InlineSqlConsole({ datasourceId }: { datasourceId: string }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [sql, setSql] = React.useState('SELECT * FROM orders LIMIT 10');
  const [result, setResult] = React.useState<any>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [collapsed, setCollapsed] = React.useState(false);

  const execute = async () => {
    setLoading(true); setError(null);
    try {
      const token = localStorage.getItem('token') || '';
      const res = await fetch('/api/v1/engine/data/query/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ datasource_id: datasourceId, sql: sql.trim(), max_rows: 500, timeout_seconds: 30 })
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      const d = data.data || data;
      // 后端返回 columns 为对象数组 [{name, label, type}]，rows 以 columnLabel 为键
      // 提取 label 作为列名，用于渲染和行数据取值
      const rawCols = d.columns || [];
      const colLabels: string[] = rawCols.map((c: any) =>
        typeof c === 'string' ? c : (c.label || c.name || '')
      );
      setResult({ columns: colLabels, rows: d.rows || [], rowCount: d.rowCount || 0, elapsedMs: d.elapsedMs || 0 });
    } catch (e: any) {
      setError(e?.message || t("dw.execFailed"));
      setResult(null);
    } finally { setLoading(false); }
  };

  return (
    <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden`}>
      <div className={`${styles.sidebarBg} px-4 py-2 flex items-center justify-between cursor-pointer select-none`}
           onClick={() => setCollapsed(!collapsed)}>
        <div className={`flex items-center gap-2 text-xs font-bold ${styles.cardText}`}>
          <LucideIcon name="Terminal" size={14} className={`${styles.accentText}`} />
          <span>{t("dw.sqlConsole")}</span>
        </div>
        <LucideIcon name={collapsed ? 'ChevronDown' : 'ChevronUp'} size={14} className={`${styles.cardTextMuted}`} />
      </div>
      {!collapsed && (
        <div className={`${styles.cardBg} p-3 space-y-3`}>
          {/* SQL editor + run button */}
          <div className="flex gap-2">
            <textarea value={sql} onChange={e => setSql(e.target.value)}
              className={`flex-1 p-2 border ${styles.inputBorder} rounded text-xs font-mono resize-none outline-none focus:${styles.accentBorder} h-16 ${styles.inputBg} ${styles.inputText}`}
              placeholder="SELECT * FROM ..." spellCheck={false} />
            <button onClick={execute} disabled={loading}
              className={`px-4 py-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} text-xs font-semibold rounded cursor-pointer disabled:opacity-50 shrink-0`}>
              {loading ? t("dw.executing") : t("dw.runExec")}
            </button>
          </div>
          {/* Result */}
          {error && <div className={`${styles.dangerText} text-xs ${styles.appBg} p-2 rounded`}>⚠ {error}</div>}
          {result && !error && (
            <div>
              <div className={`flex items-center gap-3 text-[10px] ${styles.cardTextMuted} mb-2`}>
                <span className={`font-bold ${styles.accentText}`}>{result.rowCount} {t("dw.rowsUnit")}</span>
                <span>{result.elapsedMs}ms</span>
                <span>{result.columns.length} {t("dw.colsUnit")}</span>
              </div>
              <div className={`max-h-64 overflow-auto border ${styles.cardBorder} rounded`}>
                <table className="w-full text-[11px]">
                  <thead><tr className={`${styles.appBg}`}>
                    {result.columns.map((c: string) => (
                      <th key={c} className={`px-2 py-1 text-left font-bold ${styles.cardText} whitespace-nowrap border-b`}>{c}</th>
                    ))}
                  </tr></thead>
                  <tbody>
                    {result.rows.slice(0, 50).map((row: any, i: number) => (
                      <tr key={i} className={i % 2 ? `${styles.appBg}/50` : ''}>
                        {result.columns.map((c: string) => (
                          <td key={c} className={`px-2 py-0.5 ${styles.cardTextMuted} border-b ${styles.cardBorder} max-w-[200px] truncate`}>
                            {row[c] === null ? <span className={`${styles.cardTextMuted} italic`}>NULL</span> : String(row[c])}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ConnectionsTab;
