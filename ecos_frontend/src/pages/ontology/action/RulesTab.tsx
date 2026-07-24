/**
 * RulesTab — 操作副作用规则 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Activity, Edit3, FilePlus, Trash, Trash2 } from 'lucide-react';
import DynamicIcon from '../../../components/ontology/DynamicIcon';
import type { ActionType, ActionRule, ActionRuleType, ObjectType } from '../../../types/ontology';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

interface Props {
  actionType: ActionType;
  objectTypes: ObjectType[];
  handleAddRule: (type: ActionRuleType) => void;
  handleRemoveRule: (ruleId: string) => void;
  handleRuleChange: (ruleId: string, field: keyof ActionRule, value: any) => void;
  handleAddPropertyEdit: (ruleId: string, propertyId: string) => void;
  handlePropertyEditValueChange: (ruleId: string, propertyId: string, expr: string) => void;
  handleRemovePropertyEdit: (ruleId: string, propertyId: string) => void;
}

export default function RulesTab({
  actionType, objectTypes, handleAddRule, handleRemoveRule, handleRuleChange,
  handleAddPropertyEdit, handlePropertyEditValueChange, handleRemovePropertyEdit,
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div><h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.rulesTitle')}</h4><p className={`text-[11px] ${styles.muted} mt-0.5`}>{t('ow.section.rulesDesc')}</p></div>
        <div className="flex gap-2">
          <button onClick={() => handleAddRule('modify_object')} className={`border ${styles.cardBorder} hover:${styles.appBg} ${styles.cardTextMuted} text-xs px-3 py-1.5 rounded flex items-center gap-1.5`}><Edit3 size={13} />{t('ow.btn.addModifyRule')}</button>
          <button onClick={() => handleAddRule('create_object')} className={`${styles.accentBg} hover:bg-blue-700 text-white text-xs px-3 py-1.5 rounded flex items-center gap-1.5`}><FilePlus size={13} />{t('ow.btn.addCreateRule')}</button>
          <button onClick={() => handleAddRule('delete_object')} className="border border-red-300 hover:bg-red-50 text-red-700 text-xs px-3 py-1.5 rounded flex items-center gap-1.5"><Trash2 size={13} className="text-red-500" />{t('ow.btn.addDeleteRule')}</button>
        </div>
      </div>
      {actionType.rules.length === 0 ? (
        <div className={`text-center py-12 border-2 border-dashed ${styles.cardBorder} rounded-xl ${styles.muted} text-xs space-y-2`}><Activity size={24} className={`mx-auto ${styles.muted}`} /><div>{t('ow.empty.noRules')}</div></div>
      ) : (
        <div className="space-y-4">
          {actionType.rules.map((rule, idx) => {
            const targetObjType = objectTypes.find(ot => ot.id === (rule.type === 'create_object' ? rule.targetObjectTypeId : actionType.parameters.find(p => p.id === rule.targetParameterId)?.objectTypeId));
            return (
              <div key={rule.id} className={`border ${styles.cardBorder} rounded-xl p-5 ${styles.appBg} space-y-4 shadow-2xs relative`}>
                <button onClick={() => handleRemoveRule(rule.id)} className={`absolute top-4 right-4 ${styles.muted} hover:text-red-500 p-1 rounded hover:${styles.cardBg}`} title="删除此逻辑块"><Trash size={14} /></button>
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-semibold ${styles.badgeBg} ${styles.cardTextMuted} px-2 py-0.5 rounded-full font-mono`}>{t('ow.label.ruleNumber').replace('{n}', String(idx + 1))}</span>
                  <div className={`text-xs font-semibold ${styles.cardText}`}>{rule.type === 'create_object' ? t('ow.action.ruleCreate') : rule.type === 'delete_object' ? t('ow.action.ruleDelete') : t('ow.action.ruleModify')}</div>
                </div>
                <div className={`grid grid-cols-2 gap-4 ${styles.cardBg} p-4 rounded-lg border ${styles.cardBorder}`}>
                  {rule.type === 'create_object' ? (
                    <div className="space-y-1 text-xs"><label className={`text-[11px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.instantiateObjectType')}</label><select value={rule.targetObjectTypeId || ''} onChange={e => handleRuleChange(rule.id, 'targetObjectTypeId', e.target.value)} className={`px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} w-full`}>{objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>))}</select></div>
                  ) : rule.type === 'delete_object' ? (
                    <div className="space-y-1 text-xs"><label className={`text-[11px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.targetDeleteParam')}</label><select value={rule.targetParameterId || ''} onChange={e => handleRuleChange(rule.id, 'targetParameterId', e.target.value)} className={`px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} w-full`}><option value="">{t('ow.placeholder.selectDeleteParam')}</option>{actionType.parameters.filter(p => p.dataType === 'object').map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}</select></div>
                  ) : (
                    <div className="space-y-1 text-xs"><label className={`text-[11px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.targetModifyParam')}</label><select value={rule.targetParameterId || ''} onChange={e => handleRuleChange(rule.id, 'targetParameterId', e.target.value)} className={`px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} w-full`}><option value="">{t('ow.placeholder.selectObjectParam')}</option>{actionType.parameters.filter(p => p.dataType === 'object').map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}</select></div>
                  )}
                  <div className="flex items-end justify-between text-xs"><div className={`${styles.muted} text-[11px]`}>{targetObjType ? (<span>{t('ow.label.targetEntity')} <strong className={styles.cardText}>{targetObjType.displayName}</strong></span>) : (<span className="italic">{t('ow.label.awaitingParamBinding')}</span>)}</div></div>
                </div>
                {targetObjType && (
                  <div className={`space-y-3 ${styles.cardBg} p-4 rounded-lg border ${styles.cardBorder}`}>
                    <div className={`flex justify-between items-center border-b ${styles.appBg} pb-2`}>
                      <span className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.fieldModifyBehavior')}</span>
                      <select onChange={e => { if (e.target.value) { handleAddPropertyEdit(rule.id, e.target.value); e.target.value = ''; } }} className={`px-2 py-1 text-[11px] border ${styles.cardBorder} rounded ${styles.cardBg}`}>
                        <option value="">{t('ow.btn.addPropertyEdit')}</option>
                        {targetObjType.properties.map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}
                      </select>
                    </div>
                    {rule.propertyEdits && rule.propertyEdits.length === 0 ? (
                      <div className={`text-center py-4 ${styles.muted} text-xs italic`}>{t('ow.empty.noPropertyEdits')}</div>
                    ) : (
                      <div className="space-y-2">
                        {rule.propertyEdits?.map(edit => {
                          const propDef = targetObjType.properties.find(p => p.id === edit.propertyId);
                          return (
                            <div key={edit.propertyId} className={`flex items-center gap-3 ${styles.appBg} px-3 py-2 rounded border ${styles.cardBorder} text-xs`}>
                              <div className="w-1/3 flex items-center gap-1.5"><DynamicIcon name={targetObjType.primaryKey === edit.propertyId ? 'Key' : 'Tag'} size={12} className={targetObjType.primaryKey === edit.propertyId ? 'text-amber-500' : `${styles.muted}`} /><span className={`font-semibold ${styles.cardText}`}>{propDef?.displayName || edit.propertyId}</span><span className={`text-[10px] ${styles.muted} font-mono`}>{propDef?.dataType}</span></div>
                              <div className="flex-1 flex items-center gap-2"><span className={`${styles.muted} text-[10px]`}>设为 ＝</span><input type="text" value={edit.valueExpression} onChange={e => handlePropertyEditValueChange(rule.id, edit.propertyId, e.target.value)} className={`flex-1 px-2.5 py-1 text-xs border ${styles.cardBorder} rounded font-mono ${styles.cardBg}`} placeholder={`例如 parameter.new_status 或 "MAINTENANCE"`} /></div>
                              <button onClick={() => handleRemovePropertyEdit(rule.id, edit.propertyId)} className={`${styles.muted} hover:text-red-500 p-1`}><Trash2 size={13} /></button>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
