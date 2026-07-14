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
import type { CreateEntityDTO } from '../../../types/workbench';

// ── 实体类型选项 ────────────────────────────────────────────

const ENTITY_TYPES: { value: string; label: string; desc: string }[] = [
  { value: 'MASTER', label: '主数据 (MASTER)', desc: '核心业务主数据，如客户、产品、供应商' },
  { value: 'TRANSACTION', label: '事务 (TRANSACTION)', desc: '业务交易记录，如订单、支付、发票' },
  { value: 'EVENT', label: '事件 (EVENT)', desc: '业务事件/日志，如登录、操作审计' },
  { value: 'REFERENCE', label: '引用 (REFERENCE)', desc: '字典/配置等引用数据，如国家、币种' },
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
      setError('编码仅支持英文字母、数字和下划线，且必须以字母开头');
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
    } catch (err: any) {
      setError(err?.message || '创建实体失败');
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
              <h3 className="text-sm font-semibold text-white">创建实体</h3>
              <p className="text-[10px] text-slate-500">新建本体实体定义</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-white/5 text-slate-400 hover:text-slate-300 transition"
          >
            <X size={16} />
          </button>
        </div>

        {/* ── 表单 ── */}
        <div className="px-5 py-4 space-y-4">
          {/* 编码 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">
              编码 <span className="text-red-400">*</span>
            </label>
            <input
              value={code}
              onChange={(e) => { setCode(e.target.value); setError(''); }}
              placeholder="英文字母开头，如 Customer、SalesOrder"
              maxLength={64}
              autoFocus
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:text-slate-600
                focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                transition"
            />
            <p className="text-[10px] text-slate-600 mt-1">英文字母开头，支持字母、数字、下划线</p>
          </div>

          {/* 名称 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">
              名称 <span className="text-red-400">*</span>
            </label>
            <input
              value={name}
              onChange={(e) => { setName(e.target.value); setError(''); }}
              placeholder="中文名称，如 客户、销售订单"
              maxLength={100}
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:text-slate-600
                focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                transition"
            />
          </div>

          {/* 实体类型 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">实体类型</label>
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
                    <span className="text-xs font-medium text-slate-300">{et.label}</span>
                    <p className="text-[10px] text-slate-500 mt-0.5">{et.desc}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          {/* 描述 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">描述</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="实体描述（可选）"
              rows={3}
              maxLength={500}
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:text-slate-600 resize-none
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
            onClick={onClose}
            disabled={submitting}
            className="px-4 py-2 rounded-lg text-xs font-medium text-slate-300
              bg-[#2a3040] hover:bg-[#3a4050] disabled:opacity-50 transition"
          >
            取消
          </button>
          <button
            onClick={handleSubmit}
            disabled={!isValid || submitting}
            className="px-5 py-2 rounded-lg text-xs font-semibold text-white
              bg-indigo-600 hover:bg-indigo-500
              disabled:opacity-40 disabled:cursor-not-allowed
              transition flex items-center gap-2"
          >
            {submitting && <Loader2 size={13} className="animate-spin" />}
            {submitting ? '创建中...' : '创建实体'}
          </button>
        </div>
      </div>
    </div>
  );
}
