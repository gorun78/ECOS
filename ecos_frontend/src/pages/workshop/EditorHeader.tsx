import React from 'react';
import { ArrowLeft, FlaskConical, Palette, Play, Send, Settings } from 'lucide-react';
import { DynamicIcon } from './types';
import { SEED_APP_IDS } from './mockData';
import Badge from '../../components/common/Badge';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function renderEditorHeader(vm: any) {
  const { t, styles, activeApp, activePage, activePageId, setActivePageId, activeAppId, apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId, leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot, showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType, newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc, setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage, handleUpdateAppTheme, handlePublishApp, saveAppsState, showActionModal, setShowActionModal, flightsData, setFlightsData, aircraftData, setAircraftData, pilotsData, setPilotsData, handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig, handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction, getVarTypeBadge, getPrimaryColorClass } = vm;
  return (
    <>
          {/* Top Control Header bar */}
          <div className={`h-11 ${styles.sidebarBg} border-b ${styles.cardBorder} px-3 flex items-center justify-between shrink-0 select-none`}>
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setActiveAppId(null)}
                className={`p-1.5 ${styles.sidebarHoverBg} rounded-lg ${styles.cardTextMuted} transition-colors cursor-pointer`}
                title={t('aiworkbench.ws.ed.backToApps')}
              >
                <ArrowLeft size={15} />
              </button>
              <div className={`h-4 border-l ${styles.cardBorder}`} />
              <div>
                <input
                  type="text"
                  value={activeApp.name}
                  onChange={e => handleUpdateAppTheme({}, e.target.value)}
                  className={`font-bold text-xs ${styles.cardText} bg-transparent ${styles.sidebarHoverBg} focus:outline-hidden px-1.5 py-0.5 rounded-md transition-all font-sans w-64 border border-transparent`}
                />
              </div>
              <span className={`text-[10px] ${styles.cardTextMuted} ${styles.sidebarBg} px-1.5 py-0.5 rounded font-mono uppercase`}>
                {activeApp.id}
              </span>
              {SEED_APP_IDS.has(activeApp.id) && (
                <Badge variant="warning" className="shrink-0">
                  <FlaskConical size={10} aria-hidden />
                  <span>{t('aiworkbench.ws.vw.demoSeedApp')}</span>
                </Badge>
              )}
            </div>

            {/* Switchers for Edit Mode */}
            <div className={`flex items-center ${styles.sidebarBg} p-0.5 rounded-lg border ${styles.appBorder}`}>
              <button
                type="button"
                onClick={() => {
                  setEditorMode('design');
                  setSelectedWidgetId(null);
                }}
                className={`px-3 py-1 rounded-md text-[10px] font-bold flex items-center gap-1 transition-all cursor-pointer ${
                  editorMode === 'design' ? `${styles.cardBg} ${styles.cardText} shadow-xs` : `${styles.cardTextMuted}`
                }`}
              >
                <Palette size={11} />
                <span>{t('aiworkbench.ws.ed.designMode')}</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setEditorMode('preview');
                  setSelectedWidgetId(null);
                }}
                className={`px-3 py-1 rounded-md text-[10px] font-bold flex items-center gap-1 transition-all cursor-pointer ${
                  editorMode === 'preview' ? `${styles.cardBg} ${styles.cardText} shadow-xs` : `${styles.cardTextMuted}`
                }`}
              >
                <Play size={11} />
                <span>{t('aiworkbench.ws.ed.interactMode')}</span>
              </button>
            </div>

            {/* Draft Publish buttons — 发布通道未接入后端，明示「演示·仅本地」 */}
            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1">
                <span className={`w-1.5 h-1.5 rounded-full ${styles.successBg}`} />
                <span className={`text-[10px] ${styles.cardTextMuted} mr-2`}>{t('aiworkbench.ws.ed.draftAutosaved')}</span>
              </div>
              <Badge variant="warning" className="shrink-0">
                <FlaskConical size={10} aria-hidden />
                <span>{t('aiworkbench.ws.vw.publishLocalDemoTag')}</span>
              </Badge>
              <button
                type="button"
                onClick={handlePublishApp}
                className={`h-7 px-3.5 ${styles.accentBg} text-white font-bold rounded-md shadow-xs flex items-center gap-1 transition-colors cursor-pointer text-[11px]`}
              >
                <Send size={11} />
                <span>{t(activeApp.isPublished ? 'aiworkbench.ws.ed.unpublishApp' : 'aiworkbench.ws.ed.publishApp')}</span>
              </button>
            </div>
          </div>

    </>
  );
}
