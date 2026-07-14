/**
 * CreateRelationshipModal — 创建关系模态框
 *
 * 用于在域设计器中创建实体间的关系。
 * 源实体自动填充 / 目标实体下拉选择 / 关系类型 / 编码 / 名称
 * 提交时调用 useWorkbenchStore.createRelationship(data)
 *
 * Props: { open, onClose, sourceEntityId, entities }
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo } from 'react';
import { X, GitBranch, Loader2, AlertCircle, ArrowRight, Search } from 'lucide-react';
import { useWorkbenchStore } from '../../../stores/useWorkbenchStore';
import type { CreateRelationshipDTO, Entity } from '../../../types/workbench';

// ── 关系类型选项 ────────────────────────────────────────────

const RELATIONSHIP_TYPES: { value: string; label: string; icon: string }[] = [
  { value: 'ONE_TO_ONE', label: '一对一 (1:1)', icon: '1—1' },
  { value: 'ONE_TO_MANY', label: '一对多 (1:N)', icon: '1—N' },
  { value: 'MANY_TO_ONE', label: '多对一 (N:1)', icon: 'N—1' },
  { value: 'MANY_TO_MANY', label: '多对多 (N:M)', icon: 'N—M' },
];

// ── 组件接口 ────────────────────────────────────────────────

interface CreateRelationshipModalProps {
  open: boolean;
  onClose: () => void;
  /** 源实体 ID（自动填充，不可修改） */
  sourceEntityId: string;
  /** 当前域内所有实体列表 */
  entities: Entity[];
}

// ── 主组件 ──────────────────────────────────────────────────

