/**
 * KnowledgeArticleCreateForm — 新建知识条目（含七分类）（#147U：分类编辑面的写腿）。
 *
 * 分类是新建时的必选维度；Rule 类必须给 `rule_ref`（指向 ecos_ontology_rule 的语义根），
 * 非 Rule 类禁带——与后端 `KnowledgeCategoryPolicy` 同口径，前端只做前置拦截，不做本地放宽。
 *
 * 主题 §4.1：0 硬编码色；图标仅 lucide；文案全 t()。
 * 状态：折叠(idle) / 校验失败 / 提交中 / 成功(回显 id) / 服务端失败(显式业务码)。
 */
import React, { useState } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, Plus, X } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  KNOWLEDGE_CATEGORIES,
  type KnowledgeCategoryCode,
} from '../services/categoryFilter';
import { createArticle, validateArticleInput } from '../services/articleWrite';
import { KbApiError } from '../services/kbFetch';

const CATEGORY_LABEL_KEY: Record<KnowledgeCategoryCode, string> = {
  Fact: 'knowledge.categoryFact',
  Concept: 'knowledge.categoryConcept',
  Procedure: 'knowledge.categoryProcedure',
  Rule: 'knowledge.categoryRule',
  Case: 'knowledge.categoryCase',
  Evidence: 'knowledge.categoryEvidence',
  Pattern: 'knowledge.categoryPattern',
};

type SubmitState = 'idle' | 'submitting' | 'ok' | 'error';

export default function KnowledgeArticleCreateForm({ onCreated }: { onCreated?: () => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [category, setCategory] = useState<KnowledgeCategoryCode>('Fact');
  const [ruleRef, setRuleRef] = useState('');
  const [state, setState] = useState<SubmitState>('idle');
  const [message, setMessage] = useState<string | null>(null);

  const reset = () => {
    setTitle('');
    setContent('');
    setCategory('Fact');
    setRuleRef('');
    setState('idle');
    setMessage(null);
  };

  const submit = async () => {
    const invalid = validateArticleInput({ title, content, category, ruleRef });
    if (invalid) {
      setState('error');
      setMessage(t(invalid));
      return;
    }
    setState('submitting');
    setMessage(null);
    try {
      const created = await createArticle({ title, content, category, ruleRef });
      setState('ok');
      setMessage(`${t('knowledge.articleForm.created')} ${created.id || '-'}`);
      setTitle('');
      setContent('');
      setRuleRef('');
      onCreated?.();
    } catch (err) {
      setState('error');
      setMessage(
        err instanceof KbApiError
          ? `${t('knowledge.articleForm.error')}（${err.code ?? `HTTP ${err.status}`}）`
          : `${t('knowledge.articleForm.error')}`,
      );
    }
  };

  if (!open) {
    return (
      <div className="flex justify-end">
        <button
          type="button"
          className="px-2.5 py-1 rounded-md border text-xs inline-flex items-center gap-1"
          style={{ borderColor: styles.cardBorder, color: styles.cardText, background: styles.cardBg }}
          onClick={() => setOpen(true)}
          data-testid="knowledge-article-create-open"
        >
          <Plus className="w-3.5 h-3.5" />
          {t('knowledge.articleForm.open')}
        </button>
      </div>
    );
  }

  const inputStyle = {
    borderColor: styles.cardBorder,
    background: styles.inputBg,
    color: styles.inputText,
  } as const;

  return (
    <section
      className="rounded-md border p-3 flex flex-col gap-2"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardText }}
      data-testid="knowledge-article-create-form"
    >
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">{t('knowledge.articleForm.title')}</span>
        <button
          type="button"
          className="p-1 rounded"
          style={{ color: styles.cardTextMuted }}
          onClick={() => { setOpen(false); reset(); }}
          aria-label={t('knowledge.articleForm.close')}
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      <input
        className="w-full px-2 py-1.5 rounded-md border text-xs"
        style={inputStyle}
        placeholder={t('knowledge.articleForm.titlePlaceholder')}
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        data-testid="knowledge-article-title"
      />

      <textarea
        className="w-full px-2 py-1.5 rounded-md border text-xs min-h-[72px]"
        style={inputStyle}
        placeholder={t('knowledge.articleForm.contentPlaceholder')}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        data-testid="knowledge-article-content"
      />

      <div className="flex flex-wrap items-center gap-2">
        <label className="text-xs" style={{ color: styles.cardTextMuted }}>
          {t('knowledge.articleForm.category')}
        </label>
        <select
          className="px-2 py-1 rounded-md border text-xs"
          style={inputStyle}
          value={category}
          onChange={(e) => setCategory(e.target.value as KnowledgeCategoryCode)}
          data-testid="knowledge-article-category"
        >
          {KNOWLEDGE_CATEGORIES.map((code) => (
            <option key={code} value={code}>{t(CATEGORY_LABEL_KEY[code])}</option>
          ))}
        </select>

        {category === 'Rule' && (
          <input
            className="flex-1 min-w-[220px] px-2 py-1 rounded-md border text-xs"
            style={inputStyle}
            placeholder={t('knowledge.articleForm.ruleRefPlaceholder')}
            value={ruleRef}
            onChange={(e) => setRuleRef(e.target.value)}
            data-testid="knowledge-article-ruleref"
          />
        )}
      </div>

      {state === 'error' && message && (
        <div className="text-xs flex items-center gap-1.5" style={{ color: styles.warningText }}
             data-testid="knowledge-article-error">
          <AlertTriangle className="w-3.5 h-3.5" />
          {message}
        </div>
      )}

      {state === 'ok' && message && (
        <div className="text-xs flex items-center gap-1.5" style={{ color: styles.successText }}
             data-testid="knowledge-article-ok">
          <CheckCircle2 className="w-3.5 h-3.5" />
          {message}
        </div>
      )}

      <div className="flex justify-end">
        <button
          type="button"
          className="px-3 py-1 rounded-md border text-xs inline-flex items-center gap-1"
          style={{ borderColor: styles.accentBorder, color: styles.cardText }}
          onClick={() => void submit()}
          disabled={state === 'submitting'}
          data-testid="knowledge-article-submit"
        >
          {state === 'submitting' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
          {state === 'submitting' ? t('knowledge.articleForm.submitting') : t('knowledge.articleForm.submit')}
        </button>
      </div>
    </section>
  );
}
