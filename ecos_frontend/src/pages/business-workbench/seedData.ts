/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, FunctionType, OntologyDomain } from '../../types/ontology';

// ==========================================
// 1. 原始数据源 (Datasets)
// ==========================================
export const mockDatasets: Dataset[] = [
  {
    id: 'ds_aircrafts',
    name: 'ds_aircraft_raw_records',
    path: '/Aviation/data/raw_aircraft_records',
    columns: [
      { name: 'tail_number', type: 'string' },
      { name: 'aircraft_model', type: 'string' },
      { name: 'manufacturer', type: 'string' },
      { name: 'status_code', type: 'string' },
      { name: 'last_maintenance_date', type: 'string' },
      { name: 'home_airport_iata', type: 'string' },
      { name: 'created_time', type: 'string' }
    ],
    sampleData: [
      { tail_number: 'N101UA', aircraft_model: 'Boeing 737-800', manufacturer: 'Boeing', status_code: 'ACTIVE', last_maintenance_date: '2026-05-12', home_airport_iata: 'ORD', created_time: '2024-01-10T08:00:00Z' },
      { tail_number: 'N204DL', aircraft_model: 'Airbus A321neo', manufacturer: 'Airbus', status_code: 'ACTIVE', last_maintenance_date: '2026-06-01', home_airport_iata: 'ATL', created_time: '2024-03-15T09:30:00Z' },
      { tail_number: 'N309AA', aircraft_model: 'Boeing 777-300ER', manufacturer: 'Boeing', status_code: 'MAINTENANCE', last_maintenance_date: '2026-06-25', home_airport_iata: 'DFW', created_time: '2023-11-20T14:15:00Z' },
      { tail_number: 'N508UA', aircraft_model: 'Boeing 737-MAX9', manufacturer: 'Boeing', status_code: 'INSPECTION', last_maintenance_date: '2026-06-30', home_airport_iata: 'SFO', created_time: '2025-01-05T10:00:00Z' }
    ]
  },
  {
    id: 'ds_airports',
    name: 'ds_airport_geolocations',
    path: '/Aviation/data/airport_geolocations',
    columns: [
      { name: 'iata_code', type: 'string' },
      { name: 'airport_name', type: 'string' },
      { name: 'city', type: 'string' },
      { name: 'country', type: 'string' },
      { name: 'latitude', type: 'decimal' },
      { name: 'longitude', type: 'decimal' },
      { name: 'timezone', type: 'string' }
    ],
    sampleData: [
      { iata_code: 'ORD', airport_name: '芝加哥奥黑尔国际机场', city: 'Chicago', country: 'USA', latitude: 41.9742, longitude: -87.9073, timezone: 'America/Chicago' },
      { iata_code: 'ATL', airport_name: '哈茨菲尔德-杰克逊亚特兰大国际机场', city: 'Atlanta', country: 'USA', latitude: 33.6407, longitude: -84.4277, timezone: 'America/New_York' },
      { iata_code: 'DFW', airport_name: '达拉斯-沃斯堡国际机场', city: 'Dallas', country: 'USA', latitude: 32.8998, longitude: -97.0403, timezone: 'America/Chicago' },
      { iata_code: 'SFO', airport_name: '旧金山国际机场', city: 'San Francisco', country: 'USA', latitude: 37.6213, longitude: -122.3790, timezone: 'America/Los_Angeles' },
      { iata_code: 'PEK', airport_name: '北京首都国际机场', city: 'Beijing', country: 'China', latitude: 40.0799, longitude: 116.6031, timezone: 'Asia/Shanghai' }
    ]
  },
  {
    id: 'ds_flights',
    name: 'ds_scheduled_flights',
    path: '/Aviation/data/scheduled_flights',
    columns: [
      { name: 'flight_number', type: 'string' },
      { name: 'tail_number', type: 'string' },
      { name: 'dep_airport', type: 'string' },
      { name: 'arr_airport', type: 'string' },
      { name: 'scheduled_dep', type: 'string' },
      { name: 'scheduled_arr', type: 'string' },
      { name: 'status', type: 'string' },
      { name: 'assigned_pilot_id', type: 'string' }
    ],
    sampleData: [
      { flight_number: 'UA102', tail_number: 'N101UA', dep_airport: 'ORD', arr_airport: 'SFO', scheduled_dep: '2026-07-03T08:00:00Z', scheduled_arr: '2026-07-03T12:15:00Z', status: 'ON_TIME', assigned_pilot_id: 'P01' },
      { flight_number: 'DL440', tail_number: 'N204DL', dep_airport: 'ATL', arr_airport: 'DFW', scheduled_dep: '2026-07-03T10:30:00Z', scheduled_arr: '2026-07-03T12:45:00Z', status: 'DELAYED', assigned_pilot_id: 'P02' },
      { flight_number: 'AA880', tail_number: 'N309AA', dep_airport: 'DFW', arr_airport: 'PEK', scheduled_dep: '2026-07-04T13:00:00Z', scheduled_arr: '2026-07-05T16:30:00Z', status: 'ON_TIME', assigned_pilot_id: 'P03' }
    ]
  },
  {
    id: 'ds_pilots',
    name: 'ds_pilot_personnel_records',
    path: '/Aviation/data/pilot_personnel_records',
    columns: [
      { name: 'pilot_id', type: 'string' },
      { name: 'full_name', type: 'string' },
      { name: 'pilot_rank', type: 'string' },
      { name: 'hours_flown', type: 'integer' },
      { name: 'status', type: 'string' },
      { name: 'hire_date', type: 'string' }
    ],
    sampleData: [
      { pilot_id: 'P01', full_name: '张建国', pilot_rank: 'CAPTAIN', hours_flown: 8200, status: 'ACTIVE', hire_date: '2015-08-01' },
      { pilot_id: 'P02', full_name: '李明华', pilot_rank: 'FIRST_OFFICER', hours_flown: 2400, status: 'ACTIVE', hire_date: '2021-04-12' },
      { pilot_id: 'P03', full_name: 'David Smith', pilot_rank: 'CAPTAIN', hours_flown: 12500, status: 'ACTIVE', hire_date: '2010-11-20' },
      { pilot_id: 'P04', full_name: '王小红', pilot_rank: 'FIRST_OFFICER', hours_flown: 1800, status: 'ON_LEAVE', hire_date: '2023-01-15' }
    ]
  },
  {
    id: 'ds_pilot_aircraft_ratings',
    name: 'ds_pilot_aircraft_type_ratings',
    path: '/Aviation/data/pilot_aircraft_type_ratings',
    columns: [
      { name: 'rating_id', type: 'string' },
      { name: 'pilot_id', type: 'string' },
      { name: 'aircraft_model_rating', type: 'string' },
      { name: 'granted_date', type: 'string' }
    ],
    sampleData: [
      { rating_id: 'R01', pilot_id: 'P01', aircraft_model_rating: 'Boeing 737-800', granted_date: '2016-03-10' },
      { rating_id: 'R02', pilot_id: 'P01', aircraft_model_rating: 'Boeing 737-MAX9', granted_date: '2019-11-01' },
      { rating_id: 'R03', pilot_id: 'P02', aircraft_model_rating: 'Airbus A321neo', granted_date: '2021-08-15' },
      { rating_id: 'R04', pilot_id: 'P03', aircraft_model_rating: 'Boeing 777-300ER', granted_date: '2011-05-05' }
    ]
  }
];

