/**
 * OntologyExportModal — 本体工作台导出 Modal
 *
 * 自 OntologyWorkbenchLayout.tsx 拆分（§十 单文件 ≤800 行门禁），仅搬移不改逻辑：
 * T3 主题 token；T10 闭环：格式/范围创建 + 任务列表（轮询/下载/删除）
 * a11y：role=dialog + aria-modal + Esc 关闭 + 打开时聚焦容器
 *
 * @license Apache-2.0
 */

import React, { useEffect, useRef, useState } from 'react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import ExportTasksView from './ExportTasksView';
import { createExportTask, DEFAULT_ONTOLOGY_ID } from '../../services/ontologyApi';
import type { CreateExportDTO } from '../../types/ontology';

interface OntologyExportModalProps {
  visible: boolean;
  onClose: () => void;
  onToast: (type: 'success' | 'info' | 'error', message: string) => void;
}

export default function OntologyExportModal({ visible, onClose, onToast }: OntologyExportModalProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const exportModalRef = useRef<HTMLDivElement>(null);
  const [exportFormat, setExportFormat] = useState<'JSON' | 'CSV' | 'DDL'>('JSON');
  const [exportScope, setExportScope] = useState<'FULL' | 'ENTITIES' | 'RELATIONSHIPS'>('FULL');
  const [exporting, setExporting] = useState(false);
  // T10: 新建导出任务后的列表刷新信号（ExportTasksView 监听自增即重拉任务列表）
  const [exportTasksSignal, setExportTasksSignal] = useState(0);

  // a11y：导出 Modal 打开时把焦点移入对话框容器（配合 Esc 关闭）
  useEffect(() => {
    if (visible) exportModalRef.current?.focus();
  }, [visible]);

  const handleExportOntology = async () => {
    setExporting(true);
    try {
      const dto: CreateExportDTO = {
        ontologyId: DEFAULT_ONTOLOGY_ID,
        format: exportFormat,
        scope: exportScope,
      };
      await createExportTask(dto);
      onToast('success', t('ow.msg.exportStarted'));
      // T10: 创建成功后保持 Modal 打开，刷新右下任务列表并轮询状态闭环
      setExportTasksSignal(v => v + 1);
    } catch {
      onToast('error', t('ow.msg.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  if (!visible) return null;

  return (
    <div className={`fixed inset-0 ${styles.overlayBg} z-50 flex items-center justify-center`} onClick={onClose}>
      <div
        ref={exportModalRef}
        role="dialog"
        aria-modal="true"
        aria-label={t('ow.section.exportPanel')}
        tabIndex={-1}
        onKeyDown={e => { if (e.key === 'Escape') onClose(); }}
        className={`rounded-xl shadow-2xl border ${styles.cardBorder} p-5 w-[34rem] max-w-[92vw] max-h-[85vh] overflow-y-auto ${styles.cardBg} ${styles.cardText} outline-none`}
        onClick={e => e.stopPropagation()}
      >
        <h3 className={`text-sm font-bold mb-3 ${styles.cardText}`}>{t('ow.section.exportPanel')}</h3>
        <div className="space-y-3">
          <div>
            <label className={`block text-[10px] font-semibold mb-1 ${styles.muted}`}>{t('ow.label.exportFormat')}</label>
            <div className="flex gap-2">
              {(['JSON', 'CSV', 'DDL'] as const).map(f => (
                <button key={f} onClick={() => setExportFormat(f)}
                  className={`px-3 py-1.5 rounded text-[10px] font-semibold transition-colors ${exportFormat === f ? `${styles.accentBg} text-white` : `${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}`}>
                  {t(`ow.export.format.${f}`)}
                </button>
              ))}
            </div>
          </div>
          <div>
            <label className={`block text-[10px] font-semibold mb-1 ${styles.muted}`}>{t('ow.label.exportScope')}</label>
            <div className="flex gap-2">
              {(['FULL', 'ENTITIES', 'RELATIONSHIPS'] as const).map(s => (
                <button key={s} onClick={() => setExportScope(s)}
                  className={`px-3 py-1.5 rounded text-[10px] font-semibold transition-colors ${exportScope === s ? `${styles.accentBg} text-white` : `${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}`}>
                  {t(`ow.export.scope.${s}`)}
                </button>
              ))}
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={onClose} className={`px-3 py-1.5 rounded text-[10px] font-semibold ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}>{t('ow.btn.cancel')}</button>
            <button onClick={handleExportOntology} disabled={exporting}
              className={`px-3 py-1.5 rounded text-[10px] font-semibold ${styles.accentBg} text-white ${styles.accentHover} disabled:opacity-50`}>
              {exporting ? t('ow.exportTask.creating') : t('ow.btn.exportOntology')}
            </button>
          </div>
          {/* T10 导出任务闭环：列表 / 状态轮询 / 下载到本地 / 删除已结束任务 */}
          <ExportTasksView visible={visible} refreshSignal={exportTasksSignal} onToast={onToast} />
        </div>
      </div>
    </div>
  );
}
