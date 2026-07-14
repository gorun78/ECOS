/**
 * 本体 (Ontology) 类型定义
 *
 * 从 ceos_new 原型 (src/types.ts) 迁移而来的 Palantir Foundry 风格本体模型，
 * 供 c2eos 正式工程的本体工作台使用。
 *
 * 注意：
 * - 已去除 mockData / localStorage 相关逻辑，本文件仅包含纯类型定义。
 * - ObjectType.mapping 改为可选（后端不一定返回）。
 * - 所有 interface 与 type 均导出。
 */

// ================================================================
// 属性 (Property)
// ================================================================

export type PropertyDataType =
  | "string"
  | "integer"
  | "decimal"
  | "boolean"
  | "date"
  | "timestamp"
  | "geopoint";

/** 本体属性定义 */
export interface PropertyType {
  id: string;
  displayName: string;
  apiName: string;
  dataType: PropertyDataType;
  isPrimaryKey: boolean;
  description: string;
  /** 可选 — 引用的共享属性 ID */
  sharedPropertyId?: string;
}

// ================================================================
// 对象类型 (ObjectType)
// ================================================================

export type ObjectTypeStatus = "DRAFT" | "ACTIVE" | "DEPRECATED";

/**
 * 对象类型 ↔ 数据集的属性映射
 * objectPropertyId -> datasetColumnName
 */
export interface PropertyMapping {
  datasetId: string;
  propertyMappings: Record<string, string>;
}

/** 对象类型 — 本体的核心实体定义 */
export interface ObjectType {
  id: string;
  displayName: string;
  apiName: string;
  description: string;
  /** Lucide 图标名 */
  icon: string;
  /** Tailwind 颜色类名（如 "bg-blue-500"） */
  color: string;
  /** 主键属性 ID */
  primaryKey: string;
  /** 用作标题/标签的属性 ID */
  titleProperty: string;
  properties: PropertyType[];
  /**
   * 对象类型到数据集的映射。
   * 可选 — 后端不一定返回。
   */
  mapping?: PropertyMapping;
  status: ObjectTypeStatus;
  /** 实现的接口 ID 列表 */
  interfaces?: string[];
  /** 所属域 ID */
  domainId?: string;
}

// ================================================================
// 域 (Domain)
// ================================================================

/** 本体域 — 本体的顶层组织单元 */
export interface OntologyDomain {
  id: string;
  displayName: string;
  description: string;
  /** 颜色名（如 "blue"、"emerald"、"amber"、"purple"、"rose"、"indigo"） */
  color: string;
}

// ================================================================
// 链接类型 (LinkType)
// ================================================================

export type LinkCardinality = "1:1" | "1:N" | "N:1" | "M:N";

/** 外键映射 — 用于 foreign_key 类型的链接 */
export interface ForeignKeyMapping {
  /** 源对象中的属性 ID */
  sourceKey: string;
  /** 目标对象中的属性 ID */
  targetKey: string;
}

/** 连接表映射 — 用于 join_table 类型的链接 */
export interface JoinTableMapping {
  /** 源对象中的属性 ID */
  sourceKey: string;
  /** 连接表中指向源的列 */
  joinSourceKey: string;
  /** 连接表中指向目标的列 */
  joinTargetKey: string;
  /** 目标对象中的属性 ID */
  targetKey: string;
}

/** 链接类型 ↔ 物理数据的映射 */
export interface LinkMapping {
  type: "foreign_key" | "join_table";
  /** join_table 时必填 */
  datasetId?: string;
  foreignKeyMapping?: ForeignKeyMapping;
  joinTableMapping?: JoinTableMapping;
}

/** 链接类型 — 对象类型之间的关系定义 */
export interface LinkType {
  id: string;
  displayName: string;
  apiName: string;
  description: string;
  /** 源对象类型 ID */
  sourceObjectType: string;
  /** 目标对象类型 ID */
  targetObjectType: string;
  cardinality: LinkCardinality;
  mapping: LinkMapping;
}

// ================================================================
// 动作类型 (ActionType)
// ================================================================

export type ActionParamDataType =
  | "string"
  | "integer"
  | "decimal"
  | "boolean"
  | "date"
  | "object";

/** 动作参数 */
export interface ActionParameter {
  id: string;
  displayName: string;
  dataType: ActionParamDataType;
  /** dataType === "object" 时必填 */
  objectTypeId?: string;
  isRequired: boolean;
  description: string;
}

export type ActionRuleType = "create_object" | "modify_object" | "delete_object";

/** 动作规则 */
export interface ActionRule {
  id: string;
  type: ActionRuleType;
  /** create_object 时使用 */
  targetObjectTypeId?: string;
  /** 表示要修改/删除的对象的参数 ID */
  targetParameterId?: string;
  propertyEdits?: Array<{
    propertyId: string;
    /** 值表达式，如 "parameter.newStatus" 或 "now()" */
    valueExpression: string;
  }>;
}

/** 动作校验规则 */
export interface ActionValidationRule {
  id: string;
  displayName: string;
  /** 可视化公式描述 */
  expression: string;
  errorMessage: string;
}

/** 动作表单布局 */
export interface ActionFormLayout {
  sections: Array<{
    title: string;
    parameterIds: string[];
  }>;
  buttonText?: string;
}

/** 动作类型 — 对本体对象的写操作定义 */
export interface ActionType {
  id: string;
  displayName: string;
  apiName: string;
  description: string;
  parameters: ActionParameter[];
  rules: ActionRule[];
  validationRules: ActionValidationRule[];
  formLayout?: ActionFormLayout;
}

// ================================================================
// 函数类型 (FunctionType)
// ================================================================

/** 函数参数 */
export interface FunctionParameter {
  name: string;
  /** 如 "string" | "integer" | "decimal" | "boolean" | "date" | "ObjectType" | "ObjectTypeSet" */
  dataType: string;
  objectTypeId?: string;
  description: string;
  isRequired: boolean;
}

/** 函数类型 — 对本体对象的只读计算逻辑定义 */
export interface FunctionType {
  id: string;
  displayName: string;
  apiName: string;
  description: string;
  /** 如 "string" | "integer" | "boolean" | "date" | "ObjectType" | "ObjectTypeSet" */
  returnType: string;
  returnObjectTypeId?: string;
  parameters: FunctionParameter[];
  code: string;
  associatedObjectType?: string;
}

// ================================================================
// 接口类型 (InterfaceType)
// ================================================================

/** 接口属性 */
export interface InterfaceProperty {
  id: string;
  displayName: string;
  apiName: string;
  dataType: PropertyDataType;
  isRequired: boolean;
  description: string;
}

/** 接口类型 — 跨对象类型的契约定义 */
export interface InterfaceType {
  id: string;
  displayName: string;
  apiName: string;
  description: string;
  properties: InterfaceProperty[];
}

// ================================================================
// 共享属性 (SharedProperty)
// ================================================================

/** 共享属性 — 可被多个对象类型复用的属性定义 */
export interface SharedProperty {
  id: string;
  displayName: string;
  apiName: string;
  dataType: PropertyDataType;
  description: string;
}

// ================================================================
// 数据集 (Dataset)
// ================================================================

/** 数据集列定义 */
export interface DatasetColumn {
  name: string;
  type: string;
}

/** 数据集 — 物理数据表的元数据描述 */
export interface Dataset {
  id: string;
  name: string;
  path: string;
  columns: DatasetColumn[];
  sampleData: Record<string, any>[];
}
