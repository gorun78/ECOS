import React from 'react';
import { Plus, Trash2, MousePointerClick } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, PieChart, Pie, Cell } from 'recharts';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderCenterCharts(vm: any) {
  const { t, styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass, showToast } = vm;
  return (
    <>
                  <div className="lg:col-span-6 space-y-4">
                    {/* Charts slot */}
                    {activePage?.widgets.filter((w: any) => w.slot === 'main_middle').map((w: any) => {
                      const isSelected = w.id === selectedWidgetId;
                      const simData = getSimulatedFlights();
                      
                      // Aggregation for chart series
                      const groupKey = w.config.groupByProperty || 'status';
                      
                      // Count occurrences of each value for the groupKey
                      const counts: Record<string, number> = {};
                      simData.forEach((item: any) => {
                        const val = item[groupKey] || 'UNKNOWN';
                        const stKey = val.toLowerCase();
                        const label = (['on_time', 'delayed', 'boarding', 'cancelled'].includes(stKey))
                          ? t(`aiworkbench.ws.ct.status.${stKey}`)
                          : val;

                        counts[label] = (counts[label] || 0) + 1;
                      });

                      const chartData = Object.entries(counts).map(([name, value]) => ({
                        name,
                        value
                      }));

                      if (chartData.length === 0) {
                        chartData.push({ name: t('aiworkbench.ws.ct.noData'), value: 0 });
                      }

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
                              <span>Chart</span>
                              <button type="button" onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-4 text-[11px]`}>{w.title}</h3>
                          
                          <div className="h-44 w-full">
                            <ResponsiveContainer width="100%" height="100%">
                              {w.config.chartType === 'line' ? (
                                <LineChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                                  <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                                  <XAxis dataKey="name" stroke="#94a3b8" fontSize={10} />
                                  <YAxis stroke="#94a3b8" fontSize={10} />
                                  <Tooltip />
                                  <Line type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={2} activeDot={{ r: 6 }} />
                                </LineChart>
                              ) : w.config.chartType === 'pie' ? (
                                <PieChart>
                                  <Pie
                                    data={chartData}
                                    cx="50%"
                                    cy="50%"
                                    innerRadius={40}
                                    outerRadius={60}
                                    paddingAngle={3}
                                    dataKey="value"
                                  >
                                    {chartData.map((entry: any, index: any) => (
                                      <Cell key={`cell-${index}`} fill={['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'][index % 5]} />
                                    ))}
                                  </Pie>
                                  <Tooltip />
                                </PieChart>
                              ) : (
                                <BarChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                                  <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                                  <XAxis dataKey="name" stroke="#94a3b8" fontSize={10} />
                                  <YAxis stroke="#94a3b8" fontSize={10} />
                                  <Tooltip />
                                  <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                                </BarChart>
                              )}
                            </ResponsiveContainer>
                          </div>
                        </div>
                      );
                    })}

                    {editorMode === 'design' && activePage?.widgets.filter((w: any) => w.slot === 'main_middle').length === 0 && (
                      <button
                        type="button"
                        onClick={() => { setAddWidgetSlot('main_middle'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed ${styles.divider} rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} transition-all cursor-pointer min-h-[120px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">{t('aiworkbench.ws.ct.addChart')}</span>
                      </button>
                    )}

                    {/* Table Slot */}
                    {activePage?.widgets.filter((w: any) => w.slot === 'main_bottom').map((w: any) => {
                      const isSelected = w.id === selectedWidgetId;
                      const boundSource = w.config.dataSourceVarId;
                      const boundTarget = w.config.targetVarId;

                      // Source data check (is it aircraft table or flight table?)
                      const isAircraftTable = activeApp.id === 'aircraft_maintenance' || w.id === 'w_aircraft_table';
                      const rowData = isAircraftTable ? aircraftData : getSimulatedFlights();
                      const activeSelection = getVarValue(boundTarget);

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
                              <span>Table</span>
                              <button type="button" onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          <div className={`overflow-x-auto rounded-lg border ${styles.cardBorder}`}>
                            <table className="w-full text-left border-collapse">
                              <thead>
                                <tr className={`${styles.appBg}/50 text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider border-b ${styles.cardBorder}`}>
                                  {isAircraftTable ? (
                                    <>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.tailNumber')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.model')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.manufacturer')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.airworthiness')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.lastMaintenance')}</th>
                                    </>
                                  ) : (
                                    <>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.flightNumber')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.tailNumber')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.depAirport')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.arrAirport')}</th>
                                      <th className="py-2 px-3">{t('aiworkbench.ws.ct.th.status')}</th>
                                    </>
                                  )}
                                </tr>
                              </thead>
                              <tbody className={`divide-y ${styles.divider} text-[11px]`}>
                                {rowData.map((row: any, idx: any) => {
                                  const key = isAircraftTable ? row.tailNumber : row.flightNumber;
                                  const isRowSelected = activeSelection && (isAircraftTable ? activeSelection.tailNumber === key : activeSelection.flightNumber === key);
                                  
                                  return (
                                    <tr
                                      key={key || idx}
                                      onClick={() => {
                                        if (editorMode === 'preview' && boundTarget) {
                                          handleVariableChange(boundTarget, row);
                                          showToast('info', t('aiworkbench.ws.ct.selectedEntity', { key }));
                                        }
                                      }}
                                      className={`hover:${styles.appBg} transition-colors ${
                                        editorMode === 'preview' ? 'cursor-pointer' : ''
                                      } ${isRowSelected ? 'bg-blue-50 text-blue-700 font-bold' : '${styles.cardTextMuted}'}`}
                                    >
                                      {isAircraftTable ? (
                                        <>
                                          <td className="py-2 px-3 font-mono">{row.tailNumber}</td>
                                          <td className="py-2 px-3">{row.model}</td>
                                          <td className="py-2 px-3">{row.manufacturer}</td>
                                          <td className="py-2 px-3">
                                            <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold ${
                                              row.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700 border border-emerald-100' :
                                              row.status === 'MAINTENANCE' ? 'bg-amber-50 text-amber-700 border border-amber-100' : 'bg-rose-50 text-rose-700'
                                            }`}>
                                              {row.status}
                                            </span>
                                          </td>
                                          <td className="py-2 px-3 font-mono">{row.lastMaintenance}</td>
                                        </>
                                      ) : (
                                        <>
                                          <td className={`py-2 px-3 font-bold font-mono ${styles.cardText}`}>{row.flightNumber}</td>
                                          <td className="py-2 px-3 font-mono">{row.tailNumber}</td>
                                          <td className="py-2 px-3 font-mono">{row.depAirport}</td>
                                          <td className="py-2 px-3 font-mono">{row.arrAirport}</td>
                                          <td className="py-2 px-3">
                                            <span className={`px-2 py-0.5 rounded-full text-[9px] font-bold ${
                                              row.status === 'ON_TIME' ? 'bg-emerald-50 text-emerald-700 border border-emerald-100' :
                                              row.status === 'DELAYED' ? 'bg-red-50 text-red-700 border border-red-100' : '${styles.sidebarBg} ${styles.cardText}'
                                            }`}>
                                              {row.status === 'ON_TIME' ? t('aiworkbench.ws.ct.status.on_time') : row.status === 'DELAYED' ? t('aiworkbench.ws.ct.status.delayed') : row.status}
                                            </span>
                                          </td>
                                        </>
                                      )}
                                    </tr>
                                  );
                                })}
                                {rowData.length === 0 && (
                                  <tr>
                                    <td colSpan={5} className={`py-6 text-center ${styles.cardTextMuted}`}>{t('aiworkbench.ws.ct.emptyFilterMatch')}</td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      );
                    })}

                    {editorMode === 'design' && activePage?.widgets.filter((w: any) => w.slot === 'main_bottom').length === 0 && (
                      <button
                        type="button"
                        onClick={() => { setAddWidgetSlot('main_bottom'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed ${styles.divider} rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} transition-all cursor-pointer min-h-[140px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">{t('aiworkbench.ws.ct.addTable')}</span>
                      </button>
                    )}
                  </div>
    </>
  );
}
