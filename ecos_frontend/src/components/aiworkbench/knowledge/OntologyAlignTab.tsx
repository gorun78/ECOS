/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ThemeStyles } from '../../../components/ThemeContext';
import * as Icons from 'lucide-react';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface OntologyAlignTabProps {
  styles: ThemeStyles;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  ontologyMappings: any[];
  setOntologyMappings: (v: any[]) => void;
  availableTables: any[];
  editingOntology: any | null;
  setEditingOntology: (v: any | null) => void;
  isExporting: boolean;
  handleExportOntology: () => Promise<void>;
  exportedMarkdown: string;
  showExportModal: boolean;
  setShowExportModal: (v: boolean) => void;
  handleSaveOntologyMappings: (mappings: any[]) => Promise<void>;
}

export default function OntologyAlignTab({
  styles,
  showToast,
  ontologyMappings,
  setOntologyMappings,
  availableTables,
  editingOntology,
  setEditingOntology,
  isExporting,
  handleExportOntology,
  exportedMarkdown,
  showExportModal,
  setShowExportModal,
  handleSaveOntologyMappings,
}: OntologyAlignTabProps) {
  const { t } = useLanguage();
  return (
    <div className="space-y-6">
      
      {/* Header */}
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-4 gap-4`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <Icon name="Workflow" size={16} className="text-blue-600 animate-pulse" />
            <span>语义本体与分布式物理宽表对齐管理器 (Ontology-to-Physical Column Aligner)</span>
          </h2>
          <p className={`text-xs ${styles.cardTextMuted} font-sans`}>
            建立强类型对齐契约：将逻辑本体字段 (Ontology Properties) 与 Doris / PostgreSQL 物理大宽表列名进行多对多映射绑定，并编译导出为 RAG 模型检索的无幻觉先验知识图谱。
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            onClick={handleExportOntology}
            disabled={isExporting}
            className="px-3.5 py-1.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition-all"
          >
            <Icon name="Download" size={12} />
            <span>{isExporting ? '编译导出中...' : '导出 RAG 先验知识元数据包'}</span>
          </button>
        </div>
      </div>

      {/* Quick stats cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
          <div>
            <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Active Ontologies</span>
            <span className={`text-base font-black ${styles.cardText} font-mono`}>{ontologyMappings.length} Entities</span>
          </div>
          <span className="p-2 bg-blue-50 text-blue-600 rounded-lg"><Icon name="Cpu" size={14} /></span>
        </div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
          <div>
            <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Physical Targets</span>
            <span className={`text-base font-black ${styles.cardText} font-mono`}>{availableTables.length} OLAP Tables</span>
          </div>
          <span className="p-2 bg-emerald-50 text-emerald-600 rounded-lg"><Icon name="Database" size={14} /></span>
        </div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
          <div>
            <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Mapped Connections</span>
            <span className={`text-base font-black ${styles.cardText} font-mono`}>Many-to-Many</span>
          </div>
          <span className="p-2 bg-indigo-50 text-indigo-600 rounded-lg"><Icon name="Combine" size={14} /></span>
        </div>
        <div className={`${styles.cardBg} border ${styles.cardBorder} p-3.5 rounded-xl flex items-center justify-between`}>
          <div>
            <span className={`${styles.cardTextMuted} font-mono text-[9px] block uppercase`}>Alignment Integrity</span>
            <span className="text-base font-black text-emerald-600 font-mono">100% Strong-Typed</span>
          </div>
          <span className="p-2 bg-emerald-50 text-emerald-600 rounded-lg"><Icon name="ShieldCheck" size={14} /></span>
        </div>
      </div>

      {/* Main Manager Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        
        {/* Left Side: Ontology Entities List (3 cols) */}
        <div className="lg:col-span-3 space-y-4">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                <Icon name="Layers" size={12} className={`${styles.cardTextMuted}`} />
                <span>{t('aiworkbench.knowledge.ontology.entityLabel')} (Ontology Entities)</span>
              </h3>
              <button
                onClick={() => {
                  const newId = prompt('请输入新本体实体标识符 (如 AircraftMaintenance):');
                  if (newId) {
                    const name = prompt('请输入该本体实体的中文显示名称 (如 飞机维保本体):') || newId;
                    const desc = prompt('请输入本体描述:') || '{t('aiworkbench.knowledge.ontology.newEntityTitle')}';
                    const newEntity: any = {
                      entityId: newId,
                      entityName: newId,
                      chineseName: name,
                      description: desc,
                      mappings: []
                    };
                    const updated = [...ontologyMappings, newEntity];
                    setOntologyMappings(updated);
                    setEditingOntology(newEntity);
                    showToast?.('success', `本地成功创建本体 ${newId}，请为其配置物理列映射规则。`);
                  }
                }}
                className="text-blue-600 hover:text-blue-800 font-bold text-[10px] flex items-center gap-0.5 cursor-pointer"
              >
                <Icon name="Plus" size={10} />
                <span>{t('aiworkbench.knowledge.ontology.newBtn')}</span>
              </button>
            </div>

            <div className="space-y-1.5">
              {ontologyMappings.map(ent => {
                const isSelected = editingOntology?.entityId === ent.entityId;
                return (
                  <button
                    key={ent.entityId}
                    onClick={() => setEditingOntology(ent)}
                    className={`w-full p-2.5 rounded-lg border text-left flex flex-col space-y-1 transition-all cursor-pointer ${
                      isSelected
                        ? '${styles.accentBg} ${styles.accentBorder} text-white shadow-sm'
                        : 'styles.inputBg hover:styles.appBg styles.cardBorder styles.cardText'
                    }`}
                  >
                    <div className="flex items-center justify-between w-full">
                      <span className="font-black text-xs">{ent.entityId}</span>
                      <span className={`text-[8px] px-1.5 py-0.5 rounded font-mono ${
                        isSelected ? 'bg-blue-500 text-white' : 'styles.inputBg styles.cardTextMuted'
                      }`}>
                        {ent.mappings?.length || 0} fields
                      </span>
                    </div>
                    <span className={`text-[9px] truncate block ${isSelected ? 'styles.cardTextMuted' : 'styles.cardTextMuted'}`}>
                      {ent.chineseName || ent.entityName}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Helpful Tip */}
          <div className="p-4 bg-blue-50/50 border border-blue-100 rounded-xl space-y-2 text-[10px] leading-relaxed text-blue-800 font-sans">
            <p className="font-extrabold flex items-center gap-1.5">
              <Icon name="Lightbulb" size={12} className="text-blue-600" />
              <span>多对多穿透绑定</span>
            </p>
            <p>
              系统完美支持多对多映射。例如，您可以将逻辑 <code>AviationPilot</code> 实体的 <code>lastAssignedFlightId</code> 穿透映射至另一个物理表 <code>ds_flights_clean.flight_id</code> 中，形成物理跨源级联，确保智能体查询可以轻松跨表对齐。
            </p>
          </div>
        </div>

        {/* Right Side: Properties & Column Mapping Table (9 cols) */}
        <div className="lg:col-span-9">
          {editingOntology ? (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-5`}>
              
              {/* Selected Entity Info header */}
              <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-3 gap-3`}>
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className={`font-black ${styles.cardText} text-sm font-mono`}>{editingOntology.entityId}</span>
                    <span className="px-2 py-0.5 bg-blue-50 text-blue-700 text-[10px] font-bold rounded-md">
                      {editingOntology.chineseName || '{t('aiworkbench.knowledge.ontology.entityLogicalLabel')}'}
                    </span>
                  </div>
                  <p className={`text-[11px] ${styles.cardTextMuted} font-sans`}>{editingOntology.description}</p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      if (confirm(`确认要删除 ${editingOntology.entityId} 语义实体及其全部列级映射吗？`,)} {
                        const remaining = ontologyMappings.filter(e => e.entityId !== editingOntology.entityId);
                        setOntologyMappings(remaining);
                        setEditingOntology(remaining[0] || null);
                        handleSaveOntologyMappings(remaining);
                      }
                    }}
                    className="px-2.5 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 hover:border-rose-300 font-bold rounded-lg text-[10px] flex items-center gap-1 cursor-pointer transition-all"
                  >
                    <Icon name="Trash2" size={11} />
                    <span>{t('aiworkbench.knowledge.ontology.deleteEntity')}</span>
                  </button>
                </div>
              </div>

              {/* Mappings Table */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono`}>{t('aiworkbench.knowledge.ontology.mappingList')} (Logical-to-Physical Column Mappings)</span>
                  <button
                    onClick={() => {
                      const newMappingItem = {
                        logicalField: 'newField',
                        logicalType: 'String',
                        physicalTable: availableTables[0]?.tableName || 'ds_flights_clean',
                        physicalColumn: availableTables[0]?.columns[0]?.name || 'flight_id',
                        description: '{t('aiworkbench.knowledge.ontology.newMapping')}'
                      };
                      const updatedMappings = ontologyMappings.map(e => {
                        if (e.entityId === editingOntology.entityId) {
                          return {
                            ...e,
                            mappings: [...(e.mappings || []), newMappingItem]
                          };
                        }
                        return e;
                      });
                      setOntologyMappings(updatedMappings);
                      setEditingOntology(updatedMappings.find(e => e.entityId === editingOntology.entityId,)};
                    }}
                    className="text-blue-600 hover:text-blue-800 font-bold text-[10px] flex items-center gap-0.5 cursor-pointer"
                  >
                    <Icon name="Plus" size={11} />
                    <span>{t('aiworkbench.knowledge.ontology.addMappingBtn')} (Add Row)</span>
                  </button>
                </div>

                {/* Actual Table */}
                <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden`}>
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className={`${styles.inputBg} border-b ${styles.cardBorder} text-[10px] font-extrabold ${styles.cardTextMuted} font-sans select-none`}>
                        <th className="p-3">逻辑属性名 (Logical Field)</th>
                        <th className="p-3">数据类型 (Type)</th>
                        <th className="p-3">物理库表 (Physical Table)</th>
                        <th className="p-3">物理列字段 (Physical Column)</th>
                        <th className="p-3">注释及说明 (Description)</th>
                        <th className="p-3 text-center">操作</th>
                      </tr>
                    </thead>
                    <tbody className={`divide-y ${styles.cardBorder} text-[11px]`}>
                      {(!editingOntology.mappings || editingOntology.mappings.length === 0) ? (
                        <tr>
                          <td colSpan={6} className={`p-8 text-center ${styles.cardTextMuted} font-sans`}>
                            ⚠️ 尚未为此实体配置列级对齐。请点击右上角「{t('aiworkbench.knowledge.ontology.addMappingBtn')}」开始绑定。
                          </td>
                        </tr>
                      ) : (
                        editingOntology.mappings.map((m: any, idx: number) => {
                          // Find available columns for the selected physicalTable
                          const matchedTable = availableTables.find(t => t.tableName === m.physicalTable);
                          const availableCols = matchedTable ? matchedTable.columns : [];

                          return (
                            <tr key={idx} className={`hover:${styles.appBg}`}>
                              {/* Logical Field Name */}
                              <td className="p-3">
                                <input
                                  type="text"
                                  value={m.logicalField}
                                  onChange={(e) => {
                                    const nextVal = e.target.value;
                                    const updated = ontologyMappings.map(ent => {
                                      if (ent.entityId === editingOntology.entityId) {
                                        const newM = [...ent.mappings];
                                        newM[idx] = { ...newM[idx], logicalField: nextVal };
                                        return { ...ent, mappings: newM };
                                      }
                                      return ent;
                                    });
                                    setOntologyMappings(updated);
                                    setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId,)};
                                  }}
                                  className={`w-full px-2 py-1 border ${styles.cardBorder} rounded-md font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.cardBg}`}
                                />
                              </td>

                              {/* Logical Type */}
                              <td className="p-3">
                                <select
                                  value={m.logicalType}
                                  onChange={(e) => {
                                    const nextVal = e.target.value;
                                    const updated = ontologyMappings.map(ent => {
                                      if (ent.entityId === editingOntology.entityId) {
                                        const newM = [...ent.mappings];
                                        newM[idx] = { ...newM[idx], logicalType: nextVal };
                                        return { ...ent, mappings: newM };
                                      }
                                      return ent;
                                    });
                                    setOntologyMappings(updated);
                                    setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId,)};
                                  }}
                                  className={`px-1.5 py-1 border ${styles.cardBorder} rounded-md font-bold text-[10px] ${styles.cardBg} ${styles.cardTextMuted}`}
                                >
                                  <option value="String">String</option>
                                  <option value="Integer">Integer</option>
                                  <option value="Double">Double</option>
                                  <option value="DateTime">DateTime</option>
                                  <option value="Boolean">Boolean</option>
                                </select>
                              </td>

                              {/* Physical Table Selection */}
                              <td className="p-3">
                                <select
                                  value={m.physicalTable}
                                  onChange={(e) => {
                                    const nextTable = e.target.value;
                                    const matchedT = availableTables.find(t => t.tableName === nextTable);
                                    const firstCol = matchedT?.columns[0]?.name || 'flight_id';

                                    const updated = ontologyMappings.map(ent => {
                                      if (ent.entityId === editingOntology.entityId) {
                                        const newM = [...ent.mappings];
                                        newM[idx] = { ...newM[idx], physicalTable: nextTable, physicalColumn: firstCol };
                                        return { ...ent, mappings: newM };
                                      }
                                      return ent;
                                    });
                                    setOntologyMappings(updated);
                                    setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId,)};
                                  }}
                                  className={`px-1.5 py-1 border ${styles.cardBorder} rounded-md font-bold text-[10px] ${styles.cardBg} text-blue-800`}
                                >
                                  {availableTables.map(t => (
                                    <option key={t.tableName} value={t.tableName}>{t.tableName}</option>
                                  )}
                                </select>
                              </td>

                              {/* Physical Column Selection */}
                              <td className="p-3">
                                <select
                                  value={m.physicalColumn}
                                  onChange={(e) => {
                                    const nextCol = e.target.value;
                                    const updated = ontologyMappings.map(ent => {
                                      if (ent.entityId === editingOntology.entityId) {
                                        const newM = [...ent.mappings];
                                        newM[idx] = { ...newM[idx], physicalColumn: nextCol };
                                        return { ...ent, mappings: newM };
                                      }
                                      return ent;
                                    });
                                    setOntologyMappings(updated);
                                    setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId,)};
                                  }}
                                  className={`px-1.5 py-1 border ${styles.cardBorder} rounded-md font-mono text-[10px] font-bold ${styles.cardBg} text-emerald-800`}
                                >
                                  {availableCols.map((c: any) => (
                                    <option key={c.name} value={c.name}>{c.name} ({c.type})</option>
                                  )}
                                </select>
                              </td>

                              {/* Description/Explanation */}
                              <td className="p-3">
                                <input
                                  type="text"
                                  value={m.description}
                                  onChange={(e) => {
                                    const nextVal = e.target.value;
                                    const updated = ontologyMappings.map(ent => {
                                      if (ent.entityId === editingOntology.entityId) {
                                        const newM = [...ent.mappings];
                                        newM[idx] = { ...newM[idx], description: nextVal };
                                        return { ...ent, mappings: newM };
                                      }
                                      return ent;
                                    });
                                    setOntologyMappings(updated);
                                    setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId,)};
                                  }}
                                  className={`w-full px-2 py-1 border ${styles.cardBorder} rounded-md text-[10px] ${styles.cardTextMuted} ${styles.cardBg}`}
                                />
                              </td>

                              {/* Delete Row Button */}
                              <td className="p-3 text-center">
                                <button
                                  onClick={() => {
                                    const updated = ontologyMappings.map(ent => {
                                      if (ent.entityId === editingOntology.entityId) {
                                        const newM = ent.mappings.filter((_: any, i: number) => i !== idx);
                                        return { ...ent, mappings: newM };
                                      }
                                      return ent;
                                    });
                                    setOntologyMappings(updated);
                                    setEditingOntology(updated.find(ent => ent.entityId === editingOntology.entityId,)};
                                  }}
                                  className="p-1 rounded bg-rose-50 text-rose-600 hover:bg-rose-100 cursor-pointer transition-colors"
                                >
                                  <Icon name="Trash2" size={11} />
                                </button>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Submit and Save Panel */}
              <div className={`flex items-center justify-between pt-4 border-t ${styles.cardBorder}`}>
                <div className={`text-[10px] ${styles.cardTextMuted}`}>
                  * 注意：保存映射后，系统将实时更新 RAG 先验上下文数据库，AI 生成将根据此架构强类型对齐。
                </div>
                <button
                  onClick={() => handleSaveOntologyMappings(ontologyMappings)}
                  className={`px-5 py-2 ${styles.appBg} hover:styles.sidebarActiveBg text-white font-extrabold rounded-lg shadow-sm flex items-center gap-1.5 cursor-pointer text-xs transition-colors`}
                >
                  <Icon name="Save" size={12} />
                  <span>{t('aiworkbench.knowledge.ontology.saveBtn')} (Save & Apply)</span>
                </button>
              </div>

            </div>
          ) : (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-12 text-center ${styles.cardTextMuted} space-y-2`}>
              <Icon name="Workflow" size={24} className={`mx-auto ${styles.cardTextMuted} animate-pulse`} />
              <p className="font-bold text-xs">{t('aiworkbench.knowledge.ontology.emptyEntity')} (Ontology Entity)</p>
              <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.ontology.emptyEntityHint')}。</p>
            </div>
          )}
        </div>

      </div>

      {/* Export Markdown Modal popup */}
      {showExportModal && (
        <div className={`fixed inset-0 z-50 flex items-center justify-center p-4 ${styles.appBg}/60 backdrop-blur-xs animate-fade-in`}>
          <div className={`${styles.cardBg} rounded-2xl max-w-2xl w-full border ${styles.cardBorder} shadow-xl overflow-hidden flex flex-col max-h-[85vh]`}>
            
            {/* Modal Header */}
            <div className={`${styles.cardBg} text-white p-4 flex items-center justify-between`}>
              <div className="flex items-center gap-2">
                <Icon name="Download" size={15} className="text-blue-400" />
                <span className="font-black text-xs">已编译的 Ontology Schema 先验知识元数据包 (RAG Prior-Knowledge)</span>
              </div>
              <button
                onClick={() => setShowExportModal(false)}
                className={`${styles.cardTextMuted} hover:text-white font-bold cursor-pointer`}
              >
                <Icon name="X" size={16} />
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-5 overflow-y-auto space-y-4">
              <p className={`text-[11px] ${styles.cardTextMuted} font-sans`}>
                下面的元数据包已成功融合成无幻觉 RAG 专属的非结构化上下文契约。当 Copilot 运行时，此文本会与检索意图自动对齐，强行拦截 AI 漂移并对齐底细列：
              </p>

              <pre className={`p-4 ${styles.appBg} styles.cardText rounded-xl font-mono text-[9px] whitespace-pre-wrap leading-relaxed select-text max-h-[350px] overflow-y-auto`}>
                {exportedMarkdown}
              </pre>

              <div className="p-3 bg-blue-50 border border-blue-100 rounded-xl text-[10px] text-blue-700 font-sans flex items-start gap-1.5">
                <Icon name="Info" size={13} className="shrink-0 mt-0.5" />
                <span>该元数据包已经和后端 RAG 推理引擎 (Grounded RAG Sandbox) 彻底绑定。您可以{t('aiworkbench.knowledge.ontology.closeBtn')}此弹窗，直接切换到「知识检索与 RAG 模拟」分区分区测试您的全新映射关系！</span>
              </div>
            </div>

            {/* Modal Footer */}
            <div className={`p-4 ${styles.inputBg} border-t ${styles.cardBorder} flex items-center justify-end gap-2 shrink-0`}>
              <button
                onClick={() => {
                  navigator.clipboard.writeText(exportedMarkdown);
                  showToast?.('success', '{t('aiworkbench.knowledge.ontology.copySuccess')}');
                }}
                className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center gap-1 transition-all"
              >
                <Icon name="Copy" size={12} />
                <span>{t('aiworkbench.knowledge.ontology.copyBtn')}</span>
              </button>
              <button
                onClick={() => setShowExportModal(false)}
                className={`px-4 py-1.5 ${styles.inputBg} ${styles.sidebarHoverBg} ${styles.cardTextMuted} font-bold rounded-lg text-xs cursor-pointer transition-all`}
              >
                <span>{t('aiworkbench.knowledge.ontology.closeBtn')}</span>
              </button>
            </div>

          </div>
        </div>
      )}

    </div>
  );
}