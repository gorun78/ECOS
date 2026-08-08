/**
 * TransformRulesEditor — transform rule form section
 * @license Apache-2.0
 */

import React from 'react';
import { Plus, X } from 'lucide-react';
import type { TransformRule } from './types';
import ExpressionEditor from './ExpressionEditor';

interface TransformRulesEditorProps {
  rules: TransformRule[];
  onAdd: () => void;
  onUpdate: (ruleId: string, field: keyof TransformRule, value: string) => void;
  onRemove: (ruleId: string) => void;
  onOperatorButtonClick: (ruleId: string) => void;
}

const TransformRulesEditor: React.FC<TransformRulesEditorProps> = ({
  rules,
  onAdd,
  onUpdate,
  onRemove,
  onOperatorButtonClick,
}) => {
  return (
    <div className="px-3 pb-3 space-y-2">
      {rules.map((rule) => (
        <div key={rule.id} className="p-2 border border-slate-200 rounded bg-slate-50 space-y-1.5">
          <div className="flex flex-col gap-1">
            <ExpressionEditor
              value={
                rule.function && rule.params
                  ? `${rule.function}(${rule.params})`
                  : rule.function || ''
              }
              onChange={(expr) => {
                const match = expr.match(/^(\w+)\s*\((.*)\)$/);
                const fnName = match?.[1] || expr;
                const params = match?.[2] || '';
                onUpdate(rule.id, 'function', fnName);
                onUpdate(rule.id, 'params', params);
              }}
              placeholder="输入表达式，如 upper(name)..."
              className="flex-1"
              showOperatorButton
              onOperatorButtonClick={() => onOperatorButtonClick(rule.id)}
            />
            <div className="flex gap-1 items-center text-[10px]">
              <span className="text-slate-400">列名:</span>
              <input
                type="text"
                value={rule.column}
                onChange={(e) => onUpdate(rule.id, 'column', e.target.value)}
                placeholder="列名"
                className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-emerald-400"
              />
              <button onClick={() => onRemove(rule.id)} className="p-0.5 text-red-400 hover:text-red-600 transition-colors">
                <X size={14} />
              </button>
            </div>
          </div>
        </div>
      ))}
      <button
        onClick={onAdd}
        className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-[11px] text-emerald-600 border border-dashed border-emerald-300 rounded hover:bg-emerald-50 transition-colors"
      >
        <Plus size={12} /> 添加转换规则
      </button>
    </div>
  );
};

export default TransformRulesEditor;
