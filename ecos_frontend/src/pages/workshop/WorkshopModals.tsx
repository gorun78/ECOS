import React from 'react';
import { Check, Plus, PlusCircle, Zap, Settings } from 'lucide-react';
import { DynamicIcon, mockActionTypes } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderWorkshopModals(vm: any) {
  const { styles, t, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;

  // Widget catalog: title/desc resolved via t() at render, not stored raw
  const widgets = (
    [
      { type: 'table', icon: 'TableProperties' },
      { type: 'chart', icon: 'BarChart3' },
      { type: 'metric', icon: 'Hash' },
      { type: 'object_view', icon: 'FileText' },
      { type: 'action_button', icon: 'Zap' },
      { type: 'filter_bar', icon: 'SlidersHorizontal' }
    ] as { type: string; icon: string }[]
  ).map((item) => ({
    ...item,
    title: t(`aiworkbench.ws.addWidget.widget.${item.type}.t`),
    desc: t(`aiworkbench.ws.addWidget.widget.${item.type}.d`),
  }));

  return (
    <>
      {/* 4. MODAL: ADD WIDGET */}
      {showAddWidgetModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-lg overflow-hidden flex flex-col`}>
            <div className={`px-4 py-3 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
              <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
                <PlusCircle size={14} className="text-blue-500" />
                <span>{t('aiworkbench.ws.addWidget.title', { slot: addWidgetSlot })}</span>
              </h3>
              <button type="button" onClick={() => setShowAddWidgetModal(false)} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} text-sm font-bold`}>×</button>
            </div>

            <div className="p-4 grid grid-cols-2 gap-3 max-h-[350px] overflow-y-auto">
              {widgets.map((item: any) => (
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs">
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-sm overflow-hidden`}>
            <form onSubmit={handleAddVariable}>
              <div className={`px-4 py-3 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
                <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
                  <Settings size={14} className="text-blue-500" />
                  <span>{t('aiworkbench.ws.addVar.title')}</span>
                </h3>
                <button type="button" onClick={() => setShowAddVarModal(false)} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} text-sm font-bold`}>×</button>
              </div>

              <div className="p-4 space-y-3">
                <div className="space-y-1">
                  <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.addVar.nameLabel')}</label>
                  <input
                    type="text"
                    value={newVarName}
                    onChange={e => setNewVarName(e.target.value)}
                    placeholder={t('aiworkbench.ws.addVar.namePh')}
                    className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs focus:outline-hidden`}
                    required
                  />
                </div>

                <div className="space-y-1">
                  <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.addVar.typeLabel')}</label>
                  <select
                    value={newVarType}
                    onChange={e => setNewVarType(e.target.value as any)}
                    className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                  >
                    <option value="string">{t('aiworkbench.ws.addVar.vt.string')}</option>
                    <option value="number">{t('aiworkbench.ws.addVar.vt.number')}</option>
                    <option value="object_set">{t('aiworkbench.ws.addVar.vt.object_set')}</option>
                    <option value="object">{t('aiworkbench.ws.addVar.vt.object')}</option>
                  </select>
                </div>

                {['object_set', 'object'].includes(newVarType) && (
                  <div className="space-y-1">
                    <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.addVar.oTypeLabel')}</label>
                    <select
                      value={newVarObjType}
                      onChange={e => setNewVarObjType(e.target.value)}
                      className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg}`}
                    >
                      <option value="flight">{t('aiworkbench.ws.addVar.ot.flight')}</option>
                      <option value="aircraft">{t('aiworkbench.ws.addVar.ot.aircraft')}</option>
                      <option value="pilot">{t('aiworkbench.ws.addVar.ot.pilot')}</option>
                    </select>
                  </div>
                )}

                <div className="space-y-1">
                  <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>{t('aiworkbench.ws.addVar.descLabel')}</label>
                  <input
                    type="text"
                    value={newVarDesc}
                    onChange={e => setNewVarDesc(e.target.value)}
                    placeholder={t('aiworkbench.ws.addVar.descPh')}
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
                  {t('common.cancel')}
                </button>
                <button type="submit" className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-md">
                  {t('aiworkbench.ws.addVar.confirmAdd')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 6. MODAL: RUN SIMULATED ACTION TYPE */}
      {showActionModal && (() => {
        const boundObject = activeApp ? activeApp.variables.find((v: any) => v.type === 'object')?.value : null;

        return (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs">
            <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-sm overflow-hidden`}>
              <div className={`px-4 py-3 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
                <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
                  <span className="p-1 rounded bg-amber-500 text-white">
                    <Zap size={12} className="fill-white/20" />
                  </span>
                  <span>{t('aiworkbench.ws.action.title', { name: showActionModal.displayName })}</span>
                </h3>
                <button type="button" onClick={() => setShowActionModal(null)} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} text-sm font-bold`}>×</button>
              </div>

              <div className="p-4 space-y-3">
                <p className={`text-[10px] ${styles.cardTextMuted} italic leading-relaxed border-b ${styles.cardBorder} pb-2`}>{showActionModal.description}</p>

                {/* Dynamically prompt parameter form based on bound action type */}
                {showActionModal.id === 'update_flight_status' && (
                  <>
                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>{t('aiworkbench.ws.action.flightKeyLabel')}</label>
                      <input
                        type="text"
                        disabled
                        value={boundObject?.flightNumber || ''}
                        className={`w-full px-2 py-1.5 ${styles.sidebarBg} border ${styles.cardBorder} rounded-md font-bold font-mono text-xs ${styles.cardTextMuted}`}
                      />
                    </div>

                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>{t('aiworkbench.ws.action.statusLabel')}</label>
                      <select
                        id="form_action_status"
                        className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md text-xs ${styles.cardBg} font-semibold`}
                      >
                        <option value="ON_TIME">{t('aiworkbench.ws.action.status.on_time')}</option>
                        <option value="DELAYED">{t('aiworkbench.ws.action.status.delayed')}</option>
                        <option value="BOARDING">{t('aiworkbench.ws.action.status.boarding')}</option>
                        <option value="CANCELLED">{t('aiworkbench.ws.action.status.cancelled')}</option>
                      </select>
                    </div>
                  </>
                )}

                {showActionModal.id === 'schedule_maintenance_check' && (
                  <>
                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>{t('aiworkbench.ws.action.aircraftKeyLabel')}</label>
                      <input
                        type="text"
                        disabled
                        value={boundObject?.tailNumber || ''}
                        className={`w-full px-2 py-1.5 ${styles.sidebarBg} border ${styles.cardBorder} rounded-md font-bold font-mono text-xs ${styles.cardTextMuted}`}
                      />
                    </div>

                    <div className="space-y-1">
                      <label className={`${styles.cardTextMuted} font-semibold text-[10px] block`}>{t('aiworkbench.ws.action.mdateLabel')}</label>
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
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
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
                  className="px-3.5 py-1.5 bg-[var(--card,#020617)] hover:bg-[var(--card,#0B1120)] text-white font-bold rounded-md flex items-center gap-1 transition-colors"
                >
                  <Check size={11} />
                  <span>{t('aiworkbench.ws.action.submit')}</span>
                </button>
              </div>
            </div>
          </div>
        );
      })()}
    </>
  );
}
