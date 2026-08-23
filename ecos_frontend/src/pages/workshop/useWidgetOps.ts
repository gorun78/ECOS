import { useState, useCallback } from 'react';
import type { WorkshopApp, WorkshopWidget, WorkshopVariable } from './types';

// Widget operations hook — extracted from WorkshopView for file size compliance
export function useWidgetOps(
  apps: WorkshopApp[],
  activeAppId: string | null,
  setApps: (apps: WorkshopApp[]) => void,
  showToast: (type: 'success' | 'info' | 'error', message: string) => void,
) {
  const [showActionModal, setShowActionModal] = useState<any>(null);
  const [flightsData, setFlightsData] = useState<any[]>([]);
  const [aircraftData, setAircraftData] = useState<any[]>([]);
  const [pilotsData, setPilotsData] = useState<any[]>([]);

  const handleAddVariable = (e: React.FormEvent) => {
e.preventDefault();
if (!newVarName.trim() || !activeApp) return;

const varId = `v_var_${Date.now().toString().slice(-4)}`;
const newVar: WorkshopVariable = {
  id: varId,
  name: newVarName.trim(),
  type: newVarType,
  objectTypeId: newVarType === 'object_set' || newVarType === 'object' ? newVarObjType : undefined,
  initialValue: newVarType === 'object_set' ? 'all' : null,
  value: newVarType === 'object_set' ? 'all' : null,
  description: newVarDesc.trim() || '自定义变量，为应用内组件交互传递状态。'
};

const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return { ...a, variables: [...a.variables, newVar] };
  }
  return a;
});

saveAppsState(updatedApps);
setShowAddVarModal(false);
setNewVarName('');
setNewVarDesc('');
showToast('success', `变量「${newVar.name}」添加成功！`);
  };

  // Widget management
  const handleAddWidget = (type: WorkshopWidget['type']) => {
if (!activeApp || !activePage) return;

const widgetId = `w_${type}_${Date.now().toString().slice(-4)}`;
const newWidget: WorkshopWidget = {
  id: widgetId,
  type,
  title: `${type === 'table' ? '本体表格明细' : type === 'chart' ? '分析图表' : type === 'metric' ? '数据统计指标' : type === 'object_view' ? '对象卡片' : type === 'action_button' ? '执行操作按钮' : '过滤器面板'} ${Date.now().toString().slice(-4)}`,
  slot: addWidgetSlot,
  config: {
    dataSourceVarId: activeApp.variables[0]?.id,
    targetVarId: activeApp.variables.find(v => v.type === 'object')?.id
  }
};

const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return {
      ...a,
      pages: a.pages.map(p => {
        if (p.id === activePage.id) {
          return { ...p, widgets: [...p.widgets, newWidget] };
        }
        return p;
      })
    };
  }
  return a;
});

saveAppsState(updatedApps);
setShowAddWidgetModal(false);
setSelectedWidgetId(widgetId);
showToast('success', `组件已添加至 ${addWidgetSlot} 布局槽中！`);
  };

  const handleDeleteWidget = (widgetId: string) => {
if (!activeApp || !activePage) return;
if (!window.confirm('确定要移除此应用组件吗？')) return;

const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return {
      ...a,
      pages: a.pages.map(p => {
        if (p.id === activePage.id) {
          return { ...p, widgets: p.widgets.filter(w => w.id !== widgetId) };
        }
        return p;
      })
    };
  }
  return a;
});

saveAppsState(updatedApps);
if (selectedWidgetId === widgetId) setSelectedWidgetId(null);
showToast('info', '组件已从画布中移除。');
  };

  const handleUpdateWidgetConfig = (config: Partial<WorkshopWidget['config']>, title?: string) => {
if (!activeApp || !activePage || !selectedWidgetId) return;

const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return {
      ...a,
      pages: a.pages.map(p => {
        if (p.id === activePage.id) {
          return {
            ...p,
            widgets: p.widgets.map(w => {
              if (w.id === selectedWidgetId) {
                return {
                  ...w,
                  title: title !== undefined ? title : w.title,
                  config: { ...w.config, ...config }
                };
              }
              return w;
            })
          };
        }
        return p;
      })
    };
  }
  return a;
});

saveAppsState(updatedApps);
  };

  // Layout Pages management
  const handleAddPage = () => {
if (!activeApp) return;
const pageId = `p_page_${Date.now().toString().slice(-4)}`;
const newPage: WorkshopPage = {
  id: pageId,
  title: `未命名仪表盘_${Date.now().toString().slice(-3)}`,
  icon: 'LayoutGrid',
  widgets: []
};

const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return { ...a, pages: [...a.pages, newPage] };
  }
  return a;
});

saveAppsState(updatedApps);
setActivePageId(pageId);
showToast('success', `创建了新页面：${newPage.title}`);
  };

  const handleUpdateAppTheme = (fields: Partial<WorkshopApp['theme']>, name?: string, desc?: string) => {
if (!activeApp) return;

const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return {
      ...a,
      name: name || a.name,
      description: desc || a.description,
      theme: { ...a.theme, ...fields }
    };
  }
  return a;
});

