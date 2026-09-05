import React from 'react';
import { LayoutGrid, Plus, Trash2 } from 'lucide-react';
import { DynamicIcon } from './types';
import type { WorkshopApp } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderAppListScreen(vm: any) {
  const { styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
        <div className="flex-1 overflow-y-auto p-8 max-w-5xl mx-auto w-full space-y-6">
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-4`}>
            <div>
              <h1 className={`text-xl font-bold ${styles.cardText} tracking-tight flex items-center gap-2`}>
                <span className="p-2 rounded-lg bg-blue-600 text-white">
                  <LayoutGrid size={18} />
                </span>
                <span>Workshop 应用构建中心</span>
              </h1>
              <p className={`${styles.cardTextMuted} mt-1 text-xs`}>
                通过直接绑定企业本体数据、关联链条及操作实体，进行可视化拼装交互式前端应用程序。
              </p>
            </div>
            <button
              onClick={handleCreateNewApp}
              className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white font-bold rounded-lg shadow-sm flex items-center gap-1.5 transition-all cursor-pointer text-xs"
            >
              <Plus size={14} />
              新建 Workshop 应用
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {apps.map((app: any) => (
              <div
                key={app.id}
                onClick={() => {
                  setActiveAppId(app.id);
                  setActivePageId(app.pages[0]?.id || '');
                  setSelectedWidgetId(null);
                  setEditorMode('design');
                }}
                className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-5 hover:border-blue-500 hover:shadow-md transition-all cursor-pointer flex flex-col justify-between h-44 relative group`}
              >
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <span className={`p-2 rounded-lg bg-${app.theme.primaryColor}-50 text-${app.theme.primaryColor}-600 border border-${app.theme.primaryColor}-100`}>
                      <DynamicIcon name={app.theme.logo || 'LayoutGrid'} size={16} />
                    </span>
                    <div className="flex items-center gap-2">
                      {app.isPublished ? (
                        <span className="bg-emerald-50 text-emerald-700 text-[10px] px-2 py-0.5 rounded-full font-bold border border-emerald-100">
                          已发布 (Active)
                        </span>
                      ) : (
                        <span className="bg-amber-50 text-amber-700 text-[10px] px-2 py-0.5 rounded-full font-bold border border-amber-100">
                          本地草稿
                        </span>
                      )}
                    </div>
                  </div>
                  <h3 className={`font-bold ${styles.cardText} text-sm group-hover:text-blue-600 transition-colors`}>
                    {app.name}
                  </h3>
                  <p className={`${styles.cardTextMuted} mt-1.5 text-xs line-clamp-2 leading-relaxed`}>
                    {app.description}
                  </p>
                </div>

                <div className={`flex items-center justify-between border-t ${styles.cardBorder} pt-3 text-[10px] ${styles.cardTextMuted} font-mono`}>
                  <span>更新于 {app.lastModified}</span>
                  <div className="flex items-center gap-1.5" onClick={e => e.stopPropagation()}>
                    <button
                      onClick={() => handleDeleteApp(app.id, app.name)}
                      className={`p-1.5 hover:bg-red-50 hover:text-red-600 rounded-md ${styles.cardTextMuted} transition-colors`}
                      title="删除应用"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
    </>
  );
}
