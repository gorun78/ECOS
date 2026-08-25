/**
 * Pipeline Editor — palette & constants
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import { Database, GitBranch, ArrowLeftRight, BarChart3, HardDrive, Settings } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';

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
    color: `${styles.accentText}`,
    bgColor: `${styles.infoBg}`,
    borderColor: `${styles.infoBorder}`,
  },
  {
    type: 'transform',
    label: 'Transform 转换',
    icon: ({ size, className }) => <Settings size={size} className={className} />,
    color: `${styles.successText}`,
    bgColor: `${styles.successBg}`,
    borderColor: `${styles.successBorder}`,
  },
  {
    type: 'join',
    label: 'Join 关联',
    icon: ({ size, className }) => <ArrowLeftRight size={size} className={className} />,
    color: `${styles.infoText}`,
    bgColor: `${styles.infoBg}`,
    borderColor: `${styles.infoBorder}`,
  },
  {
    type: 'aggregate',
    label: 'Aggregate 聚合',
    icon: ({ size, className }) => <BarChart3 size={size} className={className} />,
    color: `${styles.warningText}`,
    bgColor: `${styles.warningBg}`,
    borderColor: `${styles.warningBorder}`,
  },
  {
    type: 'sink',
    label: 'Sink 输出',
    icon: ({ size, className }) => <HardDrive size={size} className={className} />,
    color: `${styles.cardTextMuted}`,
    bgColor: `${styles.sidebarBg}`,
    borderColor: `${styles.inputBorder}`,
  },
];


// TODO: useTheme insertion needed
