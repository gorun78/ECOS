/**
 * ActionTypeDetail — 操作类型详情视图
 *
 * 本体工作台操作定义详情页，展示参数/副作用规则/验证/表单布局。
 * Tab 内容已拆分为独立组件（见 ./action/ 目录）。
 *
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { Trash2, Zap } from 'lucide-react';

import type { ActionType, ActionParameter, ActionRule, ActionValidationRule, ObjectType, ActionParamDataType, ActionRuleType } from '../../types/ontology';
import ParametersTab from './action/ParametersTab';
import RulesTab from './action/RulesTab';
import ValidationTab from './action/ValidationTab';
import LayoutTab from './action/LayoutTab';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';

interface ActionTypeViewProps {
  actionType: ActionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: ActionType) => void;
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function ActionTypeView({ actionType, objectTypes, onUpdate, onDelete, onNavigateToObject }: ActionTypeViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState<'parameters' | 'rules' | 'validation' | 'layout'>('parameters');
  const [newParamName, setNewParamName] = useState('');
  const [newParamType, setNewParamType] = useState<ActionParamDataType>('string');
  const [newParamObjType, setNewParamObjType] = useState(objectTypes[0]?.id || '');
  const [newSectionTitle, setNewSectionTitle] = useState('');
  const [newValName, setNewValName] = useState('');
  const [newValExpression, setNewValExpression] = useState('');
  const [newValError, setNewValError] = useState('');

  const handleFieldChange = (key: keyof ActionType, value: any) => { onUpdate({ ...actionType, [key]: value }); };

  const handleAddParam = () => {
    if (!newParamName.trim()) return;
    const paramId = newParamName.trim().replace(/\s+/g, '_').toLowerCase() + '_param';
    const newParam: ActionParameter = { id: paramId, displayName: newParamName, dataType: newParamType, isRequired: true, description: t('ow.action.paramDefaultDescription').replace('{name}', newParamName), objectTypeId: newParamType === 'object' ? newParamObjType : undefined };
    onUpdate({ ...actionType, parameters: [...actionType.parameters, newParam] });
    setNewParamName('');
  };

  const handleRemoveParam = (paramId: string) => {
    onUpdate({ ...actionType, parameters: actionType.parameters.filter(p => p.id !== paramId), rules: actionType.rules.filter(r => r.targetParameterId !== paramId) });
  };

  const handleParamFieldChange = (paramId: string, field: keyof ActionParameter, value: any) => {
    onUpdate({ ...actionType, parameters: actionType.parameters.map(p => p.id === paramId ? { ...p, [field]: value } : p) });
  };

  const handleAddRule = (type: ActionRuleType) => {
    const ruleId = `rule_${Date.now()}`;
    const newRule: ActionRule = { id: ruleId, type, targetObjectTypeId: type === 'create_object' ? objectTypes[0]?.id : undefined, targetParameterId: type !== 'create_object' ? actionType.parameters.find(p => p.dataType === 'object')?.id : undefined, propertyEdits: [] };
    onUpdate({ ...actionType, rules: [...actionType.rules, newRule] });
  };

  const handleRemoveRule = (ruleId: string) => { onUpdate({ ...actionType, rules: actionType.rules.filter(r => r.id !== ruleId) }); };
  const handleRuleChange = (ruleId: string, field: keyof ActionRule, value: any) => { onUpdate({ ...actionType, rules: actionType.rules.map(r => r.id === ruleId ? { ...r, [field]: value } : r) }); };

  const handleAddPropertyEdit = (ruleId: string, propertyId: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule || (rule.propertyEdits || []).some(pe => pe.propertyId === propertyId)) return;
    onUpdate({ ...actionType, rules: actionType.rules.map(r => r.id === ruleId ? { ...r, propertyEdits: [...(r.propertyEdits || []), { propertyId, valueExpression: 'parameter.' }] } : r) });
  };

  const handlePropertyEditValueChange = (ruleId: string, propertyId: string, expr: string) => {
    onUpdate({ ...actionType, rules: actionType.rules.map(r => r.id === ruleId ? { ...r, propertyEdits: (r.propertyEdits || []).map(pe => pe.propertyId === propertyId ? { ...pe, valueExpression: expr } : pe) } : r) });
  };

  const handleRemovePropertyEdit = (ruleId: string, propertyId: string) => {
    onUpdate({ ...actionType, rules: actionType.rules.map(r => r.id === ruleId ? { ...r, propertyEdits: (r.propertyEdits || []).filter(pe => pe.propertyId !== propertyId) } : r) });
  };

  const handleAddValidation = () => {
    if (!newValName.trim() || !newValExpression.trim()) return;
    const newVal: ActionValidationRule = { id: `val_${Date.now()}`, displayName: newValName, expression: newValExpression, errorMessage: newValError || t('ow.action.defaultValidationError') };
    onUpdate({ ...actionType, validationRules: [...actionType.validationRules, newVal] });
    setNewValName(''); setNewValExpression(''); setNewValError('');
  };

  const handleRemoveValidation = (valId: string) => {
    onUpdate({ ...actionType, validationRules: actionType.validationRules.filter(v => v.id !== valId) });
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-full border border-amber-300 bg-amber-50 text-amber-700 flex items-center justify-center"><Zap size={20} className="fill-amber-500" /></div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{actionType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.badgeBg} ${styles.badgeText} px-1.5 py-0.5 rounded`}>{actionType.apiName}</span>
              <span className={`text-xs ${styles.badgeBg} ${styles.muted} px-2 py-0.5 rounded-full font-mono`}>{actionType.rules.length} {t('ow.label.sideEffectsLabel')}</span>
            </div>
            <p className={`text-xs ${styles.muted} mt-0.5`}>{actionType.description || t('ow.empty.noDescription')}</p>
          </div>
        </div>
        <button onClick={() => onDelete(actionType.id)} className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"><Trash2 size={13} />{t('ow.btn.deleteAction')}</button>
      </div>

      <div className={`flex px-6 border-b ${styles.cardBorder} ${styles.cardBg}`}>
        {(['parameters', 'rules', 'validation', 'layout'] as const).map(tab => {
          const labels: Record<string, string> = { parameters: t('ow.tab.actionParameters'), rules: t('ow.tab.actionRules'), validation: t('ow.tab.actionValidation'), layout: t('ow.tab.actionLayout') };
          return (<button key={tab} onClick={() => setActiveTab(tab)} className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${activeTab === tab ? `${styles.accentBorder} ${styles.accentText}` : `border-transparent ${styles.muted} hover:${styles.cardText}`}`}>{labels[tab]}</button>);
        })}
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {activeTab === 'parameters' && (
          <ParametersTab actionType={actionType} objectTypes={objectTypes} newParamName={newParamName} setNewParamName={setNewParamName} newParamType={newParamType} setNewParamType={setNewParamType} newParamObjType={newParamObjType} setNewParamObjType={setNewParamObjType} handleAddParam={handleAddParam} handleRemoveParam={handleRemoveParam} handleParamFieldChange={handleParamFieldChange} onNavigateToObject={onNavigateToObject} />
        )}
        {activeTab === 'rules' && (
          <RulesTab actionType={actionType} objectTypes={objectTypes} handleAddRule={handleAddRule} handleRemoveRule={handleRemoveRule} handleRuleChange={handleRuleChange} handleAddPropertyEdit={handleAddPropertyEdit} handlePropertyEditValueChange={handlePropertyEditValueChange} handleRemovePropertyEdit={handleRemovePropertyEdit} />
        )}
        {activeTab === 'validation' && (
          <ValidationTab actionType={actionType} newValName={newValName} setNewValName={setNewValName} newValExpression={newValExpression} setNewValExpression={setNewValExpression} newValError={newValError} setNewValError={setNewValError} handleAddValidation={handleAddValidation} handleRemoveValidation={handleRemoveValidation} />
        )}
        {activeTab === 'layout' && (
          <LayoutTab actionType={actionType} newSectionTitle={newSectionTitle} setNewSectionTitle={setNewSectionTitle} onUpdate={onUpdate} />
        )}
      </div>
    </div>
  );
}
