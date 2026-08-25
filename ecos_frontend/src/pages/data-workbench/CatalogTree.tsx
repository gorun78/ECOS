/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * CatalogTree — 数据目录树形结构组件
 * 四层树: datasource → schema → table → field
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  Database, Folder, Table, Columns,
  ChevronRight, ChevronDown, Search, Loader2,
} from "lucide-react";
import { BulkResource, fetchAllResources, fetchFields } from "../../api";
import { DataField } from "../../types";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";

/* ── 节点数据结构 ── */
export type TreeNodeType = "datasource" | "schema" | "table" | "field";

export interface TreeNode {
  id: string;
  name: string;
  type: TreeNodeType;
  children: TreeNode[];
  meta: {
    datasourceId?: string;
    resourceId?: string;
    resourceType?: string;
    dataType?: string;
    nullable?: boolean;
    sourcePath?: string;
    fieldCount?: number;
  };
}

const ICON_MAP: Record<TreeNodeType, React.FC<{ className?: string }>> = {
  datasource: Database, schema: Folder, table: Table, field: Columns,
};

function buildTree(resources: BulkResource[]): TreeNode[] {
  const dsMap = new Map<string, TreeNode>();
  for (const r of resources) {
    if (!dsMap.has(r.datasourceId)) {
      dsMap.set(r.datasourceId, {
        id: r.datasourceId, name: r.datasourceName,
        type: "datasource", children: [], meta: {},
      });
    }
    const dsNode = dsMap.get(r.datasourceId)!;
    const schemaName = r.sourcePath?.split(".")[0] || "default";
    let schemaNode = dsNode.children.find(c => c.type === "schema" && c.name === schemaName);
    if (!schemaNode) {
      schemaNode = { id: `${r.datasourceId}:${schemaName}`, name: schemaName, type: "schema", children: [], meta: {} };
      dsNode.children.push(schemaNode);
    }
    schemaNode.children.push({
      id: `table:${r.resourceId}`, name: r.resourceName, type: "table", children: [],
      meta: { datasourceId: r.datasourceId, resourceId: r.resourceId, resourceType: r.resourceType, sourcePath: r.sourcePath, fieldCount: r.fieldCount },
    });
  }
  return Array.from(dsMap.values());
}

function filterTree(nodes: TreeNode[], query: string): { matches: TreeNode[]; expandIds: Set<string> } {
  const expandIds = new Set<string>();
  const q = query.toLowerCase();
  function walk(list: TreeNode[]): TreeNode[] {
    const result: TreeNode[] = [];
    for (const node of list) {
      const childMatch = walk(node.children);
      const selfMatch = node.name.toLowerCase().includes(q);
      if (selfMatch || childMatch.length > 0) {
        expandIds.add(node.id);
        result.push({ ...node, children: childMatch });
      }
    }
    return result;
  }
  return { matches: walk(nodes), expandIds };
}

/* ── 组件 ── */
interface CatalogTreeProps {
  onContextMenu: (e: React.MouseEvent, node: TreeNode) => void;
}

