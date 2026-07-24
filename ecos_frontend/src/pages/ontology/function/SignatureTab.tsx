/**
 * SignatureTab — 函数签名与参数定义 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Plus, X } from 'lucide-react';
import type { FunctionType, FunctionParameter, ObjectType } from '../../../types/ontology';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

interface SignatureTabProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  handleFieldChange: (key: keyof FunctionType, value: any) => void;
  handleAddParam: () => void;
  handleRemoveParam: (name: string) => void;
  handleParamFieldChange: (name: string, field: keyof FunctionParameter, value: any) => void;
  newParamName: string;
  setNewParamName: (v: string) => void;
  newParamType: string;
  setNewParamType: (v: string) => void;
  newParamObjType: string;
  setNewParamObjType: (v: string) => void;
}

export default function SignatureTab({
  func, objectTypes, handleFieldChange,
  handleAddParam, handleRemoveParam, handleParamFieldChange,
  newParamName, setNewParamName, newParamType, setNewParamType,
  newParamObjType, setNewParamObjType,
}: SignatureTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="flex-1 overflow-y-auto p-6 space-y-6">
      <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
        <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.funcReturnOutput')}</h3>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div className="space-y-1">
            <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.returnType')}</label>
            <select value={func.returnType} onChange={e => handleFieldChange('returnType', e.target.value)}
              className={`px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} w-full font-mono`}>
              <option value="string">{t('ow.func.returnString')}</option>
              <option value="integer">{t('ow.func.returnInteger')}</option>
              <option value="decimal">{t('ow.func.returnDecimal')}</option>
              <option value="boolean">{t('ow.func.returnBoolean')}</option>
              <option value="date">{t('ow.func.returnDate')}</option>
              <option value="timestamp">{t('ow.func.returnTimestamp')}</option>
              <option value="ObjectType">{t('ow.func.returnObjectType')}</option>
              <option value="ObjectTypeSet">{t('ow.func.returnObjectTypeSet')}</option>
            </select>
          </div>
          {(func.returnType === 'ObjectType' || func.returnType === 'ObjectTypeSet') && (
            <div className="space-y-1">
              <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.returnObjectType')}</label>
              <select value={func.returnObjectTypeId || ''} onChange={e => handleFieldChange('returnObjectTypeId', e.target.value)}
                className={`px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} w-full`}>
                {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>))}
              </select>
            </div>
          )}
        </div>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div className="space-y-1">
            <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.funcApiName')}</label>
            <input type="text" value={func.apiName} onChange={e => handleFieldChange('apiName', e.target.value)}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} font-mono focus:outline-hidden`} />
          </div>
          <div className="space-y-1">
            <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.associatedCoreObject')}</label>
            <select value={func.associatedObjectType || ''} onChange={e => handleFieldChange('associatedObjectType', e.target.value)}
              className={`px-2.5 py-1.5 border ${styles.cardBorder} rounded ${styles.cardBg} w-full`}>
              <option value="">{t('ow.placeholder.noAssociatedObject')}</option>
              {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>))}
            </select>
          </div>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.funcInputParams')}</h3>
          <div className="flex items-center gap-2">
            <input type="text" placeholder="新参数变量名 (e.g. airportCode)" value={newParamName}
              onChange={e => setNewParamName(e.target.value)}
              className={`px-2.5 py-1 text-xs border ${styles.cardBorder} rounded focus:border-blue-500 focus:outline-hidden font-mono`} />
            <select value={newParamType} onChange={e => setNewParamType(e.target.value)}
              className={`px-2 py-1 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden font-mono`}>
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
              <select value={newParamObjType} onChange={e => setNewParamObjType(e.target.value)}
                className={`px-2 py-1 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden`}>
                {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName}</option>))}
              </select>
            )}
            <button onClick={handleAddParam}
              className={`${styles.accentBg} hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1`}>
              <Plus size={13} />添加参数
            </button>
          </div>
        </div>
        <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden`}>
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-medium`}>
                <th className="py-2.5 px-4 w-12">{t('ow.label.required')}</th>
                <th className="py-2.5 px-4">{t('ow.label.variableId')}</th>
                <th className="py-2.5 px-4">{t('ow.label.paramType')}</th>
                <th className="py-2.5 px-4">{t('ow.label.boundEntityType')}</th>
                <th className="py-2.5 px-4">{t('ow.label.paramBusinessDesc')}</th>
                <th className="py-2.5 px-4 text-center">{t('ow.label.actions')}</th>
              </tr>
            </thead>
            <tbody className={`divide-y divide-gray-100 ${styles.cardTextMuted}`}>
              {func.parameters.length === 0 ? (
                <tr><td colSpan={6} className={`text-center py-8 ${styles.muted} italic`}>{t('ow.empty.noFuncParams')}</td></tr>
              ) : (
                func.parameters.map(p => (
                  <tr key={p.name} className={`hover:${styles.appBg}`}>
                    <td className="py-2.5 px-4">
                      <input type="checkbox" checked={p.isRequired}
                        onChange={e => handleParamFieldChange(p.name, 'isRequired', e.target.checked)}
                        className={`rounded ${styles.cardBorder} ${styles.accentText} focus:ring-blue-500 h-3.5 w-3.5`} />
                    </td>
                    <td className={`py-2.5 px-4 font-mono font-medium ${styles.cardText}`}>{p.name}</td>
                    <td className={`py-2.5 px-4 font-mono ${styles.muted}`}>{p.dataType}</td>
                    <td className="py-2.5 px-4">
                      {(p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                        <select value={p.objectTypeId || ''}
                          onChange={e => handleParamFieldChange(p.name, 'objectTypeId', e.target.value)}
                          className={`px-2 py-0.5 border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden`}>
                          {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName}</option>))}
                        </select>
                      ) : (<span className={`${styles.muted} font-mono`}>—</span>)}
                    </td>
                    <td className="py-2.5 px-4">
                      <input type="text" value={p.description}
                        onChange={e => handleParamFieldChange(p.name, 'description', e.target.value)}
                        className={`${styles.muted} border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full`}
                        placeholder={t('ow.placeholder.paramDescription')} />
                    </td>
                    <td className="py-2.5 px-4 text-center">
                      <button onClick={() => handleRemoveParam(p.name)} className={`${styles.muted} hover:text-red-500 p-1 rounded`}><X size={14} /></button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
