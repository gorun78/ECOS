/**
 * MappingTab — 数据源映射 Tab
 *
 * 数据映射主区 + W2 新增：
 * - 顶部操作区「校验映射」按钮（触发 C4 映射有效性校验，报告模态由本组件渲染）
 * - 映射卡片新增「非结构化文档锚点」输入区（TABLE / DOC_ONLY / MIXED）
 *
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { Database, Table, Layers, Wand2, Save, ShieldCheck, FileText, AlertTriangle, Loader2, X } from 'lucide-react';
import DynamicIcon from '../../../components/ontology/DynamicIcon';
import type { MappingTabProps } from './types';
import type { DocAnchor, DocAnchorType } from '../../../types/ontology';

/** 锚点类型统一选项（与后端 doc_anchor_type 枚举 TABLE/DOC_ONLY/MIXED 对齐） */
const ANCHOR_TYPE_OPTIONS: Array<{ value: DocAnchorType; labelKey: string }> = [
  { value: 'TABLE', labelKey: 'mapping.anchor.table' },
  { value: 'DOC_ONLY', labelKey: 'mapping.anchor.doc_only' },
  { value: 'MIXED', labelKey: 'mapping.anchor.mixed' },
];

export default function MappingTab({
  objectType,
  datasets,
  selectedDataset,
  handleDatasetChange,
  handleAutoMap,
  handlePropMappingChange,
  mappingDirty,
  onSaveMapping,
  docAnchor,
  docAnchorType,
  handleDocAnchorChange,
  handleDocAnchorTypeChange,
  onValidateMappings,
  validating,
  lastValidation,
  onDismissValidation,
}: MappingTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const mapping = objectType.mapping || { datasetId: '', propertyMappings: {} };
  const anchorType: DocAnchorType = (docAnchorType as DocAnchorType) || 'TABLE';
  const showAnchorFields = anchorType === 'DOC_ONLY' || anchorType === 'MIXED';
  const anchor = docAnchor || { docId: '', source: '' };

  /** 局部更新锚点字段（保留其余字段，触发 handleDocAnchorChange） */
  const patchAnchor = (patch: Partial<DocAnchor>) => {
    handleDocAnchorChange?.({ ...anchor, ...patch });
  };

  /** 关闭报告弹层（无 onDismissValidation 时不渲染弹层，本函数退化为 no-op） */
  const handleDismissReport = () => onDismissValidation?.();

  /** 报告弹层可见性：装载了报告且未被关闭时渲染 */
  const showReport = !!lastValidation && !!onDismissValidation;

  return (
    <div className="space-y-6">
      <div className={`${styles.appBg} border ${styles.cardBorder} rounded-lg p-4 flex justify-between items-center`}>
        <div className="flex items-center gap-3">
          <Database className={styles.cardTextMuted} size={18} />
          <div>
            <div className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.label.boundDataset')}</div>
            <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{selectedDataset?.path}</div>
          </div>
        </div>
        <div className="flex gap-2">
          {mappingDirty && (
            <span className={`text-[10px] ${styles.accentText} font-semibold flex items-center gap-1 self-center`}>
              {t('ow.label.mappingDirty')}
            </span>
          )}
          {/* W2 新增：校验映射按钮（触发 C4 映射有效性校验） */}
          {onValidateMappings && (
            <button
              onClick={onValidateMappings}
              disabled={validating}
              className={`${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1 disabled:opacity-50`}
            >
              {validating
                ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                : <ShieldCheck className="w-3.5 h-3.5" />}
              {t('mapping.validate.button')}
            </button>
          )}
          <button
            onClick={onSaveMapping}
            disabled={!mappingDirty}
            className={`${mappingDirty ? styles.accentText : styles.cardTextMuted} ${
              mappingDirty ? 'border-emerald-300 bg-emerald-50' : styles.cardBorder
            } text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1 border`}
          >
            <Save size={13} />
            {t('ow.btn.saveMapping')}
          </button>
          <select
            value={mapping.datasetId}
            onChange={e => handleDatasetChange(e.target.value)}
            className={`px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.cardBg} focus:outline-hidden`}
          >
            {datasets.map(ds => (
              <option key={ds.id} value={ds.id}>{ds.name}</option>
            ))}
          </select>
          <button
            onClick={handleAutoMap}
            className={`${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1`}
          >
            <Wand2 size={13} />
            {t('ow.btn.smartMap')}
          </button>
        </div>
      </div>

      {/* DW 层无可用数据对象时显式提示，避免下拉与左栏静默空白 */}
      {datasets.length === 0 && (
        <div className={`border ${styles.cardBorder} rounded-lg ${styles.cardBg} p-4 text-xs ${styles.muted}`}>
          {t('ow.msg.noDwDataset')}
        </div>
      )}

      {/* W2 新增：非结构化文档锚点输入区（docAnchorType=DOC_ONLY/MIXED 时才显示 docId/source 字段） */}
      {handleDocAnchorTypeChange && (
        <div className={`border ${styles.cardBorder} rounded-lg ${styles.cardBg} p-4 space-y-3`}>
          <div className="flex items-center gap-2">
            <FileText className={styles.cardTextMuted} size={14} />
            <span className={`text-xs font-semibold ${styles.cardText}`}>
              {t('mapping.anchor.title')}
            </span>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className={`block text-[10px] ${styles.muted} mb-1`}>{t('mapping.anchor.type.label')}</label>
              <select
                value={anchorType}
                onChange={e => handleDocAnchorTypeChange(e.target.value as DocAnchorType)}
                className={`w-full px-2 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.cardBg} focus:outline-hidden`}
              >
                {ANCHOR_TYPE_OPTIONS.map(opt => (
                  <option key={opt.value} value={opt.value}>{t(opt.labelKey)}</option>
                ))}
              </select>
            </div>
            {/* docId / source / docChunkCount 仅在非结构化类型下显示 */}
            {showAnchorFields ? (
              <>
                <div>
                  <label className={`block text-[10px] ${styles.muted} mb-1`}>{t('mapping.anchor.docId.label')}</label>
                  <input
                    type="text"
                    value={anchor.docId || ''}
                    placeholder={t('mapping.anchor.docId.placeholder')}
                    onChange={e => patchAnchor({ docId: e.target.value })}
                    className={`w-full px-2 py-1.5 text-xs font-mono border ${styles.inputBorder} rounded ${styles.cardBg} ${styles.sidebarText} focus:outline-hidden`}
                  />
                </div>
                <div>
                  <label className={`block text-[10px] ${styles.muted} mb-1`}>{t('mapping.anchor.source.label')}</label>
                  <input
                    type="text"
                    value={anchor.source || ''}
                    placeholder="kb"
                    onChange={e => patchAnchor({ source: e.target.value })}
                    className={`w-full px-2 py-1.5 text-xs font-mono border ${styles.inputBorder} rounded ${styles.cardBg} ${styles.sidebarText} focus:outline-hidden`}
                  />
                </div>
              </>
            ) : (
              <div className={`col-span-2 flex items-end pb-1 text-[10px] ${styles.muted}`}>
                {t('mapping.anchor.table_hint')}
              </div>
            )}
          </div>
          {showAnchorFields && (
            <div className="flex items-center gap-3">
              {typeof anchor.docChunkCount === 'number' && (
                <span className={`text-[10px] ${styles.muted}`}>
                  {t('mapping.anchor.chunkCount').replace('{count}', String(anchor.docChunkCount))}
                </span>
              )}
              {!String(anchor.docId || '').trim() && (
                <span className={`flex items-center gap-1 text-[10px] text-amber-500`}>
                  <AlertTriangle size={11} />
                  {t('mapping.anchor.required')}
                </span>
              )}
            </div>
          )}
        </div>
      )}

      {/* Split Screen Mapping Grid */}
      <div className="grid grid-cols-2 gap-8 relative">
        {/* Left Column: Raw Datasource */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1`}>
              <Table size={14} />
              {t('ow.label.rawSchema')}
            </div>
            <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono`}>
              {selectedDataset?.columns.length} {t('ow.label.columns')}
            </span>
          </div>
          <div className={`border ${styles.cardBorder} rounded-lg ${styles.cardBg} overflow-hidden divide-y ${styles.divider}`}>
            {selectedDataset?.columns.map(col => (
              <div key={col.name} className={`flex justify-between items-center px-4 py-2.5 hover:bg-blue-50/20`}>
                <div className={`font-mono text-xs font-medium ${styles.sidebarText}`}>{col.name}</div>
                <div className={`text-[10px] ${styles.muted} font-mono italic uppercase ${styles.sidebarBg} px-1.5 py-0.5 rounded`}>
                  {col.type}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Right Column: Object Properties Mapping */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1`}>
              <Layers size={14} />
              {t('ow.label.propertyMappings')}
            </div>
            <span className={`text-[10px] ${styles.badgeBg} ${styles.accentText} px-1.5 py-0.5 rounded font-mono`}>
              {Object.keys(mapping.propertyMappings).length} / {objectType.properties.length} {t('ow.label.mapped')}
            </span>
          </div>
          <div className={`border ${styles.cardBorder} rounded-lg ${styles.cardBg} overflow-hidden divide-y ${styles.divider}`}>
            {objectType.properties.map(prop => {
              const mappedCol = mapping.propertyMappings[prop.id] || '';
              return (
                <div key={prop.id} className={`flex justify-between items-center px-4 py-2.5 ${styles.cardBg} hover:bg-blue-50/20`}>
                  <div className="flex items-center gap-2">
                    <span className={objectType.primaryKey === prop.id ? 'text-amber-500' : styles.muted}>
                      <DynamicIcon name={objectType.primaryKey === prop.id ? 'Key' : 'CircleDot'} size={12} />
                    </span>
                    <div>
                      <div className={`text-xs font-medium ${styles.cardText}`}>{prop.displayName}</div>
                      <div className={`text-[10px] ${styles.muted} font-mono mt-0.5`}>{prop.id} · {prop.dataType}</div>
                    </div>
                  </div>

                  {/* Mapped Selector */}
                  <div className="flex items-center gap-2">
                    <span className={`${styles.muted} text-[10px]`}>←</span>
                    <select
                      value={mappedCol}
                      onChange={e => handlePropMappingChange(prop.id, e.target.value)}
                      className={`px-2 py-1 text-xs border rounded ${styles.cardBg} focus:outline-hidden font-mono ${styles.sidebarText} ${
                        mappedCol ? 'border-emerald-300 bg-emerald-50/30' : 'border-amber-300 bg-amber-50/10'
                      }`}
                    >
                      <option value="">{t('ow.placeholder.unmapped')}</option>
                      {selectedDataset?.columns.map(col => (
                        <option key={col.name} value={col.name}>{col.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* W2 新增：校验报告模态（通过 → 绿色 banner；失败 → 逐条 issues） */}
      {showReport && lastValidation && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
          onClick={handleDismissReport}
        >
          <div
            className={`${styles.appBg} border ${styles.cardBorder} rounded-lg shadow-xl w-[640px] max-w-[92vw] max-h-[80vh] flex flex-col`}
            onClick={e => e.stopPropagation()}
          >
            <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder}`}>
              <div className="flex items-center gap-2">
                {lastValidation.valid
                  ? <ShieldCheck size={16} className="text-emerald-500" />
                  : <AlertTriangle size={16} className="text-amber-500" />}
                <span className={`text-sm font-semibold ${styles.cardText}`}>{t('mapping.validate.report.title')}</span>
              </div>
              {onDismissValidation && (
                <button onClick={handleDismissReport} className={`${styles.cardTextMuted} hover:opacity-100 opacity-70`} aria-label="close">
                  <X size={16} />
                </button>
              )}
            </div>
            <div className="p-4 overflow-y-auto space-y-3">
              {lastValidation.valid ? (
                <div className="flex items-center gap-2 px-3 py-2 rounded border border-emerald-300 bg-emerald-50/60 text-xs text-emerald-700">
                  <ShieldCheck size={14} />
                  {t('mapping.validate.valid').replace('{count}', String(lastValidation.checkedCount))}
                </div>
              ) : (
                <div className="flex items-center gap-2 px-3 py-2 rounded border border-amber-300 bg-amber-50/60 text-xs text-amber-800">
                  <AlertTriangle size={14} />
                  {t('mapping.validate.invalid').replace('{count}', String(lastValidation.issues.length))}
                  {lastValidation.rejectCode ? (
                    <span className="ml-auto text-[10px] font-mono opacity-60">{lastValidation.rejectCode}</span>
                  ) : null}
                </div>
              )}
              {!lastValidation.valid && lastValidation.issues.length > 0 && (
                <ul className={`border ${styles.cardBorder} rounded ${styles.cardBg} divide-y ${styles.divider}`}>
                  {lastValidation.issues.map((issue, idx) => (
                    <li key={`${idx}-${issue.code}`} className="px-3 py-2">
                      <div className="flex items-center gap-2">
                        <span className={`text-[10px] font-mono ${styles.badgeBg} ${styles.accentText} px-1.5 py-0.5 rounded`}>{issue.code}</span>
                        {issue.tableName ? <span className={`text-[10px] ${styles.muted} font-mono`}>{issue.tableName}</span> : null}
                        {issue.columnName ? <span className={`text-[10px] ${styles.muted} font-mono`}>· {issue.columnName}</span> : null}
                      </div>
                      <div className={`text-xs ${styles.sidebarText} mt-1`}>{issue.message}</div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div className={`flex justify-end px-4 py-3 border-t ${styles.cardBorder}`}>
              <button
                onClick={handleDismissReport}
                className={`${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-xs px-3 py-1.5 rounded font-medium`}
              >
                {t('mapping.validate.close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
