/**
 * InstanceDetailPanel — 右侧实例详情面板（属性 / 关系遍历 / 活动时间线 + 绑定动作入口）
 * Extracted verbatim from ObjectExplorerView.tsx (pure structural refactor)
 * @license Apache-2.0
 */
import React from 'react';
import { ChevronRight, Compass, GitMerge, Terminal, X, Zap } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { ActionType, ObjectType } from '../../types/ontology';
import type { ResolvedRelation } from './types';

interface Props {
  activeObjectType: ObjectType;
  selectedInstance: Record<string, unknown>;
  setSelectedInstance: (v: null) => void;
  detailTab: 'properties' | 'relations' | 'activity';
  setDetailTab: (tab: 'properties' | 'relations' | 'activity') => void;
  availableActions: ActionType[];
  handleOpenActionModal: (action: ActionType) => void;
  resolvedRelations: ResolvedRelation[];
  handleJumpToInstance: (otId: string, instId: string) => void;
}

export default function InstanceDetailPanel({
  activeObjectType,
  selectedInstance,
  setSelectedInstance,
  detailTab,
  setDetailTab,
  availableActions,
  handleOpenActionModal,
  resolvedRelations,
  handleJumpToInstance
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`w-96 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden relative`}>
      
      {/* Detailed Panel Header */}
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.appBg} flex flex-col gap-3`}>
        <div className="flex justify-between items-start">
          <div className="flex items-center gap-2">
            <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
              <DynamicIcon name={activeObjectType.icon} size={14} />
            </span>
            <div>
              <div className={`text-[10px] ${styles.muted} font-bold uppercase tracking-wider`}>{activeObjectType.displayName} {t('ow.explore.detail')}</div>
              <h3 className={`text-xs font-bold font-mono ${styles.appText} mt-0.5`}>
                {String(selectedInstance[activeObjectType.titleProperty] ?? '')}
              </h3>
            </div>
          </div>
          <button type="button"
            onClick={() => setSelectedInstance(null)}
            className={`p-1 rounded ${styles.sidebarHoverBg} ${styles.muted} hover:opacity-70`}
          >
            <X size={14} />
          </button>
        </div>

        {/* Action Execution Button Dropdown */}
        {availableActions.length > 0 && (
          <div className="pt-1.5">
            <div className={`text-[10px] ${styles.muted} uppercase tracking-wider font-semibold mb-1 flex items-center gap-1`}>
              <Terminal size={10} />
              <span>{t('ow.explore.boundActions')}</span>
            </div>
            <div className="flex flex-col gap-1">
              {availableActions.map(act => (
                <button type="button"
                  key={act.id}
                  onClick={() => handleOpenActionModal(act)}
                  className="w-full h-8 px-2.5 rounded border border-amber-200 bg-amber-50/40 hover:bg-amber-50 text-amber-800 text-[10px] font-semibold flex items-center justify-between transition-all"
                >
                  <div className="flex items-center gap-1.5">
                    <Zap size={12} className="fill-amber-400/20 text-amber-600" />
                    <span>{t('ow.explore.trigger')}{act.displayName}</span>
                  </div>
                  <ChevronRight size={10} className="text-amber-500" />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Panel Tab switch */}
      <div className={`flex border-b ${styles.cardBorder} px-2 text-[11px] font-medium ${styles.appBg}`}>
        <button type="button"
          onClick={() => setDetailTab('properties')}
          className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
            detailTab === 'properties' ? `${styles.accentBorder} text-blue-700 font-bold` : `border-transparent ${styles.cardTextMuted} ${styles.sidebarHoverBg}`
          }`}
        >
          {t('ow.explore.tabProperties')}
        </button>
        <button type="button"
          onClick={() => setDetailTab('relations')}
          className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all flex items-center justify-center gap-1 ${
            detailTab === 'relations' ? `${styles.accentBorder} text-blue-700 font-bold` : `border-transparent ${styles.cardTextMuted} ${styles.sidebarHoverBg}`
          }`}
        >
          {t('ow.explore.tabRelations')} ({resolvedRelations.reduce((acc, curr) => acc + curr.instances.length, 0)})
        </button>
        <button type="button"
          onClick={() => setDetailTab('activity')}
          className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
            detailTab === 'activity' ? `${styles.accentBorder} text-blue-700 font-bold` : `border-transparent ${styles.cardTextMuted} ${styles.sidebarHoverBg}`
          }`}
        >
          {t('ow.explore.tabActivity')}
        </button>
      </div>

      {/* Panel tab bodies */}
      <div className="flex-1 overflow-y-auto p-4">
        
        {/* tab 1: Properties */}
        {detailTab === 'properties' && (
          <div className="space-y-4">
            {activeObjectType.properties.map(p => {
              const val = selectedInstance[p.id];
              const isPk = p.isPrimaryKey;

              return (
                <div key={p.id} className={`p-2.5 rounded-lg border ${styles.cardBorder} ${styles.sidebarHoverBg} hover:bg-blue-50/10 transition-colors`}>
                  <div className={`flex items-center justify-between text-[10px] ${styles.muted} font-mono`}>
                    <span className={`font-semibold ${styles.cardTextMuted}`}>{p.displayName}</span>
                    <span className="uppercase">{p.dataType}</span>
                  </div>
                  <div className={`mt-1 font-mono text-xs font-semibold ${styles.cardText} flex items-center justify-between`}>
                    {isPk ? (
                      <span className={`${styles.appBg} border ${styles.cardBorder} ${styles.cardText} rounded px-1.5 py-0.5 text-[10px]`}>
                        {String(val ?? t('ow.label.unspecifiedValue'))}
                      </span>
                    ) : p.id === 'status' ? (
                      <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                        val === 'ACTIVE' || val === 'ON_TIME' ? 'bg-emerald-100 text-emerald-800' :
                        val === 'MAINTENANCE' || val === 'DELAYED' ? 'bg-amber-100 text-amber-800' :
                        `${styles.appBg} ${styles.cardTextMuted}`
                      }`}>
                        {String(val ?? 'N/A')}
                      </span>
                    ) : (
                      <span>{String(val ?? t('ow.label.nullValue'))}</span>
                    )}
                    
                    {isPk && (
                      <span className="text-[9px] font-semibold text-red-500 bg-red-50 border border-red-100 px-1 rounded uppercase">Primary Key</span>
                    )}
                  </div>
                  <p className={`text-[10px] ${styles.muted} mt-1 leading-relaxed`}>{p.description}</p>
                </div>
              );
            })}
          </div>
        )}

        {/* tab 2: Relations / Connection Traversal */}
        {detailTab === 'relations' && (
          <div className="space-y-5">
            <div className={`text-[10px] ${styles.muted} font-semibold uppercase leading-relaxed`}>
              {t('ow.explore.relationTraversal')}
            </div>

            {resolvedRelations.length === 0 ? (
              <div className={`text-center py-10 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-[10px]`}>
                {t('ow.empty.noDeclaredRelations')}
              </div>
            ) : (
              <div className="space-y-4">
                {resolvedRelations.map(rel => (
                  <div key={rel.linkType.id} className={`space-y-2 border ${styles.cardBorder} rounded-lg p-3 ${styles.appBg}`}>
                    {/* Header */}
                    <div className={`flex items-center justify-between text-[11px] pb-1.5 border-b ${styles.divider}`}>
                      <div className={`flex items-center gap-1.5 font-semibold ${styles.cardText}`}>
                        <GitMerge size={12} className="text-emerald-600" />
                        <span>{rel.linkType.displayName}</span>
                      </div>
                      <span className="text-[10px] bg-emerald-100 text-emerald-700 px-1 py-0.2 rounded font-mono font-bold uppercase">
                        {rel.linkType.cardinality}
                      </span>
                    </div>
                    
                    <p className={`text-[10px] ${styles.muted}`}>{rel.linkType.description}</p>

                    {/* List matching connected instances */}
                    {rel.instances.length === 0 ? (
                      <div className={`text-[10px] ${styles.muted} italic ${styles.appBg} p-2 rounded text-center`}>
                        {t('ow.empty.noRelatedInstances').replace('{name}', rel.otherObjectType.displayName)}
                      </div>
                    ) : (
                      <div className="space-y-1 pt-1">
                        {rel.instances.map(rawInst => {
                          const inst = rawInst as Record<string, string | number | undefined>;
                          const instId = String(inst[rel.otherObjectType.primaryKey] ?? '');
                          return (
                          <div
                            key={instId}
                            onClick={() => handleJumpToInstance(rel.otherObjectType.id, instId)}
                            className={`p-2 border ${styles.cardBorder} hover:border-blue-400 ${styles.cardBg} hover:bg-blue-50/10 rounded-md cursor-pointer flex justify-between items-center transition-all group`}
                          >
                            <div className="flex items-center gap-2 truncate">
                              <span className={`p-1 rounded ${rel.otherObjectType.color}`}>
                                <DynamicIcon name={rel.otherObjectType.icon} size={11} />
                              </span>
                              <span className={`font-mono text-xs font-semibold ${styles.cardText}`}>
                                {instId}
                              </span>
                              <span className={`text-[10px] ${styles.muted} truncate max-w-[100px]`}>
                                ({inst[rel.otherObjectType.titleProperty]})
                              </span>
                            </div>
                            <Compass size={11} className={`${styles.muted} group-hover:text-blue-600 transition-colors`} />
                          </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* tab 3: Timeline Activity */}
        {detailTab === 'activity' && (
          <div className="space-y-4">
            <div className={`relative border-l ${styles.cardBorder} pl-4 ml-2 space-y-5 py-2`}>
              <div className="relative text-[11px]">
                <span className={`absolute -left-6 top-1 w-3 h-3 rounded-full bg-blue-500 border-2 border-white`} />
                <div className={`font-semibold ${styles.cardText}`}>{t('ow.explore.activityLoaded')}</div>
                <p className={`${styles.muted} text-[10px] mt-0.5`}>{t('ow.explore.activityLoadedDesc')}</p>
                <span className={`text-[9px] font-mono ${styles.muted}`}>2026-07-02 20:34</span>
              </div>
              
              {selectedInstance.status === 'MAINTENANCE' && (
                <div className="relative text-[11px]">
                  <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-amber-500 border-2 border-white" />
                  <div className={`font-semibold ${styles.cardText}`}>{t('ow.explore.activityMaintenance')}</div>
                  <p className={`${styles.muted} text-[10px] mt-0.5`}>{t('ow.explore.activityMaintenanceDesc')}</p>
                  <span className={`text-[9px] font-mono ${styles.muted}`}>{t('ow.label.justNow')}</span>
                </div>
              )}

              {selectedInstance.status === 'DELAYED' && (
                <div className="relative text-[11px]">
                  <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-red-400 border-2 border-white" />
                  <div className={`font-semibold ${styles.cardText}`}>{t('ow.explore.activityDelayed')}</div>
                  <p className={`${styles.muted} text-[10px] mt-0.5`}>{t('ow.explore.activityDelayedDesc')}</p>
                  <span className={`text-[9px] font-mono ${styles.muted}`}>{t('ow.label.justNow')}</span>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
