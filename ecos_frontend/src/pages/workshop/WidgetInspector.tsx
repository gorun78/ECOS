import React from 'react';
import { Trash2, MousePointerClick } from 'lucide-react';
import { mockActionTypes } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderWidgetInspector(vm: any) {
  const { t, styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
            <div className={`w-64 ${styles.appBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0 overflow-y-auto p-4 space-y-4 text-xs select-none`}>
              <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.ws.wi.titleProperties')}</span>

              {selectedWidgetId ? (() => {
                const w = activePage?.widgets.find((wg: any) => wg.id === selectedWidgetId);
                if (!w) return <div className={`${styles.cardTextMuted} py-6 text-center`}>{t('aiworkbench.ws.wi.selectAnyWidget')}</div>;

                return (
                  <div className="space-y-4">
                    {/* Common Widget header */}
                    <div className={`${styles.sidebarBg} p-2.5 rounded-lg border ${styles.cardBorder} space-y-1`}>
                      <div className="flex items-center justify-between">
                        <span className={`font-bold ${styles.cardText} text-xs font-mono lowercase`}>type: {w.type}</span>
                        <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>{w.id}</span>
                      </div>
                      <p className={`text-[9px] ${styles.cardTextMuted}`}>{t('aiworkbench.ws.wi.slotPrefix')} {w.slot}</p>
                    </div>

                    {/* Widget Display Title */}
                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.displayTitleLabel')}</label>
                      <input
                        type="text"
                        value={w.title}
                        onChange={e => handleUpdateWidgetConfig({}, e.target.value)}
                        className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden text-xs ${styles.cardBg}`}
                      />
                    </div>

                    {/* Data Source selection */}
                    {['table', 'chart', 'metric', 'filter_bar'].includes(w.type) && (
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.dataSourceLabel')}</label>
                        <select
                          value={w.config.dataSourceVarId || ''}
                          onChange={e => handleUpdateWidgetConfig({ dataSourceVarId: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                        >
                          {activeApp.variables.filter((v: any) => v.type === 'object_set').map((v: any) => (
                            <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {/* Widget Specific options */}
                    {w.type === 'table' && (
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.tableTargetLabel')}</label>
                        <select
                          value={w.config.targetVarId || ''}
                          onChange={e => handleUpdateWidgetConfig({ targetVarId: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                        >
                          <option value="">{t('aiworkbench.ws.wi.assignVarPlaceholder')}</option>
                          {activeApp.variables.filter((v: any) => v.type === 'object').map((v: any) => (
                            <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {w.type === 'chart' && (
                      <div className="space-y-3">
                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.chartTypeLabel')}</label>
                          <select
                            value={w.config.chartType || 'bar'}
                            onChange={e => handleUpdateWidgetConfig({ chartType: e.target.value as any })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} ${styles.cardText}`}
                          >
                            <option value="bar">{t('aiworkbench.ws.wi.chart.bar')}</option>
                            <option value="line">{t('aiworkbench.ws.wi.chart.line')}</option>
                            <option value="pie">{t('aiworkbench.ws.wi.chart.pie')}</option>
                          </select>
                        </div>
                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.groupByLabel')}</label>
                          <select
                            value={w.config.groupByProperty || 'status'}
                            onChange={e => handleUpdateWidgetConfig({ groupByProperty: e.target.value })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} ${styles.cardText}`}
                          >
                            <option value="status">{t('aiworkbench.ws.wi.groupBy.status')}</option>
                            <option value="depAirport">{t('aiworkbench.ws.wi.groupBy.depAirport')}</option>
                            <option value="arrAirport">{t('aiworkbench.ws.wi.groupBy.arrAirport')}</option>
                          </select>
                        </div>
                      </div>
                    )}

                    {w.type === 'object_view' && (
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.objectTargetLabel')}</label>
                        <select
                          value={w.config.targetVarId || ''}
                          onChange={e => handleUpdateWidgetConfig({ targetVarId: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                        >
                          {activeApp.variables.filter((v: any) => v.type === 'object').map((v: any) => (
                            <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {w.type === 'action_button' && (
                      <div className="space-y-3">
                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wi.actionTypeLabel')}</label>
                          <select
                            value={w.config.actionTypeId || ''}
                            onChange={e => handleUpdateWidgetConfig({ actionTypeId: e.target.value })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                          >
                            <option value="">{t('aiworkbench.ws.wi.selectActionPlaceholder')}</option>
                            {mockActionTypes.map((act: any) => (
                              <option key={act.id} value={act.id}>{act.displayName}</option>
                            ))}
                          </select>
                        </div>

                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.wiParamLabel')}</label>
                          <select
                            value={w.config.targetVarId || ''}
                            onChange={e => handleUpdateWidgetConfig({ targetVarId: e.target.value })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                          >
                            <option value="">{t('aiworkbench.ws.wi.assignEntityPlaceholder')}</option>
                            {activeApp.variables.filter((v: any) => v.type === 'object').map((v: any) => (
                              <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                            ))}
                          </select>
                        </div>
                      </div>
                    )}

                    <div className={`pt-3 border-t ${styles.cardBorder} flex justify-end`}>
                      <button
                        type="button"
                        onClick={() => handleDeleteWidget(w.id)}
                        className="px-3 py-1.5 bg-red-50 hover:bg-red-100 text-red-600 rounded-md font-bold transition-colors flex items-center gap-1 cursor-pointer"
                      >
                        <Trash2 size={11} />
                        <span>{t('aiworkbench.ws.wi.removeComponent')}</span>
                      </button>
                    </div>

                  </div>
                );
              })() : (
                <div className={`py-12 text-center ${styles.cardTextMuted} flex flex-col items-center justify-center`}>
                  <MousePointerClick size={24} className={`stroke-1 ${styles.cardTextMuted} mb-2`} />
                  <p className="font-semibold text-xs leading-normal">{t('aiworkbench.ws.wi.noElementSelected')}</p>
                  <p className={`text-[10px] ${styles.cardTextMuted} mt-1 max-w-[150px] leading-relaxed mx-auto`}>{t('aiworkbench.ws.wi.eduHint')}</p>
                </div>
              )}

            </div>
    </>
  );
}
