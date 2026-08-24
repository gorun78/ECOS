import React from 'react';
import { Check, Plus, PlusCircle, Zap } from 'lucide-react';
import { DynamicIcon, mockActionTypes } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderWorkshopModals(vm: any) {
  const { styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
      {/* 4. MODAL: ADD WIDGET */}
      {showAddWidgetModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs">
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-lg overflow-hidden flex flex-col`}>
            <div className={`px-4 py-3 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
              <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
                <PlusCircle size={14} className="text-blue-500" />
                <span>向布局插槽「{addWidgetSlot}」添加组件</span>
              </h3>
              <button onClick={() => setShowAddWidgetModal(false)} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} text-sm font-bold`}>×</button>
            </div>
            
            <div className="p-4 grid grid-cols-2 gap-3 max-h-[350px] overflow-y-auto">
              {[
                { type: 'table', title: '本体表格组件 (Table)', desc: '直接渲染 bound object_set 数据的实体清单，高亮选择输出至单个 Object 变量。', icon: 'TableProperties' },
                { type: 'chart', title: '智能分析图表 (Chart)', desc: '绑定本体属性，将特定状态数据利用多维 Bar、Line、Pie 图表展开深度统计。', icon: 'BarChart3' },
                { type: 'metric', title: '度量指标卡 (Metric Card)', desc: '显示单项统计指标数据（总记录量、计数、聚合求和或平均数值）。', icon: 'Hash' },
                { type: 'object_view', title: '实体档案卡片 (Object View)', desc: '绑定单个选定实体变量，直接读取实体的主键、名称及所有本体属性。', icon: 'FileText' },
                { type: 'action_button', title: 'Ontology 操作按钮', desc: '执行由本体工作台发布的业务流 Action，具备参数表单映射及防错规则。', icon: 'Zap' },
                { type: 'filter_bar', title: '应用属性筛选器', desc: '为页面生成条件单选、多选、下拉等过滤面板，驱动大盘视图同步刷新。', icon: 'SlidersHorizontal' }
              ].map(item => (
                <div
                  key={item.type}
                  onClick={() => handleAddWidget(item.type as any)}
                  className={`border ${styles.cardBorder} rounded-xl p-3 hover:border-blue-500 hover:bg-blue-50/50 cursor-pointer transition-all space-y-1.5 flex flex-col justify-between`}
                >
                  <div className="flex items-center gap-2">
                    <span className="p-1.5 rounded-lg bg-blue-50 text-blue-600 border border-blue-100">
                      <DynamicIcon name={item.icon} size={13} />
                    </span>
                    <span className={`font-bold ${styles.cardText} text-[11px]`}>{item.title}</span>
                  </div>
                  <p className={`text-[10px] ${styles.cardTextMuted} leading-normal`}>{item.desc}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 5. MODAL: ADD VARIABLE */}
      {showAddVarModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs">
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-sm overflow-hidden`}>
            <form onSubmit={handleAddVariable}>
              <div className={`px-4 py-3 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
                <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
                  <Settings size={14} className="text-blue-500" />
                  <span>添加应用运行时变量</span>
                </h3>
                <button type="button" onClick={() => setShowAddVarModal(false)} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} text-sm font-bold`}>×</button>
              </div>

              <div className="p-4 space-y-3">
                <div className="space-y-1">
                  <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>变量名称</label>
                  <input
                    type="text"
                    value={newVarName}
                    onChange={e => setNewVarName(e.target.value)}
                    placeholder="例如: v_filter_status"
                    className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs focus:outline-hidden`}
                    required
                  />
                </div>

                <div className="space-y-1">
                  <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>变量类型</label>
                  <select
                    value={newVarType}
                    onChange={e => setNewVarType(e.target.value as any)}
                    className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                  >
                    <option value="string">文本 (String)</option>
                    <option value="number">数值 (Number)</option>
                    <option value="object_set">本体对象集 (Object Set)</option>
                    <option value="object">单一本体实体 (Single Object)</option>
                  </select>
                </div>

                {['object_set', 'object'].includes(newVarType) && (
                  <div className="space-y-1">
                    <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>绑定本体对象类型 (Ontology Link)</label>
                    <select
                      value={newVarObjType}
                      onChange={e => setNewVarObjType(e.target.value)}
                      className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                    >
                      <option value="flight">航班 (Flight)</option>
                      <option value="aircraft">飞机 (Aircraft)</option>
                      <option value="pilot">飞行员 (Pilot)</option>
                    </select>
                  </div>
                )}

                <div className="space-y-1">
                  <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>简短说明</label>
                  <input
                    type="text"
                    value={newVarDesc}
                    onChange={e => setNewVarDesc(e.target.value)}
                    placeholder="存储及控制大盘交互..."
                    className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs focus:outline-hidden`}
                  />
                </div>
              </div>

              <div className={`px-4 py-3 ${styles.appBg} border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
                <button
                  type="button"
                  onClick={() => setShowAddVarModal(false)}
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-md hover:${styles.sidebarBg} font-semibold`}
                >
                  取消
                </button>
                <button type="submit" className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-md">
                  确定添加
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 6. MODAL: RUN SIMULATED ACTION TYPE */}
      {showActionModal && (() => {
        const boundObject = activeApp ? activeApp.variables.find(v => v.type === 'object')?.value : null;

        return (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs">
            <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-sm overflow-hidden`}>
              <div className={`px-4 py-3 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
                <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
                  <span className="p-1 rounded bg-amber-500 text-white">
                    <Zap size={12} className="fill-white/20" />
                  </span>
                  <span>执行本体修改：{showActionModal.displayName}</span>
                </h3>
                <button onClick={() => setShowActionModal(null)} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} text-sm font-bold`}>×</button>
              </div>

              <div className="p-4 space-y-3">
                <p className={`text-[10px] ${styles.cardTextMuted} italic leading-relaxed border-b ${styles.cardBorder} pb-2`}>{showActionModal.description}</p>
                
                {/* Dynamically prompt parameter form based on bound action type */}
                {showActionModal.id === 'update_flight_status' && (
                  <>
                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>目标航班号 (Target Key)</label>
                      <input
                        type="text"
                        disabled
                        value={boundObject?.flightNumber || ''}
                        className={`w-full px-2 py-1.5 ${styles.sidebarBg} border ${styles.cardBorder} rounded-md font-bold font-mono text-xs ${styles.cardTextMuted}`}
                      />
                    </div>

                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>选择更新的最新航班状态</label>
                      <select
                        id="form_action_status"
                        className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} font-semibold`}
                      >
                        <option value="ON_TIME">准点运行 (ON_TIME)</option>
                        <option value="DELAYED">登记发生延误 (DELAYED)</option>
                        <option value="BOARDING">开始通知登机 (BOARDING)</option>
                        <option value="CANCELLED">航班取消 (CANCELLED)</option>
                      </select>
                    </div>
                  </>
                )}

                {showActionModal.id === 'schedule_maintenance_check' && (
                  <>
                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>目标飞机 (Aircraft Key)</label>
                      <input
                        type="text"
                        disabled
                        value={boundObject?.tailNumber || ''}
                        className={`w-full px-2 py-1.5 ${styles.sidebarBg} border ${styles.cardBorder} rounded-md font-bold font-mono text-xs ${styles.cardTextMuted}`}
                      />
                    </div>

                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>开始维护登记日期</label>
                      <input
                        type="date"
                        id="form_action_mdate"
                        defaultValue={new Date().toISOString().slice(0, 10)}
                        className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} font-mono`}
                      />
                    </div>
                  </>
                )}
              </div>

              <div className={`px-4 py-3 ${styles.appBg} border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
                <button
                  type="button"
                  onClick={() => setShowActionModal(null)}
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-md hover:${styles.sidebarBg} font-semibold`}
                >
                  取消
                </button>
                <button
                  onClick={() => {
                    if (showActionModal.id === 'update_flight_status') {
                      const sel = (document.getElementById('form_action_status') as HTMLSelectElement)?.value;
                      handleExecuteSimulatedAction('update_flight_status', {
                        flight_param: boundObject?.flightNumber,
                        new_status_param: sel
                      });
                    } else if (showActionModal.id === 'schedule_maintenance_check') {
                      const mDate = (document.getElementById('form_action_mdate') as HTMLInputElement)?.value;
                      handleExecuteSimulatedAction('schedule_maintenance_check', {
                        aircraft_param: boundObject?.tailNumber,
                        maintenance_date_param: mDate
                      });
                    }
                  }}
                  className="px-3.5 py-1.5 bg-slate-900 hover:bg-slate-800 text-white font-bold rounded-md flex items-center gap-1 transition-colors"
                >
                  <Check size={11} />
                  <span>提交 Action 并修改</span>
                </button>
              </div>
            </div>
          </div>
        );
      })()}
    </>
  );
}
