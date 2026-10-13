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
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';

interface FunctionTypeViewProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: FunctionType) => void;
  onDelete: (id: string) => void;
  /** 无假真理：函数为 enterpriseOntologyDemo 演示数据（无后端端点）⇒ 禁用删除并如实标注 */
  deleteUnsupported?: boolean;
  deleteUnsupportedReason?: string;
}

export default function FunctionTypeView({ func, objectTypes, onUpdate, onDelete, deleteUnsupported, deleteUnsupportedReason }: FunctionTypeViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState<'signature' | 'code' | 'test'>('code');
  const [newParamName, setNewParamName] = useState('');
  const [newParamType, setNewParamType] = useState<string>('string');
  const [newParamObjType, setNewParamObjType] = useState(objectTypes[0]?.id || '');
  const [testInputs, setTestInputs] = useState<Record<string, unknown>>({});
  const [isTesting, setIsTesting] = useState(false);
  const [testLogs, setTestLogs] = useState<string[]>([]);
  const [testResult, setTestResult] = useState<unknown>(null);

  useEffect(() => {
    const inputs: Record<string, unknown> = {};
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

  const handleFieldChange = (key: keyof FunctionType, value: unknown) => {
    onUpdate({ ...func, [key]: value });
  };

  const handleAddParam = () => {
    if (!newParamName.trim()) return;
    const name = newParamName.trim().replace(/\s+/g, '').replace(/[^a-zA-Z0-9]/g, '');
    const newParam: FunctionParameter = {
      name, dataType: newParamType, isRequired: true,
      description: t('ow.func.paramDefaultDescription').replace('{name}', name),
      objectTypeId: (newParamType === 'ObjectType' || newParamType === 'ObjectTypeSet') ? newParamObjType : undefined
    };
    onUpdate({ ...func, parameters: [...func.parameters, newParam] });
    setNewParamName('');
  };

  const handleRemoveParam = (name: string) => {
    onUpdate({ ...func, parameters: func.parameters.filter(p => p.name !== name) });
  };

  const handleParamFieldChange = (name: string, field: keyof FunctionParameter, value: unknown) => {
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
    // 无假真理：平台不存在函数编译/沙箱执行端点。此前用 setTimeout 伪造
    // "[ECOS Compiler] 编译成功 / 38ms / 输出结果" 全是剧本 —— 全部删除，
    // 只给出如实的能力未上线说明（不产出假结果、不伪装耗时）。
    setIsTesting(false);
    setTestResult(null);
    setTestLogs([t('ontology.function_test_unsupported')]);
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-full border border-violet-300 ${styles.badgeBg} ${styles.badgeText} flex items-center justify-center`}>
            <Code size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <input type="text" value={func.displayName} onChange={e => handleFieldChange('displayName', e.target.value)}
                className={`text-lg font-semibold ${styles.cardText} border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5`} />
              <span className={`text-xs font-mono ${styles.badgeBg} ${styles.badgeText} px-1.5 py-0.5 rounded`}>{func.apiName}</span>
              <span className={`text-xs ${styles.badgeBg} ${styles.muted} px-2 py-0.5 rounded-full font-mono`}>
                Returns {func.returnType === 'ObjectTypeSet' ? `Set<${func.returnObjectTypeId}>` : func.returnType}
              </span>
            </div>
            <input type="text" value={func.description} onChange={e => handleFieldChange('description', e.target.value)}
              className={`text-xs ${styles.muted} mt-1 border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-[500px]`}
              placeholder={t('ow.placeholder.funcDescription')} />
          </div>
        </div>
        <button
          type="button"
          disabled={deleteUnsupported}
          title={deleteUnsupported ? deleteUnsupportedReason : undefined}
          onClick={() => { if (!deleteUnsupported) onDelete(func.id); }}
          className={deleteUnsupported
            ? `text-xs px-2.5 py-1.5 rounded border ${styles.appBorder} ${styles.muted} cursor-not-allowed inline-flex items-center gap-1.5 opacity-70`
            : "text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"}
        >
          <Trash2 size={13} />{deleteUnsupported ? (deleteUnsupportedReason || t('ow.btn.deleteFunction')) : t('ow.btn.deleteFunction')}
        </button>
      </div>

      <div className={`flex px-6 border-b ${styles.cardBorder} ${styles.cardBg}`}>
        {(['signature', 'code', 'test'] as const).map(tab => {
          const labels: Record<string, string> = {
            signature: t('ow.tab.funcSignature'), code: t('ow.tab.funcCode'), test: t('ow.tab.funcTest')
          };
          return (
            <button type="button" key={tab} onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? `${styles.accentBorder} ${styles.accentText}` : `border-transparent ${styles.muted} hover:${styles.cardText}`
              }`}>{labels[tab]}</button>
          );
        })}
      </div>

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
