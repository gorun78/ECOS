/**
 * Pipeline Editor — palette & constants
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node type enumeration (PMO-3J T1).
 * Wave 5: extended with TRANSFORM_UDF / JOIN / SINK (lucide icons
 * Braces / GitMerge / Upload to match the backend PipelineNodeTypesService
 * single source of truth).
 * @license Apache-2.0
 */

import { Database, FileText, Globe, Radio, Settings, HardDrive, Braces, GitMerge, Upload } from 'lucide-react';
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
 * Wave 5: extended to all 9 P2-01 types (was 6); B6-1: + SOURCE_MINIO; B6-2: + TRANSFORM_DOC_PARSE → 12.
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
      // B6-1：近源层（数据湖 MinIO）读取节点
      type: 'SOURCE_MINIO',
      labelKey: 'dw.pipeline.node.sourceMinio',
      label: t('dw.pipeline.node.sourceMinio'),
      icon: ({ size, className }) => <Database size={size} className={className} />,
      color: styles.accentText,
      bgColor: styles.accentBg,
      borderColor: styles.accentBorder,
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
      type: 'TRANSFORM_UDF',
      labelKey: 'dw.pipeline.node.transformUdf',
      label: t('dw.pipeline.node.transformUdf'),
      icon: ({ size, className }) => <Braces size={size} className={className} />,
      color: styles.infoText,
      bgColor: styles.infoBg,
      borderColor: styles.infoBorder,
    },
    {
      // B6-2：非结构化文档解析节点（近源层对象 → 解析 → 分块 → DW 层 doc/doc_chunk）
      type: 'TRANSFORM_DOC_PARSE',
      labelKey: 'dw.pipeline.node.transformDocParse',
      label: t('dw.pipeline.node.transformDocParse'),
      icon: ({ size, className }) => <FileText size={size} className={className} />,
      color: styles.infoText,
      bgColor: styles.infoBg,
      borderColor: styles.infoBorder,
    },
    {
      type: 'JOIN',
      labelKey: 'dw.pipeline.node.join',
      label: t('dw.pipeline.node.join'),
      icon: ({ size, className }) => <GitMerge size={size} className={className} />,
      color: styles.accentText,
      bgColor: styles.cardBg,
      borderColor: styles.cardBorder,
    },
    {
      type: 'SINK',
      labelKey: 'dw.pipeline.node.sink',
      label: t('dw.pipeline.node.sink'),
      icon: ({ size, className }) => <Upload size={size} className={className} />,
      color: styles.successText,
      bgColor: styles.successBg,
      borderColor: styles.successBorder,
    },
    {
      type: 'SINK_MINIO',
      labelKey: 'dw.pipeline.node.sinkMinio',
      label: t('dw.pipeline.node.sinkMinio'),
      icon: ({ size, className }) => <Database size={size} className={className} />,
      color: styles.accentText,
      bgColor: styles.accentBg,
      borderColor: styles.accentBorder,
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
  SOURCE_MINIO: 'Source MinIO',
  TRANSFORM_SQL: 'Transform SQL',
  TRANSFORM_UDF: 'Transform UDF',
  TRANSFORM_DOC_PARSE: 'Transform Doc Parse',
  JOIN: 'Join',
  SINK: 'Sink',
  SINK_MINIO: 'Sink MinIO',
  OUTPUT_OBJECT: 'Output Object',
};

/** Ordered list of P2-01 node types (palette order). */
export const PIPELINE_NODE_TYPES: PipelineNodeType[] = [
  'SOURCE_JDBC',
  'SOURCE_CSV',
  'SOURCE_REST',
  'SOURCE_CDC',
  'SOURCE_MINIO',
  'TRANSFORM_SQL',
  'TRANSFORM_UDF',
  'TRANSFORM_DOC_PARSE',
  'JOIN',
  'SINK',
  'SINK_MINIO',
  'OUTPUT_OBJECT',
];
