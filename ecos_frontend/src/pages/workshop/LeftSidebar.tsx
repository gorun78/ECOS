import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import { Plus, Trash2, LayoutGrid, LayoutDashboard, Plane, Activity, HeartPulse, Palette, MousePointerClick, Inbox, Settings } from 'lucide-react';
import { DynamicIcon } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderLeftSidebar(vm: any) {
  const { styles } = useTheme();
  const { styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
            {/* COLUMN 1: LEFT CONFIG SIDEBAR (Widget list, Pages, Variables, Styles) */}
            <div className={`w-60 ${styles.appBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
              {/* Tab Selector */}
              <div className={`flex border-b ${styles.cardBorder} divide-x divide-slate-200/55 text-center shrink-0`}>
                {(['pages', 'variables', 'widgets', 'theme'] as const).map(tab => (
                  <button
                    key={tab}
                    onClick={() => setLeftTab(tab)}
                    className={`flex-1 py-2 font-semibold text-[10px] uppercase transition-colors cursor-pointer ${
                      leftTab === tab
                        ? '${styles.cardBg} ${styles.cardText} border-b-2 border-slate-800'
                        : '${styles.cardTextMuted} hover:${styles.sidebarBg} hover:${styles.cardText}'
                    }`}
                  >
                    {tab === 'pages' ? '页面' : tab === 'variables' ? '数据变量' : tab === 'widgets' ? '组件树' : '属性'}
                  </button>
                ))}
              </div>

              {/* Tab Contents */}
              <div className="flex-1 overflow-y-auto p-3">
                
                {/* 1. PAGES VIEW */}
                {leftTab === 'pages' && (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase`}>应用页面 ({activeApp.pages.length})</span>
                      <button onClick={handleAddPage} className="p-1 hover:bg-slate-200 rounded text-blue-600" title="添加新视图">
                        <Plus size={13} />
                      </button>
                    </div>
                    <div className="space-y-1">
                      {activeApp.pages.map(p => {
                        const isActive = p.id === activePageId;
                        return (
                          <div
                            key={p.id}
                            onClick={() => {
                              setActivePageId(p.id);
                              setSelectedWidgetId(null);
                            }}
                            className={`w-full p-2 rounded-lg flex items-center justify-between cursor-pointer transition-colors ${
                              isActive ? '${styles.cardBg} shadow-xs border ${styles.cardBorder} font-bold ${styles.cardText}' : '${styles.cardTextMuted} hover:bg-slate-200/50'
                            }`}
                          >
                            <div className="flex items-center gap-2 truncate">
                              <DynamicIcon name={p.icon} size={12} className={isActive ? 'text-blue-500' : 'text-slate-400'} />
                              <input
                                type="text"
                                value={p.title}
                                disabled={!isActive}
                                onChange={e => {
                                  const title = e.target.value;
                                  const updated = apps.map(a => {
                                    if (a.id === activeApp.id) {
                                      return {
                                        ...a,
                                        pages: a.pages.map(pg => pg.id === p.id ? { ...pg, title } : pg)
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
                                onClick={e => {
                                  e.stopPropagation();
                                  if (!window.confirm(`确定要删除页面「${p.title}」吗？`)) return;
                                  const updated = apps.map(a => {
                                    if (a.id === activeApp.id) {
                                      const remaining = a.pages.filter(pg => pg.id !== p.id);
                                      return { ...a, pages: remaining };
                                    }
                                    return a;
                                  });
                                  saveAppsState(updated);
                                  if (isActive) setActivePageId(activeApp.pages.find(pg => pg.id !== p.id)?.id || '');
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
                      <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase`}>状态与变量</span>
                      <button
                        onClick={() => setShowAddVarModal(true)}
                        className="p-1 hover:bg-slate-200 rounded text-blue-600 flex items-center gap-0.5"
                      >
                        <Plus size={12} />
                        <span className="text-[10px] font-semibold">添加变量</span>
                      </button>
                    </div>

                    <div className="space-y-2">
                      {activeApp.variables.map(v => (
                        <div key={v.id} className={`p-2 ${styles.cardBg} rounded-lg border ${styles.cardBorder} space-y-1 hover:border-slate-300`}>
                          <div className="flex items-center justify-between">
                            <span className={`font-bold ${styles.cardText} text-xs truncate`} title={v.name}>{v.name}</span>
                            <span className={`px-1.5 py-0.2 rounded border text-[8px] font-mono uppercase shrink-0 ${getVarTypeBadge(v.type)}`}>
                              {v.type === 'object_set' ? `集合:${v.objectTypeId}` : v.type === 'object' ? `实体:${v.objectTypeId}` : v.type}
                            </span>
                          </div>
                          <p className={`text-[10px] ${styles.cardTextMuted} leading-tight`}>{v.description}</p>
                          <div className={`${styles.appBg} p-1 rounded font-mono text-[9px] ${styles.cardTextMuted} truncate border ${styles.cardBorder}/50 flex justify-between items-center`}>
                            <span className={`${styles.cardTextMuted}`}>运行值:</span>
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
                    <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>页面组件层级 Tree</span>
                    <div className="space-y-1">
                      {activePage?.widgets.map(w => {
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
                        <div className={`p-4 text-center ${styles.cardTextMuted} text-xs`}>画布空空如也，请从预览区添加组件</div>
                      )}
                    </div>
                  </div>
                )}

                {/* 4. APP THEME VIEW */}
                {leftTab === 'theme' && (
                  <div className="space-y-4">
                    <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase block`}>应用主题与外观设置</span>
                    
                    <div className="space-y-2">
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>应用品牌名</label>
                        <input
                          type="text"
                          value={activeApp.theme.title}
                          onChange={e => handleUpdateAppTheme({ title: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden`}
                        />
                      </div>

                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>应用描述</label>
                        <textarea
                          value={activeApp.description}
                          onChange={e => handleUpdateAppTheme({}, undefined, e.target.value)}
                          rows={2}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden resize-none`}
                        />
                      </div>

                      <div className="space-y-1.5">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>主色调 (Accent Color)</label>
                        <div className="flex gap-2">
                          {['blue', 'indigo', 'violet', 'emerald', 'rose'].map(color => {
                            const selected = activeApp.theme.primaryColor === color;
                            return (
                              <button
                                key={color}
                                onClick={() => handleUpdateAppTheme({ primaryColor: color })}
                                className={`w-5 h-5 rounded-full border ${selected ? 'ring-2 ring-slate-800 border-white shadow-xs' : 'border-transparent'}`}
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
                          <span>深色主题 (Dark Mode)</span>
                          <input
                            type="checkbox"
                            checked={activeApp.theme.isDark}
                            onChange={e => handleUpdateAppTheme({ isDark: e.target.checked })}
                            className={`rounded ${styles.cardText} border-slate-300 h-3 w-3`}
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
