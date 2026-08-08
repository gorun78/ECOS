/**
 * JoinConditionsEditor — JOIN condition form section
 * @license Apache-2.0
 */

import React from 'react';
import { Plus, X } from 'lucide-react';
import type { JoinCondition } from './types';

interface JoinConditionsEditorProps {
  joinType: string;
  conditions: JoinCondition[];
  onJoinTypeChange: (value: string) => void;
  onAdd: () => void;
  onUpdate: (condId: string, field: keyof JoinCondition, value: string) => void;
  onRemove: (condId: string) => void;
}

const JoinConditionsEditor: React.FC<JoinConditionsEditorProps> = ({
  joinType,
  conditions,
  onJoinTypeChange,
  onAdd,
  onUpdate,
  onRemove,
}) => {
  return (
    <div className="px-3 pb-3 space-y-2">
      <div>
        <label className="text-[11px] text-slate-500 block mb-1">JOIN 类型</label>
        <select
          value={joinType}
          onChange={(e) => onJoinTypeChange(e.target.value)}
          className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-purple-400 focus:ring-1 focus:ring-purple-200 outline-none"
        >
          <option value="INNER">INNER JOIN</option>
          <option value="LEFT">LEFT JOIN</option>
          <option value="RIGHT">RIGHT JOIN</option>
          <option value="FULL">FULL OUTER JOIN</option>
          <option value="CROSS">CROSS JOIN</option>
        </select>
      </div>
      {conditions.map((cond) => (
        <div key={cond.id} className="p-2 border border-slate-200 rounded bg-slate-50 space-y-1.5">
          <div className="flex gap-1">
            <input
              type="text" value={cond.leftColumn}
              onChange={(e) => onUpdate(cond.id, 'leftColumn', e.target.value)}
              placeholder="左表列"
              className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-purple-400"
            />
            <select
              value={cond.operator}
              onChange={(e) => onUpdate(cond.id, 'operator', e.target.value)}
              className="w-14 px-1 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-purple-400"
            >
              <option value="=">=</option>
              <option value="!=">!=</option>
              <option value=">">&gt;</option>
              <option value="<">&lt;</option>
              <option value=">=">&gt;=</option>
              <option value="<=">&lt;=</option>
            </select>
            <input
              type="text" value={cond.rightColumn}
              onChange={(e) => onUpdate(cond.id, 'rightColumn', e.target.value)}
              placeholder="右表列"
              className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-purple-400"
            />
            <button onClick={() => onRemove(cond.id)} className="p-0.5 text-red-400 hover:text-red-600 transition-colors">
              <X size={14} />
            </button>
          </div>
        </div>
      ))}
      <button
        onClick={onAdd}
        className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-[11px] text-purple-600 border border-dashed border-purple-300 rounded hover:bg-purple-50 transition-colors"
      >
        <Plus size={12} /> 添加 JOIN 条件
      </button>
    </div>
  );
};

export default JoinConditionsEditor;