// ==========================================
// 2. 共享属性 (Shared Properties)
// ==========================================
export const mockSharedProperties: SharedProperty[] = [
  {
    id: 'sp_created_time',
    displayName: '创建时间',
    apiName: 'createdTime',
    dataType: 'timestamp',
    description: '此记录被初始写入到本地系统的日期和时间戳。'
  },
  {
    id: 'sp_status',
    displayName: '运营状态',
    apiName: 'operationalStatus',
    dataType: 'string',
    description: '实体的通用运行或活动状态指示，如 ACTIVE, INACTIVE, MAINTENANCE 等。'
  }
];

// ==========================================
// 3. 接口类型 (Interfaces)
// ==========================================
export const mockInterfaces: InterfaceType[] = [
  {
    id: 'asset',
    displayName: '资产 (Asset)',
    apiName: 'Asset',
    description: '一种通用的物理、有形资产。要求具有可辨识的主键、制造商与生命周期状态。',
    properties: [
      { id: 'asset_id', displayName: '资产ID', apiName: 'assetId', dataType: 'string', isRequired: true, description: '唯一资产标识。' },
      { id: 'manufacturer', displayName: '制造商', apiName: 'manufacturer', dataType: 'string', isRequired: false, description: '资产的制造厂家。' },
      { id: 'status', displayName: '当前状态', apiName: 'status', dataType: 'string', isRequired: true, description: '当前运营或工作状态。' }
    ]
  },
  {
    id: 'locatable',
    displayName: '可定位实体 (Locatable)',
    apiName: 'Locatable',
    description: '拥有地球表面坐标和地理名称的实体，可被显示在地理地图上。',
    properties: [
      { id: 'location_name', displayName: '位置名称', apiName: 'locationName', dataType: 'string', isRequired: true, description: '该位置的显示标签或名称。' },
      { id: 'coordinate', displayName: '地理坐标', apiName: 'coordinate', dataType: 'geopoint', isRequired: true, description: '实体所在的纬度与经度组合。' }
    ]
  }
];

