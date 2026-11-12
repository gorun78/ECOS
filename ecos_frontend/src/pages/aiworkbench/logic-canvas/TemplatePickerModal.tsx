/**
 * TemplatePickerModal — 「从 Wagent 模板新建」弹窗（模板融合批 2）。
 *
 * 交互：模板列表（内置典型模板 / Playbook / 已发布候选）→ 填名称与槽位 → 实例化 →
 * 画布加载生成的骨架。
 * 内置来源由后端按意图确定性生成（human_gate → tool×n → aggregate），无 {{slot}} 占位，
 * 故选中内置模板时槽位框折叠为说明文案，避免误导用户填了不生效的参数。
 * 六态：加载 / 空目录（无模板或内容源未装配）/ 错误 + 重试 / 权限失败（错误文案）/ 离线
 * （apiFetchData 全局横幅接管）/ 正常。
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from 'react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  TEMPLATE_SOURCE_LABEL_KEYS,
  type AipPipelineTemplate,
  type AipPipelineInstantiateResult,
} from '../../../types/aiworkbench';
import { fetchPipelineTemplates, instantiatePipelineTemplate } from '../api';
import { Icon } from './LogicIcon';

export default function TemplatePickerModal({
  onInstantiated,
  onClose,
}: {
  onInstantiated: (result: AipPipelineInstantiateResult, name: string) => void;
  onClose: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [templates, setTemplates] = useState<AipPipelineTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AipPipelineTemplate | null>(null);
  const [name, setName] = useState('');
  const [slotsText, setSlotsText] = useState('{}');
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    fetchPipelineTemplates()
      .then((rows) => {
        setTemplates(rows);
        setSelected(rows[0] ?? null);
        setName(rows[0]?.name ?? '');
      })
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const selectTemplate = (item: AipPipelineTemplate) => {
    setSelected(item);
    setName(item.name);
  };

  const selectedIsBuiltin = selected?.source === 'BUILTIN';

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selected || !name.trim()) return;
    let slots: Record<string, string>;
    try {
      const parsed: unknown = JSON.parse(slotsText || '{}');
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        setSlotsError(t('aiworkbench.logic.template.slotsInvalid'));
        return;
      }
      slots = Object.fromEntries(
        Object.entries(parsed as Record<string, unknown>)
          .map(([k, v]) => [k, v == null ? '' : String(v)]),
      );
      setSlotsError(null);
    } catch {
      setSlotsError(t('aiworkbench.logic.template.slotsInvalid'));
      return;
    }
    setSubmitting(true);
    try {
      const result = await instantiatePipelineTemplate(selected.ref, {
        name: name.trim(),
        // 内置模板骨架无占位，不把残留的槽位文本当参数发送（避免"填了不生效"的假象）
        ...(selectedIsBuiltin ? {} : { slots }),
      });
      onInstantiated(result, name.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
      <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-2xl overflow-hidden`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
          <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5`}>
            <Icon name="LayoutTemplate" size={14} className={styles.accentText} />
            {t('aiworkbench.logic.template.title')}
          </h3>
          <button type="button" onClick={onClose}
            className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}>
            <Icon name="X" size={15} />
          </button>
        </div>

        {loading ? (
          <div className={`p-8 flex items-center justify-center gap-2 ${styles.cardTextMuted}`}>
            <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
            <span>{t('aiworkbench.logic.template.loading')}</span>
          </div>
        ) : error && templates.length === 0 ? (
          <div className="p-8 flex flex-col items-center gap-3">
            <Icon name="AlertTriangle" size={22} className={styles.dangerText} />
            <p className={`text-[11px] ${styles.dangerText} max-w-md text-center break-words`}>{error}</p>
            <button type="button" onClick={load}
              className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg ${styles.cardText} text-[11px] font-semibold cursor-pointer`}>
              {t('common.retry')}
            </button>
          </div>
        ) : templates.length === 0 ? (
          <div className={`p-8 flex flex-col items-center gap-2 ${styles.cardTextMuted}`}>
            <Icon name="LayoutTemplate" size={22} />
            <p className="text-[11px] text-center max-w-md">{t('aiworkbench.logic.template.empty')}</p>
            <p className="text-[10px] text-center max-w-md opacity-80">{t('aiworkbench.logic.template.emptyHint')}</p>
          </div>
        ) : (
          <form onSubmit={submit} className="p-4 space-y-4">
            <div className="space-y-1">
              <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.template.pick')}</label>
              <div className="max-h-56 overflow-y-auto space-y-1.5">
                {templates.map((item) => {
                  const active = selected?.ref === item.ref && selected?.version === item.version;
                  return (
                    <button key={`${item.ref}@${item.version}`} type="button"
                      onClick={() => selectTemplate(item)}
                      className={`w-full text-left px-3 py-2 rounded-lg border transition-colors cursor-pointer ${
                        active
                          ? `${styles.accentBorder} ${styles.accentBg}/10`
                          : `${styles.cardBorder} hover:opacity-80`
                      }`}>
                      <div className="flex items-center justify-between gap-2">
                        <span className={`text-[11px] font-bold ${styles.cardText}`}>{item.name}</span>
                        <span className={`text-[9px] px-1.5 py-0.5 rounded ${styles.inputBg} ${styles.cardTextMuted} font-mono`}>
                          {t(TEMPLATE_SOURCE_LABEL_KEYS[item.source])} · v{item.version}
                        </span>
                      </div>
                      <div className={`text-[10px] ${styles.cardTextMuted} font-mono truncate`}>{item.ref}</div>
                      {item.summary && (
                        <div className={`text-[10px] ${styles.cardTextMuted} truncate`}>{item.summary}</div>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="space-y-1">
              <label className={`block ${styles.cardTextMuted} font-semibold`}>
                {t('aiworkbench.logic.template.name')} <span className={styles.dangerText}>*</span>
              </label>
              <input type="text" value={name} onChange={e => setName(e.target.value)} required
                className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`} />
            </div>

            {selectedIsBuiltin ? (
              <div className={`px-2.5 py-2 rounded-lg border ${styles.cardBorder} ${styles.inputBg}`}>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>
                  {t('aiworkbench.logic.template.builtinSlotsHint')}
                </p>
              </div>
            ) : (
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>
                  {t('aiworkbench.logic.template.slots')}
                </label>
                <textarea value={slotsText} onChange={e => { setSlotsText(e.target.value); setSlotsError(null); }}
                  rows={3}
                  className={`w-full px-2.5 py-1.5 border ${slotsError ? styles.dangerBorder : styles.cardBorder} rounded-lg text-xs font-mono resize-none`} />
                <p className={`text-[10px] ${slotsError ? styles.dangerText : styles.cardTextMuted}`}>
                  {slotsError ?? t('aiworkbench.logic.template.slotsHint')}
                </p>
              </div>
            )}

            {error && (
              <p className={`text-[11px] ${styles.dangerText} break-words`}>{error}</p>
            )}

            <div className={`pt-2 border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
              <button type="button" onClick={onClose}
                className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} text-[11px] font-semibold cursor-pointer`}>
                {t('common.cancel')}
              </button>
              <button type="submit" disabled={submitting || !selected || !name.trim()}
                className={`px-4 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} rounded-lg font-bold shadow-sm cursor-pointer text-[11px] disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5`}>
                {submitting && <span className="w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin" />}
                {t('aiworkbench.logic.template.submit')}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
