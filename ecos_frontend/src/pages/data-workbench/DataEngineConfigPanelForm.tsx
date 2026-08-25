/**
 * DataEngineConfigPanelForm — 右侧配置表单子组件
 * 从 DataEngineConfigPanel 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import { AlertCircle, Shield, Eye, EyeOff } from 'lucide-react';
import type { ConfigGroup, ConfigValues, DefaultValues } from './DataEngineConfigPanel';
import type { ThemeStyles } from '../../components/ThemeContext';

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
  return (
    <div className="flex-1 flex flex-col min-h-0 overflow-y-auto">
      {loading ? (
        <div className={`flex items-center justify-center flex-1 ${styles.cardTextMuted} text-sm`}>
          正在加载配置...
        </div>
      ) : loadError ? (
        <div className="flex flex-col items-center justify-center flex-1 gap-2">
          <AlertCircle size={24} className={`${styles.warningText}`} />
          <span className={`text-sm ${styles.cardTextMuted}`}>{loadError}</span>
          <button
            onClick={onRetry}
            className={`px-3 py-1 text-xs ${styles.accentText} hover:${styles.infoBg} rounded-md transition-colors`}
          >
            重试
          </button>
        </div>
      ) : (
        <>
          {/* Group Title */}
          <div className={`px-5 py-3 border-b ${styles.cardBorder} ${styles.cardBg} sticky top-0 z-10`}>
            <div className="flex items-center gap-2">
              <span className={`${styles.cardTextMuted}`}>
                {currentGroup.icon}
              </span>
              <h3 className={`text-sm font-bold ${styles.cardText}`}>
                {currentGroup.label}
              </h3>
              {currentGroup.modified && (
                <span className={`${styles.warningText} text-[10px] font-bold`}>● 已修改</span>
              )}
            </div>
            <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5 ml-7`}>
              共 {currentGroup.items.length} 项配置
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

              return (
                <div key={item.key} className="space-y-1.5">
                  {/* Label + Description */}
                  <div className="flex items-center gap-2">
                    <label className={`text-xs font-semibold ${styles.cardText}`}>
                      {item.label}
                    </label>
                    {isPassword && (
                      <Shield size={11} className={`${styles.warningText}`} title="敏感字段" />
                    )}
                    {isModified && (
                      <span className={`w-1.5 h-1.5 rounded-full ${styles.warningBg}`} title="已修改" />
                    )}
                  </div>

                  {/* Input Control */}
                  {item.type === 'bool' ? (
                    <label className="flex items-center gap-2 cursor-pointer select-none">
                      <button
                        type="button"
                        role="switch"
                        aria-checked={displayValue === true || displayValue === 'true'}
                        onClick={() => onValueChange(item.key, !(displayValue === true || displayValue === 'true'))}
                        className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                          displayValue === true || displayValue === 'true'
                            ? styles.accentBg
                            : styles.sidebarBg
                        }`}
                      >
                        <span
                          className={`inline-block h-3.5 w-3.5 transform rounded-full ${styles.cardBg} transition-transform shadow-sm ${
                            displayValue === true || displayValue === 'true' ? 'translate-x-[18px]' : 'translate-x-[3px]'
                          }`}
                        />
                      </button>
                      <span className={`text-xs ${styles.cardTextMuted}`}>
                        {displayValue === true || displayValue === 'true' ? '启用' : '禁用'}
                      </span>
                    </label>
                  ) : item.type === 'enum' && item.options ? (
                    <select
                      value={String(displayValue)}
                      onChange={e => onValueChange(item.key, e.target.value)}
                      className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors`}
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
                        onChange={e => onValueChange(item.key, e.target.value)}
                        className={`w-full px-2.5 py-1.5 pr-16 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.warningBorder} focus:ring-1 focus:${styles.warningBorder} outline-none transition-colors`}
                        placeholder="********"
                      />
                      <button
                        type="button"
                        onClick={() => onTogglePassword(item.key)}
                        className={`absolute right-1 top-1/2 -translate-y-1/2 p-1 rounded hover:${styles.sidebarBg} ${styles.cardTextMuted} hover:${styles.cardTextMuted} transition-colors`}
                        title={passwordRevealed ? '隐藏' : '显示'}
                      >
                        {passwordRevealed ? <EyeOff size={14} /> : <Eye size={14} />}
                      </button>
                    </div>
                  ) : item.type === 'int' ? (
                    <input
                      type="number"
                      step="1"
                      value={displayValue as number}
                      onChange={e => onValueChange(item.key, parseInt(e.target.value, 10) || 0)}
                      className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors`}
                    />
                  ) : item.type === 'float' ? (
                    <input
                      type="number"
                      step="0.1"
                      min="0"
                      max="1"
                      value={displayValue as number}
                      onChange={e => onValueChange(item.key, parseFloat(e.target.value) || 0)}
                      className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors`}
                    />
                  ) : (
                    <input
                      type="text"
                      value={String(displayValue ?? '')}
                      onChange={e => onValueChange(item.key, e.target.value)}
                      className={`w-full max-w-sm px-2.5 py-1.5 text-xs border ${styles.inputBorder} rounded-md ${styles.cardBg} ${styles.cardText} font-mono focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none transition-colors`}
                    />
                  )}

                  {/* Description + Default hint */}
                  <p className={`text-[10px] ${styles.cardTextMuted} flex items-center gap-1`}>
                    {item.description}
                    <span className={`${styles.cardTextMuted}`}>|</span>
                    <span>默认: </span>
                    <code className={`text-[10px] ${styles.sidebarBg} px-1 rounded ${styles.cardTextMuted}`}>
                      {item.type === 'password' ? '****' : String(defaultValue)}
                    </code>
                  </p>
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
};

export default DataEngineConfigPanelForm;
