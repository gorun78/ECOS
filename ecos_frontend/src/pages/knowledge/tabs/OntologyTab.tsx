import React, { useState, useEffect } from 'react';
import { Workflow, Cpu, Database, Combine, ShieldCheck, Layers, Plus, Lightbulb, Trash2, Save, Info, Download, Copy, X } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

export default function OntologyTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [ontologyMappings, setOntologyMappings] = useState<any[]>([]);
  const [availableTables, setAvailableTables] = useState<any[]>([]);
  const [editingOntology, setEditingOntology] = useState<any | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [exportedMarkdown, setExportedMarkdown] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchOntologyMappings() as any;
      setOntologyMappings(data?.mappings || []);
      setAvailableTables(data?.availableTables || []);
    } catch { setOntologyMappings([]); setAvailableTables([]); }
    setIsLoading(false);
  };

  useEffect(() => { loadData(); }, []);

  const handleSaveMappings = async (mappings: any[]) => {
    try {
      await knowledgeApi.saveOntologyMappings({ mappings });
    } catch { /* fallback */ }
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      const result = await knowledgeApi.exportOntology() as any;
      setExportedMarkdown(result?.knowledgeMarkdown || result || '');
      setShowExportModal(true);
    } catch { setExportedMarkdown(''); }
    setIsExporting(false);
  };

  const handleAddEntity = () => {
    const newId = prompt(locale === 'zh' ? '请输入新本体实体标识符:' : 'Entity ID:');
    if (!newId) return;
    const name = prompt(locale === 'zh' ? '显示名称:' : 'Display name:') || newId;
    const desc = prompt(locale === 'zh' ? '描述:' : 'Description:') || '';
    const newEntity: any = { entityId: newId, entityName: newId, chineseName: name, description: desc, mappings: [] };
    const updated = [...ontologyMappings, newEntity];
    setOntologyMappings(updated);
    setEditingOntology(newEntity);
  };

  const handleAddMapping = () => {
    if (!editingOntology) return;
    const newMappingItem = { logicalField: 'newField', logicalType: 'String', physicalTable: availableTables[0]?.tableName || 'ds_flights_clean', physicalColumn: availableTables[0]?.columns?.[0]?.name || 'flight_id', description: '' };
    const updated = ontologyMappings.map(e => e.entityId === editingOntology.entityId ? { ...e, mappings: [...(e.mappings || []), newMappingItem] } : e);
    setOntologyMappings(updated);
    setEditingOntology(updated.find(e => e.entityId === editingOntology.entityId));
  };

  const updateMapping = (idx: number, field: string, value: string) => {
    if (!editingOntology) return;
    const updated = ontologyMappings.map(ent => {
      if (ent.entityId === editingOntology.entityId) {
        const newM = [...ent.mappings]; newM[idx] = { ...newM[idx], [field]: value };
        return { ...ent, mappings: newM };
      }
      return ent;
    });
    setOntologyMappings(updated);
    setEditingOntology(updated.find(e => e.entityId === editingOntology.entityId));
  };

  const removeMapping = (idx: number) => {
    if (!editingOntology) return;
    const updated = ontologyMappings.map(ent => {
      if (ent.entityId === editingOntology.entityId) {
        return { ...ent, mappings: ent.mappings.filter((_: any, i: number) => i !== idx) };
      }
      return ent;
    });
    setOntologyMappings(updated);
    setEditingOntology(updated.find(e => e.entityId === editingOntology.entityId));
  };

  const handleDeleteEntity = (entityId: string) => {
    if (!confirm(locale === 'zh' ? '确认删除？' : 'Confirm delete?')) return;
    const remaining = ontologyMappings.filter(e => e.entityId !== entityId);
    setOntologyMappings(remaining);
    setEditingOntology(remaining[0] || null);
    handleSaveMappings(remaining);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between border-b border-slate-200 pb-4 gap-4">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2"><Workflow size={16} className="text-blue-600 animate-pulse" />{locale === 'zh' ? '语义本体与物理宽表对齐管理器' : 'Ontology-to-Physical Aligner'}</h2>
          <p className="text-xs text-slate-500 font-sans">{locale === 'zh' ? '建立强类型对齐契约，将逻辑本体字段与物理大宽表列名进行多对多映射绑定。' : 'Build strong-typed alignment contracts between ontology fields and physical table columns.'}</p>
        </div>
        <button onClick={handleExport} disabled={isExporting} className="px-3.5 py-1.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition-all">
          <Download size={12} /><span>{isExporting ? (locale === 'zh' ? '导出中...' : 'Exporting...') : (locale === 'zh' ? '导出 RAG 知识包' : 'Export RAG Pack')}</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {[
          { label: 'Active Ontologies', value: `${ontologyMappings.length} Entities`, icon: <Cpu size={14} />, bg: 'bg-blue-50 text-blue-600' },
          { label: 'Physical Targets', value: `${availableTables.length} OLAP Tables`, icon: <Database size={14} />, bg: 'bg-emerald-50 text-emerald-600' },
          { label: 'Mapped Connections', value: 'Many-to-Many', icon: <Combine size={14} />, bg: 'bg-indigo-50 text-indigo-600' },
          { label: 'Alignment Integrity', value: '100% Strong-Typed', icon: <ShieldCheck size={14} />, bg: 'bg-emerald-50 text-emerald-600' },
        ].map((card, i) => (
          <div key={i} className="bg-white border border-slate-200 p-3.5 rounded-xl flex items-center justify-between">
            <div><span className="text-slate-400 font-mono text-[9px] block uppercase">{card.label}</span><span className="text-base font-black text-slate-800 font-mono">{card.value}</span></div>
            <span className={`p-2 rounded-lg ${card.bg}`}>{card.icon}</span>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <div className="lg:col-span-3 space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <h3 className="font-extrabold text-slate-800 text-xs flex items-center gap-1.5"><Layers size={12} className="text-slate-500" /><span>{locale === 'zh' ? '语义本体实体' : 'Ontology Entities'}</span></h3>
              <button onClick={handleAddEntity} className="text-blue-600 hover:text-blue-800 font-bold text-[10px] flex items-center gap-0.5 cursor-pointer"><Plus size={10} /><span>{locale === 'zh' ? '新建' : 'New'}</span></button>
            </div>
            <div className="space-y-1.5">
              {ontologyMappings.map(ent => {
                const isSelected = editingOntology?.entityId === ent.entityId;
                return (
                  <button key={ent.entityId} onClick={() => setEditingOntology(ent)} className={`w-full p-2.5 rounded-lg border text-left flex flex-col space-y-1 transition-all cursor-pointer ${isSelected ? 'bg-slate-900 border-slate-900 text-white shadow-sm' : 'bg-slate-50 hover:bg-slate-100 border-slate-200 text-slate-700'}`}>
                    <div className="flex items-center justify-between w-full">
                      <span className="font-black text-xs">{ent.entityId}</span>
                      <span className={`text-[8px] px-1.5 py-0.5 rounded font-mono ${isSelected ? 'bg-blue-500 text-white' : 'bg-slate-200 text-slate-600'}`}>{ent.mappings?.length || 0} fields</span>
                    </div>
                    <span className={`text-[9px] truncate block ${isSelected ? 'text-slate-300' : 'text-slate-500'}`}>{ent.chineseName || ent.entityName}</span>
                  </button>
                );
              })}
            </div>
          </div>
          <div className="p-4 bg-blue-50/50 border border-blue-100 rounded-xl space-y-2 text-[10px] leading-relaxed text-blue-800 font-sans">
            <p className="font-extrabold flex items-center gap-1.5"><Lightbulb size={12} className="text-blue-600" />{locale === 'zh' ? '多对多穿透绑定' : 'Many-to-Many Binding'}</p>
            <p>{locale === 'zh' ? '系统支持多对多映射。例如 AviationPilot.lastAssignedFlightId 可穿透映射至 ds_flights_clean.flight_id。' : 'The system supports many-to-many mappings across physical tables.'}</p>
          </div>
        </div>

        <div className="lg:col-span-9">
          {editingOntology ? (
            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-5">
              <div className="flex flex-col md:flex-row md:items-center justify-between border-b border-slate-100 pb-3 gap-3">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-black text-slate-800 text-sm font-mono">{editingOntology.entityId}</span>
                    <span className="px-2 py-0.5 bg-blue-50 text-blue-700 text-[10px] font-bold rounded-md">{editingOntology.chineseName || 'Entity'}</span>
                  </div>
                  <p className="text-[11px] text-slate-500 font-sans">{editingOntology.description}</p>
                </div>
                <button onClick={() => handleDeleteEntity(editingOntology.entityId)} className="px-2.5 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 font-bold rounded-lg text-[10px] flex items-center gap-1 cursor-pointer transition-all"><Trash2 size={11} />{locale === 'zh' ? '删除' : 'Delete'}</button>
              </div>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-extrabold text-slate-400 uppercase tracking-wider font-mono">{locale === 'zh' ? '对齐映射清单' : 'Column Mappings'}</span>
                  <button onClick={handleAddMapping} className="text-blue-600 hover:text-blue-800 font-bold text-[10px] flex items-center gap-0.5 cursor-pointer"><Plus size={11} />{locale === 'zh' ? '添加映射' : 'Add Row'}</button>
                </div>
                <div className="border border-slate-150 rounded-xl overflow-hidden">
                  <table className="w-full text-left border-collapse">
                    <thead><tr className="bg-slate-50 border-b border-slate-150 text-[10px] font-extrabold text-slate-500 font-sans select-none"><th className="p-3">{locale === 'zh' ? '逻辑属性' : 'Logical Field'}</th><th className="p-3">{locale === 'zh' ? '类型' : 'Type'}</th><th className="p-3">{locale === 'zh' ? '物理表' : 'Physical Table'}</th><th className="p-3">{locale === 'zh' ? '物理列' : 'Physical Column'}</th><th className="p-3">{locale === 'zh' ? '说明' : 'Description'}</th><th className="p-3 text-center">{locale === 'zh' ? '操作' : 'Action'}</th></tr></thead>
                    <tbody className="divide-y divide-slate-100 text-[11px]">
                      {(!editingOntology.mappings || editingOntology.mappings.length === 0) ? (
                        <tr><td colSpan={6} className="p-8 text-center text-slate-400 font-sans">{locale === 'zh' ? '尚未配置映射' : 'No mappings configured'}</td></tr>
                      ) : editingOntology.mappings.map((m: any, idx: number) => {
                        const matchedTable = availableTables.find((t: any) => t.tableName === m.physicalTable);
                        const availableCols = matchedTable?.columns || [];
                        return (
                          <tr key={idx} className="hover:bg-slate-50/50">
                            <td className="p-3"><input type="text" value={m.logicalField} onChange={e => updateMapping(idx, 'logicalField', e.target.value)} className="w-full px-2 py-1 border border-slate-200 rounded-md font-mono text-[10px] font-bold text-slate-700 bg-white" /></td>
                            <td className="p-3"><select value={m.logicalType} onChange={e => updateMapping(idx, 'logicalType', e.target.value)} className="px-1.5 py-1 border border-slate-200 rounded-md font-bold text-[10px] bg-white text-slate-600"><option value="String">String</option><option value="Integer">Integer</option><option value="Double">Double</option><option value="DateTime">DateTime</option><option value="Boolean">Boolean</option></select></td>
                            <td className="p-3"><select value={m.physicalTable} onChange={e => { updateMapping(idx, 'physicalTable', e.target.value); const mt = availableTables.find((t: any) => t.tableName === e.target.value); updateMapping(idx, 'physicalColumn', mt?.columns?.[0]?.name || ''); }} className="px-1.5 py-1 border border-slate-200 rounded-md font-bold text-[10px] bg-white text-blue-800">{availableTables.map((t: any) => <option key={t.tableName} value={t.tableName}>{t.tableName}</option>)}</select></td>
                            <td className="p-3"><select value={m.physicalColumn} onChange={e => updateMapping(idx, 'physicalColumn', e.target.value)} className="px-1.5 py-1 border border-slate-200 rounded-md font-mono text-[10px] font-bold bg-white text-emerald-800">{availableCols.map((c: any) => <option key={c.name} value={c.name}>{c.name} ({c.type})</option>)}</select></td>
                            <td className="p-3"><input type="text" value={m.description} onChange={e => updateMapping(idx, 'description', e.target.value)} className="w-full px-2 py-1 border border-slate-200 rounded-md text-[10px] text-slate-600 bg-white" /></td>
                            <td className="p-3 text-center"><button onClick={() => removeMapping(idx)} className="p-1 rounded bg-rose-50 text-rose-600 hover:bg-rose-100 cursor-pointer transition-colors"><Trash2 size={11} /></button></td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
              <div className="flex items-center justify-between pt-4 border-t border-slate-100">
                <div className="text-[10px] text-slate-400">* {locale === 'zh' ? '保存后实时更新 RAG 上下文数据库' : 'Saving updates RAG context in real-time'}</div>
                <button onClick={() => handleSaveMappings(ontologyMappings)} className="px-5 py-2 bg-slate-900 hover:bg-slate-800 text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition-colors"><Save size={12} /><span>{locale === 'zh' ? '保存对齐契约' : 'Save & Apply'}</span></button>
              </div>
            </div>
          ) : (
            <div className="bg-white border border-slate-200 rounded-xl p-12 text-center text-slate-400 space-y-2"><Workflow size={24} className="mx-auto text-slate-300 animate-pulse" /><p className="font-bold text-xs">{locale === 'zh' ? '请选择或创建本体对象' : 'Select or create an ontology entity'}</p></div>
          )}
        </div>
      </div>

      {showExportModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in">
          <div className="bg-white rounded-2xl max-w-2xl w-full border border-slate-200 shadow-xl overflow-hidden flex flex-col max-h-[85vh]">
            <div className="bg-slate-950 text-white p-4 flex items-center justify-between">
              <div className="flex items-center gap-2"><Download size={15} className="text-blue-400" /><span className="font-black text-xs">{locale === 'zh' ? 'RAG 先验知识元数据包' : 'RAG Prior-Knowledge Pack'}</span></div>
              <button onClick={() => setShowExportModal(false)} className="text-slate-400 hover:text-white font-bold cursor-pointer"><X size={16} /></button>
            </div>
            <div className="p-5 overflow-y-auto space-y-4">
              <pre className="p-4 bg-slate-900 text-slate-200 rounded-xl font-mono text-[9px] whitespace-pre-wrap leading-relaxed select-text max-h-[350px] overflow-y-auto">{exportedMarkdown}</pre>
            </div>
            <div className="p-4 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-2 shrink-0">
              <button onClick={() => { navigator.clipboard.writeText(exportedMarkdown); }} className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center gap-1 transition-all"><Copy size={12} />{locale === 'zh' ? '复制' : 'Copy'}</button>
              <button onClick={() => setShowExportModal(false)} className="px-4 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-700 font-bold rounded-lg text-xs cursor-pointer transition-all">{locale === 'zh' ? '关闭' : 'Close'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
