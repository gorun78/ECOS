/**
 * 场景引擎配置 · 场景模板（自原 pages/workbench/templates.tsx 迁入，2026-11-27）。
 *
 * <p>数据源：{@code /api/v1/workspace/scenario-templates}（services/scenarioConfigClient.ts
 * 单通道）。列表 / 登记 / 回填编辑 / 删除；空列表如实呈现，不造演示模板。
 * 新建场景模态内的模板预填流程（NewScenarioModal）不受影响。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Plus, Trash2 } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import {
  deleteScenarioTemplate,
  fetchScenarioTemplates,
  saveScenarioTemplate,
  type ScenarioTemplateVO,
} from '../../../services/scenarioConfigClient';
import { showToastGlobal } from '../../../components/common/Toast';
import logger from '../../../services/logger';

const EMPTY_FORM: ScenarioTemplateVO = { name: '', category: 'GENERAL', description: '', status: 'ACTIVE' };

export default function TemplatesPanel() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [templates, setTemplates] = useState<ScenarioTemplateVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<ScenarioTemplateVO>({ ...EMPTY_FORM });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const rows = await fetchScenarioTemplates();
      setTemplates(Array.isArray(rows) ? rows : []);
    } catch (e) {
      logger.error('enginecfg-templates-load', e);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const save = async () => {
    if (!String(form.name ?? '').trim()) {
      showToastGlobal('info', t('workspace.config.template.name'));
      return;
    }
    setSaving(true);
    try {
      await saveScenarioTemplate(form);
      showToastGlobal('success', t('workspace.config.saved'));
      setForm({ ...EMPTY_FORM });
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id?: string) => {
    if (!id) return;
    try {
      await deleteScenarioTemplate(id);
      showToastGlobal('success', t('workspace.config.deleted'));
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="space-y-4">
      {error && (
        <div className={`px-3 py-2 rounded border text-xs flex items-center gap-2 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <AlertTriangle size={12} />
          {t('workspace.enginecfg.loadError', { msg: error })}
          <button type="button" onClick={() => void load()} className="ml-auto underline cursor-pointer">
            {t('workspace.l1.retry')}
          </button>
        </div>
      )}

      {loading && <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.config.loading')}</p>}

      {!loading && (
        <>
          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
            <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.config.template.create')}</h3>
            <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
              <input
                value={form.name ?? ''}
                onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))}
                placeholder={t('workspace.config.template.name')}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
              <input
                value={form.category ?? ''}
                onChange={(e) => setForm((s) => ({ ...s, category: e.target.value }))}
                placeholder={t('workspace.config.template.category')}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
              <input
                value={form.description ?? ''}
                onChange={(e) => setForm((s) => ({ ...s, description: e.target.value }))}
                placeholder={t('workspace.config.template.description')}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} col-span-full`}
              />
            </div>
            <button
              type="button"
              onClick={() => void save()}
              disabled={saving}
              className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer disabled:opacity-50 ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
            >
              <Plus size={12} /> {form.id ? t('workspace.config.edit') : t('workspace.config.create')}
            </button>
            {form.id && (
              <button
                type="button"
                onClick={() => setForm({ ...EMPTY_FORM })}
                className={`ml-2 mt-3 text-xs px-3 py-1.5 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
              >
                {t('workspace.enginecfg.cancel')}
              </button>
            )}
          </section>

          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
            <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.config.template.list')}</h3>
            {templates.length === 0 ? (
              <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.config.template.empty')}</p>
            ) : (
              <ul className="space-y-2">
                {templates.map((tpl) => (
                  <li key={tpl.id} className={`border ${styles.cardBorder} rounded p-3 text-xs ${styles.cardText}`}>
                    <div className="flex items-center justify-between">
                      <span className="font-bold">{tpl.name}</span>
                      <span className={`text-[10px] ${styles.cardTextMuted}`}>{tpl.category} · {tpl.status}</span>
                    </div>
                    <p className={`mt-1 ${styles.cardTextMuted}`}>{tpl.description}</p>
                    <div className="mt-2 flex gap-2">
                      <button type="button" onClick={() => setForm(tpl)} className={`text-xs ${styles.accentText} cursor-pointer`}>
                        {t('workspace.config.edit')}
                      </button>
                      <button
                        type="button"
                        onClick={() => void remove(tpl.id)}
                        className={`text-xs ${styles.dangerText} cursor-pointer inline-flex items-center gap-1`}
                      >
                        <Trash2 size={11} /> {t('workspace.config.delete')}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}
