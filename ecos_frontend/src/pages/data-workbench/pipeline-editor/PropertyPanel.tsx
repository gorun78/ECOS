/**
 * PropertyPanel — Pipeline node property editor
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React, { useState, useMemo, useCallback } from 'react';
import type { Node } from '@xyflow/react';
import { Settings, Plus, Trash2, X, ChevronDown, FunctionSquare } from 'lucide-react';
import type { NodeConfig, TransformRule, JoinCondition, NodeStatus } from './types';
import type { DataConnection, TableInfo } from '../types';
import { PALETTE_ITEMS } from './constants';
import ExpressionEditor from './ExpressionEditor';
import OperatorSearchPanel from './OperatorSearchPanel';
import type { PBFunctionDef } from './pbFunctions';

// ─── Section collapse toggle ──────────────────────────────
const SectionToggle: React.FC<{
  collapsed: boolean;
  onClick: () => void;
  label: string;
}> = ({ collapsed, onClick, label }) => (
  <button
    onClick={onClick}
    className="flex items-center justify-between w-full px-3 py-2 text-xs font-semibold text-slate-500 hover:bg-slate-100 transition-colors"
  >
    <span>{label}</span>
    <ChevronDown
      size={14}
      className={`transition-transform duration-200 ${collapsed ? '-rotate-90' : 'rotate-0'}`}
    />
  </button>
);

// ─── Property Panel ───────────────────────────────────────

interface PropertyPanelProps {
  node: Node | null;
  connections: DataConnection[];
  onUpdateNode: (nodeId: string, config: Partial<NodeConfig>) => void;
  onDeleteNode: (nodeId: string) => void;
  onClose: () => void;
}

const PropertyPanel: React.FC<PropertyPanelProps> = React.memo(
  ({ node, connections, onUpdateNode, onDeleteNode, onClose }) => {
    const config: NodeConfig = (node?.data ?? {}) as unknown as NodeConfig;
    const [collapsedSections, setCollapsedSections] = useState<Record<string, boolean>>({});
    const [showOperatorPanel, setShowOperatorPanel] = useState(false);
    const [activeRuleId, setActiveRuleId] = useState<string | null>(null);

    const toggleSection = (key: string) => {
      setCollapsedSections((prev) => ({ ...prev, [key]: !prev[key] }));
    };

    // ── OperatorSearchPanel callback ──
    const handleSelectFunction = useCallback(
      (fn: PBFunctionDef) => {
        if (!node || !activeRuleId) return;
        const rules = (config.transformRules || []).map((r) =>
          r.id === activeRuleId ? { ...r, function: fn.name } : r
        );
        onUpdateNode(node.id, { transformRules: rules });
        setShowOperatorPanel(false);
        setActiveRuleId(null);
      },
      [node, activeRuleId, config.transformRules, onUpdateNode]
    );

    // ── Table picker helpers ──
    const allTables = useMemo(() => {
      const tables: { connectionName: string; table: TableInfo }[] = [];
      connections.forEach((conn) => {
        conn.tablesAvailable.forEach((t) => {
          tables.push({ connectionName: conn.name, table: t });
        });
      });
      return tables;
    }, [connections]);

    // ── Transform rules ──
    const addTransformRule = () => {
      const rules = [...(config.transformRules || [])];
      rules.push({ id: `rule-${Date.now()}`, column: '', function: '', params: '' });
      onUpdateNode(node!.id, { transformRules: rules });
    };

    const updateTransformRule = (ruleId: string, field: keyof TransformRule, value: string) => {
      const rules = (config.transformRules || []).map((r) =>
        r.id === ruleId ? { ...r, [field]: value } : r
      );
      onUpdateNode(node!.id, { transformRules: rules });
    };

    const removeTransformRule = (ruleId: string) => {
      const rules = (config.transformRules || []).filter((r) => r.id !== ruleId);
      onUpdateNode(node!.id, { transformRules: rules });
    };

    // ── Join conditions ──
    const addJoinCondition = () => {
      const conditions = [...(config.joinConditions || [])];
      conditions.push({
        id: `cond-${Date.now()}`,
        leftColumn: '',
        rightColumn: '',
        operator: '=',
      });
      onUpdateNode(node!.id, { joinConditions: conditions });
    };

    const updateJoinCondition = (
      condId: string,
      field: keyof JoinCondition,
      value: string
    ) => {
      const conditions = (config.joinConditions || []).map((c) =>
        c.id === condId ? { ...c, [field]: value } : c
      );
      onUpdateNode(node!.id, { joinConditions: conditions });
    };

    const removeJoinCondition = (condId: string) => {
      const conditions = (config.joinConditions || []).filter((c) => c.id !== condId);
      onUpdateNode(node!.id, { joinConditions: conditions });
    };

    // ── Aggregate group by ──
    const addGroupByColumn = () => {
      const cols = [...(config.aggregateGroupBy || []), ''];
      onUpdateNode(node!.id, { aggregateGroupBy: cols });
    };

    const updateGroupByColumn = (index: number, value: string) => {
      const cols = [...(config.aggregateGroupBy || [])];
      cols[index] = value;
      // Remove empty last entry
      const filtered = cols.filter((c, i) => i !== cols.length - 1 || c !== '');
      onUpdateNode(node!.id, { aggregateGroupBy: filtered.length > 0 ? filtered : [''] });
    };

    const removeGroupByColumn = (index: number) => {
      const cols = (config.aggregateGroupBy || []).filter((_, i) => i !== index);
      onUpdateNode(node!.id, { aggregateGroupBy: cols.length > 0 ? cols : [''] });
    };

    // ── Aggregate functions ──
    const addAggFunction = () => {
      const funcs = [...(config.aggregateFunctions || [])];
      funcs.push({ column: '', function: 'COUNT', alias: '' });
      onUpdateNode(node!.id, { aggregateFunctions: funcs });
    };

    const updateAggFunction = (
      index: number,
      field: keyof { column: string; function: string; alias: string },
      value: string
    ) => {
      const funcs = (config.aggregateFunctions || []).map((f, i) =>
        i === index ? { ...f, [field]: value } : f
      );
      onUpdateNode(node!.id, { aggregateFunctions: funcs });
    };

    const removeAggFunction = (index: number) => {
      const funcs = (config.aggregateFunctions || []).filter((_, i) => i !== index);
      onUpdateNode(node!.id, { aggregateFunctions: funcs });
    };

    if (!node) {
      return (
        <div className="w-72 border-l border-slate-200 bg-white flex flex-col h-full">
          <div className="flex items-center justify-between px-3 py-2 border-b border-slate-200 bg-slate-50">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              属性面板
            </span>
          </div>
          <div className="flex-1 flex items-center justify-center text-xs text-slate-400 p-4 text-center">
            点击画布上的节点<br />以编辑属性
          </div>
        </div>
      );
    }

    return (
      <div className="w-80 border-l border-slate-200 bg-white flex flex-col h-full overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-3 py-2 border-b border-slate-200 bg-slate-50 shrink-0">
          <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
            {PALETTE_ITEMS.find((p) => p.type === config.nodeType)?.label || '节点'} 属性
          </span>
          <div className="flex gap-1">
            <button
              onClick={() => onDeleteNode(node.id)}
              className="p-1 hover:bg-red-100 rounded text-red-500 transition-colors"
              title="删除节点"
            >
              <Trash2 size={14} />
            </button>
            <button
              onClick={onClose}
              className="p-1 hover:bg-slate-200 rounded text-slate-500 transition-colors"
              title="关闭面板"
            >
              <X size={14} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto">
          {/* Basic Info */}
          <div className="border-b border-slate-100">
            <SectionToggle
              collapsed={collapsedSections['basic']}
              onClick={() => toggleSection('basic')}
              label="基本信息"
            />
            {!collapsedSections['basic'] && (
              <div className="px-3 pb-3 space-y-2">
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">节点名称</label>
                  <input
                    type="text"
                    value={config.label || ''}
                    onChange={(e) => onUpdateNode(node.id, { label: e.target.value })}
                    className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none"
                    placeholder="输入节点名称"
                  />
                </div>
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">节点类型</label>
                  <select
                    value={config.nodeType || ''}
                    onChange={(e) => onUpdateNode(node.id, { nodeType: e.target.value })}
                    className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none"
                  >
                    {PALETTE_ITEMS.map((item) => (
                      <option key={item.type} value={item.type}>
                        {item.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">运行状态</label>
                  <select
                    value={config.nodeStatus || 'idle'}
                    onChange={(e) =>
                      onUpdateNode(node.id, { nodeStatus: e.target.value as NodeStatus })
                    }
                    className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none"
                  >
                    <option value="idle">Idle</option>
                    <option value="running">Running</option>
                    <option value="success">Success</option>
                    <option value="error">Error</option>
                  </select>
                </div>
              </div>
            )}
          </div>

          {/* Source/Sink Table */}
          {(config.nodeType === 'source' || config.nodeType === 'sink') && (
            <div className="border-b border-slate-100">
              <SectionToggle
                collapsed={collapsedSections['table']}
                onClick={() => toggleSection('table')}
                label={config.nodeType === 'source' ? '数据源表' : '目标表'}
              />
              {!collapsedSections['table'] && (
                <div className="px-3 pb-3 space-y-2">
                  <select
                    value={
                      config.nodeType === 'source' ? config.sourceTable || '' : config.targetTable || ''
                    }
                    onChange={(e) =>
                      onUpdateNode(
                        node.id,
                        config.nodeType === 'source'
                          ? { sourceTable: e.target.value }
                          : { targetTable: e.target.value }
                      )
                    }
                    className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none"
                  >
                    <option value="">-- 选择表 --</option>
                    {allTables.map(({ connectionName, table }) => (
                      <option key={`${connectionName}.${table.name}`} value={table.name}>
                        {connectionName} / {table.name} ({table.rowCount} 行)
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>
          )}

          {/* Transform Rules (Updated: ExpressionEditor + OperatorSearchPanel) */}
          {config.nodeType === 'transform' && (
            <div className="border-b border-slate-100">
              <SectionToggle
                collapsed={collapsedSections['transform']}
                onClick={() => toggleSection('transform')}
                label="转换规则"
              />
              {!collapsedSections['transform'] && (
                <div className="px-3 pb-3 space-y-2">
                  {/* ExpressionEditor + OperatorSearchPanel */}
                  {(config.transformRules || []).map((rule) => (
                    <div
                      key={rule.id}
                      className="p-2 border border-slate-200 rounded bg-slate-50 space-y-1.5"
                    >
                      {/* Expression Editor replaces old <select>+<input> */}
                      <div className="flex flex-col gap-1">
                        <ExpressionEditor
                          value={
                            rule.function && rule.params
                              ? `${rule.function}(${rule.params})`
                              : rule.function || ''
                          }
                          onChange={(expr) => {
                            // Parse expression: function(params)
                            const match = expr.match(/^(\w+)\s*\((.*)\)$/);
                            const fnName = match?.[1] || expr;
                            const params = match?.[2] || '';
                            updateTransformRule(rule.id, 'function', fnName);
                            updateTransformRule(rule.id, 'params', params);
                          }}
                          placeholder="输入表达式，如 upper(name)..."
                          className="flex-1"
                          showOperatorButton
                          onOperatorButtonClick={() => {
                            setActiveRuleId(rule.id);
                            setShowOperatorPanel(true);
                          }}
                        />
                        <div className="flex gap-1 items-center text-[10px]">
                          <span className="text-slate-400">列名:</span>
                          <input
                            type="text"
                            value={rule.column}
                            onChange={(e) => updateTransformRule(rule.id, 'column', e.target.value)}
                            placeholder="列名"
                            className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-emerald-400"
                          />
                          <button
                            onClick={() => removeTransformRule(rule.id)}
                            className="p-0.5 text-red-400 hover:text-red-600 transition-colors"
                          >
                            <X size={14} />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                  <button
                    onClick={addTransformRule}
                    className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-[11px] text-emerald-600 border border-dashed border-emerald-300 rounded hover:bg-emerald-50 transition-colors"
                  >
                    <Plus size={12} /> 添加转换规则
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Join Configuration */}
          {config.nodeType === 'join' && (
            <div className="border-b border-slate-100">
              <SectionToggle
                collapsed={collapsedSections['join']}
                onClick={() => toggleSection('join')}
                label="JOIN 配置"
              />
              {!collapsedSections['join'] && (
                <div className="px-3 pb-3 space-y-2">
                  <div>
                    <label className="text-[11px] text-slate-500 block mb-1">JOIN 类型</label>
                    <select
                      value={config.joinType || 'INNER'}
                      onChange={(e) => onUpdateNode(node.id, { joinType: e.target.value })}
                      className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-purple-400 focus:ring-1 focus:ring-purple-200 outline-none"
                    >
                      <option value="INNER">INNER JOIN</option>
                      <option value="LEFT">LEFT JOIN</option>
                      <option value="RIGHT">RIGHT JOIN</option>
                      <option value="FULL">FULL OUTER JOIN</option>
                      <option value="CROSS">CROSS JOIN</option>
                    </select>
                  </div>
                  {(config.joinConditions || []).map((cond) => (
                    <div
                      key={cond.id}
                      className="p-2 border border-slate-200 rounded bg-slate-50 space-y-1.5"
                    >
                      <div className="flex gap-1">
                        <input
                          type="text"
                          value={cond.leftColumn}
                          onChange={(e) =>
                            updateJoinCondition(cond.id, 'leftColumn', e.target.value)
                          }
                          placeholder="左表列"
                          className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-purple-400"
                        />
                        <select
                          value={cond.operator}
                          onChange={(e) =>
                            updateJoinCondition(cond.id, 'operator', e.target.value)
                          }
                          className="w-14 px-1 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-purple-400"
                        >
                          <option value="=">=</option>
                          <option value="!=">!=</option>
                          <option value=">">&gt;</option>
                          <option value="<">&lt;</option>
                          <option value=">=">&gt;=</option>
                          <option value="<=">&lt;=</option>
                        </select>
                        <input
                          type="text"
                          value={cond.rightColumn}
                          onChange={(e) =>
                            updateJoinCondition(cond.id, 'rightColumn', e.target.value)
                          }
                          placeholder="右表列"
                          className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-purple-400"
                        />
                        <button
                          onClick={() => removeJoinCondition(cond.id)}
                          className="p-0.5 text-red-400 hover:text-red-600 transition-colors"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    </div>
                  ))}
                  <button
                    onClick={addJoinCondition}
                    className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-[11px] text-purple-600 border border-dashed border-purple-300 rounded hover:bg-purple-50 transition-colors"
                  >
                    <Plus size={12} /> 添加 JOIN 条件
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Aggregate Configuration */}
          {config.nodeType === 'aggregate' && (
            <div className="border-b border-slate-100">
              <SectionToggle
                collapsed={collapsedSections['aggregate']}
                onClick={() => toggleSection('aggregate')}
                label="聚合配置"
              />
              {!collapsedSections['aggregate'] && (
                <div className="px-3 pb-3 space-y-3">
                  {/* Group By */}
                  <div>
                    <label className="text-[11px] text-slate-500 block mb-1">GROUP BY 列</label>
                    {(config.aggregateGroupBy || []).map((col, idx) => (
                      <div key={idx} className="flex gap-1 mb-1">
                        <input
                          type="text"
                          value={col}
                          onChange={(e) => updateGroupByColumn(idx, e.target.value)}
                          placeholder="列名"
                          className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
                        />
                        <button
                          onClick={() => removeGroupByColumn(idx)}
                          className="p-0.5 text-red-400 hover:text-red-600 transition-colors"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ))}
                    <button
                      onClick={addGroupByColumn}
                      className="w-full flex items-center justify-center gap-1 px-2 py-1 text-[11px] text-orange-600 border border-dashed border-orange-300 rounded hover:bg-orange-50 transition-colors mt-1"
                    >
                      <Plus size={12} /> 添加分组列
                    </button>
                  </div>

                  {/* Aggregate Functions */}
                  <div>
                    <label className="text-[11px] text-slate-500 block mb-1">聚合函数</label>
                    {(config.aggregateFunctions || []).map((func, idx) => (
                      <div
                        key={idx}
                        className="p-2 border border-slate-200 rounded bg-slate-50 space-y-1.5 mb-1"
                      >
                        <div className="flex gap-1">
                          <select
                            value={func.function}
                            onChange={(e) => updateAggFunction(idx, 'function', e.target.value)}
                            className="flex-1 px-1 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
                          >
                            <option value="COUNT">COUNT</option>
                            <option value="SUM">SUM</option>
                            <option value="AVG">AVG</option>
                            <option value="MIN">MIN</option>
                            <option value="MAX">MAX</option>
                            <option value="COUNT_DISTINCT">COUNT DISTINCT</option>
                          </select>
                          <input
                            type="text"
                            value={func.column}
                            onChange={(e) => updateAggFunction(idx, 'column', e.target.value)}
                            placeholder="列名"
                            className="w-20 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
                          />
                        </div>
                        <div className="flex gap-1 items-center">
                          <span className="text-[10px] text-slate-400">别名:</span>
                          <input
                            type="text"
                            value={func.alias}
                            onChange={(e) => updateAggFunction(idx, 'alias', e.target.value)}
                            placeholder="as"
                            className="flex-1 px-1.5 py-0.5 text-[11px] border border-slate-200 rounded outline-none focus:border-orange-400"
                          />
                          <button
                            onClick={() => removeAggFunction(idx)}
                            className="p-0.5 text-red-400 hover:text-red-600 transition-colors"
                          >
                            <X size={14} />
                          </button>
                        </div>
                      </div>
                    ))}
                    <button
                      onClick={addAggFunction}
                      className="w-full flex items-center justify-center gap-1 px-2 py-1.5 text-[11px] text-orange-600 border border-dashed border-orange-300 rounded hover:bg-orange-50 transition-colors"
                    >
                      <Plus size={12} /> 添加聚合函数
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* ── OperatorSearchPanel Popup ── */}
        {showOperatorPanel && (
          <div className="fixed inset-0 z-50 flex items-start justify-center pt-20">
            <div className="fixed inset-0 bg-black/20" onClick={() => setShowOperatorPanel(false)} />
            <OperatorSearchPanel
              onSelectFunction={handleSelectFunction}
              onClose={() => setShowOperatorPanel(false)}
              className="relative z-10 w-[420px] max-h-[520px]"
            />
          </div>
        )}
      </div>
    );
  }
);


export default PropertyPanel;
