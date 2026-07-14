/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { FunctionType, FunctionParameter, ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';

interface FunctionTypeViewProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: FunctionType) => void;
  onDelete: (id: string) => void;
}

export default function FunctionTypeView({
  func,
  objectTypes,
  onUpdate,
  onDelete
}: FunctionTypeViewProps) {
  const [activeTab, setActiveTab] = useState<'signature' | 'code' | 'test'>('code');
  const [newParamName, setNewParamName] = useState('');
  const [newParamType, setNewParamType] = useState<string>('string');
  const [newParamObjType, setNewParamObjType] = useState(objectTypes[0]?.id || '');
  
  // Test run state
  const [testInputs, setTestInputs] = useState<Record<string, any>>({});
  const [isTesting, setIsTesting] = useState(false);
  const [testLogs, setTestLogs] = useState<string[]>([]);
  const [testResult, setTestResult] = useState<any>(null);

  // Sync test inputs when parameters change
  useEffect(() => {
    const inputs: Record<string, any> = {};
    func.parameters.forEach(p => {
      if (p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') {
        const objType = objectTypes.find(ot => ot.id === p.objectTypeId) || objectTypes[0];
        inputs[p.name] = objType?.id || '';
      } else if (p.dataType === 'integer' || p.dataType === 'decimal') {
        inputs[p.name] = 0;
      } else if (p.dataType === 'boolean') {
        inputs[p.name] = true;
      } else {
        inputs[p.name] = '';
      }
    });
    setTestInputs(inputs);
  }, [func.parameters, objectTypes]);

  const handleFieldChange = (key: keyof FunctionType, value: any) => {
    onUpdate({
      ...func,
      [key]: value
    });
  };

  // Add parameter
  const handleAddParam = () => {
    if (!newParamName.trim()) return;
    const name = newParamName.trim().replace(/\s+/g, '').replace(/[^a-zA-Z0-9]/g, '');
    const newParam: FunctionParameter = {
      name,
      dataType: newParamType,
      isRequired: true,
      description: `关于参数 ${name} 的描述信息。`,
      objectTypeId: (newParamType === 'ObjectType' || newParamType === 'ObjectTypeSet') ? newParamObjType : undefined
    };

    onUpdate({
      ...func,
      parameters: [...func.parameters, newParam]
    });
    setNewParamName('');
  };

  // Remove parameter
  const handleRemoveParam = (name: string) => {
    onUpdate({
      ...func,
      parameters: func.parameters.filter(p => p.name !== name)
    });
  };

  // Update parameter field
  const handleParamFieldChange = (name: string, field: keyof FunctionParameter, value: any) => {
    onUpdate({
      ...func,
      parameters: func.parameters.map(p =>
        p.name === name ? { ...p, [field]: value } : p
      )
    });
  };

  // Template codes
  const loadTemplate = (type: 'validation' | 'default' | 'computed' | 'aggregation') => {
    let codeTemplate = '';
    const className = func.apiName.charAt(0).toUpperCase() + func.apiName.slice(1) + 'Class';

    if (type === 'validation') {
      codeTemplate = `import { Function } from "@foundry/functions-api";
import { Aircraft } from "../objects";

export class ${className} {
    /**
     * 自定义验证逻辑：检查飞机最近适航维护日期是否符合飞行任务的强制安全周期要求（例如不晚于180天）
     */
    @Function()
    public async validateMaintenancePeriod(aircraft: Aircraft, safetyIntervalDays: number): Promise<boolean> {
        if (!aircraft.lastMaintenanceDate) {
            return false;
        }
        
        const lastMaint = new Date(aircraft.lastMaintenanceDate).getTime();
        const now = Date.now();
        const diffDays = (now - lastMaint) / (1000 * 60 * 60 * 24);
        
        return diffDays <= safetyIntervalDays;
    }
}`;
    } else if (type === 'default') {
      codeTemplate = `import { Function, Integer } from "@foundry/functions-api";

export class ${className} {
    /**
     * 动态默认值提供：根据当前航班出发地及时区计算建议的滑行道耗时 (分钟)
     */
    @Function()
    public getDefaultTaxiDuration(airportCode: string): Integer {
        const busyAirports = ["ATL", "ORD", "SFO", "PEK"];
        if (busyAirports.includes(airportCode)) {
            return 25; // 繁忙大机场默认滑行25分钟
        }
        return 10; // 普通中型机场默认滑行10分钟
    }
}`;
    } else if (type === 'computed') {
      codeTemplate = `import { Function } from "@foundry/functions-api";
import { Pilot } from "../objects";

export class ${className} {
    /**
     * 对象派生属性计算：根据飞行员总安全飞行小时数，计算并返回其对应的技术等级星级评定
     */
    @Function()
    public computePilotStarLevel(pilot: Pilot): string {
        const hours = pilot.hoursFlown || 0;
        if (hours >= 10000) return "⭐⭐⭐⭐⭐ (金牌资深航线机长)";
        if (hours >= 5000) return "⭐⭐⭐⭐ (资深特级机长)";
        if (hours >= 3000) return "⭐⭐⭐ (普通一类机长)";
        return "⭐⭐ (高级副驾驶)";
    }
}`;
    } else {
      codeTemplate = `import { Function, Integer, ObjectSet } from "@foundry/functions-api";
import { Aircraft } from "../objects";

export class ${className} {
    /**
     * 统计聚合：返回给定飞机集合中，处于特定在勤维护状态 (MAINTENANCE) 的数量
     */
    @Function()
    public countAircraftsInMaintenance(aircrafts: ObjectSet<Aircraft>): Integer {
        // 利用 Foundry ObjectSet 的过滤器进行快速服务端筛选并统计
        return aircrafts
            .filter(ac => ac.status.exactMatch("MAINTENANCE"))
            .count();
    }
}`;
    }

    handleFieldChange('code', codeTemplate);
  };

  // Run mock test simulation
  const handleRunTest = () => {
    setIsTesting(true);
    setTestLogs([]);
    setTestResult(null);

    const logs: string[] = [];
    const addLog = (msg: string, delay: number) => {
      setTimeout(() => {
        setTestLogs(prev => [...prev, msg]);
      }, delay);
    };

    addLog(`[Foundry Compiler] 🔍 正在检索 TypeScript 源码并进行类型安全检测...`, 200);
    addLog(`[Foundry Compiler] ⚙️ 发现主入口函数：@Function() public async ${func.apiName}()`, 500);
    addLog(`[Foundry Compiler] ✅ 编译成功，输出：${func.apiName}.js (ES2022 Target)`, 800);
    
    // Stringify inputs
    const inputsStr = Object.entries(testInputs)
      .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}`)
      .join(', ');

    addLog(`[Foundry Runner] 🚀 开始载入本地 Sandbox 运行沙盒...`, 1100);
    addLog(`[Foundry Runner] 📥 注入运行实参：{ ${inputsStr} }`, 1300);
    addLog(`[Foundry Runner] 📡 自动连线 Ontology 本地实例服务并执行动态逻辑...`, 1600);

    setTimeout(() => {
      let output: any = null;
      // Simple mock logic computation based on function returnType
      if (func.returnType === 'boolean') {
        output = true;
      } else if (func.returnType === 'integer' || func.returnType === 'decimal') {
        output = 120; // Static mock number
      } else if (func.returnType === 'ObjectTypeSet') {
        output = {
          count: 2,
          type: func.returnObjectTypeId || 'aircraft',
          ids: ['ac_n101ua', 'ac_n204dl'],
          message: `Mock ObjectSet containing 2 instances of ${func.returnObjectTypeId}`
        };
      } else {
        output = "⭐ COMPLETED ⭐";
      }

      setTestLogs(prev => [
        ...prev,
        `[Foundry Runner] 💾 沙盒计算完成 (耗时: 38ms, 占用内存: 12.4MB)`,
        `[Foundry Runner] 🎉 执行结束，输出结果已捕获：`
      ]);
      setTestResult(output);
      setIsTesting(false);
    }, 2000);
  };

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Detail Header */}
      <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-full border border-violet-300 bg-violet-50 text-violet-700 flex items-center justify-center">
            <LucideIcon name="Code" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={func.displayName}
                onChange={e => handleFieldChange('displayName', e.target.value)}
                className="text-lg font-semibold text-slate-900 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5"
              />
              <span className="text-xs font-mono bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                {func.apiName}
              </span>
              <span className="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full font-mono">
                Returns {func.returnType === 'ObjectTypeSet' ? `Set<${func.returnObjectTypeId}>` : func.returnType}
              </span>
            </div>
            <input
              type="text"
              value={func.description}
              onChange={e => handleFieldChange('description', e.target.value)}
              className="text-xs text-slate-500 mt-1 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-[500px]"
              placeholder="添加函数的功能与作用描述"
            />
          </div>
        </div>
        <button
          onClick={() => onDelete(func.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
        >
          <LucideIcon name="Trash2" size={13} />
          删除函数
        </button>
      </div>

      {/* Tab bar */}
      <div className="flex px-6 border-b border-gray-200 bg-white">
        {(['signature', 'code', 'test'] as const).map(tab => {
          const labels = {
            signature: '1. 签名与参数 (Signature)',
            code: '2. TS 代码编辑 (TypeScript Code)',
            test: '3. 沙盒测试运行 (Sandbox Runner)'
          };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {labels[tab]}
            </button>
          );
        })}
      </div>

      {/* Content body */}
      <div className="flex-1 overflow-hidden flex">
        
        {/* SIGNATURE TAB */}
        {activeTab === 'signature' && (
          <div className="flex-1 overflow-y-auto p-6 space-y-6">
            
            {/* Signature Basic configuration */}
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-5 space-y-4">
              <h3 className="text-xs font-semibold text-slate-800">函数出参及归属 (Return Type / Output)</h3>
              <div className="grid grid-cols-2 gap-4 text-xs">
                <div className="space-y-1">
                  <label className="text-[10px] font-medium text-slate-600 block">返回参数类型 (Return Type)</label>
                  <select
                    value={func.returnType}
                    onChange={e => handleFieldChange('returnType', e.target.value)}
                    className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full font-mono"
                  >
                    <option value="string">string (字符串)</option>
                    <option value="integer">integer (整数)</option>
                    <option value="decimal">decimal (小数)</option>
                    <option value="boolean">boolean (布尔值)</option>
                    <option value="date">date (日期)</option>
                    <option value="timestamp">timestamp (时间戳)</option>
                    <option value="ObjectType">ObjectType (特定对象实例)</option>
                    <option value="ObjectTypeSet">ObjectTypeSet (对象集合)</option>
                  </select>
                </div>

                {/* Bind to Object Type if returning Object/ObjectSet */}
                {(func.returnType === 'ObjectType' || func.returnType === 'ObjectTypeSet') && (
                  <div className="space-y-1">
                    <label className="text-[10px] font-medium text-slate-600 block">返回对象类型 (Target Object Type)</label>
                    <select
                      value={func.returnObjectTypeId || ''}
                      onChange={e => handleFieldChange('returnObjectTypeId', e.target.value)}
                      className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full"
                    >
                      {objectTypes.map(ot => (
                        <option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              <div className="grid grid-cols-2 gap-4 text-xs">
                <div className="space-y-1">
                  <label className="text-[10px] font-medium text-slate-600 block">API标识名称 (API Name)</label>
                  <input
                    type="text"
                    value={func.apiName}
                    onChange={e => handleFieldChange('apiName', e.target.value)}
                    className="w-full px-2.5 py-1.5 border border-gray-300 rounded bg-white font-mono focus:outline-hidden"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[10px] font-medium text-slate-600 block">关联的核心对象 (Associated Object Type)</label>
                  <select
                    value={func.associatedObjectType || ''}
                    onChange={e => handleFieldChange('associatedObjectType', e.target.value)}
                    className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full"
                  >
                    <option value="">-- 无特定关联对象 (全局函数) --</option>
                    {objectTypes.map(ot => (
                      <option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            {/* Input parameters configuration */}
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <h3 className="text-xs font-semibold text-slate-800">定义函数入参 (Input Parameters)</h3>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="新参数变量名 (e.g. airportCode)"
                    value={newParamName}
                    onChange={e => setNewParamName(e.target.value)}
                    className="px-2.5 py-1 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden font-mono"
                  />
                  <select
                    value={newParamType}
                    onChange={e => setNewParamType(e.target.value)}
                    className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:outline-hidden font-mono"
                  >
                    <option value="string">string</option>
                    <option value="integer">integer</option>
                    <option value="decimal">decimal</option>
                    <option value="boolean">boolean</option>
                    <option value="date">date</option>
                    <option value="timestamp">timestamp</option>
                    <option value="ObjectType">ObjectType (对象实例)</option>
                    <option value="ObjectTypeSet">ObjectTypeSet (对象集合)</option>
                  </select>
                  {(newParamType === 'ObjectType' || newParamType === 'ObjectTypeSet') && (
                    <select
                      value={newParamObjType}
                      onChange={e => setNewParamObjType(e.target.value)}
                      className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:outline-hidden"
                    >
                      {objectTypes.map(ot => (
                        <option key={ot.id} value={ot.id}>{ot.displayName}</option>
                      ))}
                    </select>
                  )}
                  <button
                    onClick={handleAddParam}
                    className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1"
                  >
                    <LucideIcon name="Plus" size={13} />
                    添加参数
                  </button>
                </div>
              </div>

              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-slate-50 border-b border-gray-200 text-slate-700 font-medium">
                      <th className="py-2.5 px-4 w-12">必选</th>
                      <th className="py-2.5 px-4">变量标识</th>
                      <th className="py-2.5 px-4">参数类型</th>
                      <th className="py-2.5 px-4">绑定实体类型</th>
                      <th className="py-2.5 px-4">参数业务描述</th>
                      <th className="py-2.5 px-4 text-center">操作</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 text-slate-600">
                    {func.parameters.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="text-center py-8 text-slate-400 italic">
                          当前函数无输入参数，只能执行无参静态逻辑。
                        </td>
                      </tr>
                    ) : (
                      func.parameters.map(p => (
                        <tr key={p.name} className="hover:bg-slate-50/50">
                          <td className="py-2.5 px-4">
                            <input
                              type="checkbox"
                              checked={p.isRequired}
                              onChange={e => handleParamFieldChange(p.name, 'isRequired', e.target.checked)}
                              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 h-3.5 w-3.5"
                            />
                          </td>
                          <td className="py-2.5 px-4 font-mono font-medium text-slate-900">{p.name}</td>
                          <td className="py-2.5 px-4 font-mono text-slate-500">{p.dataType}</td>
                          <td className="py-2.5 px-4">
                            {(p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                              <select
                                value={p.objectTypeId || ''}
                                onChange={e => handleParamFieldChange(p.name, 'objectTypeId', e.target.value)}
                                className="px-2 py-0.5 border border-gray-200 rounded bg-white focus:outline-hidden"
                              >
                                {objectTypes.map(ot => (
                                  <option key={ot.id} value={ot.id}>{ot.displayName}</option>
                                ))}
                              </select>
                            ) : (
                              <span className="text-slate-400 font-mono">—</span>
                            )}
                          </td>
                          <td className="py-2.5 px-4">
                            <input
                              type="text"
                              value={p.description}
                              onChange={e => handleParamFieldChange(p.name, 'description', e.target.value)}
                              className="text-slate-500 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full"
                              placeholder="配置描述信息"
                            />
                          </td>
                          <td className="py-2.5 px-4 text-center">
                            <button
                              onClick={() => handleRemoveParam(p.name)}
                              className="text-slate-400 hover:text-red-500 p-1 rounded"
                            >
                              <LucideIcon name="X" size={14} />
                            </button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* CODE TAB */}
        {activeTab === 'code' && (
          <div className="flex-1 flex overflow-hidden">
            {/* Template selector side sidebar */}
            <div className="w-56 border-r border-gray-200 bg-slate-50/50 p-4 flex flex-col gap-4 overflow-y-auto select-none">
              <div>
                <h4 className="text-xs font-semibold text-slate-700">Foundry 函数模板</h4>
                <p className="text-[10px] text-slate-500 mt-0.5">选择契合您业务目标的TS模板一键生成标准代码结构。</p>
              </div>
              <div className="space-y-2">
                <button
                  onClick={() => loadTemplate('validation')}
                  className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2"
                >
                  <LucideIcon name="Shield" size={14} className="text-emerald-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">拦截校验模板</div>
                    <div className="text-[10px] font-normal text-slate-400 mt-0.5">限制Action提交</div>
                  </div>
                </button>
                <button
                  onClick={() => loadTemplate('default')}
                  className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2"
                >
                  <LucideIcon name="Sparkles" size={14} className="text-amber-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">动态入参默认值</div>
                    <div className="text-[10px] font-normal text-slate-400 mt-0.5">默认值公式计算</div>
                  </div>
                </button>
                <button
                  onClick={() => loadTemplate('computed')}
                  className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2"
                >
                  <LucideIcon name="Calculator" size={14} className="text-blue-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">派生计算属性</div>
                    <div className="text-[10px] font-normal text-slate-400 mt-0.5">实体派生衍生指标</div>
                  </div>
                </button>
                <button
                  onClick={() => loadTemplate('aggregation')}
                  className="w-full text-left p-2.5 bg-white border border-gray-200 hover:border-blue-500 rounded-lg text-xs font-medium text-slate-700 transition-all flex items-start gap-2"
                >
                  <LucideIcon name="TrendingUp" size={14} className="text-indigo-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">对象集统计聚合</div>
                    <div className="text-[10px] font-normal text-slate-400 mt-0.5">多实体合并聚合计算</div>
                  </div>
                </button>
              </div>
              <div className="mt-auto bg-blue-50 border border-blue-100 rounded-lg p-3 text-[11px] text-blue-700 leading-relaxed">
                <div className="font-semibold flex items-center gap-1 mb-1">
                  <LucideIcon name="Info" size={12} />
                  <span>TS代码要求:</span>
                </div>
                必须通过 <code>@Function()</code> 装饰器公开核心函数，以使 Workshop 应用和操作 (Actions) 能够发现并进行远程绑定。
              </div>
            </div>

            {/* Code editor body */}
            <div className="flex-1 flex flex-col bg-slate-900 overflow-hidden relative">
              {/* Code editor header status */}
              <div className="px-4 py-2 border-b border-slate-800 flex justify-between items-center text-[10px] text-slate-400 select-none font-mono">
                <div className="flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse"></span>
                  <span>TypeScript 1.84 - Foundry API Sync: ACTIVE</span>
                </div>
                <div className="flex items-center gap-3">
                  <span>UTF-8</span>
                  <span>Tab Size: 4</span>
                </div>
              </div>

              {/* Textarea code container */}
              <div className="flex-1 flex font-mono text-xs overflow-hidden leading-relaxed">
                {/* Simulated line numbers */}
                <div className="w-12 bg-slate-950 text-slate-600 text-right pr-3 select-none pt-4 flex flex-col">
                  {Array.from({ length: 45 }).map((_, i) => (
                    <div key={i}>{i + 1}</div>
                  ))}
                </div>
                
                {/* Main textarea */}
                <textarea
                  value={func.code}
                  onChange={e => handleFieldChange('code', e.target.value)}
                  className="flex-1 bg-slate-900 text-slate-150 p-4 border-0 focus:outline-hidden font-mono text-xs resize-none h-full overflow-y-auto leading-relaxed outline-hidden"
                  spellCheck="false"
                />
              </div>
            </div>
          </div>
        )}

        {/* TEST TAB */}
        {activeTab === 'test' && (
          <div className="flex-1 flex overflow-hidden">
            {/* Input params form */}
            <div className="w-1/3 border-r border-gray-200 p-5 bg-slate-50/50 flex flex-col justify-between overflow-y-auto select-none">
              <div className="space-y-4 text-xs">
                <div>
                  <h4 className="text-xs font-semibold text-slate-800">沙盒测试参数输入</h4>
                  <p className="text-[10px] text-slate-500 mt-0.5">请为该函数的输入参数填入测试值以模拟执行。</p>
                </div>

                <div className="space-y-3">
                  {func.parameters.map(p => {
                    const value = testInputs[p.name] !== undefined ? testInputs[p.name] : '';
                    const setVal = (newV: any) => {
                      setTestInputs(prev => ({ ...prev, [p.name]: newV }));
                    };

                    return (
                      <div key={p.name} className="space-y-1 bg-white p-3 rounded-lg border border-gray-200">
                        <div className="flex justify-between items-center text-[10px]">
                          <span className="font-semibold text-slate-700 font-mono">{p.name}</span>
                          <span className="font-mono text-slate-400">{p.dataType}{p.isRequired && '*'}</span>
                        </div>
                        <p className="text-[10px] text-slate-400 mb-1">{p.description}</p>

                        {/* RENDER DYNAMIC FIELD BASED ON TYPE */}
                        {p.dataType === 'boolean' ? (
                          <div className="flex items-center gap-3 mt-1.5">
                            <button
                              onClick={() => setVal(true)}
                              className={`px-3 py-1 text-[11px] rounded transition-all font-mono ${value === true ? 'bg-blue-600 text-white font-semibold' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                            >
                              true
                            </button>
                            <button
                              onClick={() => setVal(false)}
                              className={`px-3 py-1 text-[11px] rounded transition-all font-mono ${value === false ? 'bg-blue-600 text-white font-semibold' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                            >
                              false
                            </button>
                          </div>
                        ) : (p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                          <select
                            value={value}
                            onChange={e => setVal(e.target.value)}
                            className="w-full px-2 py-1 text-[11px] border border-gray-300 rounded bg-white mt-1"
                          >
                            <option value="">-- 选择模拟对象实体 --</option>
                            {p.dataType === 'ObjectTypeSet' ? (
                              <option value="mock_set_all_records">所有对象实体集 (ObjectSet 全量)</option>
                            ) : null}
                            {p.objectTypeId === 'flight' && (
                              <>
                                <option value="UA102">Flight: UA102 (ON_TIME)</option>
                                <option value="DL440">Flight: DL440 (DELAYED)</option>
                                <option value="AA880">Flight: AA880 (ON_TIME)</option>
                              </>
                            )}
                            {p.objectTypeId === 'aircraft' && (
                              <>
                                <option value="N101UA">Aircraft: N101UA (Boeing 737-800)</option>
                                <option value="N204DL">Aircraft: N204DL (Airbus A321neo)</option>
                                <option value="N309AA">Aircraft: N309AA (MAINTENANCE)</option>
                              </>
                            )}
                            {p.objectTypeId === 'pilot' && (
                              <>
                                <option value="P01">Pilot: P01 (张建国)</option>
                                <option value="P02">Pilot: P02 (李明华)</option>
                              </>
                            )}
                            <option value="custom_mock_1">自定义测试对象 1</option>
                          </select>
                        ) : p.dataType === 'integer' || p.dataType === 'decimal' ? (
                          <input
                            type="number"
                            value={value}
                            onChange={e => setVal(parseFloat(e.target.value) || 0)}
                            className="w-full px-2.5 py-1 text-[11px] border border-gray-300 rounded focus:outline-hidden"
                          />
                        ) : p.dataType === 'date' || p.dataType === 'timestamp' ? (
                          <input
                            type="datetime-local"
                            value={value}
                            onChange={e => setVal(e.target.value)}
                            className="w-full px-2.5 py-1 text-[11px] border border-gray-300 rounded focus:outline-hidden font-mono"
                          />
                        ) : (
                          <input
                            type="text"
                            value={value}
                            onChange={e => setVal(e.target.value)}
                            className="w-full px-2.5 py-1 text-[11px] border border-gray-300 rounded focus:outline-hidden"
                            placeholder="请输入文本"
                          />
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Big Run Button */}
              <button
                onClick={handleRunTest}
                disabled={isTesting}
                className="w-full py-2 bg-slate-900 hover:bg-slate-800 disabled:bg-slate-500 text-white rounded-lg flex items-center justify-center gap-2 font-medium transition-all shadow-xs mt-4 text-xs"
              >
                {isTesting ? (
                  <>
                    <span className="h-3 w-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    <span>正在沙盒中执行计算...</span>
                  </>
                ) : (
                  <>
                    <LucideIcon name="Play" size={14} className="fill-white" />
                    <span>运行测试 (Run Function)</span>
                  </>
                )}
              </button>
            </div>

            {/* Runner output & logs */}
            <div className="flex-1 bg-slate-950 p-5 flex flex-col text-xs font-mono overflow-y-auto text-slate-300 select-none">
              <h4 className="text-[10px] text-slate-500 tracking-wider uppercase font-semibold mb-3 border-b border-slate-900 pb-2 flex justify-between items-center">
                <span>函数模拟输出终端 (Foundry Console)</span>
                {testResult !== null && (
                  <span className="text-emerald-500 font-semibold flex items-center gap-1 bg-emerald-500/10 px-1.5 py-0.5 rounded">
                    <LucideIcon name="CheckCircle" size={11} /> SUCCESS
                  </span>
                )}
              </h4>

              {/* Logs area */}
              {testLogs.length === 0 ? (
                <div className="flex-1 flex flex-col justify-center items-center text-slate-600 italic">
                  <LucideIcon name="Terminal" size={24} className="mb-2 text-slate-700" />
                  <div>配置完左侧测试入参后，点击下方 "运行测试" 按钮。</div>
                  <div>系统将在编译并在虚拟沙盒中运行您的 TS 代码。</div>
                </div>
              ) : (
                <div className="flex-1 space-y-1.5 select-text">
                  {testLogs.map((log, i) => (
                    <div key={i} className={
                      log.includes('SUCCESS') || log.includes('捕获') ? 'text-emerald-400' :
                      log.includes('注入') ? 'text-blue-400' :
                      log.includes('⚙️') || log.includes('✅') ? 'text-slate-400' :
                      'text-slate-300'
                    }>
                      {log}
                    </div>
                  ))}

                  {/* Output block */}
                  {testResult !== null && (
                    <div className="mt-4 p-4 rounded bg-slate-900/60 border border-slate-800 text-emerald-300">
                      <div className="text-[10px] text-slate-500 mb-1 font-sans uppercase tracking-wider font-semibold">
                        返回值 (Return Value):
                      </div>
                      <pre className="text-xs leading-relaxed">
                        {typeof testResult === 'object' ? JSON.stringify(testResult, null, 4) : String(testResult)}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
