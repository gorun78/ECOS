/**
 * Pipeline Editor — palette & constants
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node type enumeration (PMO-3J T1).
 * @license Apache-2.0
 */

import { Database, FileText, Globe, Radio, Settings, HardDrive } from 'lucide-react';
import type { PipelineNodeType } from './types';

// ─── Node palette definitions ─────────────────────────────

interface PaletteItem {
  type: PipelineNodeType;
  /** i18n key used to resolve the label at render time. */
  labelKey: string;
  /** Static English fallback label (for non-visual lookups / PALETTE_LABELS). */
  label: string;
  icon: React.FC<{ size?: number; className?: string }>;
  color: string;
  bgColor: string;
  borderColor: string;
  /** Whether the node is disabled in the palette (e.g. edition-gated). */
  disabled?: boolean;
  /** i18n key for the disabled tooltip. */
  disabledTitleKey?: string;
}

type TFunc = (key: string, params?: Record<string, string | number>) => string;

/**
 * Build palette items with theme styles and i18n labels.
 * Must be called inside a component (needs `styles` from useTheme and `t` from useLanguage).
 */
export function buildPaletteItems(
  styles: Record<string, string>,
  t: TFunc
): PaletteItem[] {
  return [
    {
      type: 'SOURCE_JDBC',
      labelKey: 'dw.pipeline.node.sourceJdbc',
      label: t('dw.pipeline.node.sourceJdbc'),
      icon: ({ size, className }) => <Database size={size} className={className} />,
      color: styles.accentText,
      bgColor: styles.infoBg,
      borderColor: styles.infoBorder,
    },
    {
      type: 'SOURCE_CSV',
      labelKey: 'dw.pipeline.node.sourceCsv',
      label: t('dw.pipeline.node.sourceCsv'),
      icon: ({ size, className }) => <FileText size={size} className={className} />,
      color: styles.successText,
      bgColor: styles.successBg,
      borderColor: styles.successBorder,
    },
    {
      type: 'SOURCE_REST',
      labelKey: 'dw.pipeline.node.sourceRest',
      label: t('dw.pipeline.node.sourceRest'),
      icon: ({ size, className }) => <Globe size={size} className={className} />,
      color: styles.infoText,
      bgColor: styles.infoBg,
      borderColor: styles.infoBorder,
    },
    {
      type: 'SOURCE_CDC',
      labelKey: 'dw.pipeline.node.sourceCdc',
      label: t('dw.pipeline.node.sourceCdc'),
      icon: ({ size, className }) => <Radio size={size} className={className} />,
      color: styles.warningText,
      bgColor: styles.warningBg,
      borderColor: styles.warningBorder,
      disabled: true,
      disabledTitleKey: 'dw.pipeline.node.cdcFlagshipOnly',
    },
    {
      type: 'TRANSFORM_SQL',
      labelKey: 'dw.pipeline.node.transformSql',
      label: t('dw.pipeline.node.transformSql'),
      icon: ({ size, className }) => <Settings size={size} className={className} />,
      color: styles.successText,
      bgColor: styles.successBg,
      borderColor: styles.successBorder,
    },
    {
      type: 'OUTPUT_OBJECT',
      labelKey: 'dw.pipeline.node.outputObject',
      label: t('dw.pipeline.node.outputObject'),
      icon: ({ size, className }) => <HardDrive size={size} className={className} />,
      color: styles.cardTextMuted,
      bgColor: styles.sidebarBg,
      borderColor: styles.inputBorder,
    },
  ];
}

/**
 * Static English enum-name mapping (no theme colors / no i18n).
 * Used for non-visual lookups where a stable identifier is needed.
 */
export const PALETTE_LABELS: Record<PipelineNodeType, string> = {
  SOURCE_JDBC: 'Source JDBC',
  SOURCE_CSV: 'Source CSV',
  SOURCE_REST: 'Source REST',
  SOURCE_CDC: 'Source CDC',
  TRANSFORM_SQL: 'Transform SQL',
  OUTPUT_OBJECT: 'Output Object',
};

/** Ordered list of P2-01 node types (palette order). */
export const PIPELINE_NODE_TYPES: PipelineNodeType[] = [
  'SOURCE_JDBC',
  'SOURCE_CSV',
  'SOURCE_REST',
  'SOURCE_CDC',
  'TRANSFORM_SQL',
  'OUTPUT_OBJECT',
];
