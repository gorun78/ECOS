/**
 * useOntologyData — 本体工作台数据加载 Hook
 *
 * 从后端 API 加载本体域 (OntologyDomain)、对象类型 (ObjectType)、
 * 链接类型 (LinkType) 数据并映射为前端类型。
 *
 * 抽取自 OntologyWorkbenchLayout.tsx 的 useEffect 初始化逻辑。
 *
 * @license Apache-2.0
 */
import { useEffect } from 'react';
import {
  fetchOntologies,
  fetchEntities,
  fetchProperties,
  fetchRelationships,
  DEFAULT_ONTOLOGY_ID,
} from '../services/ontologyApi';
import type {
  ObjectType,
  LinkType,
  OntologyDomain,
} from '../types/ontology';

interface UseOntologyDataResult {
  domains: OntologyDomain[];
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
}

/**
 * 从后端加载本体工作台的种子数据。
 *
 * @param onLoaded - 数据加载完成回调，传入 { domains, objectTypes, linkTypes }
 */
export function useOntologyData(
  onLoaded: (result: UseOntologyDataResult) => void,
) {
  useEffect(() => {
    const loadData = async () => {
      try {
        // 1. Load ontologies (domains)
        const ontologies = await fetchOntologies().catch(() => [] as any[]);
        const domainList: OntologyDomain[] = (ontologies || []).map((o: any) => ({
          id: o.id || o.code,
          displayName: o.name || o.code,
          code: o.code,
          description: o.description || '',
          icon: 'FolderTree',
          color: 'border-blue-500 bg-blue-50 text-blue-700',
        }));

        // 2. Load entities for the primary ontology
        const rawEntities = await fetchEntities(DEFAULT_ONTOLOGY_ID).catch(() => []);
        const entityList: ObjectType[] = [];
        for (const e of (rawEntities || [])) {
          const entity = e as any;
          let props: { id: string; displayName: string; apiName: string; dataType: string; isPrimaryKey: boolean; description: string }[] = [];
          try {
            const rawProps = await fetchProperties(entity.id, DEFAULT_ONTOLOGY_ID).catch(() => []);
            props = (rawProps || []).map((p: any) => ({
              id: p.id || p.apiName || p.name,
              displayName: p.displayName || p.name || p.apiName,
              apiName: p.apiName || p.name,
              dataType: p.dataType || 'string',
              isPrimaryKey: p.isPrimaryKey || p.primaryKey || false,
              description: p.description || '',
            }));
          } catch { /* entity may have no properties */ }

          entityList.push({
            id: entity.id,
            displayName: entity.name || entity.code,
            apiName: entity.code || entity.name,
            description: entity.description || '',
            icon: entity.entityType === 'MASTER' ? 'Database' : 'FileText',
            color: entity.entityType === 'MASTER'
              ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
              : 'border-teal-500 bg-teal-50 text-teal-700',
            primaryKey: props.find(p => p.isPrimaryKey)?.id || 'id',
            titleProperty: props.length > 0 ? props[0].id : 'id',
            status: 'PUBLISHED',
            properties: props,
            mapping: entity.mapping || null,
            domainId: entity.domainId || null,
          } as ObjectType);
        }

        // 3. Load relationships
        const rawRels = await fetchRelationships(DEFAULT_ONTOLOGY_ID).catch(() => []);
        const relList: LinkType[] = (rawRels || []).map((r: any) => ({
          id: r.id,
          displayName: r.name || `${r.source_entity_id}→${r.target_entity_id}`,
          apiName: r.code || '',
          description: '',
          sourceObjectType: r.source_entity_id,
          targetObjectType: r.target_entity_id,
          cardinality: (r.relationship_type === 'ONE_TO_ONE' ? '1:1' :
                        r.relationship_type === 'MANY_TO_MANY' ? 'N:N' : '1:N') as any,
          mapping: { type: 'foreign_key' as const, foreignKeyMapping: {} },
        })) as LinkType[];

        onLoaded({ domains: domainList, objectTypes: entityList, linkTypes: relList });
      } catch (err) {
        console.warn('Ontology API load failed:', err);
        onLoaded({ domains: [], objectTypes: [], linkTypes: [] });
      }
    };

    loadData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
}
