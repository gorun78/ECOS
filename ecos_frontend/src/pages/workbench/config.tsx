/**
 * 场景配置页（场景工作台简化 v0.4）。
 *
 * <p>2026-11-24 用户裁决：模板功能自本页迁出到独立「场景模板」页
 * （{@code pages/workbench/templates.tsx}，首页按钮直达），本页只留算法库。</p>
 */
import React, { useEffect, useState } from 'react';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import {
  fetchScenarioAlgorithms,
  saveScenarioAlgorithm,
  deleteScenarioAlgorithm,
  type ScenarioAlgorithmVO,
} from '../../services/scenarioConfigClient';
import { showToastGlobal } from '../../components/common/Toast';
import logger from '../../services/logger';

export default function WorkbenchConfig() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [algorithms, setAlgorithms] = useState<ScenarioAlgorithmVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [algForm, setAlgForm] = useState<ScenarioAlgorithmVO>({ name: '', analysisType: 'CAUSAL', version: '1', providerType: 'DETERMINISTIC_SPI', implementationRef: '', status: 'ACTIVE' });

  const load = async () => {
    setLoading(true);
    try {
      const as = await fetchScenarioAlgorithms();
      setAlgorithms(as);
    } catch (e) {
      logger.error('config-load', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const saveAlg = async () => {
    try {
      await saveScenarioAlgorithm(algForm);
      showToastGlobal('success', t('workspace.config.saved'));
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  const removeAlg = async (id?: string) => {
    if (!id) return;
    try {
      await deleteScenarioAlgorithm(id);
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
        <h1 className={`text-lg font-bold ${styles.appText}`}>{t('workspace.config.title')}</h1>
        <Link
          to="/workbench/templates"
          className={`ml-auto text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
        >
          {t('workspace.config.templates')}
        </Link>
      </div>

      {loading && <p className={`p-4 text-xs ${styles.cardTextMuted}`}>{t('workspace.config.loading')}</p>}

      {!loading && (
        <div className="p-4 space-y-4">
          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
            <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.config.algorithm.create')}</h3>
            <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
              <input value={algForm.name ?? ''} onChange={(e) => setAlgForm((s) => ({ ...s, name: e.target.value }))} placeholder={t('workspace.config.algorithm.name')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
              <select value={algForm.analysisType ?? 'CAUSAL'} onChange={(e) => setAlgForm((s) => ({ ...s, analysisType: e.target.value }))} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
                {['CAUSAL', 'FORECAST', 'SIMULATION'].map((a) => <option key={a} value={a}>{t(`workspace.config.algorithm.type.${a}`)}</option>)}
              </select>
              <input value={algForm.version ?? ''} onChange={(e) => setAlgForm((s) => ({ ...s, version: e.target.value }))} placeholder={t('workspace.config.algorithm.version')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
              <input value={algForm.implementationRef ?? ''} onChange={(e) => setAlgForm((s) => ({ ...s, implementationRef: e.target.value }))} placeholder={t('workspace.config.algorithm.impl')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} col-span-full`} />
            </div>
            <button type="button" onClick={() => void saveAlg()} className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
              <Plus size={12} /> {t('workspace.config.create')}
            </button>
          </section>

          <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
            <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.config.algorithm.list')}</h3>
            {algorithms.length === 0 ? (
              <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.config.algorithm.empty')}</p>
            ) : (
              <ul className="space-y-2">
                {algorithms.map((alg) => (
                  <li key={alg.id} className={`border ${styles.cardBorder} rounded p-3 text-xs ${styles.cardText}`}>
                    <div className="flex items-center justify-between">
                      <span className="font-bold">{alg.name} · v{alg.version}</span>
                      <span className={`text-[10px] ${styles.cardTextMuted}`}>{t(`workspace.config.algorithm.type.${String(alg.analysisType)}`)} · {alg.status}</span>
                    </div>
                    <p className={`mt-1 font-mono ${styles.cardTextMuted}`}>{alg.implementationRef}</p>
                    <div className="mt-2 flex gap-2">
                      <button type="button" onClick={() => setAlgForm(alg)} className={`text-xs ${styles.accentText} cursor-pointer`}>{t('workspace.config.edit')}</button>
                      <button type="button" onClick={() => void removeAlg(alg.id)} className={`text-xs ${styles.dangerText} cursor-pointer inline-flex items-center gap-1`}><Trash2 size={11} /> {t('workspace.config.delete')}</button>
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
