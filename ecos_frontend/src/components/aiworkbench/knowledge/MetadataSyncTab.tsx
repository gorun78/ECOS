/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ThemeStyles } from '../../../components/ThemeContext';
import * as Icons from 'lucide-react';
import type { MetadataAsset } from './types';
import Pagination from '../../common/Pagination';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface MetadataSyncTabProps {
  styles: ThemeStyles;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  assets: MetadataAsset[];
  handleSyncAll: () => Promise<void>;
  isSyncingAll: boolean;
  handleToggleSimulation: (type: 'drift' | 'sla' | 'reset') => Promise<void>;
  isSchemaDrift: boolean;
  isSlaBreach: boolean;
  auditLogs: any[];
  syncLogs: string[];
  handleSyncAsset: (id: string) => void;
}

export default function MetadataSyncTab({
  styles,
  showToast,
  assets,
  handleSyncAll,
  isSyncingAll,
  handleToggleSimulation,
  isSchemaDrift,
  isSlaBreach,
  auditLogs,
  syncLogs,
  handleSyncAsset,
}: MetadataSyncTabProps) {
  const { t } = useLanguage();
  const [assetPage, setAssetPage] = useState(1);
  const [assetPageSize] = useState(10);
  const [assetSortBy, setAssetSortBy] = useState<string>("name");
  const [assetSortOrder, setAssetSortOrder] = useState<"asc" | "desc">("asc");
  return (
    <div className="space-y-6">
      
      {/* Title */}
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText}`}>{t('aiworkbench.knowledge.sync.title')} (Metadata Sync)</h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.sync.subtitle')}</p>
        </div>

        <button
          onClick={handleSyncAll}
          disabled={isSyncingAll}
          className={`px-4 py-2 ${styles.cardBg} hover:styles.sidebarActiveBg text-white font-bold rounded-lg transition-all flex items-center gap-1.5 shadow-sm cursor-pointer ${
            isSyncingAll ? 'opacity-70 cursor-not-allowed' : ''
          }`}
        >
          {isSyncingAll ? (
            <>
              <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
              <span>{t('aiworkbench.knowledge.sync.syncing')}</span>
            </>
          ) : (
            <>
              <Icon name="RefreshCw" size={12} />
              <span>{t('aiworkbench.knowledge.sync.syncAllBtn')} (Federated Sync)</span>
            </>
          )}
        </button>
      </div>

      {/* Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Sync list */}
        <div className="lg:col-span-2 space-y-4">
          
          {/* Real-time Exception & Drift Simulation Center */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5 text-rose-600`}>
                <Icon name="ShieldAlert" size={13} />
                <span>{t('aiworkbench.knowledge.sync.simulationTitle')} (Exception Lab)</span>
              </h3>
              <div className="flex gap-1.5">
                <span className={`px-2 py-0.5 rounded-full text-[9px] font-extrabold border ${isSchemaDrift || isSlaBreach ? 'bg-rose-50 border-rose-200 text-rose-600 animate-pulse' : 'styles.inputBg styles.cardBorder styles.cardTextMuted'}`}>
                  {isSchemaDrift || isSlaBreach ? '\u25CF {t('aiworkbench.knowledge.sync.simulationActive')}' : '\u25CF {t('aiworkbench.knowledge.sync.simulationStable')}'}
                </span>
              </div>
            </div>

            <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans`}>
              {t('aiworkbench.knowledge.sync.simulationDesc')}
            </p>

            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => handleToggleSimulation('drift')}
                className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${
                  isSchemaDrift 
                    ? 'bg-rose-50 border-rose-300 text-rose-700 font-extrabold' 
                    : 'styles.cardBg styles.cardBorder styles.cardText hover:styles.inputBg'
                }`}
              >
                <Icon name="AlertTriangle" size={11} className={isSchemaDrift ? 'animate-bounce' : ''} />
                <span>{t('aiworkbench.knowledge.sync.injectDrift')}</span>
              </button>

              <button
                onClick={() => handleToggleSimulation('sla')}
                className={`px-3 py-1.5 rounded-lg text-[10px] font-bold cursor-pointer transition-all flex items-center gap-1.5 border ${
                  isSlaBreach 
                    ? 'bg-amber-50 border-amber-300 text-amber-700 font-extrabold' 
                    : 'styles.cardBg styles.cardBorder styles.cardText hover:styles.inputBg'
                }`}
              >
                <Icon name="Clock" size={11} className={isSlaBreach ? 'animate-pulse' : ''} />
                <span>{t('aiworkbench.knowledge.sync.injectSla')}</span>
              </button>

              <button
                onClick={() => handleToggleSimulation('reset')}
                className={`px-3 py-1.5 rounded-lg text-[10px] font-bold ${styles.appBg} hover:styles.inputBg ${styles.cardTextMuted} cursor-pointer transition-all flex items-center gap-1 border styles.cardBorder ml-auto`}
              >
                <Icon name="Check" size={11} />
                <span>{t('aiworkbench.knowledge.sync.resetSim')} (Reset)</span>
              </button>
            </div>
          </div>

          {/* Real-time Audit Logs fetched from backend */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-2.5`}>
            <h4 className={`font-extrabold ${styles.cardText} text-[11px] flex items-center gap-1 ${styles.cardTextMuted} border-b ${styles.cardBorder} pb-2`}>
              <Icon name="FileClock" size={12} />
              <span>{t('aiworkbench.knowledge.sync.auditTitle')} ({auditLogs.length})</span>
            </h4>
            
            <div className="space-y-1.5 max-h-36 overflow-y-auto font-mono text-[9px]">
              {auditLogs.length === 0 ? (
                <p className={`${styles.cardTextMuted} py-4 text-center`}>{t('aiworkbench.knowledge.sync.noAnomaly')}</p>
              ) : (
                auditLogs.map((log: any, i: number) => (
                  <div key={i} className={`p-2 rounded-lg ${styles.inputBg} border ${styles.cardBorder} flex items-start justify-between gap-4`}>
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase ${
                          log.severity === 'HIGH' ? 'bg-rose-100 text-rose-700' :
                          log.severity === 'MEDIUM' ? 'bg-amber-100 text-amber-700' : 'styles.appBg styles.cardText'
                        }`}>
                          {log.severity}
                        </span>
                        <span className={`${styles.cardText} font-bold`}>{log.event}</span>
                      </div>
                      <p className={`${styles.cardTextMuted} font-sans leading-relaxed`}>{log.details}</p>
                    </div>
                    <span className={`${styles.cardTextMuted} shrink-0 text-[8px]`}>{log.timestamp}</span>
                  </div>
                ,)}
              )}
            </div>
          </div>

          <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider pt-2`}>
            {t('aiworkbench.knowledge.sync.assetsTitle')} ({assets.length})
          </h3>

          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className={`${styles.inputBg} ${styles.cardTextMuted} text-[10px] font-extrabold uppercase tracking-wider border-b ${styles.cardBorder}`}>
                  <th className="p-3 cursor-pointer hover:opacity-80 select-none" onClick={() => { if (assetSortBy === 'name') setAssetSortOrder(o => o === 'asc' ? 'desc' : 'asc'); else { setAssetSortBy('name'); setAssetSortOrder('asc'); } }}>
                    {t('aiworkbench.knowledge.sync.colName')} {assetSortBy === 'name' ? (assetSortOrder === 'asc' ? '\u25B2' : '\u25BC') : ''}
                  </th>
                  <th className="p-3 cursor-pointer hover:opacity-80 select-none" onClick={() => { if (assetSortBy === 'source') setAssetSortOrder(o => o === 'asc' ? 'desc' : 'asc'); else { setAssetSortBy('source'); setAssetSortOrder('asc'); } }}>
                    {t('aiworkbench.knowledge.sync.colSource')} {assetSortBy === 'source' ? (assetSortOrder === 'asc' ? '\u25B2' : '\u25BC') : ''}
                  </th>
                  <th className="p-3 cursor-pointer hover:opacity-80 select-none" onClick={() => { if (assetSortBy === 'type') setAssetSortOrder(o => o === 'asc' ? 'desc' : 'asc'); else { setAssetSortBy('type'); setAssetSortOrder('asc'); } }}>
                    {t('aiworkbench.knowledge.sync.colType')} {assetSortBy === 'type' ? (assetSortOrder === 'asc' ? '\u25B2' : '\u25BC') : ''}
                  </th>
                  <th className="p-3">{t('aiworkbench.knowledge.sync.colSize')}</th>
                  <th className="p-3">{t('aiworkbench.knowledge.sync.colChunks')}</th>
                  <th className="p-3 text-right">{t('aiworkbench.knowledge.sync.colStatus')} / {t('aiworkbench.knowledge.sync.colActions')}</th>
                </tr>
              </thead>
              <tbody className={`divide-y ${styles.cardBorder}`}>
                {(() => {
                  const sorted = [...assets].sort((a: any, b: any) => {
                    const aVal = String(a[assetSortBy] ?? "");
                    const bVal = String(b[assetSortBy] ?? "");
                    return assetSortOrder === "asc" ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
                  });
                  return sorted.slice((assetPage - 1) * assetPageSize, assetPage * assetPageSize).map(asset => {
                  return (
                    <tr key={asset.id} className={`hover:${styles.appBg} transition-colors`}>
                      <td className={`p-3 font-bold ${styles.cardText}`}>
                        {asset.name}
                      </td>
                      <td className="p-3">
                        <span className={`px-2 py-0.5 rounded-full font-bold text-[9px] ${
                          asset.source === 'integration' ? 'bg-amber-50 text-amber-600 border border-amber-200' :
                          asset.source === 'ontology' ? 'bg-blue-50 text-blue-600 border border-blue-200' :
                          'bg-rose-50 text-rose-600 border border-rose-200'
                        }`}>
                          {asset.source === 'integration' ? t('aiworkbench.knowledge.sync.sourceIntegration') :
                           asset.source === 'ontology' ? t('aiworkbench.knowledge.sync.sourceOntology') :
                           t('aiworkbench.knowledge.sync.sourceSecurity')}
                        </span>
                      </td>
                      <td className={`p-3 ${styles.cardTextMuted} font-medium`}>
                        {asset.type}
                      </td>
                      <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>
                        {asset.recordsOrFields}
                      </td>
                      <td className={`p-3 ${styles.cardTextMuted} font-mono font-bold`}>
                        {asset.chunksCount > 0 ? `${asset.chunksCount} chunks` : '-'}
                      </td>
                      <td className="p-3 text-right">
                        {asset.syncStatus === 'synced' ? (
                          <div className="flex items-center justify-end gap-1.5">
                            <span className="text-emerald-600 font-bold text-[10px] flex items-center gap-1">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                              <span>{t('aiworkbench.knowledge.sync.statusSynced')}</span>
                            </span>
                            <button
                              onClick={() => handleSyncAsset(asset.id)}
                              className={`p-1 ${styles.cardTextMuted} hover:${styles.cardTextMuted} cursor-pointer`}
                              title={t('aiworkbench.knowledge.sync.actionResync')}
                            >
                              <Icon name="RotateCw" size={10} />
                            </button>
                          </div>
                        ) : asset.syncStatus === 'out_of_date' ? (
                          <button
                            onClick={() => handleSyncAsset(asset.id)}
                            className="px-2 py-1 bg-amber-500 hover:bg-amber-600 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto"
                          >
                            <Icon name="AlertCircle" size={9} />
                            <span>{t('aiworkbench.knowledge.sync.statusOutdated')}</span>
                          </button>
                        ) : (
                          <button
                            onClick={() => handleSyncAsset(asset.id)}
                            className="px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-md transition-colors cursor-pointer text-[9px] flex items-center gap-1 ml-auto"
                          >
                            <Icon name="Download" size={9} />
                            <span>{t('aiworkbench.knowledge.sync.actionPull')}</span>
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              })})()}
              </tbody>
            </table>
          </div>
          {assets.length > assetPageSize && (
            <Pagination
              page={assetPage}
              pageSize={assetPageSize}
              total={assets.length}
              onPageChange={setAssetPage}
            />
          )}
        </div>

        {/* Sync Real-time Console */}
        <div className={`${styles.appBg} rounded-xl p-4 flex flex-col h-[400px] shadow-md border ${styles.cardBorder} ${styles.cardTextMuted}`}>
          <div className={`border-b ${styles.cardBorder} pb-2.5 mb-3 flex items-center justify-between`}>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-ping" />
              <span className="font-mono text-white text-[10px] font-bold">Closed-Loop Listener Pipeline</span>
            </div>
            <span className={`text-[8px] ${styles.cardTextMuted} font-mono`}>STATUS: STABLE</span>
          </div>

          <div className="flex-1 overflow-y-auto space-y-2.5 font-mono text-[9px] leading-relaxed scrollbar-thin scrollbar-thumb-slate-800">
            {syncLogs.length === 0 ? (
              <div className={`h-full flex flex-col items-center justify-center ${styles.cardTextMuted} text-center space-y-1.5`}>
                <Icon name="Terminal" size={24} className={`${styles.cardTextMuted}`} />
                <p>{t('aiworkbench.knowledge.sync.idleMessage').split('...')[0] + '...'}</p>
                <p className={`text-[8px] ${styles.cardTextMuted}`}>t('aiworkbench.knowledge.sync.idleMessage').includes('点击') ? t('aiworkbench.knowledge.sync.idleMessage').split('点击')[1] : ''</p>
              </div>
            ) : (
              syncLogs.map((log, idx) => (
                <p key={idx} className={`${log.includes('\u2705') ? 'text-emerald-400 font-bold' : log.includes('\uD83E\uDD16') ? 'text-blue-400 font-bold' : 'styles.cardTextMuted'}`}>
                  {log}
                </p>
              ,)}
            )}
          </div>

          <div className={`border-t ${styles.cardBorder} pt-3 mt-3 text-[9px] ${styles.cardTextMuted} font-mono flex justify-between items-center`}>
            <span>Doris CB Optimizer Sync Grid</span>
            <span>v2.0-Sovereign</span>
          </div>
        </div>

      </div>

    </div>
  );
}