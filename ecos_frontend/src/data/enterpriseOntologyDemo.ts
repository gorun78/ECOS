/**
 * enterpriseOntologyDemo — 企业运营本体 demo 数据（逻辑函数 / 接口规范 / 共享属性）
 *
 * 用途：本体工作台的「逻辑函数」「接口规范」「共享属性」三个菜单此前无后端数据源
 * （state 恒为空），此模块提供与已落库的企业运营本体（ont001，11 个实体）配套的演示数据。
 *
 * 口径说明：
 *   - 实体引用统一用本体实体主键（ent001..ent011）或实体 code（Department..ProductCategory），
 *     与 ontologyApi.mapEntityToObjectType 产出的 ObjectType 一致。
 *   - 这里是**数据**（业务对象的名称/定义），不是 UI 文案，故不进入 i18n 词条。
 *   - 操作类型（ActionType）不在此模块：它已由真实后端
 *     `/api/v1/ontology/action-types`（表 ecos_action_type）提供。
 *
 * @license Apache-2.0
 */

import type {
  FunctionType,
  InterfaceType,
  SharedProperty,
  ObjectType,
} from '../types/ontology';

// ================================================================
// 1. 逻辑函数（只读计算逻辑）
// ================================================================

export const DEMO_FUNCTION_TYPES: FunctionType[] = [
  {
    id: 'fn_emp_tenure',
    displayName: '员工司龄（月）',
    apiName: 'calcEmployeeTenure',
    description: '按入职日期计算员工截至当前的司龄月数',
    returnType: 'integer',
    associatedObjectType: 'Employee',
    parameters: [
      { name: 'empNo', dataType: 'string', description: '员工工号', isRequired: true },
    ],
    code: "SELECT (EXTRACT(YEAR FROM age(now(), hire_date)) * 12\n      + EXTRACT(MONTH FROM age(now(), hire_date)))::int\nFROM dw_employee WHERE emp_no = :empNo",
  },
  {
    id: 'fn_order_gross_margin',
    displayName: '订单毛利率',
    apiName: 'calcOrderGrossMargin',
    description: '订单金额扣除优惠后的毛利率（营收减成本的占比）',
    returnType: 'decimal',
    associatedObjectType: 'Order',
    parameters: [
      { name: 'orderNo', dataType: 'string', description: '订单编号', isRequired: true },
    ],
    code: "SELECT round((o.total_amount - o.discount_amount - sum(p.cost_price * 1)) /\n      NULLIF(o.total_amount - o.discount_amount, 0), 4)\nFROM dw_sales_order o WHERE o.order_no = :orderNo",
  },
  {
    id: 'fn_project_budget_usage',
    displayName: '项目预算执行率',
    apiName: 'calcProjectBudgetUsage',
    description: '项目实际成本占预算的比例，用于成本超支预警',
    returnType: 'decimal',
    associatedObjectType: 'Project',
    parameters: [
      { name: 'projectCode', dataType: 'string', description: '项目编码', isRequired: true },
    ],
    code: "SELECT round(actual_cost / NULLIF(budget, 0), 4)\nFROM dw_project WHERE project_code = :projectCode",
  },
  {
    id: 'fn_contract_remaining_days',
    displayName: '合同剩余天数',
    apiName: 'calcContractRemainingDays',
    description: '合同到期日与当前日期的差值（含负数，用于到期提醒）',
    returnType: 'integer',
    associatedObjectType: 'Contract',
    parameters: [
      { name: 'contractNo', dataType: 'string', description: '合同编号', isRequired: true },
    ],
    code: "\nSELECT (expire_date - current_date)::int\nFROM dw_contract WHERE contract_no = :contractNo",
  },
  {
    id: 'fn_asset_net_value_ratio',
    displayName: '资产净值率',
    apiName: 'calcAssetNetValueRatio',
    description: '资产净值占原值比例，用于资产折旧评估',
    returnType: 'decimal',
    associatedObjectType: 'Asset',
    parameters: [
      { name: 'assetCode', dataType: 'string', description: '资产编码', isRequired: true },
    ],
    code: "SELECT round(net_value / NULLIF(original_value, 0), 4)\nFROM dw_asset WHERE asset_code = :assetCode",
  },
  {
    id: 'fn_customer_credit_score',
    displayName: '客户信用评分',
    apiName: 'calcCustomerCreditScore',
    description: '按信用评级与客户等级折算的信用评分（0-100）',
    returnType: 'integer',
    associatedObjectType: 'Customer',
    parameters: [
      { name: 'customerCode', dataType: 'string', description: '客户编码', isRequired: true },
    ],
    code: "SELECT CASE credit_rating\n         WHEN 'AAA' THEN 95 WHEN 'AA' THEN 85\n         WHEN 'A' THEN 75 ELSE 60 END\n       - CASE customer_level WHEN 'A' THEN 0 WHEN 'B' THEN 5 ELSE 10 END\nFROM dw_customer WHERE customer_code = :customerCode",
  },
  {
    id: 'fn_dept_headcount',
    displayName: '部门在职人数',
    apiName: 'calcDeptHeadcount',
    description: '统计指定部门下在职状态的员工数量',
    returnType: 'integer',
    associatedObjectType: 'Department',
    parameters: [
      {
        name: 'dept',
        dataType: 'ObjectType',
        objectTypeId: 'ent001',
        description: '目标部门对象',
        isRequired: true,
      },
    ],
    code: "SELECT count(*)\nFROM dw_employee e JOIN dw_department d ON d.dept_code = e.dept_code\nWHERE d.dept_code = :dept.deptCode AND e.emp_status = 'ACTIVE'",
  },
];