// ==========================================
// 4. 对象类型 (Object Types)
// ==========================================
export const mockObjectTypes: ObjectType[] = [
  {
    id: 'aircraft',
    displayName: '飞机 (Aircraft)',
    apiName: 'Aircraft',
    description: '民用航空公司机队中的单独飞行器实体。承载所有航班任务，需进行定期维护检测。',
    icon: 'Plane',
    color: 'border-blue-500 bg-blue-50 text-blue-700',
    primaryKey: 'tailNumber',
    titleProperty: 'tailNumber',
    status: 'ACTIVE',
    interfaces: ['asset'],
    domainId: 'assets',
    properties: [
      { id: 'tailNumber', displayName: '机尾号', apiName: 'tailNumber', dataType: 'string', isPrimaryKey: true, description: '民航管理局颁发的全球唯一机身尾号，如 N101UA。' },
      { id: 'model', displayName: '机型', apiName: 'aircraftModel', dataType: 'string', isPrimaryKey: false, description: '飞机的具体物理型号，如 Boeing 737-800 或 Airbus A321neo。' },
      { id: 'manufacturer', displayName: '制造商', apiName: 'manufacturer', dataType: 'string', isPrimaryKey: false, description: '飞机制造企业。' },
      { id: 'status', displayName: '当前状态', apiName: 'status', dataType: 'string', isPrimaryKey: false, description: '飞机的运行状态：ACTIVE (活动中), MAINTENANCE (维护中), INSPECTION (安检中)。', sharedPropertyId: 'sp_status' },
      { id: 'lastMaintenance', displayName: '最近维护日期', apiName: 'lastMaintenanceDate', dataType: 'date', isPrimaryKey: false, description: '上一次完成高级别适航检修和维护保养的日期。' },
      { id: 'homeAirport', displayName: '基地机场', apiName: 'homeAirportIata', dataType: 'string', isPrimaryKey: false, description: '飞机所属或常驻的中心枢纽机场 IATA 三字码。' },
      { id: 'createdTime', displayName: '创建时间', apiName: 'createdTime', dataType: 'timestamp', isPrimaryKey: false, description: '该飞机建档的时间。', sharedPropertyId: 'sp_created_time' }
    ],
    mapping: {
      datasetId: 'ds_aircrafts',
      propertyMappings: {
        tailNumber: 'tail_number',
        model: 'aircraft_model',
        manufacturer: 'manufacturer',
        status: 'status_code',
        lastMaintenance: 'last_maintenance_date',
        homeAirport: 'home_airport_iata',
        createdTime: 'created_time'
      }
    }
  },
  {
    id: 'airport',
    displayName: '机场 (Airport)',
    apiName: 'Airport',
    description: '民航枢纽或中转场站。提供起飞、降落和飞机驻场维护等物理位置支持。',
    icon: 'Building2',
    color: 'border-emerald-500 bg-emerald-50 text-emerald-700',
    primaryKey: 'iataCode',
    titleProperty: 'airportName',
    status: 'ACTIVE',
    interfaces: ['locatable'],
    domainId: 'assets',
    properties: [
      { id: 'iataCode', displayName: 'IATA代码', apiName: 'iataCode', dataType: 'string', isPrimaryKey: true, description: '国际航空运输协会分配的三位英文字母缩写码，如 ORD (芝加哥)、PEK (北京)。' },
      { id: 'airportName', displayName: '机场名称', apiName: 'airportName', dataType: 'string', isPrimaryKey: false, description: '官方登记的机场中英文完整名称。' },
      { id: 'city', displayName: '所在城市', apiName: 'city', dataType: 'string', isPrimaryKey: false, description: '机场所在的中心城市名称。' },
      { id: 'country', displayName: '所在国家', apiName: 'country', dataType: 'string', isPrimaryKey: false, description: '机场所在的国家名称。' },
      { id: 'latitude', displayName: '纬度', apiName: 'latitude', dataType: 'decimal', isPrimaryKey: false, description: '跑道中心区域的地理纬度坐标。' },
      { id: 'longitude', displayName: '经度', apiName: 'longitude', dataType: 'decimal', isPrimaryKey: false, description: '跑道中心区域的地理经度坐标。' },
      { id: 'timezone', displayName: '所处时区', apiName: 'timezone', dataType: 'string', isPrimaryKey: false, description: 'IANA 标准时区标识，如 America/Chicago 或 Asia/Shanghai。' }
    ],
    mapping: {
      datasetId: 'ds_airports',
      propertyMappings: {
        iataCode: 'iata_code',
        airportName: 'airport_name',
        city: 'city',
        country: 'country',
        latitude: 'latitude',
        longitude: 'longitude',
        timezone: 'timezone'
      }
    }
  },
  {
    id: 'flight',
    displayName: '航班 (Flight)',
    apiName: 'Flight',
    description: '执行特定航线、有特定预定起降时间的民航运行实例。',
    icon: 'Navigation',
    color: 'border-purple-500 bg-purple-50 text-purple-700',
    primaryKey: 'flightNumber',
    titleProperty: 'flightNumber',
    status: 'ACTIVE',
    domainId: 'operations',
    properties: [
      { id: 'flightNumber', displayName: '航班号', apiName: 'flightNumber', dataType: 'string', isPrimaryKey: true, description: '航班呼号，如 UA102、AA880。' },
      { id: 'tailNumber', displayName: '分配机尾号', apiName: 'assignedTailNumber', dataType: 'string', isPrimaryKey: false, description: '被派往执飞此航程的具体飞机的机身号。' },
      { id: 'depAirport', displayName: '起飞机场', apiName: 'departureAirportCode', dataType: 'string', isPrimaryKey: false, description: '出发港 IATA 代码。' },
      { id: 'arrAirport', displayName: '降落机场', apiName: 'arrivalAirportCode', dataType: 'string', isPrimaryKey: false, description: '目的港 IATA 代码。' },
      { id: 'scheduledDep', displayName: '计划起飞时间', apiName: 'scheduledDepartureTime', dataType: 'timestamp', isPrimaryKey: false, description: '预定推出停机坪、滑行起飞的国际标准时间。' },
      { id: 'scheduledArr', displayName: '计划到达时间', apiName: 'scheduledArrivalTime', dataType: 'timestamp', isPrimaryKey: false, description: '预定降落并接廊桥的国际标准时间。' },
      { id: 'status', displayName: '航班状态', apiName: 'status', dataType: 'string', isPrimaryKey: false, description: '当前运营状态：ON_TIME (准点), DELAYED (延误), BOARDING (登机中), CANCELLED (取消)。', sharedPropertyId: 'sp_status' },
      { id: 'assignedPilotId', displayName: '执飞飞行员ID', apiName: 'assignedPilotId', dataType: 'string', isPrimaryKey: false, description: '指派执飞此航班的主责任飞行员编号。' }
    ],
    mapping: {
      datasetId: 'ds_flights',
      propertyMappings: {
        flightNumber: 'flight_number',
        tailNumber: 'tail_number',
        depAirport: 'dep_airport',
        arrAirport: 'arr_airport',
        scheduledDep: 'scheduled_dep',
        scheduledArr: 'scheduled_arr',
        status: 'status',
        assignedPilotId: 'assigned_pilot_id'
      }
    }
  },
  {
    id: 'pilot',
    displayName: '飞行员 (Pilot)',
    apiName: 'Pilot',
    description: '持有合法飞行执照、负责航线执飞的技术人员。',
    icon: 'UserSquare2',
    color: 'border-orange-500 bg-orange-50 text-orange-700',
    primaryKey: 'pilotId',
    titleProperty: 'fullName',
    status: 'ACTIVE',
    domainId: 'operations',
    properties: [
      { id: 'pilotId', displayName: '飞行员编号', apiName: 'pilotId', dataType: 'string', isPrimaryKey: true, description: '企业内部人事唯一的飞行员员工标识。' },
      { id: 'fullName', displayName: '姓名', apiName: 'fullName', dataType: 'string', isPrimaryKey: false, description: '飞行员的标准姓名。' },
      { id: 'rank', displayName: '职级', apiName: 'rank', dataType: 'string', isPrimaryKey: false, description: '飞行资质级别：CAPTAIN (机长) 或 FIRST_OFFICER (副驾驶)。' },
      { id: 'hoursFlown', displayName: '飞行时间(小时)', apiName: 'hoursFlown', dataType: 'integer', isPrimaryKey: false, description: '累积已完成的安全民航飞行总时间。' },
      { id: 'status', displayName: '在勤状态', apiName: 'status', dataType: 'string', isPrimaryKey: false, description: '当前考勤状态：ACTIVE (在岗执飞), ON_LEAVE (休假中), RETIRED (已退休)。', sharedPropertyId: 'sp_status' },
      { id: 'hireDate', displayName: '入职日期', apiName: 'hireDate', dataType: 'date', isPrimaryKey: false, description: '初次签署劳动合同入职航空公司的日期。' }
    ],
    mapping: {
      datasetId: 'ds_pilots',
      propertyMappings: {
        pilotId: 'pilot_id',
        fullName: 'full_name',
        rank: 'pilot_rank',
        hoursFlown: 'hours_flown',
        status: 'status',
        hireDate: 'hire_date'
      }
    }
  }
];

