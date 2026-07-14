/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { ArrowLeft, Check, Inbox, LayoutGrid, MousePointerClick, Palette, Play, Plus, PlusCircle, Send, Settings, Trash2, Zap, LayoutDashboard, Plane, Activity, HeartPulse } from 'lucide-react';
import * as Icons from 'lucide-react';
import { useTheme } from "../components/ThemeContext";


// ── Local types (self-contained workshop) ──
interface ObjectType { id: string; name: string; }
interface ActionType { id: string; name: string; displayName: string; description: string; parameters?: any[]; }
interface Dataset { id: string; sampleData: any[]; }

// ── Local action types (for action_button widgets) ──
const mockActionTypes: ActionType[] = [
  { id: 'update_flight_status', name: 'Update Flight Status', displayName: '更新航班状态', description: '更新指定航班的运行状态' },
  { id: 'schedule_maintenance_check', name: 'Schedule Maintenance', displayName: '安排适航维护检修', description: '为指定飞机安排维护检查' },
  { id: 'assign_pilot', name: 'Assign Pilot', displayName: '分派飞行员', description: '为航班分派飞行员' },
  { id: 'cancel_flight', name: 'Cancel Flight', displayName: '取消航班', description: '取消指定航班' },
];

// Dynamic icon component for data-driven icon names
const DynamicIcon = ({ name, size = 16, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || Icons.HelpCircle;
  return <Comp size={size} className={className} />;
};
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell
} from 'recharts';

// ==========================================
// 1. Interfaces & Types for Workshop
// ==========================================
export interface WorkshopVariable {
  id: string;
  name: string;
  type: 'object_set' | 'object' | 'string' | 'number' | 'boolean';
  objectTypeId?: string;
  value: any; // Current runtime value
  initialValue: any;
  description: string;
}

export interface WorkshopWidget {
  id: string;
  type: 'table' | 'chart' | 'metric' | 'object_view' | 'action_button' | 'filter_bar' | 'rich_text';
  title: string;
  slot: string; // "header" | "sidebar" | "main_top" | "main_middle" | "main_bottom" | "aside"
  config: {
    dataSourceVarId?: string; // Bind to object_set variable
    targetVarId?: string; // Bind to object selection variable
    filterProperty?: string; // Bound property to filter
    columns?: string[]; // Array of property IDs for table
    chartType?: 'bar' | 'line' | 'pie';
    groupByProperty?: string;
    metricType?: 'count' | 'sum' | 'avg';
    metricProperty?: string;
    actionTypeId?: string; // Bound Ontology Action Type
    content?: string; // For rich text
    width?: string;
    height?: string;
  };
}

export interface WorkshopPage {
  id: string;
  title: string;
  icon: string;
  widgets: WorkshopWidget[];
}

export interface WorkshopApp {
  id: string;
  name: string;
  description: string;
  lastModified: string;
  isPublished: boolean;
  theme: {
    primaryColor: string; // tailwind color (e.g., 'blue', 'indigo', 'violet')
    isDark: boolean;
    title: string;
    logo: string;
  };
  pages: WorkshopPage[];
  variables: WorkshopVariable[];
}