export default function CreateRelationshipModal({
  open,
  onClose,
  sourceEntityId,
  entities,
}: CreateRelationshipModalProps) {
  // 本地表单状态
  const [targetEntityId, setTargetEntityId] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [relationshipType, setRelationshipType] = useState('ONE_TO_MANY');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const store = useWorkbenchStore();

  // 源实体对象
  const sourceEntity = useMemo(
    () => entities.find((e) => e.id === sourceEntityId),
    [entities, sourceEntityId]
  );

  // 候选目标实体（排除源实体自身）
  const candidateTargets = useMemo(() => {
    const filtered = entities.filter((e) => e.id !== sourceEntityId);
    if (!searchQuery.trim()) return filtered;
    const q = searchQuery.toLowerCase();
    return filtered.filter(
      (e) =>
        e.code.toLowerCase().includes(q) ||
        e.name.toLowerCase().includes(q)
    );
  }, [entities, sourceEntityId, searchQuery]);

  // 打开时重置表单
  useEffect(() => {
    if (open) {
      setTargetEntityId('');
      setCode('');
      setName('');
      setRelationshipType('ONE_TO_MANY');
      setError('');
      setSubmitting(false);
      setSearchQuery('');
    }
  }, [open]);

  // 校验
  const codeRegex = /^[a-zA-Z][a-zA-Z0-9_]*$/;
  const isValid =
    targetEntityId.trim() !== '' &&
    (code.trim() !== '' || name.trim() !== '') &&
    (!code.trim() || codeRegex.test(code));

  // 提交
  const handleSubmit = async () => {
    if (!isValid || submitting) return;

    if (code.trim() && !codeRegex.test(code)) {
      setError('关系编码仅支持英文字母、数字和下划线，且必须以字母开头');
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      const dto: CreateRelationshipDTO = {
        sourceEntityId,
        targetEntityId: targetEntityId.trim(),
        code: code.trim() || `rel_${sourceEntityId}_${targetEntityId}`.slice(0, 64),
        name: name.trim() || `关系_${sourceEntityId}_${targetEntityId}`.slice(0, 100),
        relationshipType,
      };

      await store.createRelationship(dto);
      onClose();
    } catch (err: any) {
      setError(err?.message || '创建关系失败');
    } finally {
      setSubmitting(false);
    }
  };

  // 键盘操作
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
  };

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      onKeyDown={handleKeyDown}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="bg-[#1a1f2e] rounded-xl shadow-2xl w-full max-w-[480px] mx-4 border border-[#2a3040]
        animate-in zoom-in-95 duration-200">
        {/* ── 标题栏 ── */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[#2a3040]">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-emerald-500/10">
              <GitBranch size={16} className="text-emerald-400" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white">创建关系</h3>
              <p className="text-[10px] text-slate-500">定义实体间的关联关系</p>
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
          {/* 源实体（只读） */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">源实体</label>
            <div className="flex items-center gap-2 px-3 py-2.5 rounded-lg bg-[#0b0e14] border border-[#2a3040]">
              <span className="text-xs font-mono font-semibold text-indigo-400">
                {sourceEntity?.code || sourceEntityId}
              </span>
              {sourceEntity?.name && (
                <span className="text-xs text-slate-500">({sourceEntity.name})</span>
              )}
            </div>
          </div>

          {/* 关系方向提示 */}
          <div className="flex items-center justify-center gap-2 text-[10px] text-slate-500">
            <div className="flex-1 h-px bg-[#2a3040]" />
            <ArrowRight size={14} className="text-slate-600" />
            <span>指向</span>
            <ArrowRight size={14} className="text-slate-600" />
            <div className="flex-1 h-px bg-[#2a3040]" />
          </div>

          {/* 目标实体 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">
              目标实体 <span className="text-red-400">*</span>
            </label>

            {/* 搜索框 */}
            <div className="relative mb-2">
              <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="搜索实体..."
                className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg pl-8 pr-3 py-2
                  text-xs text-white placeholder:text-slate-600
                  focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                  transition"
              />
            </div>

            {/* 目标实体列表 */}
            <div className="max-h-40 overflow-y-auto rounded-lg border border-[#2a3040] bg-[#0b0e14]">
              {candidateTargets.length === 0 ? (
                <div className="px-3 py-4 text-center text-xs text-slate-500">
                  {entities.length <= 1 ? '当前域仅有一个实体，无法创建关系' : '无匹配实体'}
                </div>
              ) : (
                candidateTargets.map((entity) => (
                  <label
                    key={entity.id}
                    className={`flex items-center gap-2.5 px-3 py-2.5 cursor-pointer transition border-b border-[#2a3040]/50 last:border-b-0 ${
                      targetEntityId === entity.id
                        ? 'bg-indigo-500/10'
                        : 'hover:bg-white/[0.03]'
                    }`}
                  >
                    <input
                      type="radio"
                      name="targetEntity"
                      value={entity.id}
                      checked={targetEntityId === entity.id}
                      onChange={(e) => setTargetEntityId(e.target.value)}
                      className="accent-indigo-500 shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <span className="text-xs font-mono font-semibold text-slate-300">
                        {entity.code}
                      </span>
                      {entity.name && (
                        <span className="text-[10px] text-slate-500 ml-1.5">{entity.name}</span>
                      )}
                    </div>
                    <span className="text-[9px] px-1.5 py-0.5 rounded bg-slate-700/50 text-slate-400 font-mono">
                      {entity.entityType}
                    </span>
                  </label>
                ))
              )}
            </div>
          </div>

          {/* 关系类型 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">关系类型</label>
            <div className="grid grid-cols-2 gap-2">
              {RELATIONSHIP_TYPES.map((rt) => (
                <button
                  key={rt.value}
                  type="button"
                  onClick={() => setRelationshipType(rt.value)}
                  className={`flex items-center gap-2 px-3 py-2.5 rounded-lg border text-xs font-medium transition ${
                    relationshipType === rt.value
                      ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300'
                      : 'border-[#2a3040] bg-[#0b0e14] text-slate-400 hover:border-[#3a4050]'
                  }`}
                >
                  <span className="text-[10px] font-mono text-slate-500 w-8">{rt.icon}</span>
                  {rt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 编码 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">关系编码</label>
            <input
              value={code}
              onChange={(e) => { setCode(e.target.value); setError(''); }}
              placeholder="英文字母开头，如 has_order、belongs_to"
              maxLength={64}
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:text-slate-600
                focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                transition"
            />
          </div>

          {/* 名称 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">关系名称</label>
            <input
              value={name}
              onChange={(e) => { setName(e.target.value); setError(''); }}
              placeholder="中文名称，如 拥有订单、归属于"
              maxLength={100}
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-3 py-2.5
                text-sm text-white placeholder:text-slate-600
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
              bg-emerald-600 hover:bg-emerald-500
              disabled:opacity-40 disabled:cursor-not-allowed
              transition flex items-center gap-2"
          >
            {submitting && <Loader2 size={13} className="animate-spin" />}
            {submitting ? '创建中...' : '创建关系'}
          </button>
        </div>
      </div>
    </div>
  );
}
