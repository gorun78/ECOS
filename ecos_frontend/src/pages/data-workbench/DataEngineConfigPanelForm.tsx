/**
 * DataEngineConfigPanelForm — 右侧配置表单子组件
 * 从 DataEngineConfigPanel 拆分而来。
 * PMO-3J-T6: label/description 走 i18nKey (t 解析)；Doris 配置项 standard 版灰显。
 * @license Apache-2.0
 */
import React from 'react';
import { AlertCircle, Shield, Eye, EyeOff, Lock } from 'lucide-react';
import type { ConfigGroup, ConfigValues, DefaultValues } from './DataEngineConfigPanel';
import type { ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface Props {
  loading: boolean;
  loadError: string | null;
  currentGroup: ConfigGroup;
  values: ConfigValues;
  defaults: DefaultValues;
  originalValues: ConfigValues;
  revealedPasswords: Set<string>;
  onValueChange: (key: string, value: string | number | boolean) => void;
  onRetry: () => void;
  onTogglePassword: (key: string) => void;
  styles: ThemeStyles;
}

const DataEngineConfigPanelForm: React.FC<Props> = ({
  loading,
  loadError,
  currentGroup,
  values,
  defaults,
  originalValues,
  revealedPasswords,
  onValueChange,
  onRetry,
  onTogglePassword,
  styles,
}) => {
  const { t } = useLanguage();

  if (loading) {
    return (
      <div className="flex-1 flex flex-col min-h-0 overflow-y-auto">
        <div className={`flex items-center justify-center flex-1 ${styles.cardTextMuted} text-sm`}>
          {t('dw.cfg.form.loading')}
        </div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="flex-1 flex flex-col min-h-0 overflow-y-auto">
        <div className="flex flex-col items-center justify-center flex-1 gap-2">
          <AlertCircle size={24} className={`${styles.warningText}`} />
          <span className={`text-sm ${styles.cardTextMuted}`}>{loadError}</span>
          <button
            onClick={onRetry}
            className={`px-3 py-1 text-xs ${styles.accentText} hover:${styles.infoBg} rounded-md transition-colors`}
          >
            {t('dw.cfg.form.retry')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 overflow-y-auto">
      {/* Group Title */}
      <div className={`px-5 py-3 border-b ${styles.cardBorder} ${styles.cardBg} sticky top-0 z-10`}>
        <div className="flex items-center gap-2">
          <span className={`${styles.cardTextMuted}`}>
            {currentGroup.icon}
          </span>
          <h3 className={`text-sm font-bold ${styles.cardText}`}>
            {t(currentGroup.labelKey)}
          </h3>
          {currentGroup.modified && (
            <span className={`${styles.warningText} text-[10px] font-bold`}>{t('dw.cfg.form.modified')}</span>
          )}
        </div>
        <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5 ml-7`}>
          {t('dw.cfg.form.itemCount', { count: currentGroup.items.length })}
        </p>
      </div>

      {/* Form Fields */}
      <div className="px-5 py-4 space-y-4">
        {currentGroup.items.map(item => {
          const rawValue = values[item.key];
          const displayValue = rawValue !== undefined ? rawValue : item.defaultValue;
          const defaultValue = defaults[item.key] ?? item.defaultValue;
          const isModified = String(rawValue ?? '') !== String(originalValues[item.key] ?? '');
          const isPassword = item.type === 'password';
          const passwordRevealed = revealedPasswords.has(item.key);
          const isDisabled = item.disabled === true;

          return (
            <div key={item.key} className={`space-y-1.5 ${isDisabled ? 'opacity-50' : ''}`}>
              {/* Label + Description */}
              <div className="flex items-center gap-2">
                <label className={`text-xs font-semibold ${styles.cardText}`}>
                  {t(item.labelKey)}
                </label>
                {isPassword && (
                  <span title={t('dw.cfg.form.sensitive')}>
                    <Shield size={11} className={`${styles.warningText}`} />
                  </span>
                )}
                {isDisabled && (
                  <span title={item.disabledReasonKey ? t(item.disabledReasonKey) : undefined}>
                    <Lock size={11} className={`${styles.cardTextMuted}`} />
                  </span>
                )}
                {isModified && (
                  <span className={`w-1.5 h-1.5 rounded-full ${styles.warningBg}`} title={t('dw.cfg.form.modifiedTitle')} />
                )}
              </div>

              {/* Disabled reason banner */}
              {isDisabled && item.disabledReasonKey && (
                <p className={`text-[10px] ${styles.warningText} italic`}>
                  {t(item.disabledReasonKey)}
                </p>
              )}

              {/* Input Control */}
              {item.type === 'bool' ? (
                <label className={`flex items-center gap-2 select-none ${isDisabled ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                  <button
                    type="button"
                    role="switch"
                    aria-checked={displayValue === true || displayValue === 'true'}
                    disabled={isDisabled}
                    onClick={() => !isDisabled && onValueChange(item.key, !(displayValue === true || displayValue === 'true'))}
                    className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                      displayValue === true || displayValue === 'true'
                        ? styles.accentBg
                        : styles.sidebarBg
                    } ${isDisabled ? 'cursor-not-allowed' : ''}`}
                  >
                    <span
                      className={`inline-block h-3.5 w-3.5 transform rounded-full ${styles.cardBg} transition-transform shadow-sm ${
                        displayValue === true || displayValue === 'true' ? 'translate-x-[18px]' : 'translate-x-[3px]'
                      }`}
                    />
                  </button>
                  <span className={`text-xs ${styles.cardTextMuted}`}>
                    {displayValue === true || displayValue === 'true' ? t('dw.cfg.form.enabled') : t('dw.cfg.form.disabled')}
                  </span>
                </label>
              ) : item.type === 'enum' && item.options ? (
                <select
                  value={String(displayValue)}
                  disabled={isDisabled}
                  onChange={e => onValueChange(item.key, e.target.value)}
                  className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors disabled:cursor-not-allowed`}
                >
                  {item.options.map(opt => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              ) : isPassword ? (
                <div className="relative max-w-sm">
                  <input
                    type={passwordRevealed ? 'text' : 'password'}
                    value={passwordRevealed ? String(displayValue ?? '') : '********'}
                    disabled={isDisabled}
                    onChange={e => onValueChange(item.key, e.target.value)}
                    className={`w-full px-2.5 py-1.5 pr-16 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.warningBorder} focus:ring-1 focus:${styles.warningBorder} outline-none transition-colors disabled:cursor-not-allowed`}
                    placeholder="********"
                  />
                  <button
                    type="button"
                    onClick={() => onTogglePassword(item.key)}
                    disabled={isDisabled}
                    className={`absolute right-1 top-1/2 -translate-y-1/2 p-1 rounded hover:${styles.sidebarBg} ${styles.cardTextMuted} hover:${styles.cardTextMuted} transition-colors disabled:cursor-not-allowed disabled:opacity-50`}
                    title={passwordRevealed ? t('dw.cfg.form.hide') : t('dw.cfg.form.show')}
                  >
                    {passwordRevealed ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              ) : item.type === 'int' ? (
                <input
                  type="number"
                  step="1"
                  value={displayValue as number}
                  disabled={isDisabled}
                  onChange={e => onValueChange(item.key, parseInt(e.target.value, 10) || 0)}
                  className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors disabled:cursor-not-allowed`}
                />
              ) : item.type === 'float' ? (
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  max="1"
                  value={displayValue as number}
                  disabled={isDisabled}
                  onChange={e => onValueChange(item.key, parseFloat(e.target.value) || 0)}
                  className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors disabled:cursor-not-allowed`}
                />
              ) : (
                <input
                  type="text"
                  value={String(displayValue ?? '')}
                  disabled={isDisabled}
                  onChange={e => onValueChange(item.key, e.target.value)}
                  className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors disabled:cursor-not-allowed`}
                />
              )}

              {/* Description + Default hint */}
              <p className={`text-[10px] ${styles.cardTextMuted} flex items-center gap-1`}>
                {t(item.descriptionKey)}
                <span className={`${styles.cardTextMuted}`}>|</span>
                <span>{t('dw.cfg.form.default')}: </span>
                <code className={`text-[10px] ${styles.sidebarBg} px-1 rounded ${styles.cardTextMuted}`}>
                  {item.type === 'password' ? '****' : String(defaultValue)}
                </code>
              </p>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default DataEngineConfigPanelForm;