// ================================================================
// 2. 接口规范（跨对象类型契约）
// ================================================================

export const DEMO_INTERFACES: InterfaceType[] = [
  {
    id: 'intf_contactable',
    displayName: '可联系实体',
    apiName: 'Contactable',
    description: '具备联系人信息的实体契约，用于统一联系人展示与联络入口',
    properties: [
      { id: 'intf_contactable_p1', displayName: '联系人', apiName: 'contact_person', dataType: 'string', isRequired: true, description: '对接联系人姓名' },
      { id: 'intf_contactable_p2', displayName: '联系电话', apiName: 'contact_phone', dataType: 'string', isRequired: true, description: '可拨通的联系电话' },
      { id: 'intf_contactable_p3', displayName: '邮箱', apiName: 'email', dataType: 'string', isRequired: false, description: '对外联系邮箱' },
    ],
  },
  {
    id: 'intf_ownable',
    displayName: '可归属实体',
    apiName: 'Ownable',
    description: '需要归属到部门或责任人的实体契约，用于权限与责任划分',
    properties: [
      { id: 'intf_ownable_p1', displayName: '归属部门编码', apiName: 'dept_code', dataType: 'string', isRequired: true, description: '负责该实体的部门编码' },
      { id: 'intf_ownable_p2', displayName: '负责人工号', apiName: 'owner_emp_no', dataType: 'string', isRequired: false, description: '第一责任人员工工号' },
    ],
  },
  {
    id: 'intf_effective_dated',
    displayName: '生效期实体',
    apiName: 'EffectiveDated',
    description: '具备起止有效期的实体契约，用于有效期校验与到期提醒',
    properties: [
      { id: 'intf_effective_dated_p1', displayName: '生效日期', apiName: 'sign_date', dataType: 'date', isRequired: true, description: '契约/合作的生效起始日' },
      { id: 'intf_effective_dated_p2', displayName: '失效日期', apiName: 'expire_date', dataType: 'date', isRequired: false, description: '契约到期日，空表示长期有效' },
    ],
  },
  {
    id: 'intf_classifiable',
    displayName: '可分类实体',
    apiName: 'Classifiable',
    description: '需要参与分类体系（目录/分类层级）的实体契约',
    properties: [
      { id: 'intf_classifiable_p1', displayName: '分类编码', apiName: 'category_code', dataType: 'string', isRequired: true, description: '所属分类的唯一编码' },
    ],
  },
  {
    id: 'intf_statusful',
    displayName: '有状态实体',
    apiName: 'Statusful',
    description: '具备业务生命周期状态的实体契约，用于状态机与流转管控',
    properties: [
      { id: 'intf_statusful_p1', displayName: '业务状态', apiName: 'status', dataType: 'string', isRequired: true, description: '当前业务生命周期状态' },
    ],
  },
];

// ================================================================
// 3. 共享属性（可被多个对象类型复用）
// ================================================================

