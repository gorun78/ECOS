/**
 * Modals — Add Connection / Add Sync / Add Health Check panels
 * Extracted from DataWorkbenchLayout.tsx
 * @license Apache-2.0
 */
import React from 'react';
import LucideIcon from './LucideIcon';
import type { DataConnection } from './types';

interface AddConnectionModalProps {
  t: (key: string) => string;
  locale: string;
  newConnName: string; setNewConnName: (v: string) => void;
  newConnType: string; setNewConnType: (v: string) => void;
  newConnHost: string; setNewConnHost: (v: string) => void;
  newConnPort: number; setNewConnPort: (v: number) => void;
  newConnUser: string; setNewConnUser: (v: string) => void;
  onClose: () => void;
  onCreate: () => void;
}

export function AddConnectionModal({ t, locale, newConnName, setNewConnName, newConnType, setNewConnType, newConnHost, setNewConnHost, newConnPort, setNewConnPort, newConnUser, setNewConnUser, onClose, onCreate }: AddConnectionModalProps) {
  return (
    <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none">
      <div className="bg-white rounded-xl shadow-lg border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">
        <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
          <h3 className="text-xs font-bold text-slate-800 flex items-center gap-1.5"><LucideIcon name="Database" size={14} className="text-blue-500" /><span>{t('dw.txt.332103')}</span></h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1"><LucideIcon name="X" size={14} /></button>
        </div>
        <div className="p-5 space-y-4 text-xs">
          <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.58314a')}</label><input type="text" placeholder="e.g. 生产派班主库_Read" value={newConnName} onChange={e => setNewConnName(e.target.value)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:border-blue-500 focus:outline-hidden" /></div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.fe3609')}</label><select value={newConnType} onChange={e => setNewConnType(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white font-mono"><option value="postgresql">PostgreSQL</option><option value="s3">Amazon S3</option><option value="rest_api">REST API</option><option value="sftp">SFTP</option><option value="sap">SAP ERP</option></select></div>
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.7adcd8')}</label><input type="number" value={newConnPort} onChange={e => setNewConnPort(parseInt(e.target.value) || 0)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono" /></div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.5297f6')}</label><input type="text" placeholder="localhost" value={newConnHost} onChange={e => setNewConnHost(e.target.value)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden" /></div>
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.c00d41')}</label><input type="text" placeholder="readonly_user" value={newConnUser} onChange={e => setNewConnUser(e.target.value)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden" /></div>
          </div>
        </div>
        <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2 bg-slate-50/50">
          <button onClick={onClose} className="px-3 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded text-xs transition-colors cursor-pointer">{locale === 'zh' ? '取消' : 'Cancel'}</button>
          <button onClick={onCreate} className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded text-xs transition-colors cursor-pointer">{locale === 'zh' ? '保存并连线' : 'Save & Connect'}</button>
        </div>
      </div>
    </div>
  );
}

interface AddSyncModalProps {
  t: (key: string) => string;
  locale: string;
  newSyncName: string; setNewSyncName: (v: string) => void;
  newSyncConn: string; setNewSyncConn: (v: string) => void;
  newSyncTable: string; setNewSyncTable: (v: string) => void;
  newSyncMode: string; setNewSyncMode: (v: string) => void;
  newSyncSched: string; setNewSyncSched: (v: string) => void;
  connections: DataConnection[];
  onClose: () => void;
  onCreate: () => void;
}

export function AddSyncModal({ t, locale, newSyncName, setNewSyncName, newSyncConn, setNewSyncConn, newSyncTable, setNewSyncTable, newSyncMode, setNewSyncMode, newSyncSched, setNewSyncSched, connections, onClose, onCreate }: AddSyncModalProps) {
  return (
    <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none">
      <div className="bg-white rounded-xl shadow-lg border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">
        <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
          <h3 className="text-xs font-bold text-slate-800 flex items-center gap-1.5"><LucideIcon name="Import" size={14} className="text-green-500" /><span>{t('dw.txt.ca4caf')}</span></h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1"><LucideIcon name="X" size={14} /></button>
        </div>
        <div className="p-5 space-y-3 text-xs">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.58314a')}</label><input type="text" placeholder="e.g. 每日航班同步" value={newSyncName} onChange={e => setNewSyncName(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded focus:outline-hidden" /></div>
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.d4baa9')}</label><select value={newSyncConn} onChange={e => setNewSyncConn(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white font-mono">{connections.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.63a3c2')}</label><input type="text" placeholder="public.raw_flights" value={newSyncTable} onChange={e => setNewSyncTable(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono" /></div>
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.b9ac77')}</label><select value={newSyncMode} onChange={e => setNewSyncMode(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"><option value="snapshot">Snapshot</option><option value="incremental">Incremental</option><option value="append">Append</option></select></div>
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.d636a1')}</label><select value={newSyncSched} onChange={e => setNewSyncSched(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"><option value="manual">Manual</option><option value="hourly">Hourly</option><option value="daily">Daily</option><option value="cron">Cron</option></select></div>
          </div>
        </div>
        <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2 bg-slate-50/50">
          <button onClick={onClose} className="px-3 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded text-xs cursor-pointer">{locale === 'zh' ? '取消' : 'Cancel'}</button>
          <button onClick={onCreate} className="px-3.5 py-1.5 bg-green-600 hover:bg-green-700 text-white font-semibold rounded text-xs cursor-pointer">{locale === 'zh' ? '初始化同步任务' : 'Create Sync Task'}</button>
        </div>
      </div>
    </div>
  );
}

