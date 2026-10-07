/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { FunctionType, FunctionParameter, ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

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
  const { styles } = useTheme();
  const { t } = useLanguage();
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
      description: t('ow.function.newParamDesc', { name }),
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
     * Custom validation: check whether the aircraft's last maintenance date
     * meets the safety-interval requirement (e.g. within the last 180 days).
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
     * Dynamic default value: compute the suggested taxi duration (minutes)
     * based on the departure airport code / timezone.
     */
    @Function()
    public getDefaultTaxiDuration(airportCode: string): Integer {
        const busyAirports = ["ATL", "ORD", "SFO", "PEK"];
        if (busyAirports.includes(airportCode)) {
            return 25; // Busy mega-airport: default to 25 min
        }
        return 10; // Regular midway airport: default to 10 min
    }
}`;
    } else if (type === 'computed') {
      codeTemplate = `import { Function } from "@foundry/functions-api";
import { Pilot } from "../objects";

export class ${className} {
    /**
     * Derived attribute: given the pilot's total safe flight hours,
     * compute and return the corresponding technical star level.
     */
    @Function()
    public computePilotStarLevel(pilot: Pilot): string {
        const hours = pilot.hoursFlown || 0;
        if (hours >= 10000) return "⭐⭐⭐⭐⭐ (Gold senior line captain)";
        if (hours >= 5000) return "⭐⭐⭐⭐ (Senior special-class captain)";
        if (hours >= 3000) return "⭐⭐⭐ (Standard first-class captain)";
        return "⭐⭐ (Senior first officer)";
    }
}`;
    } else {
      codeTemplate = `import { Function, Integer, ObjectSet } from "@foundry/functions-api";
import { Aircraft } from "../objects";