// ==========================================
// 5. 链接类型 (Link Types)
// ==========================================
export const mockLinkTypes: LinkType[] = [
  {
    id: 'flight_departs_from_airport',
    displayName: '起飞机场关联',
    apiName: 'departureAirport',
    description: '将航班和其计划起飞港联系起来。',
    sourceObjectType: 'flight',
    targetObjectType: 'airport',
    cardinality: 'N:1',
    mapping: {
      type: 'foreign_key',
      foreignKeyMapping: {
        sourceKey: 'depAirport', // flight.depAirport
        targetKey: 'iataCode'   // airport.iataCode
      }
    }
  },
  {
    id: 'flight_arrives_at_airport',
    displayName: '降落机场关联',
    apiName: 'arrivalAirport',
    description: '将航班和其计划降落港联系起来。',
    sourceObjectType: 'flight',
    targetObjectType: 'airport',
    cardinality: 'N:1',
    mapping: {
      type: 'foreign_key',
      foreignKeyMapping: {
        sourceKey: 'arrAirport', // flight.arrAirport
        targetKey: 'iataCode'   // airport.iataCode
      }
    }
  },
  {
    id: 'flight_utilizes_aircraft',
    displayName: '航班执飞机身',
    apiName: 'assignedAircraft',
    description: '一架飞机可以执飞多个航班，每个航班只能有一架对应的物理飞机。',
    sourceObjectType: 'flight',
    targetObjectType: 'aircraft',
    cardinality: 'N:1',
    mapping: {
      type: 'foreign_key',
      foreignKeyMapping: {
        sourceKey: 'tailNumber', // flight.tailNumber
        targetKey: 'tailNumber'  // aircraft.tailNumber
      }
    }
  },
  {
    id: 'flight_assigned_pilot',
    displayName: '执飞飞行员',
    apiName: 'assignedPilot',
    description: '将执飞某一班航次的责任机组成员/主飞行员关联到对应航班上。',
    sourceObjectType: 'flight',
    targetObjectType: 'pilot',
    cardinality: 'N:1',
    mapping: {
      type: 'foreign_key',
      foreignKeyMapping: {
        sourceKey: 'assignedPilotId', // flight.assignedPilotId
        targetKey: 'pilotId'          // pilot.pilotId
      }
    }
  },
  {
    id: 'pilot_qualified_aircraft_models',
    displayName: '飞行资质机型',
    apiName: 'qualifiedAircraftModels',
    description: '多对多关联：飞行员经过资质审核和模拟机考训后，获得对某些飞机的执飞型号授权。',
    sourceObjectType: 'pilot',
    targetObjectType: 'aircraft',
    cardinality: 'M:N',
    mapping: {
      type: 'join_table',
      datasetId: 'ds_pilot_aircraft_ratings',
      joinTableMapping: {
        sourceKey: 'pilotId',                 // pilot.pilotId
        joinSourceKey: 'pilot_id',            // join_table.pilot_id
        joinTargetKey: 'aircraft_model_rating', // join_table.aircraft_model_rating
        targetKey: 'model'                    // aircraft.model (linked to rated aircraft models)
      }
    }
  }
];

