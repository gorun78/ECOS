import type { WorkshopApp } from './types';

// AIP voice command handler — generates/updates workshop dashboards from natural language
export function handleAipCommand(
  event: Event,
  apps: WorkshopApp[],
  activeAppId: string | null,
  setApps: (apps: WorkshopApp[]) => void,
  setActiveAppId: (id: string) => void,
  setActivePageId: (id: string) => void,
  setEditorMode: (mode: 'design' | 'preview') => void,
  setSelectedWidgetId: (id: string | null) => void,
) {
const handleAipCommand = (e: Event) => {
  const customEvent = e as CustomEvent;
  const { action } = customEvent.detail;
  
  if (action === 'ws_generate_dashboard') {
    const targetAppId = activeAppId || 'aviation_ops';
    if (!activeAppId) {
      setActiveAppId('aviation_ops');
    }
    
    const updatedApps = apps.map(a => {
      if (a.id === targetAppId) {
        let page = a.pages[0];
        if (!page) {
          page = { id: 'p_main', title: '航班运行综合大盘', icon: 'LayoutDashboard', widgets: [] };
          a.pages = [page];
        }
        
        const newWidgets: WorkshopWidget[] = [
          {
            id: 'w_filters',
            type: 'filter_bar',
            title: '运行监控筛选器 (AIP)',
            slot: 'sidebar',
            config: {
              targetVarId: 'v_filter_status',
              filterProperty: 'status',
              dataSourceVarId: 'v_filter_airport'
            }
          },
          {
            id: 'w_metric_all',
            type: 'metric',
            title: '关注航班总数 (AIP)',
            slot: 'main_top',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              metricType: 'count'
            }
          },
          {
            id: 'w_metric_delay',
            type: 'metric',
            title: '当前延误数 (AIP)',
            slot: 'main_top',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              metricType: 'sum',
              metricProperty: 'delay_flag'
            }
          },
          {
            id: 'w_flights_chart',
            type: 'chart',
            title: '航班运行状态分布 (AIP Pie)',
            slot: 'main_middle',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              chartType: 'pie',
              groupByProperty: 'status'
            }
          },
          {
            id: 'w_flights_table',
            type: 'table',
            title: '每日到离港航班清单 (Ontology)',
            slot: 'main_bottom',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              targetVarId: 'v_selected_flight',
              columns: ['flightNumber', 'tailNumber', 'depAirport', 'arrAirport', 'status', 'scheduledDep']
            }
          },
          {
            id: 'w_flight_detail',
            type: 'object_view',
            title: '航班属性卡片视图',
            slot: 'aside',
            config: {
              targetVarId: 'v_selected_flight'
            }
          },
          {
            id: 'w_action_status',
            type: 'action_button',
            title: '触发业务：更新航班状态',
            slot: 'aside',
            config: {
              actionTypeId: 'update_flight_status',
              targetVarId: 'v_selected_flight'
            }
          }
        ];
        
        return {
          ...a,
          pages: a.pages.map(p => p.id === page.id ? { ...p, widgets: newWidgets } : p)
        };
      }
      return a;
    });
    
    saveAppsState(updatedApps);
    const activeAppInstance = updatedApps.find(a => a.id === targetAppId);
    if (activeAppInstance && activeAppInstance.pages[0]) {
      setActivePageId(activeAppInstance.pages[0].id);
    }
    setEditorMode('preview');
  }
  
  else if (action === 'ws_auto_bind') {
    const targetAppId = activeAppId || 'aviation_ops';
    if (!activeAppId) {
      setActiveAppId('aviation_ops');
    }
    
    const updatedApps = apps.map(a => {
      if (a.id === targetAppId) {
        return {
          ...a,
          pages: a.pages.map(p => ({
            ...p,
            widgets: p.widgets.map(w => {
              if (w.id === 'w_flights_table' || w.type === 'table') {
                return { ...w, config: { ...w.config, targetVarId: 'v_selected_flight' } };
              }
              if (w.id === 'w_flight_detail' || w.type === 'object_view') {
                return { ...w, config: { ...w.config, targetVarId: 'v_selected_flight' } };
              }
              return w;
            })
          }))
        };
      }
      return a;
    });
    
    saveAppsState(updatedApps);
    setSelectedWidgetId('w_flights_table');
    setEditorMode('design');
    setLeftTab('variables');
  }
  
  else if (action === 'ws_inject_copilot') {
    const targetAppId = activeAppId || 'aviation_ops';
    if (!activeAppId) {
      setActiveAppId('aviation_ops');
    }
    
    const updatedApps = apps.map(a => {
      if (a.id === targetAppId) {
        const curPageId = activePageId || a.pages[0]?.id;
        return {
          ...a,
          pages: a.pages.map(p => {
            if (p.id === curPageId) {
              const exists = p.widgets.some(w => w.id === 'w_aip_copilot_btn');
              if (exists) return p;
              
              const aipBtn: WorkshopWidget = {
                id: 'w_aip_copilot_btn',
                type: 'action_button',
                title: '🚀 AIP 智能自动重排班协同 (AIP Agent)',
                slot: 'aside',
                config: {
                  actionTypeId: 'update_flight_status',
                  targetVarId: 'v_selected_flight'
                }
              };
              return { ...p, widgets: [...p.widgets, aipBtn] };
            }
            return p;
          })
        };
      }
      return a;
    });
    
    saveAppsState(updatedApps);
    setSelectedWidgetId('w_aip_copilot_btn');
    setEditorMode('preview');
  }
  
  else if (action === 'ws_transform_theme') {
    const targetAppId = activeAppId || 'aviation_ops';
    if (!activeAppId) {
      setActiveAppId('aviation_ops');
    }
    
    const updatedApps = apps.map(a => {
      if (a.id === targetAppId) {
        return {
          ...a,
          name: 'AIP 航空智能联合指挥控制中心',
          theme: {
            ...a.theme,
            isDark: true,
            primaryColor: 'violet',
            title: 'AIP Joint Command Center'
          }
        };
      }
      return a;
    });
    
    saveAppsState(updatedApps);
    setLeftTab('theme');
  }
};

window.addEventListener('aip-workshop-command', handleAipCommand);
}
