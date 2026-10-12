/**
 * CreateEntityModal — 创建实体模态框
 *
 * 用于在域设计器中新建本体实体。
 * 编码(必填英文) / 名称(必填中文) / 实体类型 / 描述
 * 提交时调用 useWorkbenchStore.createEntity(data)
 *
 * Props: { open, onClose, domainCode? }
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { X, Box, Loader2, AlertCircle } from 'lucide-react';
import { useWorkbenchStore } from '../../../stores/useWorkbenchStore';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';
import type { CreateEntityDTO } from '../../../types/workbench';

// ── 实体类型选项 ────────────────────────────────────────────

const ENTITY_TYPES: { value: string; labelKey: string; descKey: string }[] = [
  { value: 'MASTER', labelKey: 'ow.entity.type.master', descKey: 'ow.entity.type.master.desc' },
  { value: 'TRANSACTION', labelKey: 'ow.entity.type.transaction', descKey: 'ow.entity.type.transaction.desc' },
  { value: 'EVENT', labelKey: 'ow.entity.type.event', descKey: 'ow.entity.type.event.desc' },
  { value: 'REFERENCE', labelKey: 'ow.entity.type.reference', descKey: 'ow.entity.type.reference.desc' },
];

// ── 组件接口 ────────────────────────────────────────────────

interface CreateEntityModalProps {
  open: boolean;
  onClose: () => void;
  /** 所属域编码（可选，自动填充到 DTO） */
  domainCode?: string;
}

// ── 主组件 ──────────────────────────────────────────────────

export default function CreateEntityModal({ open, onClose, domainCode }: CreateEntityModalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  // 本地表单状态
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [entityType, setEntityType] = useState('MASTER');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const store = useWorkbenchStore();

  // 打开时重置表单
  useEffect(() => {
    if (open) {
      setCode('');
      setName('');
      setEntityType('MASTER');
      setDescription('');
      setError('');
      setSubmitting(false);
    }
  }, [open]);

  // 校验
  const codeRegex = /^[a-zA-Z][a-zA-Z0-9_]*$/;
  const isValid = code.trim() !== '' && codeRegex.test(code) && name.trim() !== '';

  // 提交
  const handleSubmit = async () => {
    if (!isValid || submitting) return;

    if (!codeRegex.test(code)) {
      setError(t('ow.entity.err.codeFormat'));
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      const dto: CreateEntityDTO = {
        code: code.trim(),
        name: name.trim(),
        entityType,
      };
      if (description.trim()) dto.description = description.trim();
      if (domainCode) dto.domain = domainCode;

      await store.createEntity(dto);
      onClose();
    } catch (err: unknown) {
      setError((err as { message?: string } | undefined)?.message || t('ow.entity.err.createFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  // 键盘提交
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && isValid && !submitting) {
      e.preventDefault();
      handleSubmit();
    }
    if (e.key === 'Escape') onClose();
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      onKeyDown={handleKeyDown}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="bg-[#1a1f2e] rounded-xl shadow-2xl w-full max-w-[440px] mx-4 border border-[#2a3040]
        animate-in zoom-in-95 duration-200">
        {/* ── 标题栏 ── */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[#2a3040]">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/10">
              <Box size={16} className="text-indigo-400" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white">{t('ow.entity.title')}</h3>
              <p className={`text-[10px] ${styles.muted}`}>{t('ow.entity.subtitle')}</p>
            </div>
          </div>
          <button type="button"
            onClick={onClose}
            className={`p-1.5 rounded-lg hover:bg-white/5 ${styles.cardTextMuted} hover:${styles.cardText} transition`}
          >
            <X size={16} />
          </button>
        </div>

        {/* ── 表单 ── */}
        <div className="px-5 py-4 space-y-4">
          {/* 编码 */}
          <div>
            <label className={`block text-xs font-medium ${styles.cardTextMuted} mb-1.5`}>
              {t('ow.entity.label.code')} <span className="text-red-400">*</span>
            </label>
            <input
              value={code}
              onChange={(e) => { setCode(e.target.value); setError(''); }}
              placeholder={t('ow.entity.placeholder.code')}
              maxLength={64}
              autoFocus
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:${styles.muted}
                focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                transition"
            />
            <p className={`text-[10px] ${styles.muted} mt-1`}>{t('ow.entity.hint.code')}</p>
          </div>

          {/* 名称 */}
          <div>
            <label className={`block text-xs font-medium ${styles.cardTextMuted} mb-1.5`}>
              {t('ow.entity.label.name')} <span className="text-red-400">*</span>
            </label>
            <input
              value={name}
              onChange={(e) => { setName(e.target.value); setError(''); }}
              placeholder={t('ow.entity.placeholder.name')}
              maxLength={100}
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:${styles.muted}
                focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                transition"
            />
          </div>

          {/* 实体类型 */}
          <div>
            <label className={`block text-xs font-medium ${styles.cardTextMuted} mb-1.5`}>{t('ow.entity.label.entityType')}</label>
            <div className="space-y-1.5">
              {ENTITY_TYPES.map((et) => (
                <label
                  key={et.value}
                  className={`flex items-start gap-3 px-3 py-2.5 rounded-lg border cursor-pointer transition ${
                    entityType === et.value
                      ? 'border-indigo-500/40 bg-indigo-500/10'
                      : 'border-[#2a3040] bg-[#0b0e14] hover:border-[#3a4050]'
                  }`}
                >
                  <input
                    type="radio"
                    name="entityType"
                    value={et.value}
                    checked={entityType === et.value}
                    onChange={(e) => setEntityType(e.target.value)}
                    className="mt-0.5 accent-indigo-500"
                  />
                  <div className="flex-1 min-w-0">
                    <span className={`text-xs font-medium ${styles.sidebarText}`}>{t(et.labelKey)}</span>
                    <p className={`text-[10px] ${styles.muted} mt-0.5`}>{t(et.descKey)}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {/* 描述 */}
          <div>
            <label className={`block text-xs font-medium ${styles.cardTextMuted} mb-1.5`}>{t('ow.entity.label.description')}</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('ow.entity.placeholder.description')}
              rows={3}
              maxLength={500}
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:${styles.muted} resize-none
                focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                transition"
            />
          </div>

          {/* 错误提示 */}
          {error && (
            <div className="flex items-start gap-2 px-3 py-2 rounded-lg bg-red-500/10 border border-red-500/20">
              <AlertCircle size={14} className="text-red-400 mt-0.5 shrink-0" />
              <p className="text-xs text-red-400">{error}</p>
            </div>
          )}
        </div>

        {/* ── 底部按钮 ── */}
        <div className="flex items-center justify-end gap-2.5 px-5 py-4 border-t border-[#2a3040]">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className={`px-4 py-2 rounded-lg text-xs font-medium ${styles.sidebarText}
              bg-[#2a3040] hover:bg-[#3a4050] disabled:opacity-50 transition`}
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!isValid || submitting}
            className="px-5 py-2 rounded-lg text-xs font-semibold text-white
              bg-indigo-600 hover:bg-indigo-500
              disabled:opacity-40 disabled:cursor-not-allowed
              transition flex items-center gap-2"
          >
            {submitting && <Loader2 size={13} className="animate-spin" />}
            {submitting ? t('ow.entity.creating') : t('ow.entity.title')}
          </button>
        </div>
      </div>
    </div>
  );
}
