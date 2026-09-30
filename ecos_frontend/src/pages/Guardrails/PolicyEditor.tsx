import { useTheme } from '../../components/ThemeContext';
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

  return (
    <div className="flex-1 flex flex-col overflow-y-auto">
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between shrink-0`}>
        <span className={`font-extrabold ${styles.cardText} flex items-center gap-1.5`}>
          {mode === 'create' ? <Plus size={13} className="text-rose-500" /> : <Edit3 size={13} className="text-blue-500" />}
          <span>{mode === 'create' ? '新建护栏策略' : '编辑护栏策略'}</span>
        </span>
        <button onClick={onCancel} className={`${styles.cardTextMuted} ${styles.sidebarHoverBg} cursor-pointer`}><X size={16} /></button>
      </div>

      <div className="p-4 space-y-4 flex-1">
        {/* Name */}
        <div className="space-y-1">
          <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>策略名称 <span className="text-rose-500">*</span></label>
          <input
            value={policy.name}
            onChange={e => set('name', e.target.value)}
            placeholder="例如：飞行员 SSN 列级脱敏"
            className={`w-full px-3 py-2 rounded-lg text-[11px] ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
          />
        </div>

        {/* Description */}
        <div className="space-y-1">
          <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>策略描述</label>
          <textarea
            value={policy.description}
            onChange={e => set('description', e.target.value)}
            rows={2}
            placeholder="描述该护栏的合规目的与拦截范围..."
            className={`w-full px-3 py-2 rounded-lg text-[11px] resize-none ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
          />
        </div>

        {/* Type + Severity */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>策略类型</label>
            <select
              value={policy.type}
              onChange={e => set('type', e.target.value as PolicyType)}
              className={`w-full px-2 py-2 rounded-lg text-[11px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
            >
              {TYPE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>严重级别</label>
            <select
              value={policy.severity}
              onChange={e => set('severity', e.target.value as PolicySeverity)}
              className={`w-full px-2 py-2 rounded-lg text-[11px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
            >
              <option value="block">block (强制阻断)</option>
              <option value="warn">warn (记录审计)</option>
            </select>
          </div>
        </div>

        {/* Type-specific config */}
        {(policy.type === 'column_masking' || policy.type === 'pii_redaction') && (
          <div className={`grid grid-cols-2 gap-3 p-3 ${styles.appBg} rounded-lg border ${styles.cardBorder} space-y-0`}>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>目标表 (Table)</label>
              <input value={policy.table || ''} onChange={e => set('table', e.target.value)} placeholder="ds_pilots_biography" className={`w-full px-2 py-1.5 rounded-md text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`} />
            </div>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>目标列 (Column)</label>
              <input value={policy.column || ''} onChange={e => set('column', e.target.value)} placeholder="ssn_number" className={`w-full px-2 py-1.5 rounded-md text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`} />
            </div>
            <div className="space-y-1 col-span-2">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>脱敏策略 (Mask Type)</label>
              <select
                value={policy.maskType || 'REDACT'}
                onChange={e => set('maskType', e.target.value as MaskType)}
                className={`w-full px-2 py-1.5 rounded-md text-[11px] font-bold ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`}
              >
                <option value="REDACT">REDACT (强物理抹除)</option>
                <option value="PARTIAL">PARTIAL (部分遮蔽)</option>
                <option value="HASH">HASH (混淆哈希)</option>
              </select>
            </div>
          </div>
        )}

        {policy.type === 'row_filtering' && (
          <div className={`p-3 ${styles.appBg} rounded-lg border ${styles.cardBorder} space-y-2`}>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>目标表 (Table)</label>
              <input value={policy.table || ''} onChange={e => set('table', e.target.value)} placeholder="ds_flights_clean" className={`w-full px-2 py-1.5 rounded-md text-[11px] font-mono ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} focus:outline-none focus:border-blue-500`} />
            </div>
            <div className="space-y-1">
              <label className={`block ${styles.cardText} font-bold text-[10px] uppercase`}>SQL WHERE 谓词</label>
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
                <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase`}>推荐模板:</span>
                {['hours_flown > 6000', "licence_rating = 'B737-MAX'", 'base_salary < 80000', "status = 'ON_TIME'", 'delay_minutes > 0'].map(t => (
                  <button key={t} onClick={() => set('condition', t)} className={`px-1.5 py-0.5 ${styles.badgeBg} ${styles.sidebarHoverBg} rounded font-mono text-[9px] font-bold ${styles.cardText} cursor-pointer`}>{t}</button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Enabled toggle */}
        <div className={`flex items-center justify-between p-3 ${styles.appBg} rounded-lg border ${styles.cardBorder}`}>
          <div>
            <span className={`font-bold ${styles.cardText} text-[11px] block`}>启用策略</span>
            <span className={`text-[10px] ${styles.cardTextMuted}`}>关闭后该护栏将不参与运行时拦截判定。</span>
          </div>
          <Toggle on={policy.isEnabled} onClick={() => set('isEnabled', !policy.isEnabled)} />
        </div>
      </div>

      {/* Footer actions */}
      <div className={`p-4 border-t ${styles.cardBorder} ${styles.appBg} flex gap-2 justify-end shrink-0`}>
        <button onClick={onCancel} className={`px-4 py-2 border ${styles.cardBorder} ${styles.sidebarHoverBg} rounded-lg text-[11px] font-bold ${styles.cardText} cursor-pointer`}>取消</button>
        <button
          onClick={onSave}
          disabled={saving}
          className="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-70 text-white font-bold rounded-lg text-[11px] flex items-center gap-1.5 cursor-pointer"
        >
          {saving ? <Spinner /> : <Save size={12} />}
          {saving ? '保存中...' : '保存策略'}
        </button>
      </div>
    </div>
  );
}
