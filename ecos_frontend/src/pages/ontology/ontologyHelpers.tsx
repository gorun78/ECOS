/**
 * ontologyHelpers — 本体设计器共享常量和辅助函数
 *
 * 从 DomainDesignerView.tsx 提取，供 EntityTreePanel / DomainCanvas / PropertyEditor 共用。
 *
 * @license Apache-2.0
 */

import React from 'react';
import {
  Database, Box, List, Layers, Globe,
} from 'lucide-react';

// ── 辅助函数：从 URL hash 中提取 domainCode ──────────────────

/**
 * 从当前 URL hash 中提取 domainCode 参数。
 * 期望格式: #/ontology_workbench/domains/:code
 * 也支持查询参数: #/ontology_workbench?domain=:code
 */
export function extractDomainCode(): string | null {
  const hash = window.location.hash.replace(/^#/, '');

  // 匹配路径格式 /ontology_workbench/domains/:code
  const pathMatch = hash.match(/\/ontology_workbench\/domains\/([^/?]+)/);
  if (pathMatch) return decodeURIComponent(pathMatch[1]);

  // 匹配查询参数格式 ?domain=:code
  const params = new URLSearchParams(hash.split('?')[1] || '');
  const domainParam = params.get('domain');
  if (domainParam) return decodeURIComponent(domainParam);

  return null;
}

// ── 实体类型图标 & 颜色映射 ──────────────────────────────────

export const ENTITY_TYPE_CONFIG: Record<string, { icon: React.ReactNode; color: string }> = {
  MASTER: {
    icon: <Database size={12} className="text-amber-400" />,
    color: 'text-amber-400',
  },
  TRANSACTION: {
    icon: <List size={12} className="text-emerald-400" />,
    color: 'text-emerald-400',
  },
  EVENT: {
    icon: <Layers size={12} className="text-blue-400" />,
    color: 'text-blue-400',
  },
  REFERENCE: {
    icon: <Globe size={12} className="text-purple-400" />,
    color: 'text-purple-400',
  },
  default: {
    icon: <Box size={12} className="text-slate-400" />,
    color: 'text-slate-400',
  },
};

export function getEntityTypeLabel(et: string, t: (key: string) => string): string {
  const map: Record<string, string> = {
    MASTER: t('ontology.designer.entityType.master'),
    TRANSACTION: t('ontology.designer.entityType.transaction'),
    EVENT: t('ontology.designer.entityType.event'),
    REFERENCE: t('ontology.designer.entityType.reference'),
  };
  return map[et] || et;
}
