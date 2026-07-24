/**
 * ParametersTab — 操作参数定义 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Box, Plus, X } from 'lucide-react';
import type { ActionType, ActionParameter, ObjectType, ActionParamDataType } from '../../../types/ontology';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

interface Props {
  actionType: ActionType;
  objectTypes: ObjectType[];
  newParamName: string; setNewParamName: (v: string) => void;
  newParamType: ActionParamDataType; setNewParamType: (v: ActionParamDataType) => void;
  newParamObjType: string; setNewParamObjType: (v: string) => void;
  handleAddParam: () => void;
  handleRemoveParam: (paramId: string) => void;
  handleParamFieldChange: (paramId: string, field: keyof ActionParameter, value: any) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function ParametersTab({
  actionType, objectTypes, newParamName, setNewParamName, newParamType, setNewParamType,
  newParamObjType, setNewParamObjType, handleAddParam, handleRemoveParam, handleParamFieldChange, onNavigateToObject,
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <p className={`text-xs ${styles.muted}`}>{t('ow.tab.parametersDesc')}</p>
        <div className="flex items-center gap-2">
          <input type="text" placeholder={t('ow.placeholder.newParamName')} value={newParamName} onChange={e => setNewParamName(e.target.value)}
            className={`px-3 py-1 text-xs border ${styles.cardBorder} rounded focus:border-blue-500 focus:outline-hidden`} />
          <select value={newParamType} onChange={e => setNewParamType(e.target.value as any)}
            className={`px-2 py-1 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden font-mono`}>
            <option value="string">string</option><option value="integer">integer</option><option value="decimal">decimal</option>
            <option value="boolean">boolean</option><option value="date">date</option><option value="object">{t('ow.action.paramTypeObject')}</option>
          </select>
          {newParamType === 'object' && (
            <select value={newParamObjType} onChange={e => setNewParamObjType(e.target.value)}
              className={`px-2 py-1 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden`}>
              {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName}</option>))}
            </select>
          )}
          <button onClick={handleAddParam} className={`${styles.accentBg} hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1`}>
            <Plus size={13} />{t('ow.btn.addParameter')}
          </button>
        </div>
      </div>
      <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden`}>
        <table className="w-full text-left border-collapse text-xs">
          <thead><tr className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-medium`}>
            <th className="py-2.5 px-4 w-12">{t('ow.label.required')}</th><th className="py-2.5 px-4">{t('ow.label.displayName')}</th><th className="py-2.5 px-4">{t('ow.label.paramVariableId')}</th>
            <th className="py-2.5 px-4">{t('ow.label.paramDataType')}</th><th className="py-2.5 px-4">{t('ow.label.objectBindingType')}</th><th className="py-2.5 px-4">{t('ow.label.paramDescription')}</th>
            <th className="py-2.5 px-4 text-center">{t('ow.label.actions')}</th>
          </tr></thead>
          <tbody className={`divide-y divide-gray-100 ${styles.cardTextMuted}`}>
            {actionType.parameters.map(param => (
              <tr key={param.id} className={`hover:${styles.appBg}`}>
                <td className="py-2.5 px-4"><input type="checkbox" checked={param.isRequired} onChange={e => handleParamFieldChange(param.id, 'isRequired', e.target.checked)} className={`rounded ${styles.cardBorder} ${styles.accentText} h-3.5 w-3.5`} /></td>
                <td className="py-2.5 px-4"><input type="text" value={param.displayName} onChange={e => handleParamFieldChange(param.id, 'displayName', e.target.value)} className={`font-medium ${styles.cardText} border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5`} /></td>
                <td className={`py-2.5 px-4 font-mono ${styles.muted}`}>{param.id}</td>
                <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{param.dataType}</td>
                <td className="py-2.5 px-4">{param.dataType === 'object' ? (<div className={`flex items-center gap-1 ${styles.accentText} font-semibold cursor-pointer`} onClick={() => param.objectTypeId && onNavigateToObject(param.objectTypeId)}><Box size={12} /><span>{objectTypes.find(o => o.id === param.objectTypeId)?.displayName || param.objectTypeId}</span></div>) : (<span className={`${styles.muted} font-mono`}>—</span>)}</td>
                <td className="py-2.5 px-4"><input type="text" value={param.description} onChange={e => handleParamFieldChange(param.id, 'description', e.target.value)} className={`${styles.muted} border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full`} placeholder={t('ow.placeholder.paramDescription')} /></td>
                <td className="py-2.5 px-4 text-center"><button onClick={() => handleRemoveParam(param.id)} className={`${styles.muted} hover:text-red-500 p-1 rounded`}><X size={14} /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
