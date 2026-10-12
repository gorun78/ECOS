import React from 'react';
import { Plus } from 'lucide-react';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderCenterFilters(vm: any) {
  const { t, styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
                  <div className="lg:col-span-3 space-y-4">
                    {activePage?.widgets.filter((w: any) => w.slot === 'sidebar').map((w: any) => {
                      const isSelected = w.id === selectedWidgetId;
                      const activeStatus = getVarValue('v_filter_status') || 'ALL';
                      const activeAirport = getVarValue('v_filter_airport') || 'ALL';

                      return (
                        <div
                          key={w.id}
                          onClick={() => editorMode === 'design' && setSelectedWidgetId(w.id)}
                          className={`relative rounded-xl border p-4 ${styles.cardBg} shadow-xs transition-all ${
                            editorMode === 'design' ? 'cursor-pointer hover:border-blue-400' : ''
                          } ${isSelected ? 'ring-2 ring-blue-500 border-transparent' : '${styles.cardBorder}'}`}
                        >
                          {editorMode === 'design' && (
                              <div className={`absolute top-1 right-1 flex items-center gap-1 opacity-60 hover:opacity-100 ${styles.sidebarBg} rounded px-1.5 py-0.5 text-[8px] font-mono`}>
                              <span>Filter</span>
                              <button type="button" onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          {/* Simulated Filters */}
                          <div className="space-y-3">
                            <div className="space-y-1">
                              <label className={`${styles.cardTextMuted} text-[10px] uppercase font-bold tracking-wider`}>{t('aiworkbench.ws.cf.statusLabel')}</label>
                              <div className="space-y-1.5">
                                {['ALL', 'ON_TIME', 'DELAYED', 'BOARDING', 'CANCELLED'].map((st: any) => {
                                  const stKey = st.toLowerCase();
                                  return (
                                  <label key={st} className={`flex items-center gap-2 cursor-pointer ${styles.cardText}`}>
                                    <input
                                      type="radio"
                                      name="status_filter"
                                      disabled={editorMode === 'design'}
                                      checked={activeStatus === st}
                                      onChange={() => handleVariableChange('v_filter_status', st)}
                                      className={`rounded-full text-blue-600 border ${styles.cardBorder} h-3 w-3 cursor-pointer`}
                                    />
                                    <span className="font-semibold text-xs">
                                      {t(`aiworkbench.ws.cf.status.${stKey}`)}
                                    </span>
                                  </label>
                                  );
                                })}
                              </div>
                            </div>

                            <div className={`space-y-1 pt-2 border-t ${styles.cardBorder}`}>
                              <label className={`${styles.cardTextMuted} text-[10px] uppercase font-bold tracking-wider`}>{t('aiworkbench.ws.cf.hubPortLabel')}</label>
                              <select
                                disabled={editorMode === 'design'}
                                value={activeAirport}
                                onChange={e => handleVariableChange('v_filter_airport', e.target.value)}
                                className={`w-full px-2 py-1 ${styles.cardBg} border ${styles.cardBorder} rounded-md text-xs font-semibold`}
                              >
                                <option value="ALL">{t('aiworkbench.ws.cf.airport.all')}</option>
                                <option value="ORD">{t('aiworkbench.ws.cf.airport.ord')}</option>
                                <option value="ATL">{t('aiworkbench.ws.cf.airport.atl')}</option>
                                <option value="DFW">{t('aiworkbench.ws.cf.airport.dfw')}</option>
                                <option value="SFO">{t('aiworkbench.ws.cf.airport.sfo')}</option>
                                <option value="PEK">{t('aiworkbench.ws.cf.airport.pek')}</option>
                              </select>
                            </div>
                          </div>
                        </div>
                      );
                    })}

                    {editorMode === 'design' && (
                      <button
                        type="button"
                        onClick={() => { setAddWidgetSlot('sidebar'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed ${styles.divider} rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} transition-all cursor-pointer min-h-[100px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">{t('aiworkbench.ws.cf.addFilters')}</span>
                      </button>
                    )}
                  </div>
    </>
  );
}
