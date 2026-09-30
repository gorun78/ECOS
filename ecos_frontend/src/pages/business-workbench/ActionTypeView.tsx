/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ActionType, ActionParameter, ActionRule, ActionValidationRule, ObjectType, ActionParamDataType, ActionRuleType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import ParametersTab from './action-type/ParametersTab';
import RulesTab from './action-type/RulesTab';
import ValidationTab from './action-type/ValidationTab';
import LayoutTab from './action-type/LayoutTab';
import { useActionTypeEditors } from './action-type/useActionTypeEditors';

interface ActionTypeViewProps {
  actionType: ActionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: ActionType) => void;
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function ActionTypeView({
  actionType,
  objectTypes,
  onUpdate,
  onDelete,
  onNavigateToObject
}: ActionTypeViewProps) {
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState<'parameters' | 'rules' | 'validation' | 'layout'>('parameters');
  const [newParamName, setNewParamName] = useState('');
  const [newParamType, setNewParamType] = useState<ActionParamDataType>('string');
  const [newParamObjType, setNewParamObjType] = useState(objectTypes[0]?.id || '');

  // Layout-specific state
  const [newSectionTitle, setNewSectionTitle] = useState('');

  const [newValName, setNewValName] = useState('');
  const [newValExpression, setNewValExpression] = useState('');
  const [newValError, setNewValError] = useState('');

  const handleFieldChange = (key: keyof ActionType, value: any) => {
    onUpdate({
      ...actionType,
      [key]: value
    });
  };

  // Extracted mutation handlers (no React hooks inside; parent hook order unchanged)
  const {
    handleAddParam,
    handleRemoveParam,
    handleParamFieldChange,
    handleAddRule,
    handleRemoveRule,
    handleRuleChange,
    handleAddPropertyEdit,
    handlePropertyEditValueChange,
    handleRemovePropertyEdit,
    handleAddValidation,
    handleRemoveValidation
  } = useActionTypeEditors({
    actionType,
    objectTypes,
    onUpdate,
    newParamName,
    newParamType,
    newParamObjType,
    setNewParamName,
    newValName,
    newValExpression,
    newValError,
    setNewValName,
    setNewValExpression,
    setNewValError
  });

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      {/* Detail Header */}
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-full border border-amber-300 bg-amber-50 text-amber-700 flex items-center justify-center">
            <LucideIcon name="Zap" size={20} className="fill-amber-500" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{actionType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.appBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>
                {actionType.apiName}
              </span>
              <span className={`text-xs ${styles.appBg} ${styles.cardTextMuted} px-2 py-0.5 rounded-full font-mono`}>
                {actionType.rules.length} 副作用规则
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{actionType.description || '无详细描述'}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(actionType.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
        >
          <LucideIcon name="Trash2" size={13} />
          删除操作
        </button>
      </div>

      {/* Tab bar */}
      <div className={`flex px-6 border-b ${styles.appBorder} ${styles.cardBg}`}>
        {(['parameters', 'rules', 'validation', 'layout'] as const).map(tab => {
          const labels = {
            parameters: '1. 参数定义 (Parameters)',
            rules: '2. 副作用逻辑 (Rules / Effects)',
            validation: '3. 提交前验证 (Validation)',
            layout: '4. 表单与布局 (Form & Layout)'
          };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : `border-transparent ${styles.cardTextMuted} hover:opacity-100 opacity-80`
              }`}
            >
              {labels[tab]}
            </button>
          );
        })}
      </div>

      {/* Tab panel */}
      <div className="flex-1 overflow-y-auto p-6">

        {/* PARAMETERS TAB */}
        {activeTab === 'parameters' && (
          <ParametersTab
            actionType={actionType}
            objectTypes={objectTypes}
            newParamName={newParamName}
            setNewParamName={setNewParamName}
            newParamType={newParamType}
            setNewParamType={setNewParamType}
            newParamObjType={newParamObjType}
            setNewParamObjType={setNewParamObjType}
            handleAddParam={handleAddParam}
            handleRemoveParam={handleRemoveParam}
            handleParamFieldChange={handleParamFieldChange}
            onNavigateToObject={onNavigateToObject}
          />
        )}

        {/* RULES / EFFECTS TAB */}
        {activeTab === 'rules' && (
          <RulesTab
            actionType={actionType}
            objectTypes={objectTypes}
            handleAddRule={handleAddRule}
            handleRemoveRule={handleRemoveRule}
            handleRuleChange={handleRuleChange}
            handleAddPropertyEdit={handleAddPropertyEdit}
            handlePropertyEditValueChange={handlePropertyEditValueChange}
            handleRemovePropertyEdit={handleRemovePropertyEdit}
          />
        )}

        {/* VALIDATIONS TAB */}
        {activeTab === 'validation' && (
          <ValidationTab
            actionType={actionType}
            newValName={newValName}
            setNewValName={setNewValName}
            newValExpression={newValExpression}
            setNewValExpression={setNewValExpression}
            newValError={newValError}
            setNewValError={setNewValError}
            handleAddValidation={handleAddValidation}
            handleRemoveValidation={handleRemoveValidation}
          />
        )}

        {/* LAYOUT / FORM TAB */}
        {activeTab === 'layout' && (
          <LayoutTab
            actionType={actionType}
            onUpdate={onUpdate}
            newSectionTitle={newSectionTitle}
            setNewSectionTitle={setNewSectionTitle}
          />
        )}
      </div>
    </div>
  );
}
