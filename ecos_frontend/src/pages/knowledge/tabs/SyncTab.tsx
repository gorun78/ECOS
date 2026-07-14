import React, { useState, useEffect, useCallback } from 'react';
import { RefreshCw, ShieldAlert, AlertTriangle, Clock, Check, FileClock, RotateCw, AlertCircle, Download, Terminal } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { MetadataAsset } from '../typesAndConstants';

const DEMO_ASSETS: MetadataAsset[] = [
  { id: 'int-1', source: 'integration', name: 'ds_flights_clean (航班运行大宽表)', type: 'Doris 物理表', recordsOrFields: '14,250条记录 / 8列', syncStatus: 'synced', chunksCount: 45, lastSynced: '2026-07-04 10:20' },
  { id: 'int-2', source: 'integration', name: 'ds_pilots_biography (机组档案大宽表)', type: 'Doris 物理表', recordsOrFields: '380条记录 / 6列', syncStatus: 'synced', chunksCount: 12, lastSynced: '2026-07-04 10:20' },
  { id: 'int-3', source: 'integration', name: 'flights_raw (签派流水 Bronze 表)', type: 'PostgreSQL 物理表', recordsOrFields: '240,000条记录 / 3列', syncStatus: 'pending', chunksCount: 0, lastSynced: '未同步' },
  { id: 'ont-1', source: 'ontology', name: 'AviationFlight (航班对象主体)', type: 'Ontology ObjectType', recordsOrFields: '属性: 14个 / 动作: 2个', syncStatus: 'synced', chunksCount: 18, lastSynced: '2026-07-04 11:15' },
  { id: 'ont-2', source: 'ontology', name: 'AviationPilot (飞行员对象主体)', type: 'Ontology ObjectType', recordsOrFields: '属性: 9个 / 动作: 1个', syncStatus: 'synced', chunksCount: 12, lastSynced: '2026-07-04 11:15' },
  { id: 'sec-1', source: 'security', name: 'Org_EU_Ops (欧盟运营专属物理隔离)', type: '组织安全性规范', recordsOrFields: 'IP Whitelist / 跨域限制', syncStatus: 'synced', chunksCount: 15, lastSynced: '2026-07-04 10:40' },
  { id: 'sec-3', source: 'security', name: 'ssn_number (列级最高掩蔽REDACT策略)', type: '动态脱敏安全策略', recordsOrFields: '敏感字段 / 列级脱敏', syncStatus: 'pending', chunksCount: 0, lastSynced: '未同步' },
];