// ==========================================
// 6. 操作类型 (Action Types)
// ==========================================
export const mockActionTypes: ActionType[] = [
  {
    id: 'update_flight_status',
    displayName: '更新航班状态',
    apiName: 'updateFlightStatus',
    description: '当航班发生物理变动、天气延误或允许登机时，修改航班的实时运行状态代码。',
    parameters: [
      {
        id: 'flight_param',
        displayName: '目标航班',
        dataType: 'object',
        objectTypeId: 'flight',
        isRequired: true,
        description: '需要变更状态的航班对象实体。'
      },
      {
        id: 'new_status_param',
        displayName: '新状态',
        dataType: 'string',
        isRequired: true,
        description: '将要设置的目标状态，如 ON_TIME (准点), DELAYED (延误), BOARDING (登机中), CANCELLED (取消)。'
      }
    ],
    rules: [
      {
        id: 'rule_1',
        type: 'modify_object',
        targetParameterId: 'flight_param',
        propertyEdits: [
          {
            propertyId: 'status',
            valueExpression: 'parameter.new_status_param'
          }
        ]
      }
    ],
    validationRules: [
      {
        id: 'val_1',
        displayName: '状态值验证',
        expression: 'parameter.new_status_param IN ["ON_TIME", "DELAYED", "BOARDING", "CANCELLED"]',
        errorMessage: '航班的状态值必须为以下四种之一：准点 (ON_TIME)、延误 (DELAYED)、登机中 (BOARDING)、已取消 (CANCELLED)。'
      }
    ]
  },
  {
    id: 'schedule_maintenance_check',
    displayName: '安排飞机适航维护',
    apiName: 'scheduleMaintenanceCheck',
    description: '将一台处于就绪状态的飞机设定为维护中，并记录本次高级检修的时间。',
    parameters: [
      {
        id: 'aircraft_param',
        displayName: '目标飞机',
        dataType: 'object',
        objectTypeId: 'aircraft',
        isRequired: true,
        description: '需要接受停机维护检查的飞机。'
      },
      {
        id: 'maintenance_date_param',
        displayName: '维护登记日期',
        dataType: 'date',
        isRequired: true,
        description: '开始进行停机维护检修的生效日期。'
      }
    ],
    rules: [
      {
        id: 'rule_m1',
        type: 'modify_object',
        targetParameterId: 'aircraft_param',
        propertyEdits: [
          {
            propertyId: 'status',
            valueExpression: '"MAINTENANCE"'
          },
          {
            propertyId: 'lastMaintenance',
            valueExpression: 'parameter.maintenance_date_param'
          }
        ]
      }
    ],
    validationRules: [
      {
        id: 'val_m1',
        displayName: '禁止重复维护',
        expression: 'parameter.aircraft_param.status != "MAINTENANCE"',
        errorMessage: '该飞机已经是维护中 (MAINTENANCE) 状态，无需重复触发维护安排。'
      }
    ]
  },
  {
    id: 'register_new_pilot',
    displayName: '录入新飞行员',
    apiName: 'registerNewPilot',
    description: '在航司管理系统中新建一个飞行员员工档案，并初始化入职状态。',
    parameters: [
      {
        id: 'pilot_id_param',
        displayName: '新飞行员工号',
        dataType: 'string',
        isRequired: true,
        description: '唯一的飞行员编号（建议 P 前缀）。'
      },
      {
        id: 'full_name_param',
        displayName: '姓名',
        dataType: 'string',
        isRequired: true,
        description: '飞行员身份证或执照登记的真实中文名或英文名。'
      },
      {
        id: 'rank_param',
        displayName: '初始职级',
        dataType: 'string',
        isRequired: true,
        description: '飞行员初始职级：CAPTAIN (机长) 或 FIRST_OFFICER (副驾驶)。'
      },
      {
        id: 'hire_date_param',
        displayName: '合同生效日期',
        dataType: 'date',
        isRequired: true,
        description: '飞行员合同入职生效日期。'
      }
    ],
    rules: [
      {
        id: 'rule_p1',
        type: 'create_object',
        targetObjectTypeId: 'pilot',
        propertyEdits: [
          { propertyId: 'pilotId', valueExpression: 'parameter.pilot_id_param' },
          { propertyId: 'fullName', valueExpression: 'parameter.full_name_param' },
          { propertyId: 'rank', valueExpression: 'parameter.rank_param' },
          { propertyId: 'hoursFlown', valueExpression: '0' },
          { propertyId: 'status', valueExpression: '"ACTIVE"' },
          { propertyId: 'hireDate', valueExpression: 'parameter.hire_date_param' }
        ]
      }
    ],
    validationRules: [
      {
        id: 'val_p1',
        displayName: '编号非空校验',
        expression: 'STARTS_WITH(parameter.pilot_id_param, "P")',
        errorMessage: '飞行员工号必须以大写字母 "P" 开头以符合规范。'
      }
    ]
  }
];

