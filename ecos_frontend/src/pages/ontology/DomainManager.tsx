/**
 * DomainManager — 业务域管理组件（含创建/编辑表单 + 域卡片网格）
 * @license Apache-2.0
 */
import React, { useState } from 'react';
import { AlertCircle, Check, Edit, Edit3, PlusCircle, Settings, Trash2, X } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { getDomainColorClasses } from './helpers';
import type { ObjectType, OntologyDomain } from '../../types/ontology';

interface DomainManagerProps {
  domains: OntologyDomain[];
  objectTypes: ObjectType[];
  selectedDomainFilter: string | null;
  onSelectDomainFilter: (id: string | null) => void;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objectTypes: ObjectType[]) => void;
  onQuickNavigate: (category: any, id: string) => void;
  onDeleteDomain: (domainId: string) => void;
}

export default function DomainManager({
  domains, objectTypes, selectedDomainFilter, onSelectDomainFilter,
  onUpdateDomains, onUpdateObjectTypes, onQuickNavigate, onDeleteDomain,
}: DomainManagerProps) {
  const [editingDomain, setEditingDomain] = useState<OntologyDomain | null>(null);
  const [formId, setFormId] = useState('');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formColor, setFormColor] = useState('blue');
  const [formAssignedObjects, setFormAssignedObjects] = useState<string[]>([]);
  const [isAddingNew, setIsAddingNew] = useState(false);
  const [formError, setFormError] = useState('');

  const handleStartAdd = () => { setEditingDomain(null); setFormId(''); setFormName(''); setFormDesc(''); setFormColor('blue'); setFormAssignedObjects([]); setIsAddingNew(true); setFormError(''); };
  const handleStartEdit = (domain: OntologyDomain) => { setEditingDomain(domain); setFormId(domain.id); setFormName(domain.displayName); setFormDesc(domain.description); setFormColor(domain.color); setFormAssignedObjects(objectTypes.filter(ot => ot.domainId === domain.id).map(ot => ot.id)); setIsAddingNew(true); setFormError(''); };
  const toggleObjectAssignment = (objId: string) => { setFormAssignedObjects(prev => prev.includes(objId) ? prev.filter(id => id !== objId) : [...prev, objId]); };

  const handleSaveDomain = (e: React.FormEvent) => {
    e.preventDefault(); setFormError('');
    if (!formName.trim()) { setFormError('业务域名称不能为空'); return; }
    const domainId = editingDomain ? editingDomain.id : (formId.trim().toLowerCase().replace(/[^a-z0-9_]/g, '') || `domain_${Date.now().toString().slice(-4)}`);
    if (!editingDomain && domains.some(d => d.id === domainId)) { setFormError(`业务域ID "${domainId}" 已存在，请使用唯一标识`); return; }
    const savedDomain: OntologyDomain = { id: domainId, displayName: formName.trim(), description: formDesc.trim(), color: formColor };
    const newDomains = editingDomain ? domains.map(d => d.id === editingDomain.id ? savedDomain : d) : [...domains, savedDomain];
    onUpdateDomains(newDomains);
    const updatedObjects = objectTypes.map(ot => {
      if (formAssignedObjects.includes(ot.id)) return { ...ot, domainId };
      if (ot.domainId === domainId) return { ...ot, domainId: undefined };
      return ot;
    });
    onUpdateObjectTypes(updatedObjects);
    setIsAddingNew(false); setEditingDomain(null);
  };

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
      <div className="flex justify-between items-center border-b border-slate-100 pb-3">
        <div><h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5"><Settings size={15} className="text-slate-500" />业务分级域维护中心 (Business Domain Manager)</h3><p className="text-[11px] text-slate-500 mt-0.5">创建、编辑和删除业务分级域，直观进行本体对象归宿分配。</p></div>
        {!isAddingNew && (<button onClick={handleStartAdd} className="bg-slate-900 hover:bg-slate-800 text-white text-xs px-3 py-1.5 rounded-lg font-medium flex items-center gap-1 shadow-xs"><PlusCircle size={14} />新建业务域分级</button>)}
      </div>

      {isAddingNew ? (
        <form onSubmit={handleSaveDomain} className="bg-slate-50 border border-slate-200 rounded-xl p-5 space-y-4">
          <div className="flex items-center justify-between border-b border-slate-200 pb-2">
            <h4 className="text-xs font-semibold text-slate-800 flex items-center gap-1.5"><Edit3 size={14} className="text-blue-500" /><span>{editingDomain ? `编辑业务域: ${editingDomain.displayName}` : '新建业务域分级'}</span></h4>
            <button type="button" onClick={() => { setIsAddingNew(false); setEditingDomain(null); }} className="text-slate-400 hover:text-slate-600 p-1 rounded hover:bg-slate-200/50"><X size={16} /></button>
          </div>
          {formError && (<div className="p-2.5 bg-red-50 border border-red-200 text-red-600 text-[11px] rounded-md flex items-center gap-1.5"><AlertCircle size={14} /><span>{formError}</span></div>)}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-1"><label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">唯一标识 ID</label><input type="text" disabled={!!editingDomain} value={formId} onChange={e => setFormId(e.target.value)} placeholder="如: flight_ops" className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white disabled:bg-slate-100 disabled:text-slate-400 font-mono" /></div>
            <div className="space-y-1"><label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">显示名称</label><input type="text" value={formName} onChange={e => setFormName(e.target.value)} placeholder="如: 运行控制域" className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white" /></div>
            <div className="space-y-1"><label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">代表色调</label><div className="flex items-center gap-1.5 py-1">{['blue','emerald','amber','purple','rose','indigo','slate'].map(color => { const isSelected = formColor === color; const classes = getDomainColorClasses(color); return (<button key={color} type="button" onClick={() => setFormColor(color)} className={`w-6 h-6 rounded-full border-2 transition-transform flex items-center justify-center ${classes.bg} ${isSelected ? 'border-slate-800 scale-110 shadow-xs' : 'border-slate-200 hover:scale-105'}`} title={color}><span className={`w-2.5 h-2.5 rounded-full ${classes.dot}`} /></button>); })}</div></div>
          </div>
          <div className="space-y-1"><label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">业务描述信息</label><textarea value={formDesc} onChange={e => setFormDesc(e.target.value)} placeholder="简述该业务域所承载的核心职能。" className="w-full h-16 px-3 py-1.5 text-xs border border-gray-300 rounded" /></div>
          <div className="space-y-2 border-t border-slate-200 pt-3"><label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">绑定关联的实体对象</label><div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 pt-1.5">{objectTypes.map(ot => { const isChecked = formAssignedObjects.includes(ot.id); const isMappedToOther = ot.domainId && ot.domainId !== formId; return (<div key={ot.id} onClick={() => !isMappedToOther && toggleObjectAssignment(ot.id)} className={`flex items-center justify-between p-2 rounded-lg border text-xs select-none transition-all ${isMappedToOther ? 'bg-slate-100 border-slate-200 text-slate-400 cursor-not-allowed opacity-60' : isChecked ? 'bg-blue-50 border-blue-300 text-blue-700 font-medium cursor-pointer' : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50 cursor-pointer'}`}><div className="flex items-center gap-2 truncate"><span className={`p-0.5 rounded border ${isChecked ? 'bg-blue-100 border-blue-200' : 'bg-slate-50'}`}><DynamicIcon name={ot.icon} size={12} /></span><span className="truncate">{ot.displayName}</span></div><span className={`w-3.5 h-3.5 rounded border flex items-center justify-center ${isChecked ? 'bg-blue-600 border-blue-600 text-white' : 'border-slate-300 bg-white'}`}>{isChecked && <Check size={10} />}</span></div>); })}</div></div>
          <div className="flex justify-end gap-2 border-t border-slate-200 pt-3"><button type="button" onClick={() => { setIsAddingNew(false); setEditingDomain(null); }} className="px-3.5 py-1.5 rounded-lg border border-slate-200 text-slate-600 text-xs font-semibold bg-white">取消</button><button type="submit" className="px-4 py-1.5 rounded-lg text-white text-xs font-semibold bg-blue-600 hover:bg-blue-700 shadow-sm">{editingDomain ? '保存修改' : '创建业务域'}</button></div>
        </form>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {domains.map(d => { const classes = getDomainColorClasses(d.color); const domainObjects = objectTypes.filter(ot => ot.domainId === d.id); return (<div key={d.id} className={`bg-white border border-slate-200 hover:border-slate-300 rounded-xl p-4 flex flex-col justify-between hover:shadow-2xs transition-all relative overflow-hidden ${classes.leftBorder}`}><div className="space-y-2"><div className="flex justify-between items-start"><div><h4 className="text-xs font-semibold text-slate-900">{d.displayName}</h4><span className="text-[10px] text-slate-400 font-mono">域识别ID: {d.id}</span></div><div className="flex items-center gap-1.5"><button onClick={() => handleStartEdit(d)} className="p-1 text-slate-400 hover:text-slate-600 rounded hover:bg-slate-100" title="编辑"><Edit size={12} /></button><button onClick={() => onDeleteDomain(d.id)} className="p-1 text-slate-400 hover:text-red-600 rounded hover:bg-red-50" title="删除"><Trash2 size={12} /></button></div></div><p className="text-[11px] text-slate-500 leading-relaxed min-h-[36px] line-clamp-2">{d.description || '暂无业务描述。'}</p></div><div className="border-t border-slate-100 pt-3 mt-3"><div className="flex items-center justify-between text-[10px] text-slate-400 font-semibold uppercase mb-1.5"><span>已关联对象 ({domainObjects.length})</span></div>{domainObjects.length > 0 ? (<div className="flex flex-wrap gap-1.5">{domainObjects.map(ot => (<div key={ot.id} onClick={() => onQuickNavigate('object', ot.id)} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-slate-200 bg-slate-50 text-slate-600 text-[10px] font-medium hover:border-blue-400 hover:bg-blue-50/20 cursor-pointer transition-colors"><DynamicIcon name={ot.icon} size={10} className="text-slate-400" /><span>{ot.displayName.split(' (')[0]}</span></div>))}</div>) : (<div className="text-[10px] text-slate-400 italic">暂无绑定的业务实体</div>)}</div></div>); })}
          {objectTypes.some(ot => !ot.domainId) && (<div className="bg-slate-50/50 border border-slate-200/60 rounded-xl p-4 flex flex-col justify-between border-dashed"><div className="space-y-1.5"><div className="flex justify-between items-center"><h4 className="text-xs font-semibold text-slate-700 flex items-center gap-1"><AlertCircle size={13} className="text-amber-500" /><span>未分类实体池</span></h4><span className="text-[10px] px-1.5 py-0.2 rounded-full bg-amber-100 text-amber-800 font-semibold font-mono">{objectTypes.filter(ot => !ot.domainId).length} 对象</span></div><p className="text-[11px] text-slate-400 leading-relaxed">某些本体实体还未绑定到具体的业务划分域中。</p></div><div className="border-t border-slate-200/50 pt-3 mt-3 flex flex-wrap gap-1.5">{objectTypes.filter(ot => !ot.domainId).map(ot => (<div key={ot.id} onClick={() => onQuickNavigate('object', ot.id)} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-slate-300/80 bg-white text-slate-500 text-[10px] hover:border-amber-400 hover:bg-amber-50/20 cursor-pointer transition-colors"><DynamicIcon name={ot.icon} size={10} /><span>{ot.displayName.split(' (')[0]}</span></div>))}</div></div>)}
        </div>
      )}
    </div>
  );
}
