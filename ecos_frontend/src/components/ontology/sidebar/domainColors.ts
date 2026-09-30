/**
 * domainColors — 业务划分域配色 / code 归一化纯工具
 *
 * 从 `components/ontology/Sidebar.tsx` 机械抽取（H6-T4 组件行数治理），
 * 函数体与原实现逐字一致，不改行为、不改样式类名。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export function getDomainColorText(color: string): string {
  switch (color) {
    case 'blue': return 'text-blue-500';
    case 'emerald': return 'text-emerald-500';
    case 'amber': return 'text-amber-500';
    case 'purple': return 'text-purple-500';
    case 'rose': return 'text-rose-500';
    case 'indigo': return 'text-indigo-500';
    case 'slate': return 'text-slate-500';
    default: return 'text-slate-500';
  }
}

export function getDomainColorDotClass(color: string): string {
  switch (color) {
    case 'blue': return 'bg-blue-500';
    case 'emerald': return 'bg-emerald-500';
    case 'amber': return 'bg-amber-500';
    case 'purple': return 'bg-purple-500';
    case 'rose': return 'bg-rose-500';
    case 'indigo': return 'bg-indigo-500';
    case 'slate': return 'bg-slate-500';
    default: return 'bg-slate-500';
  }
}

/** code 归一化：小写 + 仅保留 [a-z0-9_]（后端 code 唯一约束） */
export function normalizeDomainCode(raw: string): string {
  return raw.trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
}