export default function SyncTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [assets, setAssets] = useState<MetadataAsset[]>(DEMO_ASSETS);
  const [isSyncingAll, setIsSyncingAll] = useState(false);
  const [isSchemaDrift, setIsSchemaDrift] = useState(false);
  const [isSlaBreach, setIsSlaBreach] = useState(false);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [syncLogs, setSyncLogs] = useState<string[]>([]);

  const loadMetadata = async () => {
    try {
      const meta = await knowledgeApi.fetchIntegrationMetadata();
      if (meta?.simulationState) {
        setIsSchemaDrift(meta.simulationState.isSchemaDriftActive);
        setIsSlaBreach(meta.simulationState.isSlaBreachActive);
      }
    } catch { /* fallback to defaults */ }
    try {
      const logs = await knowledgeApi.fetchIntegrationLogs();
      if (Array.isArray(logs)) setAuditLogs(logs);
    } catch { /* fallback */ }
  };

  useEffect(() => { loadMetadata(); }, []);

  const handleSyncAll = async () => {
    setIsSyncingAll(true);
    setSyncLogs(['🔄 [0.0s] 启动 AIP Closed-Loop 元数据提取与向量化索引计算管道...']);
    try {
      const result = await knowledgeApi.syncVectors({
        embeddingModel: 'text-embedding-004',
        chunkSize: 512,
        overlap: 50,
      }) as any;
      const logs = result?.logs || [];
      setSyncLogs([]);
      for (let i = 0; i < logs.length; i++) {
        await new Promise(r => setTimeout(r, 150));
        setSyncLogs(prev => [...prev, logs[i]]);
      }
      if (logs.length === 0) setSyncLogs(prev => [...prev, '✅ 同步任务已提交']);
    } catch (e: any) {
      setSyncLogs(prev => [...prev, `❌ 同步异常: ${e.message}`]);
    } finally {
      setIsSyncingAll(false);
    }
  };

  const handleToggleSimulation = async (type: 'drift' | 'sla' | 'reset') => {
    try {
      await knowledgeApi.toggleSimulationDrift(type, type !== 'reset');
      await loadMetadata();
    } catch { /* fallback: local state */ }
    if (type === 'drift') setIsSchemaDrift(prev => !prev);
    else if (type === 'sla') setIsSlaBreach(prev => !prev);
    else { setIsSchemaDrift(false); setIsSlaBreach(false); }
  };

  const handleSyncAsset = (id: string) => {
    setAssets(prev => prev.map(a => {
      if (a.id === id) return { ...a, syncStatus: 'synced' as const, chunksCount: a.chunksCount || Math.floor(Math.random() * 20) + 10, lastSynced: new Date().toISOString().replace('T', ' ').substring(0, 16) };
      return a;
    }));
  };

  return (
    <div className="space-y-6">
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText}`}>
            {locale === 'zh' ? '多模态联邦元数据集成中心' : 'Federated Metadata Integration'}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {locale === 'zh' ? '动态监控、抓取和转换物理数据集、逻辑本体语义以及最高安全规则定义至本地缓存中，等待切块向量化。' : 'Monitor, fetch and transform datasets, ontology semantics and security rules for chunked vectorization.'}
          </p>
        </div>
        <button onClick={handleSyncAll} disabled={isSyncingAll} className={`px-4 py-2 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg transition-all flex items-center gap-1.5 shadow-sm cursor-pointer ${isSyncingAll ? 'opacity-70 cursor-not-allowed' : ''}`}>
          {isSyncingAll
            ? <><span className="w-3.5 h-3.5 border-2 border-slate-100 border-t-transparent rounded-full animate-spin" /><span>{locale === 'zh' ? '联邦网格抓取中...' : 'Syncing...'}</span></>
            : <><RefreshCw size={12} /><span>{locale === 'zh' ? '一键全站元数据同步' : 'Full Sync'}</span></>
          }
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <h3 className="font-extrabold text-slate-800 text-xs flex items-center gap-1.5 text-rose-600">
                <ShieldAlert size={13} /><span>{locale === 'zh' ? '物理元数据漂移与调度 SLA 仿真中心' : 'Exception Lab'}</span>
              </h3>
              <span className={`px-2 py-0.5 rounded-full text-[9px] font-extrabold border ${isSchemaDrift || isSlaBreach ? 'bg-rose-50 border-rose-200 text-rose-600 animate-pulse' : 'bg-slate-50 border-slate-200 text-slate-500'}`}>
                {isSchemaDrift || isSlaBreach ? '● 异常激活' : '● 稳定'}
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              <button onClick={() => handleToggleSimulation('drift')} className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${isSchemaDrift ? 'bg-rose-50 border-rose-300 text-rose-700' : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'}`}>
                <AlertTriangle size={11} className={isSchemaDrift ? 'animate-bounce' : ''} /><span>{locale === 'zh' ? '注入 Schema 漂移' : 'Schema Drift'}</span>
              </button>
              <button onClick={() => handleToggleSimulation('sla')} className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${isSlaBreach ? 'bg-amber-50 border-amber-300 text-amber-700' : 'bg-white border-slate-200 text-slate-700 hover:bg-slate-50'}`}>
                <Clock size={11} className={isSlaBreach ? 'animate-pulse' : ''} /><span>{locale === 'zh' ? '注入 SLA 断流' : 'SLA Breach'}</span>
              </button>
              <button onClick={() => handleToggleSimulation('reset')} className="px-3 py-1.5 rounded-lg text-[10px] font-bold bg-slate-100 hover:bg-slate-200 text-slate-700 cursor-pointer transition-all flex items-center gap-1 border border-slate-300 ml-auto">
                <Check size={11} /><span>{locale === 'zh' ? '复位' : 'Reset'}</span>
              </button>
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-2.5">
            <h4 className="font-extrabold text-slate-800 text-[11px] flex items-center gap-1 text-slate-700 border-b border-slate-100 pb-2">
              <FileClock size={12} /><span>{locale === 'zh' ? '审计记录' : 'Audit Logs'} ({auditLogs.length})</span>
            </h4>
            <div className="space-y-1.5 max-h-36 overflow-y-auto font-mono text-[9px]">
              {auditLogs.length === 0
                ? <p className="text-slate-400 py-4 text-center">{locale === 'zh' ? '暂无审计事件' : 'No audit events'}</p>
                : auditLogs.map((log: any, i: number) => (
                  <div key={i} className="p-2 rounded-lg bg-slate-50 border border-slate-150 flex items-start justify-between gap-4">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase ${log.severity === 'HIGH' ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-700'}`}>{log.severity}</span>
                        <span className="text-slate-800 font-bold">{log.event}</span>
                      </div>
                      <p className="text-slate-500 font-sans leading-relaxed">{log.details}</p>
                    </div>
                    <span className="text-slate-400 shrink-0 text-[8px]">{log.timestamp}</span>
                  </div>
                ))
              }
            </div>
          </div>

          <h3 className="text-xs font-extrabold text-slate-400 uppercase tracking-wider pt-2">
            {locale === 'zh' ? '三大工作台元数据同步列表' : 'Metadata Sync List'} ({assets.length})
          </h3>

          <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-200">
                  <th className="p-3">{locale === 'zh' ? '资产名称' : 'Asset'}</th>
                  <th className="p-3">{locale === 'zh' ? '来源' : 'Source'}</th>
                  <th className="p-3">{locale === 'zh' ? '类型' : 'Type'}</th>
                  <th className="p-3">{locale === 'zh' ? '体量' : 'Size'}</th>
                  <th className="p-3">{locale === 'zh' ? '切块' : 'Chunks'}</th>
                  <th className="p-3 text-right">{locale === 'zh' ? '状态/操作' : 'Status'}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-150">
                {assets.map(asset => (
                  <tr key={asset.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="p-3 font-bold text-slate-800">{asset.name}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded-full font-bold text-[9px] ${
                        asset.source === 'integration' ? 'bg-amber-50 text-amber-600 border border-amber-200' :
                        asset.source === 'ontology' ? 'bg-blue-50 text-blue-600 border border-blue-200' :
                        'bg-rose-50 text-rose-600 border border-rose-200'
                      }`}>
                        {asset.source === 'integration' ? (locale === 'zh' ? '集成' : 'Integration') :
                         asset.source === 'ontology' ? (locale === 'zh' ? '本体' : 'Ontology') :
                         (locale === 'zh' ? '安全' : 'Security')}
                      </span>
                    </td>
                    <td className="p-3 text-slate-500 font-medium">{asset.type}</td>
                    <td className="p-3 text-slate-500 font-mono text-[10px]">{asset.recordsOrFields}</td>
                    <td className="p-3 text-slate-600 font-mono font-bold">{asset.chunksCount > 0 ? `${asset.chunksCount} chunks` : '-'}</td>
                    <td className="p-3 text-right">
                      {asset.syncStatus === 'synced' ? (
                        <div className="flex items-center justify-end gap-1.5">
                          <span className="text-emerald-600 font-bold text-[10px] flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />{locale === 'zh' ? '已对齐' : 'Synced'}</span>
                          <button onClick={() => handleSyncAsset(asset.id)} className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer"><RotateCw size={10} /></button>
                        </div>
                      ) : asset.syncStatus === 'out_of_date' ? (
                        <button onClick={() => handleSyncAsset(asset.id)} className="px-2 py-1 bg-amber-500 hover:bg-amber-600 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto">
                          <AlertCircle size={9} />{locale === 'zh' ? '更新' : 'Update'}
                        </button>
                      ) : (
                        <button onClick={() => handleSyncAsset(asset.id)} className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto">
                          <Download size={9} />{locale === 'zh' ? '拉取' : 'Sync'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="bg-slate-900 rounded-xl p-4 flex flex-col h-[400px] shadow-md border border-slate-800 text-slate-300">
          <div className="border-b border-slate-800 pb-2.5 mb-3 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-ping" />
              <span className="font-mono text-white text-[10px] font-bold">Closed-Loop Listener Pipeline</span>
            </div>
            <span className="text-[8px] text-slate-500 font-mono">STATUS: STABLE</span>
          </div>
          <div className="flex-1 overflow-y-auto space-y-2.5 font-mono text-[9px] leading-relaxed scrollbar-thin scrollbar-thumb-slate-800">
            {syncLogs.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-slate-500 text-center space-y-1.5">
                <Terminal size={24} className="text-slate-600" />
                <p>{locale === 'zh' ? '等待联邦同步事件触发...' : 'Waiting for sync events...'}</p>
              </div>
            ) : syncLogs.map((log, idx) => (
              <p key={idx} className={`${log.includes('✅') ? 'text-emerald-400 font-bold' : log.includes('❌') ? 'text-rose-400 font-bold' : 'text-slate-300'}`}>{log}</p>
            ))}
          </div>
          <div className="border-t border-slate-800 pt-3 mt-3 text-[9px] text-slate-500 font-mono flex justify-between items-center">
            <span>Doris CB Optimizer Sync Grid</span><span>v2.0-Sovereign</span>
          </div>
        </div>
      </div>
    </div>
  );
}
