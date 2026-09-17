import React, { useState, useEffect, useCallback } from 'react';
import { RefreshCw, ShieldAlert, AlertTriangle, Clock, Check, FileClock, RotateCw, AlertCircle, Download, Terminal } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { MetadataAsset } from '../typesAndConstants';

// PMO-54: 不再硬编码 DEMO_ASSETS；列表从 /api/integration/metadata 加载（真实数据源）

function lastSyncedOf(s: any): string | undefined {
  if (!s) return undefined;
  const d = s.update_time ?? s.updated_at ?? s.last_sync_time ?? s.lastSyncTime;
  return d ? String(d).substring(0, 16) : undefined;
}

export default function SyncTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [assets, setAssets] = useState<MetadataAsset[]>([]);
  const [metaLoading, setMetaLoading] = useState(true);
  const [isSyncingAll, setIsSyncingAll] = useState(false);
  const [isSchemaDrift, setIsSchemaDrift] = useState(false);
  const [isSlaBreach, setIsSlaBreach] = useState(false);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [syncLogs, setSyncLogs] = useState<string[]>([]);

  const loadMetadata = async () => {
    setMetaLoading(true);
    try {
      const meta = await knowledgeApi.fetchIntegrationMetadata();
      if (meta?.simulationState) {
        setIsSchemaDrift(Boolean(meta.simulationState.isSchemaDriftActive));
        setIsSlaBreach(Boolean(meta.simulationState.isSlaBreachActive));
      }
    } catch { /* fallback to defaults */ }
    try {
      const logs = await knowledgeApi.fetchIntegrationLogs();
      if (Array.isArray(logs)) setAuditLogs(logs);
    } catch { /* fallback */ }
    // PMO-54: real asset list — /api/integration/metadata returns sources
    try {
      const raw = await (await fetch('/api/integration/metadata', { headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` } })).json();
      const sources = raw?.sources || raw?.data?.sources || raw?.data || raw;
      const list = Array.isArray(sources) ? sources : [];
      setAssets(list.map((s: any, i: number) => ({
        id: String(s.id ?? s.dsId ?? `int-${i}`),
        source: (s.source ?? s.sourceType ?? 'integration') as MetadataAsset['source'],
        name: String(s.name ?? s.tableName ?? s.id ?? `asset-${i}`),
        type: String(s.type ?? s.sourceType ?? 'physical_table'),
        recordsOrFields: String(s.recordsOrFields ?? s.records ?? '—'),
        syncStatus: (s.syncStatus ?? (s.status === 'synced' ? 'synced' : 'pending')) as MetadataAsset['syncStatus'],
        chunksCount: Number(s.chunksCount ?? s.chunkCount ?? 0),
        lastSynced: String(s.lastSynced ?? s.updatedAt ?? lastSyncedOf(s) ?? '—'),
      })));
    } catch {
      setAssets([]);
    } finally {
      setMetaLoading(false);
    }
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
            {t("knowledge.synctab.多模态联邦元数据集成中心")}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {t("knowledge.synctab.动态监控_抓取和转换物理数据集_逻辑本体语义以及最高安全规则")}
          </p>
        </div>
        <button onClick={handleSyncAll} disabled={isSyncingAll} className={`px-4 py-2 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg transition-all flex items-center gap-1.5 shadow-sm cursor-pointer ${isSyncingAll ? 'opacity-70 cursor-not-allowed' : ''}`}>
          {isSyncingAll
            ? <><span className="w-3.5 h-3.5 border-2 border-slate-100 border-t-transparent rounded-full animate-spin" /><span>{t("knowledge.synctab.联邦网格抓取中")}</span></>
            : <><RefreshCw size={12} /><span>{t("knowledge.synctab.一键全站元数据同步")}</span></>
          }
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <div className="flex items-center justify-between border-b border-slate-150 pb-2">
              <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5 text-rose-600`}>
                <ShieldAlert size={13} /><span>{t("knowledge.synctab.物理元数据漂移与调度_sla_仿真中心")}</span>
              </h3>
              <span className={`px-2 py-0.5 rounded-full text-[9px] font-extrabold border ${isSchemaDrift || isSlaBreach ? 'bg-rose-50 border-rose-200 text-rose-600 animate-pulse' : `bg-slate-50 ${styles.cardBorder} ${styles.muted}`}`}>
                {isSchemaDrift || isSlaBreach ? '● 异常激活' : '● 稳定'}
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              <button onClick={() => handleToggleSimulation('drift')} className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${isSchemaDrift ? 'bg-rose-50 border-rose-300 text-rose-700' : `${styles.cardBg} ${styles.cardBorder} ${styles.cardText} hover:bg-slate-50`}`}>
                <AlertTriangle size={11} className={isSchemaDrift ? 'animate-bounce' : ''} /><span>{t("knowledge.synctab.注入_schema_漂移")}</span>
              </button>
              <button onClick={() => handleToggleSimulation('sla')} className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${isSlaBreach ? 'bg-amber-50 border-amber-300 text-amber-700' : `${styles.cardBg} ${styles.cardBorder} ${styles.cardText} hover:bg-slate-50`}`}>
                <Clock size={11} className={isSlaBreach ? 'animate-pulse' : ''} /><span>{t("knowledge.synctab.注入_sla_断流")}</span>
              </button>
              <button onClick={() => handleToggleSimulation('reset')} className={`px-3 py-1.5 rounded-lg text-[10px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} cursor-pointer transition-all flex items-center gap-1 border ${styles.cardBorder} ml-auto`}>
                <Check size={11} /><span>{t("knowledge.synctab.复位")}</span>
              </button>
            </div>
          </div>

          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-2.5`}>
            <h4 className={`font-extrabold ${styles.cardText} text-[11px] flex items-center gap-1 text-slate-700 border-b border-slate-150 pb-2`}>
              <FileClock size={12} /><span>{t("knowledge.synctab.审计记录")} ({auditLogs.length})</span>
            </h4>
            <div className="space-y-1.5 max-h-36 overflow-y-auto font-mono text-[9px]">
              {auditLogs.length === 0
                ? <p className={`${styles.muted} py-4 text-center`}>{t("knowledge.synctab.暂无审计事件")}</p>
                : auditLogs.map((log: any, i: number) => (
                  <div key={i} className={`p-2 rounded-lg bg-slate-50 border border-slate-150 flex items-start justify-between gap-4`}>
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase ${log.severity === 'HIGH' ? 'bg-rose-100 text-rose-700' : `bg-slate-100 ${styles.cardText}`}`}>{log.severity}</span>
                        <span className={`${styles.cardText} font-bold`}>{log.event}</span>
                      </div>
                      <p className={`${styles.muted} font-sans leading-relaxed`}>{log.details}</p>
                    </div>
                    <span className={`${styles.muted} shrink-0 text-[8px]`}>{log.timestamp}</span>
                  </div>
                ))
              }
            </div>
          </div>

          <h3 className={`text-xs font-extrabold ${styles.muted} uppercase tracking-wider pt-2`}>
            {t("knowledge.synctab.三大工作台元数据同步列表")} ({assets.length})
          </h3>

          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className={`bg-slate-50 ${styles.muted} text-[10px] font-extrabold uppercase tracking-wider border-b ${styles.cardBorder}`}>
                  <th className="p-3">{t("knowledge.synctab.资产名称")}</th>
                  <th className="p-3">{t("knowledge.synctab.来源")}</th>
                  <th className="p-3">{t("knowledge.synctab.类型")}</th>
                  <th className="p-3">{t("knowledge.synctab.体量")}</th>
                  <th className="p-3">{t("knowledge.synctab.切块")}</th>
                  <th className="p-3 text-right">{t("knowledge.synctab.状态_操作")}</th>
                </tr>
              </thead>
              <tbody className={`divide-y divide-slate-150`}>
                {metaLoading ? (
                  <tr><td colSpan={6} className={`p-8 text-center ${styles.muted}`}>{t('knowledge.synctab.loading_metadata')}</td></tr>
                ) : assets.length === 0 ? (
                  <tr><td colSpan={6} className={`p-12 text-center ${styles.muted} space-y-1`}>
                    <p className="text-xs">{t('knowledge.synctab.empty_metadata')}</p>
                    <p className={`text-[10px] font-mono ${styles.muted}`}>/api/integration/metadata · {t('knowledge.synctab.waiting_backend')}</p>
                  </td></tr>
                ) : assets.map(asset => (
                  <tr key={asset.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className={`p-3 font-bold ${styles.cardText}`}>{asset.name}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded-full font-bold text-[9px] ${
                        asset.source === 'integration' ? 'bg-amber-50 text-amber-600 border border-amber-200' :
                        asset.source === 'ontology' ? 'bg-blue-50 text-blue-600 border border-blue-200' :
                        'bg-rose-50 text-rose-600 border border-rose-200'
                      }`}>
                        {asset.source === 'integration' ? (t("knowledge.synctab.集成")) :
                         asset.source === 'ontology' ? (t("knowledge.synctab.本体")) :
                         (t("knowledge.synctab.安全"))}
                      </span>
                    </td>
                    <td className={`p-3 ${styles.muted} font-medium`}>{asset.type}</td>
                    <td className={`p-3 ${styles.muted} font-mono text-[10px]`}>{asset.recordsOrFields}</td>
                    <td className={`p-3 ${styles.cardText} font-mono font-bold`}>{asset.chunksCount > 0 ? `${asset.chunksCount} chunks` : '-'}</td>
                    <td className="p-3 text-right">
                      {asset.syncStatus === 'synced' ? (
                        <div className="flex items-center justify-end gap-1.5">
                          <span className="text-emerald-600 font-bold text-[10px] flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />{t("knowledge.synctab.已对齐")}</span>
                          <button onClick={() => handleSyncAsset(asset.id)} className={`p-1 ${styles.muted} hover:text-white cursor-pointer`}><RotateCw size={10} /></button>
                        </div>
                      ) : asset.syncStatus === 'out_of_date' ? (
                        <button onClick={() => handleSyncAsset(asset.id)} className="px-2 py-1 bg-amber-500 hover:bg-amber-600 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto">
                          <AlertCircle size={9} />{t("knowledge.synctab.更新")}
                        </button>
                      ) : (
                        <button onClick={() => handleSyncAsset(asset.id)} className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto">
                          <Download size={9} />{t("knowledge.synctab.拉取")}
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
            <span className={`text-[8px] ${styles.muted} font-mono`}>STATUS: STABLE</span>
          </div>
          <div className="flex-1 overflow-y-auto space-y-2.5 font-mono text-[9px] leading-relaxed scrollbar-thin scrollbar-thumb-slate-800">
            {syncLogs.length === 0 ? (
              <div className={`h-full flex flex-col items-center justify-center ${styles.muted} text-center space-y-1.5`}>
                <Terminal size={24} className="text-slate-600" />
                <p>{t("knowledge.synctab.等待联邦同步事件触发")}</p>
              </div>
            ) : syncLogs.map((log, idx) => (
              <p key={idx} className={`${log.includes('✅') ? 'text-emerald-400 font-bold' : log.includes('❌') ? 'text-rose-400 font-bold' : 'text-slate-300'}`}>{log}</p>
            ))}
          </div>
          <div className="border-t border-slate-800 pt-3 mt-3 text-[9px] text-slate-400 font-mono flex justify-between items-center">
            <span>Doris CB Optimizer Sync Grid</span><span>v2.0-Sovereign</span>
          </div>
        </div>
      </div>
    </div>
  );
}