export default function CatalogTree({ onContextMenu }: CatalogTreeProps) {
  const { styles } = useTheme();
  const { locale } = useLanguage();

  const [tree, setTree] = useState<TreeNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState("");
  const [loadedFields, setLoadedFields] = useState<Record<string, DataField[]>>({});
  const [loadingFields, setLoadingFields] = useState<Set<string>>(new Set());

  useEffect(() => {
    (async () => {
      setLoading(true);
      try { const bulk = await fetchAllResources(); setTree(buildTree(bulk)); } catch { setTree([]); }
      finally { setLoading(false); }
    })();
  }, []);

  const loadTableFields = useCallback(async (node: TreeNode) => {
    const rid = node.meta.resourceId;
    if (!rid || loadedFields[rid] || loadingFields.has(rid)) return;
    setLoadingFields(prev => new Set(prev).add(rid));
    try {
      setLoadedFields(prev => ({ ...prev, [rid]: await fetchFields(rid) }));
    } catch {
      setLoadedFields(prev => ({ ...prev, [rid]: [] }));
    } finally {
      setLoadingFields(prev => { const n = new Set(prev); n.delete(rid); return n; });
    }
  }, [loadedFields, loadingFields]);

  const { matches, expandIds } = useMemo(() => {
    if (!search.trim()) return { matches: tree, expandIds: new Set<string>() };
    return filterTree(tree, search.trim());
  }, [tree, search]);

  useEffect(() => {
    if (expandIds.size > 0) setExpanded(prev => new Set([...prev, ...expandIds]));
  }, [expandIds]);

  const toggleExpand = useCallback((node: TreeNode) => {
    setExpanded(prev => {
      const next = new Set(prev);
      if (next.has(node.id)) { next.delete(node.id); }
      else { next.add(node.id); if (node.type === "table") loadTableFields(node); }
      return next;
    });
  }, [loadTableFields]);

  function highlight(text: string, q: string) {
    if (!q.trim()) return text;
    const idx = text.toLowerCase().indexOf(q.toLowerCase());
    if (idx < 0) return text;
    return <>{text.slice(0, idx)}<mark className={`${styles.warningBg} dark:${styles.warningBg} rounded px-0.5`}>{text.slice(idx, idx + q.length)}</mark>{text.slice(idx + q.length)}</>;
  }

  function renderNode(node: TreeNode, depth: number): React.ReactNode {
    const isExpanded = expanded.has(node.id);
    const fieldLen = loadedFields[node.meta.resourceId!]?.length;
    const hasChildren = node.type === "table"
      ? (fieldLen ?? node.meta.fieldCount ?? 0) > 0
      : node.children.length > 0;

    let childrenToRender: TreeNode[] = node.children;
    if (node.type === "table" && node.meta.resourceId && loadedFields[node.meta.resourceId!]) {
      childrenToRender = (loadedFields[node.meta.resourceId!] || []).map((f): TreeNode => ({
        id: `field:${f.fieldId}`, name: f.fieldName, type: "field",
        children: [] as TreeNode[], meta: { dataType: f.dataType, nullable: f.nullable },
      }));
    }

    const Icon = ICON_MAP[node.type];
    const isLoading = node.type === "table" && loadingFields.has(node.meta.resourceId!);
    const q = search.trim();

    return (
      <li key={node.id}>
        <div
          className={`flex items-center gap-1 py-1 px-1 rounded cursor-pointer hover:bg-black/5 dark:hover:${styles.cardBg}/5 text-xs ${node.type === "table" ? "font-medium" : ""}`}
          style={{ paddingLeft: `${depth * 16 + 4}px` }}
          onClick={() => hasChildren && toggleExpand(node)}
          onContextMenu={e => { e.preventDefault(); if (node.type === "table") onContextMenu(e, node); }}
        >
          {hasChildren
            ? (isExpanded ? <ChevronDown className="w-3 h-3 shrink-0 opacity-50" /> : <ChevronRight className="w-3 h-3 shrink-0 opacity-50" />)
            : <span className="w-3 shrink-0" />}
          <Icon className="w-3.5 h-3.5 shrink-0 opacity-60" />
          <span className="truncate">{q ? highlight(node.name, q) : node.name}</span>
          {node.type === "table" && node.meta.fieldCount != null && (
            <span className="ml-auto text-[10px] opacity-40 shrink-0">{node.meta.fieldCount}</span>
          )}
          {node.type === "field" && node.meta.dataType && (
            <span className="ml-auto text-[10px] opacity-40 shrink-0">{node.meta.dataType}</span>
          )}
          {isLoading && <Loader2 className="w-3 h-3 animate-spin opacity-50 shrink-0" />}
        </div>
        {hasChildren && isExpanded && (
          <ul className="list-none">{childrenToRender.map(child => renderNode(child, depth + 1))}</ul>
        )}
      </li>
    );
  }

  return (
    <div className={`flex flex-col h-full ${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
      <div className={`flex items-center gap-2 px-3 py-2 border-b ${styles.cardBorder}`}>
        <Search className="w-3.5 h-3.5 opacity-40 shrink-0" />
        <input
          type="text"
          className={`bg-transparent border-0 outline-none flex-1 text-xs ${styles.inputText} placeholder:opacity-40`}
          placeholder={locale === "zh" ? "搜索数据源、表、字段..." : "Search datasources, tables, fields..."}
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>
      <div className="flex-1 overflow-y-auto p-2">
        {loading ? (
          <div className="py-8 text-center">
            <Loader2 className="w-5 h-5 mx-auto animate-spin opacity-40" />
            <p className={`text-xs mt-2 ${styles.muted}`}>{locale === "zh" ? "加载中..." : "Loading..."}</p>
          </div>
        ) : matches.length === 0 ? (
          <div className="py-8 text-center">
            <Database className="w-6 h-6 mx-auto opacity-20" />
            <p className={`text-xs mt-2 ${styles.muted}`}>
              {search.trim()
                ? (locale === "zh" ? "未找到匹配的资源" : "No matching resources")
                : (locale === "zh" ? "暂无数据资源" : "No data resources")}
            </p>
          </div>
        ) : (
          <ul className="list-none space-y-0.5">{matches.map(node => renderNode(node, 0))}</ul>
        )}
      </div>
    </div>
  );
}
