import React from 'react';
import { Plus, Trash2, AlertTriangle, RotateCw } from 'lucide-react';
import { DynamicIcon } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderCenterCanvas(vm: any) {
  const { styles, t, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass, simDataError, simDataLoading, retryLoadSimData } = vm;
  return (
    <>
              
              {/* Application's Own Inner Header Bar */}
              <div className={`h-11 px-4 flex items-center justify-between shadow-xs border-b shrink-0 ${
                activeApp.theme.isDark ? `${styles.cardBg} ${styles.cardText}` : `${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`
              }`}>
                <div className="flex items-center gap-2">
                  <span className={`p-1 rounded ${styles.badgeBg} ${styles.badgeText}`}>
                    <DynamicIcon name={activeApp.theme.logo || 'Plane'} size={14} />
                  </span>
                  <span className="font-bold text-sm tracking-tight">{activeApp.theme.title}</span>
                </div>
                
                {/* Horizontal Navigation tabs inside App page */}
                <div className="flex items-center gap-1.5">
                  {activeApp.pages.map((p: any) => (
                    <button
                      type="button"
                      key={p.id}
                      onClick={() => {
                        setActivePageId(p.id);
                        setSelectedWidgetId(null);
                      }}
                      className={`h-7 px-3 rounded-md text-[11px] font-semibold transition-all ${
                        p.id === activePageId
                          ? `${styles.badgeBg} ${styles.badgeText}`
                          : 'opacity-65 hover:opacity-100'
                      }`}
                    >
                      {p.title}
                    </button>
                  ))}
                </div>
              </div>

              {/* 本体数据加载状态：错误态可见 + 重试（不掩成空列表假象） */}
              {simDataLoading && (
                <div className={`px-4 py-2 text-[10px] ${styles.cardTextMuted} flex items-center gap-1.5`}>
                  <RotateCw size={11} className="animate-spin" />
                  <span>{t('aiworkbench.ws.ops.dataLoading')}</span>
                </div>
              )}
              {simDataError && (
                <div className={`mx-4 mt-2 rounded-lg border px-3 py-2 text-[11px] flex items-center justify-between gap-2 ${styles.dangerBg} ${styles.dangerText}`}>
                  <span className="flex items-center gap-1.5 min-w-0">
                    <AlertTriangle size={12} className="shrink-0" />
                    <span className="truncate">{t('aiworkbench.ws.ops.dataLoadFailed', { message: simDataError })}</span>
                  </span>
                  <button
                    type="button"
                    onClick={() => void retryLoadSimData()}
                    className={`shrink-0 px-2 py-1 rounded-md border ${styles.cardBorder} font-bold cursor-pointer`}
                  >
                    {t('aiworkbench.ws.ops.dataRetry')}
                  </button>
                </div>
              )}

              {/* Central canvas structure of activePage */}
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                
                {/* 2.1. TOP METRICS SLOT */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {activePage?.widgets.filter((w: any) => w.slot === 'main_top').map((w: any) => {
                    const isSelected = w.id === selectedWidgetId;
                    const ds = w.config.dataSourceVarId;
                    const type = w.config.metricType;

                    // Compute simulation data
                    let metricValue = 0;
                    if (ds === 'v_flights_all') {
                      metricValue = flightsData.length;
                    } else if (ds === 'v_flights_filtered') {
                      const simData = getSimulatedFlights();
                      if (type === 'count') {
                        metricValue = simData.length;
                      } else {
                        metricValue = simData.filter((f: any) => f.status === 'DELAYED').length;
                      }
                    } else {
                      metricValue = aircraftData.length;
                    }

                    return (
                      <div
                        key={w.id}
                        onClick={() => editorMode === 'design' && setSelectedWidgetId(w.id)}
                        className={`relative rounded-xl border p-4 ${styles.cardBg} shadow-xs transition-all ${
                          editorMode === 'design' ? 'cursor-pointer hover:border-blue-400' : ''
                        } ${isSelected ? 'ring-2 ring-blue-500 border-transparent scale-102' : styles.cardBorder}`}
                      >
                        {editorMode === 'design' && (
                          <div className={`absolute top-1 right-1 flex items-center gap-1 opacity-60 hover:opacity-100 ${styles.sidebarBg} rounded px-1.5 py-0.5 text-[8px] font-mono`}>
                            <span>Hash</span>
                            <button type="button" onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                          </div>
                        )}
                        <p className={`text-[10px] uppercase font-bold ${styles.cardTextMuted} tracking-wider`}>{w.title}</p>
                        <h2 className={`text-2xl font-black ${styles.cardText} mt-1`}>{metricValue} <span className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t('aiworkbench.ws.cc.records')}</span></h2>
                      </div>
                    );
                  })}

                  {editorMode === 'design' && (
                    <button
                      type="button"
                      onClick={() => { setAddWidgetSlot('main_top'); setShowAddWidgetModal(true); }}
                      className={`border-2 border-dashed ${styles.divider} rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} transition-all cursor-pointer min-h-[80px]`}
                    >
                      <Plus size={15} />
                      <span className="text-[10px] mt-1 font-semibold">{t('aiworkbench.ws.cc.addMetricCard')}</span>
                    </button>
                  )}
                </div>

                {/* 2.2. MAIN SPLIT CONTENT AREA (Chart & Table / Filters & Views) */}
              </div>
    </>
  );
}
