/**
 * object-explorer shared types — moved verbatim from ObjectExplorerView.tsx
 * @license Apache-2.0
 */
import type { LinkType, ObjectType, PropertyType } from '../../types/ontology';

export interface SavedSearch {
  id: string;
  name: string;
  objectTypeId: string;
  filters: FilterQuery[];
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}

export interface FilterQuery {
  propertyId: string;
  operator: 'equals' | 'contains' | 'gt' | 'lt' | 'is_empty' | 'is_not_empty';
  value: string;
}

export interface ResolvedRelation {
  linkType: LinkType;
  direction: 'forward' | 'reverse';
  otherObjectType: ObjectType;
  instances: Array<Record<string, unknown>>;
  /** join_table 映射无解析端点：instances 恒为空不代表「无关联」，UI 必须如实标注（无假真理） */
  resolutionUnsupported?: boolean;
}

export interface AnalyticsBucket {
  name: string;
  count: number;
  percentage: string;
}

export type AnalyticsData =
  | { property: PropertyType; data: AnalyticsBucket[] }
  | unknown[];
