/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useTheme } from "../components/ThemeContext";

import type { WorkshopApp, WorkshopWidget } from './workshop/types';
import { initialApps } from './workshop/mockData';
import { handleAipCommand as aipHandler } from './workshop/aipHandler';
import { useWidgetOps } from './workshop/useWidgetOps';
import { renderAppListScreen } from './workshop/AppListScreen';
import { renderEditorHeader } from './workshop/EditorHeader';
import { renderLeftSidebar } from './workshop/LeftSidebar';
import { renderCenterCanvas } from './workshop/CenterCanvas';
import { renderCenterFilters } from './workshop/CenterFilters';
import { renderCenterCharts } from './workshop/CenterCharts';
import { renderCenterAside } from './workshop/CenterAside';
import { renderWidgetInspector } from './workshop/WidgetInspector';
import { renderWorkshopModals } from './workshop/WorkshopModals';

export type { WorkshopApp, WorkshopWidget, WorkshopVariable } from './workshop/types';

export default function WorkshopView({ showToast: propShowToast }: { showToast?: (type: 'success' | 'info' | 'error', message: string) => void }) {
  const { styles } = useTheme();
  const showToast = propShowToast || ((type: string, msg: string) => console.log(`[Workshop ${type}]: ${msg}`));

  const [apps, setApps] = useState<WorkshopApp[]>(() => {
    const cached = localStorage.getItem('ecos_workshop_apps');
    return cached ? JSON.parse(cached) : initialApps;
  });
  /**
   * Persistence for the workshop app list. Must be declared before use in
   * useWidgetOps / aipHandler callbacks, hence hoisted above the earlier
   * hook call sites (Wave-9 reorder preorder fix).
   */
  const saveAppsState = (updated: WorkshopApp[]) => {
    setApps(updated);
    localStorage.setItem('ecos_workshop_apps', JSON.stringify(updated));
  };

  const [activeAppId, setActiveAppId] = useState<string | null>(null);
  const [activePageId, setActivePageId] = useState<string>('');
  const [editorMode, setEditorMode] = useState<'design' | 'preview'>('design');
  const [selectedWidgetId, setSelectedWidgetId] = useState<string | null>(null);
  const [leftTab, setLeftTab] = useState<'pages' | 'variables' | 'widgets' | 'theme'>('pages');
  const [showAddWidgetModal, setShowAddWidgetModal] = useState(false);
  const [addWidgetSlot, setAddWidgetSlot] = useState<string>('');
  const [showAddVarModal, setShowAddVarModal] = useState(false);
  const [newVarName, setNewVarName] = useState('');
  const [newVarType, setNewVarType] = useState<'object_set' | 'object' | 'string' | 'number' | 'boolean'>('string');
  const [newVarObjType, setNewVarObjType] = useState('flight');
  const [newVarDesc, setNewVarDesc] = useState('');

  const activeApp = apps.find(a => a.id === activeAppId);
  const activePage = activeApp?.pages.find(p => p.id === activePageId);

  const widgetOps = useWidgetOps(
    apps,
    activeApp ?? null,
    activePage ?? null,
    saveAppsState,
    showToast as any,
    {
      newVarName, setNewVarName,
      newVarDesc, setNewVarDesc,
      newVarType, setNewVarType,
      newVarObjType, setNewVarObjType,
      showAddVarModal, setShowAddVarModal,
      showAddWidgetModal, setShowAddWidgetModal,
      addWidgetSlot, setAddWidgetSlot,
      selectedWidgetId, setSelectedWidgetId,
      activePageId, setActivePageId,
      persistApps: saveAppsState,
    },
  );

  const handleAipCommand = useCallback((e: Event) => {
    aipHandler(e, apps, activeAppId, activePageId || null, {
      saveAppsState,
      setActiveAppId,
      setActivePageId,
      setEditorMode,
      setSelectedWidgetId,
      setLeftTab: (tab: string) => setLeftTab(tab as 'theme' | 'pages' | 'widgets' | 'variables'),
    });
  }, [apps, activeAppId, activePageId]);

  useEffect(() => {
    window.addEventListener('workshop-aip-command', handleAipCommand as any);
    return () => window.removeEventListener('workshop-aip-command', handleAipCommand as any);
  }, [handleAipCommand]);

  const handleCreateNewApp = () => {
    const newApp: WorkshopApp = {
      id: 'app_' + Date.now(), name: '新建应用', description: '点击编辑应用描述...',
      lastModified: new Date().toISOString().slice(0, 16).replace('T', ' '),
      isPublished: false,
      theme: { primaryColor: 'indigo', isDark: false, title: 'New Workshop', logo: 'LayoutGrid' },
      pages: [{ id: 'p1', title: '首页', icon: 'LayoutDashboard', widgets: [] }],
      variables: [],
    };
    saveAppsState([...apps, newApp]);
    setActiveAppId(newApp.id); setActivePageId('p1'); setSelectedWidgetId(null); setEditorMode('design');
  };

  const handleDeleteApp = (id: string, name: string) => {
    if (!confirm(`确定删除应用「${name}」吗？`)) return;
    saveAppsState(apps.filter(a => a.id !== id));
    if (activeAppId === id) setActiveAppId(null);
  };

  const handleAddPage = () => {
    if (!activeApp) return;
    const newPage: { id: string; title: string; icon: string; widgets: WorkshopWidget[] } = { id: 'p_' + Date.now(), title: '新页面', icon: 'LayoutGrid', widgets: [] };
    saveAppsState(apps.map(a => a.id === activeApp.id ? { ...a, pages: [...a.pages, newPage] } : a));
    setActivePageId(newPage.id);
  };

  const handleUpdateAppTheme = (fields: Partial<WorkshopApp['theme']>, name?: string, desc?: string) => {
    if (!activeApp) return;
    saveAppsState(apps.map(a => a.id === activeApp.id ? {
      ...a, theme: { ...a.theme, ...fields }, name: name ?? a.name, description: desc ?? a.description,
      lastModified: new Date().toISOString().slice(0, 16).replace('T', ' '),
    } : a));
  };

  const handlePublishApp = () => {
    if (!activeApp) return;
    saveAppsState(apps.map(a => a.id === activeApp.id ? { ...a, isPublished: !a.isPublished } : a));
    showToast('success', activeApp.isPublished ? '应用已下架' : '应用已发布');
  };

  const vm = {
    styles, activeApp, activePage, activePageId, setActivePageId, activeAppId,
    apps, editorMode, setEditorMode, selectedWidgetId, setSelectedWidgetId,
    leftTab, setLeftTab, showAddWidgetModal, setShowAddWidgetModal, addWidgetSlot, setAddWidgetSlot,
    showAddVarModal, setShowAddVarModal, newVarName, setNewVarName, newVarType, setNewVarType,
    newVarObjType, setNewVarObjType, newVarDesc, setNewVarDesc,
    setActiveAppId, handleCreateNewApp, handleDeleteApp, handleAddPage,
    handleUpdateAppTheme, handlePublishApp, saveAppsState,
    onOpenApp: (app: WorkshopApp) => { setActiveAppId(app.id); setActivePageId(app.pages[0]?.id || ''); setSelectedWidgetId(null); setEditorMode('design'); },
    ...widgetOps,
  };

  return (
    <div className={`flex-1 flex flex-col h-full overflow-hidden ${styles.appBg} select-none`}>
      {activeAppId === null ? (
        renderAppListScreen(vm)
      ) : (
        <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
          {renderEditorHeader(vm)}
          <div className="flex-1 flex overflow-hidden">
            {renderLeftSidebar(vm)}
            <div className={`flex-1 flex flex-col overflow-hidden ${activeApp?.theme.isDark ? 'bg-slate-950 text-slate-100' : `${styles.sidebarBg} ${styles.cardText}`}`}>
              {renderCenterCanvas(vm)}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
                {renderCenterFilters(vm)}
                {renderCenterCharts(vm)}
                {renderCenterAside(vm)}
              </div>
            </div>
          </div>
          {renderWidgetInspector(vm)}
          {renderWorkshopModals(vm)}
        </div>
      )}
    </div>
  );
}
