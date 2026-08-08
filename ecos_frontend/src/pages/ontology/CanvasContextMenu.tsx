/**
 * CanvasContextMenu — 右键菜单浮层
 *
 * 在画布实体节点上右键时显示，绝对定位在鼠标位置。
 * 菜单项: 编辑(Pencil) / 删除(Trash2) / 添加关系(Link) / 添加属性(Plus)
 * 点击外部或菜单项后自动关闭。
 *
 * @license Apache-2.0
 */

import React, { useEffect, useRef } from 'react';
import { Pencil, Trash2, Link, Plus } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';

// ── Props ──────────────────────────────────────────────────────

export interface CanvasContextMenuProps {
  x: number;
  y: number;
  entityId: string;
  entityCode: string;
  onEdit: (id: string) => void;
  onDelete: (id: string) => void;
  onCreateRelation: (id: string) => void;
  onAddProperty: (id: string) => void;
  onClose: () => void;
}

// ── 菜单项定义 ─────────────────────────────────────────────────

interface MenuItem {
  icon: React.ReactNode;
  labelKey: string;
  action: () => void;
  hotkey?: string;
  danger?: boolean;
}

// ── 组件 ──────────────────────────────────────────────────────

export default function CanvasContextMenu({
  x,
  y,
  entityId,
  entityCode,
  onEdit,
  onDelete,
  onCreateRelation,
  onAddProperty,
  onClose,
}: CanvasContextMenuProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const menuRef = useRef<HTMLDivElement>(null);

  // ── 点击外部关闭 ──
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    // 延迟绑定，避免触发右键的 click 事件立即关闭菜单
    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleClickOutside);
    }, 0);
    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [onClose]);

  // ── 按 Escape 关闭 ──
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  // ── 修正位置防止溢出视口 ──
  const adjustedX = Math.min(x, window.innerWidth - 180);
  const adjustedY = Math.min(y, window.innerHeight - 200);

  // ── 菜单项 ──
  const items: MenuItem[] = [
    {
      icon: <Pencil size={13} />,
      labelKey: 'ontology.designer.contextMenu.edit',
      action: () => onEdit(entityId),
      hotkey: 'Enter',
    },
    {
      icon: <Trash2 size={13} />,
      labelKey: 'ontology.designer.contextMenu.delete',
      action: () => onDelete(entityId),
      hotkey: 'Del',
      danger: true,
    },
    {
      icon: <Link size={13} />,
      labelKey: 'ontology.designer.contextMenu.addRelation',
      action: () => onCreateRelation(entityId),
    },
    {
      icon: <Plus size={13} />,
      labelKey: 'ontology.designer.contextMenu.addProperty',
      action: () => onAddProperty(entityId),
    },
  ];

  return (
    <div
      ref={menuRef}
      className={`fixed z-[9999] w-44 py-1 rounded-lg shadow-xl border ${styles.cardBorder} ${styles.cardBg}`}
      style={{ left: adjustedX, top: adjustedY }}
    >
      {/* 实体名头部 */}
      <div className="px-3 py-1.5 border-b border-slate-700/50">
        <span className="text-[10px] font-mono text-slate-400 truncate block">
          {entityCode}
        </span>
      </div>

      {items.map((item, i) => (
        <button
          key={i}
          onClick={() => {
            item.action();
            onClose();
          }}
          className={`w-full flex items-center gap-2.5 px-3 py-1.5 text-xs
            hover:bg-slate-700/50 transition-colors text-left
            ${item.danger ? 'text-red-400 hover:text-red-300' : 'text-slate-300 hover:text-white'}`}
        >
          <span className="shrink-0">{item.icon}</span>
          <span className="flex-1">{t(item.labelKey)}</span>
          {item.hotkey && (
            <span className="text-[9px] text-slate-600 ml-4">{item.hotkey}</span>
          )}
        </button>
      ))}
    </div>
  );
}
