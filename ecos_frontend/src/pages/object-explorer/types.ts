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
}

export interface AnalyticsBucket {
  name: string;
  count: number;
  percentage: string;
}

export type AnalyticsData =
  | { property: PropertyType; data: AnalyticsBucket[] }
  | unknown[];
