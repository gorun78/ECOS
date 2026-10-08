/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Right-hand detail slide-over (properties / relations / activity tabs) of
// BusinessObjectExplorer. Extracted verbatim by H6-T4.

import React from 'react';
import { ActionType, ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { ResolvedRelation } from './businessObjectExplorerTypes';

interface ExplorerDetailPanelProps {
  activeObjectType: ObjectType;
  selectedInstance: any;
  onClose: () => void;
  availableActions: ActionType[];
  onOpenAction: (action: ActionType) => void;
  detailTab: 'properties' | 'relations' | 'activity';
  onDetailTabChange: (tab: 'properties' | 'relations' | 'activity') => void;
  resolvedRelations: ResolvedRelation[];
  onJumpToInstance: (otId: string, instId: string) => void;
}

export function ExplorerDetailPanel({
  activeObjectType,
  selectedInstance,
  onClose,
  availableActions,
  onOpenAction,
  detailTab,
  onDetailTabChange,
  resolvedRelations,
  onJumpToInstance,
}: ExplorerDetailPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`w-96 border-l ${styles.appBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden relative`}>

      {/* Detailed Panel Header */}
      <div className={`p-4 border-b ${styles.appBorder} ${styles.appBg} flex flex-col gap-3`}>
        <div className="flex justify-between items-start">
          <div className="flex items-center gap-2">
            <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
              <LucideIcon name={activeObjectType.icon} size={14} />
            </span>
            <div>
              <div className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{t('ow.bod.detail', { name: activeObjectType.displayName })}</div>
              <h3 className={`text-xs font-bold font-mono ${styles.text} mt-0.5`}>
                {selectedInstance[activeObjectType.titleProperty]}
              </h3>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className={`p-1 rounded hover:bg-blue-50/20 ${styles.cardTextMuted} opacity-80 hover:opacity-100 transition-opacity`}
          >
            <LucideIcon name="X" size={14} />
          </button>
        </div>

        {/* Action Execution Button Dropdown */}
        {availableActions.length > 0 && (
          <div className="pt-1.5">
            <div className={`text-[10px] ${styles.cardTextMuted} uppercase tracking-wider font-semibold mb-1 flex items-center gap-1`}>
              <LucideIcon name="Terminal" size={10} />
              <span>{t('ow.bod.actionsLabel')}</span>
            </div>
            <div className="flex flex-col gap-1">
              {availableActions.map(act => (
                <button
                  type="button"
                  key={act.id}
                  onClick={() => onOpenAction(act)}
                  className="w-full h-8 px-2.5 rounded border border-amber-200 bg-amber-50/40 hover:bg-amber-50 text-amber-800 text-[10px] font-semibold flex items-center justify-between transition-all"
                >
                  <div className="flex items-center gap-1.5">
                    <LucideIcon name="Zap" size={12} className="fill-amber-400/20 text-amber-600" />
                    <span>{t('ow.bod.trigger', { name: act.displayName })}</span>
                  </div>
                  <LucideIcon name="ChevronRight" size={10} className="text-amber-500" />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Panel Tab switch */}
      <div className={`flex border-b ${styles.divider} px-2 text-[11px] font-medium ${styles.appBg}`}>
        <button
          type="button"
          onClick={() => onDetailTabChange('properties')}
          className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
            detailTab === 'properties' ? 'border-blue-600 text-blue-700 font-bold' : `border-transparent ${styles.cardTextMuted} opacity-80 hover:opacity-100`
          }`}
        >
          {t('ow.bod.tabProperties')}
        </button>
        <button
          type="button"
          onClick={() => onDetailTabChange('relations')}
          className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all flex items-center justify-center gap-1 ${
            detailTab === 'relations' ? 'border-blue-600 text-blue-700 font-bold' : `border-transparent ${styles.cardTextMuted} opacity-80 hover:opacity-100`
          }`}
        >
          {t('ow.bod.tabRelations', { n: resolvedRelations.reduce((acc, curr) => acc + curr.instances.length, 0) })}
        </button>
        <button
          type="button"
          onClick={() => onDetailTabChange('activity')}
          className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
            detailTab === 'activity' ? 'border-blue-600 text-blue-700 font-bold' : `border-transparent ${styles.cardTextMuted} opacity-80 hover:opacity-100`
          }`}
        >
          {t('ow.bod.tabActivity')}
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
                <div key={p.id} className={`p-2.5 rounded-lg border ${styles.divider} hover:${styles.appBorder} hover:${styles.appBg} transition-colors`}>
                  <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted} font-mono`}>
                    <span className={`font-semibold ${styles.cardTextMuted}`}>{p.displayName}</span>
                    <span className="uppercase">{p.dataType}</span>
                  </div>
                  <div className={`mt-1 font-mono text-xs font-semibold ${styles.text} flex items-center justify-between`}>
                    {isPk ? (
                      <span className={`${styles.sidebarBg} ${styles.sidebarText} rounded px-1.5 py-0.5 text-[10px]`}>
                        {String(val ?? t('ow.bod.valueUnset'))}
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
                      <span>{String(val ?? t('ow.bod.valueNull'))}</span>
                    )}

                    {isPk && (
                      <span className="text-[9px] font-semibold text-red-500 bg-red-50 border border-red-100 px-1 rounded uppercase">Primary Key</span>
                    )}
                  </div>
                  <p className={`text-[10px] ${styles.cardTextMuted} mt-1 leading-relaxed`}>{p.description}</p>
                </div>
              );
            })}
          </div>
        )}

        {/* tab 2: Relations / Connection Traversal */}
        {detailTab === 'relations' && (
          <div className="space-y-5">
            <div className={`text-[10px] ${styles.cardTextMuted} font-semibold uppercase leading-relaxed`}>
              {t('ow.bod.relationsHeader')}
            </div>

            {resolvedRelations.length === 0 ? (
              <div className={`text-center py-10 border border-dashed ${styles.sidebarBorder} rounded-lg ${styles.cardTextMuted} text-[10px]`}>
                {t('ow.bod.relationsEmpty')}
              </div>
            ) : (
              <div className="space-y-4">
                {resolvedRelations.map(rel => (
                  <div key={rel.linkType.id} className={`space-y-2 border ${styles.appBorder}/60 rounded-lg p-3 ${styles.appBg}`}>
                    {/* Header */}
                    <div className={`flex items-center justify-between text-[11px] pb-1.5 border-b ${styles.divider}`}>
                      <div className={`flex items-center gap-1.5 font-semibold ${styles.cardText}`}>
                        <LucideIcon name="GitMerge" size={12} className="text-emerald-600" />
                        <span>{rel.linkType.displayName}</span>
                      </div>
                      <span className="text-[10px] bg-emerald-100 text-emerald-700 px-1 py-0.2 rounded font-mono font-bold uppercase">
                        {rel.linkType.cardinality}
                      </span>
                    </div>

                    <p className={`text-[10px] ${styles.cardTextMuted}`}>{rel.linkType.description}</p>

                    {/* List matching connected instances */}
                    {rel.instances.length === 0 ? (
                      <div className={`text-[10px] ${styles.cardTextMuted} italic ${styles.appBg} p-2 rounded text-center`}>
                        {t('ow.bod.noLinkFound', { name: rel.otherObjectType.displayName })}
                      </div>
                    ) : (
                      <div className="space-y-1 pt-1">
                        {rel.instances.map(inst => (
                          <div
                            key={inst[rel.otherObjectType.primaryKey]}
                            onClick={() => onJumpToInstance(rel.otherObjectType.id, inst[rel.otherObjectType.primaryKey])}
                            className={`p-2 border ${styles.appBorder} hover:border-blue-400 ${styles.cardBg} hover:bg-blue-50/10 rounded-md cursor-pointer flex justify-between items-center transition-all group`}
                          >
                            <div className="flex items-center gap-2 truncate">
                              <span className={`p-1 rounded ${rel.otherObjectType.color}`}>
                                <LucideIcon name={rel.otherObjectType.icon} size={11} />
                              </span>
                              <span className={`font-mono text-xs font-semibold ${styles.text}`}>
                                {inst[rel.otherObjectType.primaryKey]}
                              </span>
                              <span className={`text-[10px] ${styles.cardTextMuted} truncate max-w-[100px]`}>
                                ({inst[rel.otherObjectType.titleProperty]})
                              </span>
                            </div>
                            <LucideIcon name="Compass" size={11} className={`${styles.cardTextMuted} group-hover:text-blue-600 transition-colors`} />
                          </div>
                        ))}
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
            <div className={`relative border-l ${styles.appBorder} pl-4 ml-2 space-y-5 py-2`}>
              <div className="relative text-[11px]">
                <span className={`absolute -left-6 top-1 w-3 h-3 rounded-full bg-blue-500 border-2 ${styles.cardBg}`} />
                <div className={`font-semibold ${styles.cardText}`}>{t('ow.bod.actLoaded')}</div>
                <p className={`${styles.cardTextMuted} text-[10px] mt-0.5`}>{t('ow.bod.actLoadedDesc')}</p>
                <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>2026-07-02 20:34</span>
              </div>

              {selectedInstance.status === 'MAINTENANCE' && (
                <div className="relative text-[11px]">
                  <span className={`absolute -left-6 top-1 w-3 h-3 rounded-full bg-amber-500 border-2 ${styles.cardBg}`} />
                  <div className={`font-semibold ${styles.cardText}`}>{t('ow.bod.actMaintenance')}</div>
                  <p className={`${styles.cardTextMuted} text-[10px] mt-0.5`}>{t('ow.bod.actMaintenanceDesc')}</p>
                  <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>{t('ow.bod.justNow')}</span>
                </div>
              )}

              {selectedInstance.status === 'DELAYED' && (
                <div className="relative text-[11px]">
                  <span className={`absolute -left-6 top-1 w-3 h-3 rounded-full bg-red-400 border-2 ${styles.cardBg}`} />
                  <div className={`font-semibold ${styles.cardText}`}>{t('ow.bod.actDelayed')}</div>
                  <p className={`${styles.cardTextMuted} text-[10px] mt-0.5`}>{t('ow.bod.actDelayedDesc')}</p>
                  <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>{t('ow.bod.justNow')}</span>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
