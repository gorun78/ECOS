/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import LucideIcon from '../LucideIcon';
import { getSourceIcon, getSourceTypeLabel } from '../helpers';
import type { DataConnection } from '../types';

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
  t: (key:string)=>string;
}

const ConnectionsTab: React.FC<ConnectionsTabProps> = ({ connections, showToast, setConnections, handleCreateConnection, testingConnId, setTestingConnId, testingLogs, selectedConnId, setSelectedConnId, showAddConn, setShowAddConn, newConnName, setNewConnName, newConnType, setNewConnType, newConnHost, setNewConnHost, newConnPort, setNewConnPort, newConnUser, setNewConnUser, t }) => (
<div className="flex-1 flex overflow-hidden">
  {/* Connections list panel */}
  <div className="w-72 bg-white border-r border-slate-200 flex flex-col overflow-hidden shrink-0">
    <div className="p-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/40">
      <h3 className="text-xs font-bold text-slate-800">{t("dw.txt.64d3b2")}</h3>
      <button
        onClick={() => setShowAddConn(true)}
        className="p-1 rounded bg-blue-600 text-white hover:bg-blue-700 text-xs flex items-center gap-1 cursor-pointer font-medium"
      >
        <LucideIcon name="Plus" size={12} />
        <span>{t("dw.txt.30f7dd")}</span>
      </button>
    </div>

    <div className="flex-1 overflow-y-auto p-2 space-y-1">
      {connections.map(conn => {
        const isSelected = selectedConnId === conn.id;
        return (
          <button
            key={conn.id}
            onClick={() => setSelectedConnId(conn.id)}
            className={`w-full text-left p-3 rounded-lg border transition-all text-xs flex flex-col gap-1.5 ${
              isSelected
                ? 'bg-blue-50/80 border-blue-200 shadow-2xs'
                : 'border-slate-100 hover:bg-slate-50'
            }`}
          >
            <div className="flex justify-between items-center">
              <span className="font-semibold text-slate-800 truncate pr-2">{conn.name}</span>
              <span className={`h-2 w-2 rounded-full ${
                conn.status === 'connected' ? 'bg-emerald-500' :
                conn.status === 'error' ? 'bg-red-500' : 'bg-amber-500'
              }`} title={conn.status} />
            </div>
            <div className="flex justify-between text-[10px] text-slate-400 font-mono">
              <span>类型: {conn.type.toUpperCase()}</span>
              <span>{conn.tablesAvailable.length} 表/目录</span>
            </div>
          </button>
        );
      })}
    </div>
  </div>

  {/* Connection Detail View */}
  {(() => {
    const conn = connections.find(c => c.id === selectedConnId);
    if (!conn) return <div className="flex-1 p-6 text-slate-400">{t("dw.txt.282170")}</div>;
    return (
      <div className="flex-1 flex flex-col overflow-hidden bg-white">
        {/* Detail banner */}
        <div className="p-6 border-b border-slate-200 flex justify-between items-center bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-full border border-blue-300 bg-blue-50 text-blue-700 flex items-center justify-center">
              <LucideIcon name={getSourceIcon(conn.type)} size={20} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-sm font-bold text-slate-800">{conn.name}</span>
                <span className="text-[10px] bg-slate-100 text-slate-500 font-mono px-2 py-0.5 rounded-full uppercase">
                  {conn.type}
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-1">{getSourceTypeLabel(conn.type)}</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => /* testConnection moved to parent */ (conn.id)}
              disabled={testingConnId !== null}
              className="px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded transition-all cursor-pointer flex items-center gap-1.5"
            >
              <LucideIcon name="Wifi" size={13} />
              <span>{t("dw.txt.f636a1")}</span>
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Technical specifications */}
          <div className="grid grid-cols-3 gap-6">
            <div className="col-span-1 bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
              <h4 className="text-xs font-bold text-slate-700 border-b border-slate-200 pb-1.5 flex items-center gap-1.5">
                <LucideIcon name="Settings" size={12} className="text-slate-500" />
                物理连接配置参数
              </h4>

              <div className="text-xs space-y-2.5">
                {conn.config.host && (
                  <div>
                    <span className="text-[10px] text-slate-400 uppercase block font-mono">{t("dw.txt.16e578")}</span>
                    <span className="font-mono font-medium text-slate-800">{conn.config.host}</span>
                  </div>
                )}
                {conn.config.port && (
                  <div>
                    <span className="text-[10px] text-slate-400 uppercase block font-mono">{t("dw.txt.4016cf")}</span>
                    <span className="font-mono font-medium text-slate-800">{conn.config.port}</span>
                  </div>
                )}
                {conn.config.username && (
                  <div>
                    <span className="text-[10px] text-slate-400 uppercase block font-mono">{t("dw.txt.1169ed")}</span>
                    <span className="font-mono font-medium text-slate-800">{conn.config.username}</span>
                  </div>
                )}
                {conn.config.bucket && (
                  <div>
                    <span className="text-[10px] text-slate-400 uppercase block font-mono">{t("dw.txt.eb9003")}</span>
                    <span className="font-mono font-medium text-slate-800 truncate block">{conn.config.bucket}</span>
                  </div>
                )}
                {conn.config.endpointUrl && (
                  <div>
                    <span className="text-[10px] text-slate-400 uppercase block font-mono">{t("dw.txt.3cd968")}</span>
                    <span className="font-mono font-medium text-slate-800 truncate block">{conn.config.endpointUrl}</span>
                  </div>
                )}
                <hr className="border-slate-200" />
                <div>
                  <span className="text-[10px] text-slate-400 uppercase block font-mono">{t("dw.txt.165c7b")}</span>
                  <span className="text-slate-600 text-[11px] font-medium">{conn.config.lastTested || '从未测试'}</span>
                </div>
              </div>
            </div>

            {/* Database physical table browser */}
            <div className="col-span-2 space-y-4">
              <h4 className="text-xs font-bold text-slate-800 flex items-center justify-between">
                <span>{t("dw.txt.42bc1b")}</span>
                <span className="text-[10px] text-slate-500 font-normal"> Ontology 映射只读元数据清单 ({conn.tablesAvailable.length} 表)</span>
              </h4>

              {conn.tablesAvailable.length === 0 ? (
                <div className="p-8 border border-dashed border-slate-300 rounded-xl text-center text-slate-400 text-xs flex flex-col items-center gap-2">
                  <LucideIcon name="AlertTriangle" size={24} className="text-amber-500" />
                  <span>{t("dw.txt.2ce9e0")}</span>
                  <span>{t("dw.txt.44e8b3")}</span>
                </div>
              ) : (
                <div className="space-y-4">
                  {conn.tablesAvailable.map(tbl => (
                    <div key={tbl.name} className="border border-slate-200 rounded-xl overflow-hidden bg-slate-50/50">
                      <div className="bg-slate-100/70 px-4 py-2 flex justify-between items-center border-b border-slate-200">
                        <div className="flex items-center gap-2 text-xs">
                          <LucideIcon name="Table" size={13} className="text-blue-500" />
                          <span className="font-bold font-mono text-slate-800">{tbl.name}</span>
                        </div>
                        <span className="text-[10px] text-slate-500 bg-white border border-slate-200 px-2 py-0.5 rounded-full font-mono">
                          物理总行数: {tbl.rowCount.toLocaleString()} 行
                        </span>
                      </div>

                      <div className="p-3 bg-white">
                        <div className="grid grid-cols-4 gap-2 text-[11px]">
                          {tbl.columns.map(col => (
                            <div key={col.name} className="p-1.5 bg-slate-50 rounded border border-slate-100 flex flex-col font-mono">
                              <span className="text-slate-800 truncate font-semibold" title={col.name}>{col.name}</span>
                              <span className="text-[9px] text-slate-400 mt-0.5">{col.type}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Diagnostic Log Terminal */}
          {testingLogs.length > 0 && (
            <div className="bg-slate-950 p-4 rounded-xl text-xs font-mono text-slate-200 space-y-1.5 border border-slate-800 select-text leading-relaxed">
              <div className="text-[10px] text-slate-500 tracking-wider uppercase font-semibold mb-2 border-b border-slate-900 pb-1 flex justify-between items-center select-none">
                <span>{t("dw.txt.26079a")}</span>
                <span className="text-blue-400">JDBC API Log v1.4</span>
              </div>
              {testingLogs.map((log, i) => (
                <div key={i} className={
                  log.includes('ERROR') || log.includes('❌') ? 'text-red-400' :
                  log.includes('SUCCESS') || log.includes('✅') ? 'text-emerald-400' :
                  log.includes('🔑') ? 'text-blue-400' : 'text-slate-300'
                }>
                  {log}
                </div>
              ))}
            </div>
          )}

          {/* ═══ SQL 查询控制台 ═══ */}
          <InlineSqlConsole datasourceId={conn.id} />
        </div>
      </div>
    );
  })()}
</div>
);

// ═══ Inline SQL Query Console (嵌入式，复用数据源 ID) ═══
function InlineSqlConsole({ datasourceId }: { datasourceId: string }) {
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
        body: JSON.stringify({ datasourceId, sql: sql.trim(), maxRows: 500, timeoutSeconds: 30 })
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();
      const d = data.data || data;
      setResult({ columns: d.columns || [], rows: d.rows || [], rowCount: d.rowCount || 0, elapsedMs: d.elapsedMs || 0 });
    } catch (e: any) {
      setError(e?.message || '执行失败');
      setResult(null);
    } finally { setLoading(false); }
  };

  return (
    <div className="border border-slate-200 rounded-xl overflow-hidden">
      <div className="bg-slate-100 px-4 py-2 flex items-center justify-between cursor-pointer select-none"
           onClick={() => setCollapsed(!collapsed)}>
        <div className="flex items-center gap-2 text-xs font-bold text-slate-700">
          <LucideIcon name="Terminal" size={14} className="text-blue-600" />
          <span>SQL 查询控制台</span>
        </div>
        <LucideIcon name={collapsed ? 'ChevronDown' : 'ChevronUp'} size={14} className="text-slate-400" />
      </div>
      {!collapsed && (
        <div className="bg-white p-3 space-y-3">
          {/* SQL 编辑器 + 执行按钮 */}
          <div className="flex gap-2">
            <textarea value={sql} onChange={e => setSql(e.target.value)}
              className="flex-1 p-2 border border-slate-200 rounded text-xs font-mono resize-none outline-none focus:border-blue-400 h-16"
              placeholder="SELECT * FROM ..." spellCheck={false} />
            <button onClick={execute} disabled={loading}
              className="px-4 py-1 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded cursor-pointer disabled:opacity-50 shrink-0">
              {loading ? '执行中...' : '▶ 执行'}
            </button>
          </div>
          {/* 结果 */}
          {error && <div className="text-rose-500 text-xs bg-rose-50 p-2 rounded">⚠ {error}</div>}
          {result && !error && (
            <div>
              <div className="flex items-center gap-3 text-[10px] text-slate-500 mb-2">
                <span className="text-emerald-600 font-bold">{result.rowCount} 行</span>
                <span>{result.elapsedMs}ms</span>
                <span>{result.columns.length} 列</span>
              </div>
              <div className="max-h-64 overflow-auto border border-slate-200 rounded">
                <table className="w-full text-[11px]">
                  <thead><tr className="bg-slate-50">
                    {result.columns.map((c: string) => (
                      <th key={c} className="px-2 py-1 text-left font-bold text-slate-700 whitespace-nowrap border-b">{c}</th>
                    ))}
                  </tr></thead>
                  <tbody>
                    {result.rows.slice(0, 50).map((row: any, i: number) => (
                      <tr key={i} className={i % 2 ? 'bg-slate-50/50' : ''}>
                        {result.columns.map((c: string) => (
                          <td key={c} className="px-2 py-0.5 text-slate-600 border-b border-slate-100 max-w-[200px] truncate">
                            {row[c] === null ? <span className="text-slate-400 italic">NULL</span> : String(row[c])}
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
}

export default ConnectionsTab;
