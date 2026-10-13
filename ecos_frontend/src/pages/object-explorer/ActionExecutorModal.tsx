/**
 * ActionExecutorModal — 操作执行参数表单弹窗
 *
 * 无假真理整改：平台不存在动作执行/写回端点（services/ontologyApi 全表面核实，
 * action-types 仅 GET）。此前「执行写回」按钮 toast 成功 + refetch 原数据属假写回，
 * 现按钮禁用并如实标注「动作执行未接入」；父级 handleExecuteAction 为双保险（仅报错）。
 * 文案全部走 t() 双语，色板改用主题语义 token（原 amber/red 裸色违规一并订正）。
 * @license Apache-2.0
 */
import React from 'react';
import { Info, X, Zap } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import type { ActionType, ObjectType } from '../../types/ontology';

interface Props {
  selectedAction: ActionType;
  activeObjectType: ObjectType | null;
  actionParams: Record<string, string>;
  setActionParams: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  actionError: string | null;
  setSelectedAction: (v: null) => void;
  handleExecuteAction: () => void;
}

export default function ActionExecutorModal({ selectedAction, activeObjectType, actionParams, setActionParams, actionError, setSelectedAction, handleExecuteAction }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // [2026-10-13 检视修复] 目标状态枚举不再按 flight/aircraft 演示对象硬编码：
  // 动态解析动作 schema validationRules 中 `parameter.<id> IN ["A","B"]` 形态的取值；
  // 无枚举来源 ⇒ 禁用 + 「状态枚举源未上线」标注（动作执行本身已禁用，见上方横幅）。
  const enumByParam = React.useMemo(() => {
    const map: Record<string, string[] | null> = {};
    for (const p of selectedAction.parameters) {
      if (p.id.includes('status')) {
        const pattern = new RegExp(`parameter\\.${p.id}\\s+IN\\s*\\[([^\\]]*)\\]`, 'i');
        let values: string[] | null = null;
        for (const rule of selectedAction.validationRules ?? []) {
          const m = pattern.exec(rule.expression || '');
          if (m) {
            const vs = (m[1].match(/["']([^"']+)["']/g) ?? []).map(s => s.slice(1, -1));
            if (vs.length > 0) { values = vs; break; }
          }
        }
        map[p.id] = values;
      }
    }
    return map;
  }, [selectedAction]);

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-3xs flex items-center justify-center z-50">
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-2xl p-5 w-full sm:w-[420px] mx-4 sm:mx-auto space-y-4`}>
        <div className={`flex justify-between items-center pb-2.5 border-b ${styles.divider}`}>
          <div className="flex items-center gap-1.5"><Zap size={14} className={styles.warningText} /><h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.explorer.modal.title').replace('{name}', selectedAction.displayName)}</h3></div>
          <button type="button" onClick={() => setSelectedAction(null)} className={`${styles.cardTextMuted} hover:opacity-70`}><X size={14} /></button>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} leading-relaxed`}>{selectedAction.description}</p>
        {/* 执行能力未接入：显式告知，不伪装可写回 */}
        <div className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
          <Info size={13} className="shrink-0 mt-0.5" />
          <span>{t('ow.explorer.action_exec_unsupported')}</span>
        </div>
        <div className="space-y-3 pt-1">
          {selectedAction.parameters.map(param => {
            const isObjectParam = param.dataType === 'object';
            const isLocked = isObjectParam && param.objectTypeId === activeObjectType?.id;
            return (
              <div key={param.id} className="space-y-1 text-xs">
                <label className={`text-xs ${styles.cardTextMuted} font-semibold flex items-center justify-between`}><span>{param.displayName} ({param.id})</span>{param.isRequired && <span className={`${styles.dangerText} font-bold`}>* {t('ow.explorer.param.required')}</span>}</label>
                {isLocked ? (<input type="text" disabled value={actionParams[param.id] || ''} className={`w-full h-8 text-xs ${styles.inputBg} border ${styles.cardBorder} rounded px-2.5 ${styles.cardTextMuted} font-mono`} />)
                : enumByParam[param.id] !== undefined ? (enumByParam[param.id] ? (
                  <select value={actionParams[param.id] || ''} onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })} className={`w-full h-8 text-xs ${styles.inputBg} ${styles.inputText} border ${styles.cardBorder} rounded px-2`}><option value="">{t('ow.explorer.param.selectStatus')}</option>{(enumByParam[param.id] ?? []).map(v => <option key={v} value={v}>{v}</option>)}</select>
                ) : (
                  <>
                    <select disabled value="" className={`w-full h-8 text-xs ${styles.inputBg} ${styles.inputText} border ${styles.cardBorder} rounded px-2 opacity-60 cursor-not-allowed`}><option value="">{t('dw.objectExplorer.action.enumNotOnline')}</option></select>
                    <p className={`text-xs ${styles.warningText}`}>{t('dw.objectExplorer.action.enumNotOnlineHint')}</p>
                  </>
                ))
                : param.dataType === 'date' ? (<input type="date" value={actionParams[param.id] || ''} onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })} className={`w-full h-8 text-xs ${styles.inputBg} ${styles.inputText} border ${styles.cardBorder} rounded px-2.5`} />)
                : (<input type="text" placeholder={t('ow.explorer.param.inputPlaceholder').replace('{name}', param.displayName)} value={actionParams[param.id] || ''} onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })} className={`w-full h-8 text-xs ${styles.inputBg} ${styles.inputText} border ${styles.cardBorder} rounded px-2.5`} />)}
                <p className={`text-xs ${styles.cardTextMuted}`}>{param.description}</p>
              </div>
            );
          })}
        </div>
        {actionError && (<div className={`p-2 rounded border text-xs font-medium ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>{t('ow.explorer.constraint_violation').replace('{error}', actionError)}</div>)}
        <div className={`flex justify-end gap-2 pt-2 border-t ${styles.divider} text-xs`}>
          <button type="button" onClick={() => setSelectedAction(null)} className={`h-8 px-3 rounded ${styles.appBg} hover:opacity-70 ${styles.cardText} font-semibold`}>{t('ow.btn.cancel')}</button>
          {/* 无写端点 ⇒ 禁用（无假真理：不提供可点却什么都不写的「执行」按钮） */}
          <button type="button" disabled title={t('ow.explorer.action_exec_unsupported')} onClick={handleExecuteAction} className={`h-8 px-4 rounded flex items-center gap-1 font-semibold ${styles.inputBg} ${styles.cardTextMuted} ${styles.cardBorder} border opacity-60 cursor-not-allowed`}>
            <Info size={12} /><span>{t('ow.explorer.exec_button_disabled')}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
