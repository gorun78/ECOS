/**
 * TestTab — 沙盒测试运行 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Play, Terminal, CheckCircle } from 'lucide-react';
import type { FunctionType, ObjectType } from '../../../types/ontology';

interface TestTabProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  testInputs: Record<string, any>;
  setTestInputs: React.Dispatch<React.SetStateAction<Record<string, any>>>;
  isTesting: boolean;
  handleRunTest: () => void;
  testLogs: string[];
  testResult: any;
}

export default function TestTab({
  func, objectTypes, testInputs, setTestInputs,
  isTesting, handleRunTest, testLogs, testResult,
}: TestTabProps) {
  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="w-1/3 border-r border-gray-200 p-5 bg-slate-50/50 flex flex-col justify-between overflow-y-auto select-none">
        <div className="space-y-4 text-xs">
          <div>
            <h4 className="text-xs font-semibold text-slate-800">沙盒测试参数输入</h4>
            <p className="text-[10px] text-slate-500 mt-0.5">请为该函数的输入参数填入测试值以模拟执行。</p>
          </div>
          <div className="space-y-3">
            {func.parameters.map(p => {
              const value = testInputs[p.name] !== undefined ? testInputs[p.name] : '';
              const setVal = (newV: any) => { setTestInputs(prev => ({ ...prev, [p.name]: newV })); };
              return (
                <div key={p.name} className="space-y-1 bg-white p-3 rounded-lg border border-gray-200">
                  <div className="flex justify-between items-center text-[10px]">
                    <span className="font-semibold text-slate-700 font-mono">{p.name}</span>
                    <span className="font-mono text-slate-400">{p.dataType}{p.isRequired && '*'}</span>
                  </div>
                  <p className="text-[10px] text-slate-400 mb-1">{p.description}</p>
                  {p.dataType === 'boolean' ? (
                    <div className="flex items-center gap-3 mt-1.5">
                      <button onClick={() => setVal(true)} className={`px-3 py-1 text-[11px] rounded transition-all font-mono ${value === true ? 'bg-blue-600 text-white font-semibold' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}>true</button>
                      <button onClick={() => setVal(false)} className={`px-3 py-1 text-[11px] rounded transition-all font-mono ${value === false ? 'bg-blue-600 text-white font-semibold' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}>false</button>
                    </div>
                  ) : (p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                    <select value={value} onChange={e => setVal(e.target.value)}
                      className="w-full px-2 py-1 text-[11px] border border-gray-300 rounded bg-white mt-1">
                      <option value="">-- 选择模拟对象实体 --</option>
                      {p.dataType === 'ObjectTypeSet' ? <option value="mock_set_all_records">所有对象实体集 (ObjectSet 全量)</option> : null}
                      {p.objectTypeId === 'flight' && (<><option value="UA102">Flight: UA102 (ON_TIME)</option><option value="DL440">Flight: DL440 (DELAYED)</option><option value="AA880">Flight: AA880 (ON_TIME)</option></>)}
                      {p.objectTypeId === 'aircraft' && (<><option value="N101UA">Aircraft: N101UA (Boeing 737-800)</option><option value="N204DL">Aircraft: N204DL (Airbus A321neo)</option><option value="N309AA">Aircraft: N309AA (MAINTENANCE)</option></>)}
                      {p.objectTypeId === 'pilot' && (<><option value="P01">Pilot: P01 (张建国)</option><option value="P02">Pilot: P02 (李明华)</option></>)}
                      <option value="custom_mock_1">自定义测试对象 1</option>
                    </select>
                  ) : p.dataType === 'integer' || p.dataType === 'decimal' ? (
                    <input type="number" value={value} onChange={e => setVal(parseFloat(e.target.value) || 0)}
                      className="w-full px-2.5 py-1 text-[11px] border border-gray-300 rounded focus:outline-hidden" />
                  ) : p.dataType === 'date' || p.dataType === 'timestamp' ? (
                    <input type="datetime-local" value={value} onChange={e => setVal(e.target.value)}
                      className="w-full px-2.5 py-1 text-[11px] border border-gray-300 rounded focus:outline-hidden font-mono" />
                  ) : (
                    <input type="text" value={value} onChange={e => setVal(e.target.value)}
                      className="w-full px-2.5 py-1 text-[11px] border border-gray-300 rounded focus:outline-hidden" placeholder="请输入文本" />
                  )}
                </div>
              );
            })}
          </div>
        </div>
        <button onClick={handleRunTest} disabled={isTesting}
          className="w-full py-2 bg-slate-900 hover:bg-slate-800 disabled:bg-slate-500 text-white rounded-lg flex items-center justify-center gap-2 font-medium transition-all shadow-xs mt-4 text-xs">
          {isTesting ? (<><span className="h-3 w-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span><span>正在沙盒中执行计算...</span></>) : (<><Play size={14} className="fill-white" /><span>运行测试 (Run Function)</span></>)}
        </button>
      </div>

      <div className="flex-1 bg-slate-950 p-5 flex flex-col text-xs font-mono overflow-y-auto text-slate-300 select-none">
        <h4 className="text-[10px] text-slate-500 tracking-wider uppercase font-semibold mb-3 border-b border-slate-900 pb-2 flex justify-between items-center">
          <span>函数模拟输出终端 (ECOS Console)</span>
          {testResult !== null && (<span className="text-emerald-500 font-semibold flex items-center gap-1 bg-emerald-500/10 px-1.5 py-0.5 rounded"><CheckCircle size={11} /> SUCCESS</span>)}
        </h4>
        {testLogs.length === 0 ? (
          <div className="flex-1 flex flex-col justify-center items-center text-slate-600 italic">
            <Terminal size={24} className="mb-2 text-slate-700" />
            <div>配置完左侧测试入参后，点击下方 "运行测试" 按钮。</div>
            <div>系统将在编译并在虚拟沙盒中运行您的 TS 代码。</div>
          </div>
        ) : (
          <div className="flex-1 space-y-1.5 select-text">
            {testLogs.map((log, i) => (
              <div key={i} className={
                log.includes('SUCCESS') || log.includes('捕获') ? 'text-emerald-400' :
                log.includes('注入') ? 'text-blue-400' :
                log.includes('⚙️') || log.includes('✅') ? 'text-slate-400' : 'text-slate-300'
              }>{log}</div>
            ))}
            {testResult !== null && (
              <div className="mt-4 p-4 rounded bg-slate-900/60 border border-slate-800 text-emerald-300">
                <div className="text-[10px] text-slate-500 mb-1 font-sans uppercase tracking-wider font-semibold">返回值 (Return Value):</div>
                <pre className="text-xs leading-relaxed">{typeof testResult === 'object' ? JSON.stringify(testResult, null, 4) : String(testResult)}</pre>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
