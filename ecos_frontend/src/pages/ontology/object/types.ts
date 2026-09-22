/**
 * ObjectTypeDetail Tab 组件共享类型
 * @license Apache-2.0
 */
import type { ObjectType, PropertyType, Dataset, SharedProperty, InterfaceType, OntologyDomain } from '../../../types/ontology';
import type { DocAnchor, DocAnchorType, MappingValidationReport } from '../../../types/ontology';

export interface ObjectDetailTabProps {
  objectType: ObjectType;
  onUpdate: (updated: ObjectType) => void;
}

export interface PropertiesTabProps extends ObjectDetailTabProps {
  newPropName: string;
  setNewPropName: (v: string) => void;
  newPropType: 'string' | 'integer' | 'decimal' | 'boolean' | 'date' | 'timestamp' | 'geopoint';
  setNewPropType: (v: 'string' | 'integer' | 'decimal' | 'boolean' | 'date' | 'timestamp' | 'geopoint') => void;
  handleAddProperty: () => void;
  /** 切换属性标志位（主键 ↔ unique_flag；必填 ↔ required_flag） */
  handleTogglePropertyFlag: (propId: string, field: 'isPrimaryKey' | 'required') => void;
  handlePropertyFieldChange: (propId: string, field: keyof PropertyType, value: any) => void;
  handleRemoveProperty: (propId: string) => void;
  sharedProperties: SharedProperty[];
}

/**
 * 基础信息 Tab props —— 不继承 ObjectDetailTabProps：
 * 该 Tab 只读 objectType 并通过 handleMetaChange 回写，不消费 onUpdate，
 * 声明独立契约可避免传入无用的 onUpdate。
 */
export interface MetadataTabProps {
  objectType: ObjectType;
  handleMetaChange: (key: keyof ObjectType, value: any) => void;
  domains: OntologyDomain[];
  interfaces: InterfaceType[];
  /** 基础信息是否存在未保存改动 */
  metaDirty: boolean;
  /** 保存请求进行中（禁用按钮防重复提交） */
  metaSaving: boolean;
  /** 保存基础信息（基础字段 + 域归属） */
  onSaveMetadata: () => void;
}

export interface MappingTabProps extends ObjectDetailTabProps {
  datasets: Dataset[];
  selectedDataset: Dataset | undefined;
  handleDatasetChange: (datasetId: string) => void;
  handleAutoMap: () => void;
  handlePropMappingChange: (propId: string, colName: string) => void;
  mappingDirty: boolean;
  onSaveMapping: () => void;
  /** W2 新增：当前非结构化文档锚点（TABLE 类型时为 undefined，Tab 内不显示输入区） */
  docAnchor?: DocAnchor;
  /** W2 新增：锚点类型（默认 TABLE） */
  docAnchorType?: DocAnchorType;
  /** W2 新增：修改锚点（任意字段变化均会置位 mappingDirty） */
  handleDocAnchorChange?: (anchor: DocAnchor | null) => void;
  /** W2 新增：修改锚点类型（切换类型时保留锚点输入值以便回填） */
  handleDocAnchorTypeChange?: (type: DocAnchorType) => void;
  /** W2 新增：「校验映射」按钮点击回调（触发 C4 校验，对象详情层负责消费报告） */
  onValidateMappings?: () => void;
  /** W2 新增：校验按钮执行中禁用 */
  validating?: boolean;
  /** W2 新增：校验报告（点击「校验映射」后由父层装载；undefined = 尚未校验） */
  lastValidation?: MappingValidationReport;
  /** W2 新增：关闭校验报告弹层（不传则不渲染 Modal） */
  onDismissValidation?: () => void;
}

export interface LinksTabProps {
  objectType: ObjectType;
  relatedLinks: any[];
  onNavigateToLink: (linkId: string) => void;
}

export interface ActionsTabProps {
  objectType: ObjectType;
  relatedActions: any[];
  onNavigateToAction: (actionId: string) => void;
}