// ==========================================
// 7. 逻辑函数 (Function Types)
// ==========================================
export const mockFunctionTypes: FunctionType[] = [
  {
    id: 'calculate_flight_delay_duration',
    displayName: '计算航班延误时数',
    apiName: 'calculateFlightDelayDuration',
    description: '对比航班的计划起飞时间与实际起飞时间，计算实际延误时间并返回分钟数。用于监控航班准点绩效 (KPI)。',
    returnType: 'integer',
    parameters: [
      {
        name: 'flight',
        dataType: 'ObjectType',
        objectTypeId: 'flight',
        description: '需要检测的航班对象实体。',
        isRequired: true
      },
      {
        name: 'actualDepartureTime',
        dataType: 'timestamp',
        description: '航班实际离港的时间戳。',
        isRequired: true
      }
    ],
    code: `import { Function, Integer } from "@foundry/functions-api";
import { Flight } from "../objects";

export class FlightAnalyticsFunctions {
    @Function()
    public async calculateFlightDelayDuration(flight: Flight, actualDepartureTime: string): Promise<Integer> {
        if (!flight.scheduledDepartureTime) {
            return 0;
        }
        const scheduled = new Date(flight.scheduledDepartureTime).getTime();
        const actual = new Date(actualDepartureTime).getTime();
        
        // 计算时间差 (毫秒)
        const diffMs = actual - scheduled;
        if (diffMs <= 0) {
            return 0; // 准点或提前
        }
        
        // 转换为分钟
        return Math.floor(diffMs / (1000 * 60));
    }
}`,
    associatedObjectType: 'flight'
  },
  {
    id: 'validate_pilot_ratings_for_aircraft',
    displayName: '校验飞行员机型资质',
    apiName: 'validatePilotRatingsForAircraft',
    description: '在指派航班前，验证飞行员是否拥有执飞目标飞机的型号等级资质。',
    returnType: 'boolean',
    parameters: [
      {
        name: 'pilot',
        dataType: 'ObjectType',
        objectTypeId: 'pilot',
        description: '被指派的飞行员实体。',
        isRequired: true
      },
      {
        name: 'aircraft',
        dataType: 'ObjectType',
        objectTypeId: 'aircraft',
        description: '被执飞的飞机实体。',
        isRequired: true
      }
    ],
    code: `import { Function } from "@foundry/functions-api";
import { Pilot, Aircraft } from "../objects";

export class PilotValidationFunctions {
    @Function()
    public async validatePilotRatingsForAircraft(pilot: Pilot, aircraft: Aircraft): Promise<boolean> {
        // 获取该飞行员具备的所有机型资质证书
        const ratings = await pilot.qualifiedAircraftModels.get();
        const targetModel = aircraft.aircraftModel;
        
        if (!targetModel) {
            return false;
        }
        
        // 遍历并检查是否存在相同的型号代码
        return ratings.some(ratingAircraft => ratingAircraft.aircraftModel === targetModel);
    }
}`,
    associatedObjectType: 'pilot'
  },
  {
    id: 'get_active_aircrafts_by_airport',
    displayName: '获取机场在勤飞机列表',
    apiName: 'getActiveAircraftsByAirport',
    description: '根据机场三字码查询所有当前处于活跃状态、且驻场或计划飞往该机场的飞机列表。',
    returnType: 'ObjectTypeSet',
    returnObjectTypeId: 'aircraft',
    parameters: [
      {
        name: 'airport',
        dataType: 'ObjectType',
        objectTypeId: 'airport',
        description: '中心目标机场。',
        isRequired: true
      }
    ],
    code: `import { Function, ObjectSet } from "@foundry/functions-api";
import { Airport, Aircraft, Objects } from "../objects";

export class AirportOpsFunctions {
    @Function()
    public getActiveAircraftsByAirport(airport: Airport): ObjectSet<Aircraft> {
        // 加载全部飞机并过滤
        const allAircrafts = Objects.search().aircraft();
        
        // 过滤基地机场为当前机场且状态为 ACTIVE 的飞行器
        return allAircrafts.filter(ac => 
            ac.homeAirportIata.exactMatch(airport.iataCode) &&
            ac.status.exactMatch("ACTIVE")
        );
    }
}`,
    associatedObjectType: 'airport'
  }
];

// ==========================================
// 8. 业务分级域 (Ontology Domains)
// ==========================================
export const mockDomains: OntologyDomain[] = [
  {
    id: 'operations',
    displayName: '运行控制域 (Flight Operations)',
    description: '航空航线日常派班、值班执飞以及航班实时流转时效调度等核心业务。',
    color: 'purple'
  },
  {
    id: 'assets',
    displayName: '航空资产域 (Aviation Assets)',
    description: '包含核心有形资产(如飞机、基地机场)的全生命周期价值追踪、驻场维修检测和场站基础信息。',
    color: 'blue'
  }
];

