/**
 * CodeTab — TypeScript 代码编辑 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Calculator, Info, Shield, Sparkles, TrendingUp } from 'lucide-react';
import type { FunctionType } from '../../../types/ontology';

interface CodeTabProps {
  func: FunctionType;
  handleFieldChange: (key: keyof FunctionType, value: any) => void;
  loadTemplate: (type: 'validation' | 'default' | 'computed' | 'aggregation') => void;
}

export default function CodeTab({ func, handleFieldChange, loadTemplate }: CodeTabProps) {
  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="w-56 border-r border-gray-200 bg-slate-50/50 p-4 flex flex-col gap-4 overflow-y-auto select-none">
        <div>
          <h4 className="text-xs font-semibold text-slate-700">ECOS 函数模板</h4>
          <p className="text-[10px] text-slate-500 mt-0.5">选择契合您业务目标的TS模板一键生成标准代码结构。</p>
        </div>
        <div className="space-y-2">
          <button onClick={() => loadTemplate('validation')}
            className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2">
            <Shield size={14} className="text-emerald-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">拦截校验模板</div><div className="text-[10px] font-normal text-slate-400 mt-0.5">限制Action提交</div></div>
          </button>
          <button onClick={() => loadTemplate('default')}
            className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2">
            <Sparkles size={14} className="text-amber-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">动态入参默认值</div><div className="text-[10px] font-normal text-slate-400 mt-0.5">默认值公式计算</div></div>
          </button>
          <button onClick={() => loadTemplate('computed')}
            className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2">
            <Calculator size={14} className="text-blue-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">派生计算属性</div><div className="text-[10px] font-normal text-slate-400 mt-0.5">实体派生衍生指标</div></div>
          </button>
          <button onClick={() => loadTemplate('aggregation')}
            className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2">
            <TrendingUp size={14} className="text-indigo-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">对象集统计聚合</div><div className="text-[10px] font-normal text-slate-400 mt-0.5">多实体合并聚合计算</div></div>
          </button>
        </div>
        <div className="mt-auto bg-blue-50 border border-blue-100 rounded-lg p-3 text-[11px] text-blue-700 leading-relaxed">
          <div className="font-semibold flex items-center gap-1 mb-1"><Info size={12} /><span>TS代码要求:</span></div>
          必须通过 <code>@Function()</code> 装饰器公开核心函数，以使 Workshop 应用和操作 (Actions) 能够发现并进行远程绑定。
        </div>
      </div>

      <div className="flex-1 flex flex-col bg-slate-900 overflow-hidden relative">
        <div className="px-4 py-2 border-b border-slate-800 flex justify-between items-center text-[10px] text-slate-400 select-none font-mono">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>TypeScript 1.84 - ECOS API Sync: ACTIVE</span>
          </div>
          <div className="flex items-center gap-3"><span>UTF-8</span><span>Tab Size: 4</span></div>
        </div>
        <div className="flex-1 flex font-mono text-xs overflow-hidden leading-relaxed">
          <div className="w-12 bg-slate-950 text-slate-600 text-right pr-3 select-none pt-4 flex flex-col">
            {Array.from({ length: 45 }).map((_, i) => (<div key={i}>{i + 1}</div>))}
          </div>
          <textarea value={func.code} onChange={e => handleFieldChange('code', e.target.value)}
            className="flex-1 bg-slate-900 text-slate-150 p-4 border-0 focus:outline-hidden font-mono text-xs resize-none h-full overflow-y-auto leading-relaxed outline-hidden"
            spellCheck="false" />
        </div>
      </div>
    </div>
  );
}
