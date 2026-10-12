/**
 * GraphExplorerTab 右侧节点详情面板（自 GraphExplorerTab 拆出，纯搬迁，行为不变）。
 */
import {
  X, ExternalLink, GitBranch, Info, Tag, Minimize2,
} from 'lucide-react';
import type { GraphEdge, GraphNode } from './types';

export interface DetailPanelProps {
  styles: Record<string, string>;
  t: (key: string, params?: Record<string, string | number> | string) => string;
  node: GraphNode | null;
  nodeEdges: GraphEdge[];
  expandedNodeIds: Set<string>;
  onCollapse: (id: string) => void;
  onExpand: (id: string) => void;
  onSetPathSource: (id: string) => void;
  onClose: () => void;
}

export function DetailPanel({
  styles,
  t,
  node,
  nodeEdges,
  expandedNodeIds,
  onCollapse,
  onExpand,
  onSetPathSource,
  onClose,
}: DetailPanelProps) {
  return (
    <div className={`w-72 border-l ${styles.cardBorder} flex flex-col shrink-0 ${styles.appBg} overflow-y-auto`}>
      <div className={`px-3 py-2.5 border-b ${styles.cardBorder} flex items-center justify-between`}>
        <h3 className={`text-xs font-bold ${styles.sidebarText} flex items-center gap-1.5`}>
          <Info size={12} className="text-blue-400" />
          {t('knowledge.graph.nodeDetail')}
        </h3>
        <button type="button"
          onClick={onClose}
          className={`p-1 ${styles.sidebarHoverBg} rounded ${styles.muted} hover:${styles.cardText} cursor-pointer transition`}
        >
          <X size={13} />
        </button>
      </div>

      {node ? (
        <div className="p-3 space-y-3 text-[11px]">
          {/* Basic Info */}
          <div className="space-y-1.5">
            <div className={`text-sm font-bold ${styles.cardText}`}>
              {node.label}
            </div>
            <div className={`flex items-center gap-1.5 ${styles.cardTextMuted}`}>
              <Tag size={10} />
              <span>{node.type || 'N/A'}</span>
            </div>
            {node.description && (
              <p className={`${styles.cardTextMuted} leading-relaxed`}>
                {node.description}
              </p>
            )}
          </div>

          {/* Properties */}
          {node.properties && Object.keys(node.properties).length > 0 && (
            <div className="space-y-1.5">
              <span className={`text-[10px] font-bold ${styles.muted} uppercase tracking-wider`}>
                {t('knowledge.graph.properties')}
              </span>
              <div className={`${styles.sidebarBg} rounded-lg p-2 space-y-1`}>
                {Object.entries(node.properties).map(([key, value]) => (
                  <div key={key} className="flex justify-between text-[10px]">
                    <span className={styles.cardTextMuted}>{key}</span>
                    <span className={`${styles.cardText} font-mono`}>
                      {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Related Edges */}
          <div className="space-y-1.5">
            <span className={`text-[10px] font-bold ${styles.muted} uppercase tracking-wider`}>
              {t('knowledge.graph.relatedEdges', { count: nodeEdges.length })}
            </span>
            {nodeEdges.length === 0 ? (
              <p className={`${styles.muted} text-[10px]`}>{t('knowledge.graph.noRelatedEdges')}</p>
            ) : (
              <div className="space-y-1 max-h-40 overflow-y-auto">
                {nodeEdges.map((edge) => (
                  <div
                    key={edge.id}
                    className={`${styles.sidebarBg} rounded-md px-2 py-1.5 text-[10px] flex items-center justify-between`}
                  >
                    <span className={`${styles.cardText} truncate flex-1`}>
                      {edge.source} → {edge.target}
                    </span>
                    {edge.relationship && (
                      <span className="text-blue-400 font-bold shrink-0 ml-1">
                        {edge.relationship}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Action Buttons */}
          <div className={`space-y-1.5 pt-2 border-t ${styles.cardBorder}`}>
            {expandedNodeIds.has(node.id) ? (
              <button type="button"
                onClick={() => onCollapse(node.id)}
                className="w-full px-3 py-1.5 text-[11px] font-bold bg-red-600/20 hover:bg-red-600/30 text-red-400 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer"
              >
                <Minimize2 size={11} />
                {t('knowledge.graph.collapseNode')}
              </button>
            ) : (
              <button type="button"
                onClick={() => onExpand(node.id)}
                className="w-full px-3 py-1.5 text-[11px] font-bold bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer"
              >
                <ExternalLink size={11} />
                {t('knowledge.graph.expandNode')}
              </button>
            )}
            <button type="button"
              onClick={() => {
                onSetPathSource(node.id);
              }}
              className="w-full px-3 py-1.5 text-[11px] font-bold bg-amber-500/20 hover:bg-amber-500/30 text-amber-400 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer"
            >
              <GitBranch size={11} />
              {t('knowledge.graph.setAsPathSource')}
            </button>
          </div>
        </div>
      ) : (
        <div className={`flex items-center justify-center h-full ${styles.muted} text-[11px]`}>
          {t('knowledge.graph.noNodeSelected')}
        </div>
      )}
    </div>
  );
}
