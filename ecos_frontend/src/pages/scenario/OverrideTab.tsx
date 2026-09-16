/**
 * 人工覆写子面板（PMO-59 P4b T3）。
 *
 * <p>人机干预入口之三：对不确定性判断（V129）做专家覆写 ——
 * `POST /api/v1/cognitive/beliefs/{variable}/override`（manualOverride=true + reason 留痕，
 * 覆写后模型自动更新让位于专家意见，直至下一次人工覆写）。
 * 覆写前弹项目通用确认框（二次确认），成功后重拉版本链并标记「人工覆写」。</p>
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import LucideIcon from '../../components/LucideIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import { showToastGlobal } from '../../components/common/Toast';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { formatNum, type BeliefDistribution } from './mentalTypes';

/** 分布点草稿（prob 用字符串保留输入中间态） */
interface DistributionDraft {
  outcome: string;
  prob: string;
}

/** prob 和容差（与后端 Service 1e-6 口径一致） */
const PROB_TOLERANCE = 1e-6;

export default function OverrideTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [domain, setDomain] = useState('');
  const [beliefs, setBeliefs] = useState<BeliefDistribution[]>([]);
  const [loading, setLoading] = useState(false);
  const [variable, setVariable] = useState('');
  const [drafts, setDrafts] = useState<DistributionDraft[]>([]);
  const [reason, setReason] = useState('');
  const [evidenceId, setEvidenceId] = useState('');
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  /** 每个变量取最高版本（覆写以最新版本为基准） */
  const latestByVariable = useMemo(() => {
    const map = new Map<string, BeliefDistribution>();
    beliefs.forEach((b) => {
      const key = b.variableName ?? '';
      if (!key) {
        return;
      }
      const existed = map.get(key);
      if (!existed || b.version > existed.version) {
        map.set(key, b);
      }
    });
    return Array.from(map.values()).sort((a, b) => (a.variableName ?? '').localeCompare(b.variableName ?? ''));
  }, [beliefs]);

  /** 拉取版本链（覆写成功后同一入口重拉 = 写后刷新） */
  const loadBeliefs = useCallback(async (domainValue: string) => {
    setLoading(true);
    try {
      const data = await apiFetchData<BeliefDistribution[]>(
        `/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}`
      );
      setBeliefs(Array.isArray(data) ? data : []);
    } catch (e) {
      setBeliefs([]);
      showToastGlobal('error', `${t('scenario.iv.ov.loadFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    const domainValue = domain.trim();
    if (!domainValue) {
      setBeliefs([]);
      return;
    }
    const timer = window.setTimeout(() => {
      void loadBeliefs(domainValue);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [domain, loadBeliefs]);

  /** 切换目标变量：以该变量最新版本分布预填草稿（专家在其上改） */
  const handleVariableChange = (name: string) => {
    setVariable(name);
    const target = latestByVariable.find((b) => b.variableName === name);
    const dist = target?.distribution ?? [];
    setDrafts(dist.map((p) => ({ outcome: p.outcome, prob: String(p.prob) })));
  };

  const patchDraft = (index: number, patch: Partial<DistributionDraft>) => {
    setDrafts((prev) => prev.map((d, i) => (i === index ? { ...d, ...patch } : d)));
  };

  const addDraft = () => setDrafts((prev) => [...prev, { outcome: '', prob: '' }]);

  const removeDraft = (index: number) => setDrafts((prev) => prev.filter((_, i) => i !== index));

  /** 概率和（容差内视为 1） */
  const probSum = drafts.reduce((acc, d) => acc + (Number(d.prob) || 0), 0);

  /** 提交前校验（必填 + 分布合法 + prob 和=1） */
  const validate = (): string | null => {
    if (!domain.trim() || !variable.trim()) {
      return t('scenario.iv.ov.needInput');
    }
    if (drafts.length === 0 || drafts.some((d) => d.outcome.trim() === '' || d.prob.trim() === '')) {
      return t('scenario.iv.ov.needDistribution');
    }
    if (Math.abs(probSum - 1) > PROB_TOLERANCE) {
      return t('scenario.iv.ov.probSumInvalid');
    }
    if (!reason.trim()) {
      return t('scenario.iv.ov.needReason');
    }
    return null;
  };

  /** 二次确认后真正提交 */
  const doSubmit = async () => {
    setConfirmVisible(false);
    setSubmitting(true);
    try {
      await apiFetchData<BeliefDistribution>(
        `/api/v1/cognitive/beliefs/${encodeURIComponent(variable.trim())}/override`,
        {
          method: 'POST',
          body: JSON.stringify({
            domain: domain.trim(),
            discreteDistribution: drafts.map((d) => ({ outcome: d.outcome.trim(), prob: Number(d.prob) })),
            overrideReason: reason.trim(),
            lastEvidenceId: evidenceId.trim() === '' ? undefined : evidenceId.trim(),
          }),
        }
      );
      showToastGlobal('success', t('scenario.iv.ov.ok'));
      await loadBeliefs(domain.trim());
      setReason('');
      setEvidenceId('');
    } catch (e) {
      showToastGlobal('error', `${t('scenario.iv.ov.fail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = () => {
    const error = validate();
    if (error) {
      showToastGlobal('error', error);
      return;
    }
    setConfirmVisible(true);
  };

  return (
    <div className="space-y-4">
      {/* ── 覆写配置 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
        <div className="flex items-center gap-2">
          <LucideIcon name="UserCog" size={14} />
          <span className="text-sm font-semibold">{t('scenario.iv.ov.title')}</span>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} leading-normal`}>{t('scenario.iv.ov.desc')}</p>

        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.ov.domain')}
            </span>
            <input
              value={domain}
              onChange={(e) => setDomain(e.target.value)}
              placeholder={t('scenario.iv.cf.domainPh')}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.ov.variable')}
            </span>
            <select
              value={variable}
              onChange={(e) => handleVariableChange(e.target.value)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            >
              <option value="">{loading ? t('scenario.iv.rp.loading') : t('scenario.iv.ov.variablePh')}</option>
              {latestByVariable.map((b) => (
                <option key={b.id} value={b.variableName}>
                  {b.variableName} (v{b.version})
                </option>
              ))}
            </select>
          </label>
        </div>

        {/* ── 分布编辑 ── */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.ov.distribution')}
            </span>
            <div className="flex items-center gap-2">
              <span
                className={`text-[10px] font-mono font-bold ${
                  Math.abs(probSum - 1) > PROB_TOLERANCE ? 'text-red-400' : 'text-emerald-400'
                }`}
              >
                Σprob={formatNum(probSum)}
              </span>
              <button
                onClick={addDraft}
                className={`text-[10px] font-bold px-2 py-1 rounded border ${styles.inputBorder} ${styles.cardTextMuted} hover:border-indigo-500/50 transition-colors cursor-pointer flex items-center gap-1`}
              >
                <LucideIcon name="Plus" size={10} />
                {t('scenario.iv.ov.addOutcome')}
              </button>
            </div>
          </div>
          {drafts.length === 0 && (
            <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.ov.pickVariableFirst')}</p>
          )}
          {drafts.map((d, i) => (
            <div key={i} className="grid grid-cols-12 gap-2 items-center">
              <input
                value={d.outcome}
                onChange={(e) => patchDraft(i, { outcome: e.target.value })}
                placeholder={t('scenario.iv.ov.outcomePh')}
                className={`col-span-7 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
              <input
                type="number"
                step="0.01"
                min={0}
                max={1}
                value={d.prob}
                onChange={(e) => patchDraft(i, { prob: e.target.value })}
                placeholder="0.00"
                className={`col-span-4 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
              <button
                onClick={() => removeDraft(i)}
                title={t('scenario.iv.ov.removeOutcome')}
                className={`col-span-1 flex items-center justify-center py-1.5 rounded border ${styles.inputBorder} ${styles.cardTextMuted} hover:border-red-500/60 hover:text-red-400 transition-colors cursor-pointer`}
              >
                <LucideIcon name="Trash2" size={11} />
              </button>
            </div>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.ov.reason')}
            </span>
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t('scenario.iv.ov.reasonPh')}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.ov.evidenceId')}
            </span>
            <input
              value={evidenceId}
              onChange={(e) => setEvidenceId(e.target.value)}
              placeholder={t('scenario.iv.ov.evidenceIdPh')}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
        </div>

        <div className="flex justify-end">
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-3 py-1.5 rounded bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-[11px] font-bold transition-colors cursor-pointer flex items-center gap-1.5"
          >
            <LucideIcon name="UserCog" size={12} className={submitting ? 'animate-pulse' : ''} />
            {submitting ? t('scenario.iv.ov.submitting') : t('scenario.iv.ov.submit')}
          </button>
        </div>
      </div>

      {/* ── 版本链（覆写后人工标记可见） ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
        <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
          {t('scenario.iv.ov.versionChain')}
        </span>
        {latestByVariable.length === 0 ? (
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.ov.listEmpty')}</p>
        ) : (
          <ul className="space-y-1.5">
            {latestByVariable.map((b) => (
              <li key={b.id} className="flex flex-wrap items-center gap-2 text-[11px]">
                <span className={`font-mono ${styles.cardText}`}>{b.variableName}</span>
                <span className={`font-mono ${styles.cardTextMuted}`}>v{b.version}</span>
                <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                  ({(b.distribution ?? []).map((p) => `${p.outcome}:${formatNum(p.prob)}`).join(' / ') || '-'})
                </span>
                {b.manualOverride && (
                  <span
                    className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
                  >
                    {t('scenario.iv.ov.manualOverrideTag')}
                  </span>
                )}
                {b.overrideReason && <span className={styles.cardTextMuted}>{b.overrideReason}</span>}
              </li>
            ))}
          </ul>
        )}
      </div>

      <ConfirmDialog
        visible={confirmVisible}
        variant="warning"
        title={t('scenario.iv.ov.confirmTitle')}
        message={t('scenario.iv.ov.confirmMsg', { variable })}
        confirmText={t('scenario.iv.ov.confirm')}
        cancelText={t('scenario.iv.ov.cancel')}
        onConfirm={doSubmit}
        onCancel={() => setConfirmVisible(false)}
      />
    </div>
  );
}