export const DEMO_SHARED_PROPERTIES: SharedProperty[] = [
  {
    id: 'sp_contact_person',
    displayName: '联系人',
    apiName: 'contact_person',
    dataType: 'string',
    description: '统一的对客/对供联系人姓名，避免各实体各自命名',
  },
  {
    id: 'sp_contact_phone',
    displayName: '联系电话',
    apiName: 'contact_phone',
    dataType: 'string',
    description: '统一联系电话，返回前端时按安全规则脱敏',
  },
  {
    id: 'sp_sign_date',
    displayName: '签约日期',
    apiName: 'sign_date',
    dataType: 'date',
    description: '合作或合同关系建立日期，用于客户与合同共用口径',
  },
  {
    id: 'sp_owner_emp_no',
    displayName: '负责人工号',
    apiName: 'owner_emp_no',
    dataType: 'string',
    description: '实体第一责任人，统一指向员工主数据',
  },
  {
    id: 'sp_dept_code',
    displayName: '归属部门编码',
    apiName: 'dept_code',
    dataType: 'string',
    description: '实体归属部门，统一指向部门主数据',
  },
  {
    id: 'sp_status',
    displayName: '业务状态',
    apiName: 'status',
    dataType: 'string',
    description: '统一生命周期状态字段，各实体状态枚举由其自身字典约束',
  },
];

// ================================================================
// 4. Demo 交叉引用绑定
//
// 后端的对象类型不返回 interfaces / 属性 sharedPropertyId，若不补绑定，
// 「接口规范」的"实现对象"与「共享属性」的"绑定对象类型"将恒为空，
// 演示数据会显得残缺。此处按实体 code → 绑定关系补齐。
// ================================================================

/** 实体 code → 实现的接口 id 列表 */
const INTERFACE_BINDINGS: Record<string, string[]> = {
  Customer: ['intf_contactable', 'intf_effective_dated', 'intf_statusful'],
  Supplier: ['intf_contactable', 'intf_statusful'],
  Employee: ['intf_ownable'],
  Project: ['intf_ownable', 'intf_statusful'],
  Asset: ['intf_ownable', 'intf_statusful'],
  Contract: ['intf_effective_dated', 'intf_statusful'],
  Product: ['intf_classifiable', 'intf_statusful'],
  ProductCategory: ['intf_classifiable'],
  Department: ['intf_statusful'],
};

/** 实体 code → { 属性 apiName: 共享属性 id } */
const SHARED_PROPERTY_BINDINGS: Record<string, Record<string, string>> = {
  Customer: {
    contact_name: 'sp_contact_person',
    contact_phone: 'sp_contact_phone',
    sign_date: 'sp_sign_date',
    customer_status: 'sp_status',
  },
  Supplier: {
    contact_person: 'sp_contact_person',
    contact_phone: 'sp_contact_phone',
    supplier_status: 'sp_status',
  },
  Contract: { sign_date: 'sp_sign_date', contract_status: 'sp_status' },
  Project: { dept_code: 'sp_dept_code', owner_emp_no: 'sp_owner_emp_no', project_status: 'sp_status' },
  Employee: { dept_code: 'sp_dept_code' },
  Asset: { dept_code: 'sp_dept_code', asset_status: 'sp_status' },
  SalesOpportunity: { owner_emp_no: 'sp_owner_emp_no' },
  Department: { dept_status: 'sp_status' },
  Product: { product_status: 'sp_status' },
};

/**
 * 把 demo 的接口实现关系与共享属性引用补到对象类型上。
 *
 * @param objectTypes 后端映射出的对象类型列表
 * @returns 补齐 interfaces / sharedPropertyId 后的新列表（不修改入参）
 */
export function applyEnterpriseDemoBindings(objectTypes: ObjectType[]): ObjectType[] {
  return objectTypes.map(ot => {
    const interfaceIds = INTERFACE_BINDINGS[ot.apiName];
    const propBindings = SHARED_PROPERTY_BINDINGS[ot.apiName];
    if (!interfaceIds && !propBindings) {
      return ot;
    }
    return {
      ...ot,
      interfaces: interfaceIds ?? ot.interfaces,
      properties: propBindings
        ? ot.properties.map(p =>
            propBindings[p.apiName] ? { ...p, sharedPropertyId: propBindings[p.apiName] } : p,
          )
        : ot.properties,
    };
  });
}
