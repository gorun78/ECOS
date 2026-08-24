import React from 'react';
import { Trash2, Zap } from 'lucide-react';
import { mockActionTypes } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderCenterAside(vm: any) {
  const { styles: _s, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
                  {/* RIGHT ASIDE VIEW (Object View and Actions) */}
                  <div className="lg:col-span-3 space-y-4">
                    {/* Object card */}
                    {activePage?.widgets.filter(w => w.slot === 'aside' && w.type === 'object_view').map(w => {
                      const isSelected = w.id === selectedWidgetId;
                      const boundTarget = w.config.targetVarId;
                      const currentSelection = getVarValue(boundTarget);

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
                              <span>ObjectView</span>
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          {currentSelection ? (
                            <div className="space-y-2.5">
                              {currentSelection.flightNumber ? (
                                <>
                                  <div className="flex items-center justify-between">
                                    <span className={`text-lg font-black ${styles.cardText}`}>{currentSelection.flightNumber}</span>
                                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                      currentSelection.status === 'ON_TIME' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
                                    }`}>
                                      {currentSelection.status}
                                    </span>
                                  </div>
                                  <div className={`grid grid-cols-2 gap-2 text-[11px] ${styles.appBg} p-2.5 rounded-lg border ${styles.cardBorder} font-mono`}>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>执飞机尾号</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.tailNumber}</p>
                                    </div>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>飞行员编号</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.assignedPilotId}</p>
                                    </div>
                                    <div className={`col-span-2 border-t ${styles.cardBorder}/50 pt-1.5`}>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>起降枢纽港</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.depAirport} ✈ {currentSelection.arrAirport}</p>
                                    </div>
                                  </div>
                                </>
                              ) : (
                                <>
                                  <div className="flex items-center justify-between">
                                    <span className={`text-lg font-black ${styles.cardText}`}>{currentSelection.tailNumber}</span>
                                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700`}>
                                      {currentSelection.status}
                                    </span>
                                  </div>
                                  <div className={`grid grid-cols-2 gap-2 text-[11px] ${styles.appBg} p-2.5 rounded-lg border ${styles.cardBorder} font-mono`}>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>客机型号</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.model}</p>
                                    </div>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>制造厂商</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.manufacturer}</p>
                                    </div>
                                    <div className={`col-span-2 border-t ${styles.cardBorder}/50 pt-1.5`}>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>最近检修</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.lastMaintenance}</p>
                                    </div>
                                  </div>
                                </>
                              )}
                            </div>
                          ) : (
                            <div className={`py-8 text-center ${styles.cardTextMuted} flex flex-col items-center justify-center`}>
                              <Inbox size={20} className="stroke-1 text-slate-300 mb-1" />
                              <span>请从表格中选中任意实体查看本体卡片详情</span>
                            </div>
                          )}
                        </div>
                      );
                    })}

                    {/* Action Button */}
                    {activePage?.widgets.filter(w => w.slot === 'aside' && w.type === 'action_button').map(w => {
                      const isSelected = w.id === selectedWidgetId;
                      const boundActionId = w.config.actionTypeId;
                      const boundTargetVarId = w.config.targetVarId;
                      const boundObject = getVarValue(boundTargetVarId);

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
                              <span>Action</span>
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <button
                            disabled={editorMode === 'design' || !boundObject}
                            onClick={() => {
                              const act = mockActionTypes.find(a => a.id === boundActionId);
                              if (act) setShowActionModal(act);
                            }}
                            className={`w-full py-2.5 rounded-lg font-bold text-xs shadow-xs transition-all flex items-center justify-center gap-1.5 ${
                              editorMode === 'design' ? '${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed border ${styles.cardBorder}' :
                              boundObject ? getPrimaryColorClass(activeApp.theme.primaryColor) : '${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed border ${styles.cardBorder}'
                            }`}
                          >
                            <Zap size={12} className={boundObject ? "fill-white/25" : ""} />
                            <span>{w.title}</span>
                          </button>
                          {!boundObject && editorMode === 'preview' && (
                            <p className={`text-[9px] ${styles.cardTextMuted} text-center mt-1.5 leading-tight`}>需要选中一个本体实例后方能触发本项操作</p>
                          )}
                        </div>
                      );
                    })}

                    {editorMode === 'design' && activePage?.widgets.filter(w => w.slot === 'aside').length === 0 && (
                      <button
                        onClick={() => { setAddWidgetSlot('aside'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[140px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">放置侧边面板栏</span>
                      </button>
                    )}
                  </div>
    </>
  );
}
