/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Shared types & pure helpers for BusinessObjectExplorer (H6-T4 split).

import { ObjectType } from '../../types/ontology';

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
  linkType: import('../../types/ontology').LinkType;
  direction: 'forward' | 'reverse';
  otherObjectType: ObjectType;
  instances: any[];
}

/**
 * Normalise the various possible shapes of the `/api/v1/ontology/data`
 * response payload into a flat ObjectType[] array.
 *
 * Handles: bare arrays, `{ data: [...] }`, and objects that nest the list
 * under a key such as `objectTypes` / `objects` / `list` / `records`.
 * Items missing a stable `id` are dropped so they cannot corrupt the
 * dedup-by-id merge performed later.
 */
export function normalizeObjectTypes(payload: any): ObjectType[] {
  let raw: any[] | null = null;
  if (Array.isArray(payload)) {
    raw = payload;
  } else if (payload && typeof payload === 'object') {
    if (Array.isArray(payload.data)) {
      raw = payload.data;
    } else {
      for (const key of ['objectTypes', 'objects', 'objectList', 'list', 'records', 'items']) {
        if (Array.isArray(payload[key])) { raw = payload[key]; break; }
      }
    }
  }
  if (!raw) return [];
  return raw.filter((it: any) => it && typeof it === 'object' && typeof it.id === 'string');
}
