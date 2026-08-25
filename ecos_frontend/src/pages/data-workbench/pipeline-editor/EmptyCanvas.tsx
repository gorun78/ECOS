/**
 * EmptyCanvas — placeholder for PipelineFlowEditor
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node types (PMO-3J).
 * @license Apache-2.0
 */

import React from 'react';
import { Workflow } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';

// ─── Empty state ──────────────────────────────────────────

const EmptyCanvas: React.FC = () => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div className={`flex flex-col items-center justify-center h-full ${styles.cardTextMuted} pointer-events-none select-none`}>
      <Workflow size={64} className={`mb-4 ${styles.cardTextMuted}`} />
      <p className="text-sm font-medium">{t('dw.pipeline.empty.canvas.title')}</p>
      <p className="text-xs mt-1">{t('dw.pipeline.empty.canvas.hint')}</p>
    </div>
  );
};

export default EmptyCanvas;
