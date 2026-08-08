/**
 * AggregateConfigEditor — aggregate config form section
 * @license Apache-2.0
 */

import React from 'react';
import { Plus, X } from 'lucide-react';

interface AggFunction { column: string; function: string; alias: string }

interface AggregateConfigEditorProps {
  groupByCols: string[];
  aggFunctions: AggFunction[];
  onAddGroupBy: () => void;
  onUpdateGroupBy: (index: number, value: string) => void;
  onRemoveGroupBy: (index: number) => void;
  onAddAgg: () => void;
  onUpdateAgg: (index: number, field: keyof AggFunction, value: string) => void;
  onRemoveAgg: (index: number) => void;
}

const AggregateConfigEditor: React.FC<AggregateConfigEditorProps> = ({
  groupByCols,
  aggFunctions,
  onAddGroupBy,
  onUpdateGroupBy,
  onRemoveGroupBy,
  onAddAgg,
  onUpdateAgg,
  onRemoveAgg,
}) => {
  return (
    <div className="px-3 pb-3 space-y-3">
      {/* Group By */}
      <div>
        <label className="text-[11px] text-slate-500 block mb-1">GROUP BY 列</label>
        {groupByCols.map((col, idx) => (
          <div key={idx} className="flex gap-1 mb-1">
            <input type="text" value={col}
              onChange={(e) => onUpdateGroupBy(idx, e.target.value)}
              placeholder="列名"
              className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
            />
            <button onClick={() => onRemoveGroupBy(idx)} className="p-0.5 text-red-400 hover:text-red-600 transition-colors">
              <X size={14} />
            </button>
          </div>
        ))}
        <button
          onClick={onAddGroupBy}
          className="w-full flex items-center justify-center gap-1 px-2 py-1 text-[11px] text-orange-600 border border-dashed border-orange-300 rounded hover:bg-orange-50 transition-colors mt-1"
        >
          <Plus size={12} /> 添加分组列
        </button>
      </div>

      {/* Aggregate Functions */}
      <div>
        <label className="text-[11px] text-slate-500 block mb-1">聚合函数</label>
        {aggFunctions.map((func, idx) => (
          <div key={idx} className="p-2 border border-slate-200 rounded bg-slate-50 space-y-1.5 mb-1">
            <div className="flex gap-1">
              <select value={func.function}
                onChange={(e) => onUpdateAgg(idx, 'function', e.target.value)}
                className="flex-1 px-1 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
              >
                <option value="COUNT">COUNT</option>
                <option value="SUM">SUM</option>
                <option value="AVG">AVG</option>
                <option value="MIN">MIN</option>
                <option value="MAX">MAX</option>
                <option value="COUNT_DISTINCT">COUNT DISTINCT</option>
              </select>
              <input type="text" value={func.column}
                onChange={(e) => onUpdateAgg(idx, 'column', e.target.value)}
                placeholder="列名"
                className="w-20 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
              />
            </div>
            <div className="flex gap-1 items-center">
              <span className="text-[10px] text-slate-400">别名:</span>
              <input type="text" value={func.alias}
                onChange={(e) => onUpdateAgg(idx, 'alias', e.target.value)}
                placeholder="as"
                className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
              />
              <button onClick={() => onRemoveAgg(idx)} className="p-0.5 text-red-400 hover:text-red-600 transition-colors">
                <X size={14} />
              </button>
            </div>
          </div>
        ))}
        <button
          onClick={onAddAgg}
          className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-[11px] text-orange-600 border border-dashed border-orange-300 rounded hover:bg-orange-50 transition-colors"
        >
          <Plus size={12} /> 添加聚合函数
        </button>
      </div>
    </div>
  );
};

export default AggregateConfigEditor;
