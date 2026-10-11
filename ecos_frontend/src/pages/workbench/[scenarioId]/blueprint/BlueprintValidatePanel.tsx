/**
 * BlueprintValidatePanel — 发布前四道静态门槛（补充设计 §6.6 / 设计方案 §3.4 步骤 6）。
 *
 * <p>只读展示 GET {id}/validate 返回的 passed/reasons/details：
 * openBlockerGaps / missingRequiredDeps / unavailableCapabilities / failedP0Tests。
 * 无任何本地判定：通过与否、明细行全部来自后端。禁伪造 passed=true。</p>
 */
import React from 'react';
import LucideIcon from '../../../../components/LucideIcon';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import type { BlueprintValidateResult } from '../../../../services/scenarioBlueprintClient';

interface Props {
  result: BlueprintValidateResult | null;
  loading: boolean;
  error: string | null;
  onRun: () => void;
  disabled?: boolean;
}

export default function BlueprintValidatePanel({ result, loading, error, onRun, disabled }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const gates: Array<{ key: string; rows: Array<Record<string, unknown>> | undefined; label: string }> = [
    { key: 'blocker_gaps', rows: result?.details.openBlockerGaps, label: t('workspace.bp.gate.blocker_gaps') },
    { key: 'missing_deps', rows: result?.details.missingRequiredDeps, label: t('workspace.bp.gate.missing_deps') },
    { key: 'unavail_caps', rows: result?.details.unavailableCapabilities, label: t('workspace.bp.gate.unavail_caps') },
    { key: 'failed_p0', rows: result?.details.failedP0Tests, label: t('workspace.bp.gate.failed_p0') },
  ];

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <div className="flex items-center justify-between gap-2 mb-2">
        <h3 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <LucideIcon name="ShieldCheck" size={13} />
          {t('workspace.bp.validate')}
        </h3>
        <button
          type="button"
          onClick={onRun}
          disabled={loading || disabled}
          className={`text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.accentBorder} ${styles.accentText} ${styles.cardBg} disabled:opacity-50 disabled:cursor-not-allowed`}
        >
          {loading ? t('workspace.bp.validating') : t('workspace.bp.validate')}
        </button>
      </div>

      {error && (
        <div className={`text-[11px] rounded border px-2 py-1.5 mb-2 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          {t('workspace.bp.error', { msg: error })}
        </div>
      )}

      {result && (
        <div
          className={`text-[11px] font-bold rounded border px-2 py-1.5 mb-2 flex items-center gap-1 ${
            result.passed
              ? `${styles.successBg} ${styles.successText} ${styles.successBorder}`
              : `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
          }`}
        >
          <LucideIcon name={result.passed ? 'CheckCircle2' : 'AlertTriangle'} size={12} />
          {result.passed ? t('workspace.bp.validate_passed') : t('workspace.bp.validate_failed', { n: result.reasons.length })}
        </div>
      )}

      {result && !result.passed && result.reasons.length > 0 && (
        <ul className={`mb-2 text-[11px] ${styles.cardText} list-disc pl-4 space-y-0.5`}>
          {result.reasons.map((r, i) => (
            <li key={i}>{r}</li>
          ))}
        </ul>
      )}

      {result && (
        <div className="grid gap-2 sm:grid-cols-2">
          {gates.map((g) => {
            const rows = g.rows ?? [];
            const empty = rows.length === 0;
            return (
              <div key={g.key} className={`rounded border p-2 ${styles.cardBorder}`}>
                <div className={`text-[10px] font-bold uppercase mb-1 ${styles.cardTextMuted}`}>{g.label}</div>
                {empty ? (
                  <span className={`text-[11px] ${styles.successText}`}>{t('workspace.bp.gate.empty')}</span>
                ) : (
                  <ul className={`text-[11px] ${styles.cardText} space-y-0.5`}>
                    {rows.map((row, i) => (
                      <li key={i} className="truncate">
                        {String(row.gapId ?? row.id ?? row.capabilityId ?? row.type ?? `#${i + 1}`)}
                        {row.status ? <span className={`ml-1 ${styles.cardTextMuted}`}>[{String(row.status)}]</span> : null}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
