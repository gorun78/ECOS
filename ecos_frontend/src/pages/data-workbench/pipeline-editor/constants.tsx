/**
 * Pipeline Editor — palette & constants
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import { Database, GitBranch, ArrowLeftRight, BarChart3, HardDrive, Settings } from 'lucide-react';

// ─── Node palette definitions ─────────────────────────────

interface PaletteItem {
  type: string;
  label: string;
  icon: React.FC<{ size?: number; className?: string }>;
  color: string;
  bgColor: string;
  borderColor: string;
}

export const PALETTE_ITEMS: PaletteItem[] = [
  {
    type: 'source',
    label: 'Source 源',
    icon: ({ size, className }) => <Database size={size} className={className} />,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-500',
  },
  {
    type: 'transform',
    label: 'Transform 转换',
    icon: ({ size, className }) => <Settings size={size} className={className} />,
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-50',
    borderColor: 'border-emerald-500',
  },
  {
    type: 'join',
    label: 'Join 关联',
    icon: ({ size, className }) => <ArrowLeftRight size={size} className={className} />,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
    borderColor: 'border-purple-500',
  },
  {
    type: 'aggregate',
    label: 'Aggregate 聚合',
    icon: ({ size, className }) => <BarChart3 size={size} className={className} />,
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-500',
  },
  {
    type: 'sink',
    label: 'Sink 输出',
    icon: ({ size, className }) => <HardDrive size={size} className={className} />,
    color: 'text-slate-600',
    bgColor: 'bg-slate-100',
    borderColor: 'border-slate-400',
  },
];

