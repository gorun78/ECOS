/* Extracted from DataWorkbenchLayout.tsx */
import React, { useState, useEffect } from 'react';
import LucideIcon from '../LucideIcon';
import { getSourceIcon, getSourceTypeLabel } from '../helpers';
import type { DataConnection } from '../types';
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { deleteDataSource, updateDataSource, fetchDataSourceResources, triggerMetadataCollect } from '../api';
import { apiFetchData } from '../../../api';

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
}

const ConnectionsTab: React.FC<ConnectionsTabProps> = ({ connections, showToast, setConnections, handleCreateConnection, testingConnId, setTestingConnId, testingLogs, selectedConnId, setSelectedConnId, showAddConn, setShowAddConn, newConnName, setNewConnName, newConnType, setNewConnType, newConnHost, setNewConnHost, newConnPort, setNewConnPort, newConnUser, setNewConnUser, onTestConnection, t }) => {
  const { styles } = useTheme();
  const { t: tt } = useLanguage();
  const [editingConn, setEditingConn] = useState<DataConnection | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [loadingTables, setLoadingTables] = useState(false);
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(20); // 默认20，从引擎配置获取
  // PMO-37 元数据获取策略
  const [collecting, setCollecting] = useState(false);
  const [lastCollectInfo, setLastCollectInfo] = useState<{ time?: string; countMethod?: string } | null>(null);

  // 从引擎配置获取每页行数
  useEffect(() => {
    apiFetchData<any>('/api/v1/engine/data/settings')
      .then((cfg: any) => {
        const exec = cfg?.execution || cfg?.data?.execution || {};
        const pageSize = parseInt(exec['catalog.page_size'] || exec['memory.max_rows'] || '20', 10);
        // memory.max_rows 是查询上限，分页用合理值
        setTablePageSize(Math.min(pageSize > 0 ? pageSize : 20, 100));
      })
      .catch(() => {});
  }, []);

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
          <button
            key={conn.id}
            onClick={() => setSelectedConnId(conn.id)}
            className={`w-full text-left p-3 rounded-lg border transition-all text-xs flex flex-col gap-1.5 ${
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
          </button>
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
                        onChange={e => setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, trigger: e.target.value as any } } : c))}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
                      >
                        {STRATEGY_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.key)}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>{t("dw.strategy.count")}</label>
                      <select
                        value={conn.strategy?.countMethod || 'OFF'}
                        onChange={e => setConnections(connections.map(c => c.id === conn.id ? { ...c, strategy: { ...c.strategy, countMethod: e.target.value as any } } : c))}
                        className={`w-full text-xs p-1.5 rounded border ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
                      >
                        {COUNT_METHOD_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.key)}</option>)}
                      </select>
                    </div>
                    <p className={`text-[10px] ${styles.cardTextMuted}`}>{t("dw.strategy.hint")}</p>
                    <div className="flex gap-2">
                      <button
                        disabled={collecting}
                        onClick={async () => {
                          setCollecting(true);
                          const r = await triggerMetadataCollect(conn.id);
                          setCollecting(false);
                          if (r?.taskId) showToast('success', t('dw.strategy.collectStarted').replace('{id}', String(r.taskId)));
                          else showToast('error', t('dw.strategy.collectFailed').replace('{err}', 'HTTP'));
                        }}
                        className={`px-2 py-1 text-[11px] font-semibold rounded transition-colors flex items-center gap-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} disabled:opacity-40`}
                      >
                        <LucideIcon name="RefreshCw" size={11} className={collecting ? 'animate-spin' : ''} />
                        {collecting ? t('dw.strategy.collecting') : t('dw.strategy.collectNow')}
                      </button>
                    </div>
                    <div className={`text-[10px] ${styles.cardTextMuted}`}>
                      {t("dw.strategy.lastCollect")}:{' '}
                      {conn.metadataConfig?.lastCollectTime
                        ? new Date(String(conn.metadataConfig.lastCollectTime)).toLocaleString()
                        : t('dw.strategy.neverCollected')}
                    </div>
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
                    onClick={() => {
                      setConnections(connections.map(c => c.id === selectedConnId ? { ...c, tablesAvailable: [] } : c));
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
                    <div key={tbl.name} className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.appBg}/50`}>
                      <div className={`${styles.sidebarBg}/70 px-4 py-2 flex justify-between items-center border-b ${styles.cardBorder}`}>
                        <div className="flex items-center gap-2 text-xs">
                           <LucideIcon name="Table" size={13} className={`${styles.accentText}`} />
                          <span className={`font-bold font-mono ${styles.cardText}`}>{tbl.name}</span>
                        </div>
                        <span className={`text-[10px] ${styles.cardTextMuted} ${styles.cardBg} border ${styles.cardBorder} px-2 py-0.5 rounded-full font-mono`}>
                           {t("dw.physicalRows")} {tbl.rowCount != null && tbl.rowCount > 0 ? tbl.rowCount.toLocaleString() : t("dw.conn.rowsUnknown")} {tbl.rowCount != null && tbl.rowCount > 0 ? t("dw.rowsUnit") : ''}
                        </span>
                      </div>

                      {tbl.columns.length > 0 && (
                        <div className={`p-3 ${styles.cardBg}`}>
                          <div className="grid grid-cols-4 gap-2 text-[11px]">
                            {tbl.columns.map(col => (
                              <div key={col.name} className={`p-1.5 ${styles.appBg} rounded border ${styles.cardBorder} flex flex-col font-mono`}>
                                <span className={`${styles.cardText} truncate font-semibold`} title={col.name}>{col.name}</span>
                                <span className={`text-[9px] ${styles.cardTextMuted} mt-0.5`}>{col.type}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
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
      setResult({ columns: d.columns || [], rows: d.rows || [], rowCount: d.rowCount || 0, elapsedMs: d.elapsedMs || 0 });
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