// ==========================================
// 2. Default Initial Mock Workshop Apps
// ==========================================
const initialApps: WorkshopApp[] = [
  {
    id: 'aviation_ops',
    name: '航空运行指挥与航班调度系统',
    description: '通过直接绑定民航核心本体，实现对航班准点状态、飞机故障维护与飞行员排班的一体化低代码交互系统。',
    lastModified: '2026-07-06 14:30',
    isPublished: true,
    theme: {
      primaryColor: 'blue',
      isDark: false,
      title: 'Aviation Ops Workshop',
      logo: 'Plane'
    },
    variables: [
      { id: 'v_flights_all', name: '全部航班集合', type: 'object_set', objectTypeId: 'flight', initialValue: 'all', value: 'all', description: '包含核心本体中所有的预定航班实例。' },
      { id: 'v_flights_filtered', name: '过滤后的航班集', type: 'object_set', objectTypeId: 'flight', initialValue: 'filtered', value: 'filtered', description: '经由侧边栏状态或机场筛选后的动态子集。' },
      { id: 'v_selected_flight', name: '当前选中航班', type: 'object', objectTypeId: 'flight', initialValue: null, value: null, description: '存储表格组件中用户点击高亮的特定航班。' },
      { id: 'v_filter_status', name: '选中的航班状态', type: 'string', initialValue: 'ALL', value: 'ALL', description: '存储侧边栏单选框过滤器的状态。' },
      { id: 'v_filter_airport', name: '选中的起飞机场', type: 'string', initialValue: 'ALL', value: 'ALL', description: '存储机场下拉过滤器的选中值。' }
    ],
    pages: [
      {
        id: 'p1_flights',
        title: '航班运行综合大盘',
        icon: 'LayoutDashboard',
        widgets: [
          {
            id: 'w_filters',
            type: 'filter_bar',
            title: '运行监控筛选器',
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
            title: '关注航班总数',
            slot: 'main_top',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              metricType: 'count'
            }
          },
          {
            id: 'w_metric_delay',
            type: 'metric',
            title: '当前延误航班数',
            slot: 'main_top',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              metricType: 'sum',
              metricProperty: 'delay_flag' // Visual formula mock count where status == 'DELAYED'
            }
          },
          {
            id: 'w_flights_chart',
            type: 'chart',
            title: '航班运行状态分布',
            slot: 'main_middle',
            config: {
              dataSourceVarId: 'v_flights_filtered',
              chartType: 'bar',
              groupByProperty: 'status'
            }
          },
          {
            id: 'w_flights_table',
            type: 'table',
            title: '每日离到港航班清单',
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
            title: '航班本体属性视图',
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
        ]
      },
      {
        id: 'p2_aircrafts',
        title: '机队适航维护中心',
        icon: 'Plane',
        widgets: [
          {
            id: 'w_aircraft_metric_total',
            type: 'metric',
            title: '在编航机总数',
            slot: 'main_top',
            config: {
              dataSourceVarId: 'v_flights_all', // Represents aircraft general
              metricType: 'count'
            }
          },
          {
            id: 'w_aircraft_table',
            type: 'table',
            title: '飞机库本体明细',
            slot: 'main_bottom',
            config: {
              dataSourceVarId: 'v_flights_all',
              columns: ['tailNumber', 'model', 'manufacturer', 'status', 'lastMaintenance']
            }
          }
        ]
      }
    ]
  },
  {
    id: 'aircraft_maintenance',
    name: '机队适航度与检查管理中心',
    description: '关注民航飞机资产的使用健康、高级维护周期以及安全排查，绑定「飞机 (Aircraft)」本体对象类型。',
    lastModified: '2026-07-05 18:10',
    isPublished: false,
    theme: {
      primaryColor: 'indigo',
      isDark: true,
      title: 'Fleet Health Center',
      logo: 'Activity'
    },
    variables: [
      { id: 'v_ac_all', name: '全部飞机机队', type: 'object_set', objectTypeId: 'aircraft', initialValue: 'all', value: 'all', description: '全部飞机实体。' },
      { id: 'v_selected_ac', name: '选中待修飞机', type: 'object', objectTypeId: 'aircraft', initialValue: null, value: null, description: '存储要维护的飞机实体。' }
    ],
    pages: [
      {
        id: 'p1_health',
        title: '健康大盘',
        icon: 'HeartPulse',
        widgets: [
          {
            id: 'w_ac_table',
            type: 'table',
            title: '物理飞机列表',
            slot: 'main_bottom',
            config: {
              dataSourceVarId: 'v_ac_all',
              targetVarId: 'v_selected_ac',
              columns: ['tailNumber', 'model', 'manufacturer', 'status', 'lastMaintenance']
            }
          },
          {
            id: 'w_ac_view',
            type: 'object_view',
            title: '飞机档案卡片',
            slot: 'aside',
            config: {
              targetVarId: 'v_selected_ac'
            }
          },
          {
            id: 'w_ac_action',
            type: 'action_button',
            title: '安排适航维护检修',
            slot: 'aside',
            config: {
              actionTypeId: 'schedule_maintenance_check',
              targetVarId: 'v_selected_ac'
            }
          }
        ]
      }
    ]
  }
];

