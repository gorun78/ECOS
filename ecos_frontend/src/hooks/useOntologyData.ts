/**
 * useOntologyData — 本体工作台数据加载 Hook
 *
 * 从后端 API 加载本体域 (OntologyDomain)、对象类型 (ObjectType)、
 * 链接类型 (LinkType) 数据并映射为前端类型。
 *
 * 抽取自 OntologyWorkbenchLayout.tsx 的 useEffect 初始化逻辑。
 *
 * 无假真理整改：此前所有请求 `.catch(() => [])` + 失败仅 console.warn 后回空数组，
 * 页面把「加载失败」渲染成「暂无数据」（fail-open 假空态）。现在：
 *   - 主体数据（实体/关系）任一失败 ⇒ 置 error，由页面渲染错误态 + retry 按钮；
 *   - 逐实体属性为部分失败 ⇒ 收集失败计数，经 onPartialFailure 如实上报；
 *   - 域兜底源（本体列表接口）失败视为可降级（权威域源在 Layout.reloadDomains），
 *     仅记录不阻断。
 *
 * @license Apache-2.0
 */
import { useCallback, useEffect, useState } from 'react';
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

export interface UseOntologyDataState {
  /** 首次/重试加载中 */
  loading: boolean;
  /** 主体数据加载失败原因（null = 成功）；页面必须渲染错误态 + retry，不得静默空列表 */
  error: string | null;
  /** 手动重拉（错误态重试入口） */
  retry: () => void;
}

/**
 * 从后端加载本体工作台的种子数据。
 *
 * @param onLoaded - 数据加载成功回调，传入 { domains, objectTypes, linkTypes }
 * @param onPartialFailure - 部分实体属性加载失败时的如实上报（count = 失败个数）
 */
export function useOntologyData(
  onLoaded: (result: UseOntologyDataResult) => void,
  onPartialFailure?: (failedPropertyCount: number) => void,
): UseOntologyDataState {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const retry = useCallback(() => setAttempt(v => v + 1), []);

  useEffect(() => {
    let cancelled = false;
    const loadData = async () => {
      setLoading(true);
      setError(null);
      try {
        // 1. 加载本体列表（作为业务域展示的兜底源，权威源见 Layout.reloadDomains；
        //    此源失败是已登记的降级路径 —— 域列表随后由 reloadDomains 单独报错）
        const ontologies = await fetchOntologies().catch(() => [] as any[]);
        const domainList: OntologyDomain[] = (ontologies || []).map((o: any) => ({
          id: o.id || o.code,
          displayName: o.name || o.code,
          code: o.code,
          description: o.description || '',
          icon: 'FolderTree',
          color: 'border-blue-500 bg-blue-50 text-blue-700',
        }));

        // 2. 加载主本体的实体（主体数据 —— 失败必须报错，不得吞成空列表）
        const rawEntities = await fetchEntities(DEFAULT_ONTOLOGY_ID);
        const entities = rawEntities || [];

        // 3. 并发拉取各实体属性：单实体属性失败计数并如实上报（部分失败 ≠ 全量成功）
        let failedProps = 0;
        const propsResponses = await Promise.all(
          entities.map((entity: any) =>
            fetchProperties(entity.id).catch((): null => { failedProps += 1; return null; })
          )
        );
        if (failedProps > 0) {
          onPartialFailure?.(failedProps);
        }
        const entityList: ObjectType[] = entities.map((entity: any, idx: number) =>
          mapEntityToObjectType(entity, propsResponses[idx] || [])
        );

        // 4. 加载全部关系（全局端点，主体数据 —— 失败必须报错）
        const rawRels = await fetchRelationships();
        const relList: LinkType[] = (rawRels || []).map(mapRelationshipToLinkType);

        if (!cancelled) {
          onLoaded({ domains: domainList, objectTypes: entityList, linkTypes: relList });
          setError(null);
        }
      } catch (err: any) {
        if (!cancelled) {
          console.warn('Ontology API load failed:', err);
          setError(String(err?.message || err));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadData();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt]);

  return { loading, error, retry };
}