interface AddHealthCheckModalProps {
  t: (key: string) => string;
  locale: string;
  newCheckName: string; setNewCheckName: (v: string) => void;
  newCheckDs: string; setNewCheckDs: (v: string) => void;
  newCheckType: string; setNewCheckType: (v: string) => void;
  newCheckThreshold: string; setNewCheckThreshold: (v: string) => void;
  onClose: () => void;
  onCreate: () => void;
}

export function AddHealthCheckModal({ t, locale, newCheckName, setNewCheckName, newCheckDs, setNewCheckDs, newCheckType, setNewCheckType, newCheckThreshold, setNewCheckThreshold, onClose, onCreate }: AddHealthCheckModalProps) {
  return (
    <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none">
      <div className="bg-white rounded-xl shadow-lg border border-slate-200 max-w-md w-full overflow-hidden flex flex-col">
        <div className="px-5 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
          <h3 className="text-xs font-bold text-slate-800 flex items-center gap-1.5"><LucideIcon name="ShieldAlert" size={14} className="text-amber-500" /><span>{t('dw.txt.209a45')}</span></h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1"><LucideIcon name="X" size={14} /></button>
        </div>
        <div className="p-5 space-y-3 text-xs">
          <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.58314a')}</label><input type="text" placeholder="e.g. Flights Row Count" value={newCheckName} onChange={e => setNewCheckName(e.target.value)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden" /></div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.4868c0')}</label><input type="text" placeholder="ds_flights_clean" value={newCheckDs} onChange={e => setNewCheckDs(e.target.value)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono" /></div>
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.6f03d0')}</label><select value={newCheckType} onChange={e => setNewCheckType(e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-300 rounded bg-white"><option value="row_count">{t('dw.txt.d84bfb')}</option><option value="null_check">{t('dw.txt.4d25b4')}</option><option value="schema_check">{t('dw.txt.645259')}</option><option value="freshness">{t('dw.txt.8a99dc')}</option></select></div>
          </div>
          {newCheckType !== 'schema_check' && (
            <div className="space-y-1"><label className="text-[10px] font-semibold text-slate-600 block">{t('dw.txt.2063a2')}</label><input type="text" placeholder={newCheckType === 'row_count' ? '1000' : newCheckType === 'null_check' ? '2.0' : '120'} value={newCheckThreshold} onChange={e => setNewCheckThreshold(e.target.value)} className="w-full px-3 py-1.5 border border-slate-300 rounded focus:outline-hidden font-mono" /></div>
          )}
        </div>
        <div className="px-5 py-3 border-t border-slate-100 flex justify-end gap-2 bg-slate-50/50">
          <button onClick={onClose} className="px-3 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded text-xs cursor-pointer">{locale === 'zh' ? '取消' : 'Cancel'}</button>
          <button onClick={onCreate} className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded text-xs cursor-pointer">{locale === 'zh' ? '保存规则' : 'Save Rule'}</button>
        </div>
      </div>
    </div>
  );
}

interface ExternalInterfacesDrawerProps {
  t: (key: string) => string;
  connections: DataConnection[];
  onClose: () => void;
}

export function ExternalInterfacesDrawer({ t, connections, onClose }: ExternalInterfacesDrawerProps) {
  return (
    <div className="absolute top-12 right-0 bottom-0 w-96 bg-slate-900 text-slate-100 border-l border-slate-800 shadow-2xl z-40 flex flex-col overflow-hidden select-none">
      <div className="px-5 py-4 border-b border-slate-800 flex justify-between items-center bg-slate-950/40 shrink-0">
        <h3 className="text-xs font-bold text-white flex items-center gap-2"><LucideIcon name="Layers" size={14} className="text-amber-500 animate-pulse" /><span>{t('dw.txt.c5dda0')}</span></h3>
        <button onClick={onClose} className="text-slate-400 hover:text-white p-1"><LucideIcon name="X" size={14} /></button>
      </div>
      <div className="flex-1 overflow-y-auto p-5 space-y-4">
        <p className="text-[11px] text-slate-400 leading-relaxed font-sans">以下是当前 ECOS 集成平台与外界各大物理系统、调度系统、云对象存储以及 ERP 财务系统的注册接口。</p>
        {connections.map(conn => (
          <div key={conn.id} className="p-3 bg-slate-950 rounded-lg border border-slate-800 text-xs space-y-2.5">
            <div className="flex justify-between items-center border-b border-slate-900 pb-1.5">
              <span className="font-bold text-white font-mono">{conn.id}</span>
              <span className={`text-[9px] font-mono px-1.5 rounded-full ${conn.status === 'connected' ? 'bg-emerald-900/30 text-emerald-400' : 'bg-red-900/30 text-red-400'}`}>{conn.status.toUpperCase()}</span>
            </div>
            <div className="space-y-1 text-[11px] text-slate-400">
              <div><span className="text-slate-600 font-semibold uppercase text-[9px] block">{t('dw.txt.f274bd')}</span><span className="text-slate-200">{conn.name}</span></div>
              <div><span className="text-slate-600 font-semibold uppercase text-[9px] block">{t('dw.txt.dec92b')}</span><span className="text-slate-300 font-mono">ECOS Connector v1.2 [{conn.type.toUpperCase()}]</span></div>
              {conn.config.host && <div><span className="text-slate-600 font-semibold uppercase text-[9px] block">{t('dw.txt.15e5bb')}</span><span className="text-slate-300 font-mono">{conn.config.host}:{conn.config.port || 5432}</span></div>}
            </div>
          </div>
        ))}
      </div>
      <div className="p-4 bg-slate-950 border-t border-slate-800 text-[10px] text-slate-500 text-center select-none font-mono">Aviation Integration Gateway (Total: {connections.length} Endpoints)</div>
    </div>
  );
}
