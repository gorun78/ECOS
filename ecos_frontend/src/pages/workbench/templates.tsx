/**
 * 场景模板管理页（2026-11-24 用户裁决：模板功能自「场景配置」迁出）。
 *
 * <p>首页「场景模板」按钮直接进本页做模板维护（增 / 改 / 删 / 列表），
 * 不再借道「新建场景」模态；新建场景时仍可在模态里选模板预填（那不是本按钮的语义）。</p>
 *
 * <p>数据源：{@code /api/v1/workspace/scenario-templates} 真实端点，
 * 空列表如实呈现，不造演示模板。</p>
 */
import React, { useEffect, useState } from 'react';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import {
  deleteScenarioTemplate,
  fetchScenarioTemplates,
  saveScenarioTemplate,
  type ScenarioTemplateVO,
} from '../../services/scenarioConfigClient';
import { showToastGlobal } from '../../components/common/Toast';
import logger from '../../services/logger';

export default function WorkbenchTemplates() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [templates, setTemplates] = useState<ScenarioTemplateVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<ScenarioTemplateVO>({ name: '', category: 'GENERAL', description: '', status: 'ACTIVE' });

  const load = async () => {
    setLoading(true);
    try {
      const rows = await fetchScenarioTemplates();
      setTemplates(Array.isArray(rows) ? rows : []);
    } catch (e) {
      logger.error('templates-load', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const save = async () => {
    if (!String(form.name ?? '').trim()) {
      showToastGlobal('info', t('workspace.config.template.name'));
      return;
    }
    try {
      await saveScenarioTemplate(form);
      showToastGlobal('success', t('workspace.config.saved'));
      setForm({ name: '', category: 'GENERAL', description: '', status: 'ACTIVE' });
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
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
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      <div className={`p-3 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex items-center gap-2`}>
        <Link to="/workbench" className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}>
          <ArrowLeft size={12} />
          {t('workspace.config.back')}
        </Link>
        <h1 className={`text-lg font-bold ${styles.appText}`}>{t('workspace.config.templates')}</h1>
      </div>

      {loading && <p className={`p-4 text-xs ${styles.cardTextMuted}`}>{t('workspace.config.loading')}</p>}

      {!loading && (
        <div className="p-4 space-y-4">
          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
            <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.config.template.create')}</h3>
            <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
              <input value={form.name ?? ''} onChange={(e) => setForm((s) => ({ ...s, name: e.target.value }))} placeholder={t('workspace.config.template.name')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
              <input value={form.category ?? ''} onChange={(e) => setForm((s) => ({ ...s, category: e.target.value }))} placeholder={t('workspace.config.template.category')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
              <input value={form.description ?? ''} onChange={(e) => setForm((s) => ({ ...s, description: e.target.value }))} placeholder={t('workspace.config.template.description')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} col-span-full`} />
            </div>
            <button type="button" onClick={() => void save()} className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
              <Plus size={12} /> {t('workspace.config.create')}
            </button>
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
                      <button type="button" onClick={() => setForm(tpl)} className={`text-xs ${styles.accentText} cursor-pointer`}>{t('workspace.config.edit')}</button>
                      <button type="button" onClick={() => void remove(tpl.id)} className={`text-xs ${styles.dangerText} cursor-pointer inline-flex items-center gap-1`}><Trash2 size={11} /> {t('workspace.config.delete')}</button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
