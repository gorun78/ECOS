import React from 'react';
import { useTheme } from '../../components/ThemeContext';
import { ArrowLeft, Play, Settings } from 'lucide-react';
import { DynamicIcon } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderEditorHeader(vm: any) {
  const { styles } = useTheme();
  const { styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
          {/* Top Control Header bar */}
          <div className={`h-11 ${styles.sidebarBg} border-b ${styles.cardBorder} px-3 flex items-center justify-between shrink-0 select-none`}>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setActiveAppId(null)}
                className={`p-1.5 hover:bg-slate-200 rounded-lg ${styles.cardTextMuted} transition-colors cursor-pointer`}
                title="返回应用清单"
              >
                <ArrowLeft size={15} />
              </button>
              <div className="h-4 w-px bg-slate-300" />
              <div>
                <input
                  type="text"
                  value={activeApp.name}
                  onChange={e => handleUpdateAppTheme({}, e.target.value)}
                  className={`font-bold text-xs ${styles.cardText} bg-transparent hover:bg-slate-200/50 focus:${styles.cardBg} focus:outline-hidden px-1.5 py-0.5 rounded-md transition-all font-sans w-64 border border-transparent focus:border-slate-300`}
                />
              </div>
              <span className={`text-[10px] ${styles.cardTextMuted} bg-slate-200/60 px-1.5 py-0.5 rounded font-mono uppercase`}>
                {activeApp.id}
              </span>
            </div>

            {/* Switchers for Edit Mode */}
            <div className="flex items-center bg-slate-200 p-0.5 rounded-lg border border-slate-300/40">
              <button
                onClick={() => {
                  setEditorMode('design');
                  setSelectedWidgetId(null);
                }}
                className={`px-3 py-1 rounded-md text-[10px] font-bold flex items-center gap-1 transition-all cursor-pointer ${
                  editorMode === 'design' ? '${styles.cardBg} ${styles.cardText} shadow-xs' : '${styles.cardTextMuted} hover:${styles.cardText}'
                }`}
              >
                <Palette size={11} />
                <span>设计与布局 (Design)</span>
              </button>
              <button
                onClick={() => {
                  setEditorMode('preview');
                  setSelectedWidgetId(null);
                }}
                className={`px-3 py-1 rounded-md text-[10px] font-bold flex items-center gap-1 transition-all cursor-pointer ${
                  editorMode === 'preview' ? '${styles.cardBg} ${styles.cardText} shadow-xs' : '${styles.cardTextMuted} hover:${styles.cardText}'
                }`}
              >
                <Play size={11} />
                <span>运行预览 (Interact)</span>
              </button>
            </div>

            {/* Draft Publish buttons */}
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                <span className={`text-[10px] ${styles.cardTextMuted} mr-2`}>草稿已自动保存</span>
              </div>
              <button
                onClick={handlePublishApp}
                className="h-7 px-3.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-md shadow-xs flex items-center gap-1 transition-colors cursor-pointer text-[11px]"
              >
                <Send size={11} />
                <span>发布应用 (Publish)</span>
              </button>
            </div>
          </div>

    </>
  );
}
