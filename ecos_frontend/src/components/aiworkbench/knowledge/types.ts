/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface MetadataAsset {
  id: string;
  source: 'integration' | 'ontology' | 'security';
  name: string;
  type: string;
  recordsOrFields: string;
  syncStatus: 'synced' | 'pending' | 'out_of_date';
  chunksCount: number;
  lastSynced: string;
}

export type KnowledgeSubTab = 'architecture' | 'sync' | 'lineage' | 'ontology' | 'index' | 'rag';
