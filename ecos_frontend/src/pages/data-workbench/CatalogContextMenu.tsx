/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * CatalogContextMenu — 数据目录树右键菜单
 * 定位: absolute 浮层，鼠标位置 (x, y)
 */

import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Eye, GitBranch, Shield, X, ChevronLeft, ChevronRight, Loader2 } from "lucide-react";
import { fetchPreview } from "../../api";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { TreeNode } from "./CatalogTree";

interface CatalogContextMenuProps {
  node: TreeNode;
  x: number;
  y: number;
  onClose: () => void;
}

/* ── 预览弹窗 ── */
function PreviewModal({ resourceId, resourceName, onClose }: { resourceId: string; resourceName: string; onClose: () => void }) {
  const { styles } = useTheme();
  const { locale } = useLanguage();
  const [rows, setRows] = useState<Record<string, any>[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const pageSize = 25;

  useEffect(() => {
    (async () => {
      setLoading(true);
      try { const data = await fetchPreview(resourceId, 100); setRows(data.rows || []); } catch { setRows([]); } finally { setLoading(false); }
    })();
  }, [resourceId]);

  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const pageRows = rows.slice(page * pageSize, (page + 1) * pageSize);
  const columns = Object.keys(rows[0] || {});

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={onClose}>
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-2xl w-[90vw] max-w-4xl max-h-[80vh] flex flex-col`} onClick={e => e.stopPropagation()}>
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder}`}>
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            <Eye className="w-4 h-4 text-indigo-500" />
            {locale === "zh" ? "预览" : "Preview"}: {resourceName}
          </h3>
          <button onClick={onClose} className="opacity-50 hover:opacity-100 transition"><X className="w-4 h-4" /></button>
        </div>
        <div className="flex-1 overflow-auto p-2">
          {loading ? (
            <div className="py-16 text-center"><Loader2 className="w-6 h-6 mx-auto animate-spin opacity-40" /></div>
          ) : columns.length === 0 ? (
            <p className={`text-xs text-center py-16 ${styles.muted}`}>{locale === "zh" ? "无预览数据" : "No preview data"}</p>
          ) : (
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className={`border-b ${styles.cardBorder}`}>
                  <th className={`text-left py-1.5 px-2 font-semibold ${styles.muted} w-8`}>#</th>
                  {columns.map(col => (
                    <th key={col} className={`text-left py-1.5 px-2 font-semibold ${styles.muted} whitespace-nowrap`}>{col}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {pageRows.map((row, i) => (
                  <tr key={i} className="border-b border-current/5 hover:bg-black/5 dark:hover:${styles.cardBg}/5">
                    <td className={`py-1 px-2 ${styles.muted}`}>{page * pageSize + i + 1}</td>
                    {columns.map(col => (
                      <td key={col} className="py-1 px-2 whitespace-nowrap max-w-[300px] truncate">
                        {row[col] == null ? <span className="italic opacity-30">NULL</span> : String(row[col])}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {totalPages > 1 && (
          <div className={`flex items-center justify-between px-4 py-2 border-t ${styles.cardBorder} text-xs`}>
            <span className={styles.muted}>
              {rows.length} {locale === "zh" ? "行" : "rows"} · {locale === "zh" ? "第" : "Page"} {page + 1}/{totalPages}
            </span>
            <div className="flex items-center gap-1">
              <button disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))} className="p-1 rounded opacity-60 hover:opacity-100 disabled:opacity-20 transition">
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button disabled={page >= totalPages - 1} onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} className="p-1 rounded opacity-60 hover:opacity-100 disabled:opacity-20 transition">
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ── 主组件 ── */
export default function CatalogContextMenu({ node, x, y, onClose }: CatalogContextMenuProps) {
  const navigate = useNavigate();
  const { styles } = useTheme();
  const { locale } = useLanguage();
  const menuRef = useRef<HTMLDivElement>(null);
  const [showPreview, setShowPreview] = useState(false);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) onClose();
    };
    document.addEventListener("mousedown", handler, true);
    return () => document.removeEventListener("mousedown", handler, true);
  }, [onClose]);

  const items = [
    { icon: Eye, label: locale === "zh" ? "预览数据" : "Preview Data", action: () => setShowPreview(true) },
    { icon: GitBranch, label: locale === "zh" ? "查看血缘" : "View Lineage", action: () => navigate(`/lineage?highlight=${encodeURIComponent(node.name)}`) },
    { icon: Shield, label: locale === "zh" ? "配置DQ规则" : "Configure DQ Rules", action: () => navigate(`/dq_dashboard?table=${encodeURIComponent(node.name)}`) },
  ];

  return (
    <>
      {showPreview && (
        <PreviewModal resourceId={node.meta.resourceId!} resourceName={node.name} onClose={() => setShowPreview(false)} />
      )}
      <div
        ref={menuRef}
        className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-xl py-1 z-40 min-w-[180px]`}
        style={{ position: "fixed", left: Math.min(x, window.innerWidth - 200), top: Math.min(y, window.innerHeight - 140) }}
      >
        {items.map((item, i) => (
          <button
            key={i}
            onClick={() => { item.action(); onClose(); }}
            className={`w-full flex items-center gap-2 px-3 py-2 text-xs ${styles.cardText} hover:bg-black/5 dark:hover:${styles.cardBg}/5 transition cursor-pointer`}
          >
            <item.icon className="w-3.5 h-3.5 opacity-60" />{item.label}
          </button>
        ))}
      </div>
    </>
  );
}