saveAppsState(updatedApps);
  };

  // Run/Publish transactions
  const handlePublishApp = () => {
if (!activeApp) return;
const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return { ...a, isPublished: true, lastModified: new Date().toISOString().replace('T', ' ').slice(0, 16) };
  }
  return a;
});
saveAppsState(updatedApps);
showToast('success', '🚀 应用已成功编译发布！外部组织成员可以通过共享URL进行安全访问。');
  };

  // ==========================================
  // 3. Simulated Engine Core (Interact Preview)
  // ==========================================
  const handleVariableChange = (varId: string, value: any) => {
if (!activeApp) return;
const updatedApps = apps.map(a => {
  if (a.id === activeApp.id) {
    return {
      ...a,
      variables: a.variables.map(v => v.id === varId ? { ...v, value } : v)
    };
  }
  return a;
});
setApps(updatedApps);
  };

  // Get current active variable values
  const getVarValue = (varId?: string) => {
if (!activeApp || !varId) return null;
return activeApp.variables.find(v => v.id === varId)?.value;
  };

  // Get filtered aviation records based on active variables in the runtime simulation
  const getSimulatedFlights = () => {
const filterStatus = getVarValue('v_filter_status') || 'ALL';
const filterAirport = getVarValue('v_filter_airport') || 'ALL';

return flightsData.filter(flight => {
  const matchStatus = filterStatus === 'ALL' || flight.status === filterStatus;
  const matchAirport = filterAirport === 'ALL' || flight.depAirport === filterAirport || flight.arrAirport === filterAirport;
  return matchStatus && matchAirport;
});
  };

  // Interactive submit of action form inside preview
  const handleExecuteSimulatedAction = (actionId: string, formData: Record<string, any>) => {
if (actionId === 'update_flight_status') {
  const flightNum = formData.flight_param;
  const newStatus = formData.new_status_param;

  if (!flightNum || !newStatus) return;

  const updatedFlights = flightsData.map(f => {
    if (f.flightNumber === flightNum) {
      return { ...f, status: newStatus };
    }
    return f;
  });

  setFlightsData(updatedFlights);
  localStorage.setItem('workshop_sim_flights', JSON.stringify(updatedFlights));

  // Trigger change to sync the selected flight detail object card
  const selectedFlightVar = activeApp?.variables.find(v => v.id === 'v_selected_flight');
  if (selectedFlightVar?.value?.flightNumber === flightNum) {
    handleVariableChange('v_selected_flight', { ...selectedFlightVar.value, status: newStatus });
  }

  showToast('success', `操作成功！航班 ${flightNum} 状态已变更为 ${newStatus}`);
} 

else if (actionId === 'schedule_maintenance_check') {
  const tailNum = formData.aircraft_param;
  const mDate = formData.maintenance_date_param;

  if (!tailNum || !mDate) return;

  const updatedAc = aircraftData.map(a => {
    if (a.tailNumber === tailNum) {
      return { ...a, status: 'MAINTENANCE', lastMaintenance: mDate };
    }
    return a;
  });

  setAircraftData(updatedAc);
  localStorage.setItem('workshop_sim_aircraft', JSON.stringify(updatedAc));

  const selectedAcVar = activeApp?.variables.find(v => v.id === 'v_selected_ac');
  if (selectedAcVar?.value?.tailNumber === tailNum) {
    handleVariableChange('v_selected_ac', { ...selectedAcVar.value, status: 'MAINTENANCE', lastMaintenance: mDate });
  }

  showToast('success', `操作成功！飞机 ${tailNum} 已开始进入适航维护并录入登记表。`);
}

setShowActionModal(null);
  };

  // Render variables mapping colors for badges
  const getVarTypeBadge = (type: string) => {
switch (type) {
  case 'object_set': return 'bg-purple-100 text-purple-700 border-purple-200';
  case 'object': return 'bg-blue-100 text-blue-700 border-blue-200';
  default: return 'bg-slate-100 text-slate-700 border-slate-200';
}
  };

  // Helper to resolve tailwind theme primary colors
  const getPrimaryColorClass = (color: string) => {
switch (color) {
  case 'blue': return 'bg-blue-600 text-white hover:bg-blue-500 hover:shadow-blue-200';
  case 'indigo': return 'bg-indigo-600 text-white hover:bg-indigo-500 hover:shadow-indigo-200';
  case 'violet': return 'bg-violet-600 text-white hover:bg-violet-500 hover:shadow-violet-200';
  case 'emerald': return 'bg-emerald-600 text-white hover:bg-emerald-500 hover:shadow-emerald-200';
  case 'rose': return 'bg-rose-600 text-white hover:bg-rose-500 hover:shadow-rose-200';
  default: return 'bg-blue-600 text-white hover:bg-blue-500';
}
  };

  return {
    showActionModal, setShowActionModal,
    flightsData, setFlightsData,
    aircraftData, setAircraftData,
    pilotsData, setPilotsData,
    handleAddVariable, handleAddWidget, handleDeleteWidget, handleUpdateWidgetConfig,
    handleVariableChange, getVarValue, getSimulatedFlights, handleExecuteSimulatedAction,
    getVarTypeBadge, getPrimaryColorClass,
  };
}
