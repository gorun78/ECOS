/**
 * KnowledgeArticleCategoryEditor — 修改既有知识条目分类（#147U 编辑腿写面）。
 *
 * 只改分类维度：条目 ID + 七分类 +（Rule 时）rule_ref。前置校验与新建同口径，
 * 非法组合由后端 400 兜底；条目不存在由后端 404 兜底，前端只如实展示业务码，禁当成功。
 *
 * 主题 §4.1：0 硬编码色；图标仅 lucide；文案全 t()。
 */
import React, { useState } from 'react';
import { AlertTriangle, CheckCircle2, Loader2, Pencil, X } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { KNOWLEDGE_CATEGORIES, type KnowledgeCategoryCode } from '../services/categoryFilter';
import { updateArticleCategory, validateCategoryChange } from '../services/articleWrite';
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

type SaveState = 'idle' | 'saving' | 'ok' | 'error';

export default function KnowledgeArticleCategoryEditor({ defaultArticleId = '' }: { defaultArticleId?: string }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [open, setOpen] = useState(false);
  const [articleId, setArticleId] = useState(defaultArticleId);
  const [category, setCategory] = useState<KnowledgeCategoryCode>('Fact');
  const [ruleRef, setRuleRef] = useState('');
  const [state, setState] = useState<SaveState>('idle');
  const [message, setMessage] = useState<string | null>(null);

  const save = async () => {
    const invalid = validateCategoryChange({ articleId, category, ruleRef });
    if (invalid) {
      setState('error');
      setMessage(t(invalid));
      return;
    }
    setState('saving');
    setMessage(null);
    try {
      const updated = await updateArticleCategory({ articleId, category, ruleRef });
      setState('ok');
      setMessage(`${t('knowledge.articleEdit.ok')} ${updated.id}`);
    } catch (err) {
      setState('error');
      setMessage(
        err instanceof KbApiError
          ? `${t('knowledge.articleEdit.error')}（${err.code ?? `HTTP ${err.status}`}）`
          : t('knowledge.articleEdit.error'),
      );
    }
  };

  const inputStyle = { borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText } as const;

  if (!open) {
    return (
      <div className="flex justify-end">
        <button
          type="button"
          className="px-2.5 py-1 rounded-md border text-xs inline-flex items-center gap-1"
          style={{ borderColor: styles.cardBorder, color: styles.cardText, background: styles.cardBg }}
          onClick={() => setOpen(true)}
          data-testid="knowledge-article-edit-open"
        >
          <Pencil className="w-3.5 h-3.5" />
          {t('knowledge.articleEdit.open')}
        </button>
      </div>
    );
  }

  return (
    <section
      className="rounded-md border p-3 flex flex-col gap-2"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardText }}
      data-testid="knowledge-article-edit-form"
    >
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">{t('knowledge.articleEdit.title')}</span>
        <button
          type="button"
          className="p-1 rounded"
          style={{ color: styles.cardTextMuted }}
          onClick={() => setOpen(false)}
          aria-label={t('knowledge.articleEdit.close')}
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <input
          className="flex-1 min-w-[220px] px-2 py-1.5 rounded-md border text-xs"
          style={inputStyle}
          placeholder={t('knowledge.articleEdit.idPlaceholder')}
          value={articleId}
          onChange={(e) => setArticleId(e.target.value)}
          data-testid="knowledge-article-edit-id"
        />
        <select
          className="px-2 py-1 rounded-md border text-xs"
          style={inputStyle}
          value={category}
          onChange={(e) => setCategory(e.target.value as KnowledgeCategoryCode)}
          data-testid="knowledge-article-edit-category"
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
            data-testid="knowledge-article-edit-ruleref"
          />
        )}
      </div>

      {state === 'error' && message && (
        <div className="text-xs flex items-center gap-1.5" style={{ color: styles.warningText }}
             data-testid="knowledge-article-edit-error">
          <AlertTriangle className="w-3.5 h-3.5" />
          {message}
        </div>
      )}

      {state === 'ok' && message && (
        <div className="text-xs flex items-center gap-1.5" style={{ color: styles.successText }}
             data-testid="knowledge-article-edit-ok">
          <CheckCircle2 className="w-3.5 h-3.5" />
          {message}
        </div>
      )}

      <div className="flex justify-end">
        <button
          type="button"
          className="px-3 py-1 rounded-md border text-xs inline-flex items-center gap-1"
          style={{ borderColor: styles.accentBorder, color: styles.cardText }}
          onClick={() => void save()}
          disabled={state === 'saving'}
          data-testid="knowledge-article-edit-save"
        >
          {state === 'saving' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Pencil className="w-3.5 h-3.5" />}
          {state === 'saving' ? t('knowledge.articleEdit.saving') : t('knowledge.articleEdit.save')}
        </button>
      </div>
    </section>
  );
}
