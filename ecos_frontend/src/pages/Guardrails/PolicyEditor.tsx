import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { Plus, Edit3, X, Save } from 'lucide-react';
import type { GuardrailPolicy, MaskType, PolicySeverity, PolicyType } from './types';
import { TYPE_OPTIONS } from './constants';
import { Toggle, Spinner } from './UiPrimitives';

// ─────────────────────────────────────────────────────────────
// Policy Editor (create / edit form)
// ─────────────────────────────────────────────────────────────

export default function PolicyEditor({
  policy, mode, saving, onChange, onSave, onCancel,
}: {
  policy: GuardrailPolicy;
  mode: 'create' | 'edit';
  saving: boolean;
  onChange: (p: GuardrailPolicy) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  const set = <K extends keyof GuardrailPolicy>(key: K, value: GuardrailPolicy[K]) =>
    onChange({ ...policy, [key]: value });
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="flex-1 flex flex-col overflow-y-auto">
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between shrink-0`}>
        <span className={`font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
          {mode === 'create' ? <Plus size={13} className="text-rose-500" /> : <Edit3 size={13} className="text-blue-500" />}
          <span>{mode === 'create' ? t('gw.editor.titleCreate') : t('gw.editor.titleEdit')}</span>
        </span>
        <button type="button" onClick={onCancel} className={`${styles.cardTextMuted} ${styles.sidebarHoverBg} cursor-pointer`}><X size={16} /></button>
      </div>

      <div className="p-4 space-y-4 flex-1">
        {/* Name */}
        <div className="space-y-1">
          <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.editor.name')} <span className="text-rose-500">*</span></label>
          <input
            value={policy.name}
            onChange={e => set('name', e.target.value)}
            placeholder={t('gw.editor.namePh')}
            className={`w-full px-3 py-2 rounded-lg text-[11px] ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
          />
        </div>

        {/* Description */}
        <div className="space-y-1">
          <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.editor.desc')}</label>
          <textarea
            value={policy.description}
            onChange={e => set('description', e.target.value)}
            rows={2}
            placeholder={t('gw.editor.descPh')}
            className={`w-full px-3 py-2 rounded-lg text-[11px] resize-none ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
          />
        </div>

        {/* Type + Severity */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.editor.type')}</label>
            <select
              value={policy.type}
              onChange={e => set('type', e.target.value as PolicyType)}
              className={`w-full px-2 py-2 rounded-lg text-[11px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
            >
              {TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.labelKey)}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.editor.severity')}</label>
            <select
              value={policy.severity}
              onChange={e => set('severity', e.target.value as PolicySeverity)}
              className={`w-full px-2 py-2 rounded-lg text-[11px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
            >
              <option value="block">{t('gw.sev.block')}</option>
              <option value="warn">{t('gw.sev.warn')}</option>
            </select>
          </div>
        </div>

        {/* Type-specific config */}
        {(policy.type === 'column_masking' || policy.type === 'pii_redaction') && (
          <div className={`grid grid-cols-2 gap-3 p-3 ${styles.appBg} rounded-lg border ${styles.cardBorder} space-y-0`}>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.field.table')}</label>
              <input value={policy.table || ''} onChange={e => set('table', e.target.value)} placeholder="ds_pilots_biography" className={`w-full px-2 py-1.5 rounded-md text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`} />
            </div>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.field.col')}</label>
              <input value={policy.column || ''} onChange={e => set('column', e.target.value)} placeholder="ssn_number" className={`w-full px-2 py-1.5 rounded-md text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`} />
            </div>
            <div className="space-y-1 col-span-2">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.field.maskType')}</label>
              <select
                value={policy.maskType || 'REDACT'}
                onChange={e => set('maskType', e.target.value as MaskType)}
                className={`w-full px-2 py-1.5 rounded-md text-[11px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
              >
                <option value="REDACT">{t('gw.mask.REDACT')}</option>
                <option value="PARTIAL">{t('gw.mask.PARTIAL')}</option>
                <option value="HASH">{t('gw.mask.HASH')}</option>
              </select>
            </div>
          </div>
        )}

        {policy.type === 'row_filtering' && (
          <div className={`p-3 ${styles.appBg} rounded-lg border ${styles.cardBorder} space-y-2`}>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.field.table')}</label>
              <input value={policy.table || ''} onChange={e => set('table', e.target.value)} placeholder="ds_flights_clean" className={`w-full px-2 py-1.5 rounded-md text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`} />
            </div>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>{t('gw.field.where')}</label>
              <div className={`flex items-center gap-1 ${styles.inputBg} rounded-md px-2.5 py-1.5 border ${styles.inputBorder}`}>
                <span className={`font-mono ${styles.cardTextMuted} font-bold text-[10px] select-none`}>WHERE</span>
                <input
                  value={policy.condition || ''}
                  onChange={e => set('condition', e.target.value)}
                  placeholder="hours_flown > 6000"
                  className={`bg-transparent border-0 font-mono text-[11px] ${styles.cardText} font-bold focus:ring-0 focus:outline-none w-full`}
                />
              </div>
              <div className="flex flex-wrap items-center gap-1.5 mt-1">
                <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase`}>{t('gw.field.templates')}</span>
                {['hours_flown > 6000', "licence_rating = 'B737-MAX'", 'base_salary < 80000', "status = 'ON_TIME'", 'delay_minutes > 0'].map(tpl => (
                  <button key={tpl} type="button" onClick={() => set('condition', tpl)} className={`px-1.5 py-0.5 ${styles.badgeBg} ${styles.sidebarHoverBg} rounded font-mono text-[9px] font-bold ${styles.cardText} cursor-pointer`}>{tpl}</button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Enabled toggle */}
        <div className={`flex items-center justify-between p-3 ${styles.appBg} rounded-lg border ${styles.cardBorder}`}>
          <div>
            <span className={`font-bold ${styles.cardText} text-[11px] block`}>{t('gw.editor.enable')}</span>
            <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('gw.editor.enableHint')}</span>
          </div>
          <Toggle on={policy.isEnabled} onClick={() => set('isEnabled', !policy.isEnabled)} />
        </div>
      </div>

      {/* Footer actions */}
      <div className={`p-4 border-t ${styles.cardBorder} ${styles.appBg} flex gap-2 justify-end shrink-0`}>
        <button type="button" onClick={onCancel} className={`px-4 py-2 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-lg text-[11px] font-bold ${styles.cardText} cursor-pointer`}>{t('gw.editor.cancel')}</button>
        <button
          type="button"
          onClick={onSave}
          disabled={saving}
          className="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-70 text-white font-bold rounded-lg text-[11px] flex items-center gap-1.5 cursor-pointer"
        >
          {saving ? <Spinner /> : <Save size={12} />}
          {saving ? t('gw.editor.saving') : t('gw.editor.save')}
        </button>
      </div>
    </div>
  );
}
