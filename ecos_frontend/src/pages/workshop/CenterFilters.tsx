import React from 'react';
import { Plus } from 'lucide-react';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderCenterFilters(vm: any) {
  const { styles: _s, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
                  <div className="lg:col-span-3 space-y-4">
                    {activePage?.widgets.filter(w => w.slot === 'sidebar').map(w => {
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
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          {/* Simulated Filters */}
                          <div className="space-y-3">
                            <div className="space-y-1">
                              <label className={`${styles.cardTextMuted} text-[10px] uppercase font-bold tracking-wider`}>航班状态：</label>
                              <div className="space-y-1.5">
                                {['ALL', 'ON_TIME', 'DELAYED', 'BOARDING', 'CANCELLED'].map(st => (
                                  <label key={st} className={`flex items-center gap-2 cursor-pointer ${styles.cardText}`}>
                                    <input
                                      type="radio"
                                      name="status_filter"
                                      disabled={editorMode === 'design'}
                                      checked={activeStatus === st}
                                      onChange={() => handleVariableChange('v_filter_status', st)}
                                      className="rounded-full text-blue-600 border-slate-300 h-3 w-3 cursor-pointer"
                                    />
                                    <span className="font-semibold text-xs">
                                      {st === 'ALL' ? '全量展示' : st === 'ON_TIME' ? '准点运行' : st === 'DELAYED' ? '发生延误' : st === 'BOARDING' ? '登机中' : '已取消'}
                                    </span>
                                  </label>
                                ))}
                              </div>
                            </div>

                            <div className={`space-y-1 pt-2 border-t ${styles.cardBorder}`}>
                              <label className={`${styles.cardTextMuted} text-[10px] uppercase font-bold tracking-wider`}>枢纽港口：</label>
                              <select
                                disabled={editorMode === 'design'}
                                value={activeAirport}
                                onChange={e => handleVariableChange('v_filter_airport', e.target.value)}
                                className={`w-full px-2 py-1 ${styles.cardBg} border ${styles.cardBorder} rounded-md text-xs font-semibold`}
                              >
                                <option value="ALL">全部关联机场</option>
                                <option value="ORD">ORD (芝加哥)</option>
                                <option value="ATL">ATL (亚特兰大)</option>
                                <option value="DFW">DFW (达拉斯)</option>
                                <option value="SFO">SFO (旧金山)</option>
                                <option value="PEK">PEK (北京首都)</option>
                              </select>
                            </div>
                          </div>
                        </div>
                      );
                    })}

                    {editorMode === 'design' && (
                      <button
                        onClick={() => { setAddWidgetSlot('sidebar'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[100px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">添加页面筛选栏</span>
                      </button>
                    )}
                  </div>
    </>
  );
}
