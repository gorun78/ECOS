import type { WorkshopApp } from './types';

export const initialApps: WorkshopApp[] = [
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
];;
