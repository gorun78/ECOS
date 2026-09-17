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
  mapEntityToObjectType,
  mapRelationshipToLinkType,
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
        // 1. 加载本体列表（作为业务域展示的兜底源，权威源见 Layout.reloadDomains）
        const ontologies = await fetchOntologies().catch(() => [] as any[]);
        const domainList: OntologyDomain[] = (ontologies || []).map((o: any) => ({
          id: o.id || o.code,
          displayName: o.name || o.code,
          code: o.code,
          description: o.description || '',
          icon: 'FolderTree',
          color: 'border-blue-500 bg-blue-50 text-blue-700',
        }));

        // 2. 加载主本体的实体，并并发拉取各实体属性
        //    （属性端点为实体域 /api/v1/ecos/entities/{id}/properties，不再拼 ontologyId）
        const rawEntities = await fetchEntities(DEFAULT_ONTOLOGY_ID).catch((): any[] => []);
        const entities = rawEntities || [];
        const propsResponses = await Promise.all(
          entities.map((entity: any) =>
            fetchProperties(entity.id).catch((): any[] => [])
          )
        );
        const entityList: ObjectType[] = entities.map((entity: any, idx: number) =>
          mapEntityToObjectType(entity, propsResponses[idx] || [])
        );

        // 3. 加载全部关系（全局端点）
        const rawRels = await fetchRelationships().catch((): any[] => []);
        const relList: LinkType[] = (rawRels || []).map(mapRelationshipToLinkType);

        onLoaded({ domains: domainList, objectTypes: entityList, linkTypes: relList });
      } catch (err) {
        console.warn('Ontology API load failed:', err);
        onLoaded({ domains: [], objectTypes: [], linkTypes: [] });
      }
    };

    loadData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
}