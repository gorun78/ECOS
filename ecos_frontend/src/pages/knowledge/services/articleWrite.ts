/**
 * articleWrite.ts — 知识条目写端契约（#147U：分类编辑面的写腿）。
 *
 * 后端：`POST /api/v1/knowledge/articles`（`KnowledgeArticleController.createArticle`）
 *   - body 为 `KnowledgeArticle`：title / content / knowledgeCategory / ruleRef …
 *   - 分类闭集校验与「Rule 类必须带 rule_ref、非 Rule 类禁带」由后端
 *     `KnowledgeCategoryPolicy` 强制；前端只负责不让非法组合发出去，**不做本地放宽**。
 *
 * 错误态：`kbFetchData` 在 4xx/5xx 抛 `KbApiError`（含业务码），调用方必须显式展示，
 * 禁把失败渲染成「已保存」。
 */
import { kbFetchData } from './kbFetch';
import { KNOWLEDGE_BASE } from '../../../api/knowledgeClient';
import type { KnowledgeCategoryCode } from './categoryFilter';

export interface CreateArticleInput {
  title: string;
  content: string;
  category: KnowledgeCategoryCode;
  /** 仅 Rule 类必填（指向 ecos_ontology_rule 的语义根）；其余分类必须为空 */
  ruleRef?: string;
}

export interface CreatedArticle {
  id: string;
  title: string;
}

/** 前端侧前置校验：与后端 KnowledgeCategoryPolicy 同口径，避免确定性 400 打到服务端。 */
export function validateArticleInput(input: CreateArticleInput): string | null {
  if (!input.title || !input.title.trim()) {
    return 'knowledge.articleForm.errorTitleRequired';
  }
  if (!input.content || !input.content.trim()) {
    return 'knowledge.articleForm.errorContentRequired';
  }
  const isRule = input.category === 'Rule';
  const ruleRef = (input.ruleRef ?? '').trim();
  if (isRule && !ruleRef) {
    return 'knowledge.articleForm.errorRuleRefRequired';
  }
  if (!isRule && ruleRef) {
    return 'knowledge.articleForm.errorRuleRefForbidden';
  }
  return null;
}

export async function createArticle(input: CreateArticleInput): Promise<CreatedArticle> {
  const isRule = input.category === 'Rule';
  const body: Record<string, unknown> = {
    title: input.title.trim(),
    content: input.content,
    knowledgeCategory: input.category,
  };
  if (isRule) {
    body.ruleRef = (input.ruleRef ?? '').trim();
  }
  const data = await kbFetchData<Record<string, unknown>>(`${KNOWLEDGE_BASE}/articles`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return {
    id: String(data?.id ?? ''),
    title: String(data?.title ?? input.title.trim()),
  };
}
