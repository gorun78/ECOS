import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import { Trash2 } from 'lucide-react';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderWidgetInspector(vm: any) {
  const { styles } = useTheme();
  const { styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
            <div className={`w-64 ${styles.appBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0 overflow-y-auto p-4 space-y-4 text-xs select-none`}>
              <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase tracking-wider`}>组件属性配置面板 (Properties)</span>

              {selectedWidgetId ? (() => {
                const w = activePage?.widgets.find(wg => wg.id === selectedWidgetId);
                if (!w) return <div className={`${styles.cardTextMuted} py-6 text-center`}>请选中任意组件进行设置</div>;

                return (
                  <div className="space-y-4">
                    {/* Common Widget header */}
                    <div className={`${styles.sidebarBg} p-2.5 rounded-lg border ${styles.cardBorder} space-y-1`}>
                      <div className="flex items-center justify-between">
                        <span className={`font-bold ${styles.cardText} text-xs font-mono lowercase`}>type: {w.type}</span>
                        <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>{w.id}</span>
                      </div>
                      <p className={`text-[9px] ${styles.cardTextMuted}`}>布局插槽: {w.slot}</p>
                    </div>

                    {/* Widget Display Title */}
                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>组件显示标题</label>
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
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>绑定数据源 (Object Set)</label>
                        <select
                          value={w.config.dataSourceVarId || ''}
                          onChange={e => handleUpdateWidgetConfig({ dataSourceVarId: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                        >
                          {activeApp.variables.filter(v => v.type === 'object_set').map(v => (
                            <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {/* Widget Specific options */}
                    {w.type === 'table' && (
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>选中项输出至变量 (Object Selection)</label>
                        <select
                          value={w.config.targetVarId || ''}
                          onChange={e => handleUpdateWidgetConfig({ targetVarId: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                        >
                          <option value="">-- 请指派变量 --</option>
                          {activeApp.variables.filter(v => v.type === 'object').map(v => (
                            <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {w.type === 'chart' && (
                      <div className="space-y-3">
                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>图表呈现样式 (Chart Type)</label>
                          <select
                            value={w.config.chartType || 'bar'}
                            onChange={e => handleUpdateWidgetConfig({ chartType: e.target.value as any })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} ${styles.cardText}`}
                          >
                            <option value="bar">📊 柱状堆叠图 (Bar Chart)</option>
                            <option value="line">📈 趋势折线图 (Line Chart)</option>
                            <option value="pie">🍰 占比饼状图 (Pie Chart)</option>
                          </select>
                        </div>
                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>聚合维度属性 (Group By)</label>
                          <select
                            value={w.config.groupByProperty || 'status'}
                            onChange={e => handleUpdateWidgetConfig({ groupByProperty: e.target.value })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} ${styles.cardText}`}
                          >
                            <option value="status">运行状态 (Status)</option>
                            <option value="depAirport">起飞机场 (Departure Airport)</option>
                            <option value="arrAirport">到达机场 (Arrival Airport)</option>
                          </select>
                        </div>
                      </div>
                    )}

                    {w.type === 'object_view' && (
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>绑定目标变量 (Selected Object)</label>
                        <select
                          value={w.config.targetVarId || ''}
                          onChange={e => handleUpdateWidgetConfig({ targetVarId: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                        >
                          {activeApp.variables.filter(v => v.type === 'object').map(v => (
                            <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                          ))}
                        </select>
                      </div>
                    )}

                    {w.type === 'action_button' && (
                      <div className="space-y-3">
                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>触发的 Ontology 操作</label>
                          <select
                            value={w.config.actionTypeId || ''}
                            onChange={e => handleUpdateWidgetConfig({ actionTypeId: e.target.value })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                          >
                            <option value="">-- 请选择 Action --</option>
                            {mockActionTypes.map(act => (
                              <option key={act.id} value={act.id}>{act.displayName}</option>
                            ))}
                          </select>
                        </div>

                        <div className="space-y-1">
                          <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>传入参数来源变量</label>
                          <select
                            value={w.config.targetVarId || ''}
                            onChange={e => handleUpdateWidgetConfig({ targetVarId: e.target.value })}
                            className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                          >
                            <option value="">-- 请指派选定实体 --</option>
                            {activeApp.variables.filter(v => v.type === 'object').map(v => (
                              <option key={v.id} value={v.id}>{v.name} ({v.id})</option>
                            ))}
                          </select>
                        </div>
                      </div>
                    )}

                    <div className={`pt-3 border-t ${styles.cardBorder} flex justify-end`}>
                      <button
                        onClick={() => handleDeleteWidget(w.id)}
                        className="px-3 py-1.5 bg-red-50 hover:bg-red-100 text-red-600 rounded-md font-bold transition-colors flex items-center gap-1 cursor-pointer"
                      >
                        <Trash2 size={11} />
                        <span>移除组件</span>
                      </button>
                    </div>

                  </div>
                );
              })() : (
                <div className={`py-12 text-center ${styles.cardTextMuted} flex flex-col items-center justify-center`}>
                  <MousePointerClick size={24} className="stroke-1 text-slate-300 mb-2" />
                  <p className="font-semibold text-xs leading-normal">未选中任何元素</p>
                  <p className={`text-[10px] ${styles.cardTextMuted} mt-1 max-w-[150px] leading-relaxed mx-auto`}>请点击左侧组件树或中央设计画布上的任意组件查看属性进行定制。</p>
                </div>
              )}

            </div>
    </>
  );
}
