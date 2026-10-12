/**
 * GraphExplorerTab 新建节点 / 边弹窗（自 GraphExplorerTab 拆出，纯搬迁，行为不变）。
 */
import { Plus, ArrowRight, X } from 'lucide-react';
import type { GraphNode, NewEdgeFormState, NewNodeFormState } from './types';

export interface CreateModalProps {
  styles: Record<string, string>;
  t: (key: string) => string;
  mode: 'node' | 'edge';
  nodes: GraphNode[];
  newNodeForm: NewNodeFormState;
  onNewNodeFormChange: (patch: Partial<NewNodeFormState>) => void;
  newEdgeForm: NewEdgeFormState;
  onNewEdgeFormChange: (patch: Partial<NewEdgeFormState>) => void;
  onCreateNode: () => void;
  onCreateEdge: () => void;
  onClose: () => void;
}

export function CreateModal({
  styles,
  t,
  mode,
  nodes,
  newNodeForm,
  onNewNodeFormChange,
  newEdgeForm,
  onNewEdgeFormChange,
  onCreateNode,
  onCreateEdge,
  onClose,
}: CreateModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl w-96 p-5 space-y-4 shadow-2xl`}>
        <div className="flex items-center justify-between">
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            {mode === 'node'
              ? <><Plus size={14} className="text-blue-400" /> {t('knowledge.graph.newNode')}</>
              : <><ArrowRight size={14} className="text-emerald-400" /> {t('knowledge.graph.newEdge')}</>
            }
          </h3>
          <button type="button"
            onClick={onClose}
            className={`p-1 ${styles.sidebarHoverBg} rounded ${styles.muted} hover:${styles.cardText} cursor-pointer`}
          >
            <X size={14} />
          </button>
        </div>

        {mode === 'node' ? (
          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.nodeLabel')}</label>
              <input
                type="text"
                value={newNodeForm.label}
                onChange={(e) => onNewNodeFormChange({ label: e.target.value })}
                placeholder={t('knowledge.graph.nodeLabelPlaceholder')}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-blue-500`}
              />
            </div>
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.nodeType')}</label>
              <input
                type="text"
                value={newNodeForm.nodeType}
                onChange={(e) => onNewNodeFormChange({ nodeType: e.target.value })}
                placeholder={t('knowledge.graph.nodeTypePlaceholder')}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-blue-500`}
              />
            </div>
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.nodeDescription')}</label>
              <textarea
                value={newNodeForm.description}
                onChange={(e) => onNewNodeFormChange({ description: e.target.value })}
                placeholder={t('knowledge.graph.nodeDescriptionPlaceholder')}
                rows={2}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-blue-500 resize-none`}
              />
            </div>
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.nodeProperties')}</label>
              <textarea
                value={newNodeForm.properties}
                onChange={(e) => onNewNodeFormChange({ properties: e.target.value })}
                placeholder='{"domain": "finance", "owner": "admin"}'
                rows={2}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-blue-500 resize-none font-mono`}
              />
            </div>
            <div className="flex gap-2 pt-2">
              <button type="button"
                onClick={onClose}
                className={`flex-1 px-3 py-1.5 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} transition cursor-pointer`}
              >
                {t('knowledge.graph.cancel')}
              </button>
              <button type="button"
                onClick={onCreateNode}
                className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition cursor-pointer"
              >
                {t('knowledge.graph.create')}
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.sourceNode')}</label>
              <select
                value={newEdgeForm.sourceNodeId}
                onChange={(e) => onNewEdgeFormChange({ sourceNodeId: e.target.value })}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} outline-none focus:border-emerald-500 cursor-pointer`}
              >
                <option value="">{t('knowledge.graph.selectNode')}</option>
                {nodes.map(n => (
                  <option key={n.id} value={n.id}>{n.label} ({n.id})</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.targetNode')}</label>
              <select
                value={newEdgeForm.targetNodeId}
                onChange={(e) => onNewEdgeFormChange({ targetNodeId: e.target.value })}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} outline-none focus:border-emerald-500 cursor-pointer`}
              >
                <option value="">{t('knowledge.graph.selectNode')}</option>
                {nodes.map(n => (
                  <option key={n.id} value={n.id}>{n.label} ({n.id})</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.relationship')}</label>
              <input
                type="text"
                value={newEdgeForm.relationship}
                onChange={(e) => onNewEdgeFormChange({ relationship: e.target.value })}
                placeholder={t('knowledge.graph.relationshipPlaceholder')}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-emerald-500`}
              />
            </div>
            <div className="space-y-1.5">
              <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase`}>{t('knowledge.graph.weight')}</label>
              <input
                type="number"
                value={newEdgeForm.weight}
                onChange={(e) => onNewEdgeFormChange({ weight: e.target.value })}
                min="0"
                step="0.1"
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} outline-none focus:border-emerald-500`}
              />
            </div>
            <div className="flex gap-2 pt-2">
              <button type="button"
                onClick={onClose}
                className={`flex-1 px-3 py-1.5 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} transition cursor-pointer`}
              >
                {t('knowledge.graph.cancel')}
              </button>
              <button type="button"
                onClick={onCreateEdge}
                className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg transition cursor-pointer"
              >
                {t('knowledge.graph.create')}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
