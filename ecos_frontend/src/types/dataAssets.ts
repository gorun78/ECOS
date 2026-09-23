/**
 * PMO-data10 数据资产 + 分级分类前端契约（与 data-engine AssetController REST 对应）。
 *
 * 强类型 TS 契约 — 禁止 any（前端开发规范 §四）。
 */

/** 资产列表项（对齐 data-engine `DataAssetVO` ）。 */
export interface DataAssetVO {
  assetId: string;
  resourceId: string;
  resourceName?: string | null;
  resourceType?: string | null;
  datasourceId?: string | null;
  assetName: string;
  businessDesc?: string | null;
  owner?: string | null;
  ownerOrg?: string | null;
  dataGrain?: string | null;
  categoryId?: string | null;
  categoryName?: string | null;
  sensitivityLevel: string;
  levelName?: string | null;
  categoryStatus?: string | null;
  lastTaggedBy?: string | null;
  lastTaggedAt?: string | null;
  domain?: string | null;
  description?: string | null;
  fieldCount?: number | null;
  recordCount?: number | null;
  layer?: string | null;
  zone?: string | null;
  lastSyncTime?: string | null;
  confirmedFieldCount?: number | null;
}

/** 字段级敏感度条目（对齐 data-engine `DataAssetFieldVO` ）。 */
export interface DataAssetFieldVO {
  fieldAssetId: string;
  assetId: string;
  fieldId: string;
  fieldName: string;
  fieldType?: string | null;
  dataType: string;
  fieldSensitivity: string;
  maskStrategy: string;
  recommendLevel?: string | null;
  recommendSource?: string | null;
  confirmed: boolean;
}

/** 4 级敏感度字典（对齐 data-engine `ecos_data.ecos_data_level_def` ）。 */
export interface DataLevelDef {
  levelCode: string;
  levelValue?: number;
  levelName: string;
  levelDescription?: string | null;
  maskStrategyJson?: string | null;
  rlsStrategyJson?: string | null;
  sortOrder?: number;
}

/** 业务分类树节点（对齐 data-engine `ecos_data.ecos_data_category_tree` ）。 */
export interface DataCategoryTreeItem {
  categoryId: string;
  name: string;
  parentId: string | null;
  level?: number;
  description?: string | null;
  icon?: string | null;
}