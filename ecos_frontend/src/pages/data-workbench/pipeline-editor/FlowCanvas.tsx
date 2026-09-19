/**
 * FlowCanvas — ReactFlow canvas with custom pipeline node types
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node type enumeration (PMO-3J T1/T3).
 * @license Apache-2.0
 */

import React, { useCallback } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  Connection,
  Node,
  Edge,
  MarkerType,
  BackgroundVariant,
  type NodeTypes,
  type NodeProps,
  type HandleProps,
  Handle,
  Position,
} from '@xyflow/react';
import {
  Database, FileText, Globe, Radio, Settings, HardDrive,
  Braces, GitMerge, Upload,
  Loader2, CheckCircle, AlertCircle,
} from 'lucide-react';
import type { NodeStatus, NodeConfig, PipelineNodeType } from './types';
import EmptyCanvas from './EmptyCanvas';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';

// ─── Status badge ─────────────────────────────────────────

const StatusBadge: React.FC<{ status: NodeStatus }> = React.memo(({ status }) => {
  const { styles } = useTheme();
  switch (status) {
    case 'running':
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.accentText} ${styles.infoBg} px-1.5 py-0.5 rounded-full`}>
          <Loader2 size={10} className="animate-spin" /> running
        </span>
      );
    case 'success':
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.successText} ${styles.successBg} px-1.5 py-0.5 rounded-full`}>
          <CheckCircle size={10} /> success
        </span>
      );
    case 'error':
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.dangerText} ${styles.dangerBg} px-1.5 py-0.5 rounded-full`}>
          <AlertCircle size={10} /> error
        </span>
      );
    default:
      return (
        <span className={`inline-flex items-center gap-1 text-[10px] ${styles.muted} ${styles.sidebarBg} px-1.5 py-0.5 rounded-full`}>
          <CheckCircle size={10} /> idle
        </span>
      );
  }
});

// ─── Custom Handle ────────────────────────────────────────

const NodeHandle: React.FC<HandleProps & { position: Position }> = React.memo(
  ({ position, ...rest }) => {
    const { styles } = useTheme();
    return (
    <Handle
      {...rest}
      position={position}
      className={`!w-3 !h-3 !border-2 !${styles.cardBorder} !${styles.sidebarBg} hover:!${styles.infoBg} transition-colors`}
    />
    );
  }
);

// ─── Shared node shell ────────────────────────────────────
// A single visual shell shared by all P2-01 node types; the per-type
// body line is rendered from `config.config.*` (P2-01 schema fields).

interface NodeShellTheme {
  icon: React.FC<{ size?: number; className?: string }>;
  iconCls: string;
  headerBg: string;
  headerText: string;
  borderCls: string;
  selectedRing: string;
}

const NodeShell: React.FC<
  NodeProps & {
    theme: NodeShellTheme;
    body: (cfg: NodeConfig['config']) => React.ReactNode;
    /** Wave 3 (lower) T3: red badge dot for breakpoint nodes. */
    breakpoint?: boolean;
  }
> = React.memo(({ data, selected, theme, body, breakpoint: breakpointProp }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const config = (data ?? {}) as unknown as NodeConfig & { breakpoint?: boolean };
  const status: NodeStatus = config.nodeStatus || 'idle';
  // Wave 3 (lower) T3 — breakpoint flag can be passed as a prop (for direct
  // usage) or as `data.breakpoint` (for the runtime-wrapped variants).
  const breakpoint = breakpointProp ?? config.breakpoint ?? false;
  const Icon = theme.icon;
  return (
    <div
      className={`relative min-w-[190px] rounded-xl border-2 ${styles.cardBg} shadow-md transition-shadow ${
        selected ? `${theme.borderCls} shadow-lg ring-2 ${theme.selectedRing}` : theme.borderCls
      }`}
    >
      {/* Wave 3 (lower) T3: breakpoint red dot badge (top-right).
          Static bg-red-500 — a functional state indicator, not a
          structural color, safe across all four themes. */}
      {breakpoint && (
        <span className={`absolute -top-2 -right-2 z-10`} title={t('dw.pipeline.debug.badge')}>
          <span className={`inline-flex items-center justify-center w-4 h-4 rounded-full border-2 ${styles.cardBg}`}>
            <span className="inline-block w-2.5 h-2.5 rounded-full bg-red-500" />
          </span>
        </span>
      )}
      <NodeHandle type="target" position={Position.Top} id="top" />
      <NodeHandle type="target" position={Position.Left} id="left" />
      <div className={`flex items-center gap-2 px-3 py-2 ${theme.headerBg} rounded-t-xl border-b ${theme.borderCls}`}>
        <Icon size={16} className={`${theme.iconCls} flex-shrink-0`} />
        <span className={`text-xs font-semibold ${theme.headerText} truncate flex-1`}>
          {config.label || config.nodeType || 'Node'}
        </span>
        <StatusBadge status={status} />
      </div>
      <div className={`px-3 py-2 text-xs ${styles.cardTextMuted}`}>
        {body(config.config || {})}
      </div>
      <NodeHandle type="source" position={Position.Bottom} id="bottom" />
      <NodeHandle type="source" position={Position.Right} id="right" />
    </div>
  );
});

// Type alias for the per-node config object (P2-01 schema fields).
type NodeConfigFields = NodeConfig['config'];

// ─── Per-type node components ─────────────────────────────
// Each reads its P2-01 config fields and renders a one-line summary.
// All labels go through i18n (t("dw.xxx")) — 0 hardcoded Chinese.

const SourceJdbcNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: Database,
        iconCls: styles.accentText,
        headerBg: styles.infoBg,
        headerText: styles.accentText,
        borderCls: styles.infoBorder,
        selectedRing: styles.accentBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.datasourceId ? (
          <div className="truncate" title={cfg.datasourceId}>
            {t('dw.pipeline.node.body.datasource')}: <span className={`font-mono ${styles.accentText}`}>{cfg.datasourceId}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.selectDatasource')}</div>
        )
      }
    />
  );
};

const SourceCsvNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: FileText,
        iconCls: styles.successText,
        headerBg: styles.successBg,
        headerText: styles.successText,
        borderCls: styles.successBorder,
        selectedRing: styles.successBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.filePath ? (
          <div className="truncate" title={cfg.filePath}>
            {t('dw.pipeline.node.body.file')}: <span className={`font-mono ${styles.successText}`}>{cfg.filePath}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.selectFile')}</div>
        )
      }
    />
  );
};

const SourceRestNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: Globe,
        iconCls: styles.infoText,
        headerBg: styles.infoBg,
        headerText: styles.infoText,
        borderCls: styles.infoBorder,
        selectedRing: styles.infoBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.url ? (
          <div className="truncate" title={cfg.url}>
            {cfg.method || 'GET'} <span className={`font-mono ${styles.infoText}`}>{cfg.url}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.enterUrl')}</div>
        )
      }
    />
  );
};

const SourceCdcNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: Radio,
        iconCls: styles.warningText,
        headerBg: styles.warningBg,
        headerText: styles.warningText,
        borderCls: styles.warningBorder,
        selectedRing: styles.warningBorder,
      }}
      body={() => (
        <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.cdcFlagshipOnly')}</div>
      )}
    />
  );
};

const TransformSqlNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: Settings,
        iconCls: styles.successText,
        headerBg: styles.successBg,
        headerText: styles.successText,
        borderCls: styles.successBorder,
        selectedRing: styles.successBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.transformSql ? (
          <div className="truncate font-mono" title={cfg.transformSql}>
            {cfg.transformSql}
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.enterSql')}</div>
        )
      }
    />
  );
};

const OutputObjectNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: HardDrive,
        iconCls: styles.cardTextMuted,
        headerBg: styles.cardBg,
        headerText: styles.cardText,
        borderCls: styles.inputBorder,
        selectedRing: styles.inputBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.targetTable ? (
          <div className="truncate" title={cfg.targetTable}>
            {t('dw.pipeline.node.body.target')}: <span className={`font-mono ${styles.cardTextMuted}`}>{cfg.targetTable}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.selectTarget')}</div>
        )
      }
    />
  );
};

// ─── Wave 5: TRANSFORM_UDF / JOIN / SINK nodes ─────────────

const TransformUdfNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: Braces,
        iconCls: styles.infoText,
        headerBg: styles.infoBg,
        headerText: styles.infoText,
        borderCls: styles.infoBorder,
        selectedRing: styles.infoBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.udfId ? (
          <div className="truncate" title={cfg.udfId}>
            {t('dw.pipeline.node.body.udf')}: <span className={`font-mono ${styles.infoText}`}>{cfg.udfName || cfg.udfId}</span>
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.selectUdf')}</div>
        )
      }
    />
  );
};

const JoinNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: GitMerge,
        iconCls: styles.accentText,
        headerBg: styles.cardBg,
        headerText: styles.accentText,
        borderCls: styles.cardBorder,
        selectedRing: styles.accentBorder,
      }}
      body={(cfg: NodeConfigFields) => {
        const jt = cfg.joinType || 'inner';
        const keys = cfg.joinKeys?.length ? cfg.joinKeys.join(', ') : '';
        return (
          <div className="truncate" title={`${jt}${keys ? ' ' + keys : ''}`}>
            {String(jt).toUpperCase()}{keys ? <span className={`ml-1 font-mono ${styles.accentText}`}>{keys}</span> : null}
          </div>
        );
      }}
    />
  );
};

const SinkNode: React.FC<NodeProps> = (props) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <NodeShell
      {...props}
      theme={{
        icon: Upload,
        iconCls: styles.successText,
        headerBg: styles.successBg,
        headerText: styles.successText,
        borderCls: styles.successBorder,
        selectedRing: styles.successBorder,
      }}
      body={(cfg: NodeConfigFields) =>
        cfg.targetTable ? (
          <div className="truncate" title={`${cfg.targetDatasourceId || ''} ${cfg.targetTable}`}>
            {cfg.targetDatasourceId ? (
              <>
                {t('dw.pipeline.node.body.target')}: <span className={`font-mono ${styles.successText}`}>{cfg.targetDatasourceId}</span>
                <span className={`mx-1 ${styles.cardTextMuted}`}>/</span>
                <span className={`font-mono ${styles.successText}`}>{cfg.targetTable}</span>
              </>
            ) : (
              <>
                <span className={`font-mono ${styles.successText}`}>{cfg.targetTable}</span>
              </>
            )}
          </div>
        ) : (
          <div className={`italic ${styles.cardTextMuted}`}>{t('dw.pipeline.node.body.selectSink')}</div>
        )
      }
    />
  );
};

// ─── Node type registry (P2-01 enumeration, Wave 5: 6 → 9) ──

export const CUSTOM_NODE_TYPES: NodeTypes = {
  SOURCE_JDBC: SourceJdbcNode,
  SOURCE_CSV: SourceCsvNode,
  SOURCE_REST: SourceRestNode,
  SOURCE_CDC: SourceCdcNode,
  TRANSFORM_SQL: TransformSqlNode,
  TRANSFORM_UDF: TransformUdfNode,
  JOIN: JoinNode,
  SINK: SinkNode,
  OUTPUT_OBJECT: OutputObjectNode,
};

// ─── MiniMap color helper (P2-01, Wave 5: 6 → 9) ──────────

export const miniMapNodeColor = (node: Node): string => {
  switch (node.type as PipelineNodeType) {
    case 'SOURCE_JDBC': return '#3b82f6';
    case 'SOURCE_CSV': return '#10b981';
    case 'SOURCE_REST': return '#0ea5e9';
    case 'SOURCE_CDC': return '#f97316';
    case 'SOURCE_MINIO': return '#6366f1';
    case 'TRANSFORM_SQL': return '#14b8a6';
    case 'TRANSFORM_UDF': return '#06b6d4';
    case 'JOIN': return '#8b5cf6';
    case 'SINK': return '#22c55e';
    case 'SINK_MINIO': return '#6366f1';
    case 'OUTPUT_OBJECT': return '#64748b';
    default: return '#94a3b8';
  }
};

// ─── Wave 3 (lower) T2: danger-marked node shell ──────────
// Wrapped around the normal NodeShell content so the validation-failure red
// border/ring are visible without mutating the original node component.

const DangerShell: React.FC<{ children?: React.ReactNode }> = React.memo(({ children }) => {
  const { styles } = useTheme();
  return (
    <div className={`relative rounded-xl border-2 ${styles.dangerBorder} shadow-lg ${styles.dangerBg} p-[2px]`}>
      <div className={`rounded-lg ${styles.cardBg}`}>
        {children as React.ReactNode}
      </div>
    </div>
  );
});

// ─── FlowCanvas Props ─────────────────────────────────────

interface FlowCanvasProps {
  nodes: Node[];
  edges: Edge[];
  onNodesChange: ReturnType<typeof import('@xyflow/react').useNodesState>[2];
  onEdgesChange: ReturnType<typeof import('@xyflow/react').useEdgesState>[2];
  onConnect: (connection: Connection) => void;
  onInit: (instance: unknown) => void;
  onNodeClick: (event: React.MouseEvent, node: Node) => void;
  onPaneClick: () => void;
  onDragOver: (event: React.DragEvent<HTMLDivElement>) => void;
  onDrop: (event: React.DragEvent<HTMLDivElement>) => void;
  styles: Record<string, string>;
  /** Wave 3 (lower) T2: ids of nodes that failed local pre-checks. */
  invalidNodeIds?: Set<string>;
  /** Wave 3 (lower) T3: ids of nodes that have a breakpoint set (red dot). */
  breakpointNodeIds?: Set<string>;
}

const FlowCanvas: React.FC<FlowCanvasProps> = ({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onInit,
  onNodeClick,
  onPaneClick,
  onDragOver,
  onDrop,
  styles,
  invalidNodeIds,
  breakpointNodeIds,
}) => {
  const { t } = useLanguage();
  const onConnectWrapped = useCallback(
    (connection: Connection) => {
      onConnect(connection);
    },
    [onConnect]
  );

  // Wave 3 (lower) T2 + T3: per-node wrapping — injects (a) a danger frame
  // for nodes that failed local pre-checks, and (b) a `breakpoint` prop
  // so the node shell can render the red dot badge.
  const { displayNodes, displayNodeTypes } = React.useMemo<{
    displayNodes: Node[];
    displayNodeTypes: Record<string, React.ComponentType<NodeProps>>;
  }>(() => {
    const invalidated = (invalidNodeIds && invalidNodeIds.size > 0) || (breakpointNodeIds && breakpointNodeIds.size > 0);
    if (!invalidated) {
      return {
        displayNodes: nodes,
        displayNodeTypes: CUSTOM_NODE_TYPES as Record<string, React.ComponentType<NodeProps>>,
      };
    }
    const nextTypes: Record<string, React.ComponentType<NodeProps>> = {
      ...(CUSTOM_NODE_TYPES as Record<string, React.ComponentType<NodeProps>>),
    };
    const makeVariant = (base: React.ComponentType<NodeProps>, key: string, extra: { danger?: boolean; breakpoint?: boolean }) => {
      const variantKey = `${key}${extra.danger ? '__danger' : ''}${extra.breakpoint ? '__bp' : ''}`;
      if (nextTypes[variantKey]) return nextTypes[variantKey];
      const Wrapped = ((props: NodeProps) => {
        const content = <base {...props} />;
        if (extra.danger) {
          return <DangerShell>{content}</DangerShell>;
        }
        return content;
      }) as unknown as React.ComponentType<NodeProps>;
      nextTypes[variantKey] = Wrapped;
      return Wrapped;
    };
    const display = nodes.map((n) => {
      const isInvalid = invalidNodeIds?.has(n.id) ?? false;
      const isBp = breakpointNodeIds?.has(n.id) ?? false;
      if (!isInvalid && !isBp) return n;
      const base = nextTypes[n.type as string];
      if (!base) return n;
      makeVariant(base, n.type as string, { danger: isInvalid, breakpoint: isBp });
      const newType = `${n.type}${isInvalid ? '__danger' : ''}${isBp ? '__bp' : ''}` as string;
      // The node shell reads `breakpoint` from `data` so we don't need to
      // rely on any specific React component prop contract.
      const dataWithBp = isBp
        ? { ...(n.data as Record<string, unknown>), breakpoint: true }
        : (n.data as Record<string, unknown>);
      return { ...n, type: newType as unknown as Node['type'], data: dataWithBp as Node['data'] };
    });
    return { displayNodes: display, displayNodeTypes: nextTypes };
  }, [nodes, invalidNodeIds, breakpointNodeIds]);

  return (
    <div className="flex-1 h-full">
      <ReactFlow
        nodes={displayNodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnectWrapped}
        onInit={onInit}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        onDragOver={onDragOver}
        onDrop={onDrop}
        nodeTypes={displayNodeTypes}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        multiSelectionKeyCode="Shift"
        nodesConnectable
        snapToGrid
        snapGrid={[16, 16]}
        defaultEdgeOptions={{
          animated: true,
          style: { stroke: '#94a3b8', strokeWidth: 2 },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 16, height: 16 },
        }}
        proOptions={{ hideAttribution: true }}
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1.5} color="#e2e8f0" />
        <Controls
          className={`!rounded-lg !shadow-sm ${styles.cardBg} ${styles.cardBorder}`}
          position="bottom-right"
        />
        <MiniMap
          nodeColor={miniMapNodeColor}
          maskColor="rgba(0,0,0,0.05)"
          className={`!rounded-lg ${styles.sidebarBg} ${styles.sidebarBorder}`}
          position="bottom-left"
        />
        {nodes.length === 0 && (
          <Panel position="top-center">
            <EmptyCanvas />
          </Panel>
        )}
        <Panel position="top-left" className="!ml-2 !mt-2">
          <div className={`backdrop-blur-sm border rounded-lg px-3 py-1.5 text-xs shadow-sm ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}>
            {t('dw.pipeline.canvas.nodes')}: <span className={`font-semibold ${styles.cardText}`}>{nodes.length}</span>
            <span className={`mx-1.5 ${styles.cardTextMuted}`}>|</span>
            {t('dw.pipeline.canvas.edges')}: <span className={`font-semibold ${styles.cardText}`}>{edges.length}</span>
          </div>
        </Panel>
      </ReactFlow>
    </div>
  );
};

export default FlowCanvas;
