import React from 'react';
import { Plus, Trash2, LayoutGrid, LayoutDashboard, Plane, Activity, HeartPulse, Palette, MousePointerClick, Inbox, Settings } from 'lucide-react';
import { DynamicIcon } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderLeftSidebar(vm: any) {
  const { styles, t, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm as { styles: Record<string, string>; t: (k: string, p?: any) => string; [key: string]: any };
  return (
    <>
            {/* COLUMN 1: LEFT CONFIG SIDEBAR (Widget list, Pages, Variables, Styles) */}
            <div className={`w-60 ${styles.appBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
              {/* Tab Selector */}
              <div className={`flex border-b ${styles.cardBorder} divide-x ${styles.divider} text-center shrink-0`}>
                {(['pages', 'variables', 'widgets', 'theme'] as const).map(tab => (
                  <button
                    type="button"
                    key={tab}
                    onClick={() => setLeftTab(tab)}
                    className={`flex-1 py-2 font-semibold text-[10px] uppercase transition-colors cursor-pointer ${
                      leftTab === tab
                        ? '${styles.cardBg} ${styles.cardText} border-b-2 border-blue-500'
                        : '${styles.cardTextMuted} hover:${styles.sidebarBg} hover:${styles.cardText}'
                    }`}
                  >
                    {t(`aiworkbench.ws.ls.tab.${tab}`)}
                  </button>
                ))}
              </div>

              {/* Tab Contents */}
              <div className="flex-1 overflow-y-auto p-3">

                {/* 1. PAGES VIEW */}
                {leftTab === 'pages' && (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase`}>{t('aiworkbench.ws.ls.appPages', { count: activeApp.pages.length })}</span>
                      <button type="button" onClick={handleAddPage} className={`p-1 ${styles.sidebarHoverBg} rounded text-blue-600`} title={t('aiworkbench.ws.ls.addPageHint')}>
                        <Plus size={13} />
                      </button>
                    </div>
                    <div className="space-y-1">
                      {activeApp.pages.map((p: any) => {
                        const isActive = p.id === activePageId;
                        return (
                          <div
                            key={p.id}
                            onClick={() => {
                              setActivePageId(p.id);
                              setSelectedWidgetId(null);
                            }}
                            className={`w-full p-2 rounded-lg flex items-center justify-between cursor-pointer transition-colors ${
                              isActive ? '${styles.cardBg} shadow-xs border ${styles.cardBorder} font-bold ${styles.cardText}' : '${styles.cardTextMuted} ${styles.sidebarHoverBg}'
                            }`}
                          >
                            <div className="flex items-center gap-2 truncate">
                              <DynamicIcon name={p.icon} size={12} className={isActive ? 'text-blue-500' : styles.cardTextMuted} />
                              <input
                                type="text"
                                value={p.title}
                                disabled={!isActive}
                                onChange={e => {
                                  const title = e.target.value;
                                  const updated = apps.map((a: any) => {
                                    if (a.id === activeApp.id) {
                                      return {
                                        ...a,
                                        pages: a.pages.map((pg: any) => pg.id === p.id ? { ...pg, title } : pg)
                                      };
                                    }
                                    return a;
                                  });
                                  saveAppsState(updated);
                                }}
                                className="bg-transparent border-none text-xs focus:outline-hidden p-0 truncate font-semibold w-36 disabled:cursor-pointer"
                              />
                            </div>
                            {activeApp.pages.length > 1 && (
                              <button
                                type="button"
                                onClick={e => {
                                  e.stopPropagation();
                                  if (!window.confirm(t('aiworkbench.ws.ls.confirmDeletePage', { name: p.title }))) return;
                                  const updated = apps.map((a: any) => {
                                    if (a.id === activeApp.id) {
                                      const remaining = a.pages.filter((pg: any) => pg.id !== p.id);
                                      return { ...a, pages: remaining };
                                    }
                                    return a;
                                  });
                                  saveAppsState(updated);
                                  if (isActive) setActivePageId(activeApp.pages.find((pg: any) => pg.id !== p.id)?.id || '');
                                }}
                                className="p-1 opacity-0 hover:opacity-100 text-red-500 hover:bg-red-50 rounded"
                              >
                                <Trash2 size={11} />
                              </button>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 2. VARIABLES VIEW */}
                {leftTab === 'variables' && (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase`}>{t('aiworkbench.ws.ls.stateAndVars')}</span>
                      <button
                        type="button"
                        onClick={() => setShowAddVarModal(true)}
                        className={`p-1 ${styles.sidebarHoverBg} rounded text-blue-600 flex items-center gap-0.5`}
                      >
                        <Plus size={12} />
                        <span className="text-[10px] font-semibold">{t('aiworkbench.ws.ls.addVariable')}</span>
                      </button>
                    </div>

                    <div className="space-y-2">
                      {activeApp.variables.map((v: any) => (
                        <div key={v.id} className={`p-2 ${styles.cardBg} rounded-lg border ${styles.cardBorder} space-y-1`}>
                          <div className="flex items-center justify-between">
                            <span className={`font-bold ${styles.cardText} text-xs truncate`} title={v.name}>{v.name}</span>
                            <span className={`px-1.5 py-0.2 rounded border text-[8px] font-mono uppercase shrink-0 ${getVarTypeBadge(v.type)}`}>
                              {v.type === 'object_set' ? t('aiworkbench.ws.ls.collection', { typeId: v.objectTypeId }) : v.type === 'object' ? t('aiworkbench.ws.ls.entity', { typeId: v.objectTypeId }) : v.type}
                            </span>
                          </div>
                          <p className={`text-[10px] ${styles.cardTextMuted} leading-tight`}>{v.description}</p>
                          <div className={`${styles.appBg} p-1 rounded font-mono text-[9px] ${styles.cardTextMuted} truncate border ${styles.cardBorder}/50 flex justify-between items-center`}>
                            <span className={`${styles.cardTextMuted}`}>{t('aiworkbench.ws.ls.runtimeValue')}</span>
                            <span className={`truncate max-w-32 font-bold ${styles.cardText}`}>
                              {v.type === 'object_set' ? 'Dynamic Set' : v.value ? (typeof v.value === 'object' ? v.value.flightNumber || v.value.tailNumber : String(v.value)) : 'null'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 3. WIDGETS TREE VIEW */}
                {leftTab === 'widgets' && (
                  <div className="space-y-3">
                    <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>{t('aiworkbench.ws.ls.widgetTreeTitle')}</span>
                    <div className="space-y-1">
                      {activePage?.widgets.map((w: any) => {
                        const isSelected = w.id === selectedWidgetId;
                        return (
                          <div
                            key={w.id}
                            onClick={() => setSelectedWidgetId(w.id)}
                            className={`p-1.5 rounded-md flex items-center justify-between cursor-pointer transition-all ${
                              isSelected ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600' : '${styles.cardTextMuted} hover:${styles.sidebarBg}'
                            }`}
                          >
                            <div className="flex items-center gap-1.5 truncate">
                              <span className={`${styles.cardTextMuted}`}>
                                <DynamicIcon
                                  name={
                                    w.type === 'table' ? 'TableProperties' :
                                    w.type === 'chart' ? 'BarChart3' :
                                    w.type === 'metric' ? 'Hash' :
                                    w.type === 'object_view' ? 'FileText' : 'PlayCircle'
                                  }
                                  size={11}
                                />
                              </span>
                              <span className="truncate text-[11px]">{w.title}</span>
                            </div>
                            <span className={`text-[8px] font-mono ${styles.cardTextMuted} lowercase`}>{w.slot}</span>
                          </div>
                        );
                      })}
                      {(!activePage || activePage.widgets.length === 0) && (
                        <div className={`p-4 text-center ${styles.cardTextMuted} text-xs`}>{t('aiworkbench.ws.ls.canvasEmpty')}</div>
                      )}
                    </div>
                  </div>
                )}

                {/* 4. APP THEME VIEW */}
                {leftTab === 'theme' && (
                  <div className="space-y-4">
                    <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase block`}>{t('aiworkbench.ws.ls.themeTitle')}</span>

                    <div className="space-y-2">
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.ls.brandLabel')}</label>
                        <input
                          type="text"
                          value={activeApp.theme.title}
                          onChange={e => handleUpdateAppTheme({ title: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden`}
                        />
                      </div>

                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.ls.descLabel')}</label>
                        <textarea
                          value={activeApp.description}
                          onChange={e => handleUpdateAppTheme({}, undefined, e.target.value)}
                          rows={2}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden resize-none`}
                        />
                      </div>

                      <div className="space-y-1.5">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.ls.accentColor')}</label>
                        <div className="flex gap-2">
                          {['blue', 'indigo', 'violet', 'emerald', 'rose'].map(color => {
                            const selected = activeApp.theme.primaryColor === color;
                            return (
                              <button
                                type="button"
                                key={color}
                                onClick={() => handleUpdateAppTheme({ primaryColor: color })}
                                className={`w-5 h-5 rounded-full border ${selected ? 'ring-2 ring-blue-500 border-white shadow-xs' : 'border-transparent'}`}
                                style={{
                                  backgroundColor:
                                    color === 'blue' ? '#2563eb' :
                                    color === 'indigo' ? '#4f46e5' :
                                    color === 'violet' ? '#7c3aed' :
                                    color === 'emerald' ? '#059669' : '#e11d48'
                                }}
                              />
                            );
                          })}
                        </div>
                      </div>

                      <div className={`space-y-1.5 pt-2 border-t ${styles.cardBorder}/50`}>
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px] flex items-center justify-between`}>
                          <span>{t('aiworkbench.ws.ls.darkMode')}</span>
                          <input
                            type="checkbox"
                            checked={activeApp.theme.isDark}
                            onChange={e => handleUpdateAppTheme({ isDark: e.target.checked })}
                            className={`rounded ${styles.cardText} border ${styles.cardBorder} h-3 w-3`}
                          />
                        </label>
                      </div>
                    </div>
                  </div>
                )}

              </div>
            </div>
    </>
  );
}
