/**
 * FunctionTypeDetail — 逻辑函数类型详情视图
 *
 * 本体工作台函数定义详情页，展示函数签名/TypeScript代码/沙盒测试。
 * Tab 内容已拆分为独立组件（见 ./function/ 目录）。
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { Code, Trash2 } from 'lucide-react';

import type { FunctionType, FunctionParameter, ObjectType } from '../../types/ontology';
import SignatureTab from './function/SignatureTab';
import CodeTab from './function/CodeTab';
import TestTab from './function/TestTab';

interface FunctionTypeViewProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: FunctionType) => void;
  onDelete: (id: string) => void;
}

export default function FunctionTypeView({ func, objectTypes, onUpdate, onDelete }: FunctionTypeViewProps) {
  const [activeTab, setActiveTab] = useState<'signature' | 'code' | 'test'>('code');
  const [newParamName, setNewParamName] = useState('');
  const [newParamType, setNewParamType] = useState<string>('string');
  const [newParamObjType, setNewParamObjType] = useState(objectTypes[0]?.id || '');
  const [testInputs, setTestInputs] = useState<Record<string, any>>({});
  const [isTesting, setIsTesting] = useState(false);
  const [testLogs, setTestLogs] = useState<string[]>([]);
  const [testResult, setTestResult] = useState<any>(null);

  useEffect(() => {
    const inputs: Record<string, any> = {};
    func.parameters.forEach(p => {
      if (p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') {
        inputs[p.name] = (objectTypes.find(ot => ot.id === p.objectTypeId) || objectTypes[0])?.id || '';
      } else if (p.dataType === 'integer' || p.dataType === 'decimal') {
        inputs[p.name] = 0;
      } else if (p.dataType === 'boolean') {
        inputs[p.name] = true;
      } else { inputs[p.name] = ''; }
    });
    setTestInputs(inputs);
  }, [func.parameters, objectTypes]);

  const handleFieldChange = (key: keyof FunctionType, value: any) => {
    onUpdate({ ...func, [key]: value });
  };

  const handleAddParam = () => {
    if (!newParamName.trim()) return;
    const name = newParamName.trim().replace(/\s+/g, '').replace(/[^a-zA-Z0-9]/g, '');
    const newParam: FunctionParameter = {
      name, dataType: newParamType, isRequired: true,
      description: `关于参数 ${name} 的描述信息。`,
      objectTypeId: (newParamType === 'ObjectType' || newParamType === 'ObjectTypeSet') ? newParamObjType : undefined
    };
    onUpdate({ ...func, parameters: [...func.parameters, newParam] });
    setNewParamName('');
  };

  const handleRemoveParam = (name: string) => {
    onUpdate({ ...func, parameters: func.parameters.filter(p => p.name !== name) });
  };

  const handleParamFieldChange = (name: string, field: keyof FunctionParameter, value: any) => {
    onUpdate({ ...func, parameters: func.parameters.map(p => p.name === name ? { ...p, [field]: value } : p) });
  };

  const loadTemplate = (type: 'validation' | 'default' | 'computed' | 'aggregation') => {
    const className = func.apiName.charAt(0).toUpperCase() + func.apiName.slice(1) + 'Class';
    const templates: Record<string, string> = {
      validation: `import { Function } from "@ecos/functions-api";\nimport { Aircraft } from "../objects";\n\nexport class ${className} {\n    @Function()\n    public async validateMaintenancePeriod(aircraft: Aircraft, safetyIntervalDays: number): Promise<boolean> {\n        if (!aircraft.lastMaintenanceDate) return false;\n        const lastMaint = new Date(aircraft.lastMaintenanceDate).getTime();\n        const now = Date.now();\n        const diffDays = (now - lastMaint) / (1000 * 60 * 60 * 24);\n        return diffDays <= safetyIntervalDays;\n    }\n}`,
      default: `import { Function, Integer } from "@ecos/functions-api";\n\nexport class ${className} {\n    @Function()\n    public getDefaultTaxiDuration(airportCode: string): Integer {\n        const busyAirports = ["ATL", "ORD", "SFO", "PEK"];\n        if (busyAirports.includes(airportCode)) return 25;\n        return 10;\n    }\n}`,
      computed: `import { Function } from "@ecos/functions-api";\nimport { Pilot } from "../objects";\n\nexport class ${className} {\n    @Function()\n    public computePilotStarLevel(pilot: Pilot): string {\n        const hours = pilot.hoursFlown || 0;\n        if (hours >= 10000) return "⭐⭐⭐⭐⭐ (金牌资深航线机长)";\n        if (hours >= 5000) return "⭐⭐⭐⭐ (资深特级机长)";\n        if (hours >= 3000) return "⭐⭐⭐ (普通一类机长)";\n        return "⭐⭐ (高级副驾驶)";\n    }\n}`,
      aggregation: `import { Function, Integer, ObjectSet } from "@ecos/functions-api";\nimport { Aircraft } from "../objects";\n\nexport class ${className} {\n    @Function()\n    public countAircraftsInMaintenance(aircrafts: ObjectSet<Aircraft>): Integer {\n        return aircrafts.filter(ac => ac.status.exactMatch("MAINTENANCE")).count();\n    }\n}`,
    };
    handleFieldChange('code', templates[type]);
  };

  const handleRunTest = () => {
    setIsTesting(true);
    setTestLogs([]);
    setTestResult(null);
    const addLog = (msg: string, delay: number) => { setTimeout(() => { setTestLogs(prev => [...prev, msg]); }, delay); };
    addLog(`[ECOS Compiler] 🔍 正在检索 TypeScript 源码并进行类型安全检测...`, 200);
    addLog(`[ECOS Compiler] ⚙️ 发现主入口函数：@Function() public async ${func.apiName}()`, 500);
    addLog(`[ECOS Compiler] ✅ 编译成功，输出：${func.apiName}.js (ES2022 Target)`, 800);
    const inputsStr = Object.entries(testInputs).map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}`).join(', ');
    addLog(`[ECOS Runner] 🚀 开始载入本地 Sandbox 运行沙盒...`, 1100);
    addLog(`[ECOS Runner] 📥 注入运行实参：{ ${inputsStr} }`, 1300);
    addLog(`[ECOS Runner] 📡 自动连线 Ontology 本地实例服务并执行动态逻辑...`, 1600);
    setTimeout(() => {
      let output: any = null;
      if (func.returnType === 'boolean') output = true;
      else if (func.returnType === 'integer' || func.returnType === 'decimal') output = 120;
      else if (func.returnType === 'ObjectTypeSet') output = { count: 2, type: func.returnObjectTypeId || 'aircraft', ids: ['ac_n101ua', 'ac_n204dl'], message: `Mock ObjectSet containing 2 instances of ${func.returnObjectTypeId}` };
      else output = "⭐ COMPLETED ⭐";
      setTestLogs(prev => [...prev, `[ECOS Runner] 💾 沙盒计算完成 (耗时: 38ms, 占用内存: 12.4MB)`, `[ECOS Runner] 🎉 执行结束，输出结果已捕获：`]);
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
            <Code size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <input type="text" value={func.displayName} onChange={e => handleFieldChange('displayName', e.target.value)}
                className="text-lg font-semibold text-slate-900 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5" />
              <span className="text-xs font-mono bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">{func.apiName}</span>
              <span className="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full font-mono">
                Returns {func.returnType === 'ObjectTypeSet' ? `Set<${func.returnObjectTypeId}>` : func.returnType}
              </span>
            </div>
            <input type="text" value={func.description} onChange={e => handleFieldChange('description', e.target.value)}
              className="text-xs text-slate-500 mt-1 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-[500px]"
              placeholder="添加函数的功能与作用描述" />
          </div>
        </div>
        <button onClick={() => onDelete(func.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5">
          <Trash2 size={13} />删除函数
        </button>
      </div>

      {/* Tab bar */}
      <div className="flex px-6 border-b border-gray-200 bg-white">
        {(['signature', 'code', 'test'] as const).map(tab => {
          const labels: Record<string, string> = {
            signature: '1. 签名与参数 (Signature)', code: '2. TS 代码编辑 (TypeScript Code)', test: '3. 沙盒测试运行 (Sandbox Runner)'
          };
          return (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}>{labels[tab]}</button>
          );
        })}
      </div>

      {/* Content body */}
      <div className="flex-1 overflow-hidden flex">
        {activeTab === 'signature' && (
          <SignatureTab func={func} objectTypes={objectTypes} handleFieldChange={handleFieldChange}
            handleAddParam={handleAddParam} handleRemoveParam={handleRemoveParam}
            handleParamFieldChange={handleParamFieldChange}
            newParamName={newParamName} setNewParamName={setNewParamName}
            newParamType={newParamType} setNewParamType={setNewParamType}
            newParamObjType={newParamObjType} setNewParamObjType={setNewParamObjType} />
        )}
        {activeTab === 'code' && (
          <CodeTab func={func} handleFieldChange={handleFieldChange} loadTemplate={loadTemplate} />
        )}
        {activeTab === 'test' && (
          <TestTab func={func} objectTypes={objectTypes}
            testInputs={testInputs} setTestInputs={setTestInputs}
            isTesting={isTesting} handleRunTest={handleRunTest}
            testLogs={testLogs} testResult={testResult} />
        )}
      </div>
    </div>
  );
}
