/**
 * articleWrite.logic.test.ts — 新建知识条目表单的前置校验（#147U 写腿，纯逻辑、无需 DOM）。
 *
 * 与后端 `KnowledgeCategoryPolicy` 同口径：Rule 必带 rule_ref、非 Rule 禁带；
 * 前端只做前置拦截，不本地放宽（非法组合仍由后端 400 兜底）。
 */
import { describe, it, expect } from 'vitest';
import { validateArticleInput } from '../../services/articleWrite';
import { categoryFilterPath } from '../../services/categoryFilter';

describe('validateArticleInput', () => {
  it('标题/正文缺失分别给出对应错误键', () => {
    expect(validateArticleInput({ title: ' ', content: 'x', category: 'Fact' }))
      .toBe('knowledge.articleForm.errorTitleRequired');
    expect(validateArticleInput({ title: 't', content: '', category: 'Fact' }))
      .toBe('knowledge.articleForm.errorContentRequired');
  });

  it('Rule 类必须带 rule_ref', () => {
    expect(validateArticleInput({ title: 't', content: 'c', category: 'Rule' }))
      .toBe('knowledge.articleForm.errorRuleRefRequired');
    expect(validateArticleInput({ title: 't', content: 'c', category: 'Rule', ruleRef: '  ' }))
      .toBe('knowledge.articleForm.errorRuleRefRequired');
    expect(validateArticleInput({ title: 't', content: 'c', category: 'Rule', ruleRef: 'rule-1' }))
      .toBeNull();
  });

  it('非 Rule 类携带 rule_ref 判非法（防影子规则）', () => {
    expect(validateArticleInput({ title: 't', content: 'c', category: 'Fact', ruleRef: 'rule-1' }))
      .toBe('knowledge.articleForm.errorRuleRefForbidden');
    expect(validateArticleInput({ title: 't', content: 'c', category: 'Fact' })).toBeNull();
  });
});

describe('categoryFilterPath（读端路径，纯逻辑）', () => {
  it('分类必填，且走显式 by-category 子路径', () => {
    expect(categoryFilterPath('')).toBeNull();
    const path = categoryFilterPath('Rule') ?? '';
    expect(path).toContain('/knowledge/articles/by-category');
    expect(path).toContain('category=Rule');
    expect(path).toContain('limit=20');
  });
});