export class ${className} {
    /**
     * Aggregation: return, within the given set of aircraft, the count of
     * those currently in the MAINTENANCE status.
     */
    @Function()
    public countAircraftsInMaintenance(aircrafts: ObjectSet<Aircraft>): Integer {
        // Use Foundry ObjectSet inline filter for fast server-side counting
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

    addLog(t('ow.function.log_compiler_scan'), 200);
    addLog(t('ow.function.log_compiler_entry', { name: func.apiName }), 500);
    addLog(t('ow.function.log_compiler_success', { name: func.apiName }), 800);

    // Stringify inputs
    const inputsStr = Object.entries(testInputs)
      .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : v}`)
      .join(', ');

    addLog(t('ow.function.log_runner_sandbox'), 1100);
    addLog(t('ow.function.log_runner_inject', { inputs: inputsStr }), 1300);
    addLog(t('ow.function.log_runner_bind'), 1600);

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
        t('ow.function.log_runner_done'),
        t('ow.function.log_runner_result')
      ]);
      setTestResult(output);
      setIsTesting(false);
    }, 2000);
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      {/* Detail Header */}
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
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
                className={`text-lg font-semibold ${styles.cardText} border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5`}
              />
              <span className={`text-xs font-mono ${styles.appBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>
                {func.apiName}
              </span>
              <span className={`text-xs ${styles.appBg} ${styles.cardTextMuted} px-2 py-0.5 rounded-full font-mono`}>
                {t('ow.function.returnsPrefix')} {func.returnType === 'ObjectTypeSet' ? `Set<${func.returnObjectTypeId}>` : func.returnType}
              </span>
            </div>
            <input
              type="text"
              value={func.description}
              onChange={e => handleFieldChange('description', e.target.value)}
              className={`text-xs ${styles.cardTextMuted} mt-1 border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full max-w-[500px]`}
              placeholder={t('ow.function.descPlaceholder')}
            />
          </div>
        </div>
        <button
          onClick={() => onDelete(func.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
        >
          <LucideIcon name="Trash2" size={13} />
          {t('ow.function.deleteFunction')}
        </button>
      </div>

      {/* Tab bar */}
      <div className={`flex px-6 border-b ${styles.appBorder} ${styles.cardBg}`}>
        {(['signature', 'code', 'test'] as const).map(tab => {
          const tabLabels: Record<typeof tab, string> = {
            signature: t('ow.function.tab_signature'),
            code: t('ow.function.tab_code'),
            test: t('ow.function.tab_test')
          };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : `border-transparent ${styles.cardTextMuted} hover:opacity-100 opacity-80`
              }`}
            >
              {tabLabels[tab]}
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
            <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
              <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.function.sig_returnTitle')}</h3>
              <div className="grid grid-cols-2 gap-4 text-xs">
                <div className="space-y-1">
                  <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.function.sig_returnType')}</label>
                  <select
                    value={func.returnType}
                    onChange={e => handleFieldChange('returnType', e.target.value)}
                    className={`px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} w-full font-mono`}
                  >
                    <option value="string">{t('ow.function.rt_string')}</option>
                    <option value="integer">{t('ow.function.rt_integer')}</option>
                    <option value="decimal">{t('ow.function.rt_decimal')}</option>
                    <option value="boolean">{t('ow.function.rt_boolean')}</option>
                    <option value="date">{t('ow.function.rt_date')}</option>
                    <option value="timestamp">{t('ow.function.rt_timestamp')}</option>
                    <option value="ObjectType">{t('ow.function.rt_objectType')}</option>
                    <option value="ObjectTypeSet">{t('ow.function.rt_objectTypeSet')}</option>
                  </select>
                </div>

                {/* Bind to Object Type if returning Object/ObjectSet */}
                {(func.returnType === 'ObjectType' || func.returnType === 'ObjectTypeSet') && (
                  <div className="space-y-1">
                    <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.function.sig_returnObjType')}</label>
                    <select
                      value={func.returnObjectTypeId || ''}
                      onChange={e => handleFieldChange('returnObjectTypeId', e.target.value)}
                      className={`px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} w-full`}
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
                  <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.function.sig_apiName')}</label>
                  <input
                    type="text"
                    value={func.apiName}
                    onChange={e => handleFieldChange('apiName', e.target.value)}
                    className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} font-mono focus:outline-hidden`}
                  />
                </div>
                <div className="space-y-1">
                  <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.function.sig_associated')}</label>
                  <select
                    value={func.associatedObjectType || ''}
                    onChange={e => handleFieldChange('associatedObjectType', e.target.value)}
                    className={`px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} w-full`}
                  >
                    <option value="">{t('ow.function.sig_associated_none')}</option>
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
                <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.function.params_title')}</h3>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder={t('ow.function.params_namePlaceholder')}
                    value={newParamName}
                    onChange={e => setNewParamName(e.target.value)}
                    className={`px-2.5 py-1 text-xs border ${styles.inputBorder} rounded focus:border-blue-500 focus:outline-hidden font-mono`}
                  />
                  <select
                    value={newParamType}
                    onChange={e => setNewParamType(e.target.value)}
                    className={`px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden font-mono`}
                  >
                    <option value="string">string</option>
                    <option value="integer">integer</option>
                    <option value="decimal">decimal</option>
                    <option value="boolean">boolean</option>
                    <option value="date">date</option>
                    <option value="timestamp">timestamp</option>
                    <option value="ObjectType">{t('ow.function.pt_objectType')}</option>
                    <option value="ObjectTypeSet">{t('ow.function.pt_objectTypeSet')}</option>
                  </select>
                  {(newParamType === 'ObjectType' || newParamType === 'ObjectTypeSet') && (
                    <select
                      value={newParamObjType}
                      onChange={e => setNewParamObjType(e.target.value)}
                      className={`px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
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
                    {t('ow.function.add_param')}
                  </button>
                </div>
              </div>

              <div className={`border ${styles.appBorder} rounded-lg overflow-hidden`}>
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className={`${styles.appBg} border-b ${styles.appBorder} ${styles.cardText} font-medium`}>
                      <th className="py-2.5 px-4 w-12">{t('ow.function.th_required')}</th>
                      <th className="py-2.5 px-4">{t('ow.function.th_name')}</th>
                      <th className="py-2.5 px-4">{t('ow.function.th_type')}</th>
                      <th className="py-2.5 px-4">{t('ow.function.th_bind')}</th>
                      <th className="py-2.5 px-4">{t('ow.function.th_desc')}</th>
                      <th className="py-2.5 px-4 text-center">{t('ow.function.th_actions')}</th>
                    </tr>
                  </thead>
                  <tbody className={`divide-y ${styles.divider} ${styles.cardTextMuted}`}>
                    {func.parameters.length === 0 ? (
                      <tr>
                        <td colSpan={6} className={`text-center py-8 ${styles.cardTextMuted} italic`}>
                          {t('ow.function.params_empty')}
                        </td>
                      </tr>
                    ) : (
                      func.parameters.map(p => (
                        <tr key={p.name} className="hover:bg-blue-50/20">
                          <td className="py-2.5 px-4">
                            <input
                              type="checkbox"
                              checked={p.isRequired}
                              onChange={e => handleParamFieldChange(p.name, 'isRequired', e.target.checked)}
                              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 h-3.5 w-3.5"
                            />
                          </td>
                          <td className={`py-2.5 px-4 font-mono font-medium ${styles.cardText}`}>{p.name}</td>
                          <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{p.dataType}</td>
                          <td className="py-2.5 px-4">
                            {(p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                              <select
                                value={p.objectTypeId || ''}
                                onChange={e => handleParamFieldChange(p.name, 'objectTypeId', e.target.value)}
                                className={`px-2 py-0.5 border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
                              >
                                {objectTypes.map(ot => (
                                  <option key={ot.id} value={ot.id}>{ot.displayName}</option>
                                ))}
                              </select>
                            ) : (
                              <span className={`${styles.muted} font-mono`}>—</span>
                            )}
                          </td>
                          <td className="py-2.5 px-4">
                            <input
                              type="text"
                              value={p.description}
                              onChange={e => handleParamFieldChange(p.name, 'description', e.target.value)}
                              className={`${styles.cardTextMuted} border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full`}
                              placeholder={t('ow.function.params_descPlaceholder')}
                            />
                          </td>
                          <td className="py-2.5 px-4 text-center">
                            <button
                              onClick={() => handleRemoveParam(p.name)}
                              className={`p-1 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500 rounded`}
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
            <div className={`w-56 border-r ${styles.sidebarBorder} ${styles.sidebarBg} p-4 flex flex-col gap-4 overflow-y-auto select-none`}>
              <div>
                <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.function.code_tplTitle')}</h4>
                <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5`}>{t('ow.function.code_tplHint')}</p>
              </div>
              <div className="space-y-2">
                <button
                  onClick={() => loadTemplate('validation')}
                  className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.sidebarBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardText} transition-all flex items-start gap-2`}
                >
                  <LucideIcon name="Shield" size={14} className="text-emerald-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">{t('ow.function.tpl_validation_title')}</div>
                    <div className={`text-[10px] font-normal ${styles.cardTextMuted} mt-0.5`}>{t('ow.function.tpl_validation_desc')}</div>
                  </div>
                </button>
                <button
                  onClick={() => loadTemplate('default')}
                  className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.sidebarBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardText} transition-all flex items-start gap-2`}
                >
                  <LucideIcon name="Sparkles" size={14} className="text-amber-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">{t('ow.function.tpl_default_title')}</div>
                    <div className={`text-[10px] font-normal ${styles.cardTextMuted} mt-0.5`}>{t('ow.function.tpl_default_desc')}</div>
                  </div>
                </button>
                <button
                  onClick={() => loadTemplate('computed')}
                  className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.sidebarBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardText} transition-all flex items-start gap-2`}
                >
                  <LucideIcon name="Calculator" size={14} className="text-blue-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">{t('ow.function.tpl_computed_title')}</div>
                    <div className={`text-[10px] font-normal ${styles.cardTextMuted} mt-0.5`}>{t('ow.function.tpl_computed_desc')}</div>
                  </div>
                </button>
                <button
                  onClick={() => loadTemplate('aggregation')}
                  className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.sidebarBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardText} transition-all flex items-start gap-2`}
                >
                  <LucideIcon name="TrendingUp" size={14} className="text-indigo-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="text-[11px] font-semibold">{t('ow.function.tpl_aggregation_title')}</div>
                    <div className={`text-[10px] font-normal ${styles.cardTextMuted} mt-0.5`}>{t('ow.function.tpl_aggregation_desc')}</div>
                  </div>
                </button>
              </div>
              <div className="mt-auto bg-blue-50 border border-blue-100 rounded-lg p-3 text-[11px] text-blue-700 leading-relaxed">
                <div className="font-semibold flex items-center gap-1 mb-1">
                  <LucideIcon name="Info" size={12} />
                  <span>{t('ow.function.code_req_title')}</span>
                </div>
                {t('ow.function.code_req_body')}
              </div>
            </div>

            {/* Code editor body */}
            <div className="flex-1 flex flex-col bg-[var(--card,#0B0F19)] overflow-hidden relative">
              {/* Code editor header status */}
              <div className="px-4 py-2 border-b border-[var(--card,#1E293B)] flex justify-between items-center text-[10px] text-[var(--card,#94A3B8)] select-none font-mono">
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
                <div className="w-12 bg-[var(--card,#020617)] text-[var(--card,#64748B)] text-right pr-3 select-none pt-4 flex flex-col">
                  {Array.from({ length: 45 }).map((_, i) => (
                    <div key={i}>{i + 1}</div>
                  ))}
                </div>
                
                {/* Main textarea */}
                <textarea
                  value={func.code}
                  onChange={e => handleFieldChange('code', e.target.value)}
                  className="flex-1 bg-[var(--card,#0B0F19)] text-[var(--card,#CBD5E1)] p-4 border-0 focus:outline-hidden font-mono text-xs resize-none h-full overflow-y-auto leading-relaxed outline-hidden"
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
            <div className={`w-1/3 border-r ${styles.sidebarBorder} p-5 ${styles.sidebarBg} flex flex-col justify-between overflow-y-auto select-none`}>
              <div className="space-y-4 text-xs">
                <div>
                  <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.function.test_title')}</h4>
                  <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5`}>{t('ow.function.test_hint')}</p>
                </div>

                <div className="space-y-3">
                  {func.parameters.map(p => {
                    const value = testInputs[p.name] !== undefined ? testInputs[p.name] : '';
                    const setVal = (newV: any) => {
                      setTestInputs(prev => ({ ...prev, [p.name]: newV }));
                    };

                    return (
                      <div key={p.name} className={`space-y-1 ${styles.cardBg} p-3 rounded-lg border ${styles.cardBorder}`}>
                        <div className="flex justify-between items-center text-[10px]">
                          <span className={`font-semibold ${styles.cardText} font-mono`}>{p.name}</span>
                          <span className={`font-mono ${styles.cardTextMuted}`}>{p.dataType}{p.isRequired && '*'}</span>
                        </div>
                        <p className={`text-[10px] ${styles.cardTextMuted} mb-1`}>{p.description}</p>

                        {/* RENDER DYNAMIC FIELD BASED ON TYPE */}
                        {p.dataType === 'boolean' ? (
                          <div className="flex items-center gap-3 mt-1.5">
                            <button
                              onClick={() => setVal(true)}
                              className={`px-3 py-1 text-[11px] rounded transition-all font-mono ${value === true ? 'bg-blue-600 text-white font-semibold' : `${styles.appBg} ${styles.cardTextMuted} ${styles.sidebarHoverBg}`}`}
                            >
                              true
                            </button>
                            <button
                              onClick={() => setVal(false)}
                              className={`px-3 py-1 text-[11px] rounded transition-all font-mono ${value === false ? 'bg-blue-600 text-white font-semibold' : `${styles.appBg} ${styles.cardTextMuted} ${styles.sidebarHoverBg}`}`}
                            >
                              false
                            </button>
                          </div>
                        ) : (p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                          (() => {
                            const ot = objectTypes.find(o => o.id === p.objectTypeId);
                            const sampleLabel = ot ? `${ot.apiName || ot.displayName}` : (p.objectTypeId || 'ObjectType');
                            return (
                              <select
                                value={value}
                                onChange={e => setVal(e.target.value)}
                                className={`w-full px-2 py-1 text-[11px] border ${styles.inputBorder} rounded ${styles.inputBg} mt-1`}
                              >
                                <option value="">{t('ow.function.test_select_entity')}</option>
                                {p.dataType === 'ObjectTypeSet' ? (
                                  <option value="mock_set_all_records">{t('ow.function.test_set_all')}</option>
                                ) : null}
                                <option value={p.objectTypeId || ''}>{t('ow.function.test_mock_entity', { name: sampleLabel })}</option>
                                <option value="custom_mock_1">{t('ow.function.test_custom_mock')}</option>
                              </select>
                            );
                          })()
                        ) : p.dataType === 'integer' || p.dataType === 'decimal' ? (
                          <input
                            type="number"
                            value={value}
                            onChange={e => setVal(parseFloat(e.target.value) || 0)}
                            className={`w-full px-2.5 py-1 text-[11px] border ${styles.inputBorder} rounded focus:outline-hidden ${styles.inputBg}`}
                          />
                        ) : p.dataType === 'date' || p.dataType === 'timestamp' ? (
                          <input
                            type="datetime-local"
                            value={value}
                            onChange={e => setVal(e.target.value)}
                            className={`w-full px-2.5 py-1 text-[11px] border ${styles.inputBorder} rounded focus:outline-hidden font-mono ${styles.inputBg}`}
                          />
                        ) : (
                          <input
                            type="text"
                            value={value}
                            onChange={e => setVal(e.target.value)}
                            className={`w-full px-2.5 py-1 text-[11px] border ${styles.inputBorder} rounded focus:outline-hidden ${styles.inputBg}`}
                            placeholder={t('ow.function.test_textPlaceholder')}
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
                className="w-full py-2 bg-[var(--card,#0F172A)] hover:bg-[var(--muted,#1E293B)] disabled:bg-[var(--card,#475569)] text-white rounded-lg flex items-center justify-center gap-2 font-medium transition-all shadow-xs mt-4 text-xs"
              >
                {isTesting ? (
                  <>
                    <span className="h-3 w-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                    <span>{t('ow.function.test_running')}</span>
                  </>
                ) : (
                  <>
                    <LucideIcon name="Play" size={14} className="fill-white" />
                    <span>{t('ow.function.test_run')}</span>
                  </>
                )}
              </button>
            </div>

            {/* Runner output & logs */}
            <div className="flex-1 bg-[var(--card,#020617)] p-5 flex flex-col text-xs font-mono overflow-y-auto text-[var(--card,#CBD5E1)] select-none">
              <h4 className="text-[10px] text-[var(--card,#64748B)] tracking-wider uppercase font-semibold mb-3 border-b border-[var(--card,#1E293B)] pb-2 flex justify-between items-center">
                <span>{t('ow.function.test_console_title')}</span>
                {testResult !== null && (
                  <span className="text-emerald-500 font-semibold flex items-center gap-1 bg-emerald-500/10 px-1.5 py-0.5 rounded">
                    <LucideIcon name="CheckCircle" size={11} /> SUCCESS
                  </span>
                )}
              </h4>

              {/* Logs area */}
              {testLogs.length === 0 ? (
                <div className="flex-1 flex flex-col justify-center items-center text-[var(--card,#64748B)] italic">
                  <LucideIcon name="Terminal" size={24} className="mb-2 text-[var(--card,#475569)]" />
                  <div>{t('ow.function.test_console_line1')}</div>
                  <div>{t('ow.function.test_console_line2')}</div>
                </div>
              ) : (
                <div className="flex-1 space-y-1.5 select-text">
                  {testLogs.map((log, i) => (
                    <div key={i} className={
                      log.includes('SUCCESS') || log.includes('🎉') ? 'text-emerald-400' :
                      log.includes('📥') ? 'text-blue-400' :
                      log.includes('⚙️') || log.includes('✅') ? 'text-[var(--card,#94A3B8)]' :
                      'text-[var(--card,#CBD5E1)]'
                    }>
                      {log}
                    </div>
                  ))}

                  {/* Output block */}
                  {testResult !== null && (
                    <div className="mt-4 p-4 rounded bg-[var(--card,#0B0F19)]/60 border border-[var(--card,#1E293B)] text-emerald-300">
                      <div className="text-[10px] text-[var(--card,#64748B)] mb-1 font-sans uppercase tracking-wider font-semibold">
                        {t('ow.function.test_returnValue')}
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
