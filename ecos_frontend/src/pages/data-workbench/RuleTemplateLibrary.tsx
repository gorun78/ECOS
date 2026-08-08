import React, { useState, useEffect } from 'react';
import { AlertTriangle, Copy, Sliders, FileCheck, Terminal, X, Check, ChevronRight } from 'lucide-react';
import { createDqItem, fetchDatasets } from '../../api';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';

interface DqTemplate {
  id: string; nameZh: string; nameEn: string; type: string;
  icon: React.ElementType; defaultParams: Record<string, any>;
  descriptionZh: string; descriptionEn: string;
}
const TEMPLATES: DqTemplate[] = [
  { id: 'tpl-null-rate', nameZh: '空值率检查', nameEn: 'Null Rate Check', type: 'COMPLETENESS',
    icon: AlertTriangle, defaultParams: { threshold: 0.05 },
    descriptionZh: '字段空值占比>5%时告警', descriptionEn: 'Alert when field null ratio >5%' },
  { id: 'tpl-dup-rate', nameZh: '重复率检查', nameEn: 'Duplicate Rate Check', type: 'UNIQUENESS',
    icon: Copy, defaultParams: { threshold: 0.01 },
    descriptionZh: '重复行占比>1%时告警', descriptionEn: 'Alert when duplicate row ratio >1%' },
  { id: 'tpl-value-range', nameZh: '值域范围', nameEn: 'Value Range', type: 'VALIDITY',
    icon: Sliders, defaultParams: { min: 0, max: 100 },
    descriptionZh: '数值超出[min,max]告警', descriptionEn: 'Alert when value outside [min,max]' },
  { id: 'tpl-format', nameZh: '格式校验', nameEn: 'Format Validation', type: 'VALIDITY',
    icon: FileCheck, defaultParams: { regex: '^[\\\\w.-]+@[\\\\w-]+\\\\.\\\\w{2,}$' },
    descriptionZh: '正则匹配校验(邮箱/手机/日期)', descriptionEn: 'Regex validation (email/phone/date)' },
  { id: 'tpl-custom-sql', nameZh: '自定义SQL', nameEn: 'Custom SQL', type: 'ACCURACY',
    icon: Terminal, defaultParams: { sql: 'SELECT COUNT(*) FROM {table} WHERE {condition}' },
    descriptionZh: '自定义检查SQL返回count', descriptionEn: 'Custom SQL returns count to check quality' },
];

function buildExpr(tplId: string, params: Record<string, any>, fields: string[]): string {
  const f = fields.join(', ') || '{field}';
  switch (tplId) {
    case 'tpl-null-rate': return `NULL_RATIO(${f}) > ${params.threshold}`;
    case 'tpl-dup-rate': return `DUPLICATE_RATIO(${f}) > ${params.threshold}`;
    case 'tpl-value-range': return `VALUE(${f}) NOT BETWEEN ${params.min} AND ${params.max}`;
    case 'tpl-format': return `REGEXP(${f}, '${params.regex}')`;
    case 'tpl-custom-sql': return params.sql.replace(/\{table\}/g, fields[0] || '{table}');
    default: return '';
  }
}

interface Props { onClose: () => void; onApplied: () => void; }

