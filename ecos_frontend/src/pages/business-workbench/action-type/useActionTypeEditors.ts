/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { ActionType, ActionParameter, ActionRule, ActionValidationRule, ActionParamDataType, ActionRuleType, ObjectType } from '../../../types/ontology';

interface ActionTypeEditorsParams {
  actionType: ActionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: ActionType) => void;
  newParamName: string;
  newParamType: ActionParamDataType;
  newParamObjType: string;
  setNewParamName: (value: string) => void;
  newValName: string;
  newValExpression: string;
  newValError: string;
  setNewValName: (value: string) => void;
  setNewValExpression: (value: string) => void;
  setNewValError: (value: string) => void;
}

/**
 * Parameter / rule / validation mutation handlers, extracted verbatim from
 * ActionTypeView.tsx. Contains no React hooks, so calling it from the parent
 * does not alter the parent's hook order.
 */
export function useActionTypeEditors({
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
}: ActionTypeEditorsParams) {
  // Add parameter
  const handleAddParam = () => {
    if (!newParamName.trim()) return;
    const paramId = newParamName.trim().replace(/\s+/g, '_').toLowerCase() + '_param';
    const newParam: ActionParameter = {
      id: paramId,
      displayName: newParamName,
      dataType: newParamType,
      isRequired: true,
      description: `关于参数 ${newParamName} 的用途。`,
      objectTypeId: newParamType === 'object' ? newParamObjType : undefined
    };

    onUpdate({
      ...actionType,
      parameters: [...actionType.parameters, newParam]
    });
    setNewParamName('');
  };

  // Remove parameter
  const handleRemoveParam = (paramId: string) => {
    onUpdate({
      ...actionType,
      parameters: actionType.parameters.filter(p => p.id !== paramId),
      // Also remove any rules referencing this parameter to avoid dangling references
      rules: actionType.rules.filter(r => r.targetParameterId !== paramId)
    });
  };

  // Update specific parameter fields
  const handleParamFieldChange = (paramId: string, field: keyof ActionParameter, value: any) => {
    onUpdate({
      ...actionType,
      parameters: actionType.parameters.map(p =>
        p.id === paramId ? { ...p, [field]: value } : p
      )
    });
  };

  // Add a new Rule
  const handleAddRule = (type: ActionRuleType) => {
    const ruleId = `rule_${Date.now()}`;
    const newRule: ActionRule = {
      id: ruleId,
      type,
      targetObjectTypeId: type === 'create_object' ? objectTypes[0]?.id : undefined,
      targetParameterId: type !== 'create_object' ? actionType.parameters.find(p => p.dataType === 'object')?.id : undefined,
      propertyEdits: []
    };

    onUpdate({
      ...actionType,
      rules: [...actionType.rules, newRule]
    });
  };

  // Delete a rule
  const handleRemoveRule = (ruleId: string) => {
    onUpdate({
      ...actionType,
      rules: actionType.rules.filter(r => r.id !== ruleId)
    });
  };

  // Update Rule properties
  const handleRuleChange = (ruleId: string, field: keyof ActionRule, value: any) => {
    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, [field]: value } : r
      )
    });
  };

  // Add property edit to a rule
  const handleAddPropertyEdit = (ruleId: string, propertyId: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule) return;

    const propertyEdits = [...(rule.propertyEdits || [])];
    if (propertyEdits.some(pe => pe.propertyId === propertyId)) return; // Avoid duplicate

    propertyEdits.push({
      propertyId,
      valueExpression: 'parameter.' // Default expression stub
    });

    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, propertyEdits } : r
      )
    });
  };

  // Update property edit expression
  const handlePropertyEditValueChange = (ruleId: string, propertyId: string, expr: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule) return;

    const propertyEdits = (rule.propertyEdits || []).map(pe =>
      pe.propertyId === propertyId ? { ...pe, valueExpression: expr } : pe
    );

    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, propertyEdits } : r
      )
    });
  };

  // Remove property edit from a rule
  const handleRemovePropertyEdit = (ruleId: string, propertyId: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule) return;

    const propertyEdits = (rule.propertyEdits || []).filter(pe => pe.propertyId !== propertyId);

    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, propertyEdits } : r
      )
    });
  };

  // Add validation rule
  const handleAddValidation = () => {
    if (!newValName.trim() || !newValExpression.trim()) return;
    const newVal: ActionValidationRule = {
      id: `val_${Date.now()}`,
      displayName: newValName,
      expression: newValExpression,
      errorMessage: newValError || '验证未通过，请检查您的输入参数。'
    };

    onUpdate({
      ...actionType,
      validationRules: [...actionType.validationRules, newVal]
    });

    setNewValName('');
    setNewValExpression('');
    setNewValError('');
  };

  // Remove validation rule
  const handleRemoveValidation = (valId: string) => {
    onUpdate({
      ...actionType,
      validationRules: actionType.validationRules.filter(v => v.id !== valId)
    });
  };

  return {
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
  };
}
