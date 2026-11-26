/**
 * 场景上下文「其他知识」候选客户端（V367 批次 E）。
 *
 * <p>两类：知识文章（KNOWLEDGE_ARTICLE）与认知资产实例（COGNITIVE_ASSET）。
 * 单一通道：workspace `/available/knowledge-articles`、`/available/cognitive-assets`。</p>
 */
import { apiFetchData } from '../api';
import { WORKSPACE_ANALYSES_BASE } from './scenarioAnalysisBase';

export interface ContextAssetOption {
  id: string;
  name?: string;
  type?: string;
}

export function fetchKnowledgeArticleOptions(): Promise<ContextAssetOption[]> {
  return apiFetchData<ContextAssetOption[]>(
    `${WORKSPACE_ANALYSES_BASE}/scenarios/available/knowledge-articles`,
  );
}

export function fetchCognitiveAssetOptions(): Promise<ContextAssetOption[]> {
  return apiFetchData<ContextAssetOption[]>(
    `${WORKSPACE_ANALYSES_BASE}/scenarios/available/cognitive-assets`,
  );
}