export default function WorkshopView({ showToast: propShowToast }: { showToast?: (type: 'success' | 'info' | 'error', message: string) => void }) {
  const { styles } = useTheme();
  const showToast = propShowToast || ((type: string, msg: string) => console.log(`[workshop:${type}]`, msg));
  // Workshop states
  const [apps, setApps] = useState<WorkshopApp[]>(() => {
    const cached = localStorage.getItem('ecos_workshop_apps');
    return cached ? JSON.parse(cached) : initialApps;
  });
  const [activeAppId, setActiveAppId] = useState<string | null>(null);
  const [activePageId, setActivePageId] = useState<string>('');
  const [editorMode, setEditorMode] = useState<'design' | 'preview'>('design');
  const [selectedWidgetId, setSelectedWidgetId] = useState<string | null>(null);
  const [leftTab, setLeftTab] = useState<'pages' | 'variables' | 'widgets' | 'theme'>('pages');
  
  // Running simulation state (stores the live data of ontology entities inside the workshop so interactions update widgets)
  const [flightsData, setFlightsData] = useState<any[]>([]);
  const [aircraftData, setAircraftData] = useState<any[]>([]);
  const [pilotsData, setPilotsData] = useState<any[]>([]);

  // Modals state
  const [showAddWidgetModal, setShowAddWidgetModal] = useState(false);
  const [addWidgetSlot, setAddWidgetSlot] = useState<string>('');
  const [showAddVarModal, setShowAddVarModal] = useState(false);
  const [showActionModal, setShowActionModal] = useState<ActionType | null>(null);
  
  // Form fields
  const [newVarName, setNewVarName] = useState('');
  const [newVarType, setNewVarType] = useState<'object_set' | 'object' | 'string' | 'number'>('string');
  const [newVarObjType, setNewVarObjType] = useState('flight');
  const [newVarDesc, setNewVarDesc] = useState('');

  // Loaded Active App helper
  const activeApp = apps.find(a => a.id === activeAppId);
  const activePage = activeApp?.pages.find(p => p.id === activePageId);

  // Synchronize simulated database (localStorage-first, empty fallback)
  useEffect(() => {
    const cachedFlights = localStorage.getItem('workshop_sim_flights');
    const cachedAircraft = localStorage.getItem('workshop_sim_aircraft');
    const cachedPilots = localStorage.getItem('workshop_sim_pilots');

    if (cachedFlights && cachedAircraft) {
      setFlightsData(JSON.parse(cachedFlights));
      setAircraftData(JSON.parse(cachedAircraft));
      setPilotsData(JSON.parse(cachedPilots || '[]'));
    }
    // No external mock data — workshop uses hardcoded initialApps
  }, [activeAppId]);

  // Hook up AIP Copilot Command System for interactive automation
  useEffect(() => {
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
    return () => {
      window.removeEventListener('aip-workshop-command', handleAipCommand);
    };
  }, [apps, activeAppId, activePageId]);

  // Persist Workshop changes
  const saveAppsState = (updated: WorkshopApp[]) => {
    setApps(updated);
    localStorage.setItem('ecos_workshop_apps', JSON.stringify(updated));
  };

  const handleCreateNewApp = () => {
    const id = `app_${Date.now().toString().slice(-4)}`;
    const newApp: WorkshopApp = {
      id,
      name: '新建无代码智能大盘',
      description: '点击进行编辑。请在此处输入应用所解决的业务范围和业务场景。',
      lastModified: new Date().toISOString().replace('T', ' ').slice(0, 16),
      isPublished: false,
      theme: {
        primaryColor: 'indigo',
        isDark: false,
        title: 'New Workshop Application',
        logo: 'LayoutGrid'
      },
      variables: [
        { id: 'v_flights_all', name: '全量航班', type: 'object_set', objectTypeId: 'flight', initialValue: 'all', value: 'all', description: '所有航班实体' }
      ],
      pages: [
        {
          id: 'p_main',
          title: '主监控视图',
          icon: 'LayoutDashboard',
          widgets: [
            {
              id: 'w_welcome',
              type: 'rich_text',
              title: '欢迎面板',
              slot: 'main_top',
              config: {
                content: '# 航空指挥大盘\n请在右侧或左侧树选择本组件，将其绑定至表格、图表、指标卡等本体分析控件。'
              }
            }
          ]
        }
      ]
    };
    const updated = [...apps, newApp];
    saveAppsState(updated);
    setActiveAppId(id);
    setActivePageId('p_main');
    setEditorMode('design');
    showToast('success', `应用「${newApp.name}」创建成功！`);
  };

  const handleDeleteApp = (id: string, name: string) => {
    if (!window.confirm(`确定要彻底删除应用「${name}」吗？`)) return;
    const updated = apps.filter(a => a.id !== id);
    saveAppsState(updated);
    showToast('info', `应用已删除。`);
  };

  // Variable helpers
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

  return (
    <div className={`flex-1 flex flex-col h-full overflow-hidden ${styles.appBg} select-none text-xs font-sans`}>
      
      {/* 1. APP LIST SCREEN */}
      {activeAppId === null ? (
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
            {apps.map(app => (
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
      ) : (
        
        // 2. WORKSHOP EDITOR WORKSPACE (Three-column layout)
        <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
          
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

          {/* Master Workspace Split Panel */}
          <div className="flex-1 flex overflow-hidden">
            
            {/* COLUMN 1: LEFT CONFIG SIDEBAR (Widget list, Pages, Variables, Styles) */}
            <div className={`w-60 ${styles.appBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
              {/* Tab Selector */}
              <div className={`flex border-b ${styles.cardBorder} divide-x divide-slate-200/55 text-center shrink-0`}>
                {(['pages', 'variables', 'widgets', 'theme'] as const).map(tab => (
                  <button
                    key={tab}
                    onClick={() => setLeftTab(tab)}
                    className={`flex-1 py-2 font-semibold text-[10px] uppercase transition-colors cursor-pointer ${
                      leftTab === tab
                        ? '${styles.cardBg} ${styles.cardText} border-b-2 border-slate-800'
                        : '${styles.cardTextMuted} hover:${styles.sidebarBg} hover:${styles.cardText}'
                    }`}
                  >
                    {tab === 'pages' ? '页面' : tab === 'variables' ? '数据变量' : tab === 'widgets' ? '组件树' : '属性'}
                  </button>
                ))}
              </div>

              {/* Tab Contents */}
              <div className="flex-1 overflow-y-auto p-3">
                
                {/* 1. PAGES VIEW */}
                {leftTab === 'pages' && (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase`}>应用页面 ({activeApp.pages.length})</span>
                      <button onClick={handleAddPage} className="p-1 hover:bg-slate-200 rounded text-blue-600" title="添加新视图">
                        <Plus size={13} />
                      </button>
                    </div>
                    <div className="space-y-1">
                      {activeApp.pages.map(p => {
                        const isActive = p.id === activePageId;
                        return (
                          <div
                            key={p.id}
                            onClick={() => {
                              setActivePageId(p.id);
                              setSelectedWidgetId(null);
                            }}
                            className={`w-full p-2 rounded-lg flex items-center justify-between cursor-pointer transition-colors ${
                              isActive ? '${styles.cardBg} shadow-xs border ${styles.cardBorder} font-bold ${styles.cardText}' : '${styles.cardTextMuted} hover:bg-slate-200/50'
                            }`}
                          >
                            <div className="flex items-center gap-2 truncate">
                              <DynamicIcon name={p.icon} size={12} className={isActive ? 'text-blue-500' : 'text-slate-400'} />
                              <input
                                type="text"
                                value={p.title}
                                disabled={!isActive}
                                onChange={e => {
                                  const title = e.target.value;
                                  const updated = apps.map(a => {
                                    if (a.id === activeApp.id) {
                                      return {
                                        ...a,
                                        pages: a.pages.map(pg => pg.id === p.id ? { ...pg, title } : pg)
                                      };
                                    }
                                    return a;
                                  });
                                  saveAppsState(updated);
                                }}
                                className="bg-transparent border-none text-xs focus:outline-hidden p-0 truncate font-semibold w-36 disabled:cursor-pointer"
                              />
                            </div>
                            {activeApp.pages.length > 1 && (
                              <button
                                onClick={e => {
                                  e.stopPropagation();
                                  if (!window.confirm(`确定要删除页面「${p.title}」吗？`)) return;
                                  const updated = apps.map(a => {
                                    if (a.id === activeApp.id) {
                                      const remaining = a.pages.filter(pg => pg.id !== p.id);
                                      return { ...a, pages: remaining };
                                    }
                                    return a;
                                  });
                                  saveAppsState(updated);
                                  if (isActive) setActivePageId(activeApp.pages.find(pg => pg.id !== p.id)?.id || '');
                                }}
                                className="p-1 opacity-0 hover:opacity-100 text-red-500 hover:bg-red-50 rounded"
                              >
                                <Trash2 size={11} />
                              </button>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 2. VARIABLES VIEW */}
                {leftTab === 'variables' && (
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase`}>状态与变量</span>
                      <button
                        onClick={() => setShowAddVarModal(true)}
                        className="p-1 hover:bg-slate-200 rounded text-blue-600 flex items-center gap-0.5"
                      >
                        <Plus size={12} />
                        <span className="text-[10px] font-semibold">添加变量</span>
                      </button>
                    </div>

                    <div className="space-y-2">
                      {activeApp.variables.map(v => (
                        <div key={v.id} className={`p-2 ${styles.cardBg} rounded-lg border ${styles.cardBorder} space-y-1 hover:border-slate-300`}>
                          <div className="flex items-center justify-between">
                            <span className={`font-bold ${styles.cardText} text-xs truncate`} title={v.name}>{v.name}</span>
                            <span className={`px-1.5 py-0.2 rounded border text-[8px] font-mono uppercase shrink-0 ${getVarTypeBadge(v.type)}`}>
                              {v.type === 'object_set' ? `集合:${v.objectTypeId}` : v.type === 'object' ? `实体:${v.objectTypeId}` : v.type}
                            </span>
                          </div>
                          <p className={`text-[10px] ${styles.cardTextMuted} leading-tight`}>{v.description}</p>
                          <div className={`${styles.appBg} p-1 rounded font-mono text-[9px] ${styles.cardTextMuted} truncate border ${styles.cardBorder}/50 flex justify-between items-center`}>
                            <span className={`${styles.cardTextMuted}`}>运行值:</span>
                            <span className={`truncate max-w-32 font-bold ${styles.cardText}`}>
                              {v.type === 'object_set' ? 'Dynamic Set' : v.value ? (typeof v.value === 'object' ? v.value.flightNumber || v.value.tailNumber : String(v.value)) : 'null'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* 3. WIDGETS TREE VIEW */}
                {leftTab === 'widgets' && (
                  <div className="space-y-3">
                    <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase block mb-1`}>页面组件层级 Tree</span>
                    <div className="space-y-1">
                      {activePage?.widgets.map(w => {
                        const isSelected = w.id === selectedWidgetId;
                        return (
                          <div
                            key={w.id}
                            onClick={() => setSelectedWidgetId(w.id)}
                            className={`p-1.5 rounded-md flex items-center justify-between cursor-pointer transition-all ${
                              isSelected ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600' : '${styles.cardTextMuted} hover:${styles.sidebarBg}'
                            }`}
                          >
                            <div className="flex items-center gap-1.5 truncate">
                              <span className={`${styles.cardTextMuted}`}>
                                <DynamicIcon
                                  name={
                                    w.type === 'table' ? 'TableProperties' :
                                    w.type === 'chart' ? 'BarChart3' :
                                    w.type === 'metric' ? 'Hash' :
                                    w.type === 'object_view' ? 'FileText' : 'PlayCircle'
                                  }
                                  size={11}
                                />
                              </span>
                              <span className="truncate text-[11px]">{w.title}</span>
                            </div>
                            <span className={`text-[8px] font-mono ${styles.cardTextMuted} lowercase`}>{w.slot}</span>
                          </div>
                        );
                      })}
                      {(!activePage || activePage.widgets.length === 0) && (
                        <div className={`p-4 text-center ${styles.cardTextMuted} text-xs`}>画布空空如也，请从预览区添加组件</div>
                      )}
                    </div>
                  </div>
                )}

                {/* 4. APP THEME VIEW */}
                {leftTab === 'theme' && (
                  <div className="space-y-4">
                    <span className={`font-bold text-[10px] ${styles.cardTextMuted} uppercase block`}>应用主题与外观设置</span>
                    
                    <div className="space-y-2">
                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>应用品牌名</label>
                        <input
                          type="text"
                          value={activeApp.theme.title}
                          onChange={e => handleUpdateAppTheme({ title: e.target.value })}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden`}
                        />
                      </div>

                      <div className="space-y-1">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>应用描述</label>
                        <textarea
                          value={activeApp.description}
                          onChange={e => handleUpdateAppTheme({}, undefined, e.target.value)}
                          rows={2}
                          className={`w-full px-2 py-1.5 border ${styles.cardBorder} rounded-md focus:outline-hidden resize-none`}
                        />
                      </div>

                      <div className="space-y-1.5">
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px]`}>主色调 (Accent Color)</label>
                        <div className="flex gap-2">
                          {['blue', 'indigo', 'violet', 'emerald', 'rose'].map(color => {
                            const selected = activeApp.theme.primaryColor === color;
                            return (
                              <button
                                key={color}
                                onClick={() => handleUpdateAppTheme({ primaryColor: color })}
                                className={`w-5 h-5 rounded-full border ${selected ? 'ring-2 ring-slate-800 border-white shadow-xs' : 'border-transparent'}`}
                                style={{
                                  backgroundColor:
                                    color === 'blue' ? '#2563eb' :
                                    color === 'indigo' ? '#4f46e5' :
                                    color === 'violet' ? '#7c3aed' :
                                    color === 'emerald' ? '#059669' : '#e11d48'
                                }}
                              />
                            );
                          })}
                        </div>
                      </div>

                      <div className={`space-y-1.5 pt-2 border-t ${styles.cardBorder}/50`}>
                        <label className={`${styles.cardTextMuted} font-semibold text-[10px] flex items-center justify-between`}>
                          <span>深色主题 (Dark Mode)</span>
                          <input
                            type="checkbox"
                            checked={activeApp.theme.isDark}
                            onChange={e => handleUpdateAppTheme({ isDark: e.target.checked })}
                            className={`rounded ${styles.cardText} border-slate-300 h-3 w-3`}
                          />
                        </label>
                      </div>
                    </div>
                  </div>
                )}

              </div>
            </div>

            {/* COLUMN 2: CENTER STAGE (Canvas Designer or Live interact) */}
            <div className={`flex-1 flex flex-col overflow-hidden ${activeApp.theme.isDark ? 'bg-slate-950 text-slate-100' : '${styles.sidebarBg} ${styles.cardText}'}`}>
              
              {/* Application's Own Inner Header Bar */}
              <div className={`h-11 px-4 flex items-center justify-between shadow-xs border-b shrink-0 ${
                activeApp.theme.isDark ? 'bg-slate-900 border-slate-800 text-slate-100' : '${styles.cardBg} ${styles.cardBorder} ${styles.cardText}'
              }`}>
                <div className="flex items-center gap-2">
                  <span className={`p-1 rounded bg-${activeApp.theme.primaryColor}-100 text-${activeApp.theme.primaryColor}-700`}>
                    <DynamicIcon name={activeApp.theme.logo || 'Plane'} size={14} />
                  </span>
                  <span className="font-bold text-sm tracking-tight">{activeApp.theme.title}</span>
                </div>
                
                {/* Horizontal Navigation tabs inside App page */}
                <div className="flex items-center gap-1.5">
                  {activeApp.pages.map(p => (
                    <button
                      key={p.id}
                      onClick={() => {
                        setActivePageId(p.id);
                        setSelectedWidgetId(null);
                      }}
                      className={`h-7 px-3 rounded-md text-[11px] font-semibold transition-all ${
                        p.id === activePageId
                          ? `bg-${activeApp.theme.primaryColor}-100 text-${activeApp.theme.primaryColor}-700`
                          : 'opacity-65 hover:opacity-100 hover:bg-slate-200/40'
                      }`}
                    >
                      {p.title}
                    </button>
                  ))}
                </div>
              </div>

              {/* Central canvas structure of activePage */}
              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                
                {/* 2.1. TOP METRICS SLOT */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {activePage?.widgets.filter(w => w.slot === 'main_top').map(w => {
                    const isSelected = w.id === selectedWidgetId;
                    const ds = w.config.dataSourceVarId;
                    const type = w.config.metricType;

                    // Compute simulation data
                    let metricValue = 0;
                    if (ds === 'v_flights_all') {
                      metricValue = flightsData.length;
                    } else if (ds === 'v_flights_filtered') {
                      const simData = getSimulatedFlights();
                      if (type === 'count') {
                        metricValue = simData.length;
                      } else {
                        metricValue = simData.filter(f => f.status === 'DELAYED').length;
                      }
                    } else {
                      metricValue = aircraftData.length;
                    }

                    return (
                      <div
                        key={w.id}
                        onClick={() => editorMode === 'design' && setSelectedWidgetId(w.id)}
                        className={`relative rounded-xl border p-4 ${styles.cardBg} shadow-xs select-none transition-all ${
                          editorMode === 'design' ? 'cursor-pointer hover:border-blue-400' : ''
                        } ${isSelected ? 'ring-2 ring-blue-500 border-transparent scale-102' : '${styles.cardBorder}'}`}
                      >
                        {editorMode === 'design' && (
                          <div className={`absolute top-1 right-1 flex items-center gap-1 opacity-60 hover:opacity-100 ${styles.sidebarBg} rounded px-1.5 py-0.5 text-[8px] font-mono`}>
                            <span>Hash</span>
                            <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                          </div>
                        )}
                        <p className={`text-[10px] uppercase font-bold ${styles.cardTextMuted} tracking-wider`}>{w.title}</p>
                        <h2 className={`text-2xl font-black ${styles.cardText} mt-1`}>{metricValue} <span className={`text-xs font-semibold ${styles.cardTextMuted}`}>条记录</span></h2>
                      </div>
                    );
                  })}

                  {editorMode === 'design' && (
                    <button
                      onClick={() => { setAddWidgetSlot('main_top'); setShowAddWidgetModal(true); }}
                      className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[80px]`}
                    >
                      <Plus size={15} />
                      <span className="text-[10px] mt-1 font-semibold">添加指标卡</span>
                    </button>
                  )}
                </div>

                {/* 2.2. MAIN SPLIT CONTENT AREA (Chart & Table / Filters & Views) */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
                  
                  {/* LEFT SIDEBAR AREA WITHIN PAGE (Typically Filters) */}
                  <div className="lg:col-span-3 space-y-4">
                    {activePage?.widgets.filter(w => w.slot === 'sidebar').map(w => {
                      const isSelected = w.id === selectedWidgetId;
                      const activeStatus = getVarValue('v_filter_status') || 'ALL';
                      const activeAirport = getVarValue('v_filter_airport') || 'ALL';

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
                              <span>Filter</span>
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          {/* Simulated Filters */}
                          <div className="space-y-3">
                            <div className="space-y-1">
                              <label className={`${styles.cardTextMuted} text-[10px] uppercase font-bold tracking-wider`}>航班状态：</label>
                              <div className="space-y-1.5">
                                {['ALL', 'ON_TIME', 'DELAYED', 'BOARDING', 'CANCELLED'].map(st => (
                                  <label key={st} className={`flex items-center gap-2 cursor-pointer ${styles.cardText}`}>
                                    <input
                                      type="radio"
                                      name="status_filter"
                                      disabled={editorMode === 'design'}
                                      checked={activeStatus === st}
                                      onChange={() => handleVariableChange('v_filter_status', st)}
                                      className="rounded-full text-blue-600 border-slate-300 h-3 w-3 cursor-pointer"
                                    />
                                    <span className="font-semibold text-xs">
                                      {st === 'ALL' ? '全量展示' : st === 'ON_TIME' ? '准点运行' : st === 'DELAYED' ? '发生延误' : st === 'BOARDING' ? '登机中' : '已取消'}
                                    </span>
                                  </label>
                                ))}
                              </div>
                            </div>

                            <div className={`space-y-1 pt-2 border-t ${styles.cardBorder}`}>
                              <label className={`${styles.cardTextMuted} text-[10px] uppercase font-bold tracking-wider`}>枢纽港口：</label>
                              <select
                                disabled={editorMode === 'design'}
                                value={activeAirport}
                                onChange={e => handleVariableChange('v_filter_airport', e.target.value)}
                                className={`w-full px-2 py-1 ${styles.cardBg} border ${styles.cardBorder} rounded-md text-xs font-semibold`}
                              >
                                <option value="ALL">全部关联机场</option>
                                <option value="ORD">ORD (芝加哥)</option>
                                <option value="ATL">ATL (亚特兰大)</option>
                                <option value="DFW">DFW (达拉斯)</option>
                                <option value="SFO">SFO (旧金山)</option>
                                <option value="PEK">PEK (北京首都)</option>
                              </select>
                            </div>
                          </div>
                        </div>
                      );
                    })}

                    {editorMode === 'design' && (
                      <button
                        onClick={() => { setAddWidgetSlot('sidebar'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[100px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">添加页面筛选栏</span>
                      </button>
                    )}
                  </div>

                  {/* CENTER MAIN PLOT AREA */}
                  <div className="lg:col-span-6 space-y-4">
                    {/* Charts slot */}
                    {activePage?.widgets.filter(w => w.slot === 'main_middle').map(w => {
                      const isSelected = w.id === selectedWidgetId;
                      const simData = getSimulatedFlights();
                      
                      // Aggregation for chart series
                      const groupKey = w.config.groupByProperty || 'status';
                      
                      // Count occurrences of each value for the groupKey
                      const counts: Record<string, number> = {};
                      simData.forEach(item => {
                        const val = item[groupKey] || 'UNKNOWN';
                        let label = val;
                        if (val === 'ON_TIME') label = '准点';
                        else if (val === 'DELAYED') label = '延误';
                        else if (val === 'BOARDING') label = '登机';
                        else if (val === 'CANCELLED') label = '取消';
                        
                        counts[label] = (counts[label] || 0) + 1;
                      });

                      const chartData = Object.entries(counts).map(([name, value]) => ({
                        name,
                        value
                      }));

                      if (chartData.length === 0) {
                        chartData.push({ name: '无数据', value: 0 });
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
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
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
                                    {chartData.map((entry, index) => (
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

                    {editorMode === 'design' && activePage?.widgets.filter(w => w.slot === 'main_middle').length === 0 && (
                      <button
                        onClick={() => { setAddWidgetSlot('main_middle'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[120px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">添加分析图表</span>
                      </button>
                    )}

                    {/* Table Slot */}
                    {activePage?.widgets.filter(w => w.slot === 'main_bottom').map(w => {
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
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          <div className={`overflow-x-auto rounded-lg border ${styles.cardBorder}`}>
                            <table className="w-full text-left border-collapse">
                              <thead>
                                <tr className={`${styles.appBg}/50 text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider border-b ${styles.cardBorder}`}>
                                  {isAircraftTable ? (
                                    <>
                                      <th className="py-2 px-3">机尾号</th>
                                      <th className="py-2 px-3">机型</th>
                                      <th className="py-2 px-3">制造商</th>
                                      <th className="py-2 px-3">适航状态</th>
                                      <th className="py-2 px-3">上次维护</th>
                                    </>
                                  ) : (
                                    <>
                                      <th className="py-2 px-3">航班号</th>
                                      <th className="py-2 px-3">机尾号</th>
                                      <th className="py-2 px-3">离港</th>
                                      <th className="py-2 px-3">进港</th>
                                      <th className="py-2 px-3">运行状态</th>
                                    </>
                                  )}
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-slate-100 text-[11px]">
                                {rowData.map((row, idx) => {
                                  const key = isAircraftTable ? row.tailNumber : row.flightNumber;
                                  const isRowSelected = activeSelection && (isAircraftTable ? activeSelection.tailNumber === key : activeSelection.flightNumber === key);
                                  
                                  return (
                                    <tr
                                      key={key || idx}
                                      onClick={() => {
                                        if (editorMode === 'preview' && boundTarget) {
                                          handleVariableChange(boundTarget, row);
                                          showToast('info', `选中本体实体: ${key}`);
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
                                              {row.status === 'ON_TIME' ? '准点' : row.status === 'DELAYED' ? '延误' : row.status}
                                            </span>
                                          </td>
                                        </>
                                      )}
                                    </tr>
                                  );
                                })}
                                {rowData.length === 0 && (
                                  <tr>
                                    <td colSpan={5} className={`py-6 text-center ${styles.cardTextMuted}`}>暂无符合过滤器条件的数据实例</td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      );
                    })}

                    {editorMode === 'design' && activePage?.widgets.filter(w => w.slot === 'main_bottom').length === 0 && (
                      <button
                        onClick={() => { setAddWidgetSlot('main_bottom'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[140px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">添加本体表格明细</span>
                      </button>
                    )}
                  </div>

                  {/* RIGHT ASIDE VIEW (Object View and Actions) */}
                  <div className="lg:col-span-3 space-y-4">
                    {/* Object card */}
                    {activePage?.widgets.filter(w => w.slot === 'aside' && w.type === 'object_view').map(w => {
                      const isSelected = w.id === selectedWidgetId;
                      const boundTarget = w.config.targetVarId;
                      const currentSelection = getVarValue(boundTarget);

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
                              <span>ObjectView</span>
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <h3 className={`font-bold ${styles.cardText} border-b ${styles.cardBorder} pb-2 mb-3 text-[11px]`}>{w.title}</h3>
                          
                          {currentSelection ? (
                            <div className="space-y-2.5">
                              {currentSelection.flightNumber ? (
                                <>
                                  <div className="flex items-center justify-between">
                                    <span className={`text-lg font-black ${styles.cardText}`}>{currentSelection.flightNumber}</span>
                                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                      currentSelection.status === 'ON_TIME' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
                                    }`}>
                                      {currentSelection.status}
                                    </span>
                                  </div>
                                  <div className={`grid grid-cols-2 gap-2 text-[11px] ${styles.appBg} p-2.5 rounded-lg border ${styles.cardBorder} font-mono`}>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>执飞机尾号</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.tailNumber}</p>
                                    </div>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>飞行员编号</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.assignedPilotId}</p>
                                    </div>
                                    <div className={`col-span-2 border-t ${styles.cardBorder}/50 pt-1.5`}>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>起降枢纽港</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.depAirport} ✈ {currentSelection.arrAirport}</p>
                                    </div>
                                  </div>
                                </>
                              ) : (
                                <>
                                  <div className="flex items-center justify-between">
                                    <span className={`text-lg font-black ${styles.cardText}`}>{currentSelection.tailNumber}</span>
                                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-50 text-indigo-700`}>
                                      {currentSelection.status}
                                    </span>
                                  </div>
                                  <div className={`grid grid-cols-2 gap-2 text-[11px] ${styles.appBg} p-2.5 rounded-lg border ${styles.cardBorder} font-mono`}>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>客机型号</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.model}</p>
                                    </div>
                                    <div>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>制造厂商</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.manufacturer}</p>
                                    </div>
                                    <div className={`col-span-2 border-t ${styles.cardBorder}/50 pt-1.5`}>
                                      <p className={`text-[9px] ${styles.cardTextMuted}`}>最近检修</p>
                                      <p className={`font-bold ${styles.cardText}`}>{currentSelection.lastMaintenance}</p>
                                    </div>
                                  </div>
                                </>
                              )}
                            </div>
                          ) : (
                            <div className={`py-8 text-center ${styles.cardTextMuted} flex flex-col items-center justify-center`}>
                              <Inbox size={20} className="stroke-1 text-slate-300 mb-1" />
                              <span>请从表格中选中任意实体查看本体卡片详情</span>
                            </div>
                          )}
                        </div>
                      );
                    })}

                    {/* Action Button */}
                    {activePage?.widgets.filter(w => w.slot === 'aside' && w.type === 'action_button').map(w => {
                      const isSelected = w.id === selectedWidgetId;
                      const boundActionId = w.config.actionTypeId;
                      const boundTargetVarId = w.config.targetVarId;
                      const boundObject = getVarValue(boundTargetVarId);

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
                              <span>Action</span>
                              <button onClick={e => { e.stopPropagation(); handleDeleteWidget(w.id); }} className="hover:text-red-500 font-bold ml-1 text-[10px]">×</button>
                            </div>
                          )}
                          <button
                            disabled={editorMode === 'design' || !boundObject}
                            onClick={() => {
                              const act = mockActionTypes.find(a => a.id === boundActionId);
                              if (act) setShowActionModal(act);
                            }}
                            className={`w-full py-2.5 rounded-lg font-bold text-xs shadow-xs transition-all flex items-center justify-center gap-1.5 ${
                              editorMode === 'design' ? '${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed border ${styles.cardBorder}' :
                              boundObject ? getPrimaryColorClass(activeApp.theme.primaryColor) : '${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed border ${styles.cardBorder}'
                            }`}
                          >
                            <Zap size={12} className={boundObject ? "fill-white/25" : ""} />
                            <span>{w.title}</span>
                          </button>
                          {!boundObject && editorMode === 'preview' && (
                            <p className={`text-[9px] ${styles.cardTextMuted} text-center mt-1.5 leading-tight`}>需要选中一个本体实例后方能触发本项操作</p>
                          )}
                        </div>
                      );
                    })}

                    {editorMode === 'design' && activePage?.widgets.filter(w => w.slot === 'aside').length === 0 && (
                      <button
                        onClick={() => { setAddWidgetSlot('aside'); setShowAddWidgetModal(true); }}
                        className={`border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center ${styles.cardTextMuted} hover:${styles.cardTextMuted} hover:border-slate-400 hover:${styles.appBg} transition-all cursor-pointer min-h-[140px] w-full`}
                      >
                        <Plus size={15} />
                        <span className="text-[10px] mt-1 font-semibold">放置侧边面板栏</span>
                      </button>
                    )}
                  </div>

                </div>

              </div>
            </div>

            {/* COLUMN 3: RIGHT PROPERTIES SELECTION CONFIG PANEL */}
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

          </div>

        </div>
      )}

      {/* ==========================================
          MODALS & FLYOUTS
         ========================================== */}
      
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

    </div>
  );
}
