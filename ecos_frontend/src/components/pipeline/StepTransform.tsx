/**
 * StepTransform — 步骤 2：算子表达式与级联物理关联（拖拽建模 + 实时预览）
 *
 * 从 `components/pipeline/InteractiveStepGuide.tsx` 机械抽取（H6-T4 组件行数治理）：
 * useTransformStep 承载算子应用/拖拽 state，StepTransform 为原 JSX 片段；
 * 算子初值、className（含源码原样的非模板字符串 bug 行）、预览表列与文案逐字保留。
 *
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { Code, Database, GitBranch, Plus, RefreshCw, Sliders, AlertTriangle, X } from 'lucide-react';
import { useTheme } from '../ThemeContext';
import {
  IngressRow,
  Operator,
  STATIC_OPERATORS,
  applyTransformOperators,
} from './stepGuideData';

type ToastFn = (type: 'success' | 'error' | 'info', message: string) => void;

export function useTransformStep(toast: ToastFn) {
  // --- STEP 2: TRANSFORM DRAG-AND-DROP STATE ---
  const [appliedOperators, setAppliedOperators] = useState<Operator[]>([
    { id: 'op-filter', name: 'Row Filter (行过滤算子)', desc: '筛选 delay_minutes 大于特定数值的异常飞行记录。', icon: 'Sliders', color: 'bg-blue-100 text-blue-700 border-blue-200', type: 'filter' },
    { id: 'op-regex', name: 'Regex Clean (正则清洗)', desc: '自动剔除航司 carrier 名称首尾的空白字符并转大写。', icon: 'Code', color: 'bg-indigo-100 text-indigo-700 border-indigo-200', type: 'regex' },
  ]);
  const [filterMinutes, setFilterMinutes] = useState<number>(10);
  const [nullFillerValue, setNullFillerValue] = useState<string>('未分配飞行员');
  const [draggingOpId, setDraggingOpId] = useState<string | null>(null);

  const addOperator = (op: Operator) => {
    if (appliedOperators.some((o) => o.id === op.id)) {
      toast('info', `${op.name} 已经存在于当前变换流中`);
      return;
    }
    setAppliedOperators((prev) => [...prev, op]);
    toast('success', `已添加算子: ${op.name}`);
  };

  const removeOperator = (id: string) => {
    setAppliedOperators((prev) => prev.filter((o) => o.id !== id));
    toast('info', '算子已移除');
  };

  const handleDragStart = (id: string) => {
    setDraggingOpId(id);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (!draggingOpId) return;
    const matched = STATIC_OPERATORS.find((o) => o.id === draggingOpId);
    if (matched) {
      addOperator(matched);
    }
    setDraggingOpId(null);
  };

  // 实时响应式数据预览
  const getTransformedData = () => applyTransformOperators(appliedOperators, filterMinutes, nullFillerValue);

  return {
    appliedOperators,
    filterMinutes,
    setFilterMinutes,
    nullFillerValue,
    setNullFillerValue,
    draggingOpId,
    addOperator,
    removeOperator,
    handleDragStart,
    handleDrop,
    getTransformedData,
  };
}

interface StepTransformProps {
  appliedOperators: Operator[];
  filterMinutes: number;
  setFilterMinutes: (value: number) => void;
  nullFillerValue: string;
  setNullFillerValue: (value: string) => void;
  addOperator: (op: Operator) => void;
  removeOperator: (id: string) => void;
  handleDragStart: (id: string) => void;
  handleDrop: (e: React.DragEvent) => void;
  getTransformedData: () => IngressRow[];
}

export default function StepTransform({
  appliedOperators,
  filterMinutes,
  setFilterMinutes,
  nullFillerValue,
  setNullFillerValue,
  addOperator,
  removeOperator,
  handleDragStart,
  handleDrop,
  getTransformedData,
}: StepTransformProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-4 font-sans select-none">
      <div className="grid grid-cols-12 gap-4">
        {/* Operators Left (cols 4) */}
        <div className={`col-span-4 border ${styles.cardBorder} rounded-xl p-3 ${styles.appBg} space-y-2`}>
          <span className={`text-[9px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono`}>拖拽或点击添加清洗算子 (Operators)</span>

          <div className="space-y-2 max-h-72 overflow-y-auto">
            {STATIC_OPERATORS.map((op) => {
              const isApplied = appliedOperators.some((o) => o.id === op.id);
              return (
                <div
                  key={op.id}
                  draggable
                  onDragStart={() => handleDragStart(op.id)}
                  onClick={() => addOperator(op)}
                  className={`p-2 rounded-lg border text-[10px] flex items-start gap-2 cursor-grab active:cursor-grabbing transition-all ${op.color} ${
                    isApplied ? 'opacity-55' : 'hover:scale-102 hover:shadow-2xs'
                  }`}
                >
                  <span className="p-0.5 ${styles.cardBg}/80 rounded mt-0.5">
                    {op.type === 'filter' && <Sliders size={11} />}
                    {op.type === 'regex' && <Code size={11} />}
                    {op.type === 'nulls' && <AlertTriangle size={11} />}
                    {op.type === 'join' && <GitBranch size={11} />}
                    {op.type === 'cast' && <RefreshCw size={11} />}
                  </span>
                  <div className="flex-1">
                    <div className="font-extrabold flex justify-between items-center">
                      <span>{op.name.split(' ')[0]}</span>
                      <Plus size={10} className={`${styles.cardTextMuted} cursor-pointer hover:scale-120`} />
                    </div>
                    <p className={`text-[8px] ${styles.cardTextMuted} mt-0.5 font-sans leading-tight`}>{op.desc}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Drop canvas right (cols 8) */}
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={handleDrop}
          className={`col-span-8 border ${styles.cardBorder} rounded-xl p-3 ${styles.cardBg} flex flex-col justify-between min-h-[220px]`}
        >
          <div>
            <div className={`flex justify-between items-center mb-2 pb-1.5 border-b ${styles.appBorder}`}>
              <span className={`text-[9px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono`}>
                活动加工拓扑管道 (Applied Transformations)
              </span>
              <span className={`text-[8px] ${styles.cardTextMuted}`}>支持 HTML5 拖放算子入仓，或直接在算子内微调属性</span>
            </div>

            {appliedOperators.length === 0 ? (
              <div className={`border-2 border-dashed ${styles.cardBorder} rounded-lg p-6 flex flex-col items-center justify-center ${styles.cardTextMuted} gap-1.5 text-xs text-center min-h-[140px]`}>
                <Sliders size={20} className={`${styles.terminalText} animate-bounce`} />
                <span>请从左侧拖拽或点击算子加入此处物理建模管道</span>
              </div>
            ) : (
              <div className={`flex flex-wrap gap-2 max-h-[160px] overflow-y-auto p-1 ${styles.appBg}/50 rounded-lg`}>
                {appliedOperators.map((op) => (
                  <div key={op.id} className={`p-2 ${styles.cardBg} rounded-lg border ${styles.cardBorder} flex flex-col gap-1.5 shadow-2xs text-[10px] w-[180px] shrink-0 animate-in zoom-in-95`}>
                    <div className={`flex justify-between items-center border-b ${styles.appBorder} pb-1`}>
                      <span className={`font-extrabold ${styles.cardText} truncate pr-2`}>{op.name.split(' ')[0]}</span>
                      <button onClick={() => removeOperator(op.id)} className={`${styles.cardTextMuted} hover:${styles.cardText} cursor-pointer`}>
                        <X size={10} />
                      </button>
                    </div>

                    {op.type === 'filter' && (
                      <div className="space-y-1">
                        <div className={`flex justify-between text-[8px] ${styles.cardTextMuted}`}>
                          <span>过滤延误 &gt;= </span>
                          <span className="font-bold text-blue-600">{filterMinutes} 分钟</span>
                        </div>
                        <input
                          type="range"
                          min={0}
                          max={40}
                          value={filterMinutes}
                          onChange={(e) => setFilterMinutes(parseInt(e.target.value))}
                          className={`w-full h-1 ${styles.inputBg} rounded-lg appearance-none cursor-pointer accent-blue-600`}
                        />
                      </div>
                    )}

                    {op.type === 'nulls' && (
                      <div className="space-y-1">
                        <span className={`text-[8px] ${styles.cardTextMuted} block`}>空值默认填充值:</span>
                        <input
                          type="text"
                          value={nullFillerValue}
                          onChange={(e) => setNullFillerValue(e.target.value)}
                          className={`w-full text-[9px] px-1.5 py-0.5 border ${styles.cardBorder} rounded focus:outline-none focus:border-indigo-500 ${styles.inputBg}`}
                        />
                      </div>
                    )}

                    {op.type === 'regex' && (
                      <span className={`text-[8px] ${styles.cardTextMuted} leading-normal bg-indigo-50/50 p-1 rounded font-mono`}>
                        regex: s.strip().upper()
                      </span>
                    )}

                    {op.type === 'join' && (
                      <div className="flex items-center gap-1">
                        <span className={`text-[8px] ${styles.cardTextMuted}`}>关联维度表:</span>
                        <span className="text-[8px] bg-purple-100 text-purple-700 font-bold px-1 rounded font-mono">pilots_raw</span>
                      </div>
                    )}

                    {op.type === 'cast' && (
                      <span className={`text-[8px] ${styles.cardTextMuted} font-mono`}>delay_minutes: String ➔ Double</span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className={`mt-2 ${styles.terminalBg} text-[8px] font-mono leading-none ${styles.terminalText} p-1.5 rounded flex justify-between`}>
            <span>Optimizer Pipeline Expression Target:</span>
            <span className="text-indigo-400">DorisCatalystPushdownBuilder()</span>
          </div>
        </div>
      </div>

      {/* Reactive Output Preview Table */}
      <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.cardBg}`}>
        <div className={`${styles.appBg} px-3 py-1.5 border-b ${styles.cardBorder} flex justify-between items-center`}>
          <span className={`text-[10px] font-extrabold ${styles.cardText} flex items-center gap-1 font-sans`}>
            <Database size={11} className={styles.cardTextMuted} />
            <span>实时过滤计算动态数据预览 (Reactive In-Memory/Doris Preview)</span>
          </span>
          <span className={`text-[8px] ${styles.inputBg} ${styles.cardText} px-1.5 py-0.2 rounded font-mono`}>
            Rows count: {getTransformedData().length}
          </span>
        </div>

        <div className="overflow-x-auto max-h-36">
          <table className="w-full text-left text-[9px] font-mono border-collapse select-text">
            <thead>
              <tr className={`${styles.appBorder} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-bold`}>
                <th className={`p-2 border-r ${styles.cardBorder}`}>flight_id</th>
                <th className={`p-2 border-r ${styles.cardBorder}`}>carrier</th>
                <th className={`p-2 border-r ${styles.cardBorder}`}>origin</th>
                <th className={`p-2 border-r ${styles.cardBorder}`}>dest</th>
                <th className={`p-2 border-r ${styles.cardBorder}`}>delay_minutes</th>
                <th className={`p-2 border-r ${styles.cardBorder}`}>pilot_id</th>
                {appliedOperators.some((op) => op.type === 'join' || op.type === 'nulls') && (
                  <th className="p-2 text-indigo-700 font-extrabold">pilot_name (关联字段)</th>
                )}
              </tr>
            </thead>
            <tbody>
              {getTransformedData().map((row, idx) => (
                <tr key={idx} className={`border-b ${styles.appBorder} last:border-0 hover:${styles.appBg}/50`}>
                  <td className={`p-2 border-r ${styles.appBorder}`}>{row.flight_id}</td>
                  <td className={`p-2 border-r ${styles.appBorder}`}>{row.carrier}</td>
                  <td className={`p-2 border-r ${styles.appBorder} font-bold`}>{row.origin}</td>
                  <td className={`p-2 border-r ${styles.appBorder} font-bold`}>{row.dest}</td>
                  <td className={`p-2 border-r ${styles.appBorder}`}>{row.delay_minutes}</td>
                  <td className={`p-2 border-r ${styles.appBorder}`}>{row.pilot_id}</td>
                  {appliedOperators.some((op) => op.type === 'join' || op.type === 'nulls') && (
                    <td className="p-2 text-indigo-600 font-bold bg-indigo-50/20">{row.pilot_name || nullFillerValue}</td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
