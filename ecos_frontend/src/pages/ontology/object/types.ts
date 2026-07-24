/**
 * ObjectTypeDetail Tab 组件共享类型
 * @license Apache-2.0
 */
import type { ObjectType, PropertyType, Dataset, SharedProperty, InterfaceType, OntologyDomain } from '../../../types/ontology';

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
  handleTogglePrimaryKey: (propId: string) => void;
  handlePropertyFieldChange: (propId: string, field: keyof PropertyType, value: any) => void;
  handleRemoveProperty: (propId: string) => void;
  sharedProperties: SharedProperty[];
}

export interface MetadataTabProps extends ObjectDetailTabProps {
  handleMetaChange: (key: keyof ObjectType, value: any) => void;
  domains: OntologyDomain[];
  interfaces: InterfaceType[];
}

export interface MappingTabProps extends ObjectDetailTabProps {
  datasets: Dataset[];
  selectedDataset: Dataset | undefined;
  handleDatasetChange: (datasetId: string) => void;
  handleAutoMap: () => void;
  handlePropMappingChange: (propId: string, colName: string) => void;
  mappingDirty: boolean;
  onSaveMapping: () => void;
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
