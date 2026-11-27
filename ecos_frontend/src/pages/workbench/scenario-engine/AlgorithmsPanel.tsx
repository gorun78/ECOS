/**
 * 场景引擎配置 · 算法池（自原 pages/workbench/config.tsx 迁入，2026-11-27）。
 *
 * <p>数据源：{@code /api/v1/workspace/algorithms}（services/scenarioConfigClient.ts 单通道）。
 * 列表 / 登记 / 回填编辑 / 删除；加载失败呈现行内错误横幅 + 重试（不只 toast）。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Plus, Trash2 } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import {
  fetchScenarioAlgorithms,
  saveScenarioAlgorithm,
  deleteScenarioAlgorithm,
  type ScenarioAlgorithmVO,
} from '../../../services/scenarioConfigClient';
import { showToastGlobal } from '../../../components/common/Toast';
import logger from '../../../services/logger';

const EMPTY_FORM: ScenarioAlgorithmVO = {
  name: '',
  analysisType: 'CAUSAL',
  version: '1',
  providerType: 'DETERMINISTIC_SPI',
  implementationRef: '',
  status: 'ACTIVE',
};

export default function AlgorithmsPanel() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [algorithms, setAlgorithms] = useState<ScenarioAlgorithmVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [algForm, setAlgForm] = useState<ScenarioAlgorithmVO>({ ...EMPTY_FORM });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const rows = await fetchScenarioAlgorithms();
      setAlgorithms(Array.isArray(rows) ? rows : []);
    } catch (e) {
      logger.error('enginecfg-algorithms-load', e);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const saveAlg = async () => {
    if (!String(algForm.name ?? '').trim()) {
      showToastGlobal('info', t('workspace.config.algorithm.name'));
      return;
    }
    setSaving(true);
    try {
      await saveScenarioAlgorithm(algForm);
      showToastGlobal('success', t('workspace.config.saved'));
      setAlgForm({ ...EMPTY_FORM });
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
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
            <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.config.algorithm.create')}</h3>
            <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
              <input
                value={algForm.name ?? ''}
                onChange={(e) => setAlgForm((s) => ({ ...s, name: e.target.value }))}
                placeholder={t('workspace.config.algorithm.name')}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
              <select
                value={algForm.analysisType ?? 'CAUSAL'}
                onChange={(e) => setAlgForm((s) => ({ ...s, analysisType: e.target.value }))}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              >
                {['CAUSAL', 'FORECAST', 'SIMULATION'].map((a) => (
                  <option key={a} value={a}>{t(`workspace.config.algorithm.type.${a}`)}</option>
                ))}
              </select>
              <input
                value={algForm.version ?? ''}
                onChange={(e) => setAlgForm((s) => ({ ...s, version: e.target.value }))}
                placeholder={t('workspace.config.algorithm.version')}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
              <input
                value={algForm.implementationRef ?? ''}
                onChange={(e) => setAlgForm((s) => ({ ...s, implementationRef: e.target.value }))}
                placeholder={t('workspace.config.algorithm.impl')}
                className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} col-span-full`}
              />
            </div>
            <button
              type="button"
              onClick={() => void saveAlg()}
              disabled={saving}
              className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer disabled:opacity-50 ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
            >
              <Plus size={12} /> {algForm.id ? t('workspace.config.edit') : t('workspace.config.create')}
            </button>
            {algForm.id && (
              <button
                type="button"
                onClick={() => setAlgForm({ ...EMPTY_FORM })}
                className={`ml-2 mt-3 text-xs px-3 py-1.5 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
              >
                {t('workspace.enginecfg.cancel')}
              </button>
            )}
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
                      <span className={`text-[10px] ${styles.cardTextMuted}`}>
                        {t(`workspace.config.algorithm.type.${String(alg.analysisType)}`)} · {alg.status}
                      </span>
                    </div>
                    <p className={`mt-1 font-mono ${styles.cardTextMuted}`}>{alg.implementationRef}</p>
                    <div className="mt-2 flex gap-2">
                      <button type="button" onClick={() => setAlgForm(alg)} className={`text-xs ${styles.accentText} cursor-pointer`}>
                        {t('workspace.config.edit')}
                      </button>
                      <button
                        type="button"
                        onClick={() => void removeAlg(alg.id)}
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
