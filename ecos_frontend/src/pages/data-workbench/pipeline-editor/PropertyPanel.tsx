/**
 * PropertyPanel — Pipeline node property editor
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import React, { useState, useMemo, useCallback } from 'react';
import type { Node } from '@xyflow/react';
import { Settings, Trash2, X, ChevronDown } from 'lucide-react';
import type { NodeConfig, TransformRule, JoinCondition, NodeStatus } from './types';
import type { DataConnection, TableInfo } from '../types';
import { PALETTE_ITEMS } from './constants';
import OperatorSearchPanel from './OperatorSearchPanel';
import type { PBFunctionDef } from './pbFunctions';
import TransformRulesEditor from './TransformRulesEditor';
import JoinConditionsEditor from './JoinConditionsEditor';
import AggregateConfigEditor from './AggregateConfigEditor';

// ─── Section collapse toggle ──────────────────────────────
const SectionToggle: React.FC<{
  collapsed: boolean; onClick: () => void; label: string;
}> = ({ collapsed, onClick, label }) => (
  <button onClick={onClick}
    className="flex items-center justify-between w-full px-3 py-2 text-xs font-semibold text-slate-500 hover:bg-slate-100 transition-colors"
  >
    <span>{label}</span>
    <ChevronDown size={14} className={`transition-transform duration-200 ${collapsed ? '-rotate-90' : 'rotate-0'}`} />
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

    const allTables = useMemo(() => {
      const tables: { connectionName: string; table: TableInfo }[] = [];
      connections.forEach((conn) => conn.tablesAvailable.forEach((t) => tables.push({ connectionName: conn.name, table: t })));
      return tables;
    }, [connections]);

    // ── Transform rules helpers ──
    const addTransformRule = () => {
      const rules = [...(config.transformRules || []), { id: `rule-${Date.now()}`, column: '', function: '', params: '' }];
      onUpdateNode(node!.id, { transformRules: rules });
    };
    const updateTransformRule = (ruleId: string, field: keyof TransformRule, value: string) => {
      const rules = (config.transformRules || []).map((r) => r.id === ruleId ? { ...r, [field]: value } : r);
      onUpdateNode(node!.id, { transformRules: rules });
    };
    const removeTransformRule = (ruleId: string) => {
      const rules = (config.transformRules || []).filter((r) => r.id !== ruleId);
      onUpdateNode(node!.id, { transformRules: rules });
    };

    // ── Join helpers ──
    const addJoinCondition = () => {
      const conditions = [...(config.joinConditions || []), { id: `cond-${Date.now()}`, leftColumn: '', rightColumn: '', operator: '=' }];
      onUpdateNode(node!.id, { joinConditions: conditions });
    };
    const updateJoinCondition = (condId: string, field: keyof JoinCondition, value: string) => {
      const conditions = (config.joinConditions || []).map((c) => c.id === condId ? { ...c, [field]: value } : c);
      onUpdateNode(node!.id, { joinConditions: conditions });
    };
    const removeJoinCondition = (condId: string) => {
      const conditions = (config.joinConditions || []).filter((c) => c.id !== condId);
      onUpdateNode(node!.id, { joinConditions: conditions });
    };

    // ── Aggregate helpers ──
    const addGroupByColumn = () => {
      const cols = [...(config.aggregateGroupBy || []), ''];
      onUpdateNode(node!.id, { aggregateGroupBy: cols });
    };
    const updateGroupByColumn = (index: number, value: string) => {
      const cols = [...(config.aggregateGroupBy || [])]; cols[index] = value;
      const filtered = cols.filter((c, i) => i !== cols.length - 1 || c !== '');
      onUpdateNode(node!.id, { aggregateGroupBy: filtered.length > 0 ? filtered : [''] });
    };
    const removeGroupByColumn = (index: number) => {
      const cols = (config.aggregateGroupBy || []).filter((_, i) => i !== index);
      onUpdateNode(node!.id, { aggregateGroupBy: cols.length > 0 ? cols : [''] });
    };
    const addAggFunction = () => {
      const funcs = [...(config.aggregateFunctions || []), { column: '', function: 'COUNT', alias: '' }];
      onUpdateNode(node!.id, { aggregateFunctions: funcs });
    };
    const updateAggFunction = (index: number, field: 'column' | 'function' | 'alias', value: string) => {
      const funcs = (config.aggregateFunctions || []).map((f, i) => i === index ? { ...f, [field]: value } : f);
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
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">属性面板</span>
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
            <button onClick={() => onDeleteNode(node.id)} className="p-1 hover:bg-red-100 rounded text-red-500 transition-colors" title="删除节点">
              <Trash2 size={14} />
            </button>
            <button onClick={onClose} className="p-1 hover:bg-slate-200 rounded text-slate-500 transition-colors" title="关闭面板">
              <X size={14} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto">
          {/* Basic Info */}
          <div className="border-b border-slate-100">
            <SectionToggle collapsed={collapsedSections['basic']} onClick={() => toggleSection('basic')} label="基本信息" />
            {!collapsedSections['basic'] && (
              <div className="px-3 pb-3 space-y-2">
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">节点名称</label>
                  <input type="text" value={config.label || ''}
                    onChange={(e) => onUpdateNode(node.id, { label: e.target.value })}
                    className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none"
                    placeholder="输入节点名称" />
                </div>
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">节点类型</label>
                  <select value={config.nodeType || ''}
                    onChange={(e) => onUpdateNode(node.id, { nodeType: e.target.value })}
                    className="w-full px-2 py-1 text-xs border border-slate-200 rounded focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none"
                  >
                    {PALETTE_ITEMS.map((item) => (
                      <option key={item.type} value={item.type}>{item.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-[11px] text-slate-500 block mb-1">运行状态</label>
                  <select value={config.nodeStatus || 'idle'}
                    onChange={(e) => onUpdateNode(node.id, { nodeStatus: e.target.value as NodeStatus })}
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
              <SectionToggle collapsed={collapsedSections['table']} onClick={() => toggleSection('table')}
                label={config.nodeType === 'source' ? '数据源表' : '目标表'} />
              {!collapsedSections['table'] && (
                <div className="px-3 pb-3 space-y-2">
                  <select
                    value={config.nodeType === 'source' ? config.sourceTable || '' : config.targetTable || ''}
                    onChange={(e) => onUpdateNode(node.id,
                      config.nodeType === 'source' ? { sourceTable: e.target.value } : { targetTable: e.target.value }
                    )}
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

          {/* Transform Rules (delegated to TransformRulesEditor) */}
          {config.nodeType === 'transform' && (
            <div className="border-b border-slate-100">
              <SectionToggle collapsed={collapsedSections['transform']} onClick={() => toggleSection('transform')} label="转换规则" />
              {!collapsedSections['transform'] && (
                <TransformRulesEditor
                  rules={config.transformRules || []}
                  onAdd={addTransformRule}
                  onUpdate={updateTransformRule}
                  onRemove={removeTransformRule}
                  onOperatorButtonClick={(ruleId) => { setActiveRuleId(ruleId); setShowOperatorPanel(true); }}
                />
              )}
            </div>
          )}

          {/* Join Configuration (delegated to JoinConditionsEditor) */}
          {config.nodeType === 'join' && (
            <div className="border-b border-slate-100">
              <SectionToggle collapsed={collapsedSections['join']} onClick={() => toggleSection('join')} label="JOIN 配置" />
              {!collapsedSections['join'] && (
                <JoinConditionsEditor
                  joinType={config.joinType || 'INNER'}
                  conditions={config.joinConditions || []}
                  onJoinTypeChange={(v) => onUpdateNode(node.id, { joinType: v })}
                  onAdd={addJoinCondition}
                  onUpdate={updateJoinCondition}
                  onRemove={removeJoinCondition}
                />
              )}
            </div>
          )}

          {/* Aggregate Configuration (delegated to AggregateConfigEditor) */}
          {config.nodeType === 'aggregate' && (
            <div className="border-b border-slate-100">
              <SectionToggle collapsed={collapsedSections['aggregate']} onClick={() => toggleSection('aggregate')} label="聚合配置" />
              {!collapsedSections['aggregate'] && (
                <AggregateConfigEditor
                  groupByCols={config.aggregateGroupBy || []}
                  aggFunctions={config.aggregateFunctions || []}
                  onAddGroupBy={addGroupByColumn}
                  onUpdateGroupBy={updateGroupByColumn}
                  onRemoveGroupBy={removeGroupByColumn}
                  onAddAgg={addAggFunction}
                  onUpdateAgg={updateAggFunction}
                  onRemoveAgg={removeAggFunction}
                />
              )}
            </div>
          )}
        </div>

        {/* OperatorSearchPanel Popup */}
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
