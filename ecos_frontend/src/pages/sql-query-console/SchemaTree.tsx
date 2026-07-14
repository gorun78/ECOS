/**
 * SQL Query Console — Schema Tree Browser
 * @license Apache-2.0
 */
import React, { useState, useEffect } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import type { SchemaTreeNode } from './types';

interface SchemaTreeProps {
  datasourceId: string | null;
}

const Icon = ({ name, size = 14, className = '' }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export default function SchemaTree({ datasourceId }: SchemaTreeProps) {
  const { styles } = useTheme();
  const [tree, setTree] = useState<SchemaTreeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!datasourceId) { setTree([]); return; }
    setLoading(true);
    apiFetchData<any>(`/api/v1/engine/data/query/schema/${datasourceId}`)
      .then(data => setTree(Array.isArray(data) ? data : (data?.schemas || [])))
      .catch(() => setTree([]))
      .finally(() => setLoading(false));
  }, [datasourceId]);

  const toggle = (key: string) => {
    setExpanded(prev => {
      const next = new Set(prev);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  };

  const renderNode = (node: SchemaTreeNode, path: string, depth: number) => {
    const isExpanded = expanded.has(path);
    const hasChildren = node.children && node.children.length > 0;
    const isColumn = node.type === 'column';
    const paddingLeft = 8 + depth * 16;

    return (
      <div key={path}>
        <div
          onClick={() => hasChildren && toggle(path)}
          className={`flex items-center gap-1 py-0.5 cursor-pointer hover:bg-white/5 text-[11px] ${isColumn ? 'cursor-default' : ''}`}
          style={{ paddingLeft }}
        >
          {node.type === 'schema' && <Icon name="Folder" size={12} className="text-amber-400 shrink-0" />}
          {node.type === 'table' && <Icon name="Table" size={12} className="text-blue-400 shrink-0" />}
          {node.type === 'view' && <Icon name="Eye" size={12} className="text-purple-400 shrink-0" />}
          {node.type === 'column' && <Icon name="Hash" size={10} className="text-slate-500 shrink-0" />}
          <span className={`truncate ${isColumn ? 'text-slate-400' : styles.cardText}`}>{node.name}</span>
          {isColumn && node.dataType && (
            <span className="text-[9px] text-cyan-500/70 ml-auto shrink-0">{node.dataType}</span>
          )}
          {hasChildren && (
            <Icon name={isExpanded ? 'ChevronDown' : 'ChevronRight'} size={10} className="text-slate-500 ml-auto shrink-0" />
          )}
        </div>
        {hasChildren && isExpanded && node.children!.map((child, i) =>
          renderNode(child, `${path}/${child.name}`, depth + 1)
        )}
      </div>
    );
  };

  return (
    <div className={`w-56 border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden`}>
      <div className={`px-2.5 py-2 border-b ${styles.cardBorder} text-[10px] font-bold uppercase text-slate-400 flex items-center gap-1.5`}>
        <Icon name="FolderTree" size={13} />
        Schema 浏览器
      </div>
      <div className="flex-1 overflow-y-auto px-1 py-1">
        {loading ? (
          <div className="flex items-center justify-center py-8 text-slate-500 text-[11px]">
            <Icon name="Loader2" size={14} className="animate-spin mr-1.5" />加载中...
          </div>
        ) : tree.length === 0 ? (
          <div className="text-center py-8 text-slate-500 text-[10px]">
            {datasourceId ? '无 Schema 数据' : '请先选择数据源'}
          </div>
        ) : (
          tree.map((node, i) => renderNode(node, node.name, 0))
        )}
      </div>
    </div>
  );
}
