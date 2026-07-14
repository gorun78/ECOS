/**
 * LayoutTab — 操作表单布局 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Layers, Trash } from 'lucide-react';
import type { ActionType } from '../../../types/ontology';

interface Props {
  actionType: ActionType;
  newSectionTitle: string; setNewSectionTitle: (v: string) => void;
  onUpdate: (updated: ActionType) => void;
}

export default function LayoutTab({ actionType, newSectionTitle, setNewSectionTitle, onUpdate }: Props) {
  const layout = actionType.formLayout || {
    sections: [{ title: '基本参数信息', parameterIds: actionType.parameters.map(p => p.id) }],
    buttonText: `确认执行: ${actionType.displayName}`
  };

  const updateLayout = (updatedLayout: typeof layout) => { onUpdate({ ...actionType, formLayout: updatedLayout }); };

  const handleAddSection = () => {
    if (!newSectionTitle.trim()) return;
    updateLayout({ ...layout, sections: [...layout.sections, { title: newSectionTitle.trim(), parameterIds: [] }] });
    setNewSectionTitle('');
  };

  const handleRemoveSection = (sectionIndex: number) => {
    const removedSec = layout.sections[sectionIndex];
    const firstSec = layout.sections[0];
    const updatedSections = layout.sections.filter((_, idx) => idx !== sectionIndex);
    if (firstSec && removedSec) updatedSections[0] = { ...updatedSections[0], parameterIds: Array.from(new Set([...updatedSections[0].parameterIds, ...removedSec.parameterIds])) };
    updateLayout({ ...layout, sections: updatedSections });
  };

  const handleAddParamToSection = (sectionIndex: number, paramId: string) => {
    const cleanedSections = layout.sections.map(sec => ({ ...sec, parameterIds: sec.parameterIds.filter(id => id !== paramId) }));
    cleanedSections[sectionIndex].parameterIds.push(paramId);
    updateLayout({ ...layout, sections: cleanedSections });
  };

  return (
    <div className="space-y-6">
      <p className="text-xs text-slate-500">配置当用户在主应用 Workshop 或 Object Explorer 中运行此操作时，所呈现的操作弹窗表单布局与文案。</p>
      <div className="grid grid-cols-3 gap-6">
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-5 space-y-4 col-span-1 h-fit">
          <h4 className="text-xs font-semibold text-slate-800">表单行为文案</h4>
          <div className="space-y-1"><label className="text-[10px] font-medium text-slate-600 block">提交按钮文字 (Submit Text)</label><input type="text" value={layout.buttonText || ''} onChange={e => updateLayout({ ...layout, buttonText: e.target.value })} className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden" placeholder="如：确认修改状态" /></div>
          <hr className="border-slate-200" />
          <div className="space-y-2">
            <h5 className="text-[11px] font-semibold text-slate-700">添加新的表单区块 (Section)</h5>
            <div className="flex gap-2"><input type="text" placeholder="区块名称 (e.g. 附加选项)" value={newSectionTitle} onChange={e => setNewSectionTitle(e.target.value)} className="flex-1 px-2 py-1 text-xs border border-gray-300 rounded focus:outline-hidden" /><button onClick={handleAddSection} className="bg-slate-900 text-white hover:bg-slate-800 text-[11px] px-2.5 py-1 rounded transition-colors">+ 区块</button></div>
          </div>
        </div>
        <div className="col-span-2 space-y-4">
          <h4 className="text-xs font-semibold text-slate-800">区块划分与字段归属</h4>
          <div className="space-y-4">
            {layout.sections.map((section, secIdx) => {
              const availableParams = actionType.parameters.filter(p => !section.parameterIds.includes(p.id));
              return (
                <div key={secIdx} className="border border-slate-200 rounded-xl p-4 bg-white space-y-3 shadow-2xs relative">
                  {secIdx > 0 && (<button onClick={() => handleRemoveSection(secIdx)} className="absolute top-4 right-4 text-slate-400 hover:text-red-500" title="移除此区块"><Trash size={13} /></button>)}
                  <div className="flex items-center gap-2 border-b border-slate-100 pb-2"><Layers size={13} className="text-blue-500" /><span className="text-xs font-semibold text-slate-800">{section.title}</span><span className="text-[10px] text-slate-400">({section.parameterIds.length} 字段)</span></div>
                  {section.parameterIds.length === 0 ? (
                    <div className="text-center py-4 text-slate-400 italic text-[11px]">该区块目前为空，请在下方选择参数移入此区。</div>
                  ) : (
                    <div className="space-y-1.5">
                      {section.parameterIds.map(paramId => {
                        const pDef = actionType.parameters.find(p => p.id === paramId);
                        if (!pDef) return null;
                        return (<div key={paramId} className="flex justify-between items-center bg-slate-50 px-3 py-1.5 rounded border border-slate-100 text-xs"><div className="flex items-center gap-2"><span className="font-mono text-slate-400 text-[10px]">[{pDef.dataType}]</span><span className="font-semibold text-slate-800">{pDef.displayName}</span><span className="text-slate-400 text-[10px] font-mono">({pDef.id})</span></div><span className="text-[10px] bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded-full font-mono">{pDef.isRequired ? '必填' : '选填'}</span></div>);
                      })}
                    </div>
                  )}
                  {availableParams.length > 0 && (
                    <div className="flex items-center justify-end gap-2 text-[11px] pt-1 border-t border-slate-100 mt-2"><span className="text-slate-400">划转字段入此区:</span><select onChange={e => { if (e.target.value) { handleAddParamToSection(secIdx, e.target.value); e.target.value = ''; } }} className="px-2 py-0.5 border border-gray-300 rounded bg-white text-[10px] focus:outline-hidden"><option value="">-- 选择可移入的变量 --</option>{availableParams.map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}</select></div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