export default function RuleTemplateLibrary({ onClose, onApplied }: Props) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;
  const [step, setStep] = useState(0);
  const [tpl, setTpl] = useState<DqTemplate | null>(null);
  const [datasets, setDatasets] = useState<any[]>([]);
  const [table, setTable] = useState('');
  const [fields, setFields] = useState<string[]>([]);
  const [params, setParams] = useState<Record<string, any>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { fetchDatasets().then(d => setDatasets(Array.isArray(d) ? d : [])).catch(() => {}); }, []);

  const cur = datasets.find((d: any) => d.name === table || d.id === table);
  const cols: { name: string; type: string }[] = cur?.schema || [];

  function selectTpl(item: DqTemplate) { setTpl(item); setParams({...item.defaultParams}); setTable(''); setFields([]); setError(''); setStep(1); }
  function toggleField(name: string) { setFields(p => p.includes(name) ? p.filter(f => f !== name) : [...p, name]); }

  async function confirm() {
    if (!tpl || !table) { setError(tl('请选择目标表','Please select a target table')); return; }
    setSaving(true); setError('');
    try {
      const expr = buildExpr(tpl.id, params, fields.length ? fields : [table]);
      await createDqItem('rules', {
        name: locale === 'zh' ? tpl.nameZh : tpl.nameEn,
        code: tpl.id + '-' + Date.now(),
        ruleType: tpl.type, targetEntity: table, targetField: fields.join(','),
        ruleExpression: expr, severity: 'MEDIUM', status: 'ACTIVE',
        description: locale === 'zh' ? tpl.descriptionZh : tpl.descriptionEn,
      });
      onApplied(); onClose();
    } catch (e: any) { setError(e?.message || String(e)); }
    setSaving(false);
  }

  // ── Cards view ──
  if (step === 0) return (
    <div className="space-y-4">
      <h3 className={`text-sm font-bold ${styles.cardText}`}>{tl('DQ规则模板库','DQ Rule Template Library')}</h3>
      <div className="grid grid-cols-1 gap-2">
        {TEMPLATES.map(t => {
          const Icon = t.icon;
          return (
            <div key={t.id} onClick={() => selectTpl(t)}
              className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex items-center gap-3 hover:shadow-md transition-shadow cursor-pointer`}>
              <Icon className="w-5 h-5 text-indigo-500 shrink-0" />
              <div className="flex-1 min-w-0">
                <div className={`text-xs font-semibold ${styles.cardText}`}>
                  {locale === 'zh' ? t.nameZh : t.nameEn}
                  <span className="ml-2 text-[10px] text-slate-400 font-mono">{t.type}</span>
                </div>
                <div className="text-[10px] text-slate-400 mt-0.5 truncate">{locale === 'zh' ? t.descriptionZh : t.descriptionEn}</div>
              </div>
              <ChevronRight className="w-4 h-4 text-slate-300" />
            </div>
          );
        })}
      </div>
    </div>
  );

  // ── Wizard view ──
  const TIcon = tpl?.icon;
  return (
    <div className="space-y-4">
      <button onClick={() => setStep(0)} className="text-xs text-indigo-500 hover:text-indigo-700">← {tl('返回','Back')}</button>
      {tpl && TIcon && (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex items-center gap-2`}>
          <TIcon className="w-4 h-4 text-indigo-500" />
          <span className={`text-xs font-semibold ${styles.cardText}`}>{locale==='zh'?tpl.nameZh:tpl.nameEn}</span>
          <span className="text-[10px] text-slate-400 font-mono">{tpl.type}</span>
        </div>
      )}
      <div>
        <label className={`text-[11px] font-semibold ${styles.cardText} block mb-1`}>{tl('目标表','Target Table')}</label>
        <select className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
          value={table} onChange={e => { setTable(e.target.value); setFields([]); }}>
          <option value="">{tl('-- 选择表 --','-- Select table --')}</option>
          {datasets.map((d: any) => <option key={d.id||d.name} value={d.name||d.id}>{d.name||d.id}</option>)}
        </select>
      </div>
      {cols.length > 0 && (
        <div>
          <label className={`text-[11px] font-semibold ${styles.cardText} block mb-1`}>{tl('选择字段','Fields')} ({fields.length})</label>
          <div className={`border ${styles.cardBorder} rounded-lg max-h-32 overflow-y-auto p-2`}>
            {cols.map(c => (
              <label key={c.name} className="flex items-center gap-2 py-1 cursor-pointer hover:bg-slate-50 rounded px-1">
                <input type="checkbox" checked={fields.includes(c.name)} onChange={() => toggleField(c.name)} className="w-3 h-3 accent-indigo-500" />
                <span className="text-[11px] text-slate-600">{c.name}</span>
                <span className="text-[9px] text-slate-400 ml-auto">{c.type}</span>
              </label>
            ))}
          </div>
        </div>
      )}
      {tpl && Object.keys(tpl.defaultParams).length > 0 && (
        <div>
          <label className={`text-[11px] font-semibold ${styles.cardText} block mb-1`}>{tl('参数配置','Parameters')}</label>
          <div className="space-y-2">
            {Object.entries(tpl.defaultParams).map(([k, v]) => (
              <div key={k} className="flex items-center gap-2">
                <span className="text-[10px] text-slate-500 w-20 shrink-0">{k}</span>
                <input className={`flex-1 border ${styles.inputBorder} ${styles.inputBg} rounded px-2 py-1 text-[10px] outline-none focus:border-indigo-400`}
                  value={typeof params[k]==='string' ? params[k] : String(params[k])}
                  onChange={e => { const val=e.target.value; setParams({...params,[k]:typeof v==='number'?parseFloat(val)||0:val}); }} />
              </div>
            ))}
          </div>
        </div>
      )}
      {error && <div className="text-[11px] text-red-500 bg-red-50 rounded px-3 py-2">{error}</div>}
      <div className="flex justify-end gap-2 pt-2">
        <button onClick={onClose} className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg">{tl('取消','Cancel')}</button>
        <button onClick={confirm} disabled={saving}
          className="px-4 py-2 text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg flex items-center gap-1.5 disabled:opacity-50">
          <Check className="w-3 h-3" /> {saving ? tl('创建中...','Creating...') : tl('确认创建','Confirm')}
        </button>
      </div>
    </div>
  );
}
